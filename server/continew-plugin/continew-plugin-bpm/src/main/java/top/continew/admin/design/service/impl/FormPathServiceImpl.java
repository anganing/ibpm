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

import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.Tree;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.service.FormService;
import org.dromara.warm.flow.ui.service.FormPathService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * 表单路径服务实现
 *
 * <p>
 * 向流程设计器提供每个编码最新的已发布表单选项，使 query-def 的 formPathList 返回真实表单 ID。
 * 仅返回扁平节点，由 Warm-Flow 的 TreeUtil.buildTree 组装层级，此处不自行构建树。
 * </p>
 *
 * @author Claude Code
 * @since 2026/9/23 20:30
 */
@Service
@RequiredArgsConstructor
public class FormPathServiceImpl implements FormPathService {

    private final FormService formService;

    @Override
    public List<Tree> queryFormPath() {
        List<Form> formList = formService.list(FlowEngine.newForm()
            .setIsPublish(PublishStatus.PUBLISHED.getKey()));
        Map<String, Form> latestPublishedForms = new LinkedHashMap<>();
        Comparator<Form> versionComparator =
            Comparator.comparingInt(form -> Integer.parseInt(form.getVersion()));
        for (Form form : formList) {
            // Warm-Flow 按同编码最大数字版本 + 1 生成版本；先筛已发布，再比较数字版本。
            latestPublishedForms.merge(form.getFormCode(), form,
                BinaryOperator.maxBy(versionComparator));
        }
        return latestPublishedForms.values().stream().map(form -> {
            Tree tree = new Tree();
            // 流程定义的 formPath 存的就是表单 ID 字符串，故此处以 ID 作为节点值
            tree.setId(String.valueOf(form.getId()));
            tree.setName("%s（%s · v%s）".formatted(form.getFormName(), form.getFormCode(),
                form.getVersion()));
            return tree;
        }).toList();
    }
}
