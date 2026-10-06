-- liquibase formatted sql

-- changeset bpm:8
-- comment 新增流程暂停标记表，承载宿主侧的「暂停/恢复」状态
-- 背景：Warm-Flow 的 activity_status 同时被引擎用于「拒绝新实例发起」（InsServiceImpl#start）
--       与「拒绝在途实例的任务办理」（TaskServiceImpl#judgeActivityStatus），
--       用它做暂停会连带冻结在途审批任务，违反「暂停只控制新实例发起」的产品规则。
--       故暂停状态由宿主侧独立保存，引擎完全不感知。
-- 粒度：按 definition_id（当前生效的流程定义 ID）。暂停只作用于生效版本，
--       新复制的版本是新 ID，天然不继承暂停标记；发布切换生效版本后旧标记自动失效。
CREATE TABLE "bpm_process_pause"
(
    "id"            int8         NOT NULL,
    "definition_id" int8         NOT NULL,
    "paused"        int2         NOT NULL DEFAULT 0,
    "create_user"   int8         NOT NULL,
    "create_time"   timestamp    NOT NULL,
    "update_user"   int8         DEFAULT NULL,
    "update_time"   timestamp    DEFAULT NULL,
    "deleted"       int8         NOT NULL DEFAULT 0,
    CONSTRAINT "bpm_process_pause_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_bpm_process_pause_definition_id_deleted" ON "bpm_process_pause" ("definition_id", "deleted");
CREATE INDEX "idx_bpm_process_pause_create_user" ON "bpm_process_pause" ("create_user");
CREATE INDEX "idx_bpm_process_pause_update_user" ON "bpm_process_pause" ("update_user");
CREATE INDEX "idx_bpm_process_pause_deleted" ON "bpm_process_pause" ("deleted");
COMMENT ON TABLE "bpm_process_pause" IS '流程暂停标记表';
COMMENT ON COLUMN "bpm_process_pause"."id" IS 'ID';
COMMENT ON COLUMN "bpm_process_pause"."definition_id" IS '流程定义 ID（当前生效版本）';
COMMENT ON COLUMN "bpm_process_pause"."paused" IS '是否暂停（0：否；1：是）';
COMMENT ON COLUMN "bpm_process_pause"."create_user" IS '创建人';
COMMENT ON COLUMN "bpm_process_pause"."create_time" IS '创建时间';
COMMENT ON COLUMN "bpm_process_pause"."update_user" IS '修改人';
COMMENT ON COLUMN "bpm_process_pause"."update_time" IS '修改时间';
COMMENT ON COLUMN "bpm_process_pause"."deleted" IS '是否已删除（0：否；id：是）';
