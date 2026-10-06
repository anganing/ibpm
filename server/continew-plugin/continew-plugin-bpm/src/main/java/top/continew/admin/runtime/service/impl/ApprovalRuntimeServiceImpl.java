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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.HisTaskService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.runtime.mapper.FormSnapshotMapper;
import top.continew.admin.runtime.model.entity.FormSnapshotDO;
import top.continew.admin.runtime.model.req.TaskPageReq;
import top.continew.admin.runtime.model.resp.InstanceDetailResp;
import top.continew.admin.runtime.model.resp.InstanceStatusResp;
import top.continew.admin.runtime.model.resp.TaskActionResp;
import top.continew.admin.runtime.model.resp.TaskPageResp;
import top.continew.admin.runtime.model.resp.TaskResp;
import top.continew.admin.runtime.service.ApprovalRuntimeService;
import top.continew.admin.runtime.service.InstanceViewService;
import top.continew.starter.core.exception.BusinessException;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批实例查询和任务办理实现
 *
 * @author Codex
 * @since 2026/9/24
 */
@Service
@RequiredArgsConstructor
public class ApprovalRuntimeServiceImpl implements ApprovalRuntimeService {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final DefService defService;
    private final InsService insService;
    private final TaskService taskService;
    private final UserService userService;
    private final HisTaskService hisTaskService;
    private final FormSnapshotMapper formSnapshotMapper;
    private final ObjectMapper objectMapper;
    private final InstanceViewService instanceViewService;

    @Override
    public TaskPageResp pageTasks(Long userId, TaskPageReq query) {
        checkPage(query);
        List<Long> taskIds = userService.list(FlowEngine.newUser()
            .setProcessedBy(userId.toString())
            .setType(UserType.APPROVAL.getKey())).stream()
            .map(User::getAssociated)
            .distinct()
            .toList();
        if (taskIds.isEmpty()) {
            return new TaskPageResp(List.of(), 0);
        }
        List<Task> tasks = taskService.getByIds(taskIds);
        Map<Long, Definition> definitions = mapDefinitions(
            tasks.stream().map(Task::getDefinitionId).toList());
        Map<Long, Instance> instances = mapInstances(
            tasks.stream().map(Task::getInstanceId).toList());
        List<TaskResp> rows = tasks.stream()
            .map(task -> toTaskResp(task.getId(), task.getInstanceId(), task.getDefinitionId(),
                task.getNodeName(), task.getCreateTime(), definitions, instances))
            .toList();
        return filterAndPage(rows, query);
    }

    @Override
    public TaskPageResp pageDoneTasks(Long userId, TaskPageReq query) {
        checkPage(query);
        // 一条历史任务一行，不按实例去重；node_type=0 的起始记录不作为已办
        List<HisTask> histories = hisTaskService.list(FlowEngine.newHisTask()
            .setApprover(userId.toString()))
            .stream()
            .filter(history -> history.getNodeType() == null || history.getNodeType() != 0)
            .toList();
        if (histories.isEmpty()) {
            return new TaskPageResp(List.of(), 0);
        }
        Map<Long, Definition> definitions = mapDefinitions(
            histories.stream().map(HisTask::getDefinitionId).toList());
        Map<Long, Instance> instances = mapInstances(
            histories.stream().map(HisTask::getInstanceId).toList());
        List<TaskResp> rows = histories.stream()
            .map(history -> toTaskResp(history.getTaskId(), history.getInstanceId(),
                history.getDefinitionId(), history.getNodeName(), history.getUpdateTime(),
                definitions, instances))
            .toList();
        return filterAndPage(rows, query);
    }

    @Override
    public TaskPageResp pageMyInstances(Long userId, TaskPageReq query) {
        checkPage(query);
        List<Long> instanceIds = formSnapshotMapper
            .selectList(new LambdaQueryWrapper<FormSnapshotDO>()
                .eq(FormSnapshotDO::getCreateUser, userId)
                .eq(FormSnapshotDO::getDeleted, 0L)
                .orderByDesc(FormSnapshotDO::getCreateTime))
            .stream()
            .map(FormSnapshotDO::getInstanceId)
            .distinct()
            .toList();
        if (instanceIds.isEmpty()) {
            return new TaskPageResp(List.of(), 0);
        }
        Map<Long, Instance> instances = mapInstances(instanceIds);
        Map<Long, Definition> definitions = mapDefinitions(
            instances.values().stream().map(Instance::getDefinitionId).toList());
        List<TaskResp> rows = instances.values()
            .stream()
            .map(instance -> toTaskResp(null, instance.getId(), instance.getDefinitionId(),
                instance.getNodeName(), instance.getCreateTime(), definitions, instances))
            .toList();
        return filterAndPage(rows, query);
    }

    @Override
    public InstanceDetailResp detail(Long instanceId, Long userId) {
        instanceViewService.checkViewable(instanceId, userId);
        Instance instance = requireInstance(instanceId);
        FormSnapshotDO snapshot = requireSnapshot(instanceId);
        Definition definition = defService.getById(snapshot.getDefinitionId());
        return new InstanceDetailResp(instanceId, snapshot.getDefinitionId(),
            definition.getFlowCode(),
            definition.getFlowName(), snapshot.getBusinessId(), instance.getFlowStatus(),
            lastSkipType(instanceId), snapshot.getCreateUser(),
            parseJson(snapshot.getFormContentJson()),
            parseJson(snapshot.getFormDataJson()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskActionResp handle(Long taskId, Long userId, String message, boolean approve) {
        Task task = taskService.getById(taskId);
        if (task == null || userService.listByProcessedBys(taskId, userId.toString(),
            UserType.APPROVAL.getKey()).isEmpty()) {
            throw new BusinessException("当前用户没有该任务的办理资格");
        }
        Instance instance = approve ? taskService.pass(taskId, message, Map.of())
            : taskService.reject(taskId, message, Map.of());
        return new TaskActionResp(instance.getId(), instance.getFlowStatus(),
            lastSkipType(instance.getId()));
    }

    @Override
    public InstanceStatusResp openStatus(Long instanceId, Long appId) {
        FormSnapshotDO snapshot = requireSnapshot(instanceId);
        if (!Objects.equals(appId, snapshot.getAppId())) {
            throw new BusinessException("审批实例不存在");
        }
        Instance instance = requireInstance(instanceId);
        Definition definition = defService.getById(snapshot.getDefinitionId());
        return new InstanceStatusResp(instanceId, definition.getFlowCode(),
            snapshot.getBusinessId(),
            instance.getFlowStatus(), lastSkipType(instanceId));
    }

    /**
     * 条件只在已由当前用户限定的结果集上生效，筛选、排序、total 与分页共用同一结果集。
     */
    private TaskPageResp filterAndPage(List<TaskResp> rows, TaskPageReq query) {
        String flowName = trimToNull(query.flowName());
        String businessId = trimToNull(query.businessId());
        String instanceId = trimToNull(query.instanceId());
        String flowStatus = trimToNull(query.flowStatus());
        List<TaskResp> matched = rows.stream()
            .filter(row -> flowName == null
                || (row.flowName() != null && row.flowName().contains(flowName)))
            .filter(row -> businessId == null || businessId.equals(row.businessId()))
            .filter(row -> instanceId == null || (row.instanceId() != null
                && instanceId.equals(row.instanceId().toString())))
            .filter(row -> flowStatus == null || flowStatus.equals(row.flowStatus()))
            .sorted(Comparator.comparing(TaskResp::createTime,
                Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(TaskResp::instanceId,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(TaskResp::taskId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed())
            .toList();
        long total = matched.size();
        long offset = (long) (query.page() - 1) * query.size();
        if (offset >= total) {
            return new TaskPageResp(List.of(), total);
        }
        int to = (int) Math.min(total, offset + query.size());
        return new TaskPageResp(matched.subList((int) offset, to), total);
    }

    private void checkPage(TaskPageReq query) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 100) {
            throw new BusinessException("分页参数无效");
        }
    }

    /**
     * 列表行的节点与时间来自各自的真实来源（待办任务、历史任务、实例），
     * 业务单据 ID 与流程状态统一取实例，与实例详情读到的值同源。
     */
    private TaskResp toTaskResp(Long taskId, Long instanceId, Long definitionId, String nodeName,
        Date time, Map<Long, Definition> definitions, Map<Long, Instance> instances) {
        Definition definition = definitions.get(definitionId);
        Instance instance = instances.get(instanceId);
        return new TaskResp(taskId, instanceId, instance == null ? null : instance.getBusinessId(),
            definition == null ? null : definition.getFlowCode(),
            definition == null ? null : definition.getFlowName(), nodeName,
            instance == null ? null : instance.getFlowStatus(), time);
    }

    private Map<Long, Definition> mapDefinitions(Collection<Long> definitionIds) {
        List<Long> ids = definitionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return defService.getByIds(ids).stream().collect(Collectors
            .toMap(Definition::getId, Function.identity(), (first, second) -> first));
    }

    private Map<Long, Instance> mapInstances(Collection<Long> instanceIds) {
        List<Long> ids = instanceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return insService.getByIds(ids).stream().collect(Collectors
            .toMap(Instance::getId, Function.identity(), (first, second) -> first));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private String lastSkipType(Long instanceId) {
        return hisTaskService.getByInsId(instanceId).stream()
            .filter(history -> history.getNodeType() == null || history.getNodeType() != 0)
            .max(Comparator.comparingLong(HisTask::getId))
            .map(HisTask::getSkipType)
            .orElse(null);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, JSON_OBJECT);
        } catch (JsonProcessingException e) {
            throw new BusinessException("审批表单快照不是有效 JSON");
        }
    }
}
