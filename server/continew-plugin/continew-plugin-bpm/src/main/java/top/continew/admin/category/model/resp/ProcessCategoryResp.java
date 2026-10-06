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

package top.continew.admin.category.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Date;

/**
 * 流程分类响应参数
 *
 * @param id         分类 ID
 * @param name       分类名称
 * @param code       分类编码（流程定义以此引用分类）
 * @param sort       排序
 * @param createTime 创建时间
 * @author Claude Code
 * @since 2026/9/25
 */
public record ProcessCategoryResp(@JsonSerialize(using = ToStringSerializer.class) Long id,
    String name,
    String code, Integer sort, Date createTime) {
}
