-- 研发工程第 1 批：物料类别、物料属性组、单位换算、BOM（需求 05-研发工程/01～03）

-- ==================== 物料类别 ====================
CREATE TABLE eng_material_category (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    parent_id               BIGINT       NULL,
    code                    VARCHAR(16)  NOT NULL COMMENT '类别编码，大写字母数字',
    name                    VARCHAR(64)  NOT NULL,
    code_prefix             VARCHAR(8)   NOT NULL COMMENT '物料编码前缀（编码规则变量 categoryPrefix）',
    default_material_type   VARCHAR(32)  NOT NULL,
    default_base_uom        VARCHAR(16)  NULL,
    default_tracking        VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/BATCH/SERIAL',
    default_iqc_required    TINYINT      NOT NULL DEFAULT 1,
    default_shelf_life_days INT          NULL,
    path                    VARCHAR(256) NOT NULL COMMENT '祖先路径 /id/id/',
    level                   INT          NOT NULL,
    sort                    INT          NOT NULL DEFAULT 0,
    status                  VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark                  VARCHAR(256) NULL,
    version                 INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_eng_material_category_code UNIQUE (code)
) COMMENT '物料类别';
CREATE INDEX idx_eng_material_category_parent ON eng_material_category (parent_id);

INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
VALUES (501, NULL, 'RAW', '原材料', 'RAW', 'RAW', 'PCS', 'BATCH', 1, '/501/', 1, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (502, NULL, 'FPC', 'FPC', 'FPC', 'RAW', 'PCS', 'BATCH', 1, '/502/', 1, 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (503, NULL, 'ELEC', '电子料', 'ELEC', 'RAW', 'PCS', 'BATCH', 1, '/503/', 1, 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (504, NULL, 'PKG', '包材', 'PKG', 'PACKAGING', 'PCS', 'NONE', 1, '/504/', 1, 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (505, NULL, 'AUX', '辅料', 'AUX', 'AUXILIARY', 'PCS', 'NONE', 0, '/505/', 1, 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (506, NULL, 'SEMI', '半成品', 'SF', 'SEMI_FINISHED', 'PCS', 'BATCH', 0, '/506/', 1, 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (507, NULL, 'FG', '成品', 'FG', 'FINISHED', 'PCS', 'BATCH', 0, '/507/', 1, 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==================== 物料：补充属性组 ====================
ALTER TABLE eng_material ADD COLUMN drawing_no VARCHAR(64) NULL COMMENT '图号';
ALTER TABLE eng_material ADD COLUMN revision VARCHAR(16) NULL COMMENT '图纸版本';
ALTER TABLE eng_material ADD COLUMN brand VARCHAR(64) NULL;
ALTER TABLE eng_material ADD COLUMN manufacturer VARCHAR(128) NULL;
ALTER TABLE eng_material ADD COLUMN mpn VARCHAR(128) NULL COMMENT '制造商料号';
ALTER TABLE eng_material ADD COLUMN mpn_key VARCHAR(128) NULL COMMENT '查重键：制造商料号去空格大写';
ALTER TABLE eng_material ADD COLUMN dup_key VARCHAR(700) NULL COMMENT '查重键：名称+规格去空格大写';
ALTER TABLE eng_material ADD COLUMN hs_code VARCHAR(16) NULL;
ALTER TABLE eng_material ADD COLUMN unit_net_weight DECIMAL(18,4) NULL;
ALTER TABLE eng_material ADD COLUMN unit_gross_weight DECIMAL(18,4) NULL;
ALTER TABLE eng_material ADD COLUMN image_file_id BIGINT NULL;
-- 计划属性
ALTER TABLE eng_material ADD COLUMN source_type VARCHAR(16) NULL COMMENT 'PURCHASE/MAKE/OUTSOURCE';
ALTER TABLE eng_material ADD COLUMN lead_time_days INT NOT NULL DEFAULT 0;
ALTER TABLE eng_material ADD COLUMN safety_stock DECIMAL(18,4) NOT NULL DEFAULT 0;
ALTER TABLE eng_material ADD COLUMN max_stock DECIMAL(18,4) NULL;
ALTER TABLE eng_material ADD COLUMN order_policy VARCHAR(16) NOT NULL DEFAULT 'LOT_FOR_LOT' COMMENT 'LOT_FOR_LOT/FIXED_QTY/PERIOD';
ALTER TABLE eng_material ADD COLUMN fixed_lot_qty DECIMAL(18,4) NULL;
ALTER TABLE eng_material ADD COLUMN period_days INT NULL;
ALTER TABLE eng_material ADD COLUMN moq DECIMAL(18,4) NOT NULL DEFAULT 0;
ALTER TABLE eng_material ADD COLUMN mpq DECIMAL(18,4) NOT NULL DEFAULT 0;
ALTER TABLE eng_material ADD COLUMN planner_id BIGINT NULL;
ALTER TABLE eng_material ADD COLUMN low_level_code INT NOT NULL DEFAULT 0;
-- 采购属性
ALTER TABLE eng_material ADD COLUMN buyer_id BIGINT NULL;
ALTER TABLE eng_material ADD COLUMN purchase_uom VARCHAR(16) NULL;
ALTER TABLE eng_material ADD COLUMN over_receive_pct DECIMAL(9,6) NOT NULL DEFAULT 0;
-- 库存属性
ALTER TABLE eng_material ADD COLUMN tracking VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/BATCH/SERIAL';
ALTER TABLE eng_material ADD COLUMN issue_rule VARCHAR(16) NOT NULL DEFAULT 'FIFO' COMMENT 'FIFO/FEFO';
ALTER TABLE eng_material ADD COLUMN shelf_life_days INT NULL;
ALTER TABLE eng_material ADD COLUMN min_remaining_life_pct DECIMAL(9,6) NULL;
-- 质量属性
ALTER TABLE eng_material ADD COLUMN iqc_required TINYINT NOT NULL DEFAULT 1;
ALTER TABLE eng_material ADD COLUMN fqc_required TINYINT NOT NULL DEFAULT 0;
ALTER TABLE eng_material ADD COLUMN oqc_required TINYINT NOT NULL DEFAULT 0;
-- 财务与销售属性
ALTER TABLE eng_material ADD COLUMN standard_cost DECIMAL(24,6) NULL;
ALTER TABLE eng_material ADD COLUMN sales_uom VARCHAR(16) NULL;
ALTER TABLE eng_material ADD COLUMN purchase_tax_rate DECIMAL(9,6) NOT NULL DEFAULT 0.13;
ALTER TABLE eng_material ADD COLUMN sales_tax_rate DECIMAL(9,6) NOT NULL DEFAULT 0.13;

CREATE INDEX idx_eng_material_mpn ON eng_material (mpn_key);
CREATE INDEX idx_eng_material_dup ON eng_material (category_id, dup_key);

-- ==================== 物料单位换算 ====================
CREATE TABLE eng_material_uom (
    id          BIGINT         NOT NULL PRIMARY KEY,
    material_id BIGINT         NOT NULL,
    uom         VARCHAR(16)    NOT NULL COMMENT '辅助单位',
    rate        DECIMAL(24,10) NOT NULL COMMENT '1 辅助单位 = rate 基本单位',
    remark      VARCHAR(128)   NULL,
    version     INT            NOT NULL DEFAULT 0,
    created_by  BIGINT         NULL,
    created_at  DATETIME       NOT NULL,
    updated_by  BIGINT         NULL,
    updated_at  DATETIME       NOT NULL,
    deleted     TINYINT        NOT NULL DEFAULT 0
) COMMENT '物料单位换算';
CREATE INDEX idx_eng_material_uom ON eng_material_uom (material_id, uom);

-- ==================== BOM ====================
CREATE TABLE eng_bom (
    id              BIGINT         NOT NULL PRIMARY KEY,
    doc_no          VARCHAR(80)    NOT NULL COMMENT '父件编码-V版本',
    doc_date        DATE           NULL,
    status          VARCHAR(20)    NOT NULL,
    org_id          BIGINT         NULL,
    dept_id         BIGINT         NULL,
    owner_id        BIGINT         NULL,
    source_type     VARCHAR(32)    NULL,
    source_id       BIGINT         NULL,
    source_no       VARCHAR(64)    NULL,
    remark          VARCHAR(512)   NULL,
    material_id     BIGINT         NOT NULL COMMENT '父件',
    bom_version     INT            NOT NULL COMMENT '版本号，同一父件从 1 递增',
    base_qty        DECIMAL(18,4)  NOT NULL DEFAULT 1,
    is_default      TINYINT        NOT NULL DEFAULT 0,
    effective_date  DATE           NULL COMMENT '成为默认版本的日期',
    description     VARCHAR(256)   NULL,
    ecn_id          BIGINT         NULL,
    copied_from_id  BIGINT         NULL,
    line_count      INT            NOT NULL DEFAULT 0,
    version         INT            NOT NULL DEFAULT 0,
    created_by      BIGINT         NULL,
    created_at      DATETIME       NOT NULL,
    updated_by      BIGINT         NULL,
    updated_at      DATETIME       NOT NULL,
    deleted         TINYINT        NOT NULL DEFAULT 0
) COMMENT 'BOM 头';
CREATE INDEX idx_eng_bom_material ON eng_bom (material_id, bom_version);

CREATE TABLE eng_bom_line (
    id             BIGINT        NOT NULL PRIMARY KEY,
    bom_id         BIGINT        NOT NULL,
    line_no        INT           NOT NULL,
    component_id   BIGINT        NOT NULL,
    qty_per        DECIMAL(18,4) NOT NULL,
    uom            VARCHAR(16)   NOT NULL,
    scrap_rate     DECIMAL(9,6)  NOT NULL DEFAULT 0,
    position_no    VARCHAR(1024) NULL,
    issue_method   VARCHAR(16)   NOT NULL DEFAULT 'PICK' COMMENT 'PICK/BACKFLUSH',
    operation_seq  INT           NULL,
    is_key         TINYINT       NOT NULL DEFAULT 0,
    remark         VARCHAR(256)  NULL,
    version        INT           NOT NULL DEFAULT 0,
    created_by     BIGINT        NULL,
    created_at     DATETIME      NOT NULL,
    updated_by     BIGINT        NULL,
    updated_at     DATETIME      NOT NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0
) COMMENT 'BOM 行';
CREATE INDEX idx_eng_bom_line_bom ON eng_bom_line (bom_id, line_no);
CREATE INDEX idx_eng_bom_line_component ON eng_bom_line (component_id);

CREATE TABLE eng_bom_substitute (
    id             BIGINT         NOT NULL PRIMARY KEY,
    bom_id         BIGINT         NOT NULL,
    bom_line_id    BIGINT         NOT NULL,
    substitute_id  BIGINT         NOT NULL,
    priority       INT            NOT NULL DEFAULT 1,
    ratio          DECIMAL(24,10) NOT NULL DEFAULT 1 COMMENT '1 个主料 = ratio 个替代料',
    remark         VARCHAR(128)   NULL,
    version        INT            NOT NULL DEFAULT 0,
    created_by     BIGINT         NULL,
    created_at     DATETIME       NOT NULL,
    updated_by     BIGINT         NULL,
    updated_at     DATETIME       NOT NULL,
    deleted        TINYINT        NOT NULL DEFAULT 0
) COMMENT 'BOM 替代料';
CREATE INDEX idx_eng_bom_sub_bom ON eng_bom_substitute (bom_id);
CREATE INDEX idx_eng_bom_sub_material ON eng_bom_substitute (substitute_id);
