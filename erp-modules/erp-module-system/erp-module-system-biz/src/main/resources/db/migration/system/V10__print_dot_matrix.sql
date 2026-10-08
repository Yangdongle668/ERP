-- 针式多联打印（需求 01-系统管理/09 第 4 节）：每页固定行数分页、联次说明、打印方式；内置模板按变体区分（标准 / 针式）
ALTER TABLE sys_print_template ADD COLUMN rows_per_page INT NULL COMMENT '每页明细行数：设置后按固定行数分页并补空行，空为浏览器自动分页';
ALTER TABLE sys_print_template ADD COLUMN copies_note VARCHAR(256) NULL COMMENT '联次说明，| 分隔，如 ①白 存根|②红 财务|③黄 仓库';
ALTER TABLE sys_print_template ADD COLUMN copy_mode VARCHAR(16) NOT NULL DEFAULT 'CARBON' COMMENT 'CARBON 多联纸一次打印 / REPEAT 普通纸逐联打印';
ALTER TABLE sys_print_template ADD COLUMN builtin_key VARCHAR(32) NULL COMMENT '内置模板变体：zh-CN / en / zh-CN-dot';
UPDATE sys_print_template SET builtin_key = language WHERE is_builtin = 1;
