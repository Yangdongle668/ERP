# BI/AI模块（bi）

| 项 | 位置 |
|---|---|
| 需求文档 | [docs/requirements/13-BI与AI/](../../docs/requirements/13-BI与AI/) |
| 对外契约 | `erp-module-bi-api`（包 `com.erp.module.bi.api`） |
| 实现 | `erp-module-bi-biz`（包 `com.erp.module.bi`） |
| 数据库脚本 | `erp-module-bi-biz/src/main/resources/db/migration/bi/` |
| 前端 | `erp-ui/src/modules/bi/` |
| API 路径前缀 | `/api/bi/` |
| 错误码号段 | `1_013_xxx_xxx` |
| 数据来源扩展点 | `BiFactProvider`（各业务模块在自己的 biz 中实现，BI 只合并事实、不访问业务表） |
| 大模型适配器 | `LlmAdapter`（默认 `OpenAiCompatibleLlmAdapter`：DeepSeek / 通义千问 / 其他 OpenAI 兼容接口） |

并行开发规则见 [docs/并行开发指南.md](../../docs/并行开发指南.md)。
