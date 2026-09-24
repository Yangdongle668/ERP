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
