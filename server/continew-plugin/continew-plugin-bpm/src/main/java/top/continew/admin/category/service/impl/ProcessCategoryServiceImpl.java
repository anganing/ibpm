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

package top.continew.admin.category.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.Tree;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.category.mapper.ProcessCategoryMapper;
import top.continew.admin.category.model.entity.ProcessCategoryDO;
import top.continew.admin.category.model.req.ProcessCategoryReq;
import top.continew.admin.category.model.resp.ProcessCategoryResp;
import top.continew.admin.category.service.ProcessCategoryService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 流程分类业务实现
 *
 * @author Claude Code
 * @since 2026/9/25
 */
@Service
@RequiredArgsConstructor
public class ProcessCategoryServiceImpl implements ProcessCategoryService {

    private final ProcessCategoryMapper processCategoryMapper;

    @Override
    public List<ProcessCategoryResp> list() {
        return baseQuery().stream().map(category -> toResp(category)).toList();
    }

    @Override
    public List<Tree> queryCategory() {
        // 单级扁平：不填 parentId，框架的 TreeUtil.buildTree 会为每个节点落一个空的 children
        return baseQuery().stream()
            // 分类编码即设计器下拉的 value，也是 flow_definition.category 存的值
            .map(category -> new Tree().setId(category.getCode()).setName(category.getName()))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ProcessCategoryReq req) {
        String code = req.code().trim();
        checkCodeUnique(code, null);
        ProcessCategoryDO category = new ProcessCategoryDO();
        category.setName(req.name().trim());
        category.setCode(code);
        category.setSort(req.sort() == null ? 0 : req.sort());
        processCategoryMapper.insert(category);
        return String.valueOf(category.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProcessCategoryReq req) {
        ProcessCategoryDO category = getById(id);
        String code = req.code().trim();
        // code 是流程定义的引用键，已在使用时禁止改码，避免存量定义变成悬空引用
        if (!code.equals(category.getCode())
            && countReferencedDefinitions(category.getCode()) > 0) {
            throw new BusinessException("分类 [%s] 已被流程使用，不允许修改分类编码".formatted(category.getName()));
        }
        checkCodeUnique(code, id);
        category.setName(req.name().trim());
        category.setCode(code);
        category.setSort(req.sort() == null ? 0 : req.sort());
        processCategoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProcessCategoryDO category = getById(id);
        long referenced = countReferencedDefinitions(category.getCode());
        if (referenced > 0) {
            throw new BusinessException(
                "分类 [%s] 已被 %d 个流程使用，无法删除".formatted(category.getName(), referenced));
        }
        processCategoryMapper.deleteById(id);
    }

    private ProcessCategoryResp toResp(ProcessCategoryDO category) {
        return new ProcessCategoryResp(category.getId(), category.getName(), category.getCode(),
            category
                .getSort(),
            toDate(category.getCreateTime()));
    }

    private List<ProcessCategoryDO> baseQuery() {
        return processCategoryMapper.selectList(Wrappers.<ProcessCategoryDO>lambdaQuery()
            .orderByAsc(ProcessCategoryDO::getSort)
            .orderByAsc(ProcessCategoryDO::getId));
    }

    private ProcessCategoryDO getById(Long id) {
        ProcessCategoryDO category = processCategoryMapper.selectById(id);
        CheckUtils.throwIfNull(category, "分类不存在");
        return category;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = processCategoryMapper.selectCount(Wrappers.<ProcessCategoryDO>lambdaQuery()
            .eq(ProcessCategoryDO::getCode, code)
            .ne(excludeId != null, ProcessCategoryDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("分类编码 [%s] 已存在".formatted(code));
        }
    }

    /**
     * 统计引用了指定分类编码的流程定义数量
     *
     * <p>
     * 以 Warm-Flow 的 {@code DefService} 而非直连 {@code flow_definition} 查询，避免绕过引擎的租户与逻辑删除策略。
     * </p>
     *
     * @param code 分类编码
     * @return 引用的流程定义数量
     */
    private long countReferencedDefinitions(String code) {
        return FlowEngine.defService().list(FlowEngine.newDef().setCategory(code)).size();
    }

    private Date toDate(LocalDateTime dateTime) {
        return dateTime == null ? null
            : Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
