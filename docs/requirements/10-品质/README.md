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
