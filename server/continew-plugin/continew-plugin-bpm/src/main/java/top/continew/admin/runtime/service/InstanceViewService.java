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

/**
 * 审批实例查看资格业务
 *
 * <p>
 * 实例详情（{@code GET /admin/approval/instance/{id}}）与 Warm-Flow 原生实例运行图
 * （{@code GET /warm-flow/query-flow-chart/{id}}）必须使用同一套可见性规则：发起人、
 * 当前办理人、历史办理人可看，其他登录用户拒绝。两个入口抽取到此处统一判断，
 * 避免日后规则分叉。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25
 */
public interface InstanceViewService {

    /**
     * 断言当前用户可查看该实例，不可查看时抛出异常。
     *
     * <p>
     * 实例或快照不存在时同样抛异常，避免通过错误信息区分「不存在」与「无权限」。
     * </p>
     *
     * @param instanceId 实例 ID
     * @param userId 当前用户 ID
     */
    void checkViewable(Long instanceId, Long userId);
}
