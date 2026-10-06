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

package top.continew.admin.runtime.model.req;

/**
 * 待办、已办、我的流程共用的分页查询条件
 *
 * <p>
 * 所有条件都只在当前登录用户已限定的数据范围内生效，不能借由条件扩大可见范围。
 * </p>
 *
 * @param flowName   流程名称，模糊匹配，可为空
 * @param businessId 业务单据 ID，精确匹配，可为空
 * @param instanceId 流程实例 ID，按字符串精确匹配，避免雪花 ID 经数值转换丢失精度，可为空
 * @param flowStatus 流程状态，精确匹配，取值为 Warm-Flow 引擎的 flow_status，可为空
 * @param page       页码，从 1 开始
 * @param size       每页数量
 * @author Codex
 * @since 2026/9/26
 */
public record TaskPageReq(String flowName, String businessId, String instanceId,
    String flowStatus, int page, int size) {
}
