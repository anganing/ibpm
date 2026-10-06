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

package top.continew.admin.design.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.ui.dto.HandlerFunDto;
import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.dromara.warm.flow.ui.service.HandlerSelectService;
import org.dromara.warm.flow.ui.vo.HandlerSelectVo;
import org.springframework.stereotype.Service;
import top.continew.admin.common.api.system.UserApi;
import top.continew.admin.common.api.system.model.UserSimpleResp;

import java.util.Collections;
import java.util.List;

/**
 * 流程设计器办理人选择服务实现
 *
 * <p>
 * 本期只提供「用户」一种办理人类型，直接以用户 ID 作为入库主键，
 * 与 {@link top.continew.admin.design.handler.BpmPermissionHandler} 的 permissions() 返回值对应。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/24 00:50
 */
@Service
@RequiredArgsConstructor
public class BpmHandlerSelectServiceImpl implements HandlerSelectService {

    /**
     * 办理人类型：用户
     */
    private static final String HANDLER_TYPE_USER = "用户";

    private final UserApi userApi;

    @Override
    public List<String> getHandlerType() {
        return Collections.singletonList(HANDLER_TYPE_USER);
    }

    @Override
    public HandlerSelectVo getHandlerSelect(HandlerQuery query) {
        List<UserSimpleResp> userList = userApi.listSimple(query.getHandlerName());
        HandlerFunDto<UserSimpleResp> handlerFunDto = new HandlerFunDto<>(userList, userList.size())
            // 入库主键直接存用户 ID，避免与角色等类型混淆，无需再加前缀
            .setStorageId(user -> String.valueOf(user.getId()))
            .setHandlerCode(UserSimpleResp::getUsername)
            .setHandlerName(UserSimpleResp::getNickname)
            .setGroupName(user -> HANDLER_TYPE_USER);
        return getHandlerSelectVo(handlerFunDto);
    }
}
