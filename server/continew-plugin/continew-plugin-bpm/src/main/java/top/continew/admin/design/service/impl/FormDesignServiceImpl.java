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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.FormService;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import top.continew.admin.design.model.req.FormReq;
import top.continew.admin.design.model.resp.FormPageResp;
import top.continew.admin.design.model.resp.FormResp;
import top.continew.admin.design.service.FormDesignService;
import top.continew.starter.cache.redisson.util.RedisLockUtils;
import top.continew.starter.core.exception.BusinessException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表单设计管理业务实现
 *
 * @author Claude Code
 * @since 2026/9/23 20:30
 */
@Service
@RequiredArgsConstructor
public class FormDesignServiceImpl implements FormDesignService {

    /**
     * 内置表单类型：表单内容存于 form_content
     */
    private static final Integer FORM_TYPE_BUILT_IN = 0;

    /**
     * 同编码新版本创建锁前缀，避免并发「设计」在检查与插入之间重复创建草稿
     */
    private static final String DRAFT_LOCK_PREFIX = "Lock:bpm:form:draft:";

    private static final TypeReference<Map<String, Object>> CONTENT_TYPE = new TypeReference<>() {
    };

    private final FormService formService;
    private final DefService defService;
    private final ObjectMapper objectMapper;

    @Override
    public Long create(FormReq req) {
        String formCode = req.getFormCode();
        String formName = req.getFormName();
        // 同一编码的多个版本由 Warm-Flow 按 version 管理，仅要求名称不重复以便识别。
        // 该校验只作用于「新增」入口：同 formCode 的新版本由 design() 复制生成，不走本方法，
        // 因此不会因为版本必须同名而被拦截。
        List<Form> existList = formService.list(FlowEngine.newForm().setFormName(formName));
        if (!existList.isEmpty()) {
            throw new BusinessException("表单名称 [%s] 已存在".formatted(formName));
        }
        // 不自行生成 ID：交由 Warm-Flow 的数据填充器与底层主键生成器处理
        Form form = FlowEngine.newForm()
            .setFormCode(formCode)
            .setFormName(formName)
            .setFormType(FORM_TYPE_BUILT_IN)
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        formService.save(form);
        return form.getId();
    }

    @Override
    public Long design(Long id) {
        Form form = requireForm(id);
        if (!PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())) {
            // 未发布表单：直接编辑当前记录
            return form.getId();
        }
        // 已发布表单：必须产生新版本，绝不能改写已发布记录的内容。
        // 加分布式锁，串行化同一 formCode 的「查草稿 + 建草稿」，防止并发重复创建。
        String lockKey = DRAFT_LOCK_PREFIX + form.getFormCode();
        try (RedisLockUtils lock = RedisLockUtils.tryLockWithWatchdog(lockKey)) {
            if (!lock.isLocked()) {
                throw new BusinessException("表单 [%s] 正在生成新版本，请稍后重试".formatted(id));
            }
            Form draft = findDraft(form.getFormCode());
            if (draft != null) {
                return draft.getId();
            }
            return copyAsDraft(form);
        }
    }

    @Override
    public Map<String, Object> content(Long id) {
        Form form = requireForm(id);
        if (StringUtils.isEmpty(form.getFormContent())) {
            throw new BusinessException("表单 [%s] 尚未保存表单内容，暂无可预览内容".formatted(id));
        }
        try {
            Map<String, Object> parsed =
                objectMapper.readValue(form.getFormContent(), CONTENT_TYPE);
            if (!(parsed.get("rule") instanceof List<?>)
                || !(parsed.get("option") instanceof Map<?, ?>)) {
                throw new BusinessException("表单 [%s] 内容缺少 rule 或 option".formatted(id));
            }
            return parsed;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("表单 [%s] 内容不是有效 JSON".formatted(id));
        }
    }

    /**
     * 查找同编码下待设计的未发布版本。
     *
     * <p>
     * 重复点击「设计」必须复用同一草稿，否则会不断产生新版本。存在多个草稿时取版本号最大者，
     * 版本号相同则取最近更新的一条。
     * </p>
     */
    private Form findDraft(String formCode) {
        return formService.list(FlowEngine.newForm()
            .setFormCode(formCode)
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey())).stream()
            .max(Comparator.comparingInt(this::parseVersion)
                .thenComparing(Form::getUpdateTime,
                    Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);
    }

    /**
     * 复制来源表单为同 formCode 的新版本草稿。
     *
     * <p>
     * 版本号交由 {@code FormService#save} 按 Warm-Flow 的规则计算并持久化
     * （同 formCode 现有最大数字版本 + 1），此处不自行拼装，也不由前端传入。
     * </p>
     */
    private Long copyAsDraft(Form source) {
        Form draft = FlowEngine.newForm()
            .setFormCode(source.getFormCode())
            .setFormName(source.getFormName())
            .setFormType(source.getFormType())
            .setFormPath(source.getFormPath())
            .setExt(source.getExt())
            .setFormContent(source.getFormContent())
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        formService.save(draft);
        return draft.getId();
    }

    private int parseVersion(Form form) {
        try {
            return Integer.parseInt(form.getVersion());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public FormPageResp page(String formName, String formCode, Integer isPublish, int page,
        int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("分页参数无效");
        }
        // Warm-Flow 的 FormService 不提供组合条件查询，沿用现有 list() 的全量取回 + 内存过滤/分页模式
        List<Form> formList = formService.list(FlowEngine.newForm()).stream()
            .filter(form -> StringUtils.isEmpty(formName)
                || containsIgnoreNull(form.getFormName(), formName))
            .filter(form -> StringUtils.isEmpty(formCode)
                || containsIgnoreNull(form.getFormCode(), formCode))
            .filter(form -> isPublish == null || Objects.equals(form.getIsPublish(), isPublish))
            .sorted(Comparator.comparing(Form::getUpdateTime).reversed())
            .toList();
        long total = formList.size();
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            return new FormPageResp(List.of(), total);
        }
        int from = (int) offset;
        int to = (int) Math.min(total, offset + size);
        return new FormPageResp(formList.subList(from, to).stream().map(this::toResp).toList(),
            total);
    }

    @Override
    public void publish(Long id) {
        Form form = requireForm(id);
        // 发布前必须有已保存的表单内容，否则设计器无法渲染该表单
        if (StringUtils.isEmpty(form.getFormContent())) {
            throw new BusinessException("表单 [%s] 尚未保存表单内容，请先在设计器中保存后再发布".formatted(id));
        }
        if (PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())) {
            throw new BusinessException("表单 [%s] 已发布，无需重复发布".formatted(id));
        }
        formService.publish(id);
    }

    @Override
    public void delete(Long id) {
        Form form = requireForm(id);
        if (PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())) {
            throw new BusinessException("表单 [%s] 已发布，不允许删除".formatted(id));
        }
        // 被流程定义引用时不可删除，否则流程发起时无法渲染表单
        String formPath = id.toString();
        List<Definition> referenced =
            defService.list(FlowEngine.newDef().setFormPath(formPath)).stream()
                .filter(definition -> formPath.equals(definition.getFormPath()))
                .toList();
        if (!referenced.isEmpty()) {
            throw new BusinessException("表单 [%s] 已被流程定义 [%s] 引用，不允许删除".formatted(id,
                referenced.get(0).getFlowName()));
        }
        formService.removeById(id);
    }

    private FormResp toResp(Form form) {
        FormResp resp = new FormResp();
        resp.setId(form.getId());
        resp.setFormCode(form.getFormCode());
        resp.setFormName(form.getFormName());
        resp.setVersion(form.getVersion());
        resp.setIsPublish(form.getIsPublish());
        resp.setUpdateTime(form.getUpdateTime());
        return resp;
    }

    private Form requireForm(Long id) {
        Form form = formService.getById(id);
        if (form == null) {
            throw new BusinessException("表单 [%s] 不存在".formatted(id));
        }
        return form;
    }

    private boolean containsIgnoreNull(String source, String keyword) {
        return source != null && source.contains(keyword);
    }
}
