-- 系统管理第 2 步：定时任务、后台任务（需求 01-系统管理/12 第 2、3 节）
CREATE TABLE sys_job (
    id            BIGINT        NOT NULL PRIMARY KEY,
    code          VARCHAR(64)   NOT NULL COMMENT '任务编码（@ErpJob.code）',
    name          VARCHAR(64)   NOT NULL,
    module_code   VARCHAR(32)   NOT NULL,
    cron          VARCHAR(64)   NOT NULL COMMENT '当前 Cron',
    default_cron  VARCHAR(64)   NOT NULL COMMENT '代码声明的默认 Cron',
    enabled       TINYINT       NOT NULL DEFAULT 1,
    active        TINYINT       NOT NULL DEFAULT 1 COMMENT '声明已从代码中移除时为 0',
    last_run_at   DATETIME      NULL,
    last_result   VARCHAR(16)   NULL COMMENT 'SUCCESS/FAILED',
    last_message  VARCHAR(1024) NULL,
    next_run_at   DATETIME      NULL,
    locked_until  DATETIME      NULL COMMENT '执行锁（多实例只在一个实例执行）',
    locked_by     VARCHAR(128)  NULL COMMENT '持有锁的实例',
    version       INT           NOT NULL DEFAULT 0,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_job_code UNIQUE (code)
) COMMENT '定时任务';

CREATE TABLE sys_job_log (
    id           BIGINT        NOT NULL PRIMARY KEY,
    job_code     VARCHAR(64)   NOT NULL,
    started_at   DATETIME      NOT NULL,
    finished_at  DATETIME      NULL,
    duration_ms  BIGINT        NULL,
    result       VARCHAR(16)   NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    message      VARCHAR(1024) NULL,
    trigger_type VARCHAR(16)   NOT NULL COMMENT 'SCHEDULE/MANUAL',
    operator_id  BIGINT        NULL,
    node         VARCHAR(128)  NULL COMMENT '执行实例'
) COMMENT '定时任务执行日志（保留 90 天）';
CREATE INDEX idx_sys_job_log_code ON sys_job_log (job_code, started_at);

CREATE TABLE sys_async_task (
    id              BIGINT        NOT NULL PRIMARY KEY,
    task_type       VARCHAR(32)   NOT NULL COMMENT 'EXPORT/IMPORT/MRP/COST_CALC/...',
    name            VARCHAR(128)  NOT NULL,
    module_code     VARCHAR(32)   NOT NULL,
    status          VARCHAR(16)   NOT NULL COMMENT 'WAITING/RUNNING/SUCCESS/FAILED/CANCELED',
    progress        INT           NOT NULL DEFAULT 0,
    result_file_id  BIGINT        NULL,
    result_expired  TINYINT       NOT NULL DEFAULT 0 COMMENT '结果文件已过期删除',
    result_message  VARCHAR(1024) NULL,
    error_message   VARCHAR(1024) NULL,
    submitted_by    BIGINT        NULL,
    node            VARCHAR(128)  NULL COMMENT '执行实例',
    started_at      DATETIME      NULL,
    finished_at     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    created_by      BIGINT        NULL,
    created_at      DATETIME      NOT NULL,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME      NOT NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0
) COMMENT '后台任务';
CREATE INDEX idx_sys_async_task_user ON sys_async_task (submitted_by, status);
