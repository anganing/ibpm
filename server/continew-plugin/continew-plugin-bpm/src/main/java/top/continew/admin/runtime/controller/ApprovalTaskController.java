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

package top.continew.admin.runtime.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.runtime.model.req.ApprovalActionReq;
import top.continew.admin.runtime.model.req.TaskPageReq;
import top.continew.admin.runtime.model.resp.TaskActionResp;
import top.continew.admin.runtime.model.resp.TaskPageResp;
import top.continew.admin.runtime.service.ApprovalRuntimeService;

/**
 * 当前用户的审批待办与办理接口
 *
 * @author Codex
 * @since 2026/9/24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/approval/task")
public class ApprovalTaskController {

    private final ApprovalRuntimeService approvalRuntimeService;

    /**
     * 查询我的待办。
     *
     * @param flowName   流程名称，模糊匹配
     * @param businessId 业务单据 ID，精确匹配
     * @param instanceId 流程实例 ID，按字符串精确匹配
     * @param flowStatus 流程状态，精确匹配
     * @param page       页码
     * @param size       每页数量
     * @return 待办分页
     */
    @GetMapping("/page")
    public TaskPageResp page(@RequestParam(required = false) String flowName,
        @RequestParam(required = false) String businessId,
        @RequestParam(required = false) String instanceId,
        @RequestParam(required = false) String flowStatus,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return approvalRuntimeService.pageTasks(UserContextHolder.getUserId(),
            new TaskPageReq(flowName, businessId, instanceId, flowStatus, page, size));
    }

    /**
     * 查询我的已办。
     *
     * @param flowName   流程名称，模糊匹配
     * @param businessId 业务单据 ID，精确匹配
     * @param instanceId 流程实例 ID，按字符串精确匹配
     * @param flowStatus 流程状态，精确匹配
     * @param page       页码
     * @param size       每页数量
     * @return 已办分页
     */
    @GetMapping("/done/page")
    public TaskPageResp donePage(@RequestParam(required = false) String flowName,
        @RequestParam(required = false) String businessId,
        @RequestParam(required = false) String instanceId,
        @RequestParam(required = false) String flowStatus,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return approvalRuntimeService.pageDoneTasks(UserContextHolder.getUserId(),
            new TaskPageReq(flowName, businessId, instanceId, flowStatus, page, size));
    }

    /**
     * 审批通过。
     *
     * @param id 任务 ID
     * @param req 审批意见
     * @return 办理结果
     */
    @PostMapping("/{id}/approve")
    public TaskActionResp approve(@PathVariable Long id, @RequestBody ApprovalActionReq req) {
        return approvalRuntimeService.handle(id, UserContextHolder.getUserId(), req.message(),
            true);
    }

    /**
     * 审批驳回。
     *
     * @param id 任务 ID
     * @param req 审批意见
     * @return 办理结果
     */
    @PostMapping("/{id}/reject")
    public TaskActionResp reject(@PathVariable Long id, @RequestBody ApprovalActionReq req) {
        return approvalRuntimeService.handle(id, UserContextHolder.getUserId(), req.message(),
            false);
    }
}
