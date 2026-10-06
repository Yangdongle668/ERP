# ERP

面向制造企业（FPC / 电子组装类）的 ERP 系统，覆盖 **CRM → 销售 → 研发工程 → PMC → 资材 → 仓库 → 生产 → 品质 → 出货 → 财务 → BI/AI** 的完整业务链。

- **后端**：Java 21、Spring Boot 3.3、MyBatis-Plus 3.5、Spring Security（JWT）、Flyway（按模块迁移）、SpringDoc；模块化单体，ArchUnit 强制模块边界
- **前端**：Vue 3.5、Vite 5、TypeScript、Element Plus、Pinia；自建设计系统（`--erp-*` token、Lucide 图标、页面模板 T1～T8）
- **数据库**：MySQL 8（生产）/ H2 MySQL 模式（本地体验与测试）
- **AI 分析**：通过 OpenAI 兼容接口接入 DeepSeek、通义千问等大模型，只能经指标库取数（受权限与数据范围约束）

## 目录

- [功能模块](#功能模块)
- [一键部署（Docker）](#一键部署docker)
- [更新与回滚](#更新与回滚)
- [本地开发](#本地开发)
- [配置项](#配置项)
- [项目结构与架构](#项目结构与架构)
- [文档](#文档)

## 功能模块

| 模块 | 编码 | 主要功能 |
|---|---|---|
| 系统管理 | `system` | 组织、用户、角色与数据权限、字典、单位、币别汇率、付款条件、系统参数、编码规则、审批流、定时任务与任务中心、附件、打印模板、操作日志 |
| 工作台 | `workbench` | 首页看板卡片、待办（审批 / 任务）、消息、公告、预警 |
| CRM | `crm` | 客户档案、联系人与地址、客户料号、信用额度、跟进记录、商机 |
| 销售 | `sales` | RFQ、报价单、销售订单（变更、关闭）、退货、销售预测、价格表、回款跟踪、订单交期与出货回写、销售报表 |
| 研发工程 | `engineering` | 物料与类别、BOM（版本、审批、展开 / 反查 / 比较）、工艺路线、ECN |
| PMC | `pmc` | 需求池与交期回复、MPS、MRP 运算与建议、排产与产能、缺料分析、交期预警、出货计划 |
| 资材 | `purchase` | 供应商、价格、请购、采购订单、到货与退货、委外、询比价、对账、供应商评估 |
| 仓库 | `inventory` | 仓库与库位、入库 / 出库 / 调拨 / 盘点、批次序列号、库存期间与期初、库存报表与预警 |
| 生产 | `production` | 生产订单、领料 / 退料、报工、完工入库、不良与良率、生产报表 |
| 品质 | `quality` | IQC / IPQC / FQC / OQC 检验、NCR、客诉、CAPA、品质报表 |
| 出货 | `shipping` | 出货通知、拣货、装箱与箱唛、OQC、出货单、装箱单 / 发票 / 报关资料、物流 |
| 财务 | `finance` | 应收应付、收付款与核销、发票、凭证、成本核算、月结、损益与分析报表 |
| BI / AI | `bi` | 指标库与汇总数据层、经营驾驶舱、销售 / 采购 / 库存 / 生产 / 品质 / 财务专题分析、AI 问数、异常解读、经营周报 |
| 实时汇率 | `fx` | 每 15 分钟获取中国银行现汇买入价（美元、欧元、日元、韩元、澳元对人民币）；美元保存 3 年并计算日 / 月平均、自动写入系统汇率表，其他币别仅实时报价 |
| 固定资产 | `asset` | 资产台账、资产编码 LD1-PD-CPJ-264-001（《编码规则管理制度》）、闲置 / 送修 / 报废 |
| 系统备份 | `backup` | 超级管理员全量备份（数据库 + 附件）、下载 / 上传备份、一键恢复、自动备份与保留份数 |

每个模块的需求与实现说明见 [docs/requirements/](docs/requirements/)（各模块目录下的 `README.md`）。

## 一键部署（Docker）

需要一台安装了 **Docker 20+ 与 Docker Compose v2** 的 Linux 服务器（建议 4 核 8G 以上，首次构建需要能访问 Maven / npm 仓库）。

```bash
git clone https://github.com/Yangdongle668/ERP.git
cd ERP
./deploy.sh            # 国内服务器建议：./deploy.sh --cn（使用阿里云 Maven、npmmirror 镜像源）
```

脚本会：

1. 检查 Docker 环境；
2. 首次运行时由 `.env.example` 生成 `.env`，并随机生成数据库密码、root 密码、JWT 密钥与敏感参数加密密钥（`.env` 权限 600，请妥善保管）；
3. 构建后端与前端镜像，启动 3 个容器：`mysql`（MySQL 8）、`erp-server`（Spring Boot）、`erp-ui`（Nginx 静态页面 + `/api` 反向代理）；
4. 等待后端健康检查通过（首次启动自动执行全部模块的建表脚本）。

完成后访问 `http://服务器IP:1493`（默认端口 1493，可在 `.env` 的 `ERP_HTTP_PORT` 修改；已部署的服务器沿用原 `.env` 中的端口），初始账号 **`admin / admin123`**，首次登录会强制修改密码。

```
浏览器 ──80──▶ erp-ui（Nginx：静态页面，/api → erp-server:8080）
                  └──▶ erp-server（Spring Boot，数据卷 erp-data：附件）
                          └──▶ mysql（MySQL 8，数据卷 mysql-data）
```

**小内存服务器（2 核 2G）**：`deploy.sh` 检测到内存 ≤ 3.5GB 时自动使用低内存配置（JVM 堆 640MB、MySQL 缓冲池 128MB 并关闭 performance_schema，运行时合计约 0.8GB），镜像逐个构建；前端镜像构建只打包、不做类型检查（类型检查在 CI 中执行），打包约需 1GB 内存。建议首次部署使用 `sudo ./deploy.sh --cn --swap`，自动创建 1536MB 交换文件。配置写在 `.env` 的 `ERP_MEMORY_PROFILE`、`JAVA_OPTS`、`MYSQL_*` 中，可手工调整。

**HTTPS、监控、备份**：

```bash
./deploy.sh --https            # .env 设置 ERP_DOMAIN 后，Caddy 自动申请并续期证书（需公网 80/443）
./deploy.sh --monitoring       # Prometheus + Alertmanager + Grafana（仅本机端口，SSH 隧道访问；约 300MB 内存）
./backup.sh --install-cron     # 每天 02:30 备份数据库与附件（BACKUP_KEEP 保留份数，BACKUP_REMOTE 异地复制）
./backup.sh --restore-db 备份文件   # 恢复（也支持 --restore-files 恢复附件）
```

监控指标在 `/actuator/prometheus`，需请求头 `Authorization: Bearer <ERP_METRICS_TOKEN>`（deploy.sh 自动生成；未配置令牌时端点关闭）。告警规则见 `deploy/monitoring/alerts.yml`，设置 `ALERT_WEBHOOK_URL` 后通过 Webhook 推送。

**附件存储**：默认本地数据卷；`ERP_FILE_STORAGE=s3` 并配置 `ERP_S3_ENDPOINT / ERP_S3_BUCKET / ERP_S3_ACCESS_KEY / ERP_S3_SECRET_KEY`（MinIO、阿里云 OSS、腾讯云 COS、AWS S3）后新附件写入对象存储；历史本地附件仍可读取。OSS、COS 需设置 `ERP_S3_PATH_STYLE=false`。

常用命令：

```bash
docker compose ps                    # 容器状态
docker compose logs -f erp-server    # 后端日志
docker compose restart erp-server    # 重启后端（修改 .env 后用 docker compose up -d 使配置生效）
docker compose down                  # 停止（数据保留在数据卷中）
```

> **邮件通知**：在项目根目录新建 `docker-compose.override.yml`（不会提交到 Git，`docker compose` 自动合并）：
>
> ```yaml
> services:
>   erp-server:
>     environment:
>       SPRING_MAIL_HOST: smtp.example.com
>       SPRING_MAIL_PORT: "465"
>       SPRING_MAIL_USERNAME: erp@example.com
>       SPRING_MAIL_PASSWORD: "******"
>       SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE: "true"
> ```
>
> **HTTPS**：建议在前面再加一层带证书的反向代理（如宿主机 Nginx / Caddy），转发到 `ERP_HTTP_PORT`。
> **企业代理 / 内网仓库**：把 CA 证书（`*.crt`）放入 `deploy/certs/`，构建时会自动信任。

## 更新与回滚

```bash
./update.sh              # 拉取最新代码 → 备份数据库 → 构建新镜像 → 重启 → 健康检查
./update.sh --no-pull    # 不拉代码（已手动切换到指定版本）
./update.sh --rollback   # 回滚到上一次更新前的镜像
```

- 每次更新前自动备份数据库到 `backups/erp-时间-版本.sql.gz`，默认保留最近 10 份（`.env` 的 `BACKUP_KEEP`）。
- 数据库结构由 Flyway 脚本在启动时自动升级；脚本只增不改，已执行的脚本不会重复执行。
- 新版本启动失败时脚本会输出日志并提示回滚。回滚只替换镜像；如果新版本已经升级了数据库结构，需要同时恢复更新前的备份：

```bash
gunzip -c backups/erp-XXXX.sql.gz | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

## 本地开发

```bash
# 后端：编译 + 全部测试 + 模块边界检查
mvn -B verify

# 无需 MySQL 启动（H2 文件库，数据在 ./data）
mvn -B -DskipTests package
ERP_PROFILE=h2 java -jar erp-server/target/erp-server.jar

# 前端开发服务器 http://localhost:5173（/api 代理到 8080）
cd erp-ui && npm install && npm run dev

# 前端类型检查 + 设计系统检查 + 构建
cd erp-ui && npm run build

# 前端 E2E（Playwright）：先按上面启动后端（H2），再执行；前端开发服务器由测试自动启动
# 首次需安装浏览器：npx playwright install chromium
# 用例：登录 / 退出、逐个打开全部菜单页面（无脚本错误、无 5xx）、订单 → 出货 → 应收主线
cd erp-ui && npm run e2e
```

接口文档：<http://localhost:8080/swagger-ui.html>（`h2` profile 默认开启；`mysql` profile 默认关闭，设置 `ERP_API_DOCS_ENABLED=true` 开启）；健康检查：`/actuator/health`。

## 配置项

后端通过环境变量配置（Docker 部署时写在 `.env`）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `ERP_PROFILE` | `mysql` | `mysql` / `h2` |
| `ERP_DB_HOST` / `ERP_DB_PORT` / `ERP_DB_NAME` | `localhost` / `3306` / `erp` | MySQL 连接 |
| `ERP_DB_USER` / `ERP_DB_PASSWORD` | `erp` / `erp` | 数据库账号 |
| `ERP_JWT_SECRET` | 开发用默认值 | **生产必须设置**，至少 32 位随机字符串（`mysql` profile 下仍为默认值时拒绝启动） |
| `ERP_SECRET_KEY` | 空（由 JWT 密钥派生） | 敏感参数（如 AI API Key）的加密密钥；设置后不要再修改，否则已保存的密文无法解密 |
| `ERP_SERVER_PORT` | `8080` | 后端端口 |
| `ERP_FILE_STORAGE` / `ERP_FILE_PATH` | `local` / `./data/files` | 附件存储（`local` / `s3`） |
| `ERP_S3_ENDPOINT` / `ERP_S3_REGION` / `ERP_S3_BUCKET` / `ERP_S3_ACCESS_KEY` / `ERP_S3_SECRET_KEY` / `ERP_S3_PATH_STYLE` / `ERP_S3_PREFIX` | 空 / 空 / 空 / 空 / 空 / `true` / `erp/` | 对象存储（`ERP_FILE_STORAGE=s3` 时） |
| `ERP_METRICS_TOKEN` | 空 | Prometheus 指标端点令牌，为空时端点关闭 |
| `ERP_API_DOCS_ENABLED` | `false`（`mysql` profile） | 是否开放接口文档 `/v3/api-docs`、`/swagger-ui.html` |
| `ERP_AI_API_KEY` / `ERP_AI_BASE_URL` | 空 | AI 分析的 API Key / 接口地址（也可在“系统参数”页面配置） |
| `SPRING_MAIL_HOST` 等 | 空 | 邮件通知（工作台参数 `wb.email.enabled` 开启后生效），Docker 部署时写在 `docker-compose.override.yml` |

业务规则类参数（库存是否允许负数、审批、AI 供应商与模型、呆滞天数等）在系统内 **系统管理 → 系统参数** 中维护。

### AI 分析

在系统参数（BI/AI 模块，AI 分组）中设置：`ai.enabled=是`，`ai.provider` 选 DeepSeek / 通义千问 / 其他 OpenAI 兼容接口，填写 `ai.api-key`；接口地址与模型为空时按供应商默认（`https://api.deepseek.com` + `deepseek-chat`，`https://dashscope.aliyuncs.com/compatible-mode/v1` + `qwen-plus`）。模型只能通过 `bi_query` 工具查询当前用户有权限的指标，成本、毛利等敏感数值默认脱敏后才发送给模型。

## 项目结构与架构

```
erp-framework/
  erp-common/               纯 Java 公共类型（结果包装、异常、错误码、分页、Decimals 等）
  erp-framework-core/       Spring 基础设施（安全与 JWT、数据权限、MyBatis-Plus、事件、按模块 Flyway 迁移、Excel、操作日志）
erp-modules/
  erp-module-<code>/
    erp-module-<code>-api/  对外契约：接口、DTO（record）、枚举、事件、错误码
    erp-module-<code>-biz/  实现：Controller、Service、DAL、迁移脚本 db/migration/<code>/
erp-server/                 启动模块、配置、集成测试（H2）、架构测试（ArchUnit）
erp-ui/                     前端；页面按模块放在 src/modules/<code>/，在 index.ts 注册菜单
deploy/                     Docker 构建文件与 Nginx 配置
docs/                       需求、架构、UI 规范、并行开发指南
```

关键约定（详见 [CLAUDE.md](CLAUDE.md) 与 [后端架构设计](docs/architecture/后端架构设计.md)）：

- 模块 `-biz` 只能依赖其他模块的 `-api`，不能访问其他模块的表（ArchUnit 在 `mvn verify` 中检查）；跨模块协作通过 API 接口与领域事件。
- 表名带模块前缀；数据库变更只能新增 Flyway 脚本，SQL 同时兼容 MySQL 8 与 H2。
- 实体继承 `BaseDO` / `BaseDocDO`（雪花 ID、审计字段、逻辑删除、乐观锁）；单据状态通过状态机变更。
- 数量金额统一 `BigDecimal`；业务错误抛 `BizException`，错误码按模块号段。
- 接口权限 `@PreAuthorize("@ss.has('模块:资源:操作')")`，数据权限（本人 / 部门 / 公司 / 全部）通过 Mapper 上的 `@DataScope` 自动过滤。

CI（`.github/workflows/ci.yml`）：后端 `mvn -B verify`（H2）；同一套集成测试在 MySQL 8 服务容器上再跑一遍；前端 `npm ci && npm run build`。实体列名、迁移脚本列名、SQL 别名不能使用 MySQL 保留字（`SqlReservedWordTest` 检查）。

## 文档

- [文档索引](docs/README.md)
- [总体需求与通用规范](docs/requirements/00-总体需求与通用规范.md)、各模块需求 [docs/requirements/](docs/requirements/)
- [后端架构设计](docs/architecture/后端架构设计.md)
- [UI 设计规范](docs/ui/UI设计规范.md)
- [并行开发指南](docs/并行开发指南.md)
- [待完成与优化清单](docs/待完成与优化清单.md)
