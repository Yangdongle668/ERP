-- NCR 降级使用（需求 10-03 1.2 DOWNGRADE）：处置明细记录降级后的物料
ALTER TABLE qc_ncr_disposition ADD COLUMN target_material_id BIGINT NULL COMMENT '降级使用：降级后的物料';
