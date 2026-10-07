-- 仅测试：主线 V7 删除了 V2 的示例类别，集成测试仍按 ID 501～507 建测试物料，这里补回
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 501, NULL, 'RAW', '原材料', 'RAW', 'RAW', 'PCS', 'BATCH', 1, '/501/', 1, 910, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 501)
UNION ALL SELECT 502, NULL, 'FPC', 'FPC', 'FPC', 'RAW', 'PCS', 'BATCH', 1, '/502/', 1, 920, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 502)
UNION ALL SELECT 503, NULL, 'ELEC', '电子料', 'ELEC', 'RAW', 'PCS', 'BATCH', 1, '/503/', 1, 930, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 503)
UNION ALL SELECT 504, NULL, 'PKG', '示例包材', 'PKG', 'PACKAGING', 'PCS', 'NONE', 1, '/504/', 1, 940, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 504)
UNION ALL SELECT 505, NULL, 'AUX', '示例辅料', 'AUX', 'AUXILIARY', 'PCS', 'NONE', 0, '/505/', 1, 950, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 505)
UNION ALL SELECT 506, NULL, 'SEMI', '半成品', 'SF', 'SEMI_FINISHED', 'PCS', 'BATCH', 0, '/506/', 1, 960, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 506)
UNION ALL SELECT 507, NULL, 'FG', '成品', 'FG', 'FINISHED', 'PCS', 'BATCH', 0, '/507/', 1, 970, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE id = 507);
