-- 资材模块（需求 07-资材）：供应商、采购价格、采购申请、询价、采购订单与变更、到货、委外、采购退货、对账、供应商评估

CREATE TABLE pur_supplier (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    name_en VARCHAR(256) NULL,
    short_name VARCHAR(32) NOT NULL,
    supplier_type VARCHAR(32) NOT NULL COMMENT '字典 pur_supplier_type',
    supplier_level VARCHAR(8) NOT NULL DEFAULT 'C' COMMENT '字典 pur_supplier_level',
    supplier_status VARCHAR(16) NOT NULL COMMENT 'POTENTIAL/PENDING/QUALIFIED/SUSPENDED/ELIMINATED',
    country VARCHAR(2) NOT NULL DEFAULT 'CN',
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    address VARCHAR(256) NULL,
    tax_no VARCHAR(32) NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    website VARCHAR(128) NULL,
    buyer_id BIGINT NOT NULL COMMENT '负责采购员',
    org_id BIGINT NULL,
    dept_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_term_id BIGINT NOT NULL,
    trade_term VARCHAR(16) NULL,
    purchase_tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0.13,
    invoice_type VARCHAR(16) NOT NULL DEFAULT 'SPECIAL_VAT',
    lead_time_days INT NULL,
    qualified_at DATE NULL,
    suspend_reason VARCHAR(512) NULL,
    remark VARCHAR(1000) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_supplier_code UNIQUE (code)
) COMMENT '供应商';

CREATE TABLE pur_supplier_contact (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    title VARCHAR(64) NULL,
    contact_role VARCHAR(32) NULL COMMENT '业务/品质/财务/工程',
    phone VARCHAR(32) NULL,
    mobile VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '供应商联系人';

CREATE TABLE pur_supplier_bank (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_no VARCHAR(64) NOT NULL,
    swift VARCHAR(32) NULL,
    currency VARCHAR(3) NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '供应商银行账户';

CREATE TABLE pur_supplier_cert (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    cert_type VARCHAR(32) NOT NULL COMMENT '字典 pur_cert_type',
    cert_no VARCHAR(64) NULL,
    issue_date DATE NULL,
    expire_date DATE NULL COMMENT '空表示长期',
    file_id BIGINT NULL COMMENT '证书文件',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '供应商资质';

CREATE TABLE pur_supplier_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    supplier_part_no VARCHAR(64) NULL,
    supply_status VARCHAR(16) NOT NULL DEFAULT 'TRIAL' COMMENT 'TRIAL/QUALIFIED/DISABLED',
    is_default TINYINT NOT NULL DEFAULT 0,
    lead_time_days INT NULL,
    moq DECIMAL(18,4) NULL,
    mpq DECIMAL(18,4) NULL,
    quota_pct DECIMAL(9,4) NULL,
    approved_at DATE NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_supplier_material UNIQUE (supplier_id, material_id)
) COMMENT '可供物料';

CREATE TABLE pur_price (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    min_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    price DECIMAL(18,6) NOT NULL COMMENT '不含税单价（基本单位）',
    tax_rate DECIMAL(9,4) NOT NULL,
    price_incl_tax DECIMAL(18,6) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    price_status VARCHAR(16) NOT NULL COMMENT 'EFFECTIVE/EXPIRED/REPLACED',
    adjust_id BIGINT NOT NULL,
    adjust_line_id BIGINT NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '生效采购价格';

CREATE TABLE pur_price_adjust (
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
    currency VARCHAR(3) NOT NULL,
    adjust_reason VARCHAR(512) NOT NULL,
    adjust_source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/RFQ/IMPORT',
    rfq_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_price_adjust_no UNIQUE (doc_no)
) COMMENT '调价单';

CREATE TABLE pur_price_adjust_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    adjust_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    min_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    old_price DECIMAL(18,6) NULL,
    new_price DECIMAL(18,6) NOT NULL,
    tax_rate DECIMAL(9,4) NOT NULL,
    change_pct DECIMAL(9,4) NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '调价明细';

CREATE TABLE pur_requisition (
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
    requisition_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    request_dept_id BIGINT NULL,
    mrp_run_id BIGINT NULL,
    urgent TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_requisition_no UNIQUE (doc_no)
) COMMENT '采购申请';

CREATE TABLE pur_requisition_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    requisition_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    uom VARCHAR(16) NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    base_qty DECIMAL(18,4) NOT NULL,
    required_date DATE NOT NULL,
    suggested_supplier_id BIGINT NULL,
    reference_price DECIMAL(18,6) NULL,
    purpose VARCHAR(256) NULL,
    ordered_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    line_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ORDERED/CLOSED',
    mrp_result_id BIGINT NULL,
    source_demand VARCHAR(256) NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '采购申请明细';

CREATE TABLE pur_rfq (
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
    title VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    quote_deadline DATE NOT NULL,
    rfq_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/QUOTING/COMPARING/AWARDED/CANCELED',
    cancel_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_rfq_no UNIQUE (doc_no)
) COMMENT '询价单';

CREATE TABLE pur_rfq_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    required_date DATE NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '询价物料';

CREATE TABLE pur_rfq_supplier (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    sent_at DATETIME NULL,
    quoted TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '询价供应商';

CREATE TABLE pur_rfq_quote (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    rfq_line_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    price DECIMAL(18,6) NOT NULL,
    tax_rate DECIMAL(9,4) NOT NULL,
    moq DECIMAL(18,4) NULL,
    lead_time_days INT NULL,
    valid_until DATE NULL,
    is_awarded TINYINT NOT NULL DEFAULT 0,
    award_qty_pct DECIMAL(9,4) NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_rfq_quote UNIQUE (rfq_line_id, supplier_id)
) COMMENT '报价';

CREATE TABLE pur_order (
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
    order_type VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT 'STANDARD/SAMPLE',
    supplier_id BIGINT NOT NULL,
    supplier_contact_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1,
    payment_term_id BIGINT NOT NULL,
    payment_term_snapshot VARCHAR(2000) NULL,
    trade_term VARCHAR(16) NULL,
    tax_included TINYINT NOT NULL DEFAULT 1,
    delivery_address VARCHAR(256) NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    order_version INT NOT NULL DEFAULT 1,
    has_price_overrun TINYINT NOT NULL DEFAULT 0,
    sent_at DATETIME NULL,
    close_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_order_no UNIQUE (doc_no)
) COMMENT '采购订单';

CREATE TABLE pur_order_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    supplier_part_no VARCHAR(64) NULL,
    uom VARCHAR(16) NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    base_qty DECIMAL(18,4) NOT NULL,
    price DECIMAL(18,6) NOT NULL,
    price_incl_tax DECIMAL(18,6) NOT NULL,
    tax_rate DECIMAL(9,4) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    list_price DECIMAL(18,6) NULL,
    price_overrun TINYINT NOT NULL DEFAULT 0,
    required_date DATE NOT NULL,
    confirmed_date DATE NULL,
    received_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    stocked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    replace_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '换货退回数量（恢复未到货）',
    statement_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    first_received_date DATE NULL COMMENT '首次到货日期',
    requisition_line_id BIGINT NULL,
    line_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RECEIVED/CLOSED',
    last_follow_up VARCHAR(512) NULL,
    follow_up_at DATETIME NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '采购订单明细';

CREATE TABLE pur_order_change (
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
    change_reason VARCHAR(512) NOT NULL,
    new_version INT NOT NULL,
    amount_change_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    snapshot TEXT NULL COMMENT '变更前订单快照（json）',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_order_change_no UNIQUE (doc_no)
) COMMENT '采购订单变更单';

CREATE TABLE pur_order_change_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    change_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_line_id BIGINT NULL,
    change_type VARCHAR(16) NOT NULL COMMENT 'ADD/MODIFY/CANCEL',
    material_id BIGINT NOT NULL,
    uom VARCHAR(16) NULL,
    old_qty DECIMAL(18,4) NULL,
    new_qty DECIMAL(18,4) NULL,
    old_price DECIMAL(18,6) NULL,
    new_price DECIMAL(18,6) NULL COMMENT '不含税单价',
    tax_rate DECIMAL(9,4) NULL,
    old_required_date DATE NULL,
    new_required_date DATE NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '订单变更明细';

CREATE TABLE pur_receipt (
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
    delivery_note_no VARCHAR(64) NULL,
    arrival_at DATETIME NOT NULL,
    receipt_type VARCHAR(16) NOT NULL DEFAULT 'PURCHASE' COMMENT 'PURCHASE/OUTSOURCE/SAMPLE',
    receiver_id BIGINT NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_receipt_no UNIQUE (doc_no)
) COMMENT '到货单';

CREATE TABLE pur_receipt_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_id BIGINT NOT NULL,
    order_line_id BIGINT NULL COMMENT '委外收货为空',
    material_id BIGINT NOT NULL,
    uom VARCHAR(16) NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    base_qty DECIMAL(18,4) NOT NULL,
    supplier_batch_no VARCHAR(64) NULL,
    production_date DATE NULL,
    inspect_required TINYINT NOT NULL DEFAULT 0,
    target_warehouse_id BIGINT NULL,
    stock_in_id BIGINT NULL,
    stock_in_no VARCHAR(64) NULL,
    stocked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    stocked_date DATE NULL COMMENT '入库确认日期',
    batch_no VARCHAR(64) NULL,
    inspect_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/QUALIFIED/CONCESSION/REJECTED/PARTIAL',
    qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    concession_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    rejected_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    statement_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    inspection_no VARCHAR(64) NULL,
    judged_date DATE NULL,
    reject_reason VARCHAR(256) NULL COMMENT '入库被退回原因',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '到货明细';

CREATE TABLE pur_outsourcing (
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
    material_id BIGINT NOT NULL,
    bom_id BIGINT NOT NULL,
    bom_snapshot VARCHAR(2000) NULL,
    qty DECIMAL(18,4) NOT NULL,
    process_price DECIMAL(18,6) NOT NULL,
    tax_rate DECIMAL(9,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    required_date DATE NOT NULL,
    received_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    statement_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    mrp_result_id BIGINT NULL,
    close_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_outsourcing_no UNIQUE (doc_no)
) COMMENT '委外单';

CREATE TABLE pur_outsourcing_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    outsourcing_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    uom VARCHAR(16) NULL,
    qty_per DECIMAL(18,4) NOT NULL,
    required_qty DECIMAL(18,4) NOT NULL,
    issued_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    consumed_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    loss_qty DECIMAL(18,4) NULL,
    loss_reason VARCHAR(256) NULL,
    adjust_reason VARCHAR(256) NULL COMMENT '调整应发数量原因',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '委外用料';

CREATE TABLE pur_outsourcing_txn (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    outsourcing_id BIGINT NOT NULL,
    outsourcing_material_id BIGINT NOT NULL,
    txn_type VARCHAR(16) NOT NULL COMMENT 'ISSUE 发料 / RETURN 余料退回',
    stock_doc_id BIGINT NOT NULL COMMENT '仓库出库单/入库单',
    stock_doc_no VARCHAR(64) NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '基本单位',
    reversed TINYINT NOT NULL DEFAULT 0 COMMENT '仓库已反确认',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_outsourcing_txn UNIQUE (stock_doc_id, outsourcing_material_id, txn_type)
) COMMENT '委外发料/退料记录（仓库确认后写入，用于回写已发、退回数量）';

CREATE TABLE pur_return (
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
    return_reason VARCHAR(32) NOT NULL COMMENT '字典 pur_return_reason',
    handling VARCHAR(16) NOT NULL COMMENT 'REFUND/REPLACE',
    warehouse_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    stock_out_id BIGINT NULL,
    stock_out_no VARCHAR(64) NULL,
    ncr_no VARCHAR(64) NULL,
    void_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_return_no UNIQUE (doc_no)
) COMMENT '采购退货单';

CREATE TABLE pur_return_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    receipt_line_id BIGINT NOT NULL,
    order_line_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '基本单位',
    price_incl_tax DECIMAL(18,6) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    out_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    out_date DATE NULL,
    statement_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '采购退货明细';

CREATE TABLE pur_statement (
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
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1,
    goods_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    return_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    adjust_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    supplier_confirmed_at DATETIME NULL,
    supplier_confirmer VARCHAR(64) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_statement_no UNIQUE (doc_no)
) COMMENT '供应商对账单';

CREATE TABLE pur_statement_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    statement_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    line_type VARCHAR(16) NOT NULL COMMENT 'GOODS/RETURN/PROCESS_FEE/ADJUST',
    source_type VARCHAR(32) NULL,
    source_id BIGINT NULL,
    source_line_id BIGINT NULL,
    source_no VARCHAR(64) NULL,
    order_line_id BIGINT NULL,
    order_no VARCHAR(64) NULL,
    material_id BIGINT NULL,
    biz_date DATE NOT NULL,
    qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    price_incl_tax DECIMAL(18,6) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '对账明细';

CREATE TABLE pur_supplier_score (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    period VARCHAR(7) NOT NULL,
    lot_count INT NOT NULL DEFAULT 0,
    lot_pass_count INT NOT NULL DEFAULT 0,
    quality_score DECIMAL(9,4) NULL,
    due_line_count INT NOT NULL DEFAULT 0,
    ontime_line_count INT NOT NULL DEFAULT 0,
    delivery_score DECIMAL(9,4) NULL,
    price_score DECIMAL(9,4) NULL,
    service_score DECIMAL(9,4) NULL,
    total_score DECIMAL(9,4) NULL,
    grade VARCHAR(1) NULL,
    score_status VARCHAR(16) NOT NULL COMMENT 'CALCULATED/SCORED/PUBLISHED',
    score_comment VARCHAR(512) NULL,
    unpublish_reason VARCHAR(256) NULL,
    published_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pur_supplier_score UNIQUE (supplier_id, period)
) COMMENT '供应商评估';

CREATE UNIQUE INDEX uk_pur_supplier_name ON pur_supplier (name);
CREATE INDEX idx_pur_supplier_buyer_id ON pur_supplier (buyer_id);
CREATE INDEX idx_pur_supplier_tax_no ON pur_supplier (tax_no);
CREATE INDEX idx_pur_supplier_contact_supplier_id ON pur_supplier_contact (supplier_id);
CREATE INDEX idx_pur_supplier_bank_supplier_id ON pur_supplier_bank (supplier_id);
CREATE INDEX idx_pur_supplier_cert_supplier_id ON pur_supplier_cert (supplier_id);
CREATE INDEX idx_pur_supplier_cert_expire_date ON pur_supplier_cert (expire_date);
CREATE INDEX idx_pur_supplier_material_material_id ON pur_supplier_material (material_id);
CREATE INDEX idx_pur_price_sup_mat ON pur_price (supplier_id, material_id, price_status);
CREATE INDEX idx_pur_price_material_id ON pur_price (material_id);
CREATE INDEX idx_pur_price_adjust_supplier_id ON pur_price_adjust (supplier_id);
CREATE INDEX idx_pur_price_adjust_line_adjust_id ON pur_price_adjust_line (adjust_id);
CREATE INDEX idx_pur_requisition_line_requisition_id ON pur_requisition_line (requisition_id);
CREATE INDEX idx_pur_requisition_line_material_id ON pur_requisition_line (material_id);
CREATE INDEX idx_pur_rfq_line_rfq_id ON pur_rfq_line (rfq_id);
CREATE INDEX idx_pur_rfq_supplier_rfq_id ON pur_rfq_supplier (rfq_id);
CREATE INDEX idx_pur_rfq_supplier_supplier_id ON pur_rfq_supplier (supplier_id);
CREATE INDEX idx_pur_rfq_quote_rfq_id ON pur_rfq_quote (rfq_id);
CREATE INDEX idx_pur_order_supplier_id ON pur_order (supplier_id);
CREATE INDEX idx_pur_order_owner_id ON pur_order (owner_id);
CREATE INDEX idx_pur_order_line_order_id ON pur_order_line (order_id);
CREATE INDEX idx_pur_order_line_material_id ON pur_order_line (material_id);
CREATE INDEX idx_pur_order_line_requisition_line_id ON pur_order_line (requisition_line_id);
CREATE INDEX idx_pur_order_change_order_id ON pur_order_change (order_id);
CREATE INDEX idx_pur_order_change_line_change_id ON pur_order_change_line (change_id);
CREATE INDEX idx_pur_receipt_supplier_id ON pur_receipt (supplier_id);
CREATE INDEX idx_pur_receipt_line_receipt_id ON pur_receipt_line (receipt_id);
CREATE INDEX idx_pur_receipt_line_order_line_id ON pur_receipt_line (order_line_id);
CREATE INDEX idx_pur_receipt_line_order_id ON pur_receipt_line (order_id);
CREATE INDEX idx_pur_outsourcing_supplier_id ON pur_outsourcing (supplier_id);
CREATE INDEX idx_pur_outsourcing_material_id ON pur_outsourcing (material_id);
CREATE INDEX idx_pur_outsourcing_material_outsourcing_id ON pur_outsourcing_material (outsourcing_id);
CREATE INDEX idx_pur_outsourcing_txn_os ON pur_outsourcing_txn (outsourcing_id);
CREATE INDEX idx_pur_return_supplier_id ON pur_return (supplier_id);
CREATE INDEX idx_pur_return_line_return_id ON pur_return_line (return_id);
CREATE INDEX idx_pur_return_line_receipt_line_id ON pur_return_line (receipt_line_id);
CREATE INDEX idx_pur_statement_supplier_id ON pur_statement (supplier_id);
CREATE INDEX idx_pur_statement_line_statement_id ON pur_statement_line (statement_id);
CREATE INDEX idx_pur_statement_line_source_line_id ON pur_statement_line (source_line_id);
