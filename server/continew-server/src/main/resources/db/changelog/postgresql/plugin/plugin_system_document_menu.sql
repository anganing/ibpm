-- liquibase formatted sql

-- changeset system:1
-- comment 将原「关于项目 - 接口文档」固定路由改为「系统管理」下的动态菜单，并授权给已有「系统管理」目录权限的角色
-- 背景：前端 web/src/router/route.ts 已删除 /about 整组固定路由（接口文档、更新日志、Arco Design 文档、开源地址）。
-- 落地方式：菜单挂在目录 1000（系统管理）下，component 指向 system/document/api，对应前端 src/views/system/document/api/index.vue。
-- 排序：先全量归一化 1000 目录下现有子菜单为 1..9，本菜单取 10，
--       确保它稳定排在「系统管理」所有现有可见菜单（含 sort=999 的系统配置）之后，
--       且不受其它环境历史排序值差异影响（全量 UPDATE 幂等）。
-- 权限：菜单不设 permission（与用户管理/角色管理等页面菜单一致，页面级权限由菜单可见性控制），
--       因此无需为各角色补功能权限码，只需在 sys_role_menu 中补菜单关联。
-- 授权范围：仅补「已拥有 1000 系统管理目录」的角色，遵循本项目初始化数据中 1000/1010 成对授权的既有模式
--           （见 main_data.sql 中 547888897925840927 同时拥有 1000 与 1010），
--           不会把菜单开放给未拥有系统管理目录的角色，也不会造成角色看不到可访问入口。
-- 注意：超级管理员（super_admin）在代码中按角色 ID 走全量菜单分支（MenuServiceImpl#listByRoleId），
--       不依赖 sys_role_menu，故此处无需为其插入关联。
-- ID 选择：1300。该段位在本表未启用（现有 id 分布于 1000-1255 与 2000+，最大 id 为 10070），
--         且与 1150 系统配置的子菜单段（1160/1170/1180/1190/1210/1230/1250）不冲突。
-- 方言差异：PostgreSQL 侧 is_external/is_cache/is_hidden 为 bool 类型，故用 true/false，
--           对应 MySQL 侧的 b'1'/b'0'；标识符一律使用双引号。
-- 幂等性：INSERT ... SELECT ... WHERE NOT EXISTS 保证重复执行不产生重复行
--         （sys_menu 的 (title,parent_id,deleted) 唯一索引在未限定时不可用于 ON CONFLICT DO UPDATE）。

-- 1. 归一化「系统管理」目录下现有子菜单的排序，使其连续且可预测
UPDATE "sys_menu" SET "sort" = 1 WHERE "id" = 1010;

UPDATE "sys_menu" SET "sort" = 2 WHERE "id" = 1030;

UPDATE "sys_menu" SET "sort" = 3 WHERE "id" = 1050;

UPDATE "sys_menu" SET "sort" = 4 WHERE "id" = 1070;

UPDATE "sys_menu" SET "sort" = 5 WHERE "id" = 1090;

UPDATE "sys_menu" SET "sort" = 6 WHERE "id" = 1110;

UPDATE "sys_menu" SET "sort" = 7 WHERE "id" = 1130;

UPDATE "sys_menu" SET "sort" = 8 WHERE "id" = 1140;

UPDATE "sys_menu" SET "sort" = 9 WHERE "id" = 1150;

-- 2. 新增「接口文档」菜单（系统管理目录下最后一项）
--    沿用本项目既有菜单格式：type=2、is_external/is_cache/is_hidden 取 false、create_user=1。
--    is_cache 取 true：沿用原固定路由中接口文档页面的 keepAlive: true。
INSERT INTO "sys_menu"
("id", "title", "parent_id", "type", "path", "name", "component", "redirect", "icon", "is_external", "is_cache", "is_hidden", "permission", "sort", "status", "create_user", "create_time")
SELECT 1300, '接口文档', 1000, 2, '/system/document/api', 'SystemDocumentApi', 'system/document/api', NULL, 'swagger', false, true, false, NULL, 10, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM (SELECT "id" FROM "sys_menu" WHERE "id" = 1300) AS "t");

-- 3. 为已拥有「系统管理」目录（1000）的角色补授该菜单，与各角色既有菜单集合合并
INSERT INTO "sys_role_menu" ("role_id", "menu_id")
SELECT "t"."role_id", 1300
FROM (SELECT DISTINCT "role_id" FROM "sys_role_menu" WHERE "menu_id" = 1000) AS "t"
WHERE NOT EXISTS (SELECT 1 FROM (SELECT "role_id" FROM "sys_role_menu" WHERE "menu_id" = 1300) AS "e"
                  WHERE "e"."role_id" = "t"."role_id");

-- changeset system:2
-- comment 修正 system:1 中接口文档菜单的 component 值（缺少 /index 后缀导致页面空白）
-- 故障现象：菜单能打开、面包屑与页签均正常显示「接口文档」，但内容区为空。
-- 根因：前端 src/router/asyncModules.ts 用 import.meta.glob('@/views/**/*.vue') 收集页面模块，
--       并把路径中的 /src/views/ 前缀与 .vue 后缀去掉后作为键。因此 src/views/system/document/api/index.vue
--       对应的键是 "system/document/api/index"，而 system:1 写入的 component 为 "system/document/api"，
--       查表落空后 transformComponentView 返回 undefined，Vue Router 报
--       "Record with path /system/document/api is either missing a component(s) or children property"，
--       该路由因此渲染空白。
-- 修正依据：本表所有 type=2 且 component <> 'Layout' 的菜单，component 一律以 /index 结尾
--           （如 system/user/index、system/config/site/index、bpm/model/index），1300 是唯一例外。
-- 注意：system:1 已在既有环境执行完毕，按不可重写历史变更集的约定，此处以新变更集做增量修正。
UPDATE "sys_menu" SET "component" = 'system/document/api/index' WHERE "id" = 1300;
