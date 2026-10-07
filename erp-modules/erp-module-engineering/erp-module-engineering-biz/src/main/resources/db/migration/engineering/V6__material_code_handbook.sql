-- 物料类别编码按《物料编码手册》（20260618 A02）重建（需求 05-01 第 8 节）。
-- 1) 删除旧版《物料编码原则》预置的类别（V4，LD10～LD41、LDPR、LDCR），已有物料的类别保留；
-- 2) 建立分组「蓝电物料」（LDWL）及 22 个类别；已通过导入建立过同编码的类别时只更新编码前缀、流水号位数并补充编码段。

-- ---------- 1) 删除旧版预置类别 ----------
DELETE FROM eng_code_segment_value WHERE segment_id IN (
    SELECT s.id FROM eng_code_segment s JOIN eng_material_category c ON c.id = s.category_id
    WHERE c.code IN ('LD10','LD11','LD12','LD13','LD14','LD15','LD16','LD17','LD21','LD22','LD23','LD24','LD25','LD26','LD27','LD31','LD41') AND c.id BETWEEN 5101 AND 5119 AND NOT EXISTS (SELECT 1 FROM eng_material m WHERE m.category_id = c.id));
DELETE FROM eng_code_segment WHERE category_id IN (
    SELECT c.id FROM eng_material_category c
    WHERE c.code IN ('LD10','LD11','LD12','LD13','LD14','LD15','LD16','LD17','LD21','LD22','LD23','LD24','LD25','LD26','LD27','LD31','LD41') AND c.id BETWEEN 5101 AND 5119 AND NOT EXISTS (SELECT 1 FROM eng_material m WHERE m.category_id = c.id));
DELETE FROM eng_material_category WHERE code IN ('LD10','LD11','LD12','LD13','LD14','LD15','LD16','LD17','LD21','LD22','LD23','LD24','LD25','LD26','LD27','LD31','LD41') AND id BETWEEN 5101 AND 5119
    AND id NOT IN (SELECT category_id FROM (SELECT DISTINCT category_id FROM eng_material WHERE category_id IS NOT NULL) used);
DELETE FROM eng_material_category WHERE code IN ('LDPR', 'LDCR') AND id BETWEEN 5101 AND 5119
    AND id NOT IN (SELECT parent_id FROM (SELECT DISTINCT parent_id FROM eng_material_category WHERE parent_id IS NOT NULL) children)
    AND id NOT IN (SELECT category_id FROM (SELECT DISTINCT category_id FROM eng_material WHERE category_id IS NOT NULL) used);

-- ---------- 2) 分组「蓝电物料」 ----------
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, remark, created_at, updated_at)
SELECT 5300, NULL, 'LDWL', '蓝电物料', 'LDWL', 'RAW', 'PCS', 'BATCH', 1, '/5300/', 1, 10, 'ENABLED', '《物料编码手册》物料类别分组', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LDWL');

-- ---------- 3) 类别 ----------
-- 电池成品 99-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5301, g.id, 'LD99', '电池成品', '99-', 5, 'FINISHED', 'PCS', 'BATCH', 0, CONCAT(g.path, '5301/'), g.level + 1, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD99');
UPDATE eng_material_category SET code_prefix = '99-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD99';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5501, c.id, '电池类型', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD99' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5501 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550001, 5501, '1', '圆柱锂电池', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501
UNION ALL SELECT 550002, 5501, '2', '软包异形电池', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501
UNION ALL SELECT 550003, 5501, '3', '软包方形电池', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501
UNION ALL SELECT 550004, 5501, '4', '戒指电池', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501
UNION ALL SELECT 550005, 5501, '5', '铝壳方形电池', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501
UNION ALL SELECT 550006, 5501, '6', '铝壳异形电池', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501
UNION ALL SELECT 550007, 5501, '7', '镍氢电池', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5501;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5502, c.id, '标称电压', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD99' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5502 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550008, 5502, '1', '3.60V', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550009, 5502, '2', '3.70V', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550010, 5502, '3', '3.80V', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550011, 5502, '4', '3.85V', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550012, 5502, '5', '3.87V', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550013, 5502, '6', '3.89V', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550014, 5502, '7', '3.90V', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550015, 5502, '8', '3.91V', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550016, 5502, '9', '3.20V', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550017, 5502, 'A', '1.20V', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550018, 5502, 'B', '2.00V', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550019, 5502, 'C', '7.20V', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550020, 5502, 'D', '7.40V', 130, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550021, 5502, 'E', '21.60V', 140, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502
UNION ALL SELECT 550022, 5502, 'F', '28.8V', 150, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5502;
-- 保护板 91-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5302, g.id, 'LD91', '保护板', '91-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5302/'), g.level + 1, 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD91');
UPDATE eng_material_category SET code_prefix = '91-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD91';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5503, c.id, '保护板类别', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD91' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5503 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550023, 5503, '1', '1串', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550024, 5503, '2', '2串', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550025, 5503, '3', '3串', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550026, 5503, '4', '4串', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550027, 5503, '5', '5串', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550028, 5503, '6', '6串', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550029, 5503, '7', '7串', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550030, 5503, '8', '8串', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550031, 5503, '9', '9串', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550032, 5503, 'A', '10串', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550033, 5503, 'B', '11串', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503
UNION ALL SELECT 550034, 5503, 'C', '12串', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5503;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5504, c.id, '工艺类别', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD91' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5504 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550035, 5504, '1', 'PCM硬板', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5504
UNION ALL SELECT 550036, 5504, '2', 'PCM+FPC软硬结合板', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5504
UNION ALL SELECT 550037, 5504, '3', 'BMS', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5504;
-- 线材 92-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5303, g.id, 'LD92', '线材', '92-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5303/'), g.level + 1, 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD92');
UPDATE eng_material_category SET code_prefix = '92-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD92';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5505, c.id, '线材型号', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD92' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5505 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550038, 5505, '1', 'UL3239', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550039, 5505, '2', 'UL3302', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550040, 5505, '3', 'UL10064', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550041, 5505, '4', 'UL3135', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550042, 5505, '5', 'UL3215', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550043, 5505, '6', 'UL1007', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550044, 5505, '7', 'UL1571', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550045, 5505, '8', 'UL1061', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505
UNION ALL SELECT 550046, 5505, '0', '空白（不区分）', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5505;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5506, c.id, '颜色', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD92' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5506 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550047, 5506, '1', '红色', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550048, 5506, '2', '黑色', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550049, 5506, '3', '白色', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550050, 5506, '4', '黄色', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550051, 5506, '5', '蓝色', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550052, 5506, '6', '绿色', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550053, 5506, '7', '紫色', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506
UNION ALL SELECT 550054, 5506, '0', '空白（不区分）', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5506;
-- 端子线 93-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5304, g.id, 'LD93', '端子线', '93-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5304/'), g.level + 1, 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD93');
UPDATE eng_material_category SET code_prefix = '93-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD93';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5507, c.id, '胶壳型号', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD93' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5507 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550055, 5507, '1', '0.8刺破', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550056, 5507, '2', '间距0.8', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550057, 5507, '3', 'SH1.0', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550058, 5507, '4', '间距1.2', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550059, 5507, '5', 'GH1.25', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550060, 5507, '6', 'ZH1.5', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550061, 5507, '7', 'PH2.0', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550062, 5507, '8', 'XH2.54', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550063, 5507, '9', 'VH3.96', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550064, 5507, 'A', 'XT60', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550065, 5507, 'B', 'XT90', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550066, 5507, 'C', 'XT150', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550067, 5507, 'D', 'EC5', 130, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550068, 5507, 'E', 'NH1.0', 140, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507
UNION ALL SELECT 550069, 5507, 'F', 'Molex78172', 150, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5507;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5508, c.id, 'Pin数', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD93' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5508 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550070, 5508, '2', '2PIN', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550071, 5508, '3', '3PIN', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550072, 5508, '4', '4PIN', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550073, 5508, '5', '5PIN', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550074, 5508, '6', '6PIN', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550075, 5508, '7', '7PIN', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550076, 5508, '8', '8PIN', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550077, 5508, '9', '9PIN', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550078, 5508, 'A', '10PIN', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550079, 5508, 'B', '11PIN', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508
UNION ALL SELECT 550080, 5508, 'C', '13PIN', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5508;
-- 连接片 94-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5305, g.id, 'LD94', '连接片', '94-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5305/'), g.level + 1, 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD94');
UPDATE eng_material_category SET code_prefix = '94-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD94';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5509, c.id, '连接片类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD94' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5509 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550081, 5509, '01', '钢镀镍', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5509
UNION ALL SELECT 550082, 5509, '02', '铜镍复合', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5509
UNION ALL SELECT 550083, 5509, '03', '纯镍', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5509;
-- 标签 81-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5306, g.id, 'LD81', '标签', '81-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5306/'), g.level + 1, 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD81');
UPDATE eng_material_category SET code_prefix = '81-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD81';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5510, c.id, '标签材质', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD81' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5510 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550084, 5510, '01', 'PET', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5510
UNION ALL SELECT 550085, 5510, '02', 'PI黑色', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5510
UNION ALL SELECT 550086, 5510, '03', '铜版纸', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5510
UNION ALL SELECT 550087, 5510, '04', '合成纸', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5510;
-- 胶带（卷料） 83-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5307, g.id, 'LD83', '胶带（卷料）', '83-', 5, 'RAW', 'M', 'BATCH', 1, CONCAT(g.path, '5307/'), g.level + 1, 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD83');
UPDATE eng_material_category SET code_prefix = '83-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD83';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5511, c.id, '胶带类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD83' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5511 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550088, 5511, '01', '茶色高温胶带', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511
UNION ALL SELECT 550089, 5511, '02', '美纹胶带', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511
UNION ALL SELECT 550090, 5511, '03', '纤维胶带', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511
UNION ALL SELECT 550091, 5511, '04', '玛拉胶带', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511
UNION ALL SELECT 550092, 5511, '05', '绿色胶带', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511
UNION ALL SELECT 550093, 5511, '06', '哑黑金手指胶带', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511
UNION ALL SELECT 550094, 5511, '07', '3M双面胶', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5511;
-- 泡棉胶（模切） 82-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5308, g.id, 'LD82', '泡棉胶（模切）', '82-', 4, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5308/'), g.level + 1, 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD82');
UPDATE eng_material_category SET code_prefix = '82-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD82';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5512, c.id, '泡棉类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD82' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5512 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550095, 5512, '01', 'EVA泡棉', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5512
UNION ALL SELECT 550096, 5512, '02', '海绵', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5512
UNION ALL SELECT 550097, 5512, '03', '硅胶垫', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5512
UNION ALL SELECT 550098, 5512, '04', 'PET泡棉胶', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5512
UNION ALL SELECT 550099, 5512, '05', 'PE泡棉胶', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5512;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5513, c.id, '泡棉颜色', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD82' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5513 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550100, 5513, '1', '黑色', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5513
UNION ALL SELECT 550101, 5513, '2', '白色', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5513
UNION ALL SELECT 550102, 5513, '3', '淡黄色', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5513;
-- 青稞纸 84-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5309, g.id, 'LD84', '青稞纸', '84-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5309/'), g.level + 1, 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD84');
UPDATE eng_material_category SET code_prefix = '84-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD84';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5514, c.id, '颜色', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD84' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5514 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550103, 5514, '01', '绿色', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5514
UNION ALL SELECT 550104, 5514, '02', '红色', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5514
UNION ALL SELECT 550105, 5514, '03', '黄色', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5514;
-- 五金件 85-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5310, g.id, 'LD85', '五金件', '85-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5310/'), g.level + 1, 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD85');
UPDATE eng_material_category SET code_prefix = '85-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD85';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5515, c.id, '五金件类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD85' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5515 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550106, 5515, '01', '螺丝', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5515
UNION ALL SELECT 550107, 5515, '02', '螺母', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5515
UNION ALL SELECT 550108, 5515, '03', '螺柱', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5515
UNION ALL SELECT 550109, 5515, '04', '其它', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5515;
-- 热缩管 86-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5311, g.id, 'LD86', '热缩管', '86-', 5, 'RAW', 'KG', 'BATCH', 1, CONCAT(g.path, '5311/'), g.level + 1, 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD86');
UPDATE eng_material_category SET code_prefix = '86-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD86';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5516, c.id, '热缩管类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD86' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5516 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550110, 5516, '01', 'PVC热塑膜', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5516
UNION ALL SELECT 550111, 5516, '02', '黑色热缩管', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5516;
-- 塑胶件 87-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5312, g.id, 'LD87', '塑胶件', '87-', 5, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5312/'), g.level + 1, 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD87');
UPDATE eng_material_category SET code_prefix = '87-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD87';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5517, c.id, '塑胶件类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD87' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5517 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550112, 5517, '01', 'AB扣', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517
UNION ALL SELECT 550113, 5517, '02', 'ABS757', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517
UNION ALL SELECT 550114, 5517, '03', '扎带', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517
UNION ALL SELECT 550115, 5517, '04', '缓冲垫', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517
UNION ALL SELECT 550116, 5517, '05', '脚垫', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517
UNION ALL SELECT 550117, 5517, '06', '束线管', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517
UNION ALL SELECT 550118, 5517, '07', 'TPU', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5517;
-- 电芯 79-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5313, g.id, 'LD79', '电芯', '79-', 3, 'SEMI_FINISHED', 'PCS', 'BATCH', 0, CONCAT(g.path, '5313/'), g.level + 1, 130, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD79');
UPDATE eng_material_category SET code_prefix = '79-', code_seq_length = 3, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD79';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5518, c.id, '电芯类型', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD79' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5518 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550119, 5518, '1', 'A品电芯', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5518
UNION ALL SELECT 550120, 5518, '2', 'B品电芯', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5518;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5519, c.id, '电芯形状', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD79' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5519 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550121, 5519, '1', '圆柱锂电池', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519
UNION ALL SELECT 550122, 5519, '2', '软包异形电芯', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519
UNION ALL SELECT 550123, 5519, '3', '软包方形电芯', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519
UNION ALL SELECT 550124, 5519, '4', '戒指电池', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519
UNION ALL SELECT 550125, 5519, '5', '铝壳方形电芯', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519
UNION ALL SELECT 550126, 5519, '6', '铝壳异形电池', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519
UNION ALL SELECT 550127, 5519, '7', '镍氢电芯', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5519;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5520, c.id, '标称电压', 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD79' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5520 AND s.sort = 30);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550128, 5520, '1', '3.60V', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550129, 5520, '2', '3.70V', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550130, 5520, '3', '3.80V', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550131, 5520, '4', '3.85V', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550132, 5520, '5', '3.87V', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550133, 5520, '6', '3.89V', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550134, 5520, '7', '3.90V', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550135, 5520, '8', '3.91V', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550136, 5520, '9', '3.20V', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550137, 5520, 'A', '1.20V', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550138, 5520, 'B', '2.0V', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520
UNION ALL SELECT 550139, 5520, 'C', '12.0V', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5520;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5521, c.id, '品牌', 1, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD79' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5521 AND s.sort = 40);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550140, 5521, '1', '蓝电锂能', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550141, 5521, '2', 'EVE', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550142, 5521, '3', '三杰', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550143, 5521, '4', '鹏辉', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550144, 5521, '5', '东腾', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550145, 5521, '6', '景佳', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550146, 5521, '7', '千锂行', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550147, 5521, '8', '比克', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550148, 5521, '9', '振华', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550149, 5521, 'A', '嘉尚', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550150, 5521, 'B', '三星', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550151, 5521, 'C', '天鹏', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521
UNION ALL SELECT 550152, 5521, 'D', '松柏（广东）', 130, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5521;
-- 正极片 71-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5314, g.id, 'LD71', '正极片', '71-', 4, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5314/'), g.level + 1, 140, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD71');
UPDATE eng_material_category SET code_prefix = '71-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD71';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5522, c.id, '材料类型', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD71' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5522 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550153, 5522, '1', 'LCO(钴酸锂)', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5522
UNION ALL SELECT 550154, 5522, '2', 'NCM(镍钴锰三元)', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5522
UNION ALL SELECT 550155, 5522, '3', 'NCA(镍钴铝三元)', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5522
UNION ALL SELECT 550156, 5522, '4', 'NCM+LCO(钴酸锂+三元)', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5522
UNION ALL SELECT 550157, 5522, '5', 'LFP(磷酸铁锂)', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5522
UNION ALL SELECT 550158, 5522, '6', 'LMO(锰酸锂)', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5522;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5523, c.id, '材料电压', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD71' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5523 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550159, 5523, '1', '4.20V', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550160, 5523, '2', '4.35V', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550161, 5523, '3', '4.40V', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550162, 5523, '4', '4.45V', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550163, 5523, '5', '4.48V', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550164, 5523, '6', '4.50V', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550165, 5523, '7', '4.53V', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550166, 5523, '8', '4.55V', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523
UNION ALL SELECT 550167, 5523, '9', '4.58V', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5523;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5524, c.id, '品牌', 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD71' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5524 AND s.sort = 30);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550168, 5524, '1', 'HT(恒泰)', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5524
UNION ALL SELECT 550169, 5524, '2', 'XY(晓易)', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5524
UNION ALL SELECT 550170, 5524, '3', 'ZSY(众尚源)', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5524;
-- 负极片 72-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5315, g.id, 'LD72', '负极片', '72-', 4, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5315/'), g.level + 1, 150, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD72');
UPDATE eng_material_category SET code_prefix = '72-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD72';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5525, c.id, '材料种类', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD72' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5525 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550171, 5525, '1', 'Gr(石墨负极)', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5525
UNION ALL SELECT 550172, 5525, '2', 'Si(纯硅负极)', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5525
UNION ALL SELECT 550173, 5525, '3', 'Gr+Si(掺硅负极)', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5525;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5526, c.id, '性能类型', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD72' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5526 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550174, 5526, '1', '容量型', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5526
UNION ALL SELECT 550175, 5526, '2', '倍率型', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5526
UNION ALL SELECT 550176, 5526, '3', '低成本型', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5526;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5527, c.id, '品牌', 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD72' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5527 AND s.sort = 30);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550177, 5527, '1', 'HT(恒泰)', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5527
UNION ALL SELECT 550178, 5527, '2', 'XY(晓易)', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5527
UNION ALL SELECT 550179, 5527, '3', 'ZSY(众尚源)', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5527;
-- 极耳 73-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5316, g.id, 'LD73', '极耳', '73-', 4, 'RAW', 'PCS', 'BATCH', 1, CONCAT(g.path, '5316/'), g.level + 1, 160, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD73');
UPDATE eng_material_category SET code_prefix = '73-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD73';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5528, c.id, '极耳类型', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD73' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5528 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550180, 5528, '01', '连胶极耳', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550181, 5528, '02', '铝极耳', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550182, 5528, '03', '免转镍铝极耳(白胶)', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550183, 5528, '04', '免转镍铝极耳(黑胶)', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550184, 5528, '05', '铝转镍隐形极耳(白胶)', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550185, 5528, '06', '铝转镍隐形极耳(黑胶)', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550186, 5528, '07', '镍极耳', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528
UNION ALL SELECT 550187, 5528, '08', '铜镀镍极耳', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5528;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5529, c.id, '极耳宽度', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD73' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5529 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550188, 5529, '1', '1.0mm宽度', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5529
UNION ALL SELECT 550189, 5529, '2', '1.5mm宽度', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5529
UNION ALL SELECT 550190, 5529, '3', '2.0mm宽度', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5529
UNION ALL SELECT 550191, 5529, '4', '3.0mm宽度', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5529
UNION ALL SELECT 550192, 5529, '5', '4.0mm宽度', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5529
UNION ALL SELECT 550193, 5529, '6', '5.0mm宽度', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5529;
-- 隔膜 74-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5317, g.id, 'LD74', '隔膜', '74-', 4, 'RAW', 'M2', 'BATCH', 1, CONCAT(g.path, '5317/'), g.level + 1, 170, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD74');
UPDATE eng_material_category SET code_prefix = '74-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD74';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5530, c.id, '隔膜类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD74' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5530 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550194, 5530, '01', '陶瓷隔膜', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5530
UNION ALL SELECT 550195, 5530, '02', '凝胶隔膜', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5530
UNION ALL SELECT 550196, 5530, '03', 'PE基膜', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5530
UNION ALL SELECT 550197, 5530, '04', '凝胶陶瓷隔膜', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5530;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5531, c.id, '品牌', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD74' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5531 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550198, 5531, '1', '深圳市马斯唐', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5531
UNION ALL SELECT 550199, 5531, '2', '深圳市精利泰', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5531;
-- 电解液 75-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5318, g.id, 'LD75', '电解液', '75-', 5, 'RAW', 'KG', 'BATCH', 1, CONCAT(g.path, '5318/'), g.level + 1, 180, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD75');
UPDATE eng_material_category SET code_prefix = '75-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD75';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5532, c.id, '品牌', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD75' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5532 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550200, 5532, '01', '深圳华驰', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5532
UNION ALL SELECT 550201, 5532, '02', '四川研一', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5532
UNION ALL SELECT 550202, 5532, '03', '深圳新宙邦', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5532
UNION ALL SELECT 550203, 5532, '04', '山东化能', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5532
UNION ALL SELECT 550204, 5532, '05', '珠海赛纬', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5532;
-- 铝塑膜 76-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5319, g.id, 'LD76', '铝塑膜', '76-', 4, 'RAW', 'M2', 'BATCH', 1, CONCAT(g.path, '5319/'), g.level + 1, 190, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD76');
UPDATE eng_material_category SET code_prefix = '76-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD76';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5533, c.id, '铝塑膜颜色', 3, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD76' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5533 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550205, 5533, '001', '银色', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5533
UNION ALL SELECT 550206, 5533, '002', '黑色', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5533;
-- 包材 69-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5320, g.id, 'LD69', '包材', '69-', 5, 'PACKAGING', 'PCS', 'NONE', 1, CONCAT(g.path, '5320/'), g.level + 1, 200, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD69');
UPDATE eng_material_category SET code_prefix = '69-', code_seq_length = 5, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD69';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5534, c.id, '包材类别', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD69' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5534 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550207, 5534, '01', '外箱', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550208, 5534, '02', '平卡', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550209, 5534, '03', '内盒', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550210, 5534, '04', '珍珠棉', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550211, 5534, '05', '吸塑盒', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550212, 5534, '06', '防水PE袋', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550213, 5534, '07', '气泡袋', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550214, 5534, '08', '刀卡', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550215, 5534, '09', '防静电袋', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550216, 5534, '10', '珍珠棉盒', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550217, 5534, '11', '透明硅胶套管', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550218, 5534, '12', '护角', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534
UNION ALL SELECT 550219, 5534, '13', '卡板', 130, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5534;
-- 辅料 59-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5321, g.id, 'LD59', '辅料', '59-', 4, 'AUXILIARY', 'PCS', 'NONE', 0, CONCAT(g.path, '5321/'), g.level + 1, 210, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD59');
UPDATE eng_material_category SET code_prefix = '59-', code_seq_length = 4, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD59';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5535, c.id, '辅料名称', 3, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD59' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5535 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550220, 5535, '001', '磁环', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550221, 5535, '002', '干燥剂', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550222, 5535, '003', '墨水', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550223, 5535, '004', '喷码机清洗剂', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550224, 5535, '005', 'PET保护膜', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550225, 5535, '006', '锡线', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550226, 5535, '007', '封箱胶', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550227, 5535, '008', '打包膜', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550228, 5535, '009', '打包带', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550229, 5535, '010', '打包扣', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550230, 5535, '011', '喷码机溶剂', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535
UNION ALL SELECT 550231, 5535, '012', '固体蜡', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5535;
-- 工装模具及工程耗材 49-
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, code_seq_length, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, created_at, updated_at)
SELECT 5322, g.id, 'LD49', '工装模具及工程耗材', '49-', 3, 'AUXILIARY', 'PCS', 'NONE', 0, CONCAT(g.path, '5322/'), g.level + 1, 220, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eng_material_category g WHERE g.code = 'LDWL' AND NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LD49');
UPDATE eng_material_category SET code_prefix = '49-', code_seq_length = 3, updated_at = CURRENT_TIMESTAMP WHERE code = 'LD49';
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5536, c.id, '物品类型', 2, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD49' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5536 AND s.sort = 10);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550232, 5536, '01', '冲压模具', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5536
UNION ALL SELECT 550233, 5536, '02', '冲切模具', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5536
UNION ALL SELECT 550234, 5536, '03', '刀具', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5536
UNION ALL SELECT 550235, 5536, '04', '工装夹具', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5536
UNION ALL SELECT 550236, 5536, '05', '备品备件', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5536
UNION ALL SELECT 550237, 5536, '06', '低值易耗品', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5536;
INSERT INTO eng_code_segment (id, category_id, name, seg_length, sort, created_at, updated_at)
SELECT 5537, c.id, '工序', 2, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_material_category c
WHERE c.code = 'LD49' AND NOT EXISTS (SELECT 1 FROM eng_code_segment s WHERE s.category_id = c.id AND s.id <> 5537 AND s.sort = 20);
INSERT INTO eng_code_segment_value (id, segment_id, value_code, value_name, sort, status, created_at, updated_at)
SELECT 550238, 5537, '01', '制片', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550239, 5537, '02', '制袋', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550240, 5537, '03', '叠片', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550241, 5537, '04', '点焊', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550242, 5537, '05', '冲压', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550243, 5537, '06', '包装', 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550244, 5537, '07', '注液', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550245, 5537, '08', '化成', 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550246, 5537, '09', '二封', 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550247, 5537, '10', '分容', 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550248, 5537, '11', '复检', 110, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550249, 5537, '12', 'pack', 120, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550250, 5537, '13', '品质', 130, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550251, 5537, '14', '研发', 140, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537
UNION ALL SELECT 550252, 5537, '15', '行政', 150, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM eng_code_segment WHERE id = 5537;
