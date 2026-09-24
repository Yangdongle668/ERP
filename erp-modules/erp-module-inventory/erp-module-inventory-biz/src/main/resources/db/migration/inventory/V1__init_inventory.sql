-- 仓库模块（需求 08-仓库）：仓库与库位、库存余额与流水、批次序列号、出入库/调拨/盘点单据、库存期间

-- ==================== 仓库与库位 ====================
CREATE TABLE inv_warehouse (
    id               BIGINT       NOT NULL PRIMARY KEY,
    code             VARCHAR(16)  NOT NULL,
    name             VARCHAR(64)  NOT NULL,
    warehouse_type   VARCHAR(16)  NOT NULL COMMENT 'RAW/SEMI/FG/FPC/ELEC/PKG/AUX/NG/QC/RTN',
    org_id           BIGINT       NULL,
    manager_id       BIGINT       NULL,
    address          VARCHAR(256) NULL,
    location_enabled TINYINT      NOT NULL DEFAULT 0,
    allow_negative   TINYINT      NOT NULL DEFAULT 0,
    is_default       TINYINT      NOT NULL DEFAULT 0,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark           VARCHAR(256) NULL,
    version          INT          NOT NULL DEFAULT 0,
    created_by       BIGINT       NULL,
    created_at       DATETIME     NOT NULL,
    updated_by       BIGINT       NULL,
    updated_at       DATETIME     NOT NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_warehouse_code UNIQUE (code)
) COMMENT '仓库';

CREATE TABLE inv_location (
    id           BIGINT       NOT NULL PRIMARY KEY,
    warehouse_id BIGINT       NOT NULL,
    code         VARCHAR(32)  NOT NULL,
    name         VARCHAR(64)  NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark       VARCHAR(128) NULL,
    version      INT          NOT NULL DEFAULT 0,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0
) COMMENT '库位';
CREATE INDEX idx_inv_location_wh ON inv_location (warehouse_id, code);

CREATE TABLE inv_warehouse_user (
    warehouse_id BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    PRIMARY KEY (warehouse_id, user_id)
) COMMENT '仓库操作人员（仓库数据权限）';

CREATE TABLE inv_category_warehouse (
    id           BIGINT   NOT NULL PRIMARY KEY,
    category_id  BIGINT   NOT NULL,
    warehouse_id BIGINT   NOT NULL,
    version      INT      NOT NULL DEFAULT 0,
    created_by   BIGINT   NULL,
    created_at   DATETIME NOT NULL,
    updated_by   BIGINT   NULL,
    updated_at   DATETIME NOT NULL,
    deleted      TINYINT  NOT NULL DEFAULT 0
) COMMENT '物料类别默认仓';
CREATE INDEX idx_inv_category_wh ON inv_category_warehouse (category_id);

INSERT INTO inv_warehouse (id, code, name, warehouse_type, is_default, status, created_at, updated_at) VALUES
    (801, 'W-RAW', '原材料仓', 'RAW', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (802, 'W-SEMI', '半成品仓', 'SEMI', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (803, 'W-FG', '成品仓', 'FG', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (804, 'W-FPC', 'FPC 仓', 'FPC', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (805, 'W-ELEC', '电子料仓', 'ELEC', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (806, 'W-PKG', '包材仓', 'PKG', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (807, 'W-AUX', '辅料仓', 'AUX', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (808, 'W-NG', '不良品仓', 'NG', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (809, 'W-QC', '待检仓', 'QC', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (810, 'W-RTN', '退货仓', 'RTN', 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 类别默认仓：物料类别 ID 为研发工程初始数据（501 RAW … 507 FG）
INSERT INTO inv_category_warehouse (id, category_id, warehouse_id, created_at, updated_at) VALUES
    (821, 501, 801, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (822, 502, 804, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (823, 503, 805, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (824, 504, 806, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (825, 505, 807, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (826, 506, 802, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (827, 507, 803, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==================== 库存余额与流水 ====================
CREATE TABLE inv_stock (
    id            BIGINT        NOT NULL PRIMARY KEY,
    material_id   BIGINT        NOT NULL,
    warehouse_id  BIGINT        NOT NULL,
    location_id   BIGINT        NOT NULL DEFAULT 0,
    batch_no      VARCHAR(64)   NOT NULL DEFAULT '',
    qty           DECIMAL(18,4) NOT NULL DEFAULT 0,
    last_in_date  DATE          NULL,
    last_out_date DATE          NULL,
    version       INT           NOT NULL DEFAULT 0,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_stock_dim UNIQUE (material_id, warehouse_id, location_id, batch_no)
) COMMENT '库存余额（物料 + 仓库 + 库位 + 批次）';
CREATE INDEX idx_inv_stock_wh ON inv_stock (warehouse_id, material_id);

CREATE TABLE inv_stock_txn (
    id              BIGINT        NOT NULL PRIMARY KEY,
    txn_no          VARCHAR(32)   NOT NULL,
    doc_type        VARCHAR(16)   NOT NULL COMMENT 'STOCK_IN/STOCK_OUT/TRANSFER/COUNT',
    biz_type        VARCHAR(32)   NOT NULL COMMENT '出入库类型，如 PURCHASE_IN',
    doc_id          BIGINT        NOT NULL,
    doc_line_id     BIGINT        NULL,
    doc_no          VARCHAR(64)   NOT NULL,
    source_type     VARCHAR(32)   NULL,
    source_id       BIGINT        NULL,
    source_line_id  BIGINT        NULL,
    source_no       VARCHAR(64)   NULL,
    direction       VARCHAR(8)    NOT NULL COMMENT 'IN/OUT',
    material_id     BIGINT        NOT NULL,
    warehouse_id    BIGINT        NOT NULL,
    location_id     BIGINT        NOT NULL DEFAULT 0,
    batch_no        VARCHAR(64)   NOT NULL DEFAULT '',
    qty             DECIMAL(18,4) NOT NULL,
    unit_cost       DECIMAL(24,6) NULL,
    amount          DECIMAL(18,2) NULL,
    balance_qty     DECIMAL(18,4) NOT NULL,
    biz_date        DATE          NOT NULL,
    period          VARCHAR(7)    NOT NULL,
    is_reversal     TINYINT       NOT NULL DEFAULT 0,
    reversed_txn_id BIGINT        NULL,
    operator_id     BIGINT        NULL,
    version         INT           NOT NULL DEFAULT 0,
    created_by      BIGINT        NULL,
    created_at      DATETIME      NOT NULL,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME      NOT NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0
) COMMENT '库存流水（只增不改）';
CREATE INDEX idx_inv_txn_material_date ON inv_stock_txn (material_id, biz_date);
CREATE INDEX idx_inv_txn_doc ON inv_stock_txn (doc_type, doc_id);
CREATE INDEX idx_inv_txn_source ON inv_stock_txn (source_type, source_id);
CREATE INDEX idx_inv_txn_period ON inv_stock_txn (period);

CREATE TABLE inv_reservation (
    id           BIGINT        NOT NULL PRIMARY KEY,
    material_id  BIGINT        NOT NULL,
    warehouse_id BIGINT        NULL,
    batch_no     VARCHAR(64)   NULL,
    qty          DECIMAL(18,4) NOT NULL,
    released_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    biz_type     VARCHAR(32)   NOT NULL,
    biz_id       BIGINT        NOT NULL,
    biz_line_id  BIGINT        NULL,
    biz_no       VARCHAR(64)   NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT '库存预留';
CREATE INDEX idx_inv_reservation_material ON inv_reservation (material_id, status);
CREATE INDEX idx_inv_reservation_biz ON inv_reservation (biz_type, biz_id);

-- ==================== 批次与序列号 ====================
CREATE TABLE inv_batch (
    id                BIGINT       NOT NULL PRIMARY KEY,
    material_id       BIGINT       NOT NULL,
    batch_no          VARCHAR(64)  NOT NULL,
    supplier_id       BIGINT       NULL,
    supplier_batch_no VARCHAR(64)  NULL,
    production_date   DATE         NULL,
    expire_date       DATE         NULL,
    first_in_date     DATE         NOT NULL,
    first_in_at       DATETIME     NULL COMMENT '首次入库时间（待检超时）',
    source_type       VARCHAR(32)  NULL,
    source_no         VARCHAR(64)  NULL,
    is_concession     TINYINT      NOT NULL DEFAULT 0,
    frozen            TINYINT      NOT NULL DEFAULT 0,
    frozen_reason     VARCHAR(256) NULL,
    frozen_by_module  VARCHAR(32)  NULL COMMENT '冻结来源模块（quality 冻结时仓库人员不能解冻）',
    frozen_source_no  VARCHAR(64)  NULL,
    remark            VARCHAR(256) NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_by        BIGINT       NULL,
    created_at        DATETIME     NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME     NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_batch UNIQUE (material_id, batch_no)
) COMMENT '批次档案';

CREATE TABLE inv_serial (
    id            BIGINT      NOT NULL PRIMARY KEY,
    material_id   BIGINT      NOT NULL,
    serial_no     VARCHAR(64) NOT NULL,
    batch_no      VARCHAR(64) NULL,
    serial_status VARCHAR(16) NOT NULL COMMENT 'IN_STOCK/OUT/SCRAPPED',
    warehouse_id  BIGINT      NULL,
    location_id   BIGINT      NULL,
    last_txn_id   BIGINT      NULL,
    customer_id   BIGINT      NULL,
    version       INT         NOT NULL DEFAULT 0,
    created_by    BIGINT      NULL,
    created_at    DATETIME    NOT NULL,
    updated_by    BIGINT      NULL,
    updated_at    DATETIME    NOT NULL,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_serial UNIQUE (material_id, serial_no)
) COMMENT '序列号';

CREATE TABLE inv_serial_txn (
    id         BIGINT      NOT NULL PRIMARY KEY,
    serial_id  BIGINT      NOT NULL,
    txn_id     BIGINT      NOT NULL,
    direction  VARCHAR(8)  NOT NULL,
    doc_no     VARCHAR(64) NOT NULL,
    created_at DATETIME    NOT NULL
) COMMENT '序列号出入库记录';
CREATE INDEX idx_inv_serial_txn ON inv_serial_txn (serial_id);

-- ==================== 入库单 ====================
CREATE TABLE inv_stock_in (
    id            BIGINT       NOT NULL PRIMARY KEY,
    doc_no        VARCHAR(64)  NOT NULL,
    doc_date      DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    org_id        BIGINT       NULL,
    dept_id       BIGINT       NULL,
    owner_id      BIGINT       NULL,
    source_type   VARCHAR(32)  NULL,
    source_id     BIGINT       NULL,
    source_no     VARCHAR(64)  NULL,
    source_date   DATE         NULL,
    remark        VARCHAR(512) NULL,
    in_type       VARCHAR(32)  NOT NULL,
    warehouse_id  BIGINT       NOT NULL,
    supplier_id   BIGINT       NULL,
    customer_id   BIGINT       NULL,
    reason        VARCHAR(32)  NULL,
    manual        TINYINT      NOT NULL DEFAULT 0 COMMENT '手工新建（其他入库）',
    confirmed_by  BIGINT       NULL,
    confirmed_at  DATETIME     NULL,
    reject_reason VARCHAR(256) NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_stock_in_no UNIQUE (doc_no)
) COMMENT '入库单';
CREATE INDEX idx_inv_stock_in_source ON inv_stock_in (source_type, source_id);
CREATE INDEX idx_inv_stock_in_wh ON inv_stock_in (warehouse_id, status);

CREATE TABLE inv_stock_in_line (
    id                BIGINT        NOT NULL PRIMARY KEY,
    stock_in_id       BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    material_id       BIGINT        NOT NULL,
    uom               VARCHAR(16)   NOT NULL,
    qty               DECIMAL(18,4) NOT NULL,
    base_qty          DECIMAL(18,4) NOT NULL,
    location_id       BIGINT        NULL,
    batch_no          VARCHAR(64)   NULL,
    supplier_batch_no VARCHAR(64)   NULL,
    production_date   DATE          NULL,
    expire_date       DATE          NULL,
    serial_nos        TEXT          NULL,
    unit_cost         DECIMAL(24,6) NULL,
    amount            DECIMAL(18,2) NULL,
    source_line_id    BIGINT        NULL,
    remark            VARCHAR(256)  NULL,
    version           INT           NOT NULL DEFAULT 0,
    created_by        BIGINT        NULL,
    created_at        DATETIME      NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME      NOT NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0
) COMMENT '入库单行';
CREATE INDEX idx_inv_stock_in_line ON inv_stock_in_line (stock_in_id);
CREATE INDEX idx_inv_stock_in_line_material ON inv_stock_in_line (material_id);

-- ==================== 出库单 ====================
CREATE TABLE inv_stock_out (
    id               BIGINT       NOT NULL PRIMARY KEY,
    doc_no           VARCHAR(64)  NOT NULL,
    doc_date         DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    org_id           BIGINT       NULL,
    dept_id          BIGINT       NULL,
    owner_id         BIGINT       NULL,
    source_type      VARCHAR(32)  NULL,
    source_id        BIGINT       NULL,
    source_no        VARCHAR(64)  NULL,
    source_date      DATE         NULL,
    remark           VARCHAR(512) NULL,
    out_type         VARCHAR(32)  NOT NULL,
    warehouse_id     BIGINT       NOT NULL,
    receiver_dept_id BIGINT       NULL,
    receiver_id      BIGINT       NULL,
    supplier_id      BIGINT       NULL,
    customer_id      BIGINT       NULL,
    reason           VARCHAR(32)  NULL,
    manual           TINYINT      NOT NULL DEFAULT 0,
    confirmed_by     BIGINT       NULL,
    confirmed_at     DATETIME     NULL,
    reject_reason    VARCHAR(256) NULL,
    version          INT          NOT NULL DEFAULT 0,
    created_by       BIGINT       NULL,
    created_at       DATETIME     NOT NULL,
    updated_by       BIGINT       NULL,
    updated_at       DATETIME     NOT NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_stock_out_no UNIQUE (doc_no)
) COMMENT '出库单';
CREATE INDEX idx_inv_stock_out_source ON inv_stock_out (source_type, source_id);
CREATE INDEX idx_inv_stock_out_wh ON inv_stock_out (warehouse_id, status);

CREATE TABLE inv_stock_out_line (
    id             BIGINT        NOT NULL PRIMARY KEY,
    stock_out_id   BIGINT        NOT NULL,
    line_no        INT           NOT NULL,
    material_id    BIGINT        NOT NULL,
    uom            VARCHAR(16)   NOT NULL,
    request_qty    DECIMAL(18,4) NOT NULL,
    qty            DECIMAL(18,4) NOT NULL,
    base_qty       DECIMAL(18,4) NOT NULL,
    location_id    BIGINT        NULL,
    batch_no       VARCHAR(64)   NULL,
    serial_nos     TEXT          NULL,
    source_line_id BIGINT        NULL,
    remark         VARCHAR(256)  NULL,
    version        INT           NOT NULL DEFAULT 0,
    created_by     BIGINT        NULL,
    created_at     DATETIME      NOT NULL,
    updated_by     BIGINT        NULL,
    updated_at     DATETIME      NOT NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0
) COMMENT '出库单行';
CREATE INDEX idx_inv_stock_out_line ON inv_stock_out_line (stock_out_id);
CREATE INDEX idx_inv_stock_out_line_material ON inv_stock_out_line (material_id);

-- ==================== 调拨单 ====================
CREATE TABLE inv_transfer (
    id                BIGINT       NOT NULL PRIMARY KEY,
    doc_no            VARCHAR(64)  NOT NULL,
    doc_date          DATE         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    org_id            BIGINT       NULL,
    dept_id           BIGINT       NULL,
    owner_id          BIGINT       NULL,
    source_type       VARCHAR(32)  NULL,
    source_id         BIGINT       NULL,
    source_no         VARCHAR(64)  NULL,
    remark            VARCHAR(512) NULL,
    transfer_type     VARCHAR(16)  NOT NULL,
    from_warehouse_id BIGINT       NOT NULL,
    to_warehouse_id   BIGINT       NOT NULL,
    reason            VARCHAR(256) NULL,
    inspection_id     BIGINT       NULL,
    confirmed_by      BIGINT       NULL,
    confirmed_at      DATETIME     NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_by        BIGINT       NULL,
    created_at        DATETIME     NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME     NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_transfer_no UNIQUE (doc_no)
) COMMENT '调拨单';
CREATE INDEX idx_inv_transfer_source ON inv_transfer (source_type, source_id);

CREATE TABLE inv_transfer_line (
    id               BIGINT        NOT NULL PRIMARY KEY,
    transfer_id      BIGINT        NOT NULL,
    line_no          INT           NOT NULL,
    material_id      BIGINT        NOT NULL,
    qty              DECIMAL(18,4) NOT NULL,
    batch_no         VARCHAR(64)   NULL,
    from_location_id BIGINT        NULL,
    to_location_id   BIGINT        NULL,
    serial_nos       TEXT          NULL,
    judge_result     VARCHAR(16)   NULL COMMENT 'QUALIFIED/CONCESSION/REJECTED',
    source_line_id   BIGINT        NULL,
    remark           VARCHAR(256)  NULL,
    version          INT           NOT NULL DEFAULT 0,
    created_by       BIGINT        NULL,
    created_at       DATETIME      NOT NULL,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME      NOT NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0
) COMMENT '调拨单行';
CREATE INDEX idx_inv_transfer_line ON inv_transfer_line (transfer_id);

-- ==================== 盘点 ====================
CREATE TABLE inv_count (
    id            BIGINT       NOT NULL PRIMARY KEY,
    doc_no        VARCHAR(64)  NOT NULL,
    doc_date      DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL COMMENT '通用单据状态（审批流用）',
    org_id        BIGINT       NULL,
    dept_id       BIGINT       NULL,
    owner_id      BIGINT       NULL,
    source_type   VARCHAR(32)  NULL,
    source_id     BIGINT       NULL,
    source_no     VARCHAR(64)  NULL,
    remark        VARCHAR(512) NULL,
    count_type    VARCHAR(16)  NOT NULL COMMENT 'FULL/PARTIAL',
    warehouse_ids VARCHAR(512) NOT NULL,
    category_ids  VARCHAR(512) NULL,
    location_ids  TEXT         NULL,
    material_ids  TEXT         NULL,
    include_zero  TINYINT      NOT NULL DEFAULT 0,
    blind_count   TINYINT      NOT NULL DEFAULT 1,
    snapshot_at   DATETIME     NULL,
    count_status  VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/COUNTING/SUBMITTED/APPROVED/VOIDED',
    gain_in_id    BIGINT       NULL,
    loss_out_id   BIGINT       NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_count_no UNIQUE (doc_no)
) COMMENT '盘点单';

CREATE TABLE inv_count_line (
    id           BIGINT        NOT NULL PRIMARY KEY,
    count_id     BIGINT        NOT NULL,
    line_no      INT           NOT NULL,
    warehouse_id BIGINT        NOT NULL,
    location_id  BIGINT        NOT NULL DEFAULT 0,
    material_id  BIGINT        NOT NULL,
    batch_no     VARCHAR(64)   NOT NULL DEFAULT '',
    book_qty     DECIMAL(18,4) NOT NULL,
    count_qty    DECIMAL(18,4) NULL,
    recount_qty  DECIMAL(18,4) NULL,
    final_qty    DECIMAL(18,4) NULL,
    diff_qty     DECIMAL(18,4) NULL,
    ref_cost     DECIMAL(24,6) NULL,
    diff_amount  DECIMAL(18,2) NULL,
    need_recount TINYINT       NOT NULL DEFAULT 0,
    is_added     TINYINT       NOT NULL DEFAULT 0,
    reason       VARCHAR(32)   NULL,
    counter_id   BIGINT        NULL,
    counted_at   DATETIME      NULL,
    remark       VARCHAR(256)  NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT '盘点明细';
CREATE INDEX idx_inv_count_line ON inv_count_line (count_id, line_no);
CREATE INDEX idx_inv_count_line_dim ON inv_count_line (warehouse_id, material_id);

-- ==================== 库存期间 ====================
CREATE TABLE inv_period (
    id                BIGINT     NOT NULL PRIMARY KEY,
    period            VARCHAR(6) NOT NULL,
    start_date        DATE       NOT NULL,
    end_date          DATE       NOT NULL,
    period_status     VARCHAR(8) NOT NULL COMMENT 'OPEN/CLOSED',
    closed_by         BIGINT     NULL,
    closed_at         DATETIME   NULL,
    is_opening        TINYINT    NOT NULL DEFAULT 0 COMMENT '启用期间',
    opening_completed TINYINT    NOT NULL DEFAULT 0 COMMENT '期初已完成（仅启用期间）',
    finance_closed    TINYINT    NOT NULL DEFAULT 0 COMMENT '财务已结账（成本已锁定，由财务模块回写）',
    version           INT        NOT NULL DEFAULT 0,
    created_by        BIGINT     NULL,
    created_at        DATETIME   NOT NULL,
    updated_by        BIGINT     NULL,
    updated_at        DATETIME   NOT NULL,
    deleted           TINYINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_period UNIQUE (period)
) COMMENT '库存期间';

CREATE TABLE inv_period_balance (
    id           BIGINT        NOT NULL PRIMARY KEY,
    period       VARCHAR(6)    NOT NULL,
    material_id  BIGINT        NOT NULL,
    warehouse_id BIGINT        NOT NULL,
    qty          DECIMAL(18,4) NOT NULL,
    amount       DECIMAL(18,2) NULL,
    avg_cost     DECIMAL(24,6) NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0
) COMMENT '期末结存';
CREATE INDEX idx_inv_period_balance ON inv_period_balance (period, material_id, warehouse_id);
