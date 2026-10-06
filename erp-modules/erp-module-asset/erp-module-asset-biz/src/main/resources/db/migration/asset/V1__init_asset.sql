-- 固定资产台账（需求 15-固定资产，《编码规则管理制度》LD-QA-MS-001 5.4）
-- 资产编码 LD{公司}-{分类}-{名称缩写}-{年月}-{流水}，如 LD1-PD-CPJ-264-001
CREATE TABLE ast_asset (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code               VARCHAR(32)   NOT NULL COMMENT '资产编码',
    company_no         VARCHAR(4)    NOT NULL COMMENT '工厂代码（字典 sys_factory），编码 LD 后取末位：1 广东蓝电、0 东莞蓝电',
    asset_class        VARCHAR(8)    NOT NULL COMMENT '分类（字典 ast_asset_class）：PD/QA/EN/PU/HR/GM/CU',
    name               VARCHAR(128)  NOT NULL COMMENT '资产名称',
    name_abbr          VARCHAR(3)    NOT NULL COMMENT '名称缩写（3 位大写字母，不足用 X 补位）',
    spec               VARCHAR(256)  NULL COMMENT '规格型号',
    purchase_date      DATE          NOT NULL COMMENT '购置日期（编码年月）',
    dept_id            BIGINT        NULL COMMENT '使用部门',
    custodian_id       BIGINT        NULL COMMENT '保管人',
    location           VARCHAR(128)  NULL COMMENT '存放位置',
    supplier_name      VARCHAR(128)  NULL COMMENT '供应商',
    customer_name      VARCHAR(128)  NULL COMMENT '所属客户（客户资产）',
    original_value     DECIMAL(18,2) NULL COMMENT '原值（元）',
    useful_life_months INT           NULL COMMENT '使用年限（月）',
    asset_status       VARCHAR(16)   NOT NULL COMMENT 'IN_USE/IDLE/REPAIRING/SCRAPPED',
    scrapped_date      DATE          NULL,
    scrap_reason       VARCHAR(256)  NULL,
    remark             VARCHAR(512)  NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_ast_asset_code UNIQUE (code)
) COMMENT '固定资产';
CREATE INDEX idx_ast_asset_class ON ast_asset (asset_class);
CREATE INDEX idx_ast_asset_dept ON ast_asset (dept_id);
