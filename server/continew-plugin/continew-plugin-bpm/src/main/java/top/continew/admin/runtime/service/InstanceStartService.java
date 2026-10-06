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

import top.continew.admin.runtime.model.resp.InstanceStartResp;

import java.util.Map;

/**
 * 流程发起业务
 *
 * @author Codex
 * @since 2026/9/24
 */
public interface InstanceStartService {

    /**
     * 使用已发布的定义及表单发起流程，保存同一份表单数据快照。
     *
     * @param flowCode        流程编码
     * @param formData        表单数据
     * @param initiatorUserId 发起人 ID
     * @param businessId      业务单据 ID
     * @param appId           来源应用 ID，内部发起为 null
     * @return 实例 ID
     */
    InstanceStartResp start(String flowCode, Map<String, Object> formData, Long initiatorUserId,
        String businessId, Long appId);
}
