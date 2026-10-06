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

package top.continew.admin.open.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.api.open.AppApi;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.open.model.entity.AppDO;
import top.continew.admin.open.service.AppService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 开放平台应用跨模块读取实现
 *
 * @author Codex
 * @since 2026/9/24
 */
@Service
@RequiredArgsConstructor
public class AppApiImpl implements AppApi {

    private final AppService appService;

    @Override
    public Long requireActiveAppId(String accessKey) {
        AppDO app = appService.getByAccessKey(accessKey);
        if (app == null || !DisEnableStatusEnum.ENABLE.equals(app.getStatus()) || app.isExpired()) {
            throw new BusinessException("应用无效或已停用");
        }
        return app.getId();
    }
}
