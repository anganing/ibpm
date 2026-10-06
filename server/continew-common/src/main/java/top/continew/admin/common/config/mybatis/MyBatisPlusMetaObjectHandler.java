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

package top.continew.admin.common.config.mybatis;

import top.continew.admin.common.constant.GlobalConstants;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * MyBatis Plus 元对象处理器配置（插入或修改时自动填充）
 *
 * @author Charles7c
 * @since 2022/12/22 19:52
 */
public class MyBatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * 创建人
     */
    private static final String CREATE_USER = "createUser";
    /**
     * 创建时间
     */
    private static final String CREATE_TIME = "createTime";
    /**
     * 修改人
     */
    private static final String UPDATE_USER = "updateUser";
    /**
     * 修改时间
     */
    private static final String UPDATE_TIME = "updateTime";

    /**
     * 插入数据时填充
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        if (metaObject == null) {
            return;
        }
        Long createUser = UserContextHolder.getUserId();
        LocalDateTime createTime = LocalDateTime.now(GlobalConstants.DEFAULT_ZONE_ID);
        if (metaObject.getOriginalObject() instanceof BaseDO baseDO) {
            // 继承了 BaseDO 的类，填充创建信息字段
            baseDO.setCreateUser(ObjectUtil.defaultIfNull(baseDO.getCreateUser(), createUser));
            baseDO.setCreateTime(ObjectUtil.defaultIfNull(baseDO.getCreateTime(), createTime));
        } else {
            // 未继承 BaseDO 的类，如存在创建信息字段则进行填充
            this.fillFieldValue(metaObject, CREATE_USER, createUser, false);
            this.fillFieldValue(metaObject, CREATE_TIME, createTime, false);
        }
    }

    /**
     * 修改数据时填充
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject == null) {
            return;
        }
        Long updateUser = UserContextHolder.getUserId();
        LocalDateTime updateTime = LocalDateTime.now(GlobalConstants.DEFAULT_ZONE_ID);
        if (metaObject.getOriginalObject() instanceof BaseDO baseDO) {
            // 继承了 BaseDO 的类，填充修改信息
            baseDO.setUpdateUser(updateUser);
            baseDO.setUpdateTime(updateTime);
        } else {
            // 未继承 BaseDO 的类，根据类中拥有的修改信息字段进行填充，不存在修改信息字段不进行填充
            this.fillFieldValue(metaObject, UPDATE_USER, updateUser, true);
            this.fillFieldValue(metaObject, UPDATE_TIME, updateTime, true);
        }
    }

    /**
     * 填充字段值
     *
     * @param metaObject     元数据对象
     * @param fieldName      要填充的字段名
     * @param fillFieldValue 要填充的字段值
     * @param isOverride     如果字段值不为空，是否覆盖（true：覆盖；false：不覆盖）
     */
    private void fillFieldValue(MetaObject metaObject, String fieldName, Object fillFieldValue,
        boolean isOverride) {
        if (!metaObject.hasSetter(fieldName)) {
            return;
        }
        Object fieldValue = metaObject.getValue(fieldName);
        Object value = this.adaptToSetterType(metaObject, fieldName,
            fieldValue != null && !isOverride ? fieldValue : fillFieldValue);
        // 适配后仍需校验可赋值性：第三方实体可能存在非日期且类型不兼容的字段，此时保持原有的跳过行为
        if (this.isAssignable(metaObject, fieldName, value)) {
            setFieldValByName(fieldName, value, metaObject);
        }
    }

    /**
     * 将待填充值适配为目标字段 setter 的参数类型
     *
     * <p>
     * 第三方插件实体（如 Warm-Flow 的 FlowForm）将 createTime/updateTime 定义为 java.util.Date，
     * 与宿主 BaseDO 的 LocalDateTime 不一致，直接填充会因参数类型不匹配而失败。此处仅在目标类型为
     * java.util.Date 时按项目默认时区转换，其余类型原样返回，保持宿主既有填充行为不变。
     * </p>
     *
     * @param metaObject     元数据对象
     * @param fieldName      字段名
     * @param fillFieldValue 待填充值
     * @return 适配后的填充值
     */
    private Object adaptToSetterType(MetaObject metaObject, String fieldName,
        Object fillFieldValue) {
        if (fillFieldValue instanceof LocalDateTime localDateTime
            && Date.class.equals(metaObject.getSetterType(fieldName))) {
            return Date.from(localDateTime.atZone(GlobalConstants.DEFAULT_ZONE_ID).toInstant());
        }
        return fillFieldValue;
    }

    /**
     * 判断待填充值能否赋值给目标字段
     *
     * @param metaObject     元数据对象
     * @param fieldName      字段名
     * @param fillFieldValue 待填充值
     * @return 可以赋值返回 true，否则返回 false
     */
    private boolean isAssignable(MetaObject metaObject, String fieldName, Object fillFieldValue) {
        Class<?> setterType = metaObject.getSetterType(fieldName);
        return setterType.isInstance(fillFieldValue);
    }
}
