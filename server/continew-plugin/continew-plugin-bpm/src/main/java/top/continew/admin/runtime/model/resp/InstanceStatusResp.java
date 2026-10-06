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

/**
 * 流程实例状态
 *
 * @param instanceId   实例 ID
 * @param flowCode     流程编码
 * @param businessId   业务单据 ID
 * @param flowStatus   引擎状态
 * @param lastSkipType 最近审批动作
 * @author Codex
 * @since 2026/9/24
 */
public record InstanceStatusResp(@JsonSerialize(using = ToStringSerializer.class) Long instanceId,
    String flowCode, String businessId,
    String flowStatus, String lastSkipType) {
}
