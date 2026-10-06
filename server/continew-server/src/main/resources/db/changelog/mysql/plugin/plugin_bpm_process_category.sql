-- liquibase formatted sql

-- changeset bpm:4
-- comment 新增流程分类表，作为 flow_definition.category 的引用源
CREATE TABLE `bpm_process_category` (
    `id`          bigint       NOT NULL COMMENT 'ID',
    `name`        varchar(100) NOT NULL COMMENT '分类名称',
    `code`        varchar(100) NOT NULL COMMENT '分类编码（流程定义以此引用分类）',
    `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_user` bigint       NOT NULL COMMENT '创建人',
    `create_time` datetime     NOT NULL COMMENT '创建时间',
    `update_user` bigint       DEFAULT NULL COMMENT '修改人',
    `update_time` datetime     DEFAULT NULL COMMENT '修改时间',
    `deleted`     bigint       NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_bpm_process_category_code_deleted` (`code`, `deleted`),
    INDEX `idx_bpm_process_category_create_user` (`create_user`),
    INDEX `idx_bpm_process_category_update_user` (`update_user`),
    INDEX `idx_bpm_process_category_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程分类表';
