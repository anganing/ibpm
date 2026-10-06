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

package top.continew.admin.runtime.service.impl;

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
import top.continew.admin.design.service.ProcessPauseService;
import top.continew.admin.runtime.model.resp.DefinitionFormResp;
import top.continew.admin.runtime.model.resp.DefinitionResp;
import top.continew.admin.runtime.service.DefinitionReadService;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可发起流程及表单读取业务实现
 *
 * @author Codex
 * @since 2026/9/24
 */
@Service
@RequiredArgsConstructor
public class DefinitionReadServiceImpl implements DefinitionReadService {

    private static final Integer ACTIVE = 1;
    private static final TypeReference<Map<String, Object>> CONTENT_TYPE = new TypeReference<>() {
    };

    private final DefService defService;
    private final FormService formService;
    private final ObjectMapper objectMapper;
    private final ProcessPauseService processPauseService;

    @Override
    public List<DefinitionResp> listAvailable(String category) {
        boolean filterByCategory = StringUtils.isNotEmpty(category);
        // 暂停只拦截新发起：被暂停的生效版本从可发起列表中移除，其它流程不受影响
        Set<Long> pausedIds = processPauseService.pausedDefinitionIds();
        return defService.list(FlowEngine.newDef()
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ACTIVE)
            .setCategory(filterByCategory ? category : null)).stream()
            .filter(definition -> !pausedIds.contains(definition.getId()))
            .filter(definition -> findPublishedForm(definition) != null)
            .map(definition -> new DefinitionResp(definition.getId(), definition.getFlowCode(),
                definition.getFlowName(), definition.getCategory()))
            .toList();
    }

    @Override
    public DefinitionFormResp getForm(String flowCode) {
        Definition definition = requireAvailable(flowCode);
        Form form = findPublishedForm(definition);
        return new DefinitionFormResp(definition.getId(), definition.getFlowCode(),
            parseContent(form));
    }

    @Override
    public Definition requireAvailable(String flowCode) {
        Definition definition = defService.getPublishByFlowCode(flowCode);
        if (definition == null || !ACTIVE.equals(definition.getActivityStatus())
            || findPublishedForm(definition) == null) {
            throw new BusinessException("流程 [%s] 不可发起或未绑定已发布表单".formatted(flowCode));
        }
        // 暂停只拦截新实例发起；已有实例与在途审批任务不经过此判定，故不受影响
        if (processPauseService.isPaused(definition.getId())) {
            throw new BusinessException("流程 [%s] 已暂停，暂不可发起".formatted(flowCode));
        }
        return definition;
    }

    @Override
    public void checkAvailable(Long definitionId) {
        if (definitionId == null) {
            throw new BusinessException("流程定义不存在");
        }
        Definition definition = defService.getById(definitionId);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        // 与发起接口同源：requireAvailable(flowCode) 即 getPublishByFlowCode 选中的版本，
        // 只有 ID 一致才放行；同 flowCode 的旧版本即使自身已发布也拒绝
        Definition available = requireAvailable(definition.getFlowCode());
        if (!available.getId().equals(definitionId)) {
            throw new BusinessException("流程定义 [%s] 不可发起或未绑定已发布表单".formatted(definitionId));
        }
    }

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

    private Map<String, Object> parseContent(Form form) {
        try {
            Map<String, Object> content =
                objectMapper.readValue(form.getFormContent(), CONTENT_TYPE);
            if (!(content.get("rule") instanceof List<?>)
                || !(content.get("option") instanceof Map<?, ?>)) {
                throw new BusinessException("表单 [%s] 内容缺少 rule 或 option".formatted(form.getId()));
            }
            return content;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("表单 [%s] 内容不是有效 JSON".formatted(form.getId()));
        }
    }
}
