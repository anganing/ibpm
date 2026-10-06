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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.FormService;
import org.springframework.stereotype.Service;
import top.continew.admin.design.mapper.ProcessPauseMapper;
import top.continew.admin.design.model.entity.ProcessPauseDO;
import top.continew.admin.design.service.ProcessPauseService;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程暂停标记业务实现
 *
 * <p>
 * 暂停状态保存在宿主侧独立表 {@code bpm_process_pause}，键为<b>流程定义 ID</b>。
 * 之所以不复用 Warm-Flow 的 {@code activity_status}：该字段被引擎同时用于拒绝新实例发起
 * 与拒绝在途实例的任务办理，用它做暂停会把在途审批一并冻结。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPauseServiceImpl implements ProcessPauseService {

    /** 暂停标记的取值：0 未暂停；1 已暂停 */
    private static final Integer PAUSED = 1;

    private final ProcessPauseMapper processPauseMapper;
    private final DefService defService;
    private final FormService formService;

    @Override
    public boolean isPaused(Long definitionId) {
        if (definitionId == null) {
            return false;
        }
        Long count = processPauseMapper.selectCount(new LambdaQueryWrapper<ProcessPauseDO>()
            .eq(ProcessPauseDO::getDefinitionId, definitionId)
            .eq(ProcessPauseDO::getPaused, PAUSED));
        return count != null && count > 0;
    }

    @Override
    public Set<Long> pausedDefinitionIds() {
        return processPauseMapper.selectList(new LambdaQueryWrapper<ProcessPauseDO>()
            .eq(ProcessPauseDO::getPaused, PAUSED)).stream()
            .map(ProcessPauseDO::getDefinitionId)
            .collect(Collectors.toSet());
    }

    @Override
    public void pause(Long definitionId) {
        Definition definition = requireEffective(definitionId);
        if (isPaused(definition.getId())) {
            // 幂等：重复暂停直接成功，避免前端重复点击报错
            return;
        }
        writePaused(definition.getId(), PAUSED);
    }

    @Override
    public void resume(Long definitionId) {
        Definition definition = requireEffective(definitionId);
        // 恢复后该流程会重新出现在发起入口，因此校验绑定的表单仍然有效可发起
        Form form = findPublishedForm(definition);
        if (form == null) {
            throw new BusinessException(
                "流程 [%s] 未绑定有效的已发布表单，无法恢复".formatted(definition.getFlowCode()));
        }
        writePaused(definition.getId(), 0);
    }

    /**
     * 校验目标定义存在且确为当前生效版本
     *
     * <p>
     * 生效版本的判定必须与发起链路同源（{@code getPublishByFlowCode}），否则会出现
     * 「页面判定 A 是生效版本、实际发起走 B」的偏差。草稿、历史版本、已失效版本都会在此被拒绝。
     * </p>
     */
    private Definition requireEffective(Long definitionId) {
        Definition definition = definitionId == null ? null : defService.getById(definitionId);
        if (definition == null) {
            throw new BusinessException("流程定义 [%s] 不存在".formatted(definitionId));
        }
        Definition effective = defService.getPublishByFlowCode(definition.getFlowCode());
        if (effective == null || !effective.getId().equals(definition.getId())) {
            throw new BusinessException("流程定义 [%s] 不是当前生效版本，无法暂停或恢复".formatted(definitionId));
        }
        return definition;
    }

    /**
     * 写入暂停标记；无记录则新建，有则更新
     */
    private void writePaused(Long definitionId, Integer paused) {
        // 唯一键为 (definition_id, deleted)，正常只有一行；用 limit 1 兜底历史脏数据
        List<ProcessPauseDO> existList =
            processPauseMapper.selectList(new LambdaQueryWrapper<ProcessPauseDO>()
                .eq(ProcessPauseDO::getDefinitionId, definitionId)
                .last("LIMIT 1"));
        if (existList.isEmpty()) {
            ProcessPauseDO record = new ProcessPauseDO();
            record.setDefinitionId(definitionId);
            record.setPaused(paused);
            record.setDeleted(0L);
            processPauseMapper.insert(record);
            return;
        }
        ProcessPauseDO record = existList.get(0);
        record.setPaused(paused);
        processPauseMapper.updateById(record);
    }

    /**
     * 取该定义绑定的已发布表单，与发起链路的口径保持一致
     */
    private Form findPublishedForm(Definition definition) {
        if (!"Y".equals(definition.getFormCustom()) || definition.getFormPath() == null) {
            return null;
        }
        try {
            Form form = formService.getById(Long.valueOf(definition.getFormPath()));
            return form != null && PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())
                && form.getFormContent() != null ? form : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
