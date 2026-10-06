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

package top.continew.admin.category.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.dto.Tree;
import org.dromara.warm.flow.ui.service.CategoryService;
import org.springframework.stereotype.Service;
import top.continew.admin.category.service.ProcessCategoryService;

import java.util.List;

/**
 * Warm-Flow 设计器分类服务实现
 *
 * <p>
 * 这是「设计器分类下拉有数据」的唯一接入口。Warm-Flow 只提供 SPI 而不提供实现，宿主不实现则
 * {@code CategoryService} 为空 bean，设计器拿到的 {@code categoryList} 恒为空数组。
 * </p>
 *
 * <p>
 * 调用链：{@code CategoryService.queryCategory()} → {@code WarmFlowService.queryDef()} 塞入
 * {@link org.dromara.warm.flow.core.dto.DefJson#setCategoryList} → {@code GET /warm-flow/query-def/{id}} 返回。
 * 分类数据不走独立接口。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Service
@RequiredArgsConstructor
public class BpmCategoryServiceImpl implements CategoryService {

    private final ProcessCategoryService processCategoryService;

    @Override
    public List<Tree> queryCategory() {
        return processCategoryService.queryCategory();
    }
}
