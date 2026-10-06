-- 系统备份（需求 14-系统备份）：备份记录与恢复日志。本模块的表不参与备份和恢复。
CREATE TABLE bak_record (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    file_name          VARCHAR(128)  NOT NULL COMMENT '备份目录下的文件名',
    file_size          BIGINT        NOT NULL DEFAULT 0,
    sha256             VARCHAR(64)   NULL,
    backup_type        VARCHAR(16)   NOT NULL COMMENT 'MANUAL 手工 / AUTO 定时 / PRE_RESTORE 恢复前 / UPLOAD 上传',
    record_status      VARCHAR(16)   NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED',
    include_files      TINYINT       NOT NULL DEFAULT 0 COMMENT '是否包含附件',
    table_count        INT           NOT NULL DEFAULT 0,
    row_count          BIGINT        NOT NULL DEFAULT 0,
    attachment_count   INT           NOT NULL DEFAULT 0,
    schema_versions    VARCHAR(2000) NULL COMMENT '各模块数据库版本（JSON）',
    db_product         VARCHAR(32)   NULL,
    error_msg          VARCHAR(2000) NULL,
    remark             VARCHAR(256)  NULL,
    started_at         DATETIME      NOT NULL,
    finished_at        DATETIME      NULL,
    operator_id        BIGINT        NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '备份记录';
CREATE INDEX idx_bak_record_started ON bak_record (started_at);

CREATE TABLE bak_restore_log (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    record_id          BIGINT        NOT NULL COMMENT '恢复所用的备份',
    file_name          VARCHAR(128)  NOT NULL,
    pre_backup_id      BIGINT        NULL COMMENT '恢复前自动备份',
    restore_status     VARCHAR(16)   NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED',
    phase              VARCHAR(64)   NULL COMMENT '当前步骤',
    table_count        INT           NOT NULL DEFAULT 0,
    row_count          BIGINT        NOT NULL DEFAULT 0,
    attachment_count   INT           NOT NULL DEFAULT 0,
    error_msg          VARCHAR(2000) NULL,
    started_at         DATETIME      NOT NULL,
    finished_at        DATETIME      NULL,
    operator_id        BIGINT        NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '恢复日志';
CREATE INDEX idx_bak_restore_started ON bak_restore_log (started_at);
