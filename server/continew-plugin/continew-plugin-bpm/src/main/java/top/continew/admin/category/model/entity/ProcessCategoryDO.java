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

package top.continew.admin.category.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.io.Serial;

/**
 * 流程分类
 *
 * <p>
 * 分类为单级扁平结构，不设父子关系。{@code code} 是「流程定义 → 分类」的引用键：
 * Warm-Flow 设计器分类下拉的 value 即本字段，{@code flow_definition.category} 存的也是本字段。
 * 因此 {@code code} 必须在分类全生命周期内保持稳定（重命名 {@code name} 无影响）。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Data
@TableName("bpm_process_category")
public class ProcessCategoryDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类编码（唯一，流程定义的分类引用键）
     */
    private String code;

    /**
     * 排序
     */
    private Integer sort;
}
