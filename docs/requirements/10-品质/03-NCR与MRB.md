# 10-03 NCR 与 MRB

## 1. 功能说明

NCR（不合格品报告）记录任何环节发现的不合格：描述、数量、责任归属；由 MRB（物料评审委员会）会签决定处置方式（退货、特采、挑选、返工、报废），审批通过后系统按处置结果驱动后续动作；需要时触发 CAPA、SCAR。

- 使用者：QE、MRB 成员（品质、工程、PMC、采购/生产主管）
- 优先级：P0

### 1.1 来源

| 来源 | 编码 | 生成方式 |
|---|---|---|
| 来料检验 | IQC | 检验单判定拒收（参数自动）或提交 MRB |
| 制程检验 | IPQC | 检验单提交 MRB / 手工 |
| 成品检验 | FQC | 同 IQC |
| 出货检验 | OQC | 同 IQC |
| 生产不良 | PRODUCTION | 生产不良记录 [生成 NCR]；不良退料事件 |
| 库存 | INVENTORY | 手工（库存物料发现问题） |
| 客诉 | COMPLAINT | 客诉单生成 |

### 1.2 处置方式

| 处置 | 编码 | 系统动作 |
|---|---|---|
| 退供应商 | RETURN | 来源为 IQC 时检验判定拒收 → 不良品仓；提示采购员创建采购退货（可一键生成草稿） |
| 特采（让步接收） | CONCESSION | 检验判定特采 → 可用仓，批次标记特采 |
| 挑选 | SORT | 检验单进入“挑选”：录入挑选良品/不良数量后判定 |
| 返工 | REWORK | 判定不合格 → 不良品仓；提示生产主管创建返工生产订单（可一键生成草稿） |
| 报废 | SCRAP | 判定不合格 → 不良品仓；提示仓库创建报废出库（可一键生成草稿） |
| 降级使用 | DOWNGRADE（P2） | — |

一张 NCR 可以对数量拆分多种处置（如 80 特采、20 退货），合计 = NCR 数量。

## 2. 数据表

### qc_ncr NCR（BaseDocDO，编码规则 QC_NCR）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| source | enum(IQC/IPQC/FQC/OQC/PRODUCTION/INVENTORY/COMPLAINT) | 是 | |
| inspection_id | id | 否 | 来源检验单 |
| source_no | str(64) | 否 | 来源单号（生产订单、客诉单等） |
| material_id | id | 是 | |
| batch_no | str(64) | 否 | |
| supplier_id / customer_id | id | 否 | |
| ncr_qty | qty | 是 | 不合格数量 |
| defect_description | str(2000) | 是 | 不合格描述 |
| defect_codes | str(256) | 否 | 缺陷代码（多选） |
| severity | enum(CRITICAL/MAJOR/MINOR) | 是 | |
| responsibility | dict(qc_ncr_responsibility) | 是 | 责任归属 |
| containment | str(1000) | 否 | 围堵措施（如冻结同批次库存、通知产线） |
| capa_required | bool | 是 | 是否需要 CAPA（系统按规则建议，QE 可改） |
| scar_required | bool | 是 | 是否需要 SCAR（责任为供应商时默认是） |
| amount_base | amt | 否 | 涉及金额（数量 × 参考单价，审批条件） |

状态：草稿 → 待审批（MRB 会签）→ 已审核（处置执行中）→ 已关闭；草稿可作废。

### qc_ncr_disposition 处置明细

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ncr_id | id | 是 | |
| disposition | enum(RETURN/CONCESSION/SORT/REWORK/SCRAP) | 是 | |
| qty | qty | 是 | |
| remark | str(512) | 否 | 如特采条件“仅用于 FG1 订单” |
| follow_doc_no | str(64) | 否 | 后续单据（退货单、返工订单、报废出库单） |
| done | bool | 是 | 执行完成 |

## 3. 页面

### 3.1 NCR 列表（T1）

查询：单号、来源、物料、供应商、客户、责任、严重度、状态（默认未关闭）、日期。列：单号、来源、物料、批次、数量、严重度（致命红）、责任、处置摘要（“特采 80 / 退货 20”）、供应商/客户、状态、创建人、日期、操作。

### 3.2 编辑页（T4）

单头：来源（只读或手工选择）、物料*、批次、数量*、供应商/客户、不合格描述*、缺陷代码、严重度*、责任归属*、围堵措施、需要 CAPA、需要 SCAR、附件（照片、检验报告）。
处置明细：处置方式*、数量*、说明；底部显示合计与 NCR 数量是否一致。
页头：[保存] [提交 MRB]。

MRB 会签通过审批流配置（QC_NCR，建议节点：QE → 工程 + PMC + 采购/生产主管 会签 → 品质主管；处置含特采时加品质经理）。

### 3.3 详情页（T5）

按钮：编辑/提交（草稿）、审批、[生成采购退货]、[生成返工订单]、[生成报废出库]（已审核，按处置明细）、[标记执行完成]（每条处置）、[生成 CAPA]、[生成 SCAR]、关闭（全部处置完成）、作废、打印。
页签：处置执行（每条处置的后续单据与完成状态）、来源检验、关联（CAPA、SCAR、客诉）、审批记录、操作日志、附件。

## 4. 业务规则

| 编号 | 触发 | 规则 | 提示原文 |
|---|---|---|---|
| QC-NCR-R01 | 提交 | 处置数量合计 = NCR 数量 | `处置数量合计 {sum} 必须等于不合格数量 {qty}` |
| QC-NCR-R02 | 提交 | 来源为检验单时，处置只能是 RETURN/CONCESSION/SORT/SCRAP（IQC、复检）或 CONCESSION/REWORK/SCRAP（FQC、退货检验）；OQC 只能 REWORK/SORT/CONCESSION | `该来源不支持处置方式「{d}」` |
| QC-NCR-R03 | 审批通过 | 来源为检验单：按处置自动完成检验判定（CONCESSION → 特采数量；RETURN/REWORK/SCRAP → 不合格数量；SORT → 检验单进入挑选待判定）；发布 `NcrApprovedEvent` | — |
| QC-NCR-R04 | 审批通过 | 围堵：严重度为致命时，自动冻结该物料同批次在所有仓库的库存（`BatchApi.freeze`，冻结来源为本 NCR），关闭 NCR 时提示是否解冻 | — |
| QC-NCR-R05 | CAPA 建议 | 同物料同缺陷代码 30 天内 NCR 次数 ≥ 参数，或严重度为致命，或来源为客诉时 capa_required 默认为是 | — |
| QC-NCR-R06 | 关闭 | 所有处置执行完成；需要 CAPA/SCAR 的已生成（不要求已关闭） | `还有处置未完成` / `请先生成 CAPA` |

## 5. 接口

`/quality/ncrs`（CRUD、submit、close、void、print-data）、`/{id}/dispositions/{dId}/done`、`/{id}/create-purchase-return`、`/{id}/create-rework-order`、`/{id}/create-scrap-out`、`/{id}/create-capa`、`/{id}/create-scar`；`NcrApi.createFromDefect`。

## 6. 验收用例

| 编号 | 前置条件 | 操作 | 预期结果 |
|---|---|---|---|
| QC-NCR-T01 | IQC 5000 提交 MRB | NCR 处置：特采 4000、退货 1000，审批通过 | 检验单判定：特采 4000、不合格 1000；调拨 4000 → 电子料仓（特采标记）、1000 → 不良品仓 |
| QC-NCR-T02 | 同上 | 生成采购退货 | 资材出现草稿退货单 1000，关联 NCR |
| QC-NCR-T03 | 处置合计 900 | 提交 | 提示处置数量合计必须等于不合格数量 |
| QC-NCR-T04 | 致命缺陷 | 审批通过 | 该批次所有库存被冻结；仓库不能解冻（提示由品质冻结） |
| QC-NCR-T05 | 同物料同缺陷 30 天内第 3 次 | 新建 NCR | 默认“需要 CAPA”为是 |
