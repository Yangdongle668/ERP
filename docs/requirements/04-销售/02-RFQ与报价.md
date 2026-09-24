# 04-02 RFQ 与报价

## 1. 功能说明

- **RFQ（客户询价）**：登记客户的询价需求（客户料号、图纸、数量阶梯、目标价、要求交期），分派给工程评估和成本核算。
- **成本核算**：按 BOM 和工艺路线估算材料、人工、制费、模具分摊，加上管理费率和利润率得到建议售价。
- **报价单**：根据核算结果（或直接对已有产品）制作报价，审批后发给客户；客户接受后一键转销售订单；支持修订版本。

- 使用者：业务员、工程师、成本工程师、业务主管
- 优先级：P1（报价单可以不经 RFQ 直接新建）

## 2. 数据表

### sal_rfq RFQ（BaseDocDO，编码规则 SAL_RFQ）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| customer_id | id | 是 | 可为潜在客户 |
| contact_id | id | 否 | |
| opportunity_id | id | 否 | 商机 |
| currency | str(3) | 是 | 客户币别 |
| trade_term | dict(sys_trade_term) | 否 | |
| reply_due_date | date | 是 | 需回复客户的日期 |
| engineer_id | id | 否 | 分派的工程师（可行性、建料、BOM） |
| cost_engineer_id | id | 否 | 分派的成本工程师 |
| rfq_status | enum(DRAFT/EVALUATING/COSTED/QUOTED/CLOSED) | 是 | 草稿 / 评估中 / 已核算 / 已报价 / 已关闭 |
| close_reason | str(256) | 否 | |

### sal_rfq_line

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| line_no | int | 是 | |
| customer_part_no | str(64) | 否 | 客户料号 |
| description | str(512) | 是 | 客户需求描述 |
| material_id | id | 否 | 对应本厂物料（已有产品，或工程建料后填写） |
| annual_qty | qty | 否 | 预计年用量 |
| qty_breaks | str(128) | 是 | 报价数量阶梯，如 `1000,5000,10000` |
| target_price | price | 否 | 客户目标价 |
| required_date | date | 否 | 客户要求交期 |
| feasibility | enum(PENDING/OK/NG) | 是 | 工程可行性 |
| feasibility_remark | str(512) | 否 | |
| cost_sheet_id | id | 否 | 成本核算 |

图纸、规格书作为附件挂在 RFQ 上（category = DRAWING）。

### sal_cost_sheet 成本核算单（技术表，每个 RFQ 行 + 数量阶梯一份）

| 字段 | 类型 | 说明 |
|---|---|---|
| rfq_line_id | id | |
| qty | qty | 核算数量（阶梯） |
| material_cost | price | 材料成本（本位币/件）：BOM 多级展开，子件单价取标准成本 → 最新采购价 → 手工录入 |
| labor_cost | price | 人工 = Σ 工序标准工时(h) × 工作中心人工费率 |
| overhead_cost | price | 制费 = Σ 工时 × 制费费率 |
| setup_cost | price | 准备成本分摊 = Σ 准备时间 × 费率 ÷ 数量 |
| tooling_cost | price | 模具/治具费分摊（手工：总金额 ÷ 分摊数量） |
| packing_freight_cost | price | 包装运输费（手工） |
| admin_rate | pct | 管理费率（默认 5%） |
| profit_rate | pct | 利润率（默认 15%） |
| total_cost | price | 合计成本 = (材料 + 人工 + 制费 + 准备 + 模具 + 包装运输) × (1 + 管理费率) |
| suggested_price | price | 建议售价（本位币）= 总成本 × (1 + 利润率)；另按 RFQ 币别汇率换算 |
| detail | json | 材料明细（子件、用量、单价、来源）和工序明细，用于展示 |

### sal_quotation 报价单（BaseDocDO，编码规则 SAL_QUOTATION）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| customer_id | id | 是 | |
| contact_id | id | 否 | |
| rfq_id | id | 否 | |
| opportunity_id | id | 否 | |
| currency / exchange_rate | | 是 | |
| trade_term | dict(sys_trade_term) | 否 | |
| payment_term_id | id | 否 | 默认客户付款条件 |
| tax_included | bool | 是 | 外销 0、内销 1 |
| valid_until | date | 是 | 有效期（默认单据日期 + 参数天数） |
| revision | int | 是 | 修订版本，从 0 开始 |
| parent_quotation_id | id | 否 | 修订前的报价单 |
| terms | str(2000) | 否 | 报价条款（打印在报价单上） |
| quote_status | enum(DRAFT/PENDING/APPROVED/SENT/WON/LOST/EXPIRED/REVISED) | 是 | 草稿 / 审批中 / 已审核 / 已发送 / 已成交 / 未成交 / 已过期 / 已修订 |
| lost_reason | dict(sal_quote_lost_reason) | 否 | |
| total_amount | amt | 否 | 按每行第一档数量计算的参考金额 |
| min_margin_rate | pct | 否 | 各行最低毛利率 |
| below_floor | bool | 是 | 是否有低于底价的行 |

### sal_quotation_line

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| line_no | int | 是 | |
| material_id | id | 是 | |
| customer_part_no | str(64) | 否 | |
| description | str(512) | 否 | 对外描述（默认物料英文名/名称 + 规格） |
| uom | str(16) | 是 | |
| min_qty | qty | 是 | 阶梯起始数量；同一物料多行表示多档 |
| price | price | 是 | 单价（按 tax_included） |
| tax_rate | pct | 是 | |
| cost_price | price | 否 | 单位成本（本位币）：成本核算或标准成本；字段权限 |
| margin_rate | pct | 否 | 毛利率 = (不含税单价 × 汇率 − 成本) ÷ (不含税单价 × 汇率) |
| moq | qty | 否 | |
| lead_time_days | int | 否 | 交期（天） |
| tooling_fee | amt | 否 | 一次性模具费 |
| rfq_line_id | id | 否 | |
| remark | str(256) | 否 | |

## 3. 页面

### 3.1 RFQ 列表（T1）

查询：单号、客户、业务员、工程师、状态（默认非关闭）、回复截止日期。
列：单号、客户、行数、回复截止日期（≤ 2 天橙色、已过期红色）、工程师、成本工程师、可行性（全部 OK 绿、有 NG 红、待评估灰）、状态、业务员、操作。

### 3.2 RFQ 编辑（T4）

单头：客户*（含潜在）、联系人、商机、币别*、贸易条款、回复截止日期*、备注、附件（图纸）。
明细：客户料号、需求描述*、本厂物料（MaterialSelect，可空）、年用量、数量阶梯*（标签输入，按回车添加）、目标价、要求交期、备注。
页头：[保存] [分派]（选择工程师、成本工程师 → 状态评估中，双方收到待办）。

### 3.3 RFQ 详情（T5 变体）

- 工程师操作（`sales:rfq:assign` 或被分派人）：每行填写可行性（OK/NG + 说明）、关联本厂物料（没有时 [建料] 跳转物料新建，保存后回填）。
- 成本工程师操作（`sales:rfq:cost`）：每行每个数量阶梯 [成本核算]，打开核算页（3.4）。全部行核算完成后状态 → 已核算，通知业务员。
- 业务员：[生成报价单]（已核算，或可行性 OK 的已有产品）→ 带入客户、行、阶梯和建议售价；[关闭]（原因）。

### 3.4 成本核算页（专用，字段权限 `sales:quotation:cost`）

上方：物料、数量阶梯选择、BOM 版本（默认版本；无 BOM 时提示“请工程先建立 BOM”）。
- **材料明细**：多级展开后的末级采购件：子件、累计用量、单价（来源标签：标准成本/最新采购价/手工，可修改）、金额；合计材料成本。
- **工序明细**：工序、工作中心、标准工时、费率、人工、制费。
- **其他费用**：准备分摊、模具费总额与分摊数量、包装运输费。
- **费率**：管理费率、利润率。
- **结果**：总成本、建议售价（本位币与 RFQ 币别）、与客户目标价比较（差异 %）。
[保存核算]。

### 3.5 报价单列表（T1）

查询：单号、客户、业务员、状态（默认草稿/审批中/已审核/已发送）、物料、有效期、单据日期。
列：单号（含版本，如 QT-202609-0001 R1）、客户、币别、参考金额、最低毛利率（字段权限，低于参数红色）、有效期（已过期红色）、状态、业务员、日期、操作。

### 3.6 报价单编辑（T4）

单头：客户*、联系人、RFQ（只读）、商机、币别*、汇率、贸易条款、付款条件、含税、有效期*、条款（文本域，默认模板条款）、备注。
明细：物料*、客户料号（自动从客户料号对照带出）、对外描述、单位、起始数量*、单价*（取价规则见 01-价格表）、税率、成本（字段权限，只读）、毛利率（只读，低于最低毛利率红色）、MOQ、交期（天）、模具费、备注。
页头：[保存] [提交]。

### 3.7 报价单详情（T5）

| 按钮 | 状态 | 权限 | 行为 |
|---|---|---|---|
| 编辑 / 删除 / 提交 | 草稿 | update / delete / submit | |
| 通过 / 驳回 / 撤回 | 审批中 | 审批流 | |
| 发送 | 已审核 | send | 打印/下载 PDF（英文或中文模板），记录发送时间 → 已发送 |
| 转订单 | 已审核、已发送 | to-order | 选择要下单的行和数量 → 生成草稿销售订单（R05）→ 已成交 |
| 修订 | 已审核、已发送、已过期 | revise | 复制为新报价单（revision + 1，parent 指向原单），原单 → 已修订 |
| 未成交 | 已审核、已发送 | lose | 原因* → 未成交 |
| 打印 | 全部（草稿带水印） | print | |

页签：明细、成本分析（字段权限：每行成本构成、毛利）、修订历史（各版本价格对比）、关联单据（RFQ、订单、商机）、审批记录、操作日志、附件。

## 4. 业务规则

| 编号 | 触发 | 规则 | 提示原文 |
|---|---|---|---|
| SAL-QT-R01 | 保存 | 客户状态为潜在、审批中或正式（`validateCanQuote`）；黑名单/停用不能报价 | `客户「{name}」已停用或在黑名单中，不能报价` |
| SAL-QT-R02 | 保存 | 同一物料的阶梯起始数量不重复，且有一档 = MOQ 或 0 | `物料「{code}」的阶梯数量重复` |
| SAL-QT-R03 | 提交 | 计算每行毛利率、底价（成本 × (1 + 最低毛利率)），填充 min_margin_rate、below_floor 供审批条件使用；成本缺失时按参数处理 | `物料「{code}」没有成本，无法计算毛利`（WARN） |
| SAL-QT-R04 | 定时 | 每天 00:10：有效期已过的已审核/已发送报价 → 已过期 | — |
| SAL-QT-R05 | 转订单 | 只有正式客户可以转订单；报价未过期；订单行单价取报价中对应数量阶梯的价格；订单记录来源报价 | `客户「{name}」不是正式客户，请先转为正式客户` |
| SAL-QT-R06 | 修订 | 被修订的原报价不能再转订单 | `该报价已修订，请使用最新版本` |
| SAL-QT-R07 | 事件 | 保存时发布 `QuotationCreatedEvent`（CRM 推进商机） | — |
| SAL-RFQ-R01 | 生成报价 | RFQ 的所有行可行性不为 NG（NG 的行不带入报价） | — |
| SAL-RFQ-R02 | 提醒 | 回复截止日期前 1 天仍未报价，提醒业务员和被分派人 | — |

## 5. 接口

`/sales/rfqs`（CRUD、assign、feasibility、close）、`/sales/rfqs/{id}/lines/{lineId}/cost-sheets`（GET/PUT 核算）、`/sales/rfqs/{id}/to-quotation`；`/sales/quotations`（CRUD、submit、send、revise、lose、to-order、print-data）。

## 6. 验收用例

| 编号 | 前置条件 | 操作 | 预期结果 |
|---|---|---|---|
| SAL-QT-T01 | RFQ 1 行，阶梯 1000/5000 | 分派 → 工程 OK 并关联物料 → 成本核算两个阶梯 | RFQ 状态已核算；业务员收到通知 |
| SAL-QT-T02 | 材料 5.00、人工 1.00、制费 0.50、管理 5%、利润 15% | 核算 | 总成本 6.825，建议售价 7.84875（本位币） |
| SAL-QT-T03 | 报价单价低于底价 | 提交 | below_floor=是，走主管审批分支 |
| SAL-QT-T04 | 报价已过期 | 转订单 | 没有转订单按钮；可修订 |
| SAL-QT-T05 | 修订 R0 → R1 | — | R0 状态已修订；R1 可转订单；修订历史显示价格变化 |
| SAL-QT-T06 | 潜在客户报价 | 转订单 | 提示“客户「…」不是正式客户，请先转为正式客户” |
| SAL-QT-T07 | 报价转订单 1200 个 | — | 订单单价取 1000 阶梯价格；报价状态已成交；商机赢单（订单审核后） |
