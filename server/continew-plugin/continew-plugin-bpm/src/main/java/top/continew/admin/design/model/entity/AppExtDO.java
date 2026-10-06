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

package top.continew.admin.design.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.io.Serial;

/**
 * 第三方应用回调配置
 *
 * <p>
 * 1:1 关联能力开放的应用（{@code sys_app}），按应用维度配置流程终态回调地址。
 * 回调地址是该第三方系统的属性，配置一次即适用于其名下所有流程，
 * 因此粒度选择 {@code app_id} 而非流程定义。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
@Data
@TableName("bpm_app_ext")
public class AppExtDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联应用 ID
     */
    private Long appId;

    /**
     * 回调地址，为空表示不通知
     */
    private String callbackUrl;

    /**
     * 回调开关（1：启用；2：禁用）
     */
    private Integer callbackStatus;
}
