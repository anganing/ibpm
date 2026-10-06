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

package top.continew.admin.design.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Map;

/**
 * 第三方对接说明：流程当前生效定义及其绑定的已发布表单
 *
 * <p>
 * 选版与发起链路同源（Warm-Flow {@code getPublishByFlowCode} + 已发布表单判定），
 * 因此流程模型主列表展示最新草稿时，本响应仍给出真正用于发起的那一版。
 * 不返回应用 Secret Key、真实令牌或任何第三方凭据。
 * </p>
 *
 * @param flowCode          流程编码
 * @param flowName          流程名称
 * @param definitionId      当前生效定义 ID
 * @param definitionVersion 当前生效定义版本号
 * @param formId            绑定表单 ID
 * @param formName          绑定表单名称
 * @param formVersion       绑定表单版本号
 * @param formContent       已解析的表单规则与配置（rule + option）
 * @author Claude Code
 * @since 2026/9/26
 */
public record ProcessIntegrationResp(String flowCode, String flowName,
    @JsonSerialize(using = ToStringSerializer.class) Long definitionId,
    String definitionVersion,
    @JsonSerialize(using = ToStringSerializer.class) Long formId,
    String formName, String formVersion,
    Map<String, Object> formContent) {
}
