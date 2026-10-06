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

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.runtime.service.DefinitionReadService;
import top.continew.admin.runtime.service.InstanceViewService;
import top.continew.starter.core.exception.BusinessException;

/**
 * Warm-Flow 原生只读接口的对象级权限收口
 *
 * <p>
 * 插件自带的 {@code WarmFlowController} 对以下三个 GET 接口只做登录校验，
 * 拿到 ID 的任何登录用户都能读到流程结构与实例所处节点：
 * </p>
 *
 * <ul>
 * <li>{@code GET /warm-flow/query-flow-chart/{实例ID}} —— 与实例详情的可见范围一致</li>
 * <li>{@code GET /warm-flow/query-def/{定义ID}} —— 模型管理者，或可发起该定义的普通用户</li>
 * <li>{@code GET /warm-flow/query-def}（无 ID）—— 设计器「新增」入口，要求模型新建权限</li>
 * </ul>
 *
 * <p>
 * 本拦截器注册在第三方控制器之上：第三方 {@code @RequestMapping} 的请求同样经过
 * Spring MVC 拦截器链，因此设计器 iframe 发出的原生请求也受控。URL 上的
 * {@code disabled}、{@code onlyDesignShow} 只是前端渲染开关，不作为授权依据。
 * </p>
 *
 * <p>
 * 拒绝时直接抛出业务异常，由全局异常处理返回失败响应，不会把图数据写入响应体。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarmFlowReadPermissionInterceptor implements HandlerInterceptor {

    /** 实例运行图 */
    private static final String PATH_FLOW_CHART = "/warm-flow/query-flow-chart/";

    /** 定义预览（带 ID） */
    private static final String PATH_QUERY_DEF = "/warm-flow/query-def";

    /** 模型管理读取权限 */
    private static final String PERMISSION_DESIGNER_VIEW = "bpm:designer:view";

    /** 模型新建权限，也是设计器「新增」入口的要求 */
    private static final String PERMISSION_DESIGNER_CREATE = "bpm:designer:create";

    /** 普通发起权限 */
    private static final String PERMISSION_APPROVAL_START = "bpm:approval:start";

    private final InstanceViewService instanceViewService;
    private final DefinitionReadService definitionReadService;

    @Override
    public boolean preHandle(HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception {
        // 用解码后的分段路径匹配，与 handler 映射同一口径：PathPattern 按解码后的段值比对，
        // 若用原始 requestURI，编码写法（如 /warm-flow/%71uery-def/1）解码后会命中控制器
        // 却绕开此处的字面匹配（运行态已实测该绕过，故必须解码后再匹配）
        String uri = decodedPathWithinApplication(request);
        // 只收口 GET 读取；save-json 等写接口的现有权限规则保持不变
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (uri.startsWith(PATH_FLOW_CHART)) {
            checkFlowChart(uri);
            return true;
        }
        if (uri.equals(PATH_QUERY_DEF) || uri.startsWith(PATH_QUERY_DEF + "/")) {
            checkQueryDef(uri);
            return true;
        }
        return true;
    }

    /**
     * 拼接解码后的应用内路径（不含 context path）。{@code value()} 是未解码原始子串，
     * 必须逐段取 {@link PathContainer.PathSegment#valueToMatch()}——即 PathPattern
     * 映射比对所用的解码值——才是与 handler 映射一致的路径；%2F 等编码分隔符由
     * Tomcat 默认拒绝，不会引入段边界歧义。
     */
    private String decodedPathWithinApplication(HttpServletRequest request) {
        PathContainer path = ServletRequestPathUtils.getParsedRequestPath(request)
            .pathWithinApplication();
        StringBuilder decoded = new StringBuilder(path.value().length());
        for (PathContainer.Element element : path.elements()) {
            decoded.append(element instanceof PathContainer.PathSegment segment
                ? segment.valueToMatch()
                : element.value());
        }
        return decoded.toString();
    }

    /**
     * 实例运行图：与实例详情共用同一查看资格。
     */
    private void checkFlowChart(String uri) {
        Long instanceId = parseTrailingId(uri, PATH_FLOW_CHART);
        if (instanceId == null) {
            // 路径不含合法 ID，无法判定权限，一律拒绝而不是放行
            throw new BusinessException("审批实例不存在");
        }
        instanceViewService.checkViewable(instanceId, UserContextHolder.getUserId());
    }

    /**
     * 定义预览：模型管理者可读；否则须有发起权限，且该定义 ID 本身是当前可发起版本。
     */
    private void checkQueryDef(String uri) {
        // 无 ID：设计器「新增」入口
        if (uri.equals(PATH_QUERY_DEF)) {
            StpUtil.checkPermission(PERMISSION_DESIGNER_CREATE);
            return;
        }
        Long definitionId = parseTrailingId(uri, PATH_QUERY_DEF + "/");
        if (definitionId == null) {
            throw new BusinessException("流程定义不存在");
        }
        if (StpUtil.hasPermission(PERMISSION_DESIGNER_VIEW)) {
            return;
        }
        StpUtil.checkPermission(PERMISSION_APPROVAL_START);
        definitionReadService.checkAvailable(definitionId);
    }

    /**
     * 解析路径末尾的 ID；缺失或非法返回 null（由调用方拒绝），不回落到任何默认值。
     */
    private Long parseTrailingId(String uri, String prefix) {
        String raw = uri.substring(prefix.length());
        // 只接受单段 ID，多余路径段视为非法
        if (raw.isEmpty() || raw.indexOf('/') >= 0) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            log.warn("Warm-Flow 只读接口路径 ID 非法：{}", uri);
            return null;
        }
    }
}
