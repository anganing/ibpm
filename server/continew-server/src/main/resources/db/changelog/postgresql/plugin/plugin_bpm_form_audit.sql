-- liquibase formatted sql

-- changeset bpm:2
-- comment 补齐 Warm-Flow 1.8.9 表单实体的创建人和更新人字段
ALTER TABLE flow_form
    ADD COLUMN create_by varchar(64) NULL DEFAULT '',
    ADD COLUMN update_by varchar(64) NULL DEFAULT '';

COMMENT ON COLUMN flow_form.create_by IS '创建人';
COMMENT ON COLUMN flow_form.update_by IS '更新人';
