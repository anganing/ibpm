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

import top.continew.admin.design.model.req.ProcessReq;
import top.continew.admin.design.model.resp.ProcessIntegrationResp;
import top.continew.admin.design.model.resp.ProcessPageResp;
import top.continew.admin.design.model.resp.ProcessVersionResp;

import java.util.List;

/**
 * 流程定义设计管理业务接口
 *
 * <p>
 * 宿主侧的流程定义管理能力：分页列表、创建草稿、设计定位、发布、暂停/恢复、版本记录、删除。
 * 流程的绘制与保存由 Warm-Flow 内置设计器完成。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/24 00:30
 */
public interface ProcessDesignService {

    /**
     * 分页查询流程定义
     *
     * @param flowName 流程名称（模糊匹配，可为空）
     * @param flowCode 流程编码（模糊匹配，可为空）
     * @param category 流程分类编码（精确匹配，可为空）
     * @param isPublish 发布状态（0未发布 1已发布 9失效，可为空）
     * @param page     页码
     * @param size     每页条数
     * @return 分页结果
     */
    ProcessPageResp page(String flowName, String flowCode, String category, Integer isPublish,
        int page, int size);

    /**
     * 创建流程定义草稿
     *
     * <p>
     * 设计器只能按 URL 中的 id 定位流程，因此新增流程必须先在宿主侧建好草稿记录并拿到 ID。
     * </p>
     *
     * @param req 请求参数
     * @return 新流程定义 ID
     */
    Long create(ProcessReq req);

    /**
     * 删除流程定义
     *
     * <p>
     * 仅允许物理删除未发布且无流程实例的定义，其余情况拒绝并说明原因。
     * </p>
     *
     * @param id 流程定义 ID
     */
    void delete(Long id);

    /**
     * 定位设计器应打开的定义 ID
     *
     * <p>
     * 未发布草稿直接返回自身 ID（保存后仍更新该版本）；已发布定义则复制出同流程编码的
     * 下一版本未发布草稿并返回其 ID，原已发布定义的定义、节点、连线与表单绑定保持不变。
     * 已存在<b>版本号高于来源</b>的未发布草稿时复用该草稿，重复调用不会不断产生新版本；
     * 被更高版本超越的旧草稿属历史版本，不会被打开或复用。
     * </p>
     *
     * @param id 流程定义 ID，未发布时须为该编码的最新版本
     * @return 设计器应打开的定义 ID
     */
    Long design(Long id);

    /**
     * 暂停流程定义
     *
     * <p>
     * 只作用于当前生效版本，只拦截新实例发起；已有实例与在途审批任务不受影响。
     * </p>
     *
     * @param id 流程定义 ID，必须为当前生效版本
     */
    void pause(Long id);

    /**
     * 恢复流程定义
     *
     * <p>
     * 只作用于当前生效版本；恢复前校验其仍绑定有效的已发布表单。
     * </p>
     *
     * @param id 流程定义 ID，必须为当前生效版本
     */
    void resume(Long id);

    /**
     * 查询某定义所属流程编码下的全部版本记录
     *
     * @param id 流程定义 ID
     * @return 版本记录（按版本号倒序）
     */
    List<ProcessVersionResp> versions(Long id);

    /**
     * 发布流程定义
     *
     * <p>
     * 仅允许同编码下的最新未发布草稿发布；已失效（9）的历史定义不允许重新发布。
     * </p>
     *
     * @param id 流程定义 ID
     */
    void publish(Long id);

    /**
     * 查询第三方对接说明：当前生效定义及其绑定的已发布表单
     *
     * <p>
     * 只读接口。选版复用发起链路的判定（生效版本 + 激活 + 绑定已发布表单 + 未暂停），
     * 因此主列表展示最新草稿时仍返回真正用于发起的那一版；任一条不满足即拒绝并说明原因，
     * 由调用方原样展示，不生成冒充可用的示例。
     * </p>
     *
     * @param id 流程定义 ID（同编码的任一行均可，按 flowCode 定位生效版本）
     * @return 流程信息与表单内容
     */
    ProcessIntegrationResp integration(Long id);
}
