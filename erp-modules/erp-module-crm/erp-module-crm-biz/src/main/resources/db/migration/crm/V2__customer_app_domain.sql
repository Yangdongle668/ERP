-- 客户编码规则（《编码规则管理制度》LD-QA-MS-001 5.1）：LD-应用领域字母-三位流水，如 LD-A-001
ALTER TABLE crm_customer ADD COLUMN app_domain VARCHAR(8) NULL COMMENT '应用领域（crm_app_domain：A 智能医疗 / B 智能穿戴 / C 消费电子 / D 低空设备 / E 物联网）';
