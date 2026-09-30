# 09 生产（总览）

## 1. 模块定位

生产负责车间执行：生产订单（计划层）→ 工单派工（执行层，P1）→ 领料 / 退料 → 工序报工（含不良登记）→ 完工入库 → 关闭；并记录生产追溯关系。

边界：
- 生产订单的建议来自 PMC（MRP），排产计划在 PMC；
- 发料、入库由仓库确认过账（生产模块生成领料单/退料单/完工入库申请，仓库生成出入库单）；
- 检验判定（IPQC、FQC）归品质；
- 成本计算归财务（生产提供领料、工时、完工数据）。

## 2. 端到端流程

```mermaid
flowchart TD
  MRP[PMC 生产建议] --> MO[生产订单]
  手工/样品/返工 --> MO
  MO -->|下达| WO[工单派工 P1]
  MO -->|下达| ISS[领料单] --> OUT[仓库 生产领料出库]
  WO --> RPT[工序报工]
  MO --> RPT
  RPT -->|不良| DEF[不良登记 → NCR]
  RPT -->|检验点| IPQC[品质 IPQC]
  RPT -->|倒冲| BF[倒冲出库]
  RPT -->|末道工序合格| FIN[完工入库申请] --> IN[仓库 生产入库: 待检仓/成品仓]
  IN --> FQC[品质 FQC] --> TF[检验调拨]
  MO --> RET[退料单] --> RIN[仓库 生产退料入库]
  MO -->|完工且处理完在制| CLOSE[关闭 → 财务成本]
```

## 3. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 生产订单 | [01-生产订单](01-生产订单.md) | 列表 / 编辑 / 详情 | T1 / T4 / T5 | `/production/prod-order` | `mfg:prod-order:query` | P0 |
| 工单派工 | [02-工单派工](02-工单派工.md) | 工单列表 / 派工 | T1 / 专用 | `/production/work-order` | `mfg:work-order:query` | P1 |
| 领料与退料 | [03-领料与退料](03-领料与退料.md) | 领料单、退料单 | T1 / T4 / T5 | `/production/issue`、`/production/return` | `mfg:issue:query`、`mfg:return:query` | P0 |
| 报工 | [04-报工](04-报工.md) | 报工单列表 / 快速报工 | T1 / 专用 | `/production/report` | `mfg:report:query` | P0 |
| 完工入库 | [05-完工入库](05-完工入库.md) | 生产订单详情内操作 + 完工入库记录 | T1 | `/production/finish` | `mfg:finish:query` | P0 |
| 不良与良率 | [06-不良与良率](06-不良与良率.md) | 不良记录、良率报表 | T1 / T7 | `/production/defect`、`/production/yield` | `mfg:defect:query` | P0 / P1 |
| 生产追溯 | [07-生产追溯](07-生产追溯.md) | 追溯查询 | 专用 | `/production/trace` | `mfg:trace:query` | P1 |
| 生产报表 | [08-生产报表](08-生产报表.md) | 进度、领料差异、产量工时 | T7 | `/production/report-center` | `mfg:report-center:query` | P1 |

菜单顺序：生产订单、工单、领料、退料、报工、完工入库、不良、良率、生产追溯、生产报表。

## 4. 用户角色

| 角色 | 功能 | 数据范围建议 |
|---|---|---|
| 生产主管 | 下达、关闭生产订单，审核报工，全部查询 | 本部门及下级（按生产订单的车间 dept_id） |
| 计划员 | 新建/下达生产订单（来自 MRP） | 全部 |
| 班组长 | 派工、领料申请、报工、不良登记、退料 | 本部门 |
| 操作员（可选账号） | 快速报工（扫码） | 仅本人 |

## 5. 数据表

mfg_prod_order、mfg_prod_order_material、mfg_prod_order_operation、mfg_work_order、mfg_issue、mfg_issue_line、mfg_return、mfg_return_line、mfg_report、mfg_report_operator、mfg_defect、mfg_finish、mfg_trace。

## 6. 编码规则

MFG_PROD_ORDER、MFG_WORK_ORDER、MFG_ISSUE、MFG_RETURN、MFG_REPORT；完工入库申请 MFG_FINISH（`FN-yyyyMMdd-3`，日重置）。

## 7. 内置字典

| 类型编码 | 名称 | 内置项 |
|---|---|---|
| mfg_defect_code | 不良代码（生产登记用；品质模块的缺陷代码库可同步使用） | 无内置项，示例：短路、虚焊、少件、划伤、尺寸不良、功能不良 |
| mfg_scrap_reason | 报废原因 | PROCESS 工艺、OPERATION 操作、MATERIAL 来料、EQUIPMENT 设备、OTHER |
| mfg_over_issue_reason | 超领原因 | SCRAP 损耗报废、DEFECT 来料不良、PLAN_ERROR 用量错误、OTHER |
| mfg_shift | 班次 | DAY 白班、NIGHT 夜班 |

## 8. 可审批单据

| 单据类型 | 名称 | 条件字段 |
|---|---|---|
| MFG_PROD_ORDER | 生产订单（手工新建的） | orderType、qty |
| MFG_ISSUE_OVER | 超领单 | overPct（超领比例 %）、amountBase |

## 9. 可打印单据

MFG_PROD_ORDER（生产订单/工单流程卡，带条码）、MFG_ISSUE（领料单）、MFG_RETURN（退料单）、MFG_WORK_ORDER（派工单，带条码）。

## 10. 系统参数

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| mfg.order.over-produce-pct | 生产订单 | 允许超产比例（%） | DECIMAL | 0 | 首道工序报工上限 = 订单数量 × (1 + 比例) |
| mfg.issue.over-issue-pct | 领料 | 正常领料允许超领比例（%） | DECIMAL | 0 | 超出需走超领单 |
| mfg.issue.kit-check | 领料 | 下达时齐套检查 | ENUM(NONE/WARN/BLOCK) | WARN | |
| mfg.report.require-work-order | 报工 | 报工必须基于工单 | BOOL | 否 | 否：可直接对生产订单工序报工 |
| mfg.report.auto-approve | 报工 | 报工自动审核 | BOOL | 是 | 否：需班组长/主管审核后才计入数量 |
| mfg.close.require-return | 关闭 | 关闭前必须退回余料 | ENUM(NONE/WARN/BLOCK) | WARN | |

## 11. 对其他模块提供的 API（production-api）

| 接口 | 方法 | 使用方 |
|---|---|---|
| `ProductionOrderApi` | `createFromMrp(List<MrpSuggestion>)`、`createSampleOrder(sampleId, materialId, qty, requiredDate)`、`updateMaterialsByEcn(ecnId, changes)` | PMC、研发工程 |
| `ProductionQueryApi` | `getWipQty(materialIds)`（在制：已下达未完工的剩余数量，含预计完工日期）、`getOpenOrdersByComponent(componentId)`、`getProgress(prodOrderIds)`、`isBomUsed(bomId)`、`getAllocatedQty(materialId)`（已下达未领的用料需求） | PMC、研发工程、销售、财务 |
| `ProductionCostApi` | `getOrderIdsBySource(sourceType, ids)`（领料 / 退料 / 完工来源 → 生产订单）、`getOrders(ids)`（产品、状态、计划数量、车间）、`getWorkHours(from, to)`（已审核报工的车间、工时、合格数量） | 财务成本核算（12-07） |
| `TraceApi` | `forward(materialId, batchNo)`（原材料批次 → 用在哪些生产订单/成品批次）、`backward(materialId, batchNo)`（成品批次 → 用了哪些原材料批次） | 品质、BI |

**发布事件**：`ProductionOrderReleasedEvent`、`ProductionOrderCompletedEvent`、`ProductionOrderClosedEvent`、`WorkReportApprovedEvent`（含工装、工时、合格/不良数量）、`WorkReportReversedEvent`、`IpqcTriggerEvent`、`DefectRegisteredEvent`、`ProductionProgressEvent`。

**监听事件**：仓库 `StockOutConfirmedEvent`（领料出库、倒冲）、`StockInConfirmedEvent`（完工入库、退料入库）、`StockDocRejectedEvent`；研发工程 `EcnEffectiveEvent`；品质 `InspectionJudgedEvent`（FQC 结果回写完工记录）。

## 12. 权限点汇总

| 分组 | 权限点 |
|---|---|
| 生产订单 | `mfg:prod-order:query`（菜单）、`create`、`update`、`delete`、`submit`、`release`（下达）、`unrelease`、`suspend`（暂停/恢复）、`close`、`void`、`print` |
| 工单 | `mfg:work-order:query`（菜单）、`create`（派工）、`update`、`delete`、`print` |
| 领料 | `mfg:issue:query`（菜单）、`create`、`update`、`delete`、`submit`、`over`（超领申请）、`print` |
| 退料 | `mfg:return:query`（菜单）、`create`、`update`、`delete`、`submit`、`print` |
| 报工 | `mfg:report:query`（菜单）、`create`、`update`、`delete`、`approve`、`unapprove` |
| 完工入库 | `mfg:finish:query`（菜单）、`create`、`cancel` |
| 不良 | `mfg:defect:query`（菜单）、`create`、`update`、`to-ncr` |
| 追溯 | `mfg:trace:query`（菜单） |
| 报表 | `mfg:report-center:query`（菜单）、`export` |

## 13. 实现说明（已实现）

生产订单、工单派工、领料/超领/倒冲、退料、报工、不良处置与良率、完工入库、生产追溯、生产报表的后端与页面均已实现。与其他模块的接入方式如下：

- **库存对接（inventory-api `InventoryDocApi`）**：
  - 领料单、超领单审核后生成 `MFG_ISSUE` 出库单，退料单生成 `MFG_RETURN` 入库单（良品回物料默认仓，不良回不良品仓），完工入库生成 `MFG_FINISH` 入库单（免检入成品仓，需 FQC 入待检仓）。一张领/退料单只对应一个仓库，新建时按发料仓拆分。
  - 监听 `StockOutConfirmedEvent` / `StockInConfirmedEvent` 回写实发、实收（按行、按批次写追溯记录）；监听 `StockDocEvent`（反确认、驳回）扣回数量并追加反向追溯记录。
  - 倒冲：报工审核时按“(合格 + 报废) × 单位用量 × (1 + 损耗)”（按单位精度向上取整）生成倒冲领料单并提交，先检查可用库存，不足时报工失败。倒冲出库单是否自动过账取决于仓库参数 `inv.out.auto-confirm-source`，未开启时由仓库确认；反审核报工时作废未确认的倒冲出库单，已确认的提示先在仓库反确认。
- **需要确认的操作**：下达齐套检查（参数 `mfg.issue.kit-check` 为 WARN）、关闭时余料/在制/待处理不良（`mfg.close.require-return` 为 WARN）返回 `needConfirm`，前端确认后以 `confirmShortage=true` / `confirmScrap=true` 重试；参数为 BLOCK 时直接拒绝。可用库存已扣除先下达的其他订单未领数量。
- **不良处置**：报工登记的不良为“待处理”，返修合格、报废以补充报工单（`report_kind` = REPAIR / SCRAP）记录，计入工序合格 / 订单报废。
- **扩展点（production-api）**：
  - `DefectNcrCreator`：品质实现，“不良记录”可生成 NCR（没有实现方时 `/defects/ncr-available` 返回 false，按钮不显示）。
  - `ProductionFinishApi.onFqcJudged(finishId, qualified, rejected)`：品质 FQC 判定后回写合格入库；免检产品仓库确认即计为合格。
  - 事件：`ProductionOrderReleased/Unreleased/Completed/ClosedEvent`、`ProductionProgressEvent`、`WorkReportApprovedEvent`、`WorkReportReversedEvent`、`IpqcTriggerEvent`（检验点工序报工审核）、`DefectRegisteredEvent`、`DefectMaterialReturnedEvent`（不良退料入库，通知品质）。
- **对外接口**：`ProductionOrderApi.createFromMrp`（PMC 转单，直接“已计划”）、`release`（PMC“转单并下达”，缺料照常下达）、`updatePlanDates`（PMC 排产回写计划日期，记操作日志）、`createSampleOrder`（研发工程样品，完工后回调 `SampleApi`）；`ProductionQueryApi.getOpenOrders`（未完工订单含用料与工序，供 MRP、缺料、排产、交期预警）、`getWipQty / getAllocatedQty / getProgress / getProgressBySalesOrderLines / getOpenOrdersByComponent / isBomUsed`；`TraceApi` 正向 / 反向追溯。
- **品质**：报工保存时调用 `InspectionQueryApi.checkFirstArticle` 做首件检验卡控（参数 `qc.ipqc.first-article`）；生产订单工序页签按 `InspectionQueryApi.getIpqcRejected` 显示“IPQC 不合格”警示。
- **追溯**：追溯页显示产品批次的出货记录（出货单、日期、客户，取出货模块 `ShipmentQueryApi.getShipmentsByBatch`）：正向为所有产品批次（召回范围），反向为根批次。
- **为其他模块实现**：`SampleOrderCreator`、`BomReferenceChecker`、`RoutingReferenceChecker`、`EcnImpactProvider`（ECN 生效时提示受影响的未完工订单）、`SalesOrderReferenceChecker`（由订单生成的生产订单阻止订单反审核）、`OrgReferenceChecker`、`MaterialReferenceChecker`、`DictReferenceChecker`、`FileAccessChecker`。
- **限制**：
  - 拆卸订单（DISASSEMBLY）暂不支持。
  - 编辑页 BOM 版本下拉只列出默认版本和当前选择的版本（BomApi 暂无按产品列出版本的接口）。
  - 派工的工作中心不限制所属车间。
