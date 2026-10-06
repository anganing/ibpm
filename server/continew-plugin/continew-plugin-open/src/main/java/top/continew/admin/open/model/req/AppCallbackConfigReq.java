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

package top.continew.admin.open.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用审批回调配置保存请求参数
 *
 * <p>
 * 只开放回调开关与回调地址两个字段：应用 ID 取自路径，主键、审计字段与逻辑删除
 * 均由服务端生成或填充，不接受客户端提交。
 * </p>
 *
 * @author Claude Code
 * @since 2026/10/5
 */
@Data
@Schema(description = "应用审批回调配置保存请求参数")
public class AppCallbackConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 回调开关
     */
    @Schema(description = "回调开关（1：启用；2：禁用）", example = "1")
    @NotNull(message = "回调开关不能为空")
    private DisEnableStatusEnum callbackStatus;

    /**
     * 回调地址
     */
    @Schema(description = "回调地址，启用时必填，非空时须为合法 HTTP/HTTPS 地址，禁用时可保留原地址",
        example = "http://localhost:50064/approval/callback")
    @Length(max = 500, message = "回调地址长度不能超过 {max} 个字符")
    private String callbackUrl;
}
