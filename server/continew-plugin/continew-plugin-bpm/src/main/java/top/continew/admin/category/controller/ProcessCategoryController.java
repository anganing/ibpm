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

package top.continew.admin.category.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.category.model.req.ProcessCategoryReq;
import top.continew.admin.category.model.resp.ProcessCategoryResp;
import top.continew.admin.category.service.ProcessCategoryService;

import java.util.List;

/**
 * 流程分类管理 API
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Tag(name = "流程分类管理 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/approval/category")
public class ProcessCategoryController {

    private final ProcessCategoryService processCategoryService;

    @Operation(summary = "查询流程分类列表", description = "返回全量分类，不分页（左树与分类下拉需一次性全量数据）")
    @SaCheckPermission("bpm:category:view")
    @GetMapping("/list")
    public List<ProcessCategoryResp> list() {
        return processCategoryService.list();
    }

    @Operation(summary = "创建流程分类")
    @SaCheckPermission("bpm:category:create")
    @PostMapping
    public String create(@Valid @RequestBody ProcessCategoryReq req) {
        return processCategoryService.create(req);
    }

    @Operation(summary = "修改流程分类")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:category:update")
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @Valid @RequestBody ProcessCategoryReq req) {
        processCategoryService.update(id, req);
    }

    @Operation(summary = "删除流程分类", description = "分类被流程定义引用时拒绝删除")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:category:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        processCategoryService.delete(id);
    }
}
