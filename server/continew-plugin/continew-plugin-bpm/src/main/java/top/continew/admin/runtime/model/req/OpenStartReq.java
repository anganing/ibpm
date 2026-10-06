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

package top.continew.admin.runtime.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 第三方签名发起流程请求
 *
 * @param flowCode        流程编码
 * @param initiatorUserId 本系统发起人 ID
 * @param businessId      第三方业务单据 ID
 * @param formData        表单数据
 * @author Codex
 * @since 2026/9/24
 */
public record OpenStartReq(@NotBlank String flowCode, @NotNull Long initiatorUserId,
    @NotBlank @Size(max = 40) String businessId,
    @NotNull Map<String, Object> formData) {
}
