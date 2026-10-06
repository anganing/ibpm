-- liquibase formatted sql

-- changeset bpm:6
-- comment 修正 bpm:5 的菜单展示顺序与三个个人页面权限码
-- 顺序依据 docs/bpm-优化.md §2.1：流程模型、流程分类、流程表单、我的待办、我的已办、我的流程、发起审批
-- 权限码依据 §4.2：个人页面为 bpm:approval:todo / bpm:approval:done / bpm:approval:mine
UPDATE `sys_menu` SET `sort` = 1 WHERE `id` = 10010;

UPDATE `sys_menu` SET `sort` = 2 WHERE `id` = 10030;

UPDATE `sys_menu` SET `sort` = 3 WHERE `id` = 10040;

UPDATE `sys_menu` SET `sort` = 4, `permission` = 'bpm:approval:todo' WHERE `id` = 10050;

UPDATE `sys_menu` SET `sort` = 5, `permission` = 'bpm:approval:done' WHERE `id` = 10060;

UPDATE `sys_menu` SET `sort` = 6, `permission` = 'bpm:approval:mine' WHERE `id` = 10070;

-- 10020 仅调整位置，权限码保持 bpm:approval:start
UPDATE `sys_menu` SET `sort` = 7 WHERE `id` = 10020;
