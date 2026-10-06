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

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import top.continew.admin.runtime.model.message.AppCallbackMessage;

/**
 * 流程终态回调发送器
 *
 * <p>
 * 独立成类而非并入 {@link CallbackService}：{@code @Async} 依赖 Spring 代理生效，
 * 同类内部自调用会绕过代理而退化为同步执行，届时回调的 HTTP 往返将阻塞审批事务的
 * 提交后处理。
 * </p>
 *
 * <p>
 * 发送失败只记日志、不重试、不抛出：回调属于通知性质，第三方系统不可用不应影响
 * 本系统的审批结果，也不应让异步线程抛出无人接收的异常。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackSender {

    /**
     * 连接超时（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 5000;

    /**
     * 读取超时（毫秒）
     */
    private static final int READ_TIMEOUT = 5000;

    /**
     * 异步发送流程终态回调报文。
     *
     * @param message 回调报文
     * @param url     回调地址
     */
    @Async
    public void send(AppCallbackMessage message, String url) {
        try (HttpResponse response = HttpRequest.post(url)
            .body(JSONUtil.toJsonStr(message))
            .contentType("application/json; charset=utf-8")
            .setConnectionTimeout(CONNECT_TIMEOUT)
            .setReadTimeout(READ_TIMEOUT)
            .execute()) {
            if (!response.isOk()) {
                log.warn("流程终态回调失败：url={}，businessId={}，HTTP 状态码={}", url,
                    message.businessId(), response.getStatus());
                return;
            }
            log.info("流程终态回调成功：url={}，businessId={}，approved={}", url,
                message.businessId(), message.approved());
        } catch (IORuntimeException e) {
            log.warn("流程终态回调失败：url={}，businessId={}，原因={}", url, message.businessId(),
                e.getMessage());
        }
    }
}
