# 系统管理模块（system）

| 项 | 位置 |
|---|---|
| 需求文档 | [docs/requirements/01-系统管理.md](../../docs/requirements/01-系统管理.md) |
| 对外契约 | `erp-module-system-api`（包 `com.erp.module.system.api`） |
| 实现 | `erp-module-system-biz`（包 `com.erp.module.system`） |
| 数据库脚本 | `erp-module-system-biz/src/main/resources/db/migration/system/` |
| 前端 | `erp-ui/src/modules/system/` |
| API 路径前缀 | `/api/system/` |
| 错误码号段 | `1_001_xxx_xxx` |

并行开发规则见 [docs/并行开发指南.md](../../docs/并行开发指南.md)。
