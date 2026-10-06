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

package top.continew.admin.common.api.bpm;

import top.continew.admin.common.api.bpm.model.AppCallbackConfigDTO;
import top.continew.admin.common.enums.DisEnableStatusEnum;

/**
 * 应用审批回调配置跨模块读写接口
 *
 * <p>
 * 回调配置保存在 BPM 插件的 {@code bpm_app_ext} 表（按 {@code app_id} 关联应用），
 * 而配置入口位于应用管理页面（open 插件），遵循「内部 API 解耦」模式：调用方依赖本接口，
 * 由 BPM 插件提供实现。
 * </p>
 *
 * @author Claude Code
 * @since 2026/10/5
 */
public interface AppCallbackConfigApi {

    /**
     * 按应用 ID 查询审批回调配置
     *
     * @param appId 应用 ID
     * @return 回调配置；无扩展记录时返回 {@code null}，表示未配置
     */
    AppCallbackConfigDTO getByAppId(Long appId);

    /**
     * 保存应用审批回调配置：按 {@code app_id} 查询未删除记录，存在则更新，不存在则创建
     *
     * <p>调用方须在事务内锁定应用记录，锁持续到配置写入完成，避免首次保存并发插入。</p>
     *
     * @param appId          应用 ID，必须为已存在的应用
     * @param callbackStatus 回调开关（1：启用；2：禁用）
     * @param callbackUrl    回调地址，启用时必填，非空时须为合法 HTTP/HTTPS 地址
     */
    void save(Long appId, DisEnableStatusEnum callbackStatus, String callbackUrl);
}
