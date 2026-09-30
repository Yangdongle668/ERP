# 10 品质（总览）

## 1. 模块定位

品质负责**所有检验判定**和**质量问题闭环**：检验标准与抽样、检验单（IQC / IPQC / FQC / OQC / 退货检验 / 复检）、NCR 与 MRB（含特采）、CAPA/8D、客诉、SCAR、质量追溯与报表。

品质不直接改库存：判定结果以 `InspectionJudgedEvent` 发布，仓库据此生成检验调拨单（待检仓/退货仓 → 可用仓 / 不良品仓）。

## 2. 检验触发一览

| 检验类型 | 编码 | 触发事件 | 检验对象所在位置 | 判定后的库存动作 |
|---|---|---|---|---|
| 来料检验 | IQC | 仓库 `StockInConfirmedEvent`：采购入库 / 委外入库，入库仓为待检仓 | 待检仓 | 合格/特采 → 物料默认仓；不合格 → 不良品仓 |
| 制程检验 | IPQC | 生产 `IpqcTriggerEvent`（检验点工序报工审核）；手工（首件、巡检） | 生产线（不在库） | 无库存动作；不合格触发 NCR / 生产不良处理 |
| 成品检验 | FQC | 仓库 `StockInConfirmedEvent`：生产入库，入库仓为待检仓 | 待检仓 | 合格 → 半成品/成品仓；不合格 → 不良品仓 |
| 出货检验 | OQC | 出货 `OqcRequestEvent`（出货通知装箱完成后） | 成品仓（已拣货） | 无库存动作；合格才允许出货；不合格 → 出货通知退回拣货 |
| 退货检验 | RETURN | 仓库 `StockInConfirmedEvent`：销售退货入库，入库仓为退货仓 | 退货仓 | 良品 → 成品仓；返工/不良 → 不良品仓 |
| 复检 | RECHECK | 仓库 `RecheckRequestedEvent`（复检送检调拨确认） | 待检仓 | 同 IQC |

## 3. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 检验基础数据 | [01-检验基础数据](01-检验基础数据.md) | 检验项目、检验标准、抽样方案、缺陷代码 | T1 / T4 | `/quality/standard` | `qc:standard:query` | P0 |
| 检验单 | [02-检验单](02-检验单.md) | IQC / IPQC / FQC / OQC / 退货检验（各一个菜单，同一页面按类型过滤）；检验录入页 | T1 / 专用 | `/quality/iqc` 等 | `qc:iqc:query` 等 | P0 |
| NCR 与 MRB | [03-NCR与MRB](03-NCR与MRB.md) | NCR 列表 / 编辑 / 详情 | T1 / T4 / T5 | `/quality/ncr` | `qc:ncr:query` | P0 |
| CAPA / 8D | [04-CAPA](04-CAPA.md) | CAPA 列表 / 详情（分步） | T1 / 专用 | `/quality/capa` | `qc:capa:query` | P1 |
| 客诉 | [05-客诉](05-客诉.md) | 客诉列表 / 编辑 / 详情 | T1 / T4 / T5 | `/quality/complaint` | `qc:complaint:query` | P1 |
| SCAR | [06-SCAR](06-SCAR.md) | SCAR 列表 / 详情 | T1 / T5 | `/quality/scar` | `qc:scar:query` | P1 |
| 质量追溯与报表 | [07-质量追溯与报表](07-质量追溯与报表.md) | 质量追溯、来料/制程/出货质量报表 | 专用 / T7 | `/quality/trace`、`/quality/report` | `qc:trace:query`、`qc:report:query` | P1 |

菜单顺序：检验标准、IQC、IPQC、FQC、OQC、退货检验、NCR、CAPA、客诉、SCAR、质量追溯、质量报表。

## 4. 用户角色

| 角色 | 功能 |
|---|---|
| IQC / IPQC / FQC / OQC 检验员 | 各自类型检验单的录入与判定（初判） |
| QE / SQE | 检验标准、NCR、CAPA、SCAR、客诉分析 |
| 品质主管 | 特采与 MRB 审批、重判、报表 |
| MRB 成员（品质、工程、PMC、采购/生产主管） | NCR 会签 |

## 5. 数据表

qc_inspection_item_lib、qc_standard、qc_standard_item、qc_sampling_plan、qc_aql_table（内置）、qc_defect_code、qc_inspection、qc_inspection_item、qc_inspection_defect、qc_ncr、qc_ncr_disposition、qc_capa、qc_complaint、qc_scar。

## 6. 编码规则

QC_IQC、QC_IPQC、QC_FQC、QC_OQC、QC_RETURN（`RI-yyyyMMdd-3`）、QC_RECHECK（`RE-yyyyMMdd-3`）、QC_NCR、QC_CAPA、QC_COMPLAINT、QC_SCAR；检验标准 QC_STANDARD（`QS-4`，不重置）。

## 7. 内置字典

| 类型编码 | 名称 | 内置项 |
|---|---|---|
| qc_defect_category | 缺陷分类 | APPEARANCE 外观、DIMENSION 尺寸、FUNCTION 功能、PERFORMANCE 性能、PACKAGING 包装、LABEL 标识、DOCUMENT 资料 |
| qc_inspection_method | 检验方法 | VISUAL 目视、MEASURE 量测、TEST 功能测试、REPORT 查验报告 |
| qc_complaint_type | 客诉类型 | QUALITY 质量、DELIVERY 交付、PACKAGING 包装、SERVICE 服务、OTHER |
| qc_ncr_responsibility | 责任归属 | SUPPLIER 供应商、PROCESS 制程、DESIGN 设计、CUSTOMER 客户、LOGISTICS 物流、UNKNOWN 待确认 |

## 8. 可审批单据

| 单据类型 | 名称 | 条件字段 |
|---|---|---|
| QC_NCR | NCR（MRB 处置） | disposition（含特采时为 CONCESSION）、qty、amountBase、source |
| QC_REJUDGE | 检验重判 | inspectType |
| QC_COMPLAINT_CLOSE | 客诉结案 | severity |

## 9. 可打印单据

检验报告（各检验类型，中文；OQC 可选英文出货检验报告随货）、NCR、CAPA/8D 报告（中/英）、SCAR（中/英）。

## 10. 系统参数

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| qc.iqc.auto-ncr | 检验 | 判定不合格自动生成 NCR | BOOL | 是 | |
| qc.inspection.overdue-hours | 检验 | 检验超时（小时） | INT | 24 | 检验单创建后超时未判定提醒品质主管 |
| qc.ipqc.first-article | 检验 | 生产订单首次报工需首件检验 | BOOL | 否 | |
| qc.defect.alert-threshold | 制程 | 同一不良当日预警阈值（件） | INT | 10 | |
| qc.capa.trigger-repeat | CAPA | 触发 CAPA 的重复次数 | INT | 3 | 同物料同缺陷 30 天内 NCR 次数 |
| qc.complaint.reply-days | 客诉 | 客诉回复期限（天） | INT | 3 | 初步回复（D3 围堵）期限 |
| qc.scar.reply-days | SCAR | 供应商回复期限（天） | INT | 7 | |

## 11. 对其他模块提供的 API（quality-api）

| 接口 | 方法 | 使用方 |
|---|---|---|
| `InspectionQueryApi` | `getByBiz(bizType, bizId)`、`isOqcPassed(noticeId)` | 资材、生产、出货、销售 |
| `NcrApi` | `createFromDefect(...)`（生产不良生成 NCR） | 生产 |
| `QualityStatsApi` | `supplierLotStats(supplierId, from, to)` | 资材（供应商评估） |

**发布事件**：`InspectionCreatedEvent`、`InspectionJudgedEvent`（inspectType、来源单据、批次、合格/特采/不合格数量、结果）、`InspectionRejudgedEvent`、`NcrApprovedEvent`、`ComplaintCreatedEvent`、`ScarClosedEvent`。

**监听事件**：仓库 `StockInConfirmedEvent`、`RecheckRequestedEvent`、`TransferConfirmedEvent`（检验调拨完成 → 检验单“已处理”）；生产 `IpqcTriggerEvent`、`DefectMaterialReturnedEvent`；出货 `OqcRequestEvent`。

## 12. 权限点汇总

| 分组 | 权限点 |
|---|---|
| 检验标准 | `qc:standard:query`（菜单）、`create`、`update`、`approve`、`delete` |
| 抽样与缺陷代码 | `qc:sampling:manage`、`qc:defect-code:manage` |
| IQC / IPQC / FQC / OQC / 退货检验 | `qc:iqc:query`、`qc:iqc:inspect`（录入）、`qc:iqc:judge`（判定）；其他类型同理：`qc:ipqc:*`、`qc:fqc:*`、`qc:oqc:*`、`qc:return:*`；`qc:inspection:rejudge`（重判）、`qc:inspection:create`（手工新建 IPQC/复检） |
| NCR | `qc:ncr:query`（菜单）、`create`、`update`、`submit`、`close`、`void`、`print` |
| CAPA | `qc:capa:query`（菜单）、`create`、`update`、`verify`（效果验证）、`close` |
| 客诉 | `qc:complaint:query`（菜单）、`create`、`update`、`reply`、`close` |
| SCAR | `qc:scar:query`（菜单）、`create`、`update`、`send`、`verify`、`close` |
| 追溯与报表 | `qc:trace:query`、`qc:report:query`、`qc:report:export` |

## 13. 实现说明（已实现）

检验基础数据、检验单（IQC / IPQC / FQC / OQC / 退货检验 / 复检）、NCR 与 MRB、CAPA / 8D、客诉、SCAR、质量追溯与报表的后端与页面均已实现。出货、财务模块尚未实现，品质按下列方式接入：

- **抽样**：GB/T 2828.1 一次正常检验的字码表与主表以代码常量内置（`AqlTable`，未建 `qc_aql_table` 表），箭头规则按“字码序号 + AQL 序号”的对角结构展开；支持 AQL 0.010～10 与 0（零缺陷）。全检、固定数量方案按零缺陷判定（Ac0 / Re1），免检样本量为 0。初始数据：5 个抽样方案、5 个检验项目、8 个缺陷代码、每种检验类型一个“通用外观检验”标准。
- **检验单生成**：
  - 监听 `StockInConfirmedEvent`：采购 / 委外入库进待检仓 → IQC，生产入库进待检仓 → FQC，销售退货入退货仓 → 退货检验。同一入库单来源行不重复生成；物料质量属性免检（或方案为免检）时直接判定合格。
  - `IpqcTriggerEvent` → IPQC（报工触发）；复检送检调拨确认（`TransferConfirmedEvent` 类型 RECHECK）→ 复检单；IPQC 首件 / 巡检 / 末件与复检可手工新建。
  - OQC 由出货模块调用 `InspectionApi.requestOqc`（每个出货通知行一张），`isOqcPassed` 查询是否放行，`cancelOqc` 撤销。
- **判定**：
  - 数量合计须等于批量；特采只能走 MRB；建议不合格时判定合格须填写让步理由。
  - 同一事务内：生成检验调拨（来源 `QC_INSPECTION`，合格 / 特采 → 默认仓，不合格 → 不良品仓）→ 回写上游（到货行 `PurchaseReceiptApi.applyInspection`、完工入库 `ProductionFinishApi.onFqcJudged`、销售退货行 `SalesReturnApi.recordJudgement`）→ 发布 `InspectionJudgedEvent`；拒收时按参数自动生成草稿 NCR。
  - 调拨单全部确认后检验单变为“已处理”。
- **重判**：审批流 `QC_REJUDGE`（未配置时直接生效）。作废未确认的检验调拨，撤销上游回写（到货行恢复待检、完工入库扣回、退货行清零），检验单回到检验中；调拨已确认时提示先由仓库反确认。
- **来源撤销**：入库单反确认前，已判定（含待 MRB）的检验单阻止反确认；反确认后待检 / 检验中的检验单自动取消。
- **NCR / MRB**：
  - 提交时校验处置合计与来源允许的处置方式，审批流 `QC_NCR`（MRB 会签，条件字段 disposition / qty / amountBase / source）。
  - 审批通过后：检验来源按处置完成判定（含挑选时检验单回到检验中，只能按“挑选”判定）；致命缺陷冻结同批次（`BatchApi.freeze`，关闭时可选解冻）；发布 `NcrApprovedEvent`；含退货处置时提醒供应商的采购员。
  - 报废处置生成不良品仓的其他出库草稿，返工处置生成“已计划”的返工生产订单（`ProductionOrderApi.createFromMrp`）。
  - 生产不良“生成 NCR”由本模块实现 `DefectNcrCreator`，另提供 `NcrApi.createNcr`。
- **CAPA / 客诉 / SCAR**：按文档实现分步、验证、结案与回复流程；客诉结案走审批流 `QC_COMPLAINT_CLOSE`，同意赔偿金额 > 0 时发布 `ComplaintClaimAgreedEvent`，客诉列表按 CRM 客户负责人做数据权限；SCAR 结案发布 `ScarClosedEvent`，`QualityStatsApi.supplierLotStats` 提供供应商批次与 SCAR 统计。
- **定时任务**：`QC_INSPECTION_OVERDUE`（每小时，检验超时提醒品质主管，每单一次）、`QC_FOLLOWUP_REMIND`（每天 08:30，CAPA 到期 / 超期、客诉回复期限、SCAR 逾期提醒）。品质主管取参数 `qc.managers`，为空时取拥有 `qc:ncr:close` 的用户；致命客诉另通知参数 `qc.complaint.executives`。
- **首件检验**：生产报工保存时调用 `InspectionQueryApi.checkFirstArticle`（参数 `qc.ipqc.first-article` 打开且该订单没有合格的首件检验时阻止）。
- **其他模块契约新增**：`StockInConfirmedEvent` 增加 `supplierId` / `customerId`；`PurchaseReceiptApi.revertInspection`（重判撤销）；生产报工依赖 quality-api。
- **限制**：
  - 退供应商：“通知采购退货”调用资材 `PurchaseReturnApi.createDraft` 生成草稿退货单（IQC 来源对应到货行，其余按供应商 + 物料 + 批次找最近可退的到货；出库仓取批次所在的不良品仓），处置明细登记退货单号，并给采购员发待办确认提交；找不到可退的到货记录时只发待办，由采购员手工创建。
  - 同一不良当日预警（参数 `qc.defect.alert-threshold`）：IPQC 判定后，按缺陷代码汇总当天已判定 IPQC 的缺陷数，达到阈值时向品质主管发工作台预警（每个缺陷代码每天一次）。IPQC 不合格警示：`InspectionQueryApi.getIpqcRejected(prodOrderId)` 返回各工序最近一次判定为拒收的 IPQC，生产订单详情的工序页签显示“IPQC 不合格”标记（点击打开检验单），之后同工序判定合格即解除。
  - 质量追溯的出货记录取批次的销售出库流水，客户取出货模块 `ShipmentQueryApi.getShipmentsByBatch`。
  - SCAR 加严抽样（QC-SCAR-R04，P2）、检验报告与 8D 报告的英文模板暂未提供（SCAR 已有英文模板）。
  - 质量报表的图表以条形图表示，制程良率、直通率沿用生产报表。
