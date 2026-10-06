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

package top.continew.admin.design.config;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowDto;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import top.continew.starter.core.exception.BusinessException;

import java.lang.reflect.Type;

/**
 * Warm-Flow 表单内容保存接口（{@code POST /warm-flow/form-content}）的写入口径
 *
 * <p>
 * 该接口由设计器 iframe 直接调用，Warm-Flow 自身没有权限注解，只要 URL 上的 {@code id} 被改成
 * 已发布表单的主键，就能改写已发布版本的内容。前端「设计」按钮改为打开新版本 ID 不足以防护：
 * 请求可以绕过页面直接构造。因此这里在请求体反序列化之后、控制器执行之前强制校验：
 * </p>
 *
 * <ul>
 * <li>必须持有 {@code bpm:form:update}，仅有 {@code bpm:form:view} 不允许保存；</li>
 * <li>目标表单存在且处于已发布状态时直接拒绝，已发布内容只能通过复制新版本修改。</li>
 * </ul>
 *
 * <p>
 * 与 {@link WarmFlowSaveJsonPermissionAdvice} 同理，使用 {@link RequestBodyAdvice} 而不是
 * 拦截器：拦截器判定需要自行读取并消费请求体，容易破坏后续消息转换。
 * </p>
 *
 * @author DeepSeek Harness
 * @since 2026/9/26
 */
@RestControllerAdvice
public class WarmFlowFormContentSaveAdvice implements RequestBodyAdvice {

    /** 表单内容保存权限，与表单管理页「设计」按钮一致 */
    private static final String PERMISSION_UPDATE = "bpm:form:update";

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        return FlowDto.class.equals(methodParameter.getParameterType());
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
        Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
        MethodParameter parameter,
        Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        if (!(body instanceof FlowDto flowDto)) {
            return body;
        }
        StpUtil.checkPermission(PERMISSION_UPDATE);
        Long formId = flowDto.getId();
        // 与 WarmFlowService#saveFormContent 同源取数：走 FlowEngine 的 FormService，避免提前注入
        Form form = formId == null ? null : FlowEngine.formService().getById(formId);
        if (form == null) {
            throw new BusinessException("表单 [%s] 不存在，无法保存表单内容".formatted(formId));
        }
        if (PublishStatus.PUBLISHED.getKey().equals(form.getIsPublish())) {
            throw new BusinessException("表单 [%s] 已发布，不允许修改内容，请复制新版本后再设计".formatted(formId));
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage,
        MethodParameter parameter,
        Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
