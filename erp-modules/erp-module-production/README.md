# 生产模块（production）

| 项 | 位置 |
|---|---|
| 需求文档 | [docs/requirements/09-生产/](../../docs/requirements/09-生产/) |
| 对外契约 | `erp-module-production-api`（包 `com.erp.module.production.api`） |
| 实现 | `erp-module-production-biz`（包 `com.erp.module.production`） |
| 数据库脚本 | `erp-module-production-biz/src/main/resources/db/migration/production/` |
| 前端 | `erp-ui/src/modules/production/` |
| API 路径前缀 | `/api/production/` |
| 错误码号段 | `1_009_xxx_xxx` |

并行开发规则见 [docs/并行开发指南.md](../../docs/并行开发指南.md)。
