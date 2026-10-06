-- 计量单位：瓶、千米（物料导入用到）；已有同代码时不重复新增
INSERT INTO sys_uom (id, code, name, name_en, category, qty_precision, sort, is_builtin, status, created_at, updated_at)
SELECT 221, 'BTL', '瓶', 'bottle', 'COUNT', 0, 95, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_uom WHERE code = 'BTL');
INSERT INTO sys_uom (id, code, name, name_en, category, qty_precision, sort, is_builtin, status, created_at, updated_at)
SELECT 222, 'KM', '千米', 'km', 'LENGTH', 4, 125, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_uom WHERE code = 'KM');
INSERT INTO sys_uom_conversion (id, from_uom, to_uom, rate, created_at, updated_at)
SELECT 260, 'KM', 'M', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_uom_conversion WHERE from_uom = 'KM' AND to_uom = 'M');

-- 客户编码流水号改为 4 位，与现有客户档案（LD-A-0001）一致
UPDATE sys_code_rule SET seq_length = 4, updated_at = CURRENT_TIMESTAMP
WHERE biz_code = 'CRM_CUSTOMER' AND prefix = 'LD-{domain}-' AND seq_length = 3;
