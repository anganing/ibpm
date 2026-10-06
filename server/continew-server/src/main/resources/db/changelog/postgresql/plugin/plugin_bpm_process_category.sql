-- liquibase formatted sql

-- changeset bpm:4
-- comment 新增流程分类表，作为 flow_definition.category 的引用源
CREATE TABLE "bpm_process_category" (
    "id"          int8         NOT NULL,
    "name"        varchar(100) NOT NULL,
    "code"        varchar(100) NOT NULL,
    "sort"        int4         NOT NULL DEFAULT 0,
    "create_user" int8         NOT NULL,
    "create_time" timestamp    NOT NULL,
    "update_user" int8         DEFAULT NULL,
    "update_time" timestamp    DEFAULT NULL,
    "deleted"     int8         NOT NULL DEFAULT 0,
    CONSTRAINT "bpm_process_category_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_bpm_process_category_code_deleted" ON "bpm_process_category" ("code", "deleted");
CREATE INDEX "idx_bpm_process_category_create_user" ON "bpm_process_category" ("create_user");
CREATE INDEX "idx_bpm_process_category_update_user" ON "bpm_process_category" ("update_user");
CREATE INDEX "idx_bpm_process_category_deleted" ON "bpm_process_category" ("deleted");
COMMENT ON TABLE "bpm_process_category" IS '流程分类表';
COMMENT ON COLUMN "bpm_process_category"."id" IS 'ID';
COMMENT ON COLUMN "bpm_process_category"."name" IS '分类名称';
COMMENT ON COLUMN "bpm_process_category"."code" IS '分类编码（流程定义以此引用分类）';
COMMENT ON COLUMN "bpm_process_category"."sort" IS '排序';
COMMENT ON COLUMN "bpm_process_category"."create_user" IS '创建人';
COMMENT ON COLUMN "bpm_process_category"."create_time" IS '创建时间';
COMMENT ON COLUMN "bpm_process_category"."update_user" IS '修改人';
COMMENT ON COLUMN "bpm_process_category"."update_time" IS '修改时间';
COMMENT ON COLUMN "bpm_process_category"."deleted" IS '是否已删除（0：否；id：是）';
