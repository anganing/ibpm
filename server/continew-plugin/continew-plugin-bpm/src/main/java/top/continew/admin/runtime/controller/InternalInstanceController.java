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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.runtime.model.req.InternalStartReq;
import top.continew.admin.runtime.model.req.TaskPageReq;
import top.continew.admin.runtime.model.resp.InstanceDetailResp;
import top.continew.admin.runtime.model.resp.InstanceStartResp;
import top.continew.admin.runtime.model.resp.TaskPageResp;
import top.continew.admin.runtime.service.ApprovalRuntimeService;
import top.continew.admin.runtime.service.InstanceStartService;

import java.util.UUID;

/**
 * 本系统流程发起接口
 *
 * @author Codex
 * @since 2026/9/24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/approval/instance")
public class InternalInstanceController {

    private final InstanceStartService instanceStartService;
    private final ApprovalRuntimeService approvalRuntimeService;

    /**
     * 以当前登录用户发起流程。
     * <p>
     * 业务单据 ID 为选填：填写时使用调用方传入的真实单据 ID，留空时由本接口生成 UUID。
     * 两种来源归一为一个 businessId 后走同一条 {@link InstanceStartService#start} 链路，
     * 实例与表单快照写入同一个值。
     *
     * @param req 流程编码、表单数据与选填的业务单据 ID
     * @return 实例 ID
     */
    @PostMapping
    public InstanceStartResp start(@Valid @RequestBody InternalStartReq req) {
        return instanceStartService.start(req.flowCode(), req.formData(),
            UserContextHolder.getUserId(),
            resolveBusinessId(req.businessId()), null);
    }

    /**
     * 归一业务单据 ID：去掉首尾空格后为空则生成 UUID，否则使用调用方传入的真实值。
     *
     * @param businessId 调用方传入的业务单据 ID，可为 null
     * @return 最终写入实例与快照的业务单据 ID
     */
    private String resolveBusinessId(String businessId) {
        if (!StringUtils.hasText(businessId)) {
            return UUID.randomUUID().toString();
        }
        return businessId.trim();
    }

    /**
     * 查询我发起的流程分页。
     *
     * @param flowName   流程名称，模糊匹配
     * @param businessId 业务单据 ID，精确匹配
     * @param instanceId 流程实例 ID，按字符串精确匹配
     * @param flowStatus 流程状态，精确匹配
     * @param page       页码
     * @param size       每页数量
     * @return 我的流程分页
     */
    @GetMapping("/mine/page")
    public TaskPageResp minePage(@RequestParam(required = false) String flowName,
        @RequestParam(required = false) String businessId,
        @RequestParam(required = false) String instanceId,
        @RequestParam(required = false) String flowStatus,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return approvalRuntimeService.pageMyInstances(UserContextHolder.getUserId(),
            new TaskPageReq(flowName, businessId, instanceId, flowStatus, page, size));
    }

    /**
     * 查询本人发起或办理的实例详情。
     *
     * @param id 实例 ID
     * @return 审批详情和表单快照
     */
    @GetMapping("/{id}")
    public InstanceDetailResp detail(@PathVariable Long id) {
        return approvalRuntimeService.detail(id, UserContextHolder.getUserId());
    }
}
