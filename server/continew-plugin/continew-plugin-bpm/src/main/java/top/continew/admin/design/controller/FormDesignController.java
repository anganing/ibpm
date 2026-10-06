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

package top.continew.admin.design.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.design.model.req.FormReq;
import top.continew.admin.design.model.resp.FormPageResp;
import top.continew.admin.design.service.FormDesignService;

import java.util.Map;

/**
 * 表单设计管理 API
 *
 * <p>
 * 宿主侧表单管理入口：创建表单基础记录、分页查询列表、设计、预览、发布、删除。
 * 表单内容由 Warm-Flow 内置设计器编辑，本接口只读地提供预览所需的内容。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/23 20:30
 */
@Tag(name = "表单设计管理 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/approval/form")
public class FormDesignController {

    private final FormDesignService formDesignService;

    @Operation(summary = "创建表单", description = "创建未发布的内置表单记录，返回表单 ID")
    @SaCheckPermission("bpm:form:create")
    @PostMapping
    public Long create(@RequestBody @Valid FormReq req) {
        return formDesignService.create(req);
    }

    @Operation(summary = "分页查询表单列表", description = "分页查询表单列表，不含完整表单内容")
    @Parameter(name = "formName", description = "表单名称", example = "请假表单", in = ParameterIn.QUERY)
    @Parameter(name = "formCode", description = "表单编码", example = "leave_form",
        in = ParameterIn.QUERY)
    @Parameter(name = "isPublish", description = "是否发布（0未发布 1已发布 9失效）", example = "1",
        in = ParameterIn.QUERY)
    @SaCheckPermission("bpm:form:view")
    @GetMapping("/page")
    public FormPageResp page(@RequestParam(required = false) String formName,
        @RequestParam(required = false) String formCode,
        @RequestParam(required = false) Integer isPublish,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
        return formDesignService.page(formName, formCode, isPublish, page, size);
    }

    @Operation(summary = "设计表单", description = "返回设计器应打开的表单 ID：未发布表单返回自身，已发布表单返回复用或复制出的未发布新版本 ID")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:form:update")
    @PostMapping("/{id}/design")
    public Long design(@PathVariable Long id) {
        return formDesignService.design(id);
    }

    @Operation(summary = "读取表单内容", description = "读取表单定义（rule 与 option），供只读预览使用")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:form:view")
    @GetMapping("/{id}/content")
    public Map<String, Object> content(@PathVariable Long id) {
        return formDesignService.content(id);
    }

    @Operation(summary = "发布表单", description = "发布已保存表单内容的表单")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:form:publish")
    @PostMapping("/{id}/publish")
    public void publish(@PathVariable Long id) {
        formDesignService.publish(id);
    }

    @Operation(summary = "删除表单", description = "删除未被流程定义引用且未发布的表单")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:form:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        formDesignService.delete(id);
    }
}
