/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.design.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.FormService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import top.continew.admin.design.model.req.ProcessReq;
import top.continew.admin.design.model.resp.ProcessIntegrationResp;
import top.continew.admin.design.model.resp.ProcessPageResp;
import top.continew.admin.design.model.resp.ProcessResp;
import top.continew.admin.design.model.resp.ProcessVersionResp;
import top.continew.admin.design.service.ProcessDesignService;
import top.continew.admin.design.service.ProcessPauseService;
import top.continew.starter.cache.redisson.util.RedisLockUtils;
import top.continew.starter.core.exception.BusinessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程定义设计管理业务实现
 *
 * <p>
 * 主列表按「每个流程编码只展示版本号最大的定义」收敛；「修改」在目标已发布时复制出下一版本草稿；
 * 「暂停/恢复」只作用于当前生效版本且只拦截新实例发起。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/24 00:30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDesignServiceImpl implements ProcessDesignService {

    /**
     * 设计器模型默认值：经典模式（与 Warm-Flow 内置设计器的新建默认一致）
     */
    private static final String DEFAULT_MODEL_VALUE = "CLASSICS";

    /**
     * 同编码新版本创建锁前缀，避免并发「修改」在检查与插入之间重复创建草稿
     */
    private static final String DRAFT_LOCK_PREFIX = "Lock:bpm:process:draft:";

    /**
     * 激活状态：1 激活（新复制的草稿必须显式激活，否则引擎会拒绝发起）
     */
    private static final Integer ACTIVITY = 1;

    private final DefService defService;
    private final FormService formService;
    private final InsService insService;
    private final ProcessPauseService processPauseService;
    private final ObjectMapper objectMapper;

    /**
     * 表单内容反序列化目标类型：rule 为数组、option 为对象
     */
    private static final TypeReference<Map<String, Object>> FORM_CONTENT_TYPE =
        new TypeReference<>() {
        };

    @Override
    public ProcessPageResp page(String flowName, String flowCode, String category,
        Integer isPublish, int page,
        int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("分页参数无效");
        }
        // 先在全量结果中收敛出「每个流程编码的版本号最大的定义」，再对这批行做筛选、排序与分页。
        // total 因此是流程编码数，而非历史版本行数。
        List<Definition> latestList = latestPerFlowCode(defService.list(FlowEngine.newDef()));
        List<Definition> definitions =
            filter(latestList, flowName, flowCode, category, isPublish);
        long total = definitions.size();
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            return new ProcessPageResp(List.of(), total);
        }
        int to = (int) Math.min(total, offset + size);
        List<Definition> pageRows = definitions.subList((int) offset, to);
        // 本页涉及的流程编码一次性取出暂停标记，避免逐行查库
        Set<Long> pausedIds = processPauseService.pausedDefinitionIds();
        List<ProcessResp> list = pageRows.stream().map(item -> toResp(item, pausedIds)).toList();
        return new ProcessPageResp(list, total);
    }

    @Override
    public Long create(ProcessReq req) {
        String flowCode = req.getFlowCode();
        String flowName = req.getFlowName();
        if (StringUtils.isEmpty(flowCode)) {
            throw new BusinessException("流程编码不能为空");
        }
        if (StringUtils.isEmpty(flowName)) {
            throw new BusinessException("流程名称不能为空");
        }
        // 不自行生成 ID：交由 Warm-Flow 的数据填充器与底层主键生成器处理
        // 版本号规则由 Warm-Flow 决定，同一 flowCode 可存在多个版本，故此处不校验编码唯一
        Definition definition = FlowEngine.newDef()
            .setFlowCode(flowCode)
            .setFlowName(flowName)
            .setModelValue(DEFAULT_MODEL_VALUE)
            .setVersion("1")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey())
            .setActivityStatus(ACTIVITY);
        defService.save(definition);
        return definition.getId();
    }

    @Override
    public void delete(Long id) {
        Definition definition = requireDefinition(id);
        // 已发布（含已失效）的定义存在表单绑定与在途快照的一致性问题，只允许保留
        Integer isPublish = definition.getIsPublish();
        if (isPublish != null && !PublishStatus.UNPUBLISHED.getKey().equals(isPublish)) {
            throw new BusinessException("流程定义 [%s] 状态为 [%s]，不允许删除".formatted(id,
                PublishStatus.getValueByKey(isPublish)));
        }
        // 已有流程实例的定义被物理删除后，实例详情无法回溯流程编码与名称
        List<Instance> instances = insService.getByDefId(id);
        if (!instances.isEmpty()) {
            throw new BusinessException(
                "流程定义 [%s] 已有 [%d] 个流程实例，不允许删除".formatted(id, instances.size()));
        }
        // 复用 Warm-Flow 的删除入口，由其级联清理节点与连线
        if (!defService.removeDef(List.of(id))) {
            throw new BusinessException("流程定义 [%s] 删除失败".formatted(id));
        }
    }

    @Override
    public Long design(Long id) {
        Definition definition = requireDefinition(id);
        Integer isPublish = definition.getIsPublish();
        if (PublishStatus.EXPIRED.getKey().equals(isPublish)) {
            // 历史失效版本只读保留，不允许再编辑
            throw new BusinessException("流程定义 [%s] 已失效，不可修改".formatted(id));
        }
        if (!PublishStatus.PUBLISHED.getKey().equals(isPublish)) {
            // 未发布草稿：只有最新版本可继续设计；被更高版本超越的旧草稿视为历史版本，只读
            requireLatestVersion(definition, "设计");
            return definition.getId();
        }
        // 已发布：必须产生新版本草稿，绝不能改写已发布记录的内容与绑定。
        // 加分布式锁串行化同一 flowCode 的「查草稿 + 建草稿」，防止并发重复创建。
        String lockKey = DRAFT_LOCK_PREFIX + definition.getFlowCode();
        try (RedisLockUtils lock = RedisLockUtils.tryLockWithWatchdog(lockKey)) {
            if (!lock.isLocked()) {
                throw new BusinessException("流程 [%s] 正在生成新版本，请稍后重试".formatted(id));
            }
            // 只复用严格高于来源版本的草稿。若同编码另有更旧的未发布草稿（如 v3 早于已发布的 v4），
            // 那是被超越的历史版本，不能拿来编辑，必须为本次来源重新复制。
            Definition draft = reusableDraftFor(definition);
            if (draft != null) {
                // 幂等：重复点击「修改」复用同一草稿，不产生多个版本
                return draft.getId();
            }
            return copyAsDraft(definition);
        }
    }

    /**
     * 取可用于本次「修改」的既有草稿：版本号必须<b>严格高于</b>已发布来源版本
     *
     * <p>
     * 复用草稿的前提是它由同一来源（或更新版本）复制而来。若同编码存在版本更低的未发布草稿，
     * 说明它已被来源超越、属于历史版本，复用它会把用户带回旧内容，故返回 null 触发重新复制。
     * </p>
     *
     * @param source 已发布的来源定义
     * @return 可复用的草稿；无可复用草稿时返回 null
     */
    private Definition reusableDraftFor(Definition source) {
        Definition draft = latestDraftOfFlowCode(source.getFlowCode());
        if (draft == null) {
            return null;
        }
        // 用比较器判定严格更高版本，保持与「最新版本」相同的数值比较口径。
        // VERSION_ASC 为升序，故草稿“大于”来源即版本严格更高。
        return VERSION_ASC.compare(draft, source) > 0 ? draft : null;
    }

    @Override
    public void publish(Long id) {
        Definition definition = requireDefinition(id);
        Integer isPublish = definition.getIsPublish();
        if (PublishStatus.PUBLISHED.getKey().equals(isPublish)) {
            throw new BusinessException("流程定义 [%s] 已发布，无需重复发布".formatted(id));
        }
        if (PublishStatus.EXPIRED.getKey().equals(isPublish)) {
            // Warm-Flow 的 publish 不校验目标自身状态：直接调用会把已失效定义复活为已发布。
            // 历史失效版本按产品规则只读保留，故在此显式拒绝。
            throw new BusinessException("流程定义 [%s] 已失效，不允许重新发布".formatted(id));
        }
        // 只有最新版本可发布；被更高版本超越的旧草稿视为历史版本，只读保留
        requireLatestVersion(definition, "发布");
        // 发布前必须有已保存的节点，否则引擎无法实例化该流程
        List<Node> nodeList = FlowEngine.nodeService().getByDefId(id);
        if (nodeList.isEmpty()) {
            throw new BusinessException("流程定义 [%s] 尚未绘制流程，请先在设计器中保存后再发布".formatted(id));
        }
        // 绑定的表单必须存在且已发布，否则发起时无法渲染表单
        String formPath = definition.getFormPath();
        if (StringUtils.isEmpty(formPath)) {
            throw new BusinessException("流程定义 [%s] 尚未绑定表单，请先在设计器中绑定后再发布".formatted(id));
        }
        Form form = formService.getById(Long.valueOf(formPath));
        if (form == null) {
            throw new BusinessException("流程定义 [%s] 绑定的表单 [%s] 不存在".formatted(id, formPath));
        }
        if (!PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())) {
            throw new BusinessException("流程定义 [%s] 绑定的表单 [%s] 尚未发布，请先发布表单".formatted(id, formPath));
        }
        // defService.publish 会把同 flowCode 的其他已发布版本降级（有实例→9，无实例→0），
        // 从而使当前生效版本切换到本条，发起链路随即选中新版本。
        defService.publish(id);
    }

    @Override
    public void pause(Long id) {
        processPauseService.pause(id);
    }

    @Override
    public void resume(Long id) {
        processPauseService.resume(id);
    }

    @Override
    public List<ProcessVersionResp> versions(Long id) {
        Definition definition = requireDefinition(id);
        Definition effective = defService.getPublishByFlowCode(definition.getFlowCode());
        Long effectiveId = effective == null ? null : effective.getId();
        boolean paused = effectiveId != null && processPauseService.isPaused(effectiveId);
        return defService.list(FlowEngine.newDef().setFlowCode(definition.getFlowCode())).stream()
            .sorted(VERSION_ASC.reversed())
            .map(item -> new ProcessVersionResp(item.getId(), item.getFlowCode(),
                item.getFlowName(), item.getVersion(), item.getIsPublish(),
                item.getActivityStatus(), item.getUpdateTime(),
                item.getId().equals(effectiveId), paused))
            .toList();
    }

    @Override
    public ProcessIntegrationResp integration(Long id) {
        Definition source = requireDefinition(id);
        // 与发起链路同源选版：主列表展示的可能是最新草稿，不能把它当作对接版本
        Definition effective = defService.getPublishByFlowCode(source.getFlowCode());
        String flowCode = source.getFlowCode();
        if (effective == null) {
            throw new BusinessException("流程 [%s] 没有已发布定义，无法生成对接说明".formatted(flowCode));
        }
        if (!ACTIVITY.equals(effective.getActivityStatus())) {
            throw new BusinessException("流程 [%s] 的生效版本已挂起，暂不可发起".formatted(flowCode));
        }
        if (processPauseService.isPaused(effective.getId())) {
            throw new BusinessException("流程 [%s] 已暂停，暂不可发起".formatted(flowCode));
        }
        if (!"Y".equals(effective.getFormCustom())
            || StringUtils.isEmpty(effective.getFormPath())) {
            throw new BusinessException("流程 [%s] 的生效版本未绑定表单，无法生成对接说明".formatted(flowCode));
        }
        Form form;
        try {
            form = formService.getById(Long.valueOf(effective.getFormPath()));
        } catch (NumberFormatException e) {
            throw new BusinessException("流程 [%s] 绑定的表单标识无效".formatted(flowCode));
        }
        if (form == null) {
            throw new BusinessException("流程 [%s] 绑定的表单不存在，无法生成对接说明".formatted(flowCode));
        }
        if (!PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())) {
            throw new BusinessException("流程 [%s] 绑定的表单 [%s] 尚未发布，无法生成对接说明".formatted(flowCode,
                form.getId()));
        }
        if (StringUtils.isEmpty(form.getFormContent())) {
            throw new BusinessException("表单 [%s] 尚未保存表单内容，无法生成对接说明".formatted(form.getId()));
        }
        return new ProcessIntegrationResp(flowCode, effective.getFlowName(), effective.getId(),
            effective.getVersion(), form.getId(), form.getFormName(), form.getVersion(),
            parseFormContent(form));
    }

    /**
     * 解析表单内容为 rule + option，形状与本系统发起链路的 {@code formContent} 完全一致
     *
     * <p>
     * 直接解析并校验形状，不做字段搬运或改写：前端复用同一套渲染组件，多一次转换只会增加
     * 与发起链路不一致的风险。
     * </p>
     */
    private Map<String, Object> parseFormContent(Form form) {
        Map<String, Object> content;
        try {
            content = objectMapper.readValue(form.getFormContent(), FORM_CONTENT_TYPE);
        } catch (JsonProcessingException e) {
            throw new BusinessException("表单 [%s] 内容不是有效 JSON".formatted(form.getId()));
        }
        if (!(content.get("rule") instanceof List<?>)
            || !(content.get("option") instanceof Map<?, ?>)) {
            throw new BusinessException("表单 [%s] 内容缺少 rule 或 option".formatted(form.getId()));
        }
        return content;
    }

    /**
     * 收敛出每个流程编码的「最新版本」（版本号最大者）     *
     * <p>
     * 用 {@link #VERSION_ASC} 而非字符串比较，避免 "10" 排在 "9" 之前；同一编码存在相同版本号的
     * 异常数据时，该比较器以 updateTime、id 逐级决胜，保证结果稳定且可复现。
     * </p>
     */
    private List<Definition> latestPerFlowCode(List<Definition> definitions) {
        Map<String, Definition> latestByCode = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            String code = definition.getFlowCode();
            if (StringUtils.isEmpty(code)) {
                // 脏数据：无编码的定义无法归组，跳过并留痕
                log.warn("[BPM] 流程定义 [{}] 缺少 flowCode，已跳过主列表归组", definition.getId());
                continue;
            }
            latestByCode.merge(code, definition, (a, b) -> VERSION_ASC.compare(a, b) >= 0 ? a : b);
        }
        List<Definition> latestList = new ArrayList<>(latestByCode.values());
        // 相同版本号的异常数据在此留痕，便于发现设计器重复保存等问题（不阻断列表）
        Map<String, Long> codeCount = definitions.stream()
            .filter(item -> StringUtils.isNotEmpty(item.getFlowCode()))
            .collect(Collectors.groupingBy(Definition::getFlowCode, Collectors.counting()));
        Map<String, Long> versionCount = definitions.stream()
            .filter(item -> StringUtils.isNotEmpty(item.getFlowCode()))
            .collect(Collectors.groupingBy(item -> item.getFlowCode() + "#" + item.getVersion(),
                Collectors.counting()));
        versionCount.forEach((key, count) -> {
            if (count > 1) {
                log.warn("[BPM] 流程存在相同版本号的重复定义，流程 [{}] 共 [{}] 条定义",
                    key.substring(0, key.indexOf('#')), codeCount.getOrDefault(key
                        .substring(0, key.indexOf('#')), 0L));
            }
        });
        return latestList;
    }

    /**
     * 取同编码下的最新版本定义（版本号最大者），不存在时返回 null
     *
     * <p>
     * 主列表归组、{@code /design}、{@code /publish} 与 save-json 保护共用本方法，
     * 保证四处对「最新版本」的判定完全一致（同为数值比较 + 相同的稳定决胜规则）。
     * </p>
     */
    private Definition latestOfFlowCode(String flowCode) {
        return defService.list(FlowEngine.newDef().setFlowCode(flowCode)).stream()
            .max(VERSION_ASC)
            .orElse(null);
    }

    /**
     * 取同编码下版本号最大的未发布草稿，不存在时返回 null
     *
     * <p>
     * 必须用 {@code max(VERSION_ASC)} 取版本号<b>最大</b>的草稿。此处曾是缺陷点：
     * 早期实现用 {@code min(VERSION_DESC.reversed())}，在降序比较器上取到的是最低版本，
     * 会把已被超越的旧草稿选中。
     * </p>
     */
    private Definition latestDraftOfFlowCode(String flowCode) {
        return defService.list(FlowEngine.newDef()
            .setFlowCode(flowCode)
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey())).stream()
            .max(VERSION_ASC)
            .orElse(null);
    }

    /**
     * 校验目标定义确为同编码下的最新版本，否则拒绝
     *
     * <p>
     * 被更高版本超越的旧草稿（如 v3 已被已发布的 v4 取代）应视为历史版本，只能只读查看。
     * 「主列表不展示旧版本」不构成防护：接口可被绕过页面直接调用，故在服务层显式收口。
     * </p>
     *
     * @param definition 待校验定义
     * @param action     动作名，用于错误提示（如「设计」「发布」）
     */
    private void requireLatestVersion(Definition definition, String action) {
        Definition latest = latestOfFlowCode(definition.getFlowCode());
        if (latest == null || !latest.getId().equals(definition.getId())) {
            throw new BusinessException("流程定义 [%s] 不是流程 [%s] 的最新版本（当前最新为 [%s]），不允许%s"
                .formatted(definition.getId(), definition.getFlowCode(), latest == null
                    ? "无"
                    : latest.getId(), action));
        }
    }

    /**
     * 复制来源定义为同 flowCode 的下一版本未发布草稿
     *
     * <p>
     * 复用 Warm-Flow 的 {@code copyDef}：它会完整复制定义、节点与连线，并按自己的
     * {@code getNewVersion} 计算版本号（同编码最大数字版本 +1）。{@code Definition.copy()}
     * 不复制 isPublish/activityStatus，故新记录天然为未发布。
     * </p>
     *
     * <p>
     * <b>不要在此重新计算版本号</b>：{@code copyDef} 内部已按同一规则算好并落库，复制后再叠加一次
     * 会因读到刚插入的记录而使版本号多跳一级（v1 → v3）。此处只补齐未发布与激活状态，
     * 并刷新更新时间供主列表排序。
     * </p>
     *
     * <p>
     * 该接口返回 boolean 不返回新 ID，故复制后按「同编码未发布中版本号最大者」反查新记录，
     * 在锁内进行以保证并发安全。
     * </p>
     */
    private Long copyAsDraft(Definition source) {
        if (!defService.copyDef(source.getId())) {
            throw new BusinessException("流程 [%s] 复制新版本失败".formatted(source.getId()));
        }
        // 反查刚复制出的新草稿：取版本号最大的未发布草稿（复制出的版本必然高于同编码其他草稿）。
        // 在锁内进行，故并发下不会误取到别的记录。
        Definition draft = latestDraftOfFlowCode(source.getFlowCode());
        if (draft == null) {
            throw new BusinessException("流程 [%s] 复制后未找到新版本草稿".formatted(source.getId()));
        }
        // 只补状态与更新时间，版本号沿用 copyDef 的结果。
        // updateById 只更新非 null 字段，故不会误清空 flowName/category/formPath 等既有值。
        Definition update = FlowEngine.newDef()
            .setId(draft.getId())
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey())
            .setActivityStatus(ACTIVITY)
            .setUpdateTime(new java.util.Date());
        defService.updateById(update);
        return draft.getId();
    }

    /**
     * 版本比较器：<b>升序</b>——版本号数值小者在前，相同版本号时依次以更新时间、ID 升序决胜
     *
     * <p>
     * 刻意定义为<b>升序</b>，使 {@code max(...)} 的语义与直觉一致（取版本号最大者）。
     * 反之若定义为降序，{@code max(...)} 会取到<b>最低</b>版本——这正是本类历史上出现过的缺陷，
     * 故此处保持升序，并在所有取「最新」的地方统一使用 {@code max(VERSION_ASC)}。
     * </p>
     *
     * <p>
     * 主列表归组、最新版本判定、草稿选取三处共用同一比较器，避免出现「列表显示的最新版本」
     * 与「点开修改的那一条」不一致；版本记录展示用 {@link #VERSION_ASC} 的逆序。
     * </p>
     */
    private static final Comparator<Definition> VERSION_ASC = Comparator
        .comparingInt((Definition item) -> {
            try {
                return Integer.parseInt(item.getVersion());
            } catch (NumberFormatException e) {
                return 0;
            }
        })
        .thenComparing(Definition::getUpdateTime,
            Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(Definition::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * 按查询条件过滤流程定义，并按更新时间倒序排列
     */
    private List<Definition> filter(List<Definition> definitions, String flowName, String flowCode,
        String category,
        Integer isPublish) {
        return definitions.stream()
            .filter(item -> StringUtils.isEmpty(flowName) || (item
                .getFlowName() != null && item.getFlowName().contains(flowName)))
            .filter(item -> StringUtils.isEmpty(flowCode) || (item
                .getFlowCode() != null && item.getFlowCode().contains(flowCode)))
            .filter(item -> StringUtils.isEmpty(category) || category.equals(item.getCategory()))
            .filter(item -> isPublish == null || isPublish.equals(item.getIsPublish()))
            .sorted(Comparator.comparing(Definition::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    private Definition requireDefinition(Long id) {
        Definition definition = id == null ? null : defService.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义 [%s] 不存在".formatted(id));
        }
        return definition;
    }

    private ProcessResp toResp(Definition definition, Set<Long> pausedIds) {
        // 生效版本必须复用发起链路同源的 getPublishByFlowCode，避免「列表显示 A、实际发起走 B」
        Definition effective = defService.getPublishByFlowCode(definition.getFlowCode());
        Long effectiveId = effective == null ? null : effective.getId();
        boolean paused = effectiveId != null && pausedIds.contains(effectiveId);
        return new ProcessResp(definition.getId(), definition.getFlowCode(),
            definition.getFlowName(),
            definition.getModelValue(), definition.getCategory(), definition.getVersion(),
            definition.getIsPublish(), definition.getActivityStatus(), definition.getUpdateTime(),
            effectiveId, effective == null ? null : effective.getVersion(), paused);
    }
}
