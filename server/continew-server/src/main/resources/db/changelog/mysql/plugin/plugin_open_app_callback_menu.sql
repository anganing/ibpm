-- liquibase formatted sql

-- changeset bpm:9
-- comment 新增「审批回调配置」按钮权限码（sys_menu.type=3），挂在应用管理菜单（7010）下
-- 背景：审批回调地址原先只能直接维护 bpm_app_ext 表；现于应用管理每行提供配置抽屉，
--       查询/保存接口为 GET/PUT /open/app/{id}/callback-config，前后端权限码统一为 open:app:callback。
-- 独立权限点而非复用 open:app:update：回调地址决定审批结果外发目标，授予粒度应比修改应用基础信息更细。
-- 按钮菜单沿用本项目既有格式：name/path/component/redirect/icon 均为 NULL，is_external/is_cache/is_hidden 取 0，sort 自 1 递增。
-- 本变更集不插入任何 sys_role_menu 关联：由管理员按需在角色管理中授权。
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(7019, '审批回调配置', 7010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'open:app:callback', 9, 1, 1, NOW());
