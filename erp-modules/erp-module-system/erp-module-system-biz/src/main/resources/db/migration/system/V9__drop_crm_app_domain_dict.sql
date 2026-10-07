-- 应用领域改为 CRM 模块自己的主数据（CRM / 应用领域，表 crm_app_domain），删除原字典 crm_app_domain
DELETE FROM sys_dict_item WHERE type_code = 'crm_app_domain';
DELETE FROM sys_dict_type WHERE code = 'crm_app_domain';
