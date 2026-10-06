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

package top.continew.admin.design.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.continew.admin.common.api.bpm.model.AppCallbackConfigDTO;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.design.mapper.AppExtMapper;
import top.continew.admin.design.model.entity.AppExtDO;
import top.continew.admin.design.service.AppCallbackConfigService;
import top.continew.starter.core.exception.BusinessException;

import java.net.URI;
import java.util.List;

/**
 * 应用审批回调配置业务实现
 *
 * <p>
 * 写入 {@code bpm_app_ext}，唯一键为 {@code (app_id, deleted)}，一个应用只有一条
 * 有效扩展记录——该约束由表级唯一索引兜底，也是 {@code CallbackServiceImpl} 按
 * {@code app_id} 单行读取的前提。
 * </p>
 *
 * @author Claude Code
 * @since 2026/10/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppCallbackConfigServiceImpl implements AppCallbackConfigService {

    private final AppExtMapper appExtMapper;

    @Override
    public AppCallbackConfigDTO getByAppId(Long appId) {
        AppExtDO appExt = selectByAppId(appId);
        if (appExt == null) {
            return null;
        }
        return new AppCallbackConfigDTO(appExt.getAppId(), appExt.getCallbackUrl(), appExt
            .getCallbackStatus());
    }

    @Override
    public void save(Long appId, DisEnableStatusEnum callbackStatus, String callbackUrl) {
        if (callbackStatus == null) {
            throw new BusinessException("回调开关不能为空");
        }
        String trimmedUrl = StringUtils.hasText(callbackUrl) ? callbackUrl.trim() : null;
        // 启用时地址必填；非空地址须为合法 HTTP/HTTPS 地址，禁用时允许保留原地址
        if (DisEnableStatusEnum.ENABLE.equals(callbackStatus) && trimmedUrl == null) {
            throw new BusinessException("启用回调后必须填写回调地址");
        }
        if (trimmedUrl != null && !isValidHttpUrl(trimmedUrl)) {
            throw new BusinessException("回调地址必须是合法的 HTTP/HTTPS 地址");
        }
        writeCallbackConfig(appId, callbackStatus.getValue(), trimmedUrl);
    }

    /**
     * 校验是否为合法的 HTTP/HTTPS 地址
     *
     * <p>
     * 与回调发送端（Hutool HTTP POST）对地址的最基本要求保持一致：必须带
     * {@code http://} 或 {@code https://} 前缀且能解析出主机，允许 localhost 与合法端口。
     * </p>
     *
     * @param url 回调地址
     * @return 是否合法
     */
    private boolean isValidHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && StringUtils.hasText(uri.getHost()) && uri.getPort() <= 65535;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 写入回调配置；无记录则新建，有则更新
     */
    private void writeCallbackConfig(Long appId, Integer callbackStatus, String callbackUrl) {
        AppExtDO exist = selectByAppId(appId);
        if (exist == null) {
            AppExtDO record = new AppExtDO();
            record.setAppId(appId);
            record.setCallbackUrl(callbackUrl);
            record.setCallbackStatus(callbackStatus);
            record.setDeleted(0L);
            appExtMapper.insert(record);
            return;
        }
        // 显式 SET 支持清空地址；使用新实体触发审计填充，不回写旧审计值。
        appExtMapper.update(new AppExtDO(), new LambdaUpdateWrapper<AppExtDO>()
            .eq(AppExtDO::getId, exist.getId())
            .eq(AppExtDO::getAppId, appId)
            .eq(AppExtDO::getDeleted, 0L)
            .set(AppExtDO::getCallbackUrl, callbackUrl)
            .set(AppExtDO::getCallbackStatus, callbackStatus));
    }

    /**
     * 按应用 ID 查询未删除的扩展记录
     *
     * <p>
     * {@code deleted} 不是 MyBatis-Plus 逻辑删除字段（{@code BaseDO} 未标注
     * {@code @TableLogic}），须显式过滤；唯一键正常只有一行，用 limit 1 兜底历史脏数据。
     * </p>
     */
    private AppExtDO selectByAppId(Long appId) {
        List<AppExtDO> recordList = appExtMapper.selectList(new LambdaQueryWrapper<AppExtDO>()
            .eq(AppExtDO::getAppId, appId)
            .eq(AppExtDO::getDeleted, 0L)
            .last("LIMIT 1"));
        return recordList.isEmpty() ? null : recordList.get(0);
    }
}
