# 12 财务（总览）

## 1. 模块定位

财务负责业务财务一体化中的账务部分：应收、收款与核销、应付与采购发票、付款、凭证、成本核算、利润分析、月结。

- 业务模块只产生业务单据；财务**监听业务事件**生成应收/应付和凭证草稿，**不反向修改业务单据**（需要阻止业务撤销时，在业务撤销前的同步事件中抛出异常）。
- 范围为“业务财务 + 简版总账”。企业已使用金蝶/用友等总账软件时，可只用应收应付和成本，凭证导出到外部系统。

## 2. 业务来源与财务单据

| 业务事件 | 财务动作 |
|---|---|
| 出货确认 `ShipmentConfirmedEvent` | 生成应收单（按出货单，本位币按出货汇率） |
| 出货反确认 `ShipmentReversedEvent` | 作废对应应收单（未核销时） |
| 销售退货入库（退款）`SalesReturnReceivedEvent` | 生成红字应收 |
| 客诉赔偿 `ComplaintClaimAgreedEvent` | 生成应收折让（红字其他应收） |
| 供应商对账确认 `PurchaseStatementConfirmedEvent` | 生成应付单（含退货负数、扣款） |
| 库存月结 `PeriodClosedEvent` | 允许成本计算 |
| 生产订单关闭、领料、报工工时 | 成本计算的数据来源 |

## 3. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 财务基础设置 | [01-财务基础设置](01-财务基础设置.md) | 会计科目、会计期间、银行账户、科目映射 | T6 / T1 | `/finance/setting` | `fin:setting:query` | P0 |
| 应收 | [02-应收](02-应收.md) | 应收单、销项发票登记 | T1 / T5 | `/finance/receivable` | `fin:receivable:query` | P0 |
| 收款与核销 | [03-收款与核销](03-收款与核销.md) | 收款单、核销 | T1 / T4 / 专用 | `/finance/receipt` | `fin:receipt:query` | P0 |
| 应付与发票 | [04-应付与发票](04-应付与发票.md) | 应付单、进项发票（三单匹配） | T1 / T5 / T4 | `/finance/payable` | `fin:payable:query` | P0 |
| 付款 | [05-付款](05-付款.md) | 付款申请、付款单、核销 | T1 / T4 / T5 | `/finance/payment` | `fin:payment:query` | P0 |
| 凭证 | [06-凭证](06-凭证.md) | 凭证列表 / 编辑、凭证生成 | T1 / T4 | `/finance/voucher` | `fin:voucher:query` | P1 |
| 成本核算 | [07-成本核算](07-成本核算.md) | 成本计算、产品成本表 | 专用 / T7 | `/finance/cost` | `fin:cost:query` | P1 |
| 利润与报表 | [08-利润与报表](08-利润与报表.md) | 账龄、对账单、毛利、损益简表 | T7 | `/finance/report` | `fin:report:query` | P0 / P1 |
| 月结 | [09-月结](09-月结.md) | 期末处理与结账 | 专用 | `/finance/close` | `fin:close:query` | P1 |

菜单顺序：应收、收款、应付、付款、凭证、成本、利润与报表、月结、财务设置。

## 4. 用户角色

| 角色 | 功能 |
|---|---|
| 应收会计 | 应收确认、开票登记、收款核销、对账 |
| 应付会计 | 应付确认、进项发票、付款申请核对 |
| 出纳 | 收款登记、付款执行 |
| 成本会计 | 成本计算、成本分析 |
| 财务主管 | 审核、凭证过账、月结、报表 |

## 5. 数据表

fin_account、fin_period、fin_bank_account、fin_account_mapping、fin_receivable、fin_receivable_line、fin_sales_invoice、fin_receipt、fin_receipt_allocation、fin_payable、fin_payable_line、fin_purchase_invoice、fin_purchase_invoice_line、fin_payment_request、fin_payment_request_line、fin_payment、fin_verification、fin_voucher、fin_voucher_line、fin_cost_run、fin_cost_material、fin_cost_order、fin_cost_product、fin_fx_revaluation。

## 6. 编码规则

FIN_RECEIVABLE、FIN_RECEIPT、FIN_PAYABLE、FIN_PAYMENT_REQUEST、FIN_PAYMENT、FIN_VOUCHER；进项发票登记 FIN_PURCHASE_INVOICE（`PI-yyyyMM-4`）；销项发票登记 FIN_SALES_INVOICE（`SI-yyyyMM-4`）。

## 7. 可审批单据

| 单据类型 | 名称 | 条件字段 |
|---|---|---|
| FIN_OTHER_RECEIVABLE | 其他应收 | amountBase |
| FIN_OTHER_PAYABLE | 其他应付 | amountBase |
| FIN_PAYMENT_REQUEST | 付款申请 | amountBase、hasPrepayment（含预付款）、supplierLevel |

## 8. 系统参数

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| fin.ar.auto-confirm | 应收 | 应收单自动确认 | BOOL | 是 | 否：应收会计逐张确认 |
| fin.ap.invoice-price-tolerance | 应付 | 发票单价容差（%） | DECIMAL | 1 | 三单匹配单价差异容差 |
| fin.ap.invoice-amount-tolerance | 应付 | 发票金额尾差容差（元） | DECIMAL | 1 | |
| fin.voucher.auto-generate | 凭证 | 业务单据确认后自动生成凭证草稿 | BOOL | 否 | 否：月末批量生成 |
| fin.cost.labor-allocation | 成本 | 人工费用分配依据 | ENUM(WORK_HOURS/STD_HOURS/OUTPUT) | WORK_HOURS | 实际工时 / 标准工时 / 产量 |
| fin.cost.overhead-allocation | 成本 | 制造费用分配依据 | ENUM(WORK_HOURS/STD_HOURS/OUTPUT/MATERIAL) | WORK_HOURS | |
| fin.cost.wip-method | 成本 | 在制品计价 | ENUM(MATERIAL_ONLY/EQUIVALENT) | MATERIAL_ONLY | 在制品只计材料 / 约当产量法 |
| fin.base.company-bank | 单证 | Invoice 默认收款账户 | STRING | 空 | 银行账户编码 |

## 9. 对其他模块提供的 API（finance-api）

| 接口 | 方法 | 使用方 |
|---|---|---|
| `ReceivableQueryApi` | `getBalance(customerId)`、`getOverdue(customerId)`、`getOrderReceived(orderId)` | CRM（信用）、销售 |
| `PayableQueryApi` | `getBalance(supplierId)` | 资材 |
| `FinPeriodApi` | `isClosed(period)` | 仓库（反结账校验） |
| `CostQueryApi` | `getUnitCost(materialId, period)`、`getOrderCost(prodOrderId, period)` | 销售报表、BI |

**发布事件**：`ReceivableBalanceChangedEvent`（CRM 信用）、`ReceiptAllocatedEvent`（销售回款计划）、`InvoiceIssuedEvent`（销售已开票数量）、`CostCalculatedEvent`（仓库回填流水成本、BI）、`FinPeriodClosedEvent`。

**监听事件**：见第 2 节；另外 `StockOutReversingEvent`（销售出库：已开票/已核销阻止）、`PurchaseStatementUnconfirmingEvent`（已生成应付并已核销/已开票阻止）。

## 10. 权限点汇总

| 分组 | 权限点 |
|---|---|
| 设置 | `fin:setting:query`（菜单）、`fin:account:manage`、`fin:period:manage`、`fin:bank:manage`、`fin:mapping:manage` |
| 应收 | `fin:receivable:query`（菜单）、`confirm`、`unconfirm`、`create-other`、`invoice`（开票登记）、`export` |
| 收款 | `fin:receipt:query`（菜单）、`create`、`update`、`delete`、`confirm`、`unconfirm`、`verify`、`unverify` |
| 应付 | `fin:payable:query`（菜单）、`confirm`、`unconfirm`、`create-other`、`invoice`、`export` |
| 付款 | `fin:payment-request:query`、`create`、`submit`；`fin:payment:query`（菜单）、`create`、`confirm`、`verify` |
| 凭证 | `fin:voucher:query`（菜单）、`create`、`update`、`audit`、`post`、`unpost`、`export` |
| 成本 | `fin:cost:query`（菜单）、`calculate`、`lock` |
| 报表 | `fin:report:query`（菜单）、`export` |
| 月结 | `fin:close:query`（菜单）、`execute`、`reopen` |

## 11. 实现说明（第 1 批已实现）

第 1 批实现 P0：财务基础设置、应收与销项发票登记、收款与核销、应付与进项发票（三单匹配）、付款申请与付款、应收 / 应付账龄与客户 / 供应商往来对账单。凭证、成本核算、毛利与损益报表、月结在第 2 批实现（`CostQueryApi` 暂返回空，菜单显示“开发中”）。

- **状态**：应收单 / 应付单 `ar_status` / `ap_status`（草稿 → 待审批 → 已确认 / 已作废；事件生成的单据直接确认，其他应收 / 应付走审批流 `FIN_OTHER_RECEIVABLE` / `FIN_OTHER_PAYABLE`），收款单 / 付款单（草稿 → 已确认 / 已作废），付款申请 `request_status`（草稿 → 待审批 → 待付款 → 部分付款 → 已付款 / 已关闭 / 已作废）；状态变更均通过状态机。
- **应收来源**：监听 `ShipmentConfirmedEvent`（金额 0 的行不生成，按订单付款条件第一个出货类节点计算到期日，提单未到按出货日暂估，`BillOfLadingReceivedEvent` 后重算；无付款条件按客户信用天数）、`ShipmentReversedEvent`（作废）、`SalesReturnReceivedEvent`（仅 REFUND 生成红字，按退货入库单幂等，入库反确认作废）、`ComplaintClaimAgreedEvent`（折让红字）。幂等：同一来源（及入库单）只有一张未作废应收，出货行另有唯一键 `(ar_type, source_line_key)`，作废时释放。参数 `fin.ar.auto-confirm` 为是时自动确认（期间已结账或汇率未维护时保留草稿）。
- **汇率缺失**：事件生成的应收 / 应付在汇率未维护时汇率记 0、保留草稿，不阻断业务；确认时按业务日期重新取汇率并计算本位币。
- **阻止业务撤销**：`StockDocEvent(OUT_REVERSING)` 来源为出货单且应收已核销 / 已开票时抛出“该出货已开票/已收款核销，不能反确认”；`PurchaseStatementUnconfirmingEvent` 在应付已匹配发票 / 已申请 / 已付款时抛出“财务已根据此对账单生成应付并已处理，不能取消确认”，否则作废应付。
- **核销**：`fin_verification` 统一记录。贷方（收款、预收、红字应收 / 付款、预付、红字应付）与借方（蓝字应收 / 应付、退款）合计相等才能核销；配对顺序：退款先冲红字再冲收款，蓝字单据依次用预收（同订单，参数 `fin.ar.advance-any-order` 可放开）、收款、红字冲销。汇兑差异 = 贷方本位币 − 借方本位币。普通收款核销蓝字应收时按应收明细的订单占比回写 `SalesOrderWritebackApi.onReceiptAllocated` 并发布 `ReceiptAllocatedEvent`；预收款在收款确认时即回写，冲销时不再回写。反核销要求核销所在期间未结账。收款未核销金额 = 到账 + 手续费 − 已核销（退款为负数，不含手续费）。
- **销项发票**：按应收行部分开票（数量比例计算金额，可调整尾差 ≤ 1 元），回写 `SalesOrderWritebackApi.onInvoiced` 并发布 `InvoiceIssuedEvent`；作废 / 红冲回退。同一发票号码（未作废）不能重复登记。
- **应付与三单匹配**：监听 `PurchaseStatementConfirmedEvent` 生成应付（全部为加工费行时为委外类型；参数 `fin.ap.auto-confirm` 默认否），到期日按供应商付款条件（月结：区间结束月末 + 天数）。进项发票行记录冲减应付行的金额 `ap_amount`；单价差异 % 超过 `fin.ap.invoice-price-tolerance` 必须填原因、状态“有差异”，确认差异后在应付上追加 `PRICE_DIFF` 价差调整行；明细合计与发票价税合计差异 ≤ `fin.ap.invoice-amount-tolerance` 时调整最后一行，超过不允许保存。已认证的专票不能作废。
- **付款**：申请提交时占用应付 `requested_amount`（驳回 / 撤回 / 关闭释放未付部分）；可申请金额 = 价税合计 − 已付 − 已申请。未收齐发票的应付按参数 `fin.ap.allow-uninvoiced-request` 提示或阻止。预付款关联采购订单，累计 ≤ 订单价税合计。付款确认按申请行顺序分配并自动生成 `PAYMENT_AP` 核销；预付款付款记为预付余额，在付款核销页面冲应付。付款反确认自动反核销并恢复申请与应付占用；预付已用于冲销时需先反核销。定时任务 `FIN_AP_DUE_WEEKLY`（每周一 08:50）推送本周到期应付。
- **信用与扩展点**：实现 CRM `CreditUsageProvider`（应收余额 = 未核销应收 − 未核销预收，逾期 = 到期日早于今天的未核销蓝字应收，本位币），应收余额变化时调用 `CreditApi.refresh` 并发布 `ReceivableBalanceChangedEvent`；实现仓库 `FinancePeriodChecker` 与 `FinPeriodApi`（期间状态 CLOSED 视为已结账，未初始化的期间视为开启）；`ReceivableQueryApi.getOrderReceived` = 订单预收 + 普通收款核销到含该订单应收的金额。
- **报表**：账龄按到期日分段（无到期日按业务日期），本位币按截止日汇率折算，可下钻单据；往来对账单期末 = 期初 + 本期应收（应付）− 本期收款（付款，含预收），截止日不早于今天时与单据余额比对，不一致标红；客户对账单可按打印模板 `FIN_CUSTOMER_STATEMENT` 打印。
- **其他模块契约新增**：`SupplierApi.getFinanceInfo`、`PurchaseQueryApi.getOrderHeader / getOpenOrders`（见 07-资材 README）。
- **限制**：科目映射的“测试预览凭证”与凭证生成在第 2 批；银行流水导入按付款方名称与客户名称 / 简称 / 英文名完全一致匹配；收付款列表的数据范围按经办人及部门。
