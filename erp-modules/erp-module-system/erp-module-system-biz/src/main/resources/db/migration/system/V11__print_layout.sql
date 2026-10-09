-- 可视化打印模板（需求 01-09 第 9 节）：版式 JSON（单头条目、明细列、签名栏的勾选 / 顺序 / 宽度），模板内容由版式生成
ALTER TABLE sys_print_template ADD COLUMN layout MEDIUMTEXT NULL COMMENT '可视化版式 JSON，为空表示代码模板';
