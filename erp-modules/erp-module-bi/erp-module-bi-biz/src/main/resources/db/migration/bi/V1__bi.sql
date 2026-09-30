-- BI / AI（需求 13-BI与AI）：指标说明、汇总表、数据任务、AI 会话与日志

CREATE TABLE bi_metric (
    id           BIGINT        NOT NULL PRIMARY KEY,
    code         VARCHAR(64)   NOT NULL,
    display_name VARCHAR(64)   NULL COMMENT '展示名称（为空用代码注册名称）',
    description  VARCHAR(1000) NULL COMMENT '补充说明',
    owner_name   VARCHAR(64)   NULL COMMENT '负责人',
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_bi_metric_code UNIQUE (code)
) COMMENT '指标说明（计算逻辑在代码中注册）';

CREATE TABLE bi_agg_sales_daily (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    stat_date           DATE          NOT NULL,
    period              VARCHAR(6)    NOT NULL,
    customer_id         BIGINT        NULL,
    material_id         BIGINT        NULL,
    category_id         BIGINT        NULL,
    country             VARCHAR(8)    NULL,
    owner_id            BIGINT        NULL COMMENT '业务员',
    dept_id             BIGINT        NULL,
    org_id              BIGINT        NULL,
    order_amount        DECIMAL(18,2) NOT NULL DEFAULT 0,
    ship_amount         DECIMAL(18,2) NOT NULL DEFAULT 0,
    ship_qty            DECIMAL(18,4) NOT NULL DEFAULT 0,
    ship_cost           DECIMAL(18,2) NOT NULL DEFAULT 0,
    costed_ship_amount  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已有成本的出货额（毛利率分母）',
    return_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
    ship_line_count     INT           NOT NULL DEFAULT 0,
    on_time_line_count  INT           NOT NULL DEFAULT 0,
    receipt_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '销售日汇总';
CREATE INDEX idx_bi_sales_date ON bi_agg_sales_daily (stat_date);

CREATE TABLE bi_agg_purchase_daily (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    stat_date           DATE          NOT NULL,
    period              VARCHAR(6)    NOT NULL,
    supplier_id         BIGINT        NULL,
    material_id         BIGINT        NULL,
    category_id         BIGINT        NULL,
    owner_id            BIGINT        NULL COMMENT '采购员',
    dept_id             BIGINT        NULL,
    org_id              BIGINT        NULL,
    order_amount        DECIMAL(18,2) NOT NULL DEFAULT 0,
    order_qty           DECIMAL(18,4) NOT NULL DEFAULT 0,
    receipt_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    receipt_qty         DECIMAL(18,4) NOT NULL DEFAULT 0,
    due_line_count      INT           NOT NULL DEFAULT 0,
    on_time_line_count  INT           NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '采购日汇总';
CREATE INDEX idx_bi_purchase_date ON bi_agg_purchase_daily (stat_date);

CREATE TABLE bi_agg_production_daily (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    stat_date           DATE          NOT NULL,
    period              VARCHAR(6)    NOT NULL,
    dept_id             BIGINT        NULL COMMENT '车间',
    material_id         BIGINT        NULL,
    category_id         BIGINT        NULL,
    plan_qty            DECIMAL(18,4) NOT NULL DEFAULT 0,
    good_qty            DECIMAL(18,4) NOT NULL DEFAULT 0,
    defect_qty          DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty           DECIMAL(18,4) NOT NULL DEFAULT 0,
    work_hours          DECIMAL(18,4) NOT NULL DEFAULT 0,
    std_hours           DECIMAL(18,4) NOT NULL DEFAULT 0,
    in_qty              DECIMAL(18,4) NOT NULL DEFAULT 0,
    first_pass_qty      DECIMAL(18,4) NOT NULL DEFAULT 0,
    delayed_order_count INT           NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '生产日汇总';
CREATE INDEX idx_bi_production_date ON bi_agg_production_daily (stat_date);

CREATE TABLE bi_agg_quality_daily (
    id                BIGINT      NOT NULL PRIMARY KEY,
    stat_date         DATE        NOT NULL,
    period            VARCHAR(6)  NOT NULL,
    inspect_type      VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/FQC/OQC/RETURN/NCR/COMPLAINT',
    supplier_id       BIGINT      NULL,
    customer_id       BIGINT      NULL,
    material_id       BIGINT      NULL,
    category_id       BIGINT      NULL,
    lot_count         INT         NOT NULL DEFAULT 0,
    pass_count        INT         NOT NULL DEFAULT 0,
    concession_count  INT         NOT NULL DEFAULT 0,
    reject_count      INT         NOT NULL DEFAULT 0,
    defect_count      INT         NOT NULL DEFAULT 0,
    ncr_count         INT         NOT NULL DEFAULT 0,
    complaint_count   INT         NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '品质日汇总';
CREATE INDEX idx_bi_quality_date ON bi_agg_quality_daily (stat_date);

CREATE TABLE bi_agg_inventory_daily_snapshot (
    id              BIGINT        NOT NULL PRIMARY KEY,
    stat_date       DATE          NOT NULL,
    period          VARCHAR(6)    NOT NULL,
    warehouse_id    BIGINT        NULL,
    warehouse_type  VARCHAR(16)   NULL,
    material_id     BIGINT        NULL,
    category_id     BIGINT        NULL,
    qty             DECIMAL(18,4) NOT NULL DEFAULT 0,
    amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
    last_out_date   DATE          NULL,
    last_in_date    DATE          NULL,
    idle_days       INT           NULL COMMENT '截至快照日无出库天数（从无出库按最近入库）',
    age_days        INT           NULL COMMENT '截至快照日距最近入库天数（库龄）',
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '库存日快照';
CREATE INDEX idx_bi_inv_snapshot_date ON bi_agg_inventory_daily_snapshot (stat_date);

CREATE TABLE bi_agg_inventory_monthly (
    id              BIGINT        NOT NULL PRIMARY KEY,
    period          VARCHAR(6)    NOT NULL,
    warehouse_type  VARCHAR(16)   NULL,
    material_id     BIGINT        NULL,
    category_id     BIGINT        NULL,
    in_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
    out_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '库存月流水汇总';
CREATE INDEX idx_bi_inv_monthly_period ON bi_agg_inventory_monthly (period);

CREATE TABLE bi_agg_finance_monthly (
    id              BIGINT        NOT NULL PRIMARY KEY,
    period          VARCHAR(6)    NOT NULL,
    partner_type    VARCHAR(16)   NOT NULL COMMENT 'CUSTOMER/SUPPLIER',
    partner_id      BIGINT        NULL,
    owner_id        BIGINT        NULL,
    dept_id         BIGINT        NULL,
    org_id          BIGINT        NULL,
    begin_balance   DECIMAL(18,2) NOT NULL DEFAULT 0,
    add_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    settle_amount   DECIMAL(18,2) NOT NULL DEFAULT 0,
    end_balance     DECIMAL(18,2) NOT NULL DEFAULT 0,
    overdue_amount  DECIMAL(18,2) NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '往来月汇总';
CREATE INDEX idx_bi_finance_period ON bi_agg_finance_monthly (period);

CREATE TABLE bi_etl_job (
    id               BIGINT        NOT NULL PRIMARY KEY,
    code             VARCHAR(64)   NOT NULL,
    name             VARCHAR(64)   NOT NULL,
    job_status       VARCHAR(16)   NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/RUNNING',
    last_started_at  DATETIME      NULL,
    last_finished_at DATETIME      NULL,
    last_result      VARCHAR(16)   NULL COMMENT 'SUCCESS/FAILED',
    last_rows        INT           NULL,
    last_diff_rows   INT           NULL,
    last_duration_ms BIGINT        NULL,
    last_message     VARCHAR(1000) NULL,
    version          INT           NOT NULL DEFAULT 0,
    created_by       BIGINT        NULL,
    created_at       DATETIME      NOT NULL,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME      NOT NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_bi_etl_job_code UNIQUE (code)
) COMMENT '数据任务';

CREATE TABLE bi_etl_log (
    id           BIGINT        NOT NULL PRIMARY KEY,
    job_code     VARCHAR(64)   NOT NULL,
    started_at   DATETIME      NOT NULL,
    finished_at  DATETIME      NULL,
    result       VARCHAR(16)   NOT NULL,
    row_count    INT           NOT NULL DEFAULT 0,
    diff_rows    INT           NOT NULL DEFAULT 0,
    message      VARCHAR(2000) NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT '数据任务日志';
CREATE INDEX idx_bi_etl_log_job ON bi_etl_log (job_code, started_at);

CREATE TABLE ai_conversation (
    id          BIGINT       NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(128) NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0
) COMMENT 'AI 会话';
CREATE INDEX idx_ai_conversation_user ON ai_conversation (user_id);

CREATE TABLE ai_message (
    id               BIGINT   NOT NULL PRIMARY KEY,
    conversation_id  BIGINT   NOT NULL,
    role             VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT/TOOL',
    content          TEXT     NULL,
    tool_call        TEXT     NULL COMMENT '工具调用与结果（json）',
    result_json      TEXT     NULL COMMENT '返回给用户的数据表（json，真实数值）',
    tokens           INT      NULL,
    feedback         VARCHAR(8)   NULL COMMENT 'UP/DOWN',
    feedback_remark  VARCHAR(500) NULL,
    version          INT      NOT NULL DEFAULT 0,
    created_by       BIGINT   NULL,
    created_at       DATETIME NOT NULL,
    updated_by       BIGINT   NULL,
    updated_at       DATETIME NOT NULL,
    deleted          TINYINT  NOT NULL DEFAULT 0
) COMMENT 'AI 消息';
CREATE INDEX idx_ai_message_conv ON ai_message (conversation_id);

CREATE TABLE ai_query_log (
    id           BIGINT        NOT NULL PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    message_id   BIGINT        NULL,
    question     VARCHAR(2000) NOT NULL,
    tool_calls   TEXT          NULL,
    result_rows  INT           NOT NULL DEFAULT 0,
    latency_ms   BIGINT        NOT NULL DEFAULT 0,
    tokens       INT           NOT NULL DEFAULT 0,
    success      TINYINT       NOT NULL DEFAULT 1,
    error        VARCHAR(1000) NULL,
    feedback     VARCHAR(8)    NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT 'AI 问答日志';
CREATE INDEX idx_ai_query_log_user ON ai_query_log (user_id, created_at);

CREATE TABLE ai_anomaly (
    id           BIGINT        NOT NULL PRIMARY KEY,
    detect_date  DATE          NOT NULL,
    metric_code  VARCHAR(64)   NOT NULL,
    dimension    VARCHAR(32)   NULL,
    dim_value    VARCHAR(64)   NULL,
    dim_label    VARCHAR(128)  NULL,
    current_value DECIMAL(18,4) NULL,
    base_value   DECIMAL(18,4) NULL,
    change_pct   DECIMAL(10,2) NULL,
    method       VARCHAR(16)   NOT NULL COMMENT 'SIGMA/MOM',
    level        VARCHAR(16)   NOT NULL,
    explanation  VARCHAR(2000) NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT 'AI 异常解读';
CREATE INDEX idx_ai_anomaly_date ON ai_anomaly (detect_date);

CREATE TABLE ai_weekly_report (
    id           BIGINT        NOT NULL PRIMARY KEY,
    week_start   DATE          NOT NULL,
    user_id      BIGINT        NULL COMMENT '为空表示全公司口径',
    title        VARCHAR(128)  NOT NULL,
    data_json    TEXT          NULL,
    summary      TEXT          NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT 'AI 经营周报';
CREATE INDEX idx_ai_weekly_week ON ai_weekly_report (week_start);
