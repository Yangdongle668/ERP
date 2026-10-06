-- 实时汇率（需求 16-实时汇率）：中国银行现汇买入价，每 1 单位外币；数据保存 3 年
CREATE TABLE fx_quote (
    id           BIGINT        NOT NULL PRIMARY KEY,
    pair         VARCHAR(8)    NOT NULL COMMENT 'USD_CNY / EUR_CNY / EUR_USD',
    rate         DECIMAL(18,6) NOT NULL,
    quote_date   DATE          NOT NULL COMMENT '报价日期（按银行发布时间）',
    publish_time DATETIME      NOT NULL COMMENT '银行发布时间',
    fetched_at   DATETIME      NOT NULL COMMENT '取得时间',
    source       VARCHAR(16)   NOT NULL DEFAULT 'BOC',
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT '汇率报价（每次取得的现汇买入价）';
CREATE INDEX idx_fx_quote_pair_date ON fx_quote (pair, quote_date);
CREATE INDEX idx_fx_quote_publish ON fx_quote (pair, publish_time);

CREATE TABLE fx_daily_rate (
    id           BIGINT        NOT NULL PRIMARY KEY,
    pair         VARCHAR(8)    NOT NULL,
    rate_date    DATE          NOT NULL,
    avg_rate     DECIMAL(18,6) NOT NULL COMMENT '当天报价的算术平均',
    min_rate     DECIMAL(18,6) NOT NULL,
    max_rate     DECIMAL(18,6) NOT NULL,
    sample_count INT           NOT NULL,
    finalized    TINYINT       NOT NULL DEFAULT 0 COMMENT '1 当天已结束、平均汇率已结算',
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fx_daily_rate UNIQUE (pair, rate_date)
) COMMENT '日平均汇率';

CREATE TABLE fx_monthly_rate (
    id           BIGINT        NOT NULL PRIMARY KEY,
    pair         VARCHAR(8)    NOT NULL,
    rate_month   VARCHAR(7)    NOT NULL COMMENT 'yyyy-MM',
    avg_rate     DECIMAL(18,6) NOT NULL COMMENT '当月各日平均汇率的平均',
    day_count    INT           NOT NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fx_monthly_rate UNIQUE (pair, rate_month)
) COMMENT '月平均汇率';
