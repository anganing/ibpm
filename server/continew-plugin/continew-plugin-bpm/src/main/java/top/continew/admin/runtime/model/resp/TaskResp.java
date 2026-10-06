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

import java.util.Date;

/**
 * 待办、已办、我的流程共用的列表行
 *
 * <p>
 * 三个列表的行键不同：待办、已办用 {@code taskId}，我的流程用 {@code instanceId} 且 {@code taskId} 为
 * {@code null}，前端不得统一行键。businessId 与 flowStatus 取自实例的真实流程数据，不由前端逐行查询拼装。
 * </p>
 *
 * @param taskId     任务 ID，我的流程为 null
 * @param instanceId 实例 ID
 * @param businessId 业务单据 ID
 * @param flowCode   流程编码
 * @param flowName   流程名称
 * @param nodeName   待办节点、已办理节点或实例当前节点
 * @param flowStatus 流程状态，取 Warm-Flow 引擎的 flow_status
 * @param createTime 待办创建时间、办理时间或流程发起时间
 * @author Codex
 * @since 2026/9/24
 */
public record TaskResp(@JsonSerialize(using = ToStringSerializer.class) Long taskId,
    @JsonSerialize(using = ToStringSerializer.class) Long instanceId, String businessId,
    String flowCode, String flowName, String nodeName, String flowStatus, Date createTime) {
}
