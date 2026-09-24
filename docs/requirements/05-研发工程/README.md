# 05 研发工程（总览）

## 1. 模块定位

研发工程负责制造企业的**产品数据**：物料、BOM、工作中心、工艺路线，以及工程业务：ECN、研发项目、样品、工装、认证。几乎所有模块都引用物料和 BOM，本模块的数据准确性和变更控制决定全系统的数据质量。

## 2. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 物料类别 | [01-物料类别](01-物料类别.md) | 物料类别 | T6 树形表格 | `/engineering/category` | `eng:category:query` | P0 |
| 物料 | [02-物料](02-物料.md) | 物料列表 | T1 | `/engineering/material` | `eng:material:query` | P0 |
| | | 新建/编辑物料 | T3 | `/engineering/material/new`、`/:id/edit` | create / update | P0 |
| | | 物料详情 | T5（无审批） | `/engineering/material/:id` | query | P0 |
| BOM | [03-BOM](03-BOM.md) | BOM 列表 | T1 | `/engineering/bom` | `eng:bom:query` | P0 |
| | | BOM 编辑 | T4 | `/engineering/bom/new`、`/:id/edit` | create / update | P0 |
| | | BOM 详情（单层、多级展开、反查、比较） | T5 | `/engineering/bom/:id` | query | P0 |
| 工作中心 | [04-工作中心与工艺路线](04-工作中心与工艺路线.md) | 工作中心 | T1 + T2 | `/engineering/work-center` | `eng:work-center:query` | P1 |
| 工艺路线 | 同上 | 工艺路线列表 / 编辑 / 详情 | T1 / T4 / T5 | `/engineering/routing` | `eng:routing:query` | P1 |
| ECN | [05-ECN](05-ECN.md) | ECN 列表 / 编辑 / 详情 | T1 / T4 / T5 | `/engineering/ecn` | `eng:ecn:query` | P1 |
| 研发项目 | [06-研发项目](06-研发项目.md) | 项目列表 / 编辑 / 详情 | T1 / T3 / T5 | `/engineering/project` | `eng:project:query` | P1 |
| 样品 | [07-样品](07-样品.md) | 样品单列表 / 编辑 / 详情 | T1 / T4 / T5 | `/engineering/sample` | `eng:sample:query` | P1 |
| 工装 | [08-工装](08-工装.md) | 工装台账 / 详情 | T1 / T5 | `/engineering/tooling` | `eng:tooling:query` | P1 |
| 认证 | [09-认证](09-认证.md) | 认证证书 | T1 + T2 | `/engineering/cert` | `eng:cert:query` | P1 |

菜单顺序：项目、物料类别、物料、BOM、工作中心、工艺路线、ECN、样品、工装、认证。

## 3. 用户角色

| 角色 | 使用的功能 |
|---|---|
| 研发工程师 | 物料申请/建档、BOM、样品、ECN 发起、项目任务 |
| 工艺工程师 | 工作中心、工艺路线、工装 |
| 文控 / 数据管理员 | 物料审核启用、BOM 审核、编码管理 |
| 工程主管 | BOM 与 ECN 审批、项目 |
| 认证工程师 | 认证证书 |

## 4. 表前缀与数据表

表前缀 `eng_`：eng_material_category、eng_material、eng_material_uom、eng_bom、eng_bom_line、eng_bom_substitute、eng_work_center、eng_routing、eng_routing_step、eng_ecn、eng_ecn_line、eng_ecn_impact、eng_ecn_task、eng_project、eng_project_member、eng_project_task、eng_sample、eng_tooling、eng_tooling_material、eng_tooling_record、eng_certification、eng_certification_material。

## 5. 编码规则（声明 `CodeRuleDefinition`）

ENG_MATERIAL、ENG_PROJECT、ENG_ECN、ENG_SAMPLE、ENG_TOOLING（格式见 01-系统管理 / 05-编码规则 第 7 节）。

**对现有代码的调整**：骨架中物料编码规则的业务编码为 `MATERIAL`、固定前缀 `M`，需改为 `ENG_MATERIAL`、前缀 `{categoryPrefix}`（见 02-物料 R02）。

## 6. 内置字典（声明 `DictDefinition`）

| 类型编码 | 名称 | 内置项 |
|---|---|---|
| eng_operation | 工序 | 无内置项，示例：SMT 贴片、DIP 插件、焊接、组装、测试、包装 |
| eng_ecn_type | ECN 类型 | DESIGN 设计变更、PROCESS 工艺变更、SUBSTITUTE 物料替代、DOCUMENT 文件变更 |
| eng_ecn_reason | ECN 原因 | CUSTOMER 客户要求、COST 降本、QUALITY 质量改善、SUPPLY 供应问题、DESIGN 设计优化 |
| eng_project_stage | 项目阶段 | CONCEPT 概念、DESIGN 设计、EVT 工程验证、DVT 设计验证、PVT 生产验证、MP 量产 |
| eng_tooling_type | 工装类型 | MOLD 模具、JIG 治具、FIXTURE 夹具、GAUGE 检具、STENCIL 钢网 |
| eng_cert_type | 认证类型 | CE、UL、FCC、CCC、ROHS、REACH、KC、PSE、ISO9001、OTHER |

## 7. 可审批单据（声明 `ApprovalBizDefinition`）

| 单据类型 | 名称 | 条件字段 | 用户字段 |
|---|---|---|---|
| ENG_MATERIAL | 物料启用 | materialType 物料类型（枚举）、categoryId 物料类别（字符串） | createdBy 申请人 |
| ENG_BOM | BOM | parentMaterialType 父件类型、lineCount 行数（数字） | — |
| ENG_ECN | ECN | ecnType（字典）、urgency 紧急程度（枚举） | — |
| ENG_SAMPLE | 样品单 | sampleType、qty（数字） | — |

## 8. 可打印单据（声明 `PrintBizDefinition`）

ENG_BOM（BOM 清单，中文）、ENG_ECN（ECN 通知单，中文）、ENG_SAMPLE（样品单，中文）。

## 9. 系统参数（声明 `ParamDefinition`）

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| eng.material.enable-approval | 物料 | 物料启用需要审批 | BOOL | 否 | 是：启用操作走审批流 ENG_MATERIAL；否：有权限者直接启用 |
| eng.material.duplicate-check | 物料 | 物料查重方式 | ENUM | WARN | OFF 不查重 / WARN 提示但允许保存 / BLOCK 禁止保存 |
| eng.bom.max-level | BOM | BOM 最大层数 | INT(5～30) | 20 | |
| eng.bom.allow-draft-component | BOM | BOM 允许使用草稿物料 | BOOL | 是 | 仅草稿 BOM 可用，审核时仍要求全部启用 |
| eng.tooling.life-warn-pct | 工装 | 工装寿命预警比例 | DECIMAL(50～99) | 90 | |
| eng.cert.remind-days | 认证 | 证书到期提醒天数 | STRING | 90,30,7 | 逗号分隔 |

## 10. 对其他模块提供的 API（engineering-api）

| 接口 | 方法 | 使用方 |
|---|---|---|
| `MaterialApi` | `getMaterial`、`getMaterials`、`validateUsable`（已实现）；`getPlanAttr(id)`、`getPurchaseAttr(id)`、`getStockAttr(id)`、`getQualityAttr(id)`、`convertToBase(materialId, qty, uom)`、`search(keyword, filter)` | 全部模块 |
| `MaterialCategoryApi` | `get`、`getDescendantIds` | 仓库（类别默认仓）、BI |
| `BomApi` | `getDefaultBom(materialId, date)`、`explode(materialId, qty, date, levels)`（多级展开，虚拟件透过）、`whereUsed(componentId)`、`getLowLevelCodes()` | PMC、生产、销售（报价成本）、财务 |
| `RoutingApi` | `getDefaultRouting(materialId)` | PMC、生产、财务 |
| `WorkCenterApi` | `get`、`list` | PMC、生产 |
| `ToolingApi` | `addUsage(toolingId, count, sourceDoc)`、`validateUsable(toolingId)` | 生产 |
| `CertificationApi` | `listValid(materialId)` | 销售、出货 |

**发布的事件**：`MaterialStatusChangedEvent`（已实现）、`MaterialChangedEvent`（计划/采购/库存属性变化）、`BomApprovedEvent`、`BomDefaultChangedEvent`、`EcnApprovedEvent`、`EcnEffectiveEvent`、`CertificationExpiringEvent`、`ToolingLifeWarningEvent`。

**监听的事件**：生产 `WorkReportApprovedEvent`（累加工装使用次数）、生产 `ProductionOrderCompletedEvent`（样品单完工）。

**调用的其他模块 API**：仓库 `InventoryApi.getAvailableQty`（ECN 影响分析）、资材 `PurchaseQueryApi.getOpenQty`、生产 `ProductionQueryApi.getOpenOrdersByComponent`、生产 `ProductionOrderApi.createSampleOrder`（样品）。

## 11. 权限点汇总

| 分组 | 权限点 |
|---|---|
| 物料类别 | `eng:category:query`（菜单）、`create`、`update`、`delete` |
| 物料 | `eng:material:query`（菜单）、`create`、`update`、`enable`、`disable`、`delete`、`import`、`export`；字段 `eng:material:cost`（标准成本） |
| BOM | `eng:bom:query`（菜单）、`create`、`update`、`delete`、`submit`、`approve`（审批流关闭时直接审核用）、`unapprove`、`disable`、`set-default`、`import`、`export`、`print`；字段 `eng:bom:cost` |
| 工作中心 | `eng:work-center:query`、`create`、`update`、`delete`；字段 `eng:work-center:rate`（费率） |
| 工艺路线 | `eng:routing:query`、`create`、`update`、`delete`、`approve`、`set-default` |
| ECN | `eng:ecn:query`、`create`、`update`、`delete`、`submit`、`approve`、`effect`（切换生效）、`close`、`void`、`print` |
| 项目 | `eng:project:query`、`create`、`update`、`delete`、`close` |
| 样品 | `eng:sample:query`、`create`、`update`、`delete`、`submit`、`approve`、`ship`、`feedback`、`close`、`void`、`print` |
| 工装 | `eng:tooling:query`、`create`、`update`、`delete`、`record`（借还/保养/维修/报废登记） |
| 认证 | `eng:cert:query`、`create`、`update`、`delete` |

## 12. 开发批次

- 第 1 批（P0）：物料类别、物料、BOM（含多级展开、反查、导入）
- 第 2 批（P1）：工作中心、工艺路线、ECN、样品、工装、认证、研发项目
