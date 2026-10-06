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
import lombok.Data;

/**
 * 流程定义请求参数
 *
 * <p>
 * 仅用于创建流程定义草稿：宿主需先拿到定义 ID 才能打开设计器（设计器只能按 URL 中的 id 定位流程）。
 * 流程的分类、模型、表单绑定等由设计器保存时写入，此处不接收。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Data
@Schema(description = "流程定义请求参数")
public class ProcessReq {

    /**
     * 流程编码
     */
    @Schema(description = "流程编码", example = "oa_leave01")
    private String flowCode;

    /**
     * 流程名称
     */
    @Schema(description = "流程名称", example = "请假流程")
    private String flowName;
}
