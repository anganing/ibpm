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
 * 流程定义分页列表行
 *
 * <p>
 * 主列表每行代表一个流程编码的<b>最新版本</b>（版本号最大者）。与之并列的
 * {@code effectiveId}/{@code effectiveVersion} 表示<b>当前生效版本</b>，即发起链路实际选中的那一版
 * （由 Warm-Flow 的 {@code getPublishByFlowCode} 决定）。两者可能不同：当最新版本还是未发布草稿时，
 * 上一已发布且激活的版本仍在生效、仍可发起。
 * </p>
 *
 * @param id               流程定义 ID（最新版本）
 * @param flowCode         流程编码
 * @param flowName         流程名称
 * @param modelValue       设计器模型（CLASSICS 经典 / MIMIC 仿钉钉）
 * @param category         流程分类编码
 * @param version          版本号（最新版本）
 * @param isPublish        是否发布（0未发布 1已发布 9失效）
 * @param activityStatus   激活状态（0挂起 1激活）
 * @param updateTime       更新时间
 * @param effectiveId      当前生效版本的定义 ID（无生效版本时为 null）
 * @param effectiveVersion 当前生效版本的版本号（无生效版本时为 null）
 * @param paused           当前生效版本是否已被暂停（宿主侧标记）
 * @author Claude Code
 * @since 2026/9/25
 */
public record ProcessResp(@JsonSerialize(using = ToStringSerializer.class) Long id,
    String flowCode, String flowName, String modelValue, String category, String version,
    Integer isPublish, Integer activityStatus, Date updateTime,
    @JsonSerialize(using = ToStringSerializer.class) Long effectiveId,
    String effectiveVersion, Boolean paused) {
}
