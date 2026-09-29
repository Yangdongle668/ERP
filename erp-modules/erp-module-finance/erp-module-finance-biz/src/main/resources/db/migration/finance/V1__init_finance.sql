-- 财务模块第 1 批（需求 12-财务 01～05、08）：基础设置、应收与销项发票、收款与核销、应付与进项发票、付款申请与付款

CREATE TABLE fin_account (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    parent_code VARCHAR(32) NULL,
    account_type VARCHAR(16) NOT NULL COMMENT 'ASSET/LIABILITY/EQUITY/COST/PROFIT_LOSS',
    direction VARCHAR(8) NOT NULL COMMENT 'DEBIT/CREDIT',
    aux_types VARCHAR(64) NULL COMMENT '辅助核算：CUSTOMER、SUPPLIER、DEPT、MATERIAL、PROJECT，逗号分隔',
    currency_accounting TINYINT NOT NULL DEFAULT 0 COMMENT '外币核算',
    is_leaf TINYINT NOT NULL DEFAULT 1 COMMENT '末级科目',
    account_level INT NOT NULL DEFAULT 1,
    account_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_account_code UNIQUE (code)
) COMMENT '会计科目';

CREATE TABLE fin_period (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    period VARCHAR(6) NOT NULL COMMENT 'yyyyMM',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    period_status VARCHAR(16) NOT NULL COMMENT 'NOT_OPEN/OPEN/CLOSED',
    cost_locked TINYINT NOT NULL DEFAULT 0 COMMENT '成本已锁定',
    closed_by BIGINT NULL,
    closed_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_period UNIQUE (period)
) COMMENT '会计期间';

CREATE TABLE fin_bank_account (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL COMMENT '如 中行美元户',
    bank_name VARCHAR(128) NOT NULL,
    account_no VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    swift VARCHAR(32) NULL,
    bank_address VARCHAR(256) NULL,
    account_code VARCHAR(32) NULL COMMENT '对应会计科目',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '同币别默认',
    bank_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_bank_account_code UNIQUE (code)
) COMMENT '本公司银行账户';

CREATE TABLE fin_account_mapping (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    biz_type VARCHAR(32) NOT NULL COMMENT 'SALES_AR/SALES_RETURN_AR/RECEIPT/PURCHASE_AP/PAYMENT/…',
    match_condition VARCHAR(512) NULL COMMENT '条件 JSON，为空表示默认',
    condition_desc VARCHAR(256) NULL,
    priority INT NOT NULL DEFAULT 0,
    entries TEXT NOT NULL COMMENT '分录模板 JSON',
    mapping_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '科目映射（业务类型 → 分录模板）';

CREATE INDEX idx_fin_account_mapping_type ON fin_account_mapping (biz_type, priority);

CREATE TABLE fin_receivable (
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
    ar_type VARCHAR(16) NOT NULL COMMENT 'SALES/SALES_RETURN/DISCOUNT/OTHER',
    customer_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '不含税（原币，红字为负）',
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '价税合计（原币）',
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    biz_date DATE NOT NULL COMMENT '业务日期，决定会计期间',
    due_date DATE NULL,
    payment_term_id BIGINT NULL,
    order_id BIGINT NULL COMMENT '主订单（预收冲销匹配）',
    source_ref_id BIGINT NULL COMMENT '来源子单据（退货入库单等，事件幂等）',
    bl_date DATE NULL COMMENT '提单日期（到期日重算）',
    verified_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    invoiced_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ar_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/CONFIRMED/VOIDED',
    confirmed_at DATETIME NULL,
    void_reason VARCHAR(256) NULL,
    voucher_id BIGINT NULL,
    description VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_receivable_no UNIQUE (doc_no)
) COMMENT '应收单';

CREATE INDEX idx_fin_receivable_customer ON fin_receivable (customer_id, ar_status);

CREATE INDEX idx_fin_receivable_source ON fin_receivable (source_type, source_id);

CREATE INDEX idx_fin_receivable_date ON fin_receivable (biz_date);

CREATE TABLE fin_receivable_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    receivable_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    ar_type VARCHAR(16) NOT NULL,
    source_line_id BIGINT NULL COMMENT '出货单行 / 退货单行',
    source_line_key BIGINT NULL COMMENT '有效时 = source_line_id，作废后置空（幂等唯一键）',
    order_id BIGINT NULL,
    order_no VARCHAR(64) NULL,
    order_line_id BIGINT NULL,
    material_id BIGINT NULL,
    description VARCHAR(256) NULL,
    qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '基本单位',
    price_incl_tax DECIMAL(18,6) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    invoiced_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    invoiced_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_receivable_line_source UNIQUE (ar_type, source_line_key)
) COMMENT '应收单明细';

CREATE INDEX idx_fin_receivable_line_parent ON fin_receivable_line (receivable_id, line_no);

CREATE TABLE fin_sales_invoice (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    invoice_type VARCHAR(16) NOT NULL COMMENT 'VAT_SPECIAL/VAT_NORMAL/EXPORT/OTHER',
    invoice_no VARCHAR(64) NOT NULL,
    invoice_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    invoice_status VARCHAR(16) NOT NULL COMMENT 'REGISTERED/VOIDED/RED',
    void_reason VARCHAR(256) NULL,
    remark VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_sales_invoice_no UNIQUE (doc_no)
) COMMENT '销项发票登记';

CREATE INDEX idx_fin_sales_invoice_customer ON fin_sales_invoice (customer_id);

CREATE TABLE fin_sales_invoice_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    receivable_id BIGINT NOT NULL,
    receivable_line_id BIGINT NOT NULL,
    order_line_id BIGINT NULL,
    material_id BIGINT NULL,
    qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '销项发票明细（对应应收单行）';

CREATE INDEX idx_fin_sales_invoice_line_parent ON fin_sales_invoice_line (invoice_id, line_no);

CREATE INDEX idx_fin_sales_invoice_line_ar ON fin_sales_invoice_line (receivable_line_id);

CREATE TABLE fin_receipt (
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
    customer_id BIGINT NOT NULL,
    receipt_type VARCHAR(16) NOT NULL COMMENT 'SALES/ADVANCE/OTHER/REFUND',
    bank_account_id BIGINT NOT NULL,
    settlement_method VARCHAR(16) NOT NULL,
    receipt_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL COMMENT '到账金额（退款为负）',
    bank_fee DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    bank_ref_no VARCHAR(64) NULL,
    payer_name VARCHAR(128) NULL,
    order_id BIGINT NULL COMMENT '预收款对应销售订单',
    order_no VARCHAR(64) NULL,
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已核销（原币，含手续费部分）',
    receipt_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/CONFIRMED/VOIDED',
    confirmed_at DATETIME NULL,
    voucher_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_receipt_no UNIQUE (doc_no)
) COMMENT '收款单';

CREATE INDEX idx_fin_receipt_customer ON fin_receipt (customer_id, receipt_status);

CREATE TABLE fin_verification (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    verify_type VARCHAR(16) NOT NULL COMMENT 'RECEIPT_AR/ADVANCE_AR/RED_BLUE_AR/PAYMENT_AP/PREPAY_AP/RED_BLUE_AP',
    batch_no VARCHAR(32) NOT NULL COMMENT '同一次核销操作',
    partner_type VARCHAR(16) NOT NULL COMMENT 'CUSTOMER/SUPPLIER',
    partner_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    doc_a_type VARCHAR(16) NOT NULL COMMENT 'RECEIPT/PAYMENT/RECEIVABLE/PAYABLE',
    doc_a_id BIGINT NOT NULL,
    doc_a_no VARCHAR(64) NULL,
    doc_b_type VARCHAR(16) NOT NULL,
    doc_b_id BIGINT NOT NULL,
    doc_b_no VARCHAR(64) NULL,
    order_id BIGINT NULL COMMENT '应收关联订单（回款计划）',
    amount DECIMAL(18,2) NOT NULL,
    amount_base_a DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_base_b DECIMAL(18,2) NOT NULL DEFAULT 0,
    fx_diff DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '汇兑差异 = base_a − base_b（正为收益）',
    period VARCHAR(6) NOT NULL,
    verified_at DATETIME NOT NULL,
    operator_id BIGINT NULL,
    reversed TINYINT NOT NULL DEFAULT 0,
    reversed_at DATETIME NULL,
    reversed_by BIGINT NULL,
    voucher_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '核销记录（收款 / 付款共用）';

CREATE INDEX idx_fin_verification_a ON fin_verification (doc_a_type, doc_a_id);

CREATE INDEX idx_fin_verification_b ON fin_verification (doc_b_type, doc_b_id);

CREATE INDEX idx_fin_verification_partner ON fin_verification (partner_type, partner_id);

CREATE TABLE fin_payable (
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
    ap_type VARCHAR(16) NOT NULL COMMENT 'PURCHASE/OUTSOURCE/OTHER',
    supplier_id BIGINT NOT NULL,
    statement_id BIGINT NULL,
    statement_no VARCHAR(64) NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    biz_date DATE NOT NULL,
    due_date DATE NULL,
    invoiced_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    requested_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已申请未付',
    verified_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已付款核销',
    ap_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/CONFIRMED/VOIDED',
    confirmed_at DATETIME NULL,
    void_reason VARCHAR(256) NULL,
    voucher_id BIGINT NULL,
    description VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_payable_no UNIQUE (doc_no)
) COMMENT '应付单';

CREATE INDEX idx_fin_payable_supplier ON fin_payable (supplier_id, ap_status);

CREATE INDEX idx_fin_payable_statement ON fin_payable (statement_id);

CREATE TABLE fin_payable_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    payable_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    line_type VARCHAR(16) NOT NULL COMMENT 'GOODS/RETURN/PROCESS_FEE/ADJUST/PRICE_DIFF/OTHER',
    statement_line_id BIGINT NULL,
    source_no VARCHAR(64) NULL,
    order_no VARCHAR(64) NULL,
    material_id BIGINT NULL,
    description VARCHAR(256) NULL,
    qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    price_incl_tax DECIMAL(18,6) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    invoiced_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    invoiced_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '应付单明细';

CREATE INDEX idx_fin_payable_line_parent ON fin_payable_line (payable_id, line_no);

CREATE TABLE fin_purchase_invoice (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    invoice_type VARCHAR(16) NOT NULL COMMENT 'VAT_SPECIAL/VAT_NORMAL/OTHER',
    invoice_no VARCHAR(64) NOT NULL,
    invoice_code VARCHAR(32) NULL,
    invoice_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    match_status VARCHAR(16) NOT NULL COMMENT 'UNMATCHED/MATCHED/DIFF',
    diff_confirmed_by BIGINT NULL,
    diff_confirmed_at DATETIME NULL,
    deduction_status VARCHAR(16) NOT NULL COMMENT 'NOT_CERTIFIED/CERTIFIED',
    certified_period VARCHAR(6) NULL,
    invoice_status VARCHAR(16) NOT NULL COMMENT 'REGISTERED/VOIDED',
    void_reason VARCHAR(256) NULL,
    remark VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_purchase_invoice_no UNIQUE (doc_no)
) COMMENT '进项发票登记（三单匹配）';

CREATE INDEX idx_fin_purchase_invoice_supplier ON fin_purchase_invoice (supplier_id, invoice_no);

CREATE TABLE fin_purchase_invoice_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    payable_id BIGINT NOT NULL,
    payable_line_id BIGINT NOT NULL,
    material_id BIGINT NULL,
    qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    invoice_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '发票不含税单价',
    ap_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '应付不含税单价',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ap_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冲减应付行未开票的金额（价税合计）',
    price_diff_pct DECIMAL(10,4) NOT NULL DEFAULT 0,
    over_tolerance TINYINT NOT NULL DEFAULT 0,
    diff_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '进项发票匹配明细';

CREATE INDEX idx_fin_purchase_invoice_line_parent ON fin_purchase_invoice_line (invoice_id, line_no);

CREATE INDEX idx_fin_purchase_invoice_line_ap ON fin_purchase_invoice_line (payable_line_id);

CREATE TABLE fin_payment_request (
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
    supplier_id BIGINT NOT NULL,
    request_type VARCHAR(16) NOT NULL COMMENT 'PAYABLE/PREPAYMENT',
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    plan_pay_date DATE NOT NULL,
    supplier_bank_id BIGINT NULL,
    supplier_bank_text VARCHAR(512) NULL COMMENT '收款账户快照',
    order_id BIGINT NULL COMMENT '预付款对应采购订单',
    order_no VARCHAR(64) NULL,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    request_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/APPROVED/PARTIAL/PAID/CLOSED/VOIDED',
    reason VARCHAR(512) NULL,
    uninvoiced_warning TINYINT NOT NULL DEFAULT 0 COMMENT '含未收到发票的应付',
    approved_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_payment_request_no UNIQUE (doc_no)
) COMMENT '付款申请';

CREATE INDEX idx_fin_payment_request_supplier ON fin_payment_request (supplier_id, request_status);

CREATE TABLE fin_payment_request_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    payable_id BIGINT NOT NULL,
    payable_no VARCHAR(64) NULL,
    amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '付款申请明细';

CREATE INDEX idx_fin_payment_request_line_parent ON fin_payment_request_line (request_id, line_no);

CREATE INDEX idx_fin_payment_request_line_ap ON fin_payment_request_line (payable_id);

CREATE TABLE fin_payment (
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
    request_id BIGINT NOT NULL,
    request_no VARCHAR(64) NULL,
    request_type VARCHAR(16) NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    bank_account_id BIGINT NOT NULL,
    settlement_method VARCHAR(16) NOT NULL,
    pay_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    bank_fee DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    bank_ref_no VARCHAR(64) NULL,
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已核销应付（原币）',
    payment_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/CONFIRMED/VOIDED',
    confirmed_at DATETIME NULL,
    voucher_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_payment_no UNIQUE (doc_no)
) COMMENT '付款单';

CREATE INDEX idx_fin_payment_supplier ON fin_payment (supplier_id, payment_status);

CREATE INDEX idx_fin_payment_request ON fin_payment (request_id);

-- 常用一级科目（《企业会计准则》），财务可增删改
INSERT INTO fin_account (id, code, name, parent_code, account_type, direction, aux_types, currency_accounting, is_leaf, account_level, account_status, created_at, updated_at) VALUES
    (12001, '1001', '库存现金', NULL, 'ASSET', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12002, '1002', '银行存款', NULL, 'ASSET', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12003, '1122', '应收账款', NULL, 'ASSET', 'DEBIT', 'CUSTOMER', 1, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12004, '1123', '预付账款', NULL, 'ASSET', 'DEBIT', 'SUPPLIER', 1, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12005, '1221', '其他应收款', NULL, 'ASSET', 'DEBIT', 'CUSTOMER', 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12006, '1403', '原材料', NULL, 'ASSET', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12007, '1405', '库存商品', NULL, 'ASSET', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12008, '1411', '周转材料', NULL, 'ASSET', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12009, '2202', '应付账款', NULL, 'LIABILITY', 'CREDIT', 'SUPPLIER', 1, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12010, '2203', '预收账款', NULL, 'LIABILITY', 'CREDIT', 'CUSTOMER', 1, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12011, '2221', '应交税费', NULL, 'LIABILITY', 'CREDIT', NULL, 0, 0, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12012, '222101', '应交增值税', '2221', 'LIABILITY', 'CREDIT', NULL, 0, 1, 2, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12013, '2241', '其他应付款', NULL, 'LIABILITY', 'CREDIT', 'SUPPLIER', 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12014, '4103', '本年利润', NULL, 'EQUITY', 'CREDIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12015, '5001', '生产成本', NULL, 'COST', 'DEBIT', NULL, 0, 0, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12016, '500101', '直接材料', '5001', 'COST', 'DEBIT', NULL, 0, 1, 2, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12017, '500102', '直接人工', '5001', 'COST', 'DEBIT', NULL, 0, 1, 2, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12018, '500103', '制造费用', '5001', 'COST', 'DEBIT', NULL, 0, 1, 2, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12019, '5101', '制造费用', NULL, 'COST', 'DEBIT', 'DEPT', 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12020, '6001', '主营业务收入', NULL, 'PROFIT_LOSS', 'CREDIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12021, '6051', '其他业务收入', NULL, 'PROFIT_LOSS', 'CREDIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12022, '6401', '主营业务成本', NULL, 'PROFIT_LOSS', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12023, '6601', '销售费用', NULL, 'PROFIT_LOSS', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12024, '6602', '管理费用', NULL, 'PROFIT_LOSS', 'DEBIT', NULL, 0, 1, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12025, '6603', '财务费用', NULL, 'PROFIT_LOSS', 'DEBIT', NULL, 0, 0, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12026, '660301', '汇兑损益', '6603', 'PROFIT_LOSS', 'DEBIT', NULL, 0, 1, 2, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12027, '660302', '手续费', '6603', 'PROFIT_LOSS', 'DEBIT', NULL, 0, 1, 2, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
