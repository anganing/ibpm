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

package top.continew.admin.design.service;

import top.continew.admin.design.model.req.FormReq;
import top.continew.admin.design.model.resp.FormPageResp;

import java.util.Map;

/**
 * 表单设计管理业务接口
 *
 * <p>
 * 提供宿主侧的表单管理能力：创建基础记录、分页查询、设计入口、发布、删除。
 * 表单内容的编辑由 Warm-Flow 内置设计器（type=form&amp;id=）完成。
 * 表单只有「未发布 / 已发布」两种业务状态，不提供停用。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/23 20:30
 */
public interface FormDesignService {

    /**
     * 创建未发布的内置表单记录
     *
     * @param req 表单创建请求参数
     * @return 表单 ID
     */
    Long create(FormReq req);

    /**
     * 分页查询表单列表
     *
     * @param formName  表单名称（模糊查询，可为空）
     * @param formCode  表单编码（模糊查询，可为空）
     * @param isPublish 是否发布（可为空）
     * @param page      页码
     * @param size      每页条数
     * @return 表单分页列表
     */
    FormPageResp page(String formName, String formCode, Integer isPublish, int page, int size);

    /**
     * 取得「设计」入口应打开的表单 ID
     *
     * <p>
     * 未发布表单直接返回自身 ID；已发布表单返回同 {@code formCode} 的新版本 ID：
     * 若已存在待设计的未发布版本则复用该草稿，否则复制来源表单生成新版本。
     * 新版本号由 Warm-Flow（{@code FormService#save} 的版本规则）持久化，不由调用方指定。
     * </p>
     *
     * @param id 被点击「设计」的表单 ID
     * @return 设计器应打开的表单 ID
     */
    Long design(Long id);

    /**
     * 读取表单内容（表单定义，非表单填写数据），供只读预览使用
     *
     * @param id 表单 ID
     * @return 已解析的表单内容（含 rule、option）
     */
    Map<String, Object> content(Long id);

    /**
     * 发布表单
     *
     * @param id 表单 ID
     */
    void publish(Long id);

    /**
     * 删除表单
     *
     * @param id 表单 ID
     */
    void delete(Long id);
}
