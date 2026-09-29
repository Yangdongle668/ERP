-- PMC 模块（需求 06-PMC）：需求池、MPS、MRP（运算、建议、追溯、例外、供需平衡）、产能日历、排产、缺料快照、交期预警、出货计划
CREATE TABLE pmc_demand (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    demand_type VARCHAR(16) NOT NULL COMMENT 'SALES_ORDER/FORECAST/MANUAL',
    source_id BIGINT NULL COMMENT '销售订单 / 预测单',
    source_line_id BIGINT NULL COMMENT '销售订单行 / 预测行',
    source_no VARCHAR(64) NULL,
    source_line_no INT NULL,
    customer_id BIGINT NULL,
    sales_owner_id BIGINT NULL COMMENT '业务员',
    material_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '需求数量（基本单位）',
    fulfilled_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已满足：订单已出货 / 预测已冲销',
    open_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    required_date DATE NOT NULL COMMENT '需求日期：承诺交期优先',
    customer_date DATE NULL COMMENT '客户要求交期',
    promised_date DATE NULL COMMENT '承诺交期',
    priority INT NOT NULL DEFAULT 5,
    demand_status VARCHAR(16) NOT NULL COMMENT 'OPEN/CLOSED',
    reply_status VARCHAR(16) NULL COMMENT '销售订单：PENDING 待回复 / REPLIED 已回复 / REREPLY 需重新回复',
    replied_qty DECIMAL(18,4) NULL COMMENT '回复时的数量（变更检测）',
    replied_customer_date DATE NULL COMMENT '回复时的要求交期',
    reply_remark VARCHAR(256) NULL,
    replied_by BIGINT NULL,
    replied_at DATETIME NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '需求池';
CREATE INDEX idx_pmc_demand_material ON pmc_demand (material_id, required_date);
CREATE INDEX idx_pmc_demand_source ON pmc_demand (demand_type, source_line_id);
CREATE INDEX idx_pmc_demand_order ON pmc_demand (demand_type, source_id);

CREATE TABLE pmc_mps (
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
    start_week VARCHAR(8) NOT NULL COMMENT 'ISO 周 2026-W40',
    end_week VARCHAR(8) NOT NULL,
    mps_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PUBLISHED/CLOSED',
    published_at DATETIME NULL,
    copied_from_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmc_mps_no UNIQUE (doc_no)
) COMMENT 'MPS 主生产计划';

CREATE TABLE pmc_mps_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    mps_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    week VARCHAR(8) NOT NULL,
    demand_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    wip_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '在制完工',
    planned_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    projected_on_hand DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(128) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'MPS 明细（物料 × 周）';
CREATE INDEX idx_pmc_mps_line ON pmc_mps_line (mps_id, material_id);

CREATE TABLE pmc_mrp_run (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    run_no VARCHAR(32) NOT NULL,
    run_type VARCHAR(16) NOT NULL COMMENT 'FULL/NET_CHANGE/ORDER',
    scope TEXT NULL COMMENT '运算范围 JSON',
    params TEXT NULL COMMENT '参数快照 JSON',
    run_status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    material_count INT NOT NULL DEFAULT 0,
    suggestion_count INT NOT NULL DEFAULT 0,
    exception_count INT NOT NULL DEFAULT 0,
    progress INT NOT NULL DEFAULT 0,
    error_msg VARCHAR(2000) NULL,
    operator_id BIGINT NULL,
    task_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmc_mrp_run_no UNIQUE (run_no)
) COMMENT 'MRP 运算记录';
CREATE INDEX idx_pmc_mrp_run_status ON pmc_mrp_run (run_status);

CREATE TABLE pmc_mrp_result (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    suggestion_type VARCHAR(16) NOT NULL COMMENT 'PURCHASE/MAKE/OUTSOURCE',
    material_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '建议数量（已按批量规则取整）',
    original_qty DECIMAL(18,4) NOT NULL COMMENT '运算得到的建议数量',
    net_requirement DECIMAL(18,4) NOT NULL,
    required_date DATE NOT NULL,
    release_date DATE NOT NULL,
    original_required_date DATE NOT NULL,
    is_late TINYINT NOT NULL DEFAULT 0,
    supplier_id BIGINT NULL,
    planner_id BIGINT NULL,
    buyer_id BIGINT NULL,
    bom_id BIGINT NULL,
    dept_id BIGINT NULL,
    suggestion_status VARCHAR(16) NOT NULL COMMENT 'PENDING/CONVERTED/IGNORED/SUPERSEDED',
    converted_doc_type VARCHAR(32) NULL,
    converted_doc_id BIGINT NULL,
    converted_doc_no VARCHAR(64) NULL,
    ignore_reason VARCHAR(256) NULL,
    handled_by BIGINT NULL,
    handled_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'MRP 建议';
CREATE INDEX idx_pmc_mrp_result_run ON pmc_mrp_result (run_id, suggestion_type);
CREATE INDEX idx_pmc_mrp_result_status ON pmc_mrp_result (suggestion_status);

CREATE TABLE pmc_mrp_pegging (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    result_id BIGINT NOT NULL,
    demand_type VARCHAR(16) NOT NULL COMMENT 'SALES_ORDER/FORECAST/MANUAL/MPS/SAFETY_STOCK/PARENT/ALLOCATION',
    source_id BIGINT NULL,
    source_no VARCHAR(64) NULL,
    parent_material_id BIGINT NULL,
    parent_result_id BIGINT NULL,
    qty DECIMAL(18,4) NOT NULL,
    required_date DATE NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'MRP 需求追溯';
CREATE INDEX idx_pmc_mrp_pegging ON pmc_mrp_pegging (result_id);

CREATE TABLE pmc_mrp_exception (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    exception_type VARCHAR(16) NOT NULL COMMENT 'EXPEDITE/DEFER/CANCEL/PAST_DUE/DISABLED',
    doc_type VARCHAR(32) NULL,
    doc_id BIGINT NULL,
    doc_no VARCHAR(64) NULL,
    doc_line_id BIGINT NULL,
    supply_date DATE NULL COMMENT '当前供应日期',
    suggested_date DATE NULL,
    qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    message VARCHAR(256) NOT NULL,
    owner_id BIGINT NULL COMMENT '负责人：采购员 / 计划员',
    handled TINYINT NOT NULL DEFAULT 0,
    pushed_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'MRP 例外信息';
CREATE INDEX idx_pmc_mrp_exception_run ON pmc_mrp_exception (run_id);

CREATE TABLE pmc_mrp_balance (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    seq INT NOT NULL,
    bal_date DATE NOT NULL,
    entry_type VARCHAR(16) NOT NULL COMMENT 'OPENING/SALES_ORDER/FORECAST/MANUAL/MPS/SAFETY_STOCK/PARENT/ALLOCATION/PURCHASE/WIP/PLANNED',
    doc_no VARCHAR(64) NULL,
    parent_material_id BIGINT NULL,
    demand_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    supply_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    projected_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    safety_stock DECIMAL(18,4) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'MRP 供需平衡明细（按运算）';
CREATE INDEX idx_pmc_mrp_balance ON pmc_mrp_balance (run_id, material_id);

CREATE TABLE pmc_capacity_calendar (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    work_center_id BIGINT NOT NULL COMMENT '0 表示全厂（节假日）',
    cal_date DATE NOT NULL,
    available_hours DECIMAL(9,2) NOT NULL,
    reason VARCHAR(64) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmc_capacity_calendar UNIQUE (work_center_id, cal_date)
) COMMENT '产能日历（例外日）';

CREATE TABLE pmc_schedule (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    prod_order_id BIGINT NOT NULL,
    prod_order_no VARCHAR(64) NOT NULL,
    material_id BIGINT NOT NULL,
    operation_seq INT NOT NULL,
    operation VARCHAR(64) NULL,
    work_center_id BIGINT NULL,
    dept_id BIGINT NULL,
    sched_start DATETIME NOT NULL,
    sched_end DATETIME NOT NULL,
    load_hours DECIMAL(12,2) NOT NULL DEFAULT 0,
    day_loads VARCHAR(2000) NULL COMMENT '按日占用：yyyy-MM-dd=小时;…',
    due_date DATE NULL COMMENT '需求日期',
    is_late TINYINT NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 5,
    locked TINYINT NOT NULL DEFAULT 0,
    manual TINYINT NOT NULL DEFAULT 0,
    applied TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '排产结果';
CREATE INDEX idx_pmc_schedule_order ON pmc_schedule (prod_order_id);
CREATE INDEX idx_pmc_schedule_wc ON pmc_schedule (work_center_id, sched_start);

CREATE TABLE pmc_shortage_order (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    snapshot_no VARCHAR(32) NOT NULL,
    prod_order_id BIGINT NOT NULL,
    prod_order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    plan_start DATE NULL,
    priority INT NOT NULL DEFAULT 5,
    prod_status VARCHAR(16) NULL,
    dept_id BIGINT NULL,
    sales_order_no VARCHAR(64) NULL,
    customer_date DATE NULL,
    line_count INT NOT NULL DEFAULT 0,
    short_line_count INT NOT NULL DEFAULT 0,
    line_kit_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    qty_kit_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    kitable_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    eta_date DATE NULL,
    has_no_supply TINYINT NOT NULL DEFAULT 0,
    sort_no INT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '缺料分析快照（订单）';
CREATE INDEX idx_pmc_shortage_order ON pmc_shortage_order (snapshot_no);

CREATE TABLE pmc_shortage_snapshot (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    snapshot_no VARCHAR(32) NOT NULL,
    prod_order_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    need_date DATE NULL COMMENT '计划开工',
    unissued_qty DECIMAL(18,4) NOT NULL,
    allocated_qty DECIMAL(18,4) NOT NULL,
    shortage_qty DECIMAL(18,4) NOT NULL,
    supply_detail TEXT NULL COMMENT '覆盖缺料的在途 JSON',
    eta_date DATE NULL,
    no_supply_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    buyer_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '缺料分析快照（用料行）';
CREATE INDEX idx_pmc_shortage_snapshot ON pmc_shortage_snapshot (snapshot_no, prod_order_id);

CREATE TABLE pmc_push_log (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    push_type VARCHAR(16) NOT NULL,
    ref_key VARCHAR(64) NOT NULL,
    push_date DATE NOT NULL,
    user_ids VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmc_push_log UNIQUE (push_type, ref_key, push_date)
) COMMENT '推送记录（催料、例外，同一对象一天只推一次）';

CREATE TABLE pmc_delivery_alert (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    order_line_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    order_line_no INT NOT NULL,
    customer_id BIGINT NULL,
    sales_owner_id BIGINT NULL,
    sales_dept_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    open_qty DECIMAL(18,4) NOT NULL,
    promised_date DATE NOT NULL,
    estimated_date DATE NOT NULL,
    delay_days INT NOT NULL,
    alert_level VARCHAR(16) NOT NULL COMMENT 'INFO/WARNING/CRITICAL',
    cause VARCHAR(32) NOT NULL COMMENT 'NO_STOCK_NO_WO/MATERIAL_SHORTAGE/CAPACITY/WO_DELAY',
    cause_detail VARCHAR(512) NULL,
    handle_status VARCHAR(16) NOT NULL COMMENT 'OPEN/HANDLED/IGNORED/CLOSED',
    handle_remark VARCHAR(512) NULL,
    handled_delay_days INT NULL COMMENT '处理时的延期天数',
    handled_by BIGINT NULL,
    handled_at DATETIME NULL,
    calculated_at DATETIME NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmc_delivery_alert UNIQUE (order_line_id)
) COMMENT '交期预警';

CREATE TABLE pmc_shipping_plan (
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
    plan_week VARCHAR(8) NOT NULL,
    plan_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PUBLISHED/CLOSED',
    published_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmc_shipping_plan_no UNIQUE (doc_no)
) COMMENT '出货计划';

CREATE TABLE pmc_shipping_plan_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_line_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    order_line_no INT NOT NULL,
    customer_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    due_date DATE NULL COMMENT '承诺交期，无则要求交期',
    open_qty DECIMAL(18,4) NOT NULL,
    available_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    plan_qty DECIMAL(18,4) NOT NULL,
    plan_ship_date DATE NOT NULL,
    transport_mode VARCHAR(16) NULL COMMENT 'SEA/AIR/EXPRESS/LAND',
    noticed_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    line_status VARCHAR(16) NOT NULL COMMENT 'PLANNED/NOTICED/CANCELED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '出货计划明细';
CREATE INDEX idx_pmc_ship_plan_line ON pmc_shipping_plan_line (plan_id);
CREATE INDEX idx_pmc_ship_plan_order_line ON pmc_shipping_plan_line (order_line_id);

