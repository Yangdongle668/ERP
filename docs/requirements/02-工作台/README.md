# 02 工作台（总览）

## 1. 模块定位

工作台是用户登录后的首页，聚合“需要我处理的事”（待办、审批）和“我需要关注的数”（角色看板、预警），以及消息和公告。工作台**不产生业务数据**。

### 1.1 事件契约（定义在 system-api 的 `notify` 包）

待办、消息、预警由各业务模块产生，工作台统一存储和展示。为了保持依赖方向（业务模块 → 平台），事件类定义在 **system-api**（所有模块都已依赖），工作台监听：

| 事件 | 字段 | 说明 |
|---|---|---|
| `TodoCreatedEvent` | `todoKey`（业务唯一键，如 `WF_TASK:123`、`IQC:456`）、`userIds`、`category`（APPROVAL/TASK）、`bizType`、`bizId`、`bizNo`、`title`、`route`、`priority`（HIGH/NORMAL/LOW）、`dueTime` | 为一个或多个用户创建待办 |
| `TodoDoneEvent` | `todoKey`、`userIds`（空表示所有人）、`result`（DONE/CANCELED） | 完成/取消待办 |
| `MessageSendEvent` | `userIds`、`msgType`（NOTICE/APPROVAL_RESULT/TASK_DONE/REMIND/SYSTEM）、`title`、`content`、`route`、`sendEmail` | 站内消息（可同时发邮件） |
| `AlertRaisedEvent` | `alertKey`（如 `STOCK_LOW:materialId`）、`alertType`、`level`（INFO/WARNING/CRITICAL）、`userIds` 或 `permission`（拥有该权限的用户）、`bizType`、`bizId`、`title`、`content`、`route` | 预警（同 alertKey 未处理时更新而不是新增） |
| `AlertResolvedEvent` | `alertKey` | 条件消除，自动关闭预警 |

业务模块使用 `NotifyApi`（system-api，内部即发布上述事件）以减少样板代码：`notifyApi.todo(...)`、`notifyApi.done(...)`、`notifyApi.message(...)`、`notifyApi.alert(...)`。

## 2. 功能与页面清单

| 功能 | 文档 | 页面 | 路由 | 权限 | 优先级 |
|---|---|---|---|---|---|
| 首页 | [01-首页](01-首页.md) | 工作台首页（卡片布局） | `/workbench/home` | 登录即可 | P0 |
| 待办与审批 | [02-待办与审批](02-待办与审批.md) | 我的待办、我已处理、我发起的 | `/workbench/todo` | 登录即可 | P0 |
| 消息与公告 | [03-消息与公告](03-消息与公告.md) | 消息中心、公告管理 | `/workbench/message`、`/workbench/notice` | 登录即可 / `wb:notice:manage` | P0 / P1 |
| 预警中心 | [04-预警中心](04-预警中心.md) | 预警中心 | `/workbench/alert` | 登录即可（只看给自己的） | P1 |

## 3. 数据表

wb_todo、wb_message、wb_alert、wb_alert_user、wb_notice、wb_notice_read、wb_layout、wb_shortcut。

## 4. 系统参数

| 编码 | 分组 | 名称 | 类型 | 默认 | 说明 |
|---|---|---|---|---|---|
| wb.todo.poll-seconds | 待办 | 待办角标刷新间隔（秒） | INT(30～600) | 60 | |
| wb.message.retention-days | 消息 | 消息保留天数 | INT | 180 | |
| wb.todo.retention-days | 待办 | 已处理待办保留天数 | INT | 365 | |
| wb.email.enabled | 邮件 | 启用邮件通知 | BOOL | 否 | 需先配置 SMTP（`spring.mail.*`） |
| wb.dashboard.refresh-minutes | 看板 | 看板数据缓存（分钟） | INT | 15 | |

## 5. 权限点

`wb:notice:manage`（公告管理）、`wb:alert:handle`（处理预警，默认所有用户都可处理给自己的预警）。其余功能登录即可使用。看板卡片按卡片所需的业务权限显示（如“应收逾期”卡片需要 `fin:receivable:query`）。

## 6. 实现说明（已实现）

- **数据表**：迁移脚本 `V1__workbench.sql`（wb_todo、wb_message、wb_alert、wb_alert_user、wb_notice、wb_notice_read、wb_layout、wb_shortcut）。
- **事件存储**：`TodoCreatedEvent` / `TodoDoneEvent` / `MessageSendEvent` / `AlertRaisedEvent` / `AlertResolvedEvent` 均在业务事务提交后（`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`）以独立事务写入，工作台写入失败只记日志，不影响业务；业务回滚时不会产生待办。待办按 `(todo_key, user_id)` 幂等更新（WB-TODO-R02）；已处理待办、消息的清理使用物理删除，便于同一键再次创建。
- **待办**：审批类待办键为 `WF_TASK:{taskId}`，列表返回 `taskId`，前端快捷通过 / 驳回 / 批量通过直接调用系统管理审批流接口（`/system/workflow/tasks/{id}/approve|reject`、`/tasks/batch-approve`），“我已处理”“我发起的”也直接使用审批流接口（`/tasks/my?status=DONE`、`/instances/my`、撤回）。审批类不能手工完成，任务类可“标记完成”。用户停用（`UserDeactivatedEvent`）后其任务类待办转给主部门负责人，负责人已有同键待办或未设置负责人时取消（R03）。定时任务 `WB_TODO_RECONCILE`（每天 02:00）用 `WorkflowApi.getTaskStatuses` 对账（R04），`WB_CLEANUP`（02:30）清理超期待办与消息（R05、WB-MSG-R03）。
- **消息与邮件**：参数 `wb.email.enabled` 为是且配置了 `spring.mail.*` 时异步发送，失败按 1、5、15 分钟重试 3 次（WB-MSG-R01），用户无邮箱不发（R02）。
- **公告**：内容用 jsoup `Safelist.relaxed()`（去掉 iframe）过滤脚本、事件属性（WB-NTC-R01）；按部门发布时含下级部门；应读人数 = 范围内的启用用户（`UserApi.listEnabled`）；首页只显示已发布、已到发布时间、未过期的公告；首页加载时对未读重要公告弹窗，“我已阅读”后不再弹出（R02）。编辑器暂为 HTML 文本 + 预览（富文本编辑器组件待引入）。
- **预警**：同一 `alert_key` 一条记录。OPEN 时更新内容与级别；HANDLED 仅在级别升高时重新打开；IGNORED 在级别升高或 7 天忽略期已过时重新打开；RESOLVED 或不存在时新建（重置首次预警时间）。接收人取事件 userIds，为空时取拥有 permission 的用户，重复发生时追加新接收人。新建、重新打开、或未处理时级别升高且新级别为严重时，给全部接收人发“【严重预警】”站内消息（并按参数发邮件，R03）。
- **首页看板**：卡片扩展点 `workbench-api` 的 `DashboardCard`（`DashboardCard.of / chart` 工厂 + `CardData`），由各业务模块在自己的 biz 中注册 Bean、只统计本模块的表；`load()` 在当前用户请求中执行，Mapper 的数据权限自动生效（R01）。工作台按权限过滤（多个权限用 `|` 表示任一），结果按用户缓存 `wb.dashboard.refresh-minutes` 分钟（`?refresh=true` 强制刷新），单卡失败返回“卡片数据加载失败”，前端显示“加载失败，点击重试”（R02）。已注册卡片：销售 `SAL_ORDER_MONTH` 本月接单额（环比）、`SAL_ORDER_OPEN` 在手订单；出货 `SHP_SHIPMENT_MONTH` 本月出货额、`SHP_SHIPMENT_TREND` 近 6 个月出货趋势；财务 `FIN_RECEIVED_MONTH` 本月回款额（收款 / 预收核销应收本位币）、`FIN_AR_OVERDUE` 逾期应收、`FIN_AP_WEEK` 本周到期应付；PMC `PMC_ON_TIME_RATE` 交期达成率（本月已到承诺 / 客户交期的销售订单需求中已满足的比例）、`PMC_MRP_PENDING` 待转 MRP 建议、`PMC_SHORTAGE_ORDERS` 缺料订单（最近一次缺料分析）；资材 `PUR_ORDER_OVERDUE` 逾期未到货；品质 `QC_IQC_PENDING` 待检 / 超时待检、`QC_IQC_PASS_RATE` 来料合格率；生产 `MFG_PROGRESS` 生产进度、`MFG_YIELD_TODAY` 今日良率；仓库 `INV_IN_PENDING` 待入库、`INV_OUT_PENDING` 待出库、`INV_STOCK_AMOUNT` 库存金额（按流水金额结存，按仓库类型汇总）。
- **布局与快捷入口**：默认布局 = 有权限的全部卡片按注册顺序；保存后按保存顺序，新卡片追加在后。快捷入口最多 12 个，未保存时按权限从默认入口中取前 6 个；“最近访问”记录在浏览器本地（最多 5 个）。
- **系统管理契约新增**：`UserApi.listEnabled`、`WorkflowApi.getTaskStatuses`、`UserDeactivatedEvent`（见 01-系统管理 README）。
- **限制**：`POST /workbench/todos/batch-approve` 未单独提供（前端直接调用审批流批量接口）；“需要填写业务内容的审批节点跳过批量”由审批流批量接口处理。

