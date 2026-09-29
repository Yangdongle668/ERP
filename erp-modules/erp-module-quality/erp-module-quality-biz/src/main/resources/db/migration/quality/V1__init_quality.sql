-- 品质模块（需求 10-品质）：检验基础数据、检验单、NCR/MRB、CAPA/8D、客诉、SCAR。GB/T 2828.1 字码表与主表以代码常量内置（AqlTable）

CREATE TABLE qc_inspection_item_lib (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    item_type VARCHAR(16) NOT NULL COMMENT 'QUALITATIVE 定性 / QUANTITATIVE 定量',
    method VARCHAR(32) NOT NULL COMMENT '字典 qc_inspection_method',
    unit VARCHAR(16) NULL,
    defect_level VARCHAR(4) NOT NULL COMMENT 'CR/MA/MI',
    tool VARCHAR(64) NULL,
    description VARCHAR(512) NULL,
    item_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_inspection_item_lib_code UNIQUE (code)
) COMMENT '检验项目库';

CREATE TABLE qc_sampling_plan (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    plan_type VARCHAR(16) NOT NULL COMMENT 'GB2828/FULL/FIXED/EXEMPT',
    inspection_level VARCHAR(4) NULL COMMENT 'S1/S2/S3/S4/I/II/III',
    aql_cr VARCHAR(8) NULL,
    aql_ma VARCHAR(8) NULL,
    aql_mi VARCHAR(8) NULL,
    fixed_qty INT NULL,
    plan_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_sampling_plan_code UNIQUE (code)
) COMMENT '抽样方案';

CREATE TABLE qc_defect_code (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL COMMENT '字典 qc_defect_category',
    default_level VARCHAR(4) NOT NULL COMMENT 'CR/MA/MI',
    code_status VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_defect_code_code UNIQUE (code)
) COMMENT '缺陷代码';

CREATE TABLE qc_standard (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL COMMENT '编码规则 QC_STANDARD，各版本相同',
    name VARCHAR(128) NOT NULL,
    inspect_type VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/FQC/OQC/RETURN',
    scope_type VARCHAR(16) NOT NULL COMMENT 'MATERIAL/CATEGORY',
    material_id BIGINT NULL,
    category_id BIGINT NULL COMMENT 'CATEGORY 且为空 = 通用标准',
    operation VARCHAR(32) NULL COMMENT 'IPQC 适用工序（字典 eng_operation）',
    sampling_plan_id BIGINT NOT NULL,
    std_version INT NOT NULL,
    std_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/EFFECTIVE/OBSOLETE',
    file_id BIGINT NULL COMMENT '检验规范文件（SIP）',
    effective_at DATETIME NULL,
    remark VARCHAR(512) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_standard_ver UNIQUE (code, std_version)
) COMMENT '检验标准（同一编号多版本）';

CREATE INDEX idx_qc_standard_match ON qc_standard (inspect_type, std_status, material_id);

CREATE TABLE qc_standard_item (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    standard_id BIGINT NOT NULL,
    seq INT NOT NULL,
    lib_item_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    method VARCHAR(32) NULL,
    unit VARCHAR(16) NULL,
    spec VARCHAR(256) NULL,
    target DECIMAL(18,6) NULL,
    upper_limit DECIMAL(18,6) NULL,
    lower_limit DECIMAL(18,6) NULL,
    defect_level VARCHAR(4) NOT NULL,
    sampling_plan_id BIGINT NULL COMMENT '覆盖标准的抽样方案',
    is_key TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '检验标准项目';

CREATE INDEX idx_qc_standard_item_std ON qc_standard_item (standard_id);

CREATE TABLE qc_inspection (
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
    inspect_type VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/FQC/OQC/RETURN/RECHECK',
    ipqc_kind VARCHAR(16) NULL COMMENT 'FIRST/PATROL/LAST/REPORT',
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NULL,
    lot_qty DECIMAL(18,4) NOT NULL COMMENT '送检批量（基本单位）',
    supplier_id BIGINT NULL,
    customer_id BIGINT NULL,
    source_line_id BIGINT NULL COMMENT '来源单据行（入库单来源行、出货通知行）',
    upstream_type VARCHAR(32) NULL COMMENT '上游业务单据类型：PUR_RECEIPT / MFG_FINISH / SAL_RETURN / SHP_NOTICE / MFG_REPORT / INV_TRANSFER',
    upstream_id BIGINT NULL,
    upstream_line_id BIGINT NULL,
    upstream_no VARCHAR(64) NULL,
    prod_order_id BIGINT NULL COMMENT 'IPQC',
    operation_seq INT NULL COMMENT 'IPQC 工序号',
    warehouse_id BIGINT NULL COMMENT '被检物所在仓库',
    standard_id BIGINT NULL,
    standard_code VARCHAR(32) NULL,
    standard_version INT NULL,
    sampling_snapshot TEXT NULL COMMENT '抽样方案与计算结果 JSON',
    sample_qty INT NOT NULL DEFAULT 0,
    inspector_id BIGINT NULL,
    started_at DATETIME NULL,
    inspected_at DATETIME NULL,
    suggested_result VARCHAR(8) NULL COMMENT 'PASS/FAIL',
    result VARCHAR(16) NULL COMMENT 'QUALIFIED/REJECTED/CONCESSION/SORTED',
    qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    concession_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    rejected_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    cr_count INT NOT NULL DEFAULT 0,
    ma_count INT NOT NULL DEFAULT 0,
    mi_count INT NOT NULL DEFAULT 0,
    judge_by BIGINT NULL,
    judge_at DATETIME NULL,
    judge_reason VARCHAR(512) NULL COMMENT '让步判定合格理由等',
    ncr_id BIGINT NULL,
    insp_status VARCHAR(16) NOT NULL COMMENT 'PENDING/INSPECTING/WAIT_MRB/JUDGED/HANDLED/CANCELED',
    mrb_sort TINYINT NOT NULL DEFAULT 0 COMMENT 'MRB 处置含挑选：只能按挑选判定',
    preset_concession_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT 'MRB 已定特采数量',
    preset_rejected_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT 'MRB 已定不合格数量',
    transfer_ids VARCHAR(256) NULL COMMENT '检验调拨单 ID',
    confirmed_transfer_ids VARCHAR(256) NULL,
    rejudge_count INT NOT NULL DEFAULT 0,
    rejudge_reason VARCHAR(512) NULL,
    overdue_notified TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_inspection_no UNIQUE (doc_no)
) COMMENT '检验单';

CREATE INDEX idx_qc_inspection_type ON qc_inspection (inspect_type, insp_status);

CREATE INDEX idx_qc_inspection_source ON qc_inspection (source_type, source_id);

CREATE INDEX idx_qc_inspection_upstream ON qc_inspection (upstream_type, upstream_id);

CREATE INDEX idx_qc_inspection_material ON qc_inspection (material_id, batch_no);

CREATE TABLE qc_inspection_item (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    seq INT NOT NULL,
    item_name VARCHAR(64) NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    method VARCHAR(32) NULL,
    unit VARCHAR(16) NULL,
    spec VARCHAR(256) NULL,
    target DECIMAL(18,6) NULL,
    upper_limit DECIMAL(18,6) NULL,
    lower_limit DECIMAL(18,6) NULL,
    defect_level VARCHAR(4) NOT NULL,
    is_key TINYINT NOT NULL DEFAULT 0,
    sample_qty INT NOT NULL DEFAULT 0,
    measured_values TEXT NULL COMMENT '定量：测量值 JSON 数组',
    ng_count INT NOT NULL DEFAULT 0,
    item_result VARCHAR(4) NULL COMMENT 'OK/NG',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '检验项目结果';

CREATE INDEX idx_qc_inspection_item_ins ON qc_inspection_item (inspection_id);

CREATE TABLE qc_inspection_defect (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    defect_code VARCHAR(32) NOT NULL,
    defect_name VARCHAR(64) NULL,
    defect_level VARCHAR(4) NOT NULL,
    qty INT NOT NULL,
    description VARCHAR(512) NULL,
    image_file_ids VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '检验缺陷明细';

CREATE INDEX idx_qc_inspection_defect_ins ON qc_inspection_defect (inspection_id);

CREATE TABLE qc_ncr (
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
    ncr_source VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/FQC/OQC/RETURN/RECHECK/PRODUCTION/INVENTORY/COMPLAINT',
    inspection_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NULL,
    supplier_id BIGINT NULL,
    customer_id BIGINT NULL,
    ncr_qty DECIMAL(18,4) NOT NULL,
    defect_description VARCHAR(2000) NOT NULL,
    defect_codes VARCHAR(256) NULL,
    severity VARCHAR(16) NOT NULL COMMENT 'CRITICAL/MAJOR/MINOR',
    responsibility VARCHAR(16) NOT NULL COMMENT '字典 qc_ncr_responsibility',
    containment VARCHAR(1000) NULL,
    capa_required TINYINT NOT NULL DEFAULT 0,
    scar_required TINYINT NOT NULL DEFAULT 0,
    amount_base DECIMAL(18,2) NULL COMMENT '涉及金额（本位币）',
    capa_id BIGINT NULL,
    scar_id BIGINT NULL,
    complaint_id BIGINT NULL,
    batch_frozen TINYINT NOT NULL DEFAULT 0 COMMENT '致命缺陷已冻结批次',
    approved_at DATETIME NULL,
    closed_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_ncr_no UNIQUE (doc_no)
) COMMENT 'NCR 不合格品报告';

CREATE INDEX idx_qc_ncr_material ON qc_ncr (material_id, created_at);

CREATE INDEX idx_qc_ncr_inspection ON qc_ncr (inspection_id);

CREATE TABLE qc_ncr_disposition (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    ncr_id BIGINT NOT NULL,
    seq INT NOT NULL,
    disposition VARCHAR(16) NOT NULL COMMENT 'RETURN/CONCESSION/SORT/REWORK/SCRAP',
    qty DECIMAL(18,4) NOT NULL,
    remark VARCHAR(512) NULL,
    follow_doc_no VARCHAR(64) NULL,
    done TINYINT NOT NULL DEFAULT 0,
    done_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'NCR 处置明细';

CREATE INDEX idx_qc_ncr_disposition_ncr ON qc_ncr_disposition (ncr_id);

CREATE TABLE qc_capa (
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
    capa_source VARCHAR(16) NOT NULL COMMENT 'NCR/COMPLAINT/AUDIT/OTHER',
    material_id BIGINT NULL,
    customer_id BIGINT NULL,
    supplier_id BIGINT NULL,
    leader_id BIGINT NOT NULL,
    team_members VARCHAR(512) NULL COMMENT '小组成员用户 ID，逗号分隔',
    d1_team VARCHAR(1000) NULL COMMENT 'D1 小组说明',
    d2_problem TEXT NULL,
    d3_containment TEXT NULL,
    d3_due DATE NULL,
    d3_done_at DATETIME NULL,
    d4_root_cause TEXT NULL,
    d4_method VARCHAR(64) NULL,
    d5_actions TEXT NULL,
    d6_implementation TEXT NULL,
    d7_prevention TEXT NULL,
    d8_summary TEXT NULL,
    current_step INT NOT NULL DEFAULT 1 COMMENT '当前步骤 1～8（之前的步骤已完成）；9 = 全部完成',
    due_date DATE NOT NULL,
    verify_result VARCHAR(16) NULL COMMENT 'EFFECTIVE/INEFFECTIVE',
    verify_by BIGINT NULL,
    verify_at DATETIME NULL,
    invalid_count INT NOT NULL DEFAULT 0 COMMENT '验证无效次数',
    verify_history VARCHAR(2000) NULL COMMENT '历次验证记录',
    capa_status VARCHAR(16) NOT NULL COMMENT 'OPEN/VERIFYING/CLOSED/CANCELED',
    closed_at DATETIME NULL,
    cancel_reason VARCHAR(256) NULL,
    last_remind_date DATE NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_capa_no UNIQUE (doc_no)
) COMMENT 'CAPA / 8D';

CREATE INDEX idx_qc_capa_status ON qc_capa (capa_status, due_date);

CREATE TABLE qc_complaint (
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
    contact_id BIGINT NULL,
    complaint_type VARCHAR(16) NOT NULL COMMENT '字典 qc_complaint_type',
    severity VARCHAR(16) NOT NULL COMMENT 'CRITICAL/MAJOR/MINOR',
    material_id BIGINT NULL,
    customer_part_no VARCHAR(64) NULL,
    order_no VARCHAR(64) NULL,
    shipment_no VARCHAR(64) NULL,
    batch_no VARCHAR(64) NULL,
    serial_nos TEXT NULL,
    complaint_qty DECIMAL(18,4) NULL,
    description TEXT NOT NULL,
    received_at DATETIME NOT NULL,
    reply_due_date DATE NOT NULL,
    qe_id BIGINT NOT NULL,
    sales_owner_id BIGINT NULL COMMENT 'CRM 客户负责人（数据权限）',
    sales_dept_id BIGINT NULL,
    root_cause TEXT NULL,
    reply_content TEXT NULL,
    replied_at DATETIME NULL,
    handling VARCHAR(16) NULL COMMENT 'NONE/RETURN/REPLACE/CREDIT/REWORK_ONSITE',
    handling_remark VARCHAR(512) NULL,
    claim_amount DECIMAL(18,2) NULL,
    agreed_amount DECIMAL(18,2) NULL,
    currency VARCHAR(3) NULL,
    complaint_status VARCHAR(16) NOT NULL COMMENT 'OPEN/ANALYZING/REPLIED/CLOSED/CANCELED',
    capa_id BIGINT NULL,
    ncr_id BIGINT NULL,
    return_id BIGINT NULL,
    cancel_reason VARCHAR(256) NULL,
    closed_at DATETIME NULL,
    last_remind_date DATE NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_complaint_no UNIQUE (doc_no)
) COMMENT '客诉';

CREATE INDEX idx_qc_complaint_status ON qc_complaint (complaint_status, reply_due_date);

CREATE INDEX idx_qc_complaint_customer ON qc_complaint (customer_id);

CREATE TABLE qc_scar (
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
    ncr_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NULL,
    problem_description TEXT NOT NULL,
    requirement TEXT NOT NULL,
    reply_due_date DATE NULL,
    sent_at DATETIME NULL,
    reply_content TEXT NULL,
    replied_at DATETIME NULL,
    verify_plan VARCHAR(512) NULL,
    verify_result VARCHAR(16) NULL,
    invalid_count INT NOT NULL DEFAULT 0,
    scar_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/SENT/REPLIED/VERIFYING/CLOSED/CANCELED',
    cancel_reason VARCHAR(256) NULL,
    closed_at DATETIME NULL,
    last_remind_date DATE NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_scar_no UNIQUE (doc_no)
) COMMENT 'SCAR 供应商纠正措施要求';

CREATE INDEX idx_qc_scar_supplier ON qc_scar (supplier_id, scar_status);

-- 初始数据：常用抽样方案、外观检验项目、各检验类型的通用外观检验标准（01 文档第 4 节）
INSERT INTO qc_sampling_plan (id, code, name, plan_type, inspection_level, aql_cr, aql_ma, aql_mi, fixed_qty, plan_status, created_at, updated_at) VALUES
    (1001, 'GB-II-065-15', '一般 II 级 CR0 MA0.65 MI1.5', 'GB2828', 'II', '0', '0.65', '1.5', NULL, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1002, 'GB-II-04-10', '一般 II 级 CR0 MA0.4 MI1.0', 'GB2828', 'II', '0', '0.4', '1.0', NULL, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1003, 'FIXED-5', '固定抽 5 个', 'FIXED', NULL, NULL, NULL, NULL, 5, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1004, 'FULL', '全检', 'FULL', NULL, NULL, NULL, NULL, NULL, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1005, 'EXEMPT', '免检', 'EXEMPT', NULL, NULL, NULL, NULL, NULL, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qc_inspection_item_lib (id, code, name, item_type, method, unit, defect_level, tool, description, item_status, created_at, updated_at) VALUES
    (1101, 'APPEARANCE', '外观', 'QUALITATIVE', 'VISUAL', NULL, 'MI', NULL, '目视检查有无破损、脏污、划伤、变形', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1102, 'LABEL', '标识', 'QUALITATIVE', 'VISUAL', NULL, 'MA', NULL, '标签内容（料号、数量、批次）与实物一致', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1103, 'PACKAGING', '包装', 'QUALITATIVE', 'VISUAL', NULL, 'MI', NULL, '包装完好、防护到位', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1104, 'DIMENSION', '尺寸', 'QUANTITATIVE', 'MEASURE', 'mm', 'MA', '卡尺', '按图纸关键尺寸量测', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1105, 'FUNCTION', '功能测试', 'QUALITATIVE', 'TEST', NULL, 'MA', NULL, '按测试规范进行功能测试', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qc_defect_code (id, code, name, category, default_level, code_status, created_at, updated_at) VALUES
    (1201, 'D-SCRATCH', '划伤', 'APPEARANCE', 'MI', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1202, 'D-DIRTY', '脏污', 'APPEARANCE', 'MI', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1203, 'D-BROKEN', '破损', 'APPEARANCE', 'MA', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1204, 'D-DIM', '尺寸超差', 'DIMENSION', 'MA', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1205, 'D-FUNC', '功能不良', 'FUNCTION', 'MA', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1206, 'D-SAFETY', '安全隐患', 'PERFORMANCE', 'CR', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1207, 'D-LABEL', '标识错误', 'LABEL', 'MA', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1208, 'D-PACK', '包装不良', 'PACKAGING', 'MI', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qc_standard (id, code, name, inspect_type, scope_type, material_id, category_id, operation, sampling_plan_id, std_version, std_status, effective_at, created_at, updated_at) VALUES
    (1301, 'QS-GEN-IQC', '通用外观检验（IQC）', 'IQC', 'CATEGORY', NULL, NULL, NULL, 1001, 1, 'EFFECTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1302, 'QS-GEN-IPQC', '通用外观检验（IPQC）', 'IPQC', 'CATEGORY', NULL, NULL, NULL, 1001, 1, 'EFFECTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1303, 'QS-GEN-FQC', '通用外观检验（FQC）', 'FQC', 'CATEGORY', NULL, NULL, NULL, 1001, 1, 'EFFECTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1304, 'QS-GEN-OQC', '通用外观检验（OQC）', 'OQC', 'CATEGORY', NULL, NULL, NULL, 1001, 1, 'EFFECTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1305, 'QS-GEN-RETURN', '通用外观检验（退货）', 'RETURN', 'CATEGORY', NULL, NULL, NULL, 1001, 1, 'EFFECTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qc_standard_item (id, standard_id, seq, lib_item_id, name, item_type, method, unit, spec, defect_level, is_key, created_at, updated_at) VALUES
    (1311, 1301, 1, 1101, '外观', 'QUALITATIVE', 'VISUAL', NULL, '无破损、脏污、划伤', 'MI', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1312, 1301, 2, 1102, '标识', 'QUALITATIVE', 'VISUAL', NULL, '标签与实物一致', 'MA', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1321, 1302, 1, 1101, '外观', 'QUALITATIVE', 'VISUAL', NULL, '无破损、脏污、划伤', 'MI', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1331, 1303, 1, 1101, '外观', 'QUALITATIVE', 'VISUAL', NULL, '无破损、脏污、划伤', 'MI', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1332, 1303, 2, 1105, '功能测试', 'QUALITATIVE', 'TEST', NULL, '功能正常', 'MA', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1341, 1304, 1, 1101, '外观', 'QUALITATIVE', 'VISUAL', NULL, '无破损、脏污、划伤', 'MI', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1342, 1304, 2, 1103, '包装', 'QUALITATIVE', 'VISUAL', NULL, '包装完好、标签正确', 'MA', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1351, 1305, 1, 1101, '外观', 'QUALITATIVE', 'VISUAL', NULL, '无破损、脏污、划伤', 'MI', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
