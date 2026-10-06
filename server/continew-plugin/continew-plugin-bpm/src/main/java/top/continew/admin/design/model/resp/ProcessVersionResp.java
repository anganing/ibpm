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

package top.continew.admin.design.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Date;

/**
 * 流程定义版本记录行
 *
 * <p>
 * 供「版本记录」只读入口展示同一流程编码下的全部版本。历史版本不会被删除，
 * 其中 {@code effective} 标识当前生效版本（与实际发起链路同源判定）。
 * </p>
 *
 * @param id             流程定义 ID
 * @param flowCode       流程编码
 * @param flowName       流程名称
 * @param version        版本号
 * @param isPublish      是否发布（0未发布 1已发布 9失效）
 * @param activityStatus 激活状态（0挂起 1激活）
 * @param updateTime     更新时间
 * @param effective      是否为当前生效版本
 * @param paused         该流程编码是否已被暂停（宿主侧标记）
 * @author Claude Code
 * @since 2026/9/26
 */
public record ProcessVersionResp(@JsonSerialize(using = ToStringSerializer.class) Long id,
    String flowCode, String flowName, String version, Integer isPublish, Integer activityStatus,
    Date updateTime, Boolean effective, Boolean paused) {
}
