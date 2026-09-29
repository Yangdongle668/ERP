-- 出货模块（需求 11-出货）：出货通知、拣货、装箱、出货单、单证（Packing List / Invoice / 报关）、货代与物流跟踪

CREATE TABLE shp_notice (
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
    currency VARCHAR(3) NOT NULL,
    ship_date DATE NOT NULL COMMENT '计划出货日期',
    transport_mode VARCHAR(16) NOT NULL COMMENT '字典 shp_transport_mode',
    trade_term VARCHAR(16) NULL,
    port_of_loading VARCHAR(64) NULL,
    port_of_destination VARCHAR(64) NULL,
    ship_to_address_id BIGINT NULL,
    ship_to_snapshot TEXT NULL,
    notify_party VARCHAR(512) NULL,
    forwarder_id BIGINT NULL,
    warehouse_id BIGINT NOT NULL,
    oqc_required TINYINT NOT NULL DEFAULT 0,
    oqc_result VARCHAR(16) NULL COMMENT 'PENDING / PASSED / REJECTED',
    oqc_inspection_ids VARCHAR(512) NULL COMMENT '本轮 OQC 检验单 ID（逗号分隔）',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '价税合计（原币）',
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    credit_warning TINYINT NOT NULL DEFAULT 0,
    prepayment_unpaid TINYINT NOT NULL DEFAULT 0,
    notice_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/APPROVED/PICKING/PACKED/OQC/READY/SHIPPED/CLOSED/VOIDED',
    approved_at DATETIME NULL,
    close_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_notice_no UNIQUE (doc_no)
) COMMENT '出货通知';

CREATE INDEX idx_shp_notice_customer ON shp_notice (customer_id, notice_status);

CREATE INDEX idx_shp_notice_date ON shp_notice (ship_date);

CREATE TABLE shp_notice_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    order_line_id BIGINT NOT NULL,
    order_line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    customer_part_no VARCHAR(64) NULL,
    description VARCHAR(512) NULL,
    uom VARCHAR(16) NOT NULL COMMENT '订单单位',
    qty DECIMAL(18,4) NOT NULL COMMENT '通知数量（订单单位）',
    base_qty DECIMAL(18,4) NOT NULL,
    price_incl_tax DECIMAL(18,6) NOT NULL COMMENT '含税单价（每订单单位）',
    base_price_incl_tax DECIMAL(18,6) NOT NULL COMMENT '含税单价（每基本单位）',
    tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    oqc_required TINYINT NOT NULL DEFAULT 0,
    picked_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已拣货（基本单位）',
    packed_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    shipped_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    shortage_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '按实拣完成的缺货数量（基本单位，已释放回订单）',
    shipping_plan_line_id BIGINT NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '出货通知明细';

CREATE INDEX idx_shp_notice_line_notice ON shp_notice_line (notice_id);

CREATE INDEX idx_shp_notice_line_order ON shp_notice_line (order_line_id);

CREATE TABLE shp_picking (
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
    notice_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    picker_id BIGINT NULL,
    picking_status VARCHAR(16) NOT NULL COMMENT 'WAITING/PICKING/DONE/CANCELED',
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_picking_no UNIQUE (doc_no)
) COMMENT '拣货单';

CREATE INDEX idx_shp_picking_notice ON shp_picking (notice_id);

CREATE TABLE shp_picking_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    picking_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    notice_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    location_id BIGINT NULL,
    batch_no VARCHAR(64) NULL,
    suggested_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    picked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    serial_nos TEXT NULL,
    shortage TINYINT NOT NULL DEFAULT 0 COMMENT '推荐时库存不足',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '拣货明细（一行通知可对应多个批次 / 库位）';

CREATE INDEX idx_shp_picking_line_picking ON shp_picking_line (picking_id);

CREATE TABLE shp_carton (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    carton_no INT NOT NULL,
    carton_spec VARCHAR(32) NULL COMMENT '字典 shp_carton_spec',
    length_cm DECIMAL(10,2) NULL,
    width_cm DECIMAL(10,2) NULL,
    height_cm DECIMAL(10,2) NULL,
    gross_weight_kg DECIMAL(12,3) NULL,
    net_weight_kg DECIMAL(12,3) NULL,
    cbm DECIMAL(12,4) NULL,
    pallet_no VARCHAR(16) NULL,
    shipment_id BIGINT NULL COMMENT '随哪张出货单出货',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '装箱：箱';

CREATE INDEX idx_shp_carton_notice ON shp_carton (notice_id, carton_no);

CREATE TABLE shp_carton_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    carton_id BIGINT NOT NULL,
    notice_id BIGINT NOT NULL,
    notice_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '基本单位',
    serial_nos TEXT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '装箱：箱内明细';

CREATE INDEX idx_shp_carton_line_carton ON shp_carton_line (carton_id);

CREATE INDEX idx_shp_carton_line_notice ON shp_carton_line (notice_id);

CREATE TABLE shp_shipment (
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
    notice_id BIGINT NOT NULL,
    notice_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1,
    ship_date DATE NOT NULL,
    transport_mode VARCHAR(16) NOT NULL,
    trade_term VARCHAR(16) NULL,
    port_of_loading VARCHAR(64) NULL,
    port_of_destination VARCHAR(64) NULL,
    ship_to_snapshot TEXT NULL,
    warehouse_id BIGINT NOT NULL,
    total_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    carton_count INT NULL,
    gross_weight DECIMAL(12,3) NULL,
    net_weight DECIMAL(12,3) NULL,
    cbm DECIMAL(12,4) NULL,
    stock_out_id BIGINT NULL,
    packing_list_id BIGINT NULL,
    invoice_id BIGINT NULL,
    customs_id BIGINT NULL,
    forwarder_id BIGINT NULL,
    container_no VARCHAR(32) NULL,
    seal_no VARCHAR(32) NULL,
    bl_no VARCHAR(64) NULL,
    bl_date DATE NULL,
    etd DATE NULL,
    eta DATE NULL,
    logistics_status VARCHAR(16) NULL COMMENT '字典 shp_logistics_status',
    logistics_updated_at DATETIME NULL,
    signed_at DATETIME NULL,
    signed_by VARCHAR(64) NULL,
    credit_warning TINYINT NOT NULL DEFAULT 0,
    prepayment_unpaid TINYINT NOT NULL DEFAULT 0,
    shipment_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/SUBMITTED/SHIPPED/COMPLETED/VOIDED',
    shipped_at DATETIME NULL,
    eta_reminded TINYINT NOT NULL DEFAULT 0,
    void_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_shipment_no UNIQUE (doc_no)
) COMMENT '出货单';

CREATE INDEX idx_shp_shipment_notice ON shp_shipment (notice_id);

CREATE INDEX idx_shp_shipment_customer ON shp_shipment (customer_id, ship_date);

CREATE TABLE shp_shipment_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    notice_line_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    order_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    customer_part_no VARCHAR(64) NULL,
    description VARCHAR(512) NULL,
    uom VARCHAR(16) NOT NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '订单单位',
    base_qty DECIMAL(18,4) NOT NULL,
    batch_no VARCHAR(64) NULL,
    serial_nos TEXT NULL,
    price_incl_tax DECIMAL(18,6) NOT NULL,
    tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '不含税',
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    out_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '出货单明细（一行一个批次）';

CREATE INDEX idx_shp_shipment_line_shipment ON shp_shipment_line (shipment_id);

CREATE INDEX idx_shp_shipment_line_order ON shp_shipment_line (order_line_id);

CREATE INDEX idx_shp_shipment_line_batch ON shp_shipment_line (material_id, batch_no);

CREATE TABLE shp_packing_list (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    pl_no VARCHAR(32) NOT NULL,
    pl_date DATE NOT NULL,
    consignee VARCHAR(512) NULL,
    notify_party VARCHAR(512) NULL,
    shipping_marks VARCHAR(1000) NULL,
    line_data TEXT NULL COMMENT '行快照 JSON',
    total_data TEXT NULL COMMENT '合计 JSON',
    remark VARCHAR(1000) NULL,
    invalid TINYINT NOT NULL DEFAULT 0 COMMENT '出货单反确认 / 作废后失效',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_packing_list_no UNIQUE (pl_no)
) COMMENT 'Packing List';

CREATE TABLE shp_invoice (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    invoice_no VARCHAR(32) NOT NULL,
    invoice_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    bill_to VARCHAR(512) NULL,
    consignee VARCHAR(512) NULL,
    notify_party VARCHAR(512) NULL,
    currency VARCHAR(3) NOT NULL,
    trade_term VARCHAR(64) NULL,
    payment_term_text VARCHAR(256) NULL,
    port_of_loading VARCHAR(64) NULL,
    port_of_destination VARCHAR(64) NULL,
    vessel_flight VARCHAR(64) NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_in_words VARCHAR(512) NULL,
    bank_info VARCHAR(1000) NULL,
    remark VARCHAR(1000) NULL,
    invalid TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_invoice_no UNIQUE (invoice_no)
) COMMENT 'Commercial Invoice';

CREATE TABLE shp_invoice_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    shipment_line_id BIGINT NULL,
    order_no VARCHAR(64) NULL,
    customer_po_no VARCHAR(64) NULL,
    customer_part_no VARCHAR(64) NULL,
    description VARCHAR(512) NULL,
    hs_code VARCHAR(16) NULL,
    origin VARCHAR(32) NULL,
    qty DECIMAL(18,4) NOT NULL,
    uom VARCHAR(16) NULL,
    unit_price DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'Invoice 明细';

CREATE INDEX idx_shp_invoice_line_invoice ON shp_invoice_line (invoice_id);

CREATE TABLE shp_customs (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    doc_code VARCHAR(32) NOT NULL COMMENT '编码规则 SHP_CUSTOMS',
    customs_no VARCHAR(32) NULL COMMENT '报关单号（报关后回填）',
    declare_date DATE NULL,
    trade_mode VARCHAR(32) NULL,
    declare_port VARCHAR(64) NULL,
    destination_country VARCHAR(64) NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(1000) NULL,
    invalid TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_customs_code UNIQUE (doc_code)
) COMMENT '报关资料';

CREATE TABLE shp_customs_item (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customs_id BIGINT NOT NULL,
    seq INT NOT NULL,
    hs_code VARCHAR(16) NULL COMMENT '物料无 HS 编码时由单证员填写',
    declare_name VARCHAR(128) NOT NULL,
    declare_elements VARCHAR(512) NULL,
    qty DECIMAL(18,4) NOT NULL,
    uom VARCHAR(16) NULL,
    second_qty DECIMAL(18,4) NULL,
    second_uom VARCHAR(16) NULL,
    unit_price DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    origin VARCHAR(32) NULL,
    net_weight DECIMAL(12,3) NULL,
    gross_weight DECIMAL(12,3) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '报关商品（按 HS 编码合并）';

CREATE INDEX idx_shp_customs_item_customs ON shp_customs_item (customs_id);

CREATE TABLE shp_forwarder (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    contact VARCHAR(64) NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    services VARCHAR(64) NULL COMMENT '运输方式，逗号分隔',
    forwarder_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shp_forwarder_code UNIQUE (code)
) COMMENT '货代';

CREATE TABLE shp_logistics_event (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    logistics_status VARCHAR(16) NOT NULL,
    occurred_at DATETIME NOT NULL,
    location VARCHAR(128) NULL,
    remark VARCHAR(256) NULL,
    operator_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '物流记录';

CREATE INDEX idx_shp_logistics_event_shipment ON shp_logistics_event (shipment_id, occurred_at);
