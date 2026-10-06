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

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;

/**
 * 流程终态回调业务
 *
 * <p>
 * 回调地址按实例来源应用（{@code bpm_form_snapshot.app_id}）查 {@code bpm_app_ext}，
 * 因此仅第三方签名发起的流程会回调；本系统内发起的流程 {@code appId} 为 null，不回调。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
public interface CallbackService {

    /**
     * 实例到达终态时向来源应用发送回调，否则不做任何事。
     *
     * <p>
     * 判定为「终态」需同时满足：引擎状态为已完成（{@code flow_status=8}）。
     * 注意引擎在「审批通过走到结束节点」与「终态驳回」两种情况下都会置 8，
     * 二者的区分由报文中的 {@code approved} 字段承担。
     * </p>
     *
     * @param instance   流程实例
     * @param definition 流程定义
     */
    void notifyIfFinished(Instance instance, Definition definition);
}
