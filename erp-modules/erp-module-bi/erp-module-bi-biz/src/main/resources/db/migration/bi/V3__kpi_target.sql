-- KPI 月度目标（经营驾驶舱显示达成率）
CREATE TABLE bi_kpi_target (
    id           BIGINT        NOT NULL PRIMARY KEY,
    metric_code  VARCHAR(64)   NOT NULL COMMENT '指标编码（金额类累计指标）',
    target_month VARCHAR(6)    NOT NULL COMMENT '目标月份 yyyyMM',
    target_value DECIMAL(18,2) NOT NULL COMMENT '目标值（本位币）',
    remark       VARCHAR(256)  NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT 'KPI 月度目标';
CREATE UNIQUE INDEX uk_bi_kpi_target ON bi_kpi_target (metric_code, target_month);
