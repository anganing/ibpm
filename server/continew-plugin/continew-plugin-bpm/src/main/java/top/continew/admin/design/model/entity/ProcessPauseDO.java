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
 * 流程暂停标记
 *
 * <p>
 * 宿主侧的「暂停/恢复」状态，按<b>流程定义 ID</b>（当前生效版本）存储，引擎完全不感知。
 * 不使用 Warm-Flow 的 {@code activity_status}：该字段同时被引擎用于拒绝新实例发起
 * （{@code InsServiceImpl#start}）与拒绝<b>在途实例</b>的任务办理
 * （{@code TaskServiceImpl#judgeActivityStatus}），用它做暂停会连带冻结在途审批任务。
 * </p>
 *
 * <p>
 * 粒度选择 {@code definition_id} 而非 {@code flow_code}：暂停只作用于生效版本，
 * 复制出的新版本是新 ID，天然不继承暂停标记，符合「暂停不随新版本继承」的产品规则。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
@Data
@TableName("bpm_process_pause")
public class ProcessPauseDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流程定义 ID（当前生效版本）
     */
    private Long definitionId;

    /**
     * 是否暂停（0：否；1：是）
     */
    private Integer paused;
}
