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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 表单简要响应参数
 *
 * <p>
 * 仅供表单管理列表使用，不包含 form_content 等完整表单定义。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/23 20:30
 */
@Data
@Schema(description = "表单简要响应参数")
public class FormResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @Schema(description = "ID", example = "893224086832939080")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 表单编码
     */
    @Schema(description = "表单编码", example = "leave_form")
    private String formCode;

    /**
     * 表单名称
     */
    @Schema(description = "表单名称", example = "请假表单")
    private String formName;

    /**
     * 版本号
     */
    @Schema(description = "版本号", example = "1")
    private String version;

    /**
     * 是否发布（0未发布 1已发布 9失效）
     */
    @Schema(description = "是否发布（0未发布 1已发布 9失效）", example = "0")
    private Integer isPublish;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2026-09-24 17:43:43")
    private Date updateTime;
}
