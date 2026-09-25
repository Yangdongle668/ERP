-- 研发工程第 2 批：工作中心、工艺路线、ECN、研发项目、样品、工装、认证（需求 05-研发工程/04～09）

CREATE TABLE eng_work_center (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code               VARCHAR(32)   NOT NULL,
    name               VARCHAR(64)   NOT NULL,
    dept_id            BIGINT        NOT NULL COMMENT '所属车间',
    wc_type            VARCHAR(16)   NOT NULL DEFAULT 'LINE' COMMENT 'LINE/MACHINE/MANUAL/OUTSOURCE',
    hours_per_shift    DECIMAL(18,4) NOT NULL DEFAULT 8 COMMENT '每班小时数',
    shift_count        INT           NOT NULL DEFAULT 1 COMMENT '班次数',
    efficiency_pct     DECIMAL(9,4)  NOT NULL DEFAULT 1 COMMENT '效率，1 = 100%',
    labor_rate         DECIMAL(24,6) NULL COMMENT '人工费率（本位币/小时）',
    overhead_rate      DECIMAL(24,6) NULL COMMENT '制费费率（本位币/小时）',
    status             VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    remark             VARCHAR(256)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '工作中心';
CREATE INDEX idx_eng_work_center_code ON eng_work_center (code);

CREATE TABLE eng_routing (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(80)  NOT NULL,
    doc_date           DATE         NULL,
    status             VARCHAR(20)  NOT NULL,
    org_id             BIGINT       NULL,
    dept_id            BIGINT       NULL,
    owner_id           BIGINT       NULL,
    source_type        VARCHAR(32)  NULL,
    source_id          BIGINT       NULL,
    source_no          VARCHAR(64)  NULL,
    remark             VARCHAR(512) NULL,
    material_id        BIGINT        NOT NULL COMMENT '自制件',
    routing_version    INT           NOT NULL COMMENT '版本号，同一物料从 1 递增',
    is_default         TINYINT       NOT NULL DEFAULT 0,
    effective_date     DATE          NULL COMMENT '成为默认版本的日期',
    description        VARCHAR(256)  NULL,
    copied_from_id     BIGINT        NULL,
    step_count         INT           NOT NULL DEFAULT 0,
    total_setup_minutes DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_run_seconds  DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '总标准工时（秒/件）',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '工艺路线头';
CREATE INDEX idx_eng_routing_material_id ON eng_routing (material_id);

CREATE TABLE eng_routing_step (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    routing_id         BIGINT        NOT NULL,
    seq                INT           NOT NULL COMMENT '工序号 10、20…',
    operation          VARCHAR(32)   NOT NULL COMMENT '字典 eng_operation',
    work_center_id     BIGINT        NOT NULL,
    setup_minutes      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '准备时间（分钟/批）',
    run_seconds        DECIMAL(18,4) NOT NULL COMMENT '标准工时（秒/件）',
    is_report_point    TINYINT       NOT NULL DEFAULT 1 COMMENT '报工点',
    is_inspection_point TINYINT       NOT NULL DEFAULT 0 COMMENT '检验点',
    is_outsourced      TINYINT       NOT NULL DEFAULT 0 COMMENT '委外工序',
    remark             VARCHAR(256)  NULL COMMENT '作业要点',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '工序';
CREATE INDEX idx_eng_routing_step_routing_id ON eng_routing_step (routing_id);
CREATE INDEX idx_eng_routing_step_work_center_id ON eng_routing_step (work_center_id);

CREATE TABLE eng_ecn (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(80)  NOT NULL,
    doc_date           DATE         NULL,
    status             VARCHAR(20)  NOT NULL,
    org_id             BIGINT       NULL,
    dept_id            BIGINT       NULL,
    owner_id           BIGINT       NULL,
    source_type        VARCHAR(32)  NULL,
    source_id          BIGINT       NULL,
    source_no          VARCHAR(64)  NULL,
    remark             VARCHAR(512) NULL,
    title              VARCHAR(128)  NOT NULL,
    ecn_type           VARCHAR(32)   NOT NULL COMMENT '字典 eng_ecn_type',
    reason_type        VARCHAR(32)   NOT NULL COMMENT '字典 eng_ecn_reason',
    reason             VARCHAR(1000) NOT NULL,
    urgency            VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT',
    effective_mode     VARCHAR(16)   NOT NULL DEFAULT 'IMMEDIATE' COMMENT 'IMMEDIATE/DATE/USE_UP',
    effective_date     DATE          NULL,
    customer_id        BIGINT        NULL,
    analyzed           TINYINT       NOT NULL DEFAULT 0 COMMENT '已完成影响分析',
    key_part           TINYINT       NOT NULL DEFAULT 0 COMMENT '涉及关键件',
    approved_at        DATETIME      NULL,
    effected_at        DATETIME      NULL COMMENT '实际生效时间',
    closed_at          DATETIME      NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'ECN 工程变更';

CREATE TABLE eng_ecn_line (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    ecn_id             BIGINT        NOT NULL,
    line_no            INT           NOT NULL,
    bom_id             BIGINT        NOT NULL COMMENT '被变更的 BOM 版本',
    action             VARCHAR(16)   NOT NULL COMMENT 'ADD/REMOVE/REPLACE/CHANGE_QTY',
    old_component_id   BIGINT        NULL,
    new_component_id   BIGINT        NULL,
    old_qty_per        DECIMAL(18,4) NULL,
    new_qty_per        DECIMAL(18,4) NULL,
    old_scrap_rate     DECIMAL(9,4)  NULL,
    new_scrap_rate     DECIMAL(9,4)  NULL,
    position_no        VARCHAR(1024) NULL COMMENT '新位号',
    new_bom_id         BIGINT        NULL COMMENT '审批后生成的新 BOM 版本',
    remark             VARCHAR(256)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'ECN 变更明细';
CREATE INDEX idx_eng_ecn_line_ecn_id ON eng_ecn_line (ecn_id);
CREATE INDEX idx_eng_ecn_line_bom_id ON eng_ecn_line (bom_id);

CREATE TABLE eng_ecn_impact (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    ecn_id             BIGINT        NOT NULL,
    material_id        BIGINT        NOT NULL,
    impact_type        VARCHAR(16)   NOT NULL COMMENT 'STOCK/PURCHASE/WIP/SALES',
    doc_no             VARCHAR(64)   NULL,
    qty                DECIMAL(18,4) NULL,
    handling           VARCHAR(32)   NULL COMMENT '处理方式',
    handling_remark    VARCHAR(256)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'ECN 影响分析';
CREATE INDEX idx_eng_ecn_impact_ecn_id ON eng_ecn_impact (ecn_id);

CREATE TABLE eng_ecn_task (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    ecn_id             BIGINT        NOT NULL,
    dept_role          VARCHAR(16)   NOT NULL COMMENT 'PURCHASE/WAREHOUSE/PRODUCTION/QUALITY/PMC/CERT',
    assignee_id        BIGINT        NULL,
    content            VARCHAR(512)  NOT NULL,
    task_status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE',
    done_remark        VARCHAR(512)  NULL,
    done_by            BIGINT        NULL,
    done_at            DATETIME      NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'ECN 执行确认';
CREATE INDEX idx_eng_ecn_task_ecn_id ON eng_ecn_task (ecn_id);

CREATE TABLE eng_project (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(80)  NOT NULL,
    doc_date           DATE         NULL,
    status             VARCHAR(20)  NOT NULL,
    org_id             BIGINT       NULL,
    dept_id            BIGINT       NULL,
    owner_id           BIGINT       NULL,
    source_type        VARCHAR(32)  NULL,
    source_id          BIGINT       NULL,
    source_no          VARCHAR(64)  NULL,
    remark             VARCHAR(512) NULL,
    name               VARCHAR(128)  NOT NULL,
    project_type       VARCHAR(32)   NOT NULL DEFAULT 'NPI' COMMENT 'NPI/IMPROVEMENT/CUSTOMER_CUSTOM',
    customer_id        BIGINT        NULL,
    product_material_id BIGINT        NULL,
    pm_user_id         BIGINT        NOT NULL COMMENT '项目经理',
    stage              VARCHAR(32)   NOT NULL DEFAULT 'CONCEPT' COMMENT '字典 eng_project_stage',
    plan_start         DATE          NOT NULL,
    plan_end           DATE          NOT NULL,
    actual_start       DATE          NULL,
    actual_end         DATE          NULL,
    progress_pct       DECIMAL(9,4)  NOT NULL DEFAULT 0 COMMENT '进度（0～1）',
    priority           VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
    project_status     VARCHAR(16)   NOT NULL DEFAULT 'PLANNING' COMMENT 'PLANNING/IN_PROGRESS/ON_HOLD/COMPLETED/CANCELED',
    description        TEXT          NULL,
    cancel_reason      VARCHAR(256)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '研发项目';
CREATE INDEX idx_eng_project_pm_user_id ON eng_project (pm_user_id);

CREATE TABLE eng_project_member (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    project_id         BIGINT        NOT NULL,
    user_id            BIGINT        NOT NULL,
    member_role        VARCHAR(32)   NULL COMMENT '角色',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '项目成员';
CREATE INDEX idx_eng_project_member_project_id ON eng_project_member (project_id);

CREATE TABLE eng_project_task (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    project_id         BIGINT        NOT NULL,
    stage              VARCHAR(32)   NOT NULL,
    name               VARCHAR(128)  NOT NULL,
    owner_id           BIGINT        NOT NULL,
    plan_start         DATE          NOT NULL,
    plan_end           DATE          NOT NULL,
    actual_end         DATE          NULL,
    task_status        VARCHAR(16)   NOT NULL DEFAULT 'TODO' COMMENT 'TODO/DOING/DONE/CANCELED',
    deliverable        VARCHAR(256)  NULL,
    weight             INT           NOT NULL DEFAULT 1 COMMENT '权重 1～10',
    remark             VARCHAR(512)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '项目任务';
CREATE INDEX idx_eng_project_task_project_id ON eng_project_task (project_id);
CREATE INDEX idx_eng_project_task_owner_id ON eng_project_task (owner_id);

CREATE TABLE eng_sample (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no             VARCHAR(80)  NOT NULL,
    doc_date           DATE         NULL,
    status             VARCHAR(20)  NOT NULL,
    org_id             BIGINT       NULL,
    dept_id            BIGINT       NULL,
    owner_id           BIGINT       NULL,
    source_type        VARCHAR(32)  NULL,
    source_id          BIGINT       NULL,
    source_no          VARCHAR(64)  NULL,
    remark             VARCHAR(512) NULL,
    sample_type        VARCHAR(16)   NOT NULL DEFAULT 'CUSTOMER' COMMENT 'CUSTOMER/ENGINEERING/CERTIFICATION',
    customer_id        BIGINT        NULL,
    contact_id         BIGINT        NULL,
    project_id         BIGINT        NULL,
    material_id        BIGINT        NOT NULL,
    customer_part_no   VARCHAR(64)   NULL,
    qty                DECIMAL(18,4) NOT NULL,
    required_date      DATE          NOT NULL COMMENT '要求寄出日期',
    make_method        VARCHAR(16)   NOT NULL DEFAULT 'PRODUCE' COMMENT 'PRODUCE/FROM_STOCK',
    prod_order_id      BIGINT        NULL,
    prod_order_no      VARCHAR(64)   NULL,
    purpose            VARCHAR(512)  NOT NULL,
    requirements       VARCHAR(1000) NULL,
    ship_date          DATE          NULL,
    courier            VARCHAR(32)   NULL,
    tracking_no        VARCHAR(64)   NULL,
    ship_address       VARCHAR(512)  NULL,
    stock_out_id       BIGINT        NULL,
    stock_out_no       VARCHAR(64)   NULL,
    stock_out_done     TINYINT       NOT NULL DEFAULT 0 COMMENT '出库单已确认',
    feedback_result    VARCHAR(16)   NULL COMMENT 'APPROVED/CONDITIONAL/REJECTED',
    feedback_date      DATE          NULL,
    feedback_content   VARCHAR(1000) NULL,
    sample_status      VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/MAKING/READY/SHIPPED/FEEDBACK/CLOSED/VOIDED',
    close_reason       VARCHAR(256)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '样品单';
CREATE INDEX idx_eng_sample_material_id ON eng_sample (material_id);

CREATE TABLE eng_tooling (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code               VARCHAR(32)   NOT NULL COMMENT '工装编号',
    name               VARCHAR(128)  NOT NULL,
    tooling_type       VARCHAR(32)   NOT NULL COMMENT '字典 eng_tooling_type',
    spec               VARCHAR(256)  NULL,
    ownership          VARCHAR(16)   NOT NULL DEFAULT 'OWN' COMMENT 'OWN/CUSTOMER',
    customer_id        BIGINT        NULL,
    cavity             INT           NOT NULL DEFAULT 1 COMMENT '模穴数',
    design_life        INT           NULL COMMENT '设计寿命（次）',
    used_count         INT           NOT NULL DEFAULT 0 COMMENT '已使用次数',
    maintain_cycle     INT           NULL COMMENT '保养周期（次）',
    last_maintain_count INT           NOT NULL DEFAULT 0 COMMENT '上次保养时的使用次数',
    location           VARCHAR(64)   NULL,
    tooling_status     VARCHAR(16)   NOT NULL DEFAULT 'IN_STOCK' COMMENT 'IN_STOCK/IN_USE/LENT/REPAIRING/SCRAPPED',
    holder_id          BIGINT        NULL,
    supplier_name      VARCHAR(128)  NULL,
    purchase_date      DATE          NULL,
    purchase_amount    DECIMAL(18,2) NULL,
    allow_over_life    TINYINT       NOT NULL DEFAULT 0 COMMENT '超寿命继续使用',
    over_life_reason   VARCHAR(256)  NULL,
    life_warned        TINYINT       NOT NULL DEFAULT 0 COMMENT '已发寿命预警',
    remark             VARCHAR(512)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '工装台账';
CREATE INDEX idx_eng_tooling_code ON eng_tooling (code);

CREATE TABLE eng_tooling_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    tooling_id         BIGINT        NOT NULL,
    material_id        BIGINT        NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '工装适用物料';
CREATE INDEX idx_eng_tooling_material_tooling_id ON eng_tooling_material (tooling_id);
CREATE INDEX idx_eng_tooling_material_material_id ON eng_tooling_material (material_id);

CREATE TABLE eng_tooling_record (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    tooling_id         BIGINT        NOT NULL,
    record_type        VARCHAR(16)   NOT NULL COMMENT 'LEND/RETURN/USAGE/MAINTAIN/REPAIR_START/REPAIR_END/SCRAP/ADJUST',
    record_count       INT           NULL COMMENT 'USAGE 本次次数；ADJUST 调整后次数',
    user_id            BIGINT        NULL COMMENT '经办人',
    source_doc_no      VARCHAR(64)   NULL,
    content            VARCHAR(512)  NULL,
    cost               DECIMAL(18,2) NULL,
    occurred_at        DATETIME      NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '工装使用与维护记录';
CREATE INDEX idx_eng_tooling_record_tooling_id ON eng_tooling_record (tooling_id);

CREATE TABLE eng_certification (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    cert_type          VARCHAR(32)   NOT NULL COMMENT '字典 eng_cert_type',
    cert_no            VARCHAR(64)   NOT NULL,
    name               VARCHAR(128)  NOT NULL,
    issuing_body       VARCHAR(128)  NOT NULL,
    holder             VARCHAR(128)  NULL,
    issue_date         DATE          NOT NULL,
    expire_date        DATE          NULL COMMENT '为空表示长期有效',
    countries          VARCHAR(256)  NULL COMMENT 'ISO 代码逗号分隔',
    scope              VARCHAR(1000) NULL,
    cert_status        VARCHAR(16)   NOT NULL DEFAULT 'VALID' COMMENT 'VALID/REVOKED',
    revoke_reason      VARCHAR(256)  NULL,
    reminded_days      VARCHAR(64)   NULL COMMENT '已提醒的阈值（天）',
    remark             VARCHAR(512)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '认证证书';
CREATE INDEX idx_eng_certification_cert_type_cert_no ON eng_certification (cert_type,cert_no);

CREATE TABLE eng_certification_material (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    certification_id   BIGINT        NOT NULL,
    material_id        BIGINT        NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '证书适用物料';
CREATE INDEX idx_eng_certification_material_certification_id ON eng_certification_material (certification_id);
CREATE INDEX idx_eng_certification_material_material_id ON eng_certification_material (material_id);
