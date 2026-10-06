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

package top.continew.admin.design.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 表单创建请求参数
 *
 * @author Claude Code
 * @since 2026/9/23 20:30
 */
@Data
@Schema(description = "表单创建请求参数")
public class FormReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表单编码
     */
    @Schema(description = "表单编码", example = "leave_form")
    @NotBlank(message = "表单编码不能为空")
    @Length(max = 40, message = "表单编码长度不能超过 {max} 个字符")
    private String formCode;

    /**
     * 表单名称
     */
    @Schema(description = "表单名称", example = "请假表单")
    @NotBlank(message = "表单名称不能为空")
    @Length(max = 100, message = "表单名称长度不能超过 {max} 个字符")
    private String formName;
}
