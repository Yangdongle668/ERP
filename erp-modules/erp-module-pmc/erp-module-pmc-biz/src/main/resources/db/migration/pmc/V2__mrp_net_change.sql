-- MRP 净变更（需求 06-03 run_type = NET_CHANGE）：记录每次运算各物料计算结果的指纹，下次净变更运算只替换指纹变化的物料
CREATE TABLE pmc_mrp_fingerprint (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL COMMENT '建议、追溯、例外、供需平衡的 SHA-256',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT 'MRP 物料结果指纹';
CREATE INDEX idx_pmc_mrp_fp_run ON pmc_mrp_fingerprint (run_id, material_id);
