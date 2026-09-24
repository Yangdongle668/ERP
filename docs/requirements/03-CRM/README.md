# 03 CRM（总览）

## 1. 模块定位

CRM 是**客户主数据**的唯一维护方，并管理售前过程（跟进记录、商机）和客户信用额度。销售、出货、财务、品质（客诉）只引用客户。

## 2. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 客户 | [01-客户](01-客户.md) | 客户列表 | T1 | `/crm/customer` | `crm:customer:query` | P0 |
| | | 新建/编辑客户 | T3 | `/crm/customer/new`、`/:id/edit` | create / update | P0 |
| | | 客户详情（360 视图） | T5 | `/crm/customer/:id` | query | P0 |
| 联系人 | 01-客户（子表） | 联系人查询 | T1 | `/crm/contact` | `crm:customer:query` | P0 |
| 客户料号对照 | [02-客户料号对照](02-客户料号对照.md) | 客户料号 | T1 + T2 | `/crm/customer-part` | `crm:customer-part:query` | P1 |
| 信用管理 | [03-信用管理](03-信用管理.md) | 客户信用 | T1 | `/crm/credit` | `crm:credit:query` | P1 |
| 跟进记录 | [04-跟进记录](04-跟进记录.md) | 跟进记录 | T1 + T2 | `/crm/followup` | `crm:followup:query` | P1 |
| 商机 | [05-商机](05-商机.md) | 商机列表 / 详情 | T1 / 抽屉 | `/crm/opportunity` | `crm:opportunity:query` | P1 |

菜单顺序：客户、联系人、客户料号、客户信用、跟进记录、商机。

## 3. 用户角色与数据权限

| 角色 | 功能 | 数据范围建议 |
|---|---|---|
| 业务员 | 维护自己负责的客户、联系人、跟进、商机 | 仅本人（按客户负责人 owner_id） |
| 业务主管 | 查看团队客户、转移客户、审批转正式客户 | 本部门及下级（按客户所属部门 dept_id） |
| 财务 | 维护信用额度 | 全部 |

客户数据范围字段：`owner_id`（负责业务员）、`dept_id`（负责部门，默认为负责人的主部门）。

## 4. 数据表

crm_customer、crm_contact、crm_address、crm_customer_bank、crm_customer_part、crm_customer_credit、crm_credit_change_log、crm_followup、crm_opportunity、crm_customer_transfer_log。

## 5. 编码规则

CRM_CUSTOMER（C + 5 位，允许手工）、CRM_OPPORTUNITY。

## 6. 内置字典

| 类型编码 | 名称 | 内置项 |
|---|---|---|
| crm_customer_type | 客户类型 | END_USER 终端客户、TRADER 贸易商、AGENT 代理商、OEM 品牌商 |
| crm_customer_level | 客户等级 | A、B、C、D（标签 A 级重点客户…D 级风险客户） |
| crm_industry | 行业 | 无内置项 |
| crm_source | 客户来源 | EXHIBITION 展会、WEBSITE 官网、B2B 平台、REFERRAL 转介绍、COLD_CALL 陌生开发、OTHER 其他 |
| crm_contact_role | 联系人角色 | PURCHASE 采购、ENGINEERING 工程、QUALITY 品质、FINANCE 财务、LOGISTICS 物流、MANAGEMENT 管理层、OTHER 其他 |
| crm_followup_type | 跟进方式 | VISIT 拜访、PHONE 电话、EMAIL 邮件、EXHIBITION 展会、VIDEO 视频会议、OTHER 其他 |
| crm_lost_reason | 输单原因 | PRICE 价格、DELIVERY 交期、QUALITY 质量、COMPETITOR 竞争对手、PROJECT_CANCELED 项目取消、OTHER 其他 |

## 7. 可审批单据

| 单据类型 | 名称 | 条件字段 | 用户字段 |
|---|---|---|---|
| CRM_CUSTOMER_ACTIVATE | 客户转正式 | customerType、country、creditLimitBase（信用额度，本位币） | ownerId 负责业务员 |
| CRM_CREDIT_CHANGE | 信用额度调整 | newLimitBase、increaseBase（增加额） | ownerId |

## 8. 系统参数

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| crm.customer.duplicate-check | 客户 | 客户查重方式 | ENUM(OFF/WARN/BLOCK) | WARN | |
| crm.credit.control-mode | 信用 | 信用控制方式 | ENUM(NONE/WARN/BLOCK) | WARN | 客户未单独设置时使用 |
| crm.credit.check-points | 信用 | 信用检查时点 | STRING | ORDER,SHIPMENT | 逗号分隔：ORDER 销售订单审核、SHIPMENT 出货单提交 |
| crm.followup.remind-time | 跟进 | 跟进提醒时间 | TIME | 09:00 | |

## 9. 对其他模块提供的 API（crm-api）

| 接口 | 方法 | 使用方 |
|---|---|---|
| `CustomerApi` | `getCustomer`、`getCustomers(ids)`、`validateCanOrder`（已定义）、`validateCanQuote(id)`（潜在客户也可）、`getAddresses(id, type)`、`getDefaultAddress(id, type)`、`getContacts(id)`、`search(keyword, statuses)` | 销售、出货、财务、品质 |
| `CustomerPartApi` | `toMaterial(customerId, customerPartNo)`、`toCustomerPart(customerId, materialId)` | 销售、出货 |
| `CreditApi` | `check(customerId, newAmountBase, checkPoint)` → `{pass, mode, limit, used, available, message}` | 销售、出货 |

**发布事件**：`CustomerStatusChangedEvent`、`CustomerOwnerChangedEvent`。
**监听事件**：财务 `ReceivableBalanceChangedEvent`、销售 `SalesOrderOpenAmountChangedEvent`（更新信用占用）；销售 `QuotationCreatedEvent`、`SalesOrderApprovedEvent`（商机阶段推进）。

## 10. 权限点汇总

| 分组 | 权限点 |
|---|---|
| 客户 | `crm:customer:query`（菜单）、`create`、`update`、`activate`（转正式）、`disable`、`blacklist`、`transfer`、`delete`、`import`、`export` |
| 客户料号 | `crm:customer-part:query`（菜单）、`create`、`update`、`delete`、`import` |
| 信用 | `crm:credit:query`（菜单）、`update`（调整额度）；字段 `crm:customer:credit`（在客户页面查看信用字段） |
| 跟进 | `crm:followup:query`（菜单）、`create`、`update`、`delete` |
| 商机 | `crm:opportunity:query`（菜单）、`create`、`update`、`delete`、`close`（赢单/输单） |
