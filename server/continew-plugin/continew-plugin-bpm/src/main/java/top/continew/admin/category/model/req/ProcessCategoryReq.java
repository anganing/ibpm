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

package top.continew.admin.category.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

/**
 * 流程分类请求参数
 *
 * @param name 分类名称
 * @param code 分类编码（唯一；流程定义以此引用分类）
 * @param sort 排序
 * @author Claude Code
 * @since 2026/9/25
 */
@Schema(description = "流程分类请求参数")
public record ProcessCategoryReq(
    @Schema(description = "分类名称", example = "人事审批") @NotBlank(message = "分类名称不能为空") @Length(
        max = 50, message = "分类名称长度不能超过 {max} 个字符") String name,

    @Schema(description = "分类编码", example = "hr") @NotBlank(message = "分类编码不能为空") @Length(max = 50,
        message = "分类编码长度不能超过 {max} 个字符") String code,

    @Schema(description = "排序", example = "1") Integer sort) {
}
