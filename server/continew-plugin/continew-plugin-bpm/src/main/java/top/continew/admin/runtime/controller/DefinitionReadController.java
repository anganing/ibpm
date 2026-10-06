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

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.runtime.model.resp.DefinitionFormResp;
import top.continew.admin.runtime.model.resp.DefinitionResp;
import top.continew.admin.runtime.service.DefinitionReadService;

import java.util.List;

/**
 * 发起流程时使用的定义与表单接口
 *
 * @author Codex
 * @since 2026/9/24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/approval/definition")
public class DefinitionReadController {

    private final DefinitionReadService definitionReadService;

    /**
     * 可发起流程列表。
     *
     * @param category 流程分类编码（可选，不传时不按分类过滤）
     * @return 流程列表
     */
    @GetMapping("/list")
    public List<DefinitionResp> list(@RequestParam(required = false) String category) {
        return definitionReadService.listAvailable(category);
    }

    /**
     * 读取已发布流程绑定的表单。
     *
     * @param flowCode 流程编码
     * @return 表单内容
     */
    @GetMapping("/{flowCode}/form")
    public DefinitionFormResp form(@PathVariable String flowCode) {
        return definitionReadService.getForm(flowCode);
    }
}
