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

package top.continew.admin.open.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用审批回调配置响应参数
 *
 * @author Claude Code
 * @since 2026/10/5
 */
@Data
@Schema(description = "应用审批回调配置响应参数")
public class AppCallbackConfigResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联应用 ID
     */
    @Schema(description = "关联应用 ID", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    /**
     * 回调地址
     */
    @Schema(description = "回调地址，为空表示不通知", example = "http://localhost:50064/approval/callback")
    private String callbackUrl;

    /**
     * 回调开关
     */
    @Schema(description = "回调开关（1：启用；2：禁用）", example = "1")
    private Integer callbackStatus;

    /**
     * 是否已配置
     */
    @Schema(description = "是否已配置（false：无扩展记录，展示默认关闭状态）", example = "false")
    private Boolean configured;
}
