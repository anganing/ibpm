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

package top.continew.admin.design.handler;

import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.springframework.stereotype.Component;
import top.continew.admin.common.context.UserContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * BPM 办理人权限处理器
 *
 * <p>
 * 设计器中配置的办理人权限标识即用户 ID（见
 * {@link top.continew.admin.design.service.impl.BpmHandlerSelectServiceImpl}），
 * 因此当前登录用户的权限集合就是其用户 ID 字符串。引擎据此校验办理资格并产生待办。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/24 00:50
 */
@Component
public class BpmPermissionHandler implements PermissionHandler {

    @Override
    public List<String> permissions() {
        Long userId = UserContextHolder.getUserId();
        return userId == null ? Collections.emptyList()
            : Collections.singletonList(String.valueOf(userId));
    }

    @Override
    public String getHandler() {
        Long userId = UserContextHolder.getUserId();
        return userId == null ? null : String.valueOf(userId);
    }
}
