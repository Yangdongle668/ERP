# 01 系统管理（总览）

## 1. 模块定位

系统管理是平台层，提供所有业务模块共用的基础能力和基础数据：组织、用户、角色权限、数据字典、编码规则、计量单位、币别汇率、审批流、打印模板、系统参数、日志审计、附件、任务中心。**缺少本模块其他模块无法运行**，因此在开发批次中排第一。

本模块不依赖任何业务模块。业务模块通过本模块的 api（`erp-module-system-api`）使用这些能力，并通过“声明式注册”（见第 5 节）向本模块登记自己的编码规则、权限点、字典、参数、审批单据、打印单据，不需要修改本模块的代码或数据库脚本。

## 2. 功能与页面清单

| 功能 | 文档 | 页面 | 模板 | 路由 | 菜单权限 | 优先级 |
|---|---|---|---|---|---|---|
| 组织架构 | [01-组织架构](01-组织架构.md) | 组织架构 | T6（树形表格） | `/system/org` | `system:org:query` | P0 |
| 用户管理 | [02-用户管理](02-用户管理.md) | 用户列表 | T1 | `/system/user` | `system:user:query` | P0 |
| | | 新建/编辑用户 | T3 | `/system/user/new`、`/:id/edit` | `system:user:create` / `update` | P0 |
| 角色与权限 | [03-角色与权限](03-角色与权限.md) | 角色列表 + 授权 | T1 + 抽屉 | `/system/role` | `system:role:query` | P0 |
| 数据字典 | [04-数据字典](04-数据字典.md) | 字典类型 + 字典项 | 左右分栏 | `/system/dict` | `system:dict:query` | P0 |
| 编码规则 | [05-编码规则](05-编码规则.md) | 编码规则 | T1 + T2 | `/system/code-rule` | `system:code-rule:query` | P0 |
| 计量单位 | [06-计量单位](06-计量单位.md) | 计量单位 | T1 + T2 | `/system/uom` | `system:uom:query` | P0 |
| 币别与汇率 | [07-币别汇率](07-币别汇率.md) | 币别、汇率（两个页签） | T1 + T2 | `/system/currency` | `system:currency:query` | P0 |
| 审批流 | [08-审批流](08-审批流.md) | 审批流配置 | 专用 | `/system/workflow` | `system:workflow:query` | P0 |
| | | 审批实例监控 | T1 | `/system/workflow-instance` | `system:workflow:monitor` | P1 |
| 打印模板 | [09-打印模板](09-打印模板.md) | 打印模板 | T1 + 编辑器 | `/system/print-template` | `system:print:query` | P1 |
| 系统参数 | [10-系统参数](10-系统参数.md) | 系统参数 | T8 | `/system/param` | `system:param:query` | P0 |
| 日志审计 | [11-日志审计](11-日志审计.md) | 登录日志、操作日志（两个页签） | T1 | `/system/log` | `system:log:query` | P0 |
| 附件与任务中心 | [12-附件与任务中心](12-附件与任务中心.md) | 任务中心 | T1 | `/system/task` | 登录即可（只看自己的） | P1 |
| | | 定时任务 | T1 | `/system/job` | `system:job:query` | P1 |
| 登录与个人中心 | [13-登录与个人中心](13-登录与个人中心.md) | 登录、个人中心、修改密码 | 专用 | `/login`、`/profile` | 登录即可 | P0 |

侧边栏“系统管理”下的菜单顺序：组织架构、用户、角色、数据字典、编码规则、计量单位、币别汇率、审批流、打印模板、系统参数、日志审计、定时任务、任务中心。

## 3. 用户角色

| 角色 | 使用的功能 |
|---|---|
| 系统管理员 | 全部 |
| 部门主管（可选授权） | 查看本部门用户、审批实例 |
| 普通用户 | 登录、个人中心、修改密码、任务中心（自己的任务） |

## 4. 数据表总览

| 表 | 说明 | 文档 |
|---|---|---|
| sys_org | 组织（公司/部门） | 01 |
| sys_user、sys_user_dept、sys_user_role | 用户、兼职部门、用户角色 | 02 |
| sys_role、sys_role_permission、sys_role_data_dept | 角色、角色权限、自定义数据范围 | 03 |
| sys_permission（技术表，启动时由声明同步） | 权限点目录 | 03 |
| sys_dict_type、sys_dict_item | 字典 | 04 |
| sys_code_rule、sys_code_seq | 编码规则、流水号 | 05 |
| sys_uom、sys_uom_conversion | 计量单位、通用换算 | 06 |
| sys_currency、sys_exchange_rate | 币别、汇率 | 07 |
| wf_biz_type、wf_definition、wf_branch、wf_node、wf_instance、wf_task | 审批流 | 08 |
| sys_print_template | 打印模板 | 09 |
| sys_param | 系统参数 | 10 |
| sys_login_log、sys_oper_log、sys_doc_log | 日志 | 11 |
| sys_file、sys_async_task、sys_job、sys_job_log | 附件、异步任务、定时任务 | 12 |

## 5. 声明式注册机制（业务模块如何接入）

为了让各模块并行开发而不修改系统管理模块，业务模块在自己的配置类中声明以下 Bean，系统管理模块在启动时收集：

| 声明类型（在 system-api 中定义） | 用途 | 启动时的处理 |
|---|---|---|
| `CodeRuleDefinition` | 编码规则默认值 | 首次使用时写入 `sys_code_rule`；之后以数据库为准 |
| `PermissionDefinition` | 权限点目录：模块 → 分组（菜单）→ 权限点 | 每次启动同步到 `sys_permission`（新增/更新名称；代码中删除的权限点标记为失效） |
| `DictDefinition` | 模块内置字典（类型 + 项） | 字典类型不存在则创建；内置项不存在则补充；不覆盖管理员对标签、颜色、排序的修改 |
| `ParamDefinition` | 系统参数（编码、名称、类型、默认值、说明） | 参数不存在则按默认值创建 |
| `ApprovalBizDefinition` | 可审批的单据类型及其条件字段 | 同步到 `wf_biz_type` |
| `PrintBizDefinition` | 可打印的单据类型、可用变量说明、内置默认模板文件路径 | 同步到打印业务类型；内置模板不存在时导入 |
| `@ErpJob` 注解 | 定时任务 | 同步到 `sys_job` |

同一编码被两个模块重复声明时启动失败，并在日志中指出冲突的编码和模块。

## 6. 对其他模块提供的 API（system-api）

| 接口 | 方法 | 说明 |
|---|---|---|
| `CurrentUserApi` | `current()` | 当前用户：ID、姓名、主部门、所属公司、角色、数据范围 |
| `UserApi` | `get(id)`、`list(ids)`、`getDeptLeader(deptId)`、`getSuperior(userId)` | 用户查询、部门负责人、直属上级 |
| `OrgApi` | `get(id)`、`getChildrenIds(id)`、`getCompanyOf(deptId)` | 组织查询 |
| `DataScopeApi` | `currentScope()` | 当前用户的数据范围（供业务查询拼条件） |
| `CodeRuleApi` | `nextCode(bizCode)` | 生成编码（已实现） |
| `DictApi` | `getItems(type)`、`validate(type, value)`、`label(type, value)` | 字典 |
| `UomApi` | `get(code)`、`convert(qty, from, to)`、`round(qty, uom)` | 计量单位 |
| `CurrencyApi` | `getBaseCurrency()`、`getRate(currency, date)`、`getPrecision(currency)` | 币别汇率 |
| `ParamApi` | `getString/getInt/getDecimal/getBool(key)` | 系统参数 |
| `WorkflowApi` | `start(...)`、`withdraw(...)`、`getStatus(...)` | 审批流；结果以 `ApprovalCompletedEvent` 通知 |
| `DocLogApi` | `record(bizType, bizId, bizNo, action, fromStatus, toStatus, reason)` | 单据操作日志 |
| `FileApi` | `bind(fileIds, bizType, bizId)`、`list(bizType, bizId)` | 附件 |
| `AsyncTaskApi` | `submit(type, name, runnable)` | 提交后台任务（异步导出等） |

## 7. 开发分期

| 批次 | 功能 |
|---|---|
| 第 1 步（其他模块开工前完成） | 组织架构、用户、角色权限（含数据范围）、字典、编码规则、计量单位、币别汇率、系统参数、日志、登录与个人中心、声明式注册机制 |
| 第 2 步（与业务模块并行） | 审批流、打印模板、附件、任务中心、定时任务 |

在审批流完成之前，业务模块调用 `WorkflowApi.start()` 时按“该单据未配置审批流”处理（直接审核通过），因此不会阻塞业务模块开发。
