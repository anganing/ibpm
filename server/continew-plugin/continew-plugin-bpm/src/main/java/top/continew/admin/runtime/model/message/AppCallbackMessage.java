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

package top.continew.admin.runtime.model.message;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 流程终态回调报文
 *
 * <p>
 * 仅在流程到达终态时发送，不含全量表单数据。审批通过与被驳回都会到达终态
 * （引擎均置 {@code flow_status=8}），二者靠 {@code approved} 区分。
 * </p>
 *
 * <p>
 * {@code instanceId} 必须序列化为字符串：雪花 ID 超出 JavaScript 安全整数范围，
 * 直接输出为 JSON 数字会在接收端丢精度。
 * </p>
 *
 * @param event           事件类型，当前固定为 PROCESS_FINISHED
 * @param instanceId      流程实例 ID
 * @param businessId      业务单据 ID
 * @param flowCode        流程编码
 * @param flowName        流程名称
 * @param approved        是否审批通过（false 表示终态为驳回）
 * @param flowStatus      引擎流程状态，终态为 8
 * @param flowStatusLabel 流程状态中文名，取自 Warm-Flow 的 FlowStatus 枚举
 * @param message         终审审批意见，可为 null
 * @param finishTime      终态完成时间，格式 yyyy-MM-dd HH:mm:ss
 * @author Claude Code
 * @since 2026/9/26
 */
public record AppCallbackMessage(String event,
    @JsonSerialize(using = ToStringSerializer.class) Long instanceId,
    String businessId,
    String flowCode,
    String flowName,
    boolean approved,
    String flowStatus,
    String flowStatusLabel,
    String message,
    String finishTime) {

    /**
     * 回调事件类型：流程终态
     */
    public static final String EVENT_PROCESS_FINISHED = "PROCESS_FINISHED";
}
