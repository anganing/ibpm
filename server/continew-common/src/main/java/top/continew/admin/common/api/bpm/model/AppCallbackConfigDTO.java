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

package top.continew.admin.common.api.bpm.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用审批回调配置
 *
 * <p>
 * {@code bpm_app_ext} 的跨模块只读视图，配置属于应用级：同一应用名下所有流程共用。
 * </p>
 *
 * @param appId          关联应用 ID
 * @param callbackUrl    回调地址，为空表示不通知
 * @param callbackStatus 回调开关（1：启用；2：禁用）
 * @author Claude Code
 * @since 2026/10/5
 */
public record AppCallbackConfigDTO(Long appId, String callbackUrl, Integer callbackStatus)
    implements
        Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
