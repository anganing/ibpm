-- liquibase formatted sql

-- changeset bpm:5
-- comment 收口 BPM 页面菜单结构与发起用户菜单授权
-- 1) 10010 改为“流程模型”，指向实际存在的 bpm/model/index
UPDATE `sys_menu`
SET `title`   = '流程模型',
    `path`    = '/bpm/model',
    `name`    = 'BpmModel',
    `component` = 'bpm/model/index'
WHERE `id` = 10010;

-- 2) 10020 改为“发起审批”，权限码由 bpm:approval:view 收口为 bpm:approval:start
UPDATE `sys_menu`
SET `title`      = '发起审批',
    `permission` = 'bpm:approval:start'
WHERE `id` = 10020;

-- 3) 新增五个页面菜单（均为 type=2），ID 与同级 sort 显式固定，避免与既有 10010/10020 冲突
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(10030, '流程分类', 10000, 2, '/bpm/category', 'BpmCategory', 'bpm/category/index', 'apps', b'0', b'0', b'0', 'bpm:category:view', 3, 1, 1, NOW()),
(10040, '流程表单', 10000, 2, '/bpm/form', 'BpmForm', 'bpm/form/index', 'file', b'0', b'0', b'0', 'bpm:form:view', 4, 1, 1, NOW()),
(10050, '我的待办', 10000, 2, '/bpm/todo', 'BpmTodo', 'bpm/todo/index', 'clock-circle', b'0', b'0', b'0', 'bpm:todo:view', 5, 1, 1, NOW()),
(10060, '我的已办', 10000, 2, '/bpm/done', 'BpmDone', 'bpm/done/index', 'check-circle-fill', b'0', b'0', b'0', 'bpm:done:view', 6, 1, 1, NOW()),
(10070, '我的流程', 10000, 2, '/bpm/mine', 'BpmMine', 'bpm/mine/index', 'user', b'0', b'0', b'0', 'bpm:mine:view', 7, 1, 1, NOW());

-- 4) 以“当前拥有 10020 的角色”为集合，补授分类只读页与三个个人页面；
--    仅补页面菜单（type=2），不含任何按钮行；分类页对这些角色只读（仅 view 权限码）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, rm.`tenant_id`
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` IN (10030, 10050, 10060, 10070)
WHERE rm.`menu_id` = 10020
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x
      WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`tenant_id` = rm.`tenant_id`
  );

-- 5) 兜底：确保这些角色也持有 10000 目录（正常已持有，此处仅防漏）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT rm.`role_id`, 10000, rm.`tenant_id`
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 10020
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x
      WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 10000 AND x.`tenant_id` = rm.`tenant_id`
  );
