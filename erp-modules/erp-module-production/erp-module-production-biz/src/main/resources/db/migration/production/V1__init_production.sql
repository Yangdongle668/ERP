-- 生产模块（需求 09-生产）：生产订单（用料、工序）、工单、领料 / 退料、报工（人员）、不良、完工入库、追溯

-- ==================== 生产订单（09-01） ====================

CREATE TABLE mfg_prod_order (
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
    order_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/SAMPLE/REWORK/DISASSEMBLY',
    prod_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/PENDING/PLANNED/RELEASED/IN_PROGRESS/SUSPENDED/COMPLETED/CLOSED/VOIDED',
    status_before_suspend VARCHAR(16) NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '计划数量（基本单位）',
    bom_id BIGINT NULL,
    routing_id BIGINT NULL,
    plan_start DATE NOT NULL,
    plan_end DATE NOT NULL,
    actual_start DATETIME NULL,
    actual_end DATETIME NULL,
    released_at DATETIME NULL,
    priority INT NOT NULL DEFAULT 5,
    batch_no VARCHAR(64) NOT NULL,
    sales_order_line_id BIGINT NULL,
    sales_order_id BIGINT NULL,
    sales_order_no VARCHAR(64) NULL,
    completed_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '末道报工点合格数',
    scrapped_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    finished_request_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    stocked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    qualified_stocked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    fqc_rejected_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    close_reason VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfg_prod_order_no UNIQUE (doc_no)
) COMMENT '生产订单';
CREATE INDEX idx_mfg_po_material ON mfg_prod_order (material_id);
CREATE INDEX idx_mfg_po_status ON mfg_prod_order (prod_status);
CREATE INDEX idx_mfg_po_sales_line ON mfg_prod_order (sales_order_line_id);
CREATE INDEX idx_mfg_po_batch ON mfg_prod_order (batch_no);

CREATE TABLE mfg_prod_order_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    prod_order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    component_id BIGINT NOT NULL,
    qty_per DECIMAL(18,6) NOT NULL COMMENT '单位用量（已除以 BOM 基数，不含损耗）',
    scrap_rate DECIMAL(9,6) NOT NULL DEFAULT 0,
    required_qty DECIMAL(18,4) NOT NULL,
    issue_method VARCHAR(16) NOT NULL DEFAULT 'PICK' COMMENT 'PICK/BACKFLUSH',
    operation_seq INT NULL,
    issued_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已领（含超领、倒冲）',
    over_issued_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已退（良品 + 不良）',
    returned_good_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    substitute_of_id BIGINT NULL,
    is_added TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '生产订单用料';
CREATE INDEX idx_mfg_pom_order ON mfg_prod_order_material (prod_order_id);
CREATE INDEX idx_mfg_pom_component ON mfg_prod_order_material (component_id);

CREATE TABLE mfg_prod_order_operation (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    prod_order_id BIGINT NOT NULL,
    seq INT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    work_center_id BIGINT NULL,
    is_report_point TINYINT NOT NULL DEFAULT 1,
    is_inspection_point TINYINT NOT NULL DEFAULT 0,
    is_outsourced TINYINT NOT NULL DEFAULT 0,
    std_run_seconds DECIMAL(18,4) NOT NULL DEFAULT 0,
    std_setup_minutes DECIMAL(18,4) NOT NULL DEFAULT 0,
    good_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计合格（含返修合格）',
    first_good_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '一次合格（不含返修）',
    defect_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    repaired_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    dispatched_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    actual_hours DECIMAL(18,4) NOT NULL DEFAULT 0,
    op_status VARCHAR(16) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/RUNNING/DONE',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '生产订单工序';
CREATE INDEX idx_mfg_poo_order ON mfg_prod_order_operation (prod_order_id);

-- ==================== 工单（09-02） ====================

CREATE TABLE mfg_work_order (
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
    prod_order_id BIGINT NOT NULL,
    operation_seq INT NOT NULL,
    work_center_id BIGINT NOT NULL,
    plan_date DATE NOT NULL,
    shift VARCHAR(16) NOT NULL,
    team_leader_id BIGINT NULL,
    plan_qty DECIMAL(18,4) NOT NULL,
    good_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    defect_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    wo_status VARCHAR(16) NOT NULL COMMENT 'DISPATCHED/RUNNING/DONE/CANCELED',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfg_work_order_no UNIQUE (doc_no)
) COMMENT '工单（派工）';
CREATE INDEX idx_mfg_wo_order ON mfg_work_order (prod_order_id, operation_seq);
CREATE INDEX idx_mfg_wo_date ON mfg_work_order (plan_date);

-- ==================== 领料 / 退料（09-03） ====================

CREATE TABLE mfg_issue (
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
    issue_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/OVER/BACKFLUSH',
    prod_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NULL,
    kit_qty DECIMAL(18,4) NULL,
    over_reason VARCHAR(32) NULL,
    over_remark VARCHAR(256) NULL,
    report_id BIGINT NULL COMMENT '倒冲来源报工单',
    stock_out_ids VARCHAR(256) NULL,
    stock_out_nos VARCHAR(512) NULL,
    submitted_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfg_issue_no UNIQUE (doc_no)
) COMMENT '领料单';
CREATE INDEX idx_mfg_issue_order ON mfg_issue (prod_order_id);
CREATE INDEX idx_mfg_issue_report ON mfg_issue (report_id);

CREATE TABLE mfg_issue_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    warehouse_id BIGINT NULL,
    request_qty DECIMAL(18,4) NOT NULL,
    issued_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '领料单明细';
CREATE INDEX idx_mfg_issue_line_issue ON mfg_issue_line (issue_id);

CREATE TABLE mfg_return (
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
    prod_order_id BIGINT NOT NULL,
    return_type VARCHAR(16) NOT NULL COMMENT 'GOOD/DEFECT',
    warehouse_id BIGINT NOT NULL,
    stock_in_ids VARCHAR(256) NULL,
    stock_in_nos VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfg_return_no UNIQUE (doc_no)
) COMMENT '退料单';
CREATE INDEX idx_mfg_return_order ON mfg_return (prod_order_id);

CREATE TABLE mfg_return_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    batch_no VARCHAR(64) NULL,
    defect_desc VARCHAR(256) NULL,
    received_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '退料单明细';
CREATE INDEX idx_mfg_return_line_return ON mfg_return_line (return_id);

-- ==================== 报工（09-04） ====================

CREATE TABLE mfg_report (
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
    prod_order_id BIGINT NOT NULL,
    operation_seq INT NOT NULL,
    work_order_id BIGINT NULL,
    work_center_id BIGINT NULL,
    report_date DATE NOT NULL,
    shift VARCHAR(16) NOT NULL,
    report_kind VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/REPAIR 返修补报/SCRAP 报废补报',
    defect_id BIGINT NULL COMMENT '补报来源不良记录',
    good_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    defect_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_reason VARCHAR(32) NULL,
    work_hours DECIMAL(18,4) NOT NULL DEFAULT 0,
    machine_hours DECIMAL(18,4) NULL,
    tooling_id BIGINT NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    approved_at DATETIME NULL,
    approved_by BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfg_report_no UNIQUE (doc_no)
) COMMENT '报工单';
CREATE INDEX idx_mfg_report_order ON mfg_report (prod_order_id, operation_seq);
CREATE INDEX idx_mfg_report_date ON mfg_report (report_date);
CREATE INDEX idx_mfg_report_wo ON mfg_report (work_order_id);

CREATE TABLE mfg_report_operator (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    operator_name VARCHAR(64) NULL,
    hours DECIMAL(18,4) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '报工人员';
CREATE INDEX idx_mfg_rpt_op_report ON mfg_report_operator (report_id);
CREATE INDEX idx_mfg_rpt_op_user ON mfg_report_operator (user_id);

-- ==================== 不良（09-06） ====================

CREATE TABLE mfg_defect (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    prod_order_id BIGINT NOT NULL,
    operation_seq INT NOT NULL,
    material_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    dept_id BIGINT NULL,
    defect_code VARCHAR(32) NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    position VARCHAR(128) NULL,
    description VARCHAR(512) NULL,
    image_file_ids VARCHAR(256) NULL,
    disposition VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REPAIRED/SCRAPPED',
    repaired_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrapped_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    ncr_no VARCHAR(64) NULL,
    handled_by BIGINT NULL,
    handled_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '不良记录';
CREATE INDEX idx_mfg_defect_report ON mfg_defect (report_id);
CREATE INDEX idx_mfg_defect_order ON mfg_defect (prod_order_id);
CREATE INDEX idx_mfg_defect_date ON mfg_defect (report_date);

-- ==================== 完工入库（09-05） ====================

CREATE TABLE mfg_finish (
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
    prod_order_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    fqc_required TINYINT NOT NULL DEFAULT 0,
    warehouse_id BIGINT NULL,
    serial_nos TEXT NULL,
    stock_in_ids VARCHAR(256) NULL,
    stock_in_nos VARCHAR(512) NULL,
    stocked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    rejected_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    finish_status VARCHAR(16) NOT NULL COMMENT 'SUBMITTED/STOCKED/JUDGED/CANCELED',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfg_finish_no UNIQUE (doc_no)
) COMMENT '完工入库申请';
CREATE INDEX idx_mfg_finish_order ON mfg_finish (prod_order_id);

-- ==================== 追溯（09-07，只增不改） ====================

CREATE TABLE mfg_trace (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    prod_order_id BIGINT NOT NULL,
    product_material_id BIGINT NOT NULL,
    product_batch_no VARCHAR(64) NOT NULL,
    component_material_id BIGINT NOT NULL,
    component_batch_no VARCHAR(64) NULL,
    qty DECIMAL(18,4) NOT NULL COMMENT '领料为正，退料为负',
    source_doc_type VARCHAR(32) NOT NULL,
    source_doc_id BIGINT NULL,
    source_doc_no VARCHAR(64) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '生产追溯关系';
CREATE INDEX idx_mfg_trace_product ON mfg_trace (product_material_id, product_batch_no);
CREATE INDEX idx_mfg_trace_component ON mfg_trace (component_material_id, component_batch_no);
CREATE INDEX idx_mfg_trace_order ON mfg_trace (prod_order_id);

