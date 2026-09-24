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
