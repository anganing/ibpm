-- liquibase formatted sql

-- changeset bpm:2
-- comment 补齐 Warm-Flow 1.8.9 表单实体的创建人和更新人字段
ALTER TABLE `flow_form`
    ADD COLUMN `create_by` varchar(64) NULL DEFAULT '' COMMENT '创建人' AFTER `create_time`,
    ADD COLUMN `update_by` varchar(64) NULL DEFAULT '' COMMENT '更新人' AFTER `update_time`;
