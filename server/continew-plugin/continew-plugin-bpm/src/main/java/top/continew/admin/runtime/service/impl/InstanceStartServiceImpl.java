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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.service.InsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.runtime.mapper.FormSnapshotMapper;
import top.continew.admin.runtime.model.entity.FormSnapshotDO;
import top.continew.admin.runtime.model.resp.DefinitionFormResp;
import top.continew.admin.runtime.model.resp.InstanceStartResp;
import top.continew.admin.runtime.service.DefinitionReadService;
import top.continew.admin.runtime.service.InstanceStartService;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程发起与表单快照事务
 *
 * @author Codex
 * @since 2026/9/24
 */
@Service
@RequiredArgsConstructor
public class InstanceStartServiceImpl implements InstanceStartService {

    private final DefinitionReadService definitionReadService;
    private final InsService insService;
    private final FormSnapshotMapper formSnapshotMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceStartResp start(String flowCode, Map<String, Object> formData,
        Long initiatorUserId,
        String businessId, Long appId) {
        Definition definition = definitionReadService.requireAvailable(flowCode);
        DefinitionFormResp form = definitionReadService.getForm(flowCode);
        if (!definition.getId().equals(form.definitionId())) {
            throw new BusinessException("流程定义发生变化，请重试");
        }
        FlowParams params = FlowParams.build()
            .flowCode(flowCode)
            .handler(initiatorUserId.toString())
            .formData(formData);
        Instance instance = insService.start(businessId, params);
        FormSnapshotDO snapshot = new FormSnapshotDO();
        snapshot.setInstanceId(instance.getId());
        snapshot.setDefinitionId(definition.getId());
        snapshot.setBusinessId(businessId);
        snapshot.setAppId(appId);
        snapshot.setCreateUser(initiatorUserId);
        snapshot.setCreateTime(LocalDateTime.now());
        snapshot.setDeleted(0L);
        try {
            snapshot.setFormContentJson(objectMapper.writeValueAsString(form.formContent()));
            snapshot.setFormDataJson(objectMapper.writeValueAsString(formData));
        } catch (JsonProcessingException e) {
            throw new BusinessException("表单数据无法保存");
        }
        if (formSnapshotMapper.insert(snapshot) != 1) {
            throw new BusinessException("表单快照保存失败");
        }
        return new InstanceStartResp(instance.getId());
    }
}
