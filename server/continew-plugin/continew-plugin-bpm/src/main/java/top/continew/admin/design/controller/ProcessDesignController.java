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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.design.model.req.ProcessReq;
import top.continew.admin.design.model.resp.ProcessIntegrationResp;
import top.continew.admin.design.model.resp.ProcessPageResp;
import top.continew.admin.design.model.resp.ProcessVersionResp;
import top.continew.admin.design.service.ProcessDesignService;

import java.util.List;

/**
 * 流程设计管理 API
 *
 * <p>
 * 宿主侧的流程定义管理入口：分页列表、创建草稿、设计定位、发布、暂停/恢复、版本记录、删除。
 * 流程的绘制与保存由 Warm-Flow 内置设计器完成，本接口不涉及流程内容读写。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/24 00:30
 */
@Tag(name = "流程设计管理 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/approval/process")
public class ProcessDesignController {

    private final ProcessDesignService processDesignService;

    @Operation(summary = "分页查询流程定义", description = "按名称、编码、分类、发布状态分页查询流程定义")
    @SaCheckPermission("bpm:designer:view")
    @GetMapping("/page")
    public ProcessPageResp page(@RequestParam(required = false) String flowName,
        @RequestParam(required = false) String flowCode,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer isPublish,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size) {
        return processDesignService.page(flowName, flowCode, category, isPublish, page, size);
    }

    @Operation(summary = "创建流程定义", description = "创建未发布的流程定义草稿，返回定义 ID 供设计器打开")
    @SaCheckPermission("bpm:designer:create")
    @PostMapping
    public Long create(@RequestBody ProcessReq req) {
        return processDesignService.create(req);
    }

    @Operation(summary = "删除流程定义", description = "仅允许删除未发布且无流程实例的流程定义")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        processDesignService.delete(id);
    }

    @Operation(summary = "定位设计器应打开的定义", description = "未发布草稿返回自身 ID；已发布定义复制出下一版本草稿并返回其 ID")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:update")
    @PostMapping("/{id}/design")
    public Long design(@PathVariable Long id) {
        return processDesignService.design(id);
    }

    @Operation(summary = "暂停流程定义", description = "暂停当前生效版本，暂停后不可再发起；已有实例与在途审批任务不受影响")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:update")
    @PostMapping("/{id}/pause")
    public void pause(@PathVariable Long id) {
        processDesignService.pause(id);
    }

    @Operation(summary = "恢复流程定义", description = "恢复当前生效版本，恢复后可再次发起；恢复前校验绑定的表单仍有效")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:update")
    @PostMapping("/{id}/resume")
    public void resume(@PathVariable Long id) {
        processDesignService.resume(id);
    }

    @Operation(summary = "查询版本记录", description = "查询该定义所属流程编码下的全部版本，含 ID、版本号、发布状态、激活状态与更新时间")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:view")
    @GetMapping("/{id}/versions")
    public List<ProcessVersionResp> versions(@PathVariable Long id) {
        return processDesignService.versions(id);
    }

    @Operation(summary = "发布流程定义", description = "发布已保存并绑定已发布表单的流程定义")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:publish")
    @PostMapping("/{id}/publish")
    public void publish(@PathVariable Long id) {
        processDesignService.publish(id);
    }

    @Operation(summary = "查询第三方对接说明",
        description = "只读返回当前生效定义及其绑定的已发布表单；无可发起版本、未绑定或未发布表单、已暂停时返回原因")
    @Parameter(name = "id", description = "ID", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("bpm:designer:view")
    @GetMapping("/{id}/integration")
    public ProcessIntegrationResp integration(@PathVariable Long id) {
        return processDesignService.integration(id);
    }
}
