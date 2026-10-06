-- liquibase formatted sql

-- changeset bpm:7
-- comment 为流程模型、流程分类、流程表单三个页面落地操作按钮权限码（sys_menu.type=3）
-- 依据 docs/bpm-优化.md §4.2：模型 4 个、分类 3 个、表单 4 个，共 11 个不同权限码。
--「停用」复用 update（ProcessDesignController/FormDesignController 的 /{id}/disable 均标注 update），不另建停用码。
-- 按钮菜单沿用本项目既有格式：name/path/component/redirect/icon 均为 NULL，is_external/is_cache/is_hidden 取 false，sort 自 1 递增。
-- 本变更集不插入任何 sys_role_menu 关联：模型与表单的普通角色管理权限尚未裁决。
INSERT INTO "sys_menu"
("id", "title", "parent_id", "type", "path", "name", "component", "redirect", "icon", "is_external", "is_cache", "is_hidden", "permission", "sort", "status", "create_user", "create_time")
VALUES
(10011, '新增', 10010, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:designer:create', 1, 1, 1, NOW()),
(10012, '修改', 10010, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:designer:update', 2, 1, 1, NOW()),
(10013, '删除', 10010, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:designer:delete', 3, 1, 1, NOW()),
(10014, '发布', 10010, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:designer:publish', 4, 1, 1, NOW()),
(10031, '新增', 10030, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:category:create', 1, 1, 1, NOW()),
(10032, '修改', 10030, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:category:update', 2, 1, 1, NOW()),
(10033, '删除', 10030, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:category:delete', 3, 1, 1, NOW()),
(10041, '新增', 10040, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:form:create', 1, 1, 1, NOW()),
(10042, '修改', 10040, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:form:update', 2, 1, 1, NOW()),
(10043, '删除', 10040, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:form:delete', 3, 1, 1, NOW()),
(10044, '发布', 10040, 3, NULL, NULL, NULL, NULL, NULL, false, false, false, 'bpm:form:publish', 4, 1, 1, NOW());
