-- 报表订阅：保存的查询 + 周期，定时以订阅人身份（权限、数据范围）生成并发送
CREATE TABLE bi_subscription (
    id           BIGINT        NOT NULL PRIMARY KEY,
    user_id      BIGINT        NOT NULL COMMENT '订阅人',
    sub_name     VARCHAR(64)   NOT NULL COMMENT '订阅名称',
    metrics      VARCHAR(500)  NOT NULL COMMENT '指标编码，逗号分隔',
    dimension    VARCHAR(32)   NULL COMMENT '分组维度（为空只给合计）',
    filters      VARCHAR(1000) NULL COMMENT '筛选条件（JSON：维度 → 取值列表）',
    period_type  VARCHAR(16)   NOT NULL COMMENT 'YESTERDAY / LAST_WEEK / LAST_MONTH / MONTH_TO_DATE',
    top_n        INT           NOT NULL DEFAULT 10 COMMENT '分组时显示前 N 行',
    frequency    VARCHAR(16)   NOT NULL COMMENT 'DAILY / WEEKLY / MONTHLY',
    weekday      INT           NULL COMMENT '每周几发送（1 周一～7 周日）',
    monthday     INT           NULL COMMENT '每月几号发送（1～28）',
    send_email   TINYINT       NOT NULL DEFAULT 0 COMMENT '同时发邮件',
    enabled      TINYINT       NOT NULL DEFAULT 1,
    last_sent_on DATE          NULL,
    last_status  VARCHAR(16)   NULL COMMENT 'SUCCESS / FAILED',
    last_message VARCHAR(256)  NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT '报表订阅';
CREATE INDEX idx_bi_subscription_user ON bi_subscription (user_id);
