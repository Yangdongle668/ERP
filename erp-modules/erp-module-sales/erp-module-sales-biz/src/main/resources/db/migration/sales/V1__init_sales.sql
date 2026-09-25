-- 销售模块（需求 04-销售）：价格表、RFQ 与成本核算、报价、销售订单（快照、执行记录、回款计划）、订单变更、销售预测、销售退货

-- ==================== 价格表（04-01） ====================

CREATE TABLE sal_price_list (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(64)   NOT NULL,
    doc_date           DATE          NULL,
    status             VARCHAR(20)   NOT NULL COMMENT 'DRAFT/PENDING_APPROVAL/APPROVED（生效）/CLOSED',
    org_id             BIGINT        NULL,
    dept_id            BIGINT        NULL,
    owner_id           BIGINT        NULL,
    source_type        VARCHAR(32)   NULL,
    source_id          BIGINT        NULL,
    source_no          VARCHAR(64)   NULL,
    remark             VARCHAR(1000) NULL,
    name VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL COMMENT 'CUSTOMER/LEVEL/ALL',
    customer_id BIGINT NULL,
    customer_level VARCHAR(8) NULL,
    currency VARCHAR(3) NOT NULL,
    tax_included TINYINT NOT NULL DEFAULT 1,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    close_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sal_price_list_no UNIQUE (doc_no)
) COMMENT '销售价格表';
CREATE INDEX idx_sal_price_list_customer ON sal_price_list (customer_id);

CREATE TABLE sal_price_list_item (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    price_list_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    uom VARCHAR(16) NOT NULL,
    min_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '阶梯起始数量（销售单位）',
    price DECIMAL(18,6) NOT NULL COMMENT '按价格表 tax_included 为含税或不含税',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '销售价格表明细（阶梯）';
CREATE INDEX idx_sal_price_item_list ON sal_price_list_item (price_list_id);
CREATE INDEX idx_sal_price_item_material ON sal_price_list_item (material_id);

-- ==================== RFQ、成本核算（04-02） ====================

CREATE TABLE sal_rfq (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(64)   NOT NULL,
    doc_date           DATE          NULL,
    status             VARCHAR(20)   NOT NULL COMMENT '通用状态，由 rfq_status 推导',
    org_id             BIGINT        NULL,
    dept_id            BIGINT        NULL,
    owner_id           BIGINT        NULL,
    source_type        VARCHAR(32)   NULL,
    source_id          BIGINT        NULL,
    source_no          VARCHAR(64)   NULL,
    remark             VARCHAR(1000) NULL,
    customer_id BIGINT NOT NULL,
    contact_id BIGINT NULL,
    opportunity_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL,
    trade_term VARCHAR(16) NULL,
    reply_due_date DATE NOT NULL,
    engineer_id BIGINT NULL,
    cost_engineer_id BIGINT NULL,
    rfq_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/EVALUATING/COSTED/QUOTED/CLOSED',
    close_reason VARCHAR(256) NULL,
    reminded_at DATETIME NULL COMMENT '截止前提醒时间（每张只提醒一次）',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sal_rfq_no UNIQUE (doc_no)
) COMMENT '客户询价 RFQ';
CREATE INDEX idx_sal_rfq_customer ON sal_rfq (customer_id);

CREATE TABLE sal_rfq_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    customer_part_no VARCHAR(64) NULL,
    description VARCHAR(512) NOT NULL,
    material_id BIGINT NULL,
    annual_qty DECIMAL(18,4) NULL,
    qty_breaks VARCHAR(128) NOT NULL COMMENT '数量阶梯，逗号分隔',
    target_price DECIMAL(18,6) NULL,
    required_date DATE NULL,
    feasibility VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/OK/NG',
    feasibility_remark VARCHAR(512) NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'RFQ 明细';
CREATE INDEX idx_sal_rfq_line_rfq ON sal_rfq_line (rfq_id);

CREATE TABLE sal_cost_sheet (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    rfq_line_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '核算数量（阶梯）',
    bom_id BIGINT NULL,
    material_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    labor_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    overhead_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    setup_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    tooling_total DECIMAL(18,2) NULL COMMENT '模具/治具费总额',
    tooling_qty DECIMAL(18,4) NULL COMMENT '模具费分摊数量',
    tooling_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    packing_freight_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    admin_rate DECIMAL(9,4) NOT NULL DEFAULT 0.05,
    profit_rate DECIMAL(9,4) NOT NULL DEFAULT 0.15,
    total_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    suggested_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '建议售价（本位币）',
    suggested_price_cur DECIMAL(18,6) NULL COMMENT '建议售价（RFQ 币别）',
    detail TEXT NULL COMMENT '材料与工序明细 JSON',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '成本核算单（RFQ 行 × 数量阶梯）';
CREATE INDEX idx_sal_cost_sheet_line ON sal_cost_sheet (rfq_line_id);

-- ==================== 报价单（04-02） ====================

CREATE TABLE sal_quotation (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(64)   NOT NULL,
    doc_date           DATE          NULL,
    status             VARCHAR(20)   NOT NULL COMMENT '通用状态，由 quote_status 推导',
    org_id             BIGINT        NULL,
    dept_id            BIGINT        NULL,
    owner_id           BIGINT        NULL,
    source_type        VARCHAR(32)   NULL,
    source_id          BIGINT        NULL,
    source_no          VARCHAR(64)   NULL,
    remark             VARCHAR(1000) NULL,
    customer_id BIGINT NOT NULL,
    contact_id BIGINT NULL,
    rfq_id BIGINT NULL,
    opportunity_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,8) NOT NULL,
    trade_term VARCHAR(16) NULL,
    payment_term_id BIGINT NULL,
    tax_included TINYINT NOT NULL DEFAULT 1,
    valid_until DATE NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    parent_quotation_id BIGINT NULL,
    root_quotation_id BIGINT NULL COMMENT '修订链的第一张（R0）',
    terms VARCHAR(2000) NULL,
    quote_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/APPROVED/SENT/WON/LOST/EXPIRED/REVISED',
    lost_reason VARCHAR(32) NULL,
    lost_remark VARCHAR(256) NULL,
    sent_at DATETIME NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '参考金额：各行首档数量 × 单价',
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    min_margin_rate DECIMAL(9,4) NULL,
    below_floor TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '报价单';
CREATE INDEX idx_sal_quotation_no ON sal_quotation (doc_no);
CREATE INDEX idx_sal_quotation_customer ON sal_quotation (customer_id);
CREATE INDEX idx_sal_quotation_rfq ON sal_quotation (rfq_id);

CREATE TABLE sal_quotation_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    customer_part_no VARCHAR(64) NULL,
    description VARCHAR(512) NULL,
    uom VARCHAR(16) NOT NULL,
    min_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    price DECIMAL(18,6) NOT NULL COMMENT '按单头 tax_included',
    tax_rate DECIMAL(9,4) NOT NULL,
    cost_price DECIMAL(18,6) NULL COMMENT '单位成本（本位币，基本单位）',
    margin_rate DECIMAL(9,4) NULL,
    below_floor TINYINT NOT NULL DEFAULT 0,
    moq DECIMAL(18,4) NULL,
    lead_time_days INT NULL,
    tooling_fee DECIMAL(18,2) NULL,
    rfq_line_id BIGINT NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '报价单明细（同一物料多行表示多档）';
CREATE INDEX idx_sal_quotation_line_q ON sal_quotation_line (quotation_id);
CREATE INDEX idx_sal_quotation_line_m ON sal_quotation_line (material_id);

-- ==================== 销售订单（04-03、04-07） ====================

CREATE TABLE sal_order (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(64)   NOT NULL,
    doc_date           DATE          NULL,
    status             VARCHAR(20)   NOT NULL,
    org_id             BIGINT        NULL,
    dept_id            BIGINT        NULL,
    owner_id           BIGINT        NULL COMMENT '业务员',
    source_type        VARCHAR(32)   NULL,
    source_id          BIGINT        NULL,
    source_no          VARCHAR(64)   NULL,
    remark             VARCHAR(1000) NULL,
    order_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '字典 sal_order_type',
    customer_id BIGINT NOT NULL,
    contact_id BIGINT NULL,
    customer_po_no VARCHAR(64) NULL,
    customer_po_date DATE NULL,
    quotation_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,8) NOT NULL,
    tax_included TINYINT NOT NULL DEFAULT 1,
    payment_term_id BIGINT NULL,
    payment_term_snapshot TEXT NULL,
    trade_term VARCHAR(16) NULL,
    port_of_loading VARCHAR(64) NULL,
    port_of_destination VARCHAR(64) NULL,
    ship_to_address_id BIGINT NULL,
    ship_to_snapshot TEXT NULL,
    bill_to_address_id BIGINT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    shipped_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已出货金额（价税合计，原币）',
    received_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已回款（原币）',
    min_margin_rate DECIMAL(9,4) NULL,
    below_floor TINYINT NOT NULL DEFAULT 0,
    credit_warning TINYINT NOT NULL DEFAULT 0,
    order_version INT NOT NULL DEFAULT 1,
    delivery_risk TINYINT NOT NULL DEFAULT 0,
    close_reason VARCHAR(256) NULL,
    terms VARCHAR(2000) NULL,
    approved_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sal_order_no UNIQUE (doc_no)
) COMMENT '销售订单';
CREATE INDEX idx_sal_order_customer ON sal_order (customer_id);
CREATE INDEX idx_sal_order_po ON sal_order (customer_id, customer_po_no);
CREATE INDEX idx_sal_order_owner ON sal_order (owner_id);

CREATE TABLE sal_order_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    customer_part_id BIGINT NULL,
    customer_part_no VARCHAR(64) NULL,
    description VARCHAR(512) NULL,
    uom VARCHAR(16) NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    base_qty DECIMAL(18,4) NOT NULL,
    price DECIMAL(18,6) NOT NULL,
    price_incl_tax DECIMAL(18,6) NOT NULL,
    tax_rate DECIMAL(9,4) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    price_source VARCHAR(64) NULL,
    cost_price DECIMAL(18,6) NULL COMMENT '单位成本（本位币，基本单位）',
    margin_rate DECIMAL(9,4) NULL,
    below_floor TINYINT NOT NULL DEFAULT 0,
    required_date DATE NOT NULL,
    promised_date DATE NULL,
    promised_by BIGINT NULL,
    promised_at DATETIME NULL,
    promise_remark VARCHAR(256) NULL,
    noticed_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    shipped_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    invoiced_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    quotation_line_id BIGINT NULL,
    line_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/SHIPPED/CLOSED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '销售订单明细';
CREATE INDEX idx_sal_order_line_order ON sal_order_line (order_id);
CREATE INDEX idx_sal_order_line_material ON sal_order_line (material_id);
CREATE INDEX idx_sal_order_line_part ON sal_order_line (customer_part_id);

CREATE TABLE sal_order_snapshot (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_version INT NOT NULL,
    content TEXT NOT NULL COMMENT '变更前的单头和全部行（JSON）',
    change_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '销售订单版本快照';
CREATE INDEX idx_sal_order_snapshot_order ON sal_order_snapshot (order_id);

CREATE TABLE sal_order_exec (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_line_id BIGINT NULL,
    exec_type VARCHAR(16) NOT NULL COMMENT 'NOTICE/SHIP/SHIP_REVERSE/BL/INVOICE/RECEIPT/RETURN',
    doc_type VARCHAR(32) NULL,
    doc_id BIGINT NULL,
    doc_no VARCHAR(64) NULL,
    qty DECIMAL(18,4) NULL COMMENT '基本单位',
    amount DECIMAL(18,2) NULL COMMENT '原币',
    exec_date DATE NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '订单执行记录（出货、开票、回款等回写流水，用于执行情况与幂等）';
CREATE INDEX idx_sal_order_exec_order ON sal_order_exec (order_id);
CREATE INDEX idx_sal_order_exec_doc ON sal_order_exec (doc_type, doc_id);

CREATE TABLE sal_payment_plan (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    seq INT NOT NULL,
    batch_no INT NOT NULL DEFAULT 0 COMMENT '出货类节点的批次（第 n 次出货），0 表示整单节点或尚未拆分的余额',
    node_name VARCHAR(64) NOT NULL,
    percent DECIMAL(9,4) NOT NULL,
    base_event VARCHAR(20) NOT NULL,
    days INT NOT NULL DEFAULT 0,
    plan_amount DECIMAL(18,2) NOT NULL,
    shipment_id BIGINT NULL COMMENT '出货类批次计划对应的出货单',
    event_date DATE NULL,
    due_date DATE NULL,
    received_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    plan_status VARCHAR(16) NOT NULL DEFAULT 'NOT_DUE' COMMENT 'NOT_DUE/DUE/OVERDUE/PARTIAL/RECEIVED/CANCELED',
    remark VARCHAR(256) NULL COMMENT '催收备注',
    promised_pay_date DATE NULL COMMENT '客户承诺付款日期',
    followed_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '回款计划';
CREATE INDEX idx_sal_payment_plan_order ON sal_payment_plan (order_id);
CREATE INDEX idx_sal_payment_plan_due ON sal_payment_plan (due_date);

-- ==================== 订单变更（04-04） ====================

CREATE TABLE sal_order_change (
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
    order_id BIGINT NOT NULL,
    order_version_from INT NOT NULL,
    change_reason VARCHAR(32) NOT NULL,
    reason_remark VARCHAR(512) NOT NULL,
    amount_before DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_after DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_change_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    header_changes TEXT NULL COMMENT '[{field, label, old, new}]',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sal_order_change_no UNIQUE (doc_no)
) COMMENT '销售订单变更单';
CREATE INDEX idx_sal_order_change_order ON sal_order_change (order_id);

CREATE TABLE sal_order_change_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    change_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    change_type VARCHAR(16) NOT NULL COMMENT 'ADD/MODIFY/CANCEL',
    order_line_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    uom VARCHAR(16) NULL,
    old_qty DECIMAL(18,4) NULL,
    new_qty DECIMAL(18,4) NULL,
    old_price DECIMAL(18,6) NULL COMMENT '按订单 tax_included 的录入单价',
    new_price DECIMAL(18,6) NULL,
    old_required_date DATE NULL,
    new_required_date DATE NULL,
    new_customer_part_no VARCHAR(64) NULL,
    new_description VARCHAR(512) NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '订单变更明细';
CREATE INDEX idx_sal_order_change_line ON sal_order_change_line (change_id);

-- ==================== 销售预测（04-05） ====================

CREATE TABLE sal_forecast (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(64)   NOT NULL,
    doc_date           DATE          NULL,
    status             VARCHAR(20)   NOT NULL COMMENT 'DRAFT/APPROVED（已发布）/CLOSED',
    org_id             BIGINT        NULL,
    dept_id            BIGINT        NULL,
    owner_id           BIGINT        NULL,
    source_type        VARCHAR(32)   NULL,
    source_id          BIGINT        NULL,
    source_no          VARCHAR(64)   NULL,
    remark             VARCHAR(1000) NULL,
    title VARCHAR(128) NOT NULL,
    start_period VARCHAR(6) NOT NULL,
    end_period VARCHAR(6) NOT NULL,
    published_at DATETIME NULL,
    close_reason VARCHAR(256) NULL,
    revised_from_id BIGINT NULL COMMENT '修订来源（发布时关闭并迁移冲销记录）',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sal_forecast_no UNIQUE (doc_no)
) COMMENT '销售预测';

CREATE TABLE sal_forecast_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    forecast_id BIGINT NOT NULL,
    customer_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    period VARCHAR(6) NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    consumed_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '销售预测明细（客户 + 物料 + 月份）';
CREATE INDEX idx_sal_forecast_line_fc ON sal_forecast_line (forecast_id);
CREATE INDEX idx_sal_forecast_line_mp ON sal_forecast_line (material_id, period);

CREATE TABLE sal_forecast_consumption (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    forecast_line_id BIGINT NOT NULL,
    order_line_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '预测冲销记录';
CREATE INDEX idx_sal_fc_consume_line ON sal_forecast_consumption (forecast_line_id);
CREATE INDEX idx_sal_fc_consume_order ON sal_forecast_consumption (order_line_id);

-- ==================== 销售退货（04-06） ====================

CREATE TABLE sal_return (
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
    rma_no VARCHAR(64) NULL,
    return_reason VARCHAR(32) NOT NULL,
    handling VARCHAR(16) NOT NULL COMMENT 'REFUND/REPLACE',
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,8) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    complaint_no VARCHAR(64) NULL,
    stock_in_id BIGINT NULL,
    expected_arrival_date DATE NULL,
    void_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sal_return_no UNIQUE (doc_no)
) COMMENT '销售退货单';
CREATE INDEX idx_sal_return_customer ON sal_return (customer_id);

CREATE TABLE sal_return_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_id BIGINT NOT NULL,
    order_line_id BIGINT NOT NULL,
    shipment_line_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NULL,
    serial_nos TEXT NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '基本单位',
    price_incl_tax DECIMAL(18,6) NOT NULL COMMENT '每基本单位',
    tax_rate DECIMAL(9,4) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    received_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    good_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    rework_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '销售退货明细';
CREATE INDEX idx_sal_return_line_return ON sal_return_line (return_id);
CREATE INDEX idx_sal_return_line_order ON sal_return_line (order_line_id);
