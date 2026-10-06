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
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import top.continew.starter.core.exception.BusinessException;

import java.lang.reflect.Type;
import java.util.Comparator;

/**
 * Warm-Flow 设计器写接口的权限与状态收口
 *
 * <p>
 * 设计器直接调用插件提供的 {@code POST /warm-flow/save-json}，该接口本身没有权限注解，
 * 其请求体反序列化后才知道是「新建」还是「修改」。若在 Sa-Token 拦截器里判定，就必须自行
 * 读取并消费请求体，容易破坏后续的消息转换；这里改用 {@link RequestBodyAdvice}，在
 * Spring 完成报文到对象的转换后、调用控制器之前按已解析的 {@link DefJson} 校验权限与状态。
 * </p>
 *
 * <p>
 * 权限口径与 {@link top.continew.admin.design.controller.ProcessDesignController} 保持一致：
 * 定义 ID 为空表示新建，要求 {@code bpm:designer:create}；ID 非空表示修改，
 * 要求 {@code bpm:designer:update}。仅有 {@code bpm:designer:view} 不允许保存。
 * </p>
 *
 * <p>
 * 在权限之后追加状态校验：目标定义已发布或已失效时拒绝写入；未发布草稿中也只有同编码的
 * <b>最新版本</b>可写（被更高版本超越的旧草稿属历史版本，只读保留）。已发布定义只能通过
 * {@code POST /admin/approval/process/{id}/design} 复制新版本后再设计，直接改写会破坏
 * 已发布内容与在途实例快照的一致性。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/25 12:30
 */
@RestControllerAdvice
public class WarmFlowSaveJsonPermissionAdvice implements RequestBodyAdvice {

    /** 未绑定流程定义 ID 时的保存视为新建 */
    private static final String PERMISSION_CREATE = "bpm:designer:create";

    /** 已绑定流程定义 ID 时的保存视为修改 */
    private static final String PERMISSION_UPDATE = "bpm:designer:update";

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        return DefJson.class.equals(methodParameter.getParameterType());
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
        Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
        MethodParameter parameter, Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof DefJson defJson) {
            Long definitionId = defJson.getId();
            // 权限先行：无权限者不得通过后续的错误信息探测定义是否存在或其状态
            StpUtil.checkPermission(definitionId == null ? PERMISSION_CREATE : PERMISSION_UPDATE);
            // 再校验目标状态：已发布/已失效的定义不允许被设计器直接改写。
            // 前端「修改」按钮改走 /design 复制新版本不是充分防护——请求可绕过页面直接构造。
            if (definitionId != null) {
                Definition definition = FlowEngine.defService().getById(definitionId);
                if (definition == null) {
                    throw new BusinessException("流程定义 [%s] 不存在，无法保存".formatted(definitionId));
                }
                Integer isPublish = definition.getIsPublish();
                if (PublishStatus.PUBLISHED.getKey().equals(isPublish)) {
                    throw new BusinessException(
                        "流程定义 [%s] 已发布，不允许直接改写，请通过「修改」生成新版本".formatted(definitionId));
                }
                if (PublishStatus.EXPIRED.getKey().equals(isPublish)) {
                    throw new BusinessException("流程定义 [%s] 已失效，不允许改写".formatted(definitionId));
                }
                // 未发布草稿中也只有「最新版本」可写：被更高版本超越的旧草稿属历史版本，只读保留。
                // 与 ProcessDesignServiceImpl 的 requireLatestVersion 同口径，避免绕过 /design 改写历史草稿。
                requireLatestVersion(definition);
            }
        }
        return body;
    }

    /**
     * 校验目标定义确为同编码下的最新版本
     *
     * <p>
     * 与 {@link top.continew.admin.design.service.impl.ProcessDesignServiceImpl} 的口径保持一致：
     * 按版本号数值比较（非字符串字典序），相同版本号时以更新时间、ID 逐级决胜。
     * </p>
     */
    private void requireLatestVersion(Definition definition) {
        Definition latest = FlowEngine.defService().list(FlowEngine.newDef()
            .setFlowCode(definition.getFlowCode())).stream().max(COMPARE_VERSION).orElse(null);
        if (latest == null || !latest.getId().equals(definition.getId())) {
            throw new BusinessException("流程定义 [%s] 不是流程 [%s] 的最新版本（当前最新为 [%s]），不允许保存"
                .formatted(definition.getId(), definition.getFlowCode(),
                    latest == null ? "无" : latest
                        .getId()));
        }
    }

    /**
     * 版本比较器：<b>升序</b>——版本号数值小者在前，相同版本号时依次以更新时间、ID 升序决胜
     *
     * <p>
     * 刻意保持升序，使 {@code max(...)} 的语义与直觉一致（取版本号最大者）；若定义为降序，
     * {@code max(...)} 会取到最低版本。与 ProcessDesignServiceImpl 的比较器同口径。
     * </p>
     */
    private static final Comparator<Definition> COMPARE_VERSION = Comparator
        .comparingInt((Definition item) -> {
            try {
                return Integer.parseInt(item.getVersion());
            } catch (NumberFormatException e) {
                return 0;
            }
        })
        .thenComparing(Definition::getUpdateTime,
            Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(Definition::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage,
        MethodParameter parameter,
        Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
