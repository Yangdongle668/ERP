-- 研发工程：物料
CREATE TABLE eng_material (
    id            BIGINT       NOT NULL PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL COMMENT '物料编码',
    name          VARCHAR(128) NOT NULL COMMENT '物料名称',
    name_en       VARCHAR(256) NULL COMMENT '英文名称（对外单据使用）',
    spec          VARCHAR(512) NULL COMMENT '规格型号',
    material_type VARCHAR(32)  NOT NULL COMMENT 'RAW/SEMI_FINISHED/FINISHED/PACKAGING/AUXILIARY/PHANTOM',
    category_id   BIGINT       NULL COMMENT '物料类别（如 FPC、电子料）',
    base_uom      VARCHAR(16)  NOT NULL COMMENT '基本单位',
    status        VARCHAR(16)  NOT NULL COMMENT 'DRAFT/ENABLED/DISABLED',
    remark        VARCHAR(512) NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_eng_material_code UNIQUE (code)
) COMMENT '物料';

CREATE INDEX idx_eng_material_name ON eng_material (name);
CREATE INDEX idx_eng_material_category ON eng_material (category_id);
