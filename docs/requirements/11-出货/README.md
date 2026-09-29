# 11 出货（总览）

## 1. 模块定位

出货负责把成品从仓库交付给客户：出货通知 → 拣货 → 装箱 → OQC → 出货单（出库）→ 单证（Packing List、Commercial Invoice、报关资料）→ 物流跟踪（提单、签收）。

边界：
- 出货数量来源于销售订单（出货只能针对已审核订单行）；
- 实际库存扣减由仓库确认“销售出库单”完成；
- OQC 判定归品质；
- 应收由财务监听出货确认事件生成。

## 2. 端到端流程

```mermaid
flowchart LR
  SO[销售订单行] --> SN[出货通知]
  SP[PMC 出货计划] --> SN
  SN -->|审核| PK[拣货单]
  PK -->|拣货完成| PA[装箱]
  PA -->|需要 OQC| OQC[品质 OQC]
  PA -->|免检| SH[出货单]
  OQC -->|合格| SH
  SH -->|提交| OUT[仓库 销售出库单] -->|确认出库| SHIPPED[已出货]
  SHIPPED --> EV[ShipmentConfirmedEvent: 销售回写 / 财务应收 / 回款计划]
  SH --> DOC[Packing List / Invoice / 报关]
  SHIPPED --> LOG[物流: 提单 / 签收]
```

## 3. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 出货通知 | [01-出货通知](01-出货通知.md) | 列表 / 编辑 / 详情 | T1 / T4 / T5 | `/shipping/notice` | `shp:notice:query` | P0 |
| 拣货与装箱 | [02-拣货与装箱](02-拣货与装箱.md) | 拣货单、装箱 | T1 / 专用 | `/shipping/picking`、`/shipping/packing` | `shp:picking:query`、`shp:packing:query` | P0 |
| 出货单 | [03-出货单](03-出货单.md) | 列表 / 编辑 / 详情 | T1 / T4 / T5 | `/shipping/shipment` | `shp:shipment:query` | P0 |
| 出货单证 | [04-出货单证](04-出货单证.md) | Packing List、Invoice、报关资料 | T1 / T4 | `/shipping/packing-list`、`/shipping/invoice`、`/shipping/customs` | `shp:document:query` | P0 / P1 |
| 物流跟踪 | [05-物流跟踪](05-物流跟踪.md) | 物流跟踪、货代 | T1 | `/shipping/logistics`、`/shipping/forwarder` | `shp:logistics:query` | P1 |
| 出货报表 | [06-出货报表](06-出货报表.md) | 出货明细、待出货、准时率 | T7 | `/shipping/report` | `shp:report:query` | P1 |

菜单顺序：出货通知、拣货单、装箱、出货单、Packing List、Invoice、报关、物流跟踪、货代、出货报表。

## 4. 用户角色

| 角色 | 功能 | 数据范围 |
|---|---|---|
| 船务 / 单证员 | 出货通知、出货单、单证、订舱、物流 | 全部或本部门 |
| 仓管员 | 拣货、装箱、确认出库 | 按仓库 |
| 业务员 | 查看自己订单的出货、单证 | 仅本人（按订单业务员） |

## 5. 数据表

shp_notice、shp_notice_line、shp_picking、shp_picking_line、shp_carton、shp_carton_line、shp_shipment、shp_shipment_line、shp_packing_list、shp_invoice、shp_invoice_line、shp_customs、shp_customs_item、shp_forwarder、shp_logistics_event。

## 6. 编码规则

SHP_NOTICE、SHP_PICKING、SHP_PACKING_LIST（允许手工）、SHP_INVOICE（允许手工）、SHP_SHIPMENT；报关资料 SHP_CUSTOMS（`CD-yyyyMM-4`）。

## 7. 内置字典

| 类型编码 | 名称 | 内置项 |
|---|---|---|
| shp_transport_mode | 运输方式 | SEA 海运、AIR 空运、EXPRESS 快递、LAND 陆运、RAIL 铁路 |
| shp_container_type | 柜型 | 20GP、40GP、40HQ、LCL 拼箱 |
| shp_carton_spec | 常用外箱规格 | 无内置项（管理员维护：名称 + 长宽高 cm + 皮重 kg） |
| shp_logistics_status | 物流状态 | BOOKED 已订舱、LOADED 已装柜、DEPARTED 已离港、ARRIVED 已到港、CLEARED 已清关、DELIVERED 已签收 |

## 8. 可审批单据

| 单据类型 | 名称 | 条件字段 |
|---|---|---|
| SHP_NOTICE | 出货通知 | amountBase、creditWarning、prepaymentUnpaid（出货前款项未收齐，布尔） |
| SHP_SHIPMENT | 出货单（放行） | amountBase、creditWarning、prepaymentUnpaid |

## 9. 可打印单据

出货通知（中文）、拣货单（中文，按库位排序）、箱唛标签（中/英，每箱一张）、Packing List（英文）、Commercial Invoice（英文）、出货单/送货单（中文，客户签收联）、报关资料（中文）。

## 10. 系统参数

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| shp.notice.credit-check | 出货 | 出货通知提交时检查信用 | BOOL | 是 | 使用 CRM 信用规则 |
| shp.shipment.prepayment-check | 出货 | 出货前款项未收齐时 | ENUM(NONE/WARN/BLOCK) | WARN | 检查销售回款计划中“出货前”节点 |
| shp.oqc.required-default | OQC | 未设置物料 OQC 属性时默认需要 OQC | BOOL | 否 | 以物料质量属性“出货检验”为准 |
| shp.picking.enabled | 拣货 | 启用拣货单 | BOOL | 是 | 否：出货单直接生成出库单，由仓管员确认时分配批次 |
| shp.packing.enabled | 装箱 | 启用装箱 | BOOL | 是 | 否：Packing List 按出货单行简单生成（不记录箱号） |

## 11. 对其他模块提供的 API（shipping-api）

| 接口 | 方法 | 使用方 |
|---|---|---|
| `ShipmentQueryApi` | `getShippedLines(customerId, filter)`（退货选单）、`getShipmentsByBatch(materialId, batchNo)`（追溯）、`getShipmentsByOrder(orderId)` | 销售、生产追溯、品质、财务 |

**发布事件**：`ShipmentNoticeChangedEvent`（已通知数量）、`OqcRequestEvent`、`ShipmentConfirmedEvent`（订单行、数量、金额、批次、出货日期）、`ShipmentReversedEvent`、`BillOfLadingReceivedEvent`、`ShipmentSignedEvent`。

**监听事件**：仓库 `StockOutConfirmedEvent` / `StockOutReversingEvent` / `StockOutReversedEvent`（销售出库）；品质 `InspectionJudgedEvent`（OQC）；PMC `ShippingPlanPublishedEvent`；CRM `CustomerStatusChangedEvent`（黑名单拦截）。

## 12. 权限点汇总

| 分组 | 权限点 |
|---|---|
| 出货通知 | `shp:notice:query`（菜单）、`create`、`update`、`delete`、`submit`、`unapprove`、`close`、`print` |
| 拣货 | `shp:picking:query`（菜单）、`pick`（录入拣货）、`print` |
| 装箱 | `shp:packing:query`（菜单）、`pack`、`print-label` |
| 出货单 | `shp:shipment:query`（菜单）、`create`、`update`、`delete`、`submit`、`withdraw`、`print`、`export` |
| 单证 | `shp:document:query`（菜单）、`create`、`update`、`print`；字段 `shp:document:price`（Invoice 价格，默认与 `sales:order:query` 同时授予） |
| 物流 | `shp:logistics:query`（菜单）、`update`、`shp:forwarder:manage` |
| 报表 | `shp:report:query`（菜单）、`export` |

## 13. 实现说明（已实现）

出货通知、拣货、装箱与 OQC、出货单、出货单证（Packing List / Invoice / 报关资料）、物流跟踪与货代、出货报表的后端与页面均已实现。财务模块尚未实现，出货按下列方式接入：

- **状态**：出货通知 `notice_status`（草稿 → 待审批 → 已审核 → 拣货中 → 已装箱 →〔待 OQC → 待出货〕→ 已出货 / 已关闭），出货单 `shipment_status`（草稿 → 待审批 → 待出库 → 已出货 → 已完成 / 已作废），拣货单 `picking_status`；`BaseDocDO.status` 随之映射为通用单据状态。
- **订单占用**：出货通知保存即通过 `SalesOrderWritebackApi.onNoticeChanged` 回写订单已通知数量（修改时先释放后占用），同时发布 `ShipmentNoticeChangedEvent`；从出货计划生成时回写 `ShippingPlanApi.onNoticed`。删除草稿、关闭通知、拣货“按实拣完成”的缺货都会释放回订单。通知数量为订单单位，拣货、装箱、出货内部按基本单位。
- **信用与款项**：提交通知时按参数做 CRM 信用检查（BLOCK 阻止、WARN 记 `credit_warning`），检查订单“出货前”未收款（`SalesOrderQueryApi.getUnpaidBeforeShipment`）记 `prepayment_unpaid`，二者与 `amountBase` 作为审批条件。出货单提交时再次检查信用；出货前款项按参数 NONE / WARN / BLOCK。
- **拣货**：审核后按 `InventoryQueryApi.suggestBatches`（FIFO/FEFO）推荐批次与库位，库存不足的部分为“缺货”行。录入实拣时校验批次在出货仓的可用量（`ReservationApi` 未上线，不做预留）。未启用拣货（`shp.picking.enabled`=否）时审核后直接“已装箱”，出货单不带批次、由仓管员确认出库时分配。
- **装箱**：按“通知行 + 批次”核对实拣与装箱数量；已完成装箱 / 已申请 OQC 后修改装箱会退回“拣货中”并取消未完成的 OQC。未启用装箱时拣货完成即“已装箱”，Packing List 按出货单行生成。库位在打印中以库位 ID 表示（仓库未提供库位查询接口）。
- **OQC**：物料质量属性“出货检验”或参数 `shp.oqc.required-default` 决定通知行是否需要 OQC。申请 OQC 调用 `InspectionApi.requestOqc`（每个通知行 + 批次一张，本轮检验单 ID 记在 `oqc_inspection_ids`），监听 `InspectionJudgedEvent`：本轮全部判定后合格 / 特采 → 待出货，任一拒收 → 退回“已装箱”并提醒船务。出货单提交时需要 OQC 的行要求 `oqc_result = PASSED`。
- **出货单**：从通知按箱生成（默认全部未出货箱，箱记录 `shipment_id`，一张通知可分多张出货单）；汇率取出货日期汇率（`CurrencyApi.getRate`，需先维护汇率）。提交 → `InventoryDocApi.createStockOut(SALES_OUT)`（来源 `SHP_SHIPMENT`，行 = 出货单行）；监听 `StockOutConfirmedEvent` → 已出货，回写出库数量、通知已出货、`SalesOrderWritebackApi.onShipped`，发布 `ShipmentConfirmedEvent`；监听 `StockDocEvent`：`OUT_REVERSED` → 回到待出库并冲回（`onShipmentReversed`、`ShipmentReversedEvent`、单证失效），`REJECTED` → 回到草稿。财务上线后在 `OUT_REVERSING` 中阻止已开票 / 核销的反确认。
- **物流**：登记提单日期（不早于出货日期）时回写 `onBillOfLading` 并发布 `BillOfLadingReceivedEvent`，外销客户以提单为完成；物流状态不可倒退（倒退需说明），“已签收”或登记签收 → 已完成并发布 `ShipmentSignedEvent`。定时任务 `SHP_ETA_OVERDUE`（每天 08:20）提醒 ETA 已过 3 天未到港的出货。
- **单证**：Invoice 英文大写金额按币别生成（如 “SAY US DOLLARS TWELVE THOUSAND FIVE HUNDRED ONLY”），收款银行取参数 `sal.print.bank-info`；报关资料按物料 HS 编码合并，申报要素给出模板默认值。字段权限 `shp:document:price` 控制通知、出货单、单证、报表中的价格与金额（无权限显示 `***`）。
- **对外查询**：`ShipmentQueryApi.getShippedLines / getShipmentsByBatch / getShipmentsByOrder`（只返回已出货 / 已完成）。
- **其他模块契约新增**：`SalesOrderQueryApi.getOrderHeaders`（订单头：客户 PO、付款条件、收货 / 开票地址、贸易条款、港口）。销售出货回写支持“反确认后再次出库”。
- **限制**：拣货扫码仅支持扫描批次号；准时率的延期原因暂未关联 PMC 交期预警；出货通知、出货单列表的数据范围按经办人及部门，业务员按订单查看出货需通过订单详情；报关资料未上线申报要素模板维护。
