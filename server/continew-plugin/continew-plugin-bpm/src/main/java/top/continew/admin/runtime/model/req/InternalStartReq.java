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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 本系统发起流程请求
 *
 * @param flowCode   流程编码
 * @param formData   表单数据
 * @param businessId 业务单据 ID，选填；留空时由后端生成 UUID
 * @author Codex
 * @since 2026/9/24
 */
public record InternalStartReq(@NotBlank String flowCode, @NotNull Map<String, Object> formData,
    @Pattern(regexp = "^$|^\\S(?:.*\\S)?$", message = "业务单据 ID 不能包含首尾空格") @Size(max = 40,
        message = "业务单据 ID 长度不能超过 40 个字符") String businessId) {
}
