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

package top.continew.admin.runtime.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Map;

/**
 * 审批实例详情与表单快照
 *
 * @param instanceId      实例 ID
 * @param definitionId    流程定义 ID
 * @param flowCode        流程编码
 * @param flowName        流程名称
 * @param businessId      业务单据 ID
 * @param flowStatus      引擎状态
 * @param lastSkipType    最近审批动作
 * @param initiatorUserId 发起人 ID
 * @param formContent     发起时的表单规则与配置
 * @param formData        发起时提交的数据
 * @author Codex
 * @since 2026/9/24
 */
public record InstanceDetailResp(@JsonSerialize(using = ToStringSerializer.class) Long instanceId,
    @JsonSerialize(using = ToStringSerializer.class) Long definitionId, String flowCode,
    String flowName, String businessId, String flowStatus,
    String lastSkipType, @JsonSerialize(using = ToStringSerializer.class) Long initiatorUserId,
    Map<String, Object> formContent, Map<String, Object> formData) {
}
