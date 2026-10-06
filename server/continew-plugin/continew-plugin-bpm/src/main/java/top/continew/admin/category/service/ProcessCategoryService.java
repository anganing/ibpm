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

package top.continew.admin.category.service;

import org.dromara.warm.flow.core.dto.Tree;
import top.continew.admin.category.model.req.ProcessCategoryReq;
import top.continew.admin.category.model.resp.ProcessCategoryResp;

import java.util.List;

/**
 * 流程分类业务接口
 *
 * @author Claude Code
 * @since 2026/9/25
 */
public interface ProcessCategoryService {

    /**
     * 查询全量分类（按 sort 升序，sort 相同时按 id 升序）
     *
     * <p>
     * 分类是流程模型页 / 发起审批页左树与设计器分类下拉的数据源，需一次性全量返回，故不分页。
     * </p>
     *
     * @return 分类列表
     */
    List<ProcessCategoryResp> list();

    /**
     * 查询分类树节点
     *
     * <p>
     * 供 Warm-Flow 设计器分类下拉使用。单级扁平结构，{@link Tree#getId()} 返回分类 {@code code}
     * 而非主键 {@code id}，以与 {@code flow_definition.category}（varchar）的类型和语义对齐。
     * </p>
     *
     * @return 分类树节点列表
     */
    List<Tree> queryCategory();

    /**
     * 创建分类
     *
     * @param req 请求参数
     * @return 新分类 ID
     */
    String create(ProcessCategoryReq req);

    /**
     * 修改分类
     *
     * @param id  分类 ID
     * @param req 请求参数
     */
    void update(Long id, ProcessCategoryReq req);

    /**
     * 删除分类
     *
     * @param id 分类 ID
     */
    void delete(Long id);
}
