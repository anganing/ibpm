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
import top.continew.admin.runtime.model.resp.DefinitionFormResp;
import top.continew.admin.runtime.model.resp.DefinitionResp;

import java.util.List;

/**
 * 可发起流程及表单读取业务
 *
 * @author Codex
 * @since 2026/9/24
 */
public interface DefinitionReadService {

    /**
     * 查询可发起的流程定义。
     *
     * @param category 流程分类编码（分类表 code，可空；为空时不按分类过滤）
     * @return 已发布、激活且绑定已发布表单的流程
     */
    List<DefinitionResp> listAvailable(String category);

    /**
     * 读取可发起流程的表单。
     *
     * @param flowCode 流程编码
     * @return 表单内容
     */
    DefinitionFormResp getForm(String flowCode);

    /**
     * 获取可发起流程定义，供发起服务复用同一检查。
     *
     * @param flowCode 流程编码
     * @return 流程定义
     */
    Definition requireAvailable(String flowCode);

    /**
     * 断言该定义 ID 本身是当前可发起的版本，不可发起时抛出异常。
     *
     * <p>
     * 与 {@link #requireAvailable(String)} 的区别：此处按<b>定义 ID</b> 定位，
     * 只放行「已发布、激活、绑定已发布表单」的那一条记录。历史版本定义
     * （同 flowCode 的非当前版本）即使已发布也不放行，避免用旧版本结构预览。
     * </p>
     *
     * @param definitionId 定义 ID
     */
    void checkAvailable(Long definitionId);
}
