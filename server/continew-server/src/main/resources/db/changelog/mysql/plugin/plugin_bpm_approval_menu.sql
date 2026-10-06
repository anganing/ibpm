-- liquibase formatted sql

-- changeset bpm:3
-- comment 增加审批发起与待办入口，并赋予现有角色访问权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(10020, '审批工作台', 10000, 2, '/bpm/approval', 'BpmApproval', 'bpm/approval/index', 'check-circle', b'0', b'0', b'0', 'bpm:approval:view', 2, 1, 1, NOW());

UPDATE `sys_menu` SET `redirect` = '/bpm/approval' WHERE `id` = 10000;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT r.`id`, m.`id`, r.`tenant_id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`id` IN (10000, 10020)
WHERE r.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = r.`tenant_id`
  );
