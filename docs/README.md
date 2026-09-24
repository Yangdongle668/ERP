# 文档目录

## 需求分析（字段级）

阅读顺序：先看《[需求编写约定](requirements/README.md)》和《[总体需求与通用规范](requirements/00-总体需求与通用规范.md)》，再看模块目录。每个模块目录的 `README.md` 是该模块的总览（页面清单、角色、数据表、编码规则、字典、审批、参数、对外 API、事件、权限点），其余文件每个对应一个功能点（数据表 → 页面 → 状态 → 规则 → 接口 → 验收用例）。

| 编号 | 模块 | 功能文档 |
|---|---|---|
| 00 | [总体需求与通用规范](requirements/00-总体需求与通用规范.md) | 单据结构、状态机、上下游回写、编码、审批、单位、币别、权限、审计、非功能 |
| 01 | [系统管理](requirements/01-系统管理/) | 组织架构、用户、角色与权限、数据字典、编码规则、计量单位、币别汇率、审批流、打印模板、系统参数、日志审计、附件与任务中心、登录与个人中心、付款条件与贸易基础数据 |
| 02 | [工作台](requirements/02-工作台/) | 首页看板、待办与审批、消息与公告、预警中心 |
| 03 | [CRM](requirements/03-CRM/) | 客户、客户料号对照、信用管理、跟进记录、商机 |
| 04 | [销售](requirements/04-销售/) | 价格表、RFQ 与报价、销售订单、订单变更、销售预测、销售退货、回款跟踪、销售报表 |
| 05 | [研发工程](requirements/05-研发工程/) | 物料类别、物料、BOM、工作中心与工艺路线、ECN、研发项目、样品、工装、认证 |
| 06 | [PMC](requirements/06-PMC/) | 需求池与交期回复、MPS、MRP 运算、MRP 建议处理、排产与产能、缺料分析、交期预警、出货计划 |
| 07 | [资材](requirements/07-资材/) | 供应商、采购价格、采购申请、询价比价、采购订单、到货、委外加工、采购退货、供应商对账、供应商评估、采购报表 |
| 08 | [仓库](requirements/08-仓库/) | 仓库与库位（10 类仓）、库存模型与过账、入库单、出库单、调拨单、盘点、批次与序列号、库存查询与报表、期初与月结 |
| 09 | [生产](requirements/09-生产/) | 生产订单、工单派工、领料与退料、报工、完工入库、不良与良率、生产追溯、生产报表 |
| 10 | [品质](requirements/10-品质/) | 检验基础数据（GB/T 2828.1 抽样）、检验单（IQC/IPQC/FQC/OQC/退货/复检）、NCR 与 MRB、CAPA、客诉、SCAR、质量追溯与报表 |
| 11 | [出货](requirements/11-出货/) | 出货通知、拣货与装箱、出货单、出货单证（PL/Invoice/报关）、物流跟踪、出货报表 |
| 12 | [财务](requirements/12-财务/) | 财务基础设置、应收、收款与核销、应付与发票、付款、凭证、成本核算、利润与报表、月结 |
| 13 | [BI / AI](requirements/13-BI与AI/) | 指标库与数据层、经营驾驶舱、专题分析、AI 分析 |

## 界面

- [UI 设计规范](ui/UI设计规范.md)：布局、8 类页面模板（T1～T8）、公共组件清单、交互与显示格式、权限表现、导入导出与打印

## 架构与开发

- [后端架构设计](architecture/后端架构设计.md)
- [并行开发指南](并行开发指南.md)

## 模块间主要事件（速查）

| 事件 | 发布方 | 主要监听方 |
|---|---|---|
| `StockInConfirmedEvent` / `StockOutConfirmedEvent` / `TransferConfirmedEvent` | 仓库 | 资材、生产、出货、销售、品质、财务 |
| `InspectionJudgedEvent` | 品质 | 仓库（检验调拨）、资材、生产、销售、出货 |
| `SalesOrderApprovedEvent` / `ChangedEvent` / `ClosedEvent` | 销售 | PMC、CRM |
| `ShipmentConfirmedEvent` / `ShipmentReversedEvent` | 出货 | 销售、财务、PMC、CRM、BI |
| `PurchaseStatementConfirmedEvent` | 资材 | 财务 |
| `ReceiptAllocatedEvent` / `ReceivableBalanceChangedEvent` | 财务 | 销售、CRM |
| `ApprovalCompletedEvent` | 系统管理 | 所有有审批的模块 |
| `TodoCreatedEvent` / `MessageSendEvent` / `AlertRaisedEvent` | 所有模块（经 system-api `NotifyApi`） | 工作台 |
| `PeriodClosedEvent`（库存）/ `CostCalculatedEvent` | 仓库 / 财务 | 财务 / 仓库、BI |
