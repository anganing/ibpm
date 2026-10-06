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

package top.continew.admin.runtime.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.service.HisTaskService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.service.UserService;
import org.springframework.stereotype.Service;
import top.continew.admin.runtime.mapper.FormSnapshotMapper;
import top.continew.admin.runtime.model.entity.FormSnapshotDO;
import top.continew.admin.runtime.service.InstanceViewService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 审批实例查看资格业务实现
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Service
@RequiredArgsConstructor
public class InstanceViewServiceImpl implements InstanceViewService {

    private final InsService insService;
    private final TaskService taskService;
    private final UserService userService;
    private final HisTaskService hisTaskService;
    private final FormSnapshotMapper formSnapshotMapper;

    @Override
    public void checkViewable(Long instanceId, Long userId) {
        if (instanceId == null) {
            throw new BusinessException("审批实例不存在");
        }
        Instance instance = requireInstance(instanceId);
        FormSnapshotDO snapshot = requireSnapshot(instanceId);
        if (!userId.equals(snapshot.getCreateUser()) && !canHandleInstance(instanceId, userId)
            && hisTaskService.getByInsId(instanceId).stream()
                .noneMatch(history -> userId.toString().equals(history.getApprover()))) {
            throw new BusinessException("无权查看该审批实例");
        }
    }

    private boolean canHandleInstance(Long instanceId, Long userId) {
        return taskService.getByInsId(instanceId).stream()
            .anyMatch(task -> !userService.listByProcessedBys(task.getId(), userId.toString(),
                UserType.APPROVAL.getKey()).isEmpty());
    }

    private Instance requireInstance(Long instanceId) {
        Instance instance = insService.getById(instanceId);
        if (instance == null) {
            throw new BusinessException("审批实例不存在");
        }
        return instance;
    }

    private FormSnapshotDO requireSnapshot(Long instanceId) {
        FormSnapshotDO snapshot =
            formSnapshotMapper.selectOne(new LambdaQueryWrapper<FormSnapshotDO>()
                .eq(FormSnapshotDO::getInstanceId, instanceId)
                .eq(FormSnapshotDO::getDeleted, 0L));
        if (snapshot == null) {
            throw new BusinessException("审批实例快照不存在");
        }
        return snapshot;
    }
}
