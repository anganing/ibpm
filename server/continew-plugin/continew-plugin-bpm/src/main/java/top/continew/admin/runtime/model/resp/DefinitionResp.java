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
 * 可发起的流程定义
 *
 * @param id       定义 ID
 * @param flowCode 流程编码
 * @param flowName 流程名称
 * @param category 流程分类编码（对应 {@code flow_definition.category}，无分类时为 {@code null}）
 * @author Codex
 * @since 2026/9/24
 */
public record DefinitionResp(@JsonSerialize(using = ToStringSerializer.class) Long id,
    String flowCode, String flowName, String category) {
}
