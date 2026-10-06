-- liquibase formatted sql

-- changeset bpm:8
-- comment 新增流程暂停标记表，承载宿主侧的「暂停/恢复」状态
-- 背景：Warm-Flow 的 activity_status 同时被引擎用于「拒绝新实例发起」（InsServiceImpl#start）
--       与「拒绝在途实例的任务办理」（TaskServiceImpl#judgeActivityStatus），
--       用它做暂停会连带冻结在途审批任务，违反「暂停只控制新实例发起」的产品规则。
--       故暂停状态由宿主侧独立保存，引擎完全不感知。
-- 粒度：按 definition_id（当前生效的流程定义 ID）。暂停只作用于生效版本，
--       新复制的版本是新 ID，天然不继承暂停标记；发布切换生效版本后旧标记自动失效。
CREATE TABLE `bpm_process_pause`
(
    `id`            bigint      NOT NULL COMMENT 'ID',
    `definition_id` bigint      NOT NULL COMMENT '流程定义 ID（当前生效版本）',
    `paused`        tinyint     NOT NULL DEFAULT 0 COMMENT '是否暂停（0：否；1：是）',
    `create_user`   bigint      NOT NULL COMMENT '创建人',
    `create_time`   datetime    NOT NULL COMMENT '创建时间',
    `update_user`   bigint      DEFAULT NULL COMMENT '修改人',
    `update_time`   datetime    DEFAULT NULL COMMENT '修改时间',
    `deleted`       bigint      NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_bpm_process_pause_definition_id_deleted` (`definition_id`, `deleted`),
    INDEX `idx_bpm_process_pause_create_user` (`create_user`),
    INDEX `idx_bpm_process_pause_update_user` (`update_user`),
    INDEX `idx_bpm_process_pause_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程暂停标记表';
