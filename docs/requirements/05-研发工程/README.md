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
| `RoutingApi` | `getDefaultRouting(materialId)`、`getRouting(routingId)`（已实现） | PMC、生产、财务 |
| `WorkCenterApi` | `get`、`list`（已实现） | PMC、生产 |
| `ToolingApi` | `get`、`listUsable(materialId)`、`validateUsable(toolingId)`、`usageOf(toolingId, outputQty)`、`addUsage(toolingId, count, sourceDocNo)`（已实现） | 生产 |
| `CertificationApi` | `listValid(materialId)`（已实现） | 销售、出货 |
| `SampleApi` | `onProductionCompleted(sampleId)`（已实现，样品生产订单完工入库后由生产模块调用） | 生产 |

**发布的事件**：`MaterialStatusChangedEvent`（已实现）、`MaterialChangedEvent`（计划/采购/库存属性变化）、`BomApprovedEvent`、`BomDefaultChangedEvent`、`EcnApprovedEvent`、`EcnEffectiveEvent`、`CertificationExpiringEvent`、`ToolingLifeWarningEvent`。

**监听的事件**：审批 `ApprovalCompletedEvent`（ECN、样品单）、仓库 `StockOutConfirmedEvent` / `StockDocEvent`（样品出库确认、反确认、退回）。工装使用次数、样品单完工由生产模块直接调用 `ToolingApi`、`SampleApi`（见第 12 节）。

**调用的其他模块 API**：仓库 `InventoryQueryApi.getStockSummary`（ECN 影响分析）、仓库 `InventoryDocApi.createStockOut`（样品出库）；在途采购、在制生产订单、销售订单影响与样品生产订单通过扩展点 `EcnImpactProvider`、`SampleOrderCreator` 由对应模块实现。

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

- 第 1 批（P0）：物料类别、物料、BOM（含多级展开、反查、导入）——**已实现**
- 第 2 批（P1）：工作中心、工艺路线、ECN、样品、工装、认证、研发项目——**已实现**

第 1 批实现说明（其他模块接入时需要知道的约定）：

- **引用检查扩展点**：`MaterialReferenceChecker`（engineering-api）由仓库、资材、销售、生产等模块各自实现，返回库存量、未完成单据数、是否被引用、是否不能改库存管理方式，用于物料停用提示（R08）、删除（R09）、修改库存管理方式（R10）和单位换算锁定（R07）；`BomReferenceChecker` 由生产模块实现（被生产订单使用的 BOM 版本不能反审核，R09）。各模块未实现前视为“未被引用”。
- **需求量口径**：`BomApi.explode` 按第 3 节公式逐层计算并按子件单位精度向上取整；虚拟件透过（不出现在结果中）。低位码在 BOM 审核、设为默认、反审核后同步重算（按默认已审核版本）。
- **物料编码**：业务编码 `ENG_MATERIAL`，前缀 `{categoryPrefix}` + 5 位流水，不同前缀独立计数。骨架中的旧规则 `MATERIAL` 不再使用，可在“编码规则”页面忽略。
- 物料详情的“库存”“供应商”页签依赖仓库 `InventoryQueryApi` 和资材的价格接口，待对应模块实现后补充；工序列（`operation_seq`）待工艺路线实现后启用。

第 2 批实现说明：

- **扩展点（engineering-api，由其他模块实现，未实现前视为“无影响/未使用”）**：
  - `EcnImpactProvider`：ECN 影响分析中的在途采购（资材）、在制生产订单（生产）、未完成销售订单（销售）；库存由研发工程直接调用仓库 `InventoryQueryApi.getStockSummary`。可以有多个实现。
  - `SampleOrderCreator`：生产模块实现“生成样品生产订单”；未实现时样品详情提示“生产模块尚未启用”，只能走“从库存领取”。完工入库后生产模块调用 `SampleApi.onProductionCompleted`。
  - `RoutingReferenceChecker`：生产模块实现，被生产订单使用的工艺路线不能反审核、工作中心不能删除。
- **工装使用次数**：生产模块报工时直接调用 `ToolingApi`：先 `validateUsable`（R03，达到寿命且未允许超寿命时抛错），再 `usageOf(toolingId, 合格 + 不良)` 按模穴向上取整得到次数，最后 `addUsage`（报工反审核传负数扣回）。不再通过监听 `WorkReportApprovedEvent` 实现。
- **ECN 生效**：审批通过为每个 BOM 复制新版本并直接审核（版本说明为 ECN 单号，`eng_bom.ecn_id` 记录来源）；IMMEDIATE 立即生效，DATE 由定时任务 `ENG_ECN_EFFECT`（每天 00:10）处理生效日期已到的 ECN，USE_UP 由“立即生效”按钮手工切换。`EcnEffectiveEvent.updateWipDocNos` 为处理方式“更新用料”的在制生产订单，生产模块据此更新未领料部分。
- **执行确认任务**：分析影响时按影响类型自动生成（库存 → 仓库、在途采购 → 采购、在制 → 生产、销售订单 → PMC），品质默认“确认检验标准”；涉及关键件（BOM 行 is_key）或已关联认证的物料时增加“认证评估”（默认负责人为有 `eng:cert:create` 权限的用户）。未指定负责人的任务由发起人确认。
- **样品出库**：样品单“申请出库”生成仓库“其他出库”草稿（来源 `ENG_SAMPLE`），仓库确认出库（`StockOutConfirmedEvent`）后才能登记寄出；出库单反确认或退回时样品单恢复为未出库。
- **定时任务**：`ENG_ECN_EFFECT`（ECN 定时生效）、`ENG_SAMPLE_REMIND`（样品寄出提醒，每天 08:30）、`ENG_PROJECT_TASK_REMIND`（项目任务到期提醒）、`ENG_CERT_EXPIRY`（证书到期提醒，按 `eng.cert.remind-days` 每个阈值只提醒一次，续期后重新提醒）。
