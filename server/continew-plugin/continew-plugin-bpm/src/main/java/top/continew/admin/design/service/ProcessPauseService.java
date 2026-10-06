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

import java.util.Set;

/**
 * 流程暂停标记业务接口
 *
 * <p>
 * 「暂停/恢复」只作用于流程的<b>当前生效版本</b>（即实际发起链路选中的那一版），
 * 并且只拦截<b>新实例发起</b>；已有实例与在途审批任务不受影响。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/26
 */
public interface ProcessPauseService {

    /**
     * 判断给定流程定义是否处于暂停状态
     *
     * @param definitionId 流程定义 ID（当前生效版本）
     * @return 是否暂停
     */
    boolean isPaused(Long definitionId);

    /**
     * 批量查询处于暂停状态的流程定义 ID，供可发起列表一次性过滤
     *
     * @return 暂停中的流程定义 ID 集合
     */
    Set<Long> pausedDefinitionIds();

    /**
     * 暂停当前生效版本
     *
     * @param definitionId 流程定义 ID，必须为当前生效版本
     */
    void pause(Long definitionId);

    /**
     * 恢复当前生效版本
     *
     * @param definitionId 流程定义 ID，必须为当前生效版本
     */
    void resume(Long definitionId);
}
