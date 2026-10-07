-- 物料类别分组调整（《物料编码手册》20260618 A02）：
--   一级：电池成品 99- / 电芯物料（79、71～76） / PACK物料（91～94、81～87） / 包材 69- / 辅料 59- / 工装模具及工程耗材 49-
--   删除分组「蓝电物料」（LDWL）及 V2 预置的示例类别（原材料、FPC、电子料、包材、辅料、半成品、成品；已有物料或下级的保留）。
-- 类别编码、编码前缀、编码段不变，物料编码不受影响。

-- ---------- 1) 分组 ----------
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, remark, created_at, updated_at)
SELECT 5330, NULL, 'LDDX', '电芯物料', 'LDDX', 'RAW', 'PCS', 'BATCH', 1, '/5330/', 1, 20, 'ENABLED', '电芯及电芯原材料（7x）', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LDDX');
INSERT INTO eng_material_category (id, parent_id, code, name, code_prefix, default_material_type, default_base_uom, default_tracking,
                                   default_iqc_required, path, level, sort, status, remark, created_at, updated_at)
SELECT 5331, NULL, 'LDPK', 'PACK物料', 'LDPK', 'RAW', 'PCS', 'BATCH', 1, '/5331/', 1, 30, 'ENABLED', 'PACK 组装用料（9x、8x）', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM eng_material_category WHERE code = 'LDPK');

-- ---------- 2) 下级挂到新分组 ----------
UPDATE eng_material_category
   SET parent_id = (SELECT id FROM (SELECT MAX(id) AS id FROM eng_material_category WHERE code = 'LDDX') g), updated_at = CURRENT_TIMESTAMP
 WHERE code IN ('LD79', 'LD71', 'LD72', 'LD73', 'LD74', 'LD75', 'LD76');
UPDATE eng_material_category
   SET parent_id = (SELECT id FROM (SELECT MAX(id) AS id FROM eng_material_category WHERE code = 'LDPK') g), updated_at = CURRENT_TIMESTAMP
 WHERE code IN ('LD91', 'LD92', 'LD93', 'LD94', 'LD81', 'LD82', 'LD83', 'LD84', 'LD85', 'LD86', 'LD87');
UPDATE eng_material_category SET path = CONCAT('/', parent_id, '/', id, '/'), level = 2
 WHERE code IN ('LD79', 'LD71', 'LD72', 'LD73', 'LD74', 'LD75', 'LD76',
                'LD91', 'LD92', 'LD93', 'LD94', 'LD81', 'LD82', 'LD83', 'LD84', 'LD85', 'LD86', 'LD87');
-- 电芯物料内：电芯在前，正极片～铝塑膜按编码
UPDATE eng_material_category SET sort = 10 WHERE code = 'LD79';
UPDATE eng_material_category SET sort = 20 WHERE code = 'LD71';
UPDATE eng_material_category SET sort = 30 WHERE code = 'LD72';
UPDATE eng_material_category SET sort = 40 WHERE code = 'LD73';
UPDATE eng_material_category SET sort = 50 WHERE code = 'LD74';
UPDATE eng_material_category SET sort = 60 WHERE code = 'LD75';
UPDATE eng_material_category SET sort = 70 WHERE code = 'LD76';
-- PACK物料内：保护板、线材、端子线、连接片，再标签～塑胶件
UPDATE eng_material_category SET sort = 10 WHERE code = 'LD91';
UPDATE eng_material_category SET sort = 20 WHERE code = 'LD92';
UPDATE eng_material_category SET sort = 30 WHERE code = 'LD93';
UPDATE eng_material_category SET sort = 40 WHERE code = 'LD94';
UPDATE eng_material_category SET sort = 50 WHERE code = 'LD81';
UPDATE eng_material_category SET sort = 60 WHERE code = 'LD82';
UPDATE eng_material_category SET sort = 70 WHERE code = 'LD83';
UPDATE eng_material_category SET sort = 80 WHERE code = 'LD84';
UPDATE eng_material_category SET sort = 90 WHERE code = 'LD85';
UPDATE eng_material_category SET sort = 100 WHERE code = 'LD86';
UPDATE eng_material_category SET sort = 110 WHERE code = 'LD87';

-- ---------- 3) 单独一级的类别 ----------
UPDATE eng_material_category SET parent_id = NULL, path = CONCAT('/', id, '/'), level = 1, updated_at = CURRENT_TIMESTAMP
 WHERE code IN ('LD99', 'LD69', 'LD59', 'LD49');
UPDATE eng_material_category SET sort = 10 WHERE code = 'LD99';
UPDATE eng_material_category SET sort = 40 WHERE code = 'LD69';
UPDATE eng_material_category SET sort = 50 WHERE code = 'LD59';
UPDATE eng_material_category SET sort = 60 WHERE code = 'LD49';

-- 「蓝电物料」下自建的其他类别改为一级（子查询包一层聚合，MySQL 才允许引用被更新的表）
UPDATE eng_material_category SET parent_id = NULL, path = CONCAT('/', id, '/'), level = 1, updated_at = CURRENT_TIMESTAMP
 WHERE parent_id = (SELECT id FROM (SELECT MAX(id) AS id FROM eng_material_category WHERE code = 'LDWL') g);

-- ---------- 4) 删除旧分组和示例类别（没有物料、没有下级的） ----------
DELETE FROM eng_material_category
 WHERE (code = 'LDWL' OR id IN (501, 502, 503, 504, 505, 506, 507))
   AND id NOT IN (SELECT parent_id FROM (SELECT DISTINCT parent_id FROM eng_material_category WHERE parent_id IS NOT NULL) children)
   AND id NOT IN (SELECT category_id FROM (SELECT DISTINCT category_id FROM eng_material WHERE category_id IS NOT NULL) used);
