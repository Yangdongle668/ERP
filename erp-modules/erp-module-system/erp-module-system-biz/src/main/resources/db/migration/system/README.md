# 系统管理模块数据库迁移脚本

- 本目录的脚本只由系统管理模块执行，迁移历史表为 `flyway_history_system`，版本号与其他模块互不影响。
- 命名：`V<版本>__<说明>.sql`，如 `V1__init.sql`、`V2__add_xxx.sql`。
- 表名统一以本模块前缀开头（见 docs/architecture/后端架构设计.md 的表前缀约定）。
- 已合并到 main 的脚本禁止修改，只能新增脚本。
