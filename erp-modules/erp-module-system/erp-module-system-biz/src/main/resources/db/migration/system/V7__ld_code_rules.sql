-- 《编码规则管理制度》LD-QA-MS-001：客户编码 LD-领域-001、供应商编码 LD-S0001。
-- 规则表中已有的默认规则（管理员未修改过）改为新格式；新流水从 1 开始（前缀不同，计数独立）。
UPDATE sys_code_rule SET prefix = 'LD-{domain}-', seq_length = 3, updated_at = CURRENT_TIMESTAMP
WHERE biz_code = 'CRM_CUSTOMER' AND prefix = 'C' AND seq_length = 5 AND date_pattern = '';
UPDATE sys_code_rule SET prefix = 'LD-S', seq_length = 4, updated_at = CURRENT_TIMESTAMP
WHERE biz_code = 'PUR_SUPPLIER' AND prefix = 'V' AND seq_length = 5 AND date_pattern = '';
-- 供应商规则没有前缀变量，计数键为 ALL：改为新格式时同时从 1 重新计数（客户按领域前缀独立计数，无需处理）
DELETE FROM sys_code_seq WHERE biz_code = 'PUR_SUPPLIER' AND reset_key = 'ALL'
  AND EXISTS (SELECT 1 FROM sys_code_rule r WHERE r.biz_code = 'PUR_SUPPLIER' AND r.prefix = 'LD-S');
