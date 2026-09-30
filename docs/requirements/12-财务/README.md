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

## 11. 实现说明（第 1 批）

第 1 批实现 P0：财务基础设置、应收与销项发票登记、收款与核销、应付与进项发票（三单匹配）、付款申请与付款、应收 / 应付账龄与客户 / 供应商往来对账单。凭证、成本核算、毛利与损益报表、月结在第 2 批实现（见第 12 节）。

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
- **限制**：银行流水导入按付款方名称与客户名称 / 简称 / 英文名完全一致匹配；收付款列表的数据范围按经办人及部门。

## 12. 实现说明（第 2 批）

第 2 批实现 P1：凭证（06）、成本核算（07）、收付款日报 / 毛利 / 损益 / 科目余额 / 明细账（08）、月结（09）。迁移脚本 `V2__finance_voucher_cost_close.sql`（凭证、成本结果、外币重估表，初始化常用科目映射）。

- **凭证**：凭证号 `记-yyyyMM-NNNN` 按期间自编（不走编码规则），删除为物理删除以便号码复用；状态 草稿 → 已审核 → 已过账（审核 / 反审核 / 过账 / 反过账均走状态机）。审核要求借贷相等且至少两行、审核人 ≠ 制单人；已结账期间不能新增、修改、审核、过账。整理凭证号按日期重排草稿与已审核凭证，已过账的不变。
- **生成**：按科目映射（条件映射按优先级匹配，否则默认映射）生成分录；金额为负时借贷互换；映射科目为 1002 时替换为收付款银行账户的科目；辅助核算取 `auxFrom` 或科目的辅助核算类型；外币核算科目记录原币与汇率。批量生成可“按单据”（每张一张凭证）或“按类型汇总”（同科目、方向、辅助项、币别合并）；成本类（销售成本、生产领料、完工入库）按最近一次成功计算的汇总金额生成一张凭证，凭证 ID 记在计算记录上。单据上记录 voucher_id，每张单据只生成一次，凭证删除后清除引用可重新生成。参数 `fin.voucher.auto-generate` 为是时，应收 / 应付 / 收款 / 付款确认后自动生成草稿（失败不阻断确认）。单据反确认时草稿凭证自动删除，已审核 / 过账的提示先反审核。汇兑损益：客户核销差异为正记收益（借 1122 贷 660301），供应商核销差异为正记损失（条件映射 `{"partnerType":"SUPPLIER"}`，借 660301 贷 2202）。映射测试接口 `GET /finance/account-mappings/preview?bizType=&docId=`。导出为通用 Excel（凭证 × 分录），金蝶 / 用友模板为 P2。
- **成本核算**：前置条件库存期间已月结、财务未结账、未锁定；同一期间同时只允许一个计算（超过 1 小时视为中断）；计算在独立事务中执行，失败回滚本次结果并记录 FAILED，保留上次成功结果；已生成成本凭证时需先删除凭证才能重算。算法：物料全公司统一月加权平均（期初取上期结存金额，没有时按流水推算）；采购 / 委外 / 其他入库 / 盘盈按流水单价加权，生产入库按本次计算的完工成本加权；退料、委外退回、销售退货按当期加权单价冲减出库（简化：销售退货未取原出库期间单价）；本期无入库沿用期初单价。领料按加权单价归集到订单；人工、制费按车间费用 × 订单工时占比分配（`fin.cost.labor-allocation` / `overhead-allocation` 为 OUTPUT 时按合格数量，STD_HOURS / MATERIAL 暂按工时）；已完工 / 已关闭订单承担全部成本，否则按 `fin.cost.wip-method`（MATERIAL_ONLY：在制只留材料；EQUIVALENT：完工程度 50%）；按低位码逐层计算，循环引用时记异常并按现有单价计算。异常：负数结存、单价波动超 30%、无单价出库、无单价入库、无工时有费用、循环。结果回填本期出库 / 生产入库流水单价（`InventoryCostApi.applyCosts`）并写入期末结存金额。锁定后发布 `CostCalculatedEvent`；期间已结账时不能解锁。产品成本表的“标准成本 / 差异”列待物料标准成本字段上线后提供（现显示 -）。
- **成本查询**：`CostQueryApi` 返回已成功计算期间的物料加权单价、订单完工成本；同时实现销售 `SalesCostProvider`（最近一个成功计算期间的实际单价，没有时销售按最新采购价兜底）。
- **报表**：收付款日报按日期、账户列示原币期初、收款（到账金额）、付款（金额 + 手续费）、期末；毛利收入 = 已确认出货 / 退货应收明细不含税本位币，成本 = 数量 × 应收业务期间的物料加权单价（数量按基本单位计），未计算的期间成本显示“未计算”并提示；订单毛利可按订单行 / 订单 / 客户 / 业务员汇总，产品、客户毛利按毛利排名。月度损益：收入取已确认应收不含税本位币，营业成本取成本计算的销售出库金额，费用取已过账凭证 6601 / 6602 / 6603 借方净发生额，列本月、本年累计、上年同期。科目余额表、明细账取已过账凭证（可勾选含未过账），上级科目按编码前缀汇总，余额以科目方向为正。
- **月结**：检查项（阻止）：上期已结账、库存已月结、成本已锁定、无草稿 / 待审批应收应付及草稿收付款、进项发票差异已确认、外币已重估（无外币余额跳过）、凭证已全部过账（本期无凭证视为通过）；待生成凭证的单据只提示。外币重估：外币应收 / 应付（已确认未核销部分，账面按单据汇率）与外币银行存款（收款到账 − 付款及手续费）按月末汇率（`RateType.MONTH_END`）重估，缺汇率时提示先维护；生成一张重估凭证草稿（应收 / 银行差异为正借资产贷 660301，应付差异为正借 660301 贷 2202，同科目同往来合并）；重新重估会删除原草稿凭证。结账：期间 CLOSED、下期开启，参数 `fin.close.fx-auto-reverse` 为是时在下月 1 日生成冲回凭证，发布 `FinPeriodClosedEvent(period, false)`。反结账：只能反结账最近一个已结账期间，原因必填，删除冲回凭证草稿、解锁成本，发布 `FinPeriodClosedEvent(period, true)`。
- **其他模块契约新增**：仓库 `InventoryCostApi`（见 08-仓库 README），生产 `ProductionCostApi`（见 09-生产 README）。
- **前端**：凭证列表 / 凭证编辑（经典凭证样式，借贷不平衡标红）、成本核算（状态检查、费用录入、计算记录、异常清单、产品成本表下钻订单成本、物料单价表）、月结（期间列表 + 四步向导）、财务报表新增 7 个页签。

