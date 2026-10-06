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

package top.continew.admin.runtime.service;

import top.continew.admin.runtime.model.req.TaskPageReq;
import top.continew.admin.runtime.model.resp.InstanceDetailResp;
import top.continew.admin.runtime.model.resp.InstanceStatusResp;
import top.continew.admin.runtime.model.resp.TaskActionResp;
import top.continew.admin.runtime.model.resp.TaskPageResp;

/**
 * 流程实例查询和任务办理业务
 *
 * @author Codex
 * @since 2026/9/24
 */
public interface ApprovalRuntimeService {

    /**
     * 当前用户的待办。
     *
     * @param userId 用户 ID
     * @param query 查询条件与分页参数
     * @return 待办分页
     */
    TaskPageResp pageTasks(Long userId, TaskPageReq query);

    /**
     * 当前用户的已办。
     *
     * @param userId 用户 ID
     * @param query 查询条件与分页参数
     * @return 已办分页
     */
    TaskPageResp pageDoneTasks(Long userId, TaskPageReq query);

    /**
     * 当前用户发起的流程实例。
     *
     * @param userId 用户 ID
     * @param query 查询条件与分页参数
     * @return 我的流程分页
     */
    TaskPageResp pageMyInstances(Long userId, TaskPageReq query);

    /**
     * 当前用户可访问的实例详情。
     *
     * @param instanceId 实例 ID
     * @param userId 当前用户 ID
     * @return 详情
     */
    InstanceDetailResp detail(Long instanceId, Long userId);

    /**
     * 审批任务。
     *
     * @param taskId 任务 ID
     * @param userId 当前用户 ID
     * @param message 审批意见
     * @param approve true 通过，false 驳回
     * @return 办理结果
     */
    TaskActionResp handle(Long taskId, Long userId, String message, boolean approve);

    /**
     * 仅查询当前验签应用发起的实例状态。
     *
     * @param instanceId 实例 ID
     * @param appId 验签应用 ID
     * @return 实例状态
     */
    InstanceStatusResp openStatus(Long instanceId, Long appId);
}
