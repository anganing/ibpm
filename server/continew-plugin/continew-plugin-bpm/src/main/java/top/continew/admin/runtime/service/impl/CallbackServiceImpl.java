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
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.HisTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.design.mapper.AppExtMapper;
import top.continew.admin.design.model.entity.AppExtDO;
import top.continew.admin.runtime.mapper.FormSnapshotMapper;
import top.continew.admin.runtime.model.entity.FormSnapshotDO;
import top.continew.admin.runtime.model.message.AppCallbackMessage;
import top.continew.admin.runtime.service.CallbackSender;
import top.continew.admin.runtime.service.CallbackService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * 流程终态回调实现
 *
 * @author Claude Code
 * @since 2026/9/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackServiceImpl implements CallbackService {

    /**
     * 回调报文的完成时间格式
     */
    private static final DateTimeFormatter FINISH_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FormSnapshotMapper formSnapshotMapper;
    private final AppExtMapper appExtMapper;
    private final HisTaskService hisTaskService;
    private final CallbackSender callbackSender;

    @Override
    public void notifyIfFinished(Instance instance, Definition definition) {
        // 完成监听器在中间节点审批后也会触发，仅终态（flow_status=8）才回调
        if (!FlowStatus.FINISHED.getKey().equals(instance.getFlowStatus())) {
            return;
        }
        FormSnapshotDO snapshot =
            formSnapshotMapper.selectOne(new LambdaQueryWrapper<FormSnapshotDO>()
                .eq(FormSnapshotDO::getInstanceId, instance.getId())
                .eq(FormSnapshotDO::getDeleted, 0L));
        if (snapshot == null || snapshot.getAppId() == null) {
            // appId 为空即本系统内发起，无来源应用可回调
            return;
        }
        AppExtDO appExt = appExtMapper.selectOne(new LambdaQueryWrapper<AppExtDO>()
            .eq(AppExtDO::getAppId, snapshot.getAppId())
            .eq(AppExtDO::getDeleted, 0L));
        if (appExt == null || !StringUtils.hasText(appExt.getCallbackUrl())
            || !DisEnableStatusEnum.ENABLE.getValue().equals(appExt.getCallbackStatus())) {
            return;
        }
        // 终审记录同时提供审批动作与审批意见，一次查询取齐
        HisTask lastHandled = lastHandledTask(instance.getId());
        boolean approved = lastHandled == null
            || !SkipType.REJECT.getKey().equals(lastHandled.getSkipType());
        AppCallbackMessage message = new AppCallbackMessage(
            AppCallbackMessage.EVENT_PROCESS_FINISHED, instance.getId(), snapshot.getBusinessId(),
            definition.getFlowCode(), definition.getFlowName(), approved,
            instance.getFlowStatus(), FlowStatus.getValueByKey(instance.getFlowStatus()),
            lastHandled == null ? null : lastHandled.getMessage(),
            LocalDateTime.now().format(FINISH_TIME_FORMATTER));
        sendAfterCommit(message, appExt.getCallbackUrl());
    }

    /**
     * 在事务提交后发送。完成监听器执行时审批事务尚未提交，此刻发送会出现
     * 「第三方已收到回调，但本地事务随后回滚」的不一致；无事务时直接发送。
     */
    private void sendAfterCommit(AppCallbackMessage message, String url) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callbackSender.send(message, url);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                callbackSender.send(message, url);
            }
        });
    }

    /**
     * 取最近一次已办理记录。起始节点的 {@code node_type=0} 是发起时落的历史记录，
     * 不作为审批动作，与已办列表的过滤口径保持一致。
     */
    private HisTask lastHandledTask(Long instanceId) {
        return hisTaskService.getByInsId(instanceId).stream()
            .filter(history -> history.getNodeType() == null || history.getNodeType() != 0)
            .max(Comparator.comparingLong(HisTask::getId))
            .orElse(null);
    }
}
