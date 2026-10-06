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

package top.continew.admin.runtime.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.springframework.stereotype.Component;
import top.continew.admin.runtime.service.CallbackService;

/**
 * BPM 全局监听器：桥接 Warm-Flow 事件与终态回调
 *
 * <p>
 * 注册方式见 {@link top.continew.admin.design.handler.BpmPermissionHandler}：实现 Warm-Flow 的 SPI
 * 接口并声明为 Spring {@code @Component} 即可，引擎在未配置 {@code warm-flow.global-listener-path}
 * 时按类型从容器取 Bean，无需额外的 {@code @Configuration} 或 {@code @Bean}。
 * </p>
 *
 * <p>
 * 本类只负责在正确的时机（任务完成）把事件转交业务，终态判定与「提交后再发送」
 * 由 {@link CallbackService} 承担。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmGlobalListener implements GlobalListener {

    private final CallbackService callbackService;

    @Override
    public void finish(ListenerVariable listenerVariable) {
        try {
            callbackService.notifyIfFinished(listenerVariable.getInstance(),
                listenerVariable.getDefinition());
        } catch (Exception e) {
            // 监听器在审批事务内执行，回调的任何异常都不能让审批失败
            log.error("流程终态回调处理异常", e);
        }
    }
}
