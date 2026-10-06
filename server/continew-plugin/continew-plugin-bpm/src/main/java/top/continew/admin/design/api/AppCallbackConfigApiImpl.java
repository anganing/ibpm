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

package top.continew.admin.design.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.api.bpm.AppCallbackConfigApi;
import top.continew.admin.common.api.bpm.model.AppCallbackConfigDTO;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.design.service.AppCallbackConfigService;

/**
 * 应用审批回调配置跨模块读写实现
 *
 * @author Claude Code
 * @since 2026/10/5
 */
@Service
@RequiredArgsConstructor
public class AppCallbackConfigApiImpl implements AppCallbackConfigApi {

    private final AppCallbackConfigService appCallbackConfigService;

    @Override
    public AppCallbackConfigDTO getByAppId(Long appId) {
        return appCallbackConfigService.getByAppId(appId);
    }

    @Override
    public void save(Long appId, DisEnableStatusEnum callbackStatus, String callbackUrl) {
        appCallbackConfigService.save(appId, callbackStatus, callbackUrl);
    }
}
