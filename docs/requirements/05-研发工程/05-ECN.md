# 05-05 ECN（工程变更）

## 1. 功能说明

对已审核 BOM 的受控变更：记录变更原因和内容，分析对库存、在途采购、在制生产的影响，经审批后自动生成新 BOM 版本，并按生效方式切换默认版本，最后由相关部门确认处理完毕后关闭。

- 使用者：研发工程师（发起）、工程/品质/PMC/采购主管（会签审批）、相关部门（执行确认）
- 优先级：P1
- 一张 ECN 可以同时变更多个父件的 BOM（例如某物料在 5 个产品中都被替换）。

## 2. 数据表

### eng_ecn ECN（单据，编码规则 ENG_ECN）

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| title | str(128) | 是 | | 变更标题 |
| ecn_type | dict(eng_ecn_type) | 是 | | 变更类型 |
| reason_type | dict(eng_ecn_reason) | 是 | | 变更原因 |
| reason | str(1000) | 是 | | 变更原因说明 |
| urgency | enum(NORMAL/URGENT) | 是 | NORMAL | 紧急程度 |
| effective_mode | enum(IMMEDIATE/DATE/USE_UP) | 是 | IMMEDIATE | 生效方式：审批后立即 / 指定日期 / 旧料用完后手工切换 |
| effective_date | date | 条件 | | effective_mode=DATE 时必填，≥ 今天 |
| customer_id | id | 否 | | 客户要求的变更填写客户 |
| effected_at | datetime | 否 | | 实际生效时间 |

状态：草稿 → 待审批 → 已审核（已生成新 BOM 版本，等待生效）→ 已生效（执行中）→ 已关闭；草稿可作废。已审核/已生效映射为通用状态 APPROVED / IN_PROGRESS，已关闭为 COMPLETED。

### eng_ecn_line 变更明细

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ecn_id | id | 是 | |
| line_no | int | 是 | |
| bom_id | id | 是 | 被变更的 BOM 版本（必须为已审核的默认版本） |
| action | enum(ADD/REMOVE/REPLACE/CHANGE_QTY) | 是 | 新增子件 / 删除子件 / 替换子件 / 修改用量或损耗 |
| old_component_id | id | 条件 | REMOVE/REPLACE/CHANGE_QTY 必填，必须在该 BOM 中 |
| new_component_id | id | 条件 | ADD/REPLACE 必填，启用物料 |
| old_qty_per / new_qty_per | qty | 条件 | 旧值系统带出；ADD、CHANGE_QTY、REPLACE 需填新用量 |
| old_scrap_rate / new_scrap_rate | pct | 否 | |
| position_no | str(1024) | 否 | 新位号 |
| new_bom_id | id | 否 | 审批通过后生成的新 BOM 版本 |
| remark | str(256) | 否 | |

### eng_ecn_impact 影响分析（审批前生成，可刷新）

| 字段 | 类型 | 说明 |
|---|---|---|
| ecn_id | id | |
| material_id | id | 受影响物料（被删除/替换的旧子件，以及父件） |
| impact_type | enum(STOCK/PURCHASE/WIP/SALES) | 库存 / 在途采购 / 在制生产订单 / 未完成销售订单（父件） |
| doc_no | str(64) | 相关单据号（库存为空） |
| qty | qty | 数量 |
| handling | enum(CONTINUE_USE/REWORK/SCRAP/RETURN_SUPPLIER/CANCEL/KEEP/UPDATE_WIP/NO_ACTION) | 处理方式 |
| handling_remark | str(256) | |

处理方式可选项按影响类型：库存（继续使用 / 返工 / 报废 / 退供应商）；在途采购（取消 / 保留）；在制生产订单（更新用料 / 保留原用料）；销售订单（无需处理，仅提示）。

### eng_ecn_task 执行确认

| 字段 | 类型 | 说明 |
|---|---|---|
| ecn_id | id | |
| dept_role | enum(PURCHASE/WAREHOUSE/PRODUCTION/QUALITY/PMC) | 执行部门 |
| assignee_id | id | 负责人 |
| content | str(512) | 需执行的内容（根据影响分析自动生成，可修改） |
| status | enum(PENDING/DONE) | |
| done_remark | str(512) | |
| done_at | datetime | |

## 3. 页面

### 3.1 ECN 列表（T1）

路由 `/engineering/ecn`。查询：单号、标题（模糊）、类型、状态（默认非关闭、非作废）、涉及物料（MaterialSelect）、单据日期（默认近 6 个月）。
列：单号（链接）、标题、类型、原因、紧急（紧急显示红色标签）、生效方式、生效日期、状态、发起人、单据日期、操作（编辑、删除——草稿）。
工具栏：[新建]、[导出]。

### 3.2 ECN 编辑页（T4）

**单头**：标题*、类型*、原因类型*、原因说明*（文本域，整行）、紧急程度、生效方式*、生效日期（条件必填）、客户（CustomerSelect）、附件（变更前后图纸等）。

**变更明细（LinesEditor）**

| 列 | 控件 | 说明 |
|---|---|---|
| BOM | 选择（父件编码搜索，只列默认且已审核的 BOM） | |
| 变更动作 | 下拉 | |
| 原子件 | 下拉（该 BOM 的子件） | REMOVE/REPLACE/CHANGE_QTY 可用；选择后带出原用量、原损耗 |
| 新子件 | MaterialSelect | ADD/REPLACE 可用 |
| 原用量 / 新用量 | 只读 / QtyInput | |
| 原损耗 / 新损耗 | 只读 / 数字框 | |
| 新位号 | 输入框 | |
| 备注 | 输入框 | |

工具栏额外按钮：[批量替换]：输入“原子件 → 新子件”，系统查出所有默认 BOM 中使用原子件的行，一次生成多行 REPLACE 明细。

**影响分析**（编辑页下方卡片）：[分析影响] 按钮（保存后可用）→ 生成影响列表，每行选择处理方式和备注。提交前必须完成分析且每行都选择了处理方式。

**执行确认**：根据影响分析自动生成各部门任务（库存 → 仓库；在途采购 → 采购；在制 → 生产；品质默认生成“确认检验标准”任务），可调整负责人和内容。

### 3.3 ECN 详情（T5）

**按钮显示矩阵**

| 按钮 | 草稿 | 待审批 | 已审核 | 已生效 | 已关闭 | 权限 |
|---|---|---|---|---|---|---|
| 编辑 / 删除 / 作废 | ✓ | | | | | update / delete / void |
| 提交 | ✓ | | | | | submit |
| 撤回、通过、驳回 | | ✓ | | | | 审批流 |
| 立即生效 | | | ✓（生效方式为“用完切换”或需要提前生效） | | | effect |
| 确认完成（我的任务） | | | | ✓（当前用户有 PENDING 任务） | | 登录即可 |
| 关闭 | | | | ✓（全部任务完成） | | close |
| 打印 | ✓ | ✓ | ✓ | ✓ | ✓ | print |

页签：变更明细（含新 BOM 版本链接）、影响分析、执行确认（任务列表、完成情况）、审批记录、操作日志、附件。

## 4. 状态与流转

| 当前 | 动作 | 结果 | 副作用 |
|---|---|---|---|
| 草稿 | 提交 | 待审批 / 已审核 | 校验 R01～R04 |
| 待审批 | 审批通过 | 已审核 | 为每个涉及的 BOM 复制新版本并应用变更，新版本直接为“已审核”（描述为“ECN-xxx”），发布 `EcnApprovedEvent`；生效方式 IMMEDIATE 时立即执行“生效” |
| 已审核 | 生效（立即/到达生效日期的定时任务/手工） | 已生效 | 新 BOM 版本设为默认；在制生产订单处理方式为“更新用料”的，调用生产模块更新用料（未领料部分）；发布 `EcnEffectiveEvent`；给执行任务负责人生成待办 |
| 已生效 | 各任务确认完成 | 已生效 | |
| 已生效 | 关闭 | 已关闭 | 全部任务 DONE |
| 草稿 | 作废 | 已作废 | |

## 5. 业务规则

| 编号 | 触发 | 规则 | 提示原文 |
|---|---|---|---|
| ENG-ECN-R01 | 提交 | 明细至少一行；每行 BOM 必须是当前默认且已审核的版本 | `BOM「{no}」不是当前默认版本，请重新选择` |
| ENG-ECN-R02 | 提交 | 同一 BOM 同一子件在本 ECN 中只能有一个动作；应用变更后不能出现重复子件、不能出现循环引用（同 BOM 规则） | `BOM「{no}」中子件「{code}」存在多个变更` |
| ENG-ECN-R03 | 提交 | 已完成影响分析，且每行都有处理方式 | `请完成影响分析并选择处理方式` |
| ENG-ECN-R04 | 提交 | 同一个 BOM 不能同时存在于两张未关闭（待审批/已审核）的 ECN 中 | `BOM「{no}」正在 ECN「{ecnNo}」中变更` |
| ENG-ECN-R05 | 审批通过 | 生成新 BOM 前再次检查 BOM 仍为默认版本；若期间已有其他版本成为默认，审批失败 | `BOM「{no}」在审批期间已变更，请驳回后重新发起` |
| ENG-ECN-R06 | 生效 | 定时任务每天 00:10 处理生效日期为今天的 ECN | — |
| ENG-ECN-R07 | 关闭 | 所有执行任务完成 | `还有 {n} 项执行任务未完成` |
| ENG-ECN-R08 | 关键件 | 变更涉及关键件（is_key）或已关联认证的物料时，提交时提示“该变更涉及认证关键件，请评估是否需要重新认证”，并自动增加一条“认证评估”执行任务（负责人：认证工程师角色） | — |

## 6. 接口

| 方法 | 路径 | 权限 |
|---|---|---|
| GET / POST / PUT / DELETE | /engineering/ecns[/{id}] | `eng:ecn:query/create/update/delete` |
| POST | /engineering/ecns/batch-replace-preview | `eng:ecn:create`（原子件 → 新子件，返回将生成的明细） |
| POST | /engineering/ecns/{id}/analyze | `eng:ecn:update` |
| POST | /engineering/ecns/{id}/submit、/void、/effect、/close | 相应权限 |
| POST | /engineering/ecns/{id}/tasks/{taskId}/done | 任务负责人 |
| GET | /engineering/ecns/{id}/print-data | `eng:ecn:print` |

## 7. 验收用例

| 编号 | 前置条件 | 操作 | 预期结果 |
|---|---|---|---|
| ENG-ECN-T01 | 连接器 A 在 FG1、FG2 的默认 BOM 中 | 批量替换 A → B，生效方式立即 | 生成 2 行 REPLACE 明细 |
| ENG-ECN-T02 | 同上，A 有库存 500，在途采购 1000 | 分析影响 | 列出库存 500、PO 1000，以及用到 A 的在制生产订单 |
| ENG-ECN-T03 | 未选择处理方式 | 提交 | 提示“请完成影响分析并选择处理方式” |
| ENG-ECN-T04 | 审批通过 | — | FG1、FG2 各生成新 BOM 版本（B 替换 A）并成为默认；执行任务推送到采购、仓库、生产负责人 |
| ENG-ECN-T05 | 生效方式指定日期 10-01 | 审批通过 | 新版本已审核但非默认；10-01 零点后自动成为默认 |
| ENG-ECN-T06 | 任务未全部完成 | 关闭 | 提示“还有 2 项执行任务未完成” |
| ENG-ECN-T07 | FG1 的 BOM 已在另一张待审批 ECN 中 | 提交新 ECN | 提示“BOM「FG1-V2」正在 ECN「ECN-202609-001」中变更” |
