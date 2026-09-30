-- 序列号物料盘点：实盘 / 复盘序列号清单（逗号分隔）与比对出的盘盈、盘亏序列号（INV-CNT-R07）
ALTER TABLE inv_count_line ADD COLUMN count_serials TEXT NULL;
ALTER TABLE inv_count_line ADD COLUMN recount_serials TEXT NULL;
ALTER TABLE inv_count_line ADD COLUMN gain_serials TEXT NULL;
ALTER TABLE inv_count_line ADD COLUMN loss_serials TEXT NULL;
