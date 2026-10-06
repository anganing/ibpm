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

package top.continew.admin.runtime.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.continew.starter.core.constant.OrderedConstants;

/**
 * BPM 运行时 Web MVC 配置
 *
 * <p>
 * 注册 {@link WarmFlowReadPermissionInterceptor}，收口 Warm-Flow 插件原生的只读接口。
 * 这里不设置 excludePathPatterns：拦截器自身按 URI 精确匹配三个只读入口，
 * 其余请求原样放行，避免通配符书写失误导致漏放行或误拦截。
 * </p>
 *
 * <p>
 * 顺序必须晚于认证拦截器（{@link OrderedConstants.Interceptor#AUTH_INTERCEPTOR}），
 * 因为本拦截器依赖 Sa-Token 已完成登录校验后才能取得当前用户。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Configuration
@RequiredArgsConstructor
public class BpmWebMvcConfiguration implements WebMvcConfigurer {

    private final WarmFlowReadPermissionInterceptor warmFlowReadPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(warmFlowReadPermissionInterceptor)
            .order(OrderedConstants.Interceptor.AUTH_INTERCEPTOR + 1);
    }
}
