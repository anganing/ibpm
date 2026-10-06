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

package top.continew.admin.runtime.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.api.open.AppApi;
import top.continew.admin.common.api.system.UserApi;
import top.continew.admin.runtime.model.req.OpenStartReq;
import top.continew.admin.runtime.model.resp.InstanceStartResp;
import top.continew.admin.runtime.model.resp.InstanceStatusResp;
import top.continew.admin.runtime.service.ApprovalRuntimeService;
import top.continew.admin.runtime.service.InstanceStartService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 第三方签名发起与状态查询接口
 *
 * @author Codex
 * @since 2026/9/24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/open/approval/instance")
public class OpenInstanceController {

    private final AppApi appApi;
    private final UserApi userApi;
    private final InstanceStartService instanceStartService;
    private final ApprovalRuntimeService approvalRuntimeService;

    /**
     * 验签应用发起流程。四个签名参数由拦截器校验，参数绑定保证不可走登录分支。
     *
     * @param accessKey 应用 Access Key
     * @param timestamp 签名时间戳
     * @param nonce     签名随机数
     * @param sign      签名
     * @param req       发起请求
     * @return 实例 ID
     */
    @PostMapping
    public InstanceStartResp start(@RequestParam String accessKey, @RequestParam String timestamp,
        @RequestParam String nonce, @RequestParam String sign,
        @Valid @RequestBody OpenStartReq req) {
        if (!userApi.isEnabled(req.initiatorUserId())) {
            throw new BusinessException("发起人不存在或已停用");
        }
        Long appId = appApi.requireActiveAppId(accessKey);
        return instanceStartService.start(req.flowCode(), req.formData(), req.initiatorUserId(),
            req.businessId(), appId);
    }

    /**
     * 只查询同一验签应用发起的实例状态。
     *
     * @param id        实例 ID
     * @param accessKey 应用 Access Key
     * @param timestamp 签名时间戳
     * @param nonce     签名随机数
     * @param sign      签名
     * @return 实例状态
     */
    @GetMapping("/{id}")
    public InstanceStatusResp status(@PathVariable Long id, @RequestParam String accessKey,
        @RequestParam String timestamp, @RequestParam String nonce,
        @RequestParam String sign) {
        Long appId = appApi.requireActiveAppId(accessKey);
        return approvalRuntimeService.openStatus(id, appId);
    }
}
