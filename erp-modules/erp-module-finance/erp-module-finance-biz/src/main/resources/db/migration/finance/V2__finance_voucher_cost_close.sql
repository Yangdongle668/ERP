-- 财务模块第 2 批（需求 12-财务 06、07、08、09）：凭证、成本核算、外币重估；默认科目映射

CREATE TABLE fin_voucher (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(64)   NOT NULL,
    doc_date           DATE          NULL,
    status             VARCHAR(20)   NOT NULL,
    org_id             BIGINT        NULL,
    dept_id            BIGINT        NULL,
    owner_id           BIGINT        NULL,
    source_type        VARCHAR(32)   NULL,
    source_id          BIGINT        NULL,
    source_no          VARCHAR(64)   NULL,
    remark             VARCHAR(1000) NULL,
    voucher_word VARCHAR(8) NOT NULL DEFAULT '记' COMMENT '凭证字',
    voucher_seq INT NOT NULL COMMENT '期间内序号',
    period VARCHAR(6) NOT NULL,
    voucher_source VARCHAR(16) NOT NULL COMMENT 'AUTO/MANUAL',
    biz_type VARCHAR(32) NULL COMMENT '生成的业务类型（科目映射）',
    source_ids TEXT NULL COMMENT '生成来源单据 ID，逗号分隔',
    attachment_count INT NOT NULL DEFAULT 0,
    total_debit DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_credit DECIMAL(18,2) NOT NULL DEFAULT 0,
    voucher_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/AUDITED/POSTED',
    auditor_id BIGINT NULL,
    audited_at DATETIME NULL,
    poster_id BIGINT NULL,
    posted_at DATETIME NULL,
    reversal_of_id BIGINT NULL COMMENT '冲回的原凭证（外币重估下月冲回）',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_voucher_no UNIQUE (doc_no)
) COMMENT '记账凭证';

CREATE INDEX idx_fin_voucher_period ON fin_voucher (period, voucher_seq);

CREATE TABLE fin_voucher_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    summary VARCHAR(256) NOT NULL,
    account_code VARCHAR(32) NOT NULL,
    debit DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '本位币',
    credit DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '本位币',
    currency VARCHAR(3) NULL,
    fc_amount DECIMAL(18,2) NULL COMMENT '原币',
    exchange_rate DECIMAL(18,6) NULL,
    aux_customer_id BIGINT NULL,
    aux_supplier_id BIGINT NULL,
    aux_dept_id BIGINT NULL,
    aux_material_id BIGINT NULL,
    aux_project_id BIGINT NULL,
    source_type VARCHAR(32) NULL COMMENT '来源单据类型',
    source_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '凭证分录';

CREATE INDEX idx_fin_voucher_line_parent ON fin_voucher_line (voucher_id, line_no);

CREATE INDEX idx_fin_voucher_line_account ON fin_voucher_line (account_code);

CREATE TABLE fin_cost_run (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL,
    run_status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    operator_id BIGINT NULL,
    error_message VARCHAR(1000) NULL,
    material_count INT NOT NULL DEFAULT 0,
    order_count INT NOT NULL DEFAULT 0,
    total_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '完工产品总成本',
    exception_count INT NOT NULL DEFAULT 0,
    sales_cost_voucher_id BIGINT NULL,
    issue_voucher_id BIGINT NULL,
    finish_voucher_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '成本计算记录';

CREATE INDEX idx_fin_cost_run_period ON fin_cost_run (period, run_status);

CREATE TABLE fin_cost_expense (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL,
    dept_id BIGINT NOT NULL COMMENT '车间',
    labor_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    overhead_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_cost_expense UNIQUE (period, dept_id)
) COMMENT '本期费用（按车间）';

CREATE TABLE fin_cost_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL,
    material_id BIGINT NOT NULL,
    opening_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    opening_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    in_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '参与加权的本期入库',
    in_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(24,6) NOT NULL DEFAULT 0 COMMENT '加权单价',
    prev_unit_cost DECIMAL(24,6) NULL COMMENT '上期单价',
    out_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '按加权单价计价的净出库',
    out_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    closing_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    closing_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    sales_out_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '销售净出库（扣退货）',
    sales_out_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    issue_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '生产领料净额（扣退料）',
    production_in_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '生产入库金额',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_cost_material UNIQUE (period, material_id)
) COMMENT '物料加权结果';

CREATE TABLE fin_cost_order (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL,
    prod_order_id BIGINT NOT NULL,
    prod_order_no VARCHAR(64) NULL,
    material_id BIGINT NOT NULL,
    dept_id BIGINT NULL,
    order_type VARCHAR(16) NULL,
    work_hours DECIMAL(18,4) NOT NULL DEFAULT 0,
    opening_wip DECIMAL(18,2) NOT NULL DEFAULT 0,
    opening_wip_material DECIMAL(18,2) NOT NULL DEFAULT 0,
    material_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    labor_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    overhead_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    finished_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    finished_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    finished_material DECIMAL(18,2) NOT NULL DEFAULT 0,
    finished_labor DECIMAL(18,2) NOT NULL DEFAULT 0,
    finished_overhead DECIMAL(18,2) NOT NULL DEFAULT 0,
    ending_wip DECIMAL(18,2) NOT NULL DEFAULT 0,
    ending_wip_material DECIMAL(18,2) NOT NULL DEFAULT 0,
    cum_finished_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计完工',
    unit_cost DECIMAL(24,6) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_cost_order UNIQUE (period, prod_order_id)
) COMMENT '生产订单成本';

CREATE TABLE fin_cost_order_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    cost_order_id BIGINT NOT NULL,
    period VARCHAR(6) NOT NULL,
    prod_order_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    issue_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    return_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(24,6) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '订单材料明细';

CREATE INDEX idx_fin_cost_order_material ON fin_cost_order_material (cost_order_id);

CREATE TABLE fin_cost_exception (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL,
    run_id BIGINT NOT NULL,
    ex_type VARCHAR(32) NOT NULL COMMENT 'NEGATIVE_BALANCE/PRICE_SWING/ZERO_COST_OUT/NO_HOURS/NO_PRICE_IN/CYCLE',
    material_id BIGINT NULL,
    prod_order_id BIGINT NULL,
    dept_id BIGINT NULL,
    message VARCHAR(512) NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '成本计算异常';

CREATE INDEX idx_fin_cost_exception ON fin_cost_exception (period);

CREATE TABLE fin_fx_revaluation (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL,
    doc_type VARCHAR(8) NOT NULL COMMENT 'AR/AP/BANK',
    doc_id BIGINT NOT NULL,
    doc_no VARCHAR(64) NULL,
    partner_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL,
    fc_balance DECIMAL(18,2) NOT NULL,
    book_base DECIMAL(18,2) NOT NULL,
    period_end_rate DECIMAL(18,6) NOT NULL,
    revalued_base DECIMAL(18,2) NOT NULL,
    diff DECIMAL(18,2) NOT NULL,
    voucher_id BIGINT NULL,
    reversal_voucher_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '外币期末重估';

CREATE INDEX idx_fin_fx_revaluation ON fin_fx_revaluation (period, doc_type);

-- 常用科目映射（需求 12-06 第 3 节），财务可修改
INSERT INTO fin_account_mapping (id, biz_type, match_condition, condition_desc, priority, entries, mapping_status, remark, created_at, updated_at) VALUES
    (12101, 'SALES_AR', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "1122", "amountField": "totalAmount", "summaryTemplate": "出货 {sourceNo} {partner}", "auxFrom": "CUSTOMER"}, {"direction": "CREDIT", "accountCode": "6001", "amountField": "amount", "summaryTemplate": "出货 {sourceNo} {partner}"}, {"direction": "CREDIT", "accountCode": "222101", "amountField": "tax", "summaryTemplate": "出货 {sourceNo} 销项税"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12102, 'RECEIPT', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "1002", "amountField": "amount", "summaryTemplate": "收款 {docNo} {partner}"}, {"direction": "DEBIT", "accountCode": "660302", "amountField": "fee", "summaryTemplate": "收款手续费 {docNo}"}, {"direction": "CREDIT", "accountCode": "1122", "amountField": "totalAmount", "summaryTemplate": "收款 {docNo} {partner}", "auxFrom": "CUSTOMER"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12103, 'PURCHASE_AP', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "1403", "amountField": "amount", "summaryTemplate": "采购 {sourceNo} {partner}"}, {"direction": "DEBIT", "accountCode": "222101", "amountField": "tax", "summaryTemplate": "采购 {sourceNo} 进项税"}, {"direction": "CREDIT", "accountCode": "2202", "amountField": "totalAmount", "summaryTemplate": "采购 {sourceNo} {partner}", "auxFrom": "SUPPLIER"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12104, 'PAYMENT', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "2202", "amountField": "amount", "summaryTemplate": "付款 {docNo} {partner}", "auxFrom": "SUPPLIER"}, {"direction": "DEBIT", "accountCode": "660302", "amountField": "fee", "summaryTemplate": "付款手续费 {docNo}"}, {"direction": "CREDIT", "accountCode": "1002", "amountField": "totalAmount", "summaryTemplate": "付款 {docNo} {partner}"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12105, 'PRODUCTION_ISSUE', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "500101", "amountField": "cost", "summaryTemplate": "{period} 生产领料"}, {"direction": "CREDIT", "accountCode": "1403", "amountField": "cost", "summaryTemplate": "{period} 生产领料"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12106, 'PRODUCTION_IN', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "1405", "amountField": "cost", "summaryTemplate": "{period} 完工入库"}, {"direction": "CREDIT", "accountCode": "500101", "amountField": "cost", "summaryTemplate": "{period} 完工入库"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12107, 'STOCK_OUT_SALES_COST', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "6401", "amountField": "cost", "summaryTemplate": "{period} 销售成本结转"}, {"direction": "CREDIT", "accountCode": "1405", "amountField": "cost", "summaryTemplate": "{period} 销售成本结转"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12108, 'FX_GAIN_LOSS', NULL, '默认', 0, '[{"direction": "DEBIT", "accountCode": "1122", "amountField": "fxDiff", "summaryTemplate": "汇兑损益 {docNo}", "auxFrom": "CUSTOMER"}, {"direction": "CREDIT", "accountCode": "660301", "amountField": "fxDiff", "summaryTemplate": "汇兑损益 {docNo}"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12109, 'FX_GAIN_LOSS', '{"partnerType":"SUPPLIER"}', '供应商（付款核销）', 10, '[{"direction": "DEBIT", "accountCode": "660301", "amountField": "fxDiff", "summaryTemplate": "汇兑损益 {docNo}"}, {"direction": "CREDIT", "accountCode": "2202", "amountField": "fxDiff", "summaryTemplate": "汇兑损益 {docNo}", "auxFrom": "SUPPLIER"}]', 'ENABLED', '初始化', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
