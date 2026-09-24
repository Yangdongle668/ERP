# 05-03 BOM

## 1. 功能说明

BOM（物料清单）定义一个父件（半成品、成品、虚拟件）由哪些子件、各多少组成。多级 BOM 由各级父件的单层 BOM 串联而成。

- 使用者：研发工程师（编制）、工程主管（审核）；PMC、生产、财务、销售（引用）
- 优先级：P0
- 一个父件可以有多个 BOM **版本**，同一时间只有一个**默认版本**被 MRP、生产订单、成本计算使用。
- 已审核的 BOM 不能修改：小改动通过“新建版本（复制）”或 ECN 完成，保证已下达生产订单使用的 BOM 可追溯。

## 2. 数据表

### eng_bom BOM 头（单据，含 BaseDocDO 字段；doc_no 不使用编码规则，取值为 `父件编码-版本`，如 `FG00001-V2`）

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| material_id | id | 是 | | 父件 |
| version | int | 是 | | 版本号，同一父件从 1 递增，显示为 V1、V2 |
| base_qty | qty | 是 | 1 | 基数：用量是指生产“基数”个父件所需的子件数量 |
| is_default | bool | 是 | 0 | 是否默认版本 |
| effective_date | date | 否 | | 成为默认版本的日期（系统记录） |
| description | str(256) | 否 | | 版本说明，如“改用 B 供应商连接器” |
| ecn_id | id | 否 | | 由 ECN 生成时关联 |
| copied_from_id | id | 否 | | 复制来源版本 |

状态使用通用单据状态：草稿 DRAFT → 待审批 PENDING_APPROVAL → 已审核 APPROVED → 已停用（用 CLOSED 表示，界面显示“停用”）；另有已作废 VOIDED。

唯一：`uk_material_version(material_id, version)`。

### eng_bom_line BOM 行

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| bom_id | id | 是 | | |
| line_no | int | 是 | | 行号 1、2、3… |
| component_id | id | 是 | | 子件物料 |
| qty_per | qty | 是 | | 用量（每 base_qty 个父件），> 0 |
| uom | str(16) | 是 | 子件基本单位 | 只能是子件基本单位（BOM 统一按基本单位） |
| scrap_rate | pct | 是 | 0 | 损耗率 0～100%（存 0～1） |
| position_no | str(1024) | 否 | | 位号，如 `R1,R2,R5-R8` |
| issue_method | enum(PICK/BACKFLUSH) | 是 | PICK | 发料方式：领料 / 倒冲（报工时自动扣料） |
| operation_seq | int | 否 | | 在哪道工序使用（有工艺路线时可选） |
| is_key | bool | 是 | 0 | 关键件（ECN、认证关注） |
| remark | str(256) | 否 | | |

唯一：`uk_bom_line(bom_id, line_no)`；同一 BOM 内同一子件不允许重复出现（`uk_bom_component(bom_id, component_id)`），不同用途用位号区分并合并用量。

### eng_bom_substitute 替代料

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| bom_line_id | id | 是 | | 主料行 |
| substitute_id | id | 是 | | 替代料 |
| priority | int | 是 | 1 | 优先级，1 最高 |
| ratio | rate | 是 | 1 | 替代比例：1 个主料 = ratio 个替代料 |
| remark | str(128) | 否 | | |

## 3. 需求量计算（全系统统一口径）

```
子件需求量 = 父件数量 ÷ base_qty × qty_per × (1 + scrap_rate)
结果按子件单位精度向上取整（ROUND_UP）
```

示例：父件 100 个，base_qty = 1，qty_per = 2，损耗 2% → 100 × 2 × 1.02 = 204。

多级展开：逐层计算；**虚拟件（PHANTOM）** 不作为需求出现，直接用其子件替代（透过）。

## 4. 页面

### 4.1 BOM 列表（T1）

路由 `/engineering/bom`，权限 `eng:bom:query`。

**查询条件**

| 条件 | 控件 | 匹配 | 默认 |
|---|---|---|---|
| 父件 | MaterialSelect（半成品/成品/虚拟件） | 等于 | |
| 父件编码/名称 | 输入框 | 编码前缀或名称模糊 | |
| 状态 | 下拉多选 | 包含 | 草稿、待审批、已审核 |
| 仅默认版本 | 开关 | | 开 |
| 包含子件 | MaterialSelect | 该 BOM 直接包含此子件 | （展开区） |

**列表列**

| 列 | 字段 | 宽度 | 说明 |
|---|---|---|---|
| 父件编码 | material.code | 130 | 链接到 BOM 详情 |
| 父件名称 | material.name | 180 | |
| 规格 | material.spec | 180 | |
| 版本 | version | 70 | “V2”，默认版本后显示绿色“默认”标签 |
| 基数 | base_qty | 70 | 右 |
| 子件数 | — | 70 | 右 |
| 说明 | description | 自适应 | |
| 状态 | status | 80 | |
| 更新人/时间 | updated_by/updated_at | 160 | |
| 操作 | — | 200 | |

**按钮**

| 按钮 | 位置 | 权限 | 显示条件 | 行为 |
|---|---|---|---|---|
| 新建 | 工具栏 | `eng:bom:create` | 始终 | 打开 BOM 编辑页 |
| 导入 | 工具栏 | `eng:bom:import` | 始终 | 见 4.5 |
| 导出 | 工具栏 | `eng:bom:export` | 始终 | 选项：单层 / 多级展开（勾选行） |
| 编辑 | 行 | `eng:bom:update` | 草稿 | |
| 新建版本 | 行 | `eng:bom:create` | 已审核或停用 | 复制该版本为新草稿版本（版本号 = 该父件最大版本 + 1） |
| 设为默认 | 行·更多 | `eng:bom:set-default` | 已审核且非默认 | R07 |
| 停用 | 行·更多 | `eng:bom:disable` | 已审核且非默认 | 确认 |
| 删除 | 行·更多 | `eng:bom:delete` | 草稿 | |
| 打印 | 行·更多 | `eng:bom:print` | 非作废 | |

### 4.2 BOM 编辑页（T4）

路由 `/engineering/bom/new`、`/:id/edit`。

**单头**

| 字段 | 控件 | 必填 | 默认 | 可编辑 | 校验 |
|---|---|---|---|---|---|
| 父件 | MaterialSelect（类型：半成品、成品、虚拟件；草稿或启用） | 是 | | 仅新建 | R01 |
| 版本 | 只读 | — | 自动 | 否 | 新建时显示“V{下一版本}” |
| 基数 | QtyInput | 是 | 1 | 草稿 | > 0 |
| 版本说明 | 输入框 | 否 | | 草稿 | ≤256；新建版本时必填 |
| 附件 | AttachmentUpload | 否 | | 草稿 | |

父件信息只读显示：名称、规格、单位、类型。

**明细（LinesEditor）**

| 列 | 控件 | 必填 | 默认 | 宽度 | 校验 |
|---|---|---|---|---|---|
| 行号 | 只读 | — | 自动 | 50 | |
| 子件编码 | MaterialSelect（启用；参数允许时含草稿） | 是 | | 150 | R02、R03、R04 |
| 名称 / 规格 / 类型 | 只读 | — | 带出 | 160 / 180 / 70 | |
| 单位 | 只读 | — | 子件基本单位 | 60 | |
| 用量 | QtyInput（按单位精度，最多 4 位） | 是 | 1 | 100 | > 0 |
| 损耗率(%) | 数字框 0～100 | 是 | 0 | 80 | |
| 位号 | 输入框 | 否 | | 180 | 填写位号时，用量应等于位号个数（R05，警告不阻止） |
| 发料方式 | 下拉（领料/倒冲） | 是 | 领料 | 90 | |
| 工序 | 下拉（父件默认工艺路线的工序） | 否 | | 100 | 无工艺路线时隐藏 |
| 关键件 | 复选框 | 否 | 否 | 60 | |
| 替代料 | 按钮“替代(n)” | 否 | | 80 | 打开替代料弹窗：替代料（MaterialSelect）、优先级、比例、备注；替代料不能是主料本身或同 BOM 其他主料 |
| 备注 | 输入框 | 否 | | 140 | |

工具栏：[添加行] [批量添加物料]（MaterialPickerDialog）[从其他 BOM 复制行]（选择 BOM 版本后勾选行带入）[导入行] [删除选中]。

页头按钮：[取消] [保存草稿] [提交]。

### 4.3 BOM 详情（T5）

路由 `/engineering/bom/:id`。页头：`父件编码 名称 V2` + 状态 + 默认标签。

**按钮显示矩阵**

| 按钮 | 草稿 | 待审批 | 已审核（非默认） | 已审核（默认） | 停用 | 权限 |
|---|---|---|---|---|---|---|
| 编辑 | ✓ | | | | | update |
| 提交 | ✓ | | | | | submit |
| 撤回 / 通过 / 驳回 | | ✓（审批流） | | | | — |
| 反审核 | | | ✓ | | | unapprove（R09） |
| 设为默认 | | | ✓ | | | set-default |
| 新建版本 | | | ✓ | ✓ | ✓ | create |
| 停用 | | | ✓ | | | disable |
| 删除 | ✓ | | | | | delete |
| 打印 | ✓ | ✓ | ✓ | ✓ | ✓ | print |

**页签**

| 页签 | 内容 |
|---|---|
| 明细 | 单层明细只读表格（含替代料展开行） |
| 多级展开 | 树形表格：层级（缩进 + “1、1.1、1.1.1”）、子件编码、名称、规格、单位、单层用量、损耗率、**累计用量**（相对 1 个顶层父件）、取得方式、是否虚拟件、子 BOM 版本；顶部输入“父件数量”可计算需求量；[导出] |
| 反查 | 本 BOM 父件被哪些 BOM 使用（where-used），多级向上，列出顶层成品 |
| 版本比较 | 选择另一个版本，对比结果：新增（绿）、删除（红）、用量/损耗/位号变化（橙，显示旧值 → 新值） |
| 成本（P1，字段权限 `eng:bom:cost`） | 按多级展开的材料标准成本卷算：每行 = 累计用量 × 子件标准成本；汇总材料成本；缺少标准成本的子件标红 |
| 审批记录 / 操作日志 / 附件 | 通用 |

### 4.4 物料反查（物料详情页“BOM”页签，见 02-物料）

### 4.5 BOM 导入

模板列：父件编码*、基数、版本说明、子件编码*、用量*、损耗率(%)、位号、发料方式（领料/倒冲）、关键件（是/否）、备注。

- 同一父件编码的连续行组成一个 BOM，导入为该父件的**新草稿版本**。
- 导入选项：“导入后提交审核”。
- 校验：父件/子件存在、类型符合、循环引用、子件在同一 BOM 中不重复。

## 5. 状态与流转

| 当前 | 动作 | 前置条件 | 结果 | 副作用 |
|---|---|---|---|---|
| 草稿 | 提交 | R02～R04、R06 | 待审批 / 已审核（无审批流） | |
| 待审批 | 审批通过 | | 已审核 | 若该父件没有默认版本，自动设为默认（R07 副作用）；重算低位码；发布 `BomApprovedEvent` |
| 待审批 | 驳回 / 撤回 | | 草稿 | |
| 已审核（非默认） | 反审核 | R09 | 草稿 | |
| 已审核（非默认） | 设为默认 | | 已审核（默认） | 原默认版本取消默认；发布 `BomDefaultChangedEvent` |
| 已审核（非默认） | 停用 | | 停用 | |
| 停用 | — | | | 停用版本不能恢复，需要时通过“新建版本”复制 |
| 草稿 | 删除 | | 删除 | 若为该父件最大版本，版本号可被复用 |

## 6. 业务规则

| 编号 | 触发 | 规则 | 提示原文 |
|---|---|---|---|
| ENG-BOM-R01 | 保存 | 父件类型必须是半成品、成品或虚拟件 | `原材料、包材、辅料不能作为 BOM 父件` |
| ENG-BOM-R02 | 保存 | 子件不能是父件本身；同一 BOM 子件不重复 | `子件不能与父件相同` / `子件「{code}」重复，请合并为一行` |
| ENG-BOM-R03 | 保存/提交 | **循环引用检测**：子件的默认（或任一已审核/草稿）BOM 多级展开中不能包含本父件 | `存在循环引用：{A} → {B} → {A}` |
| ENG-BOM-R04 | 提交 | 所有子件、替代料必须为启用状态；父件必须为启用或草稿 | `子件「{code}」未启用，不能提交` |
| ENG-BOM-R05 | 保存 | 位号个数与用量 × 基数不一致时警告（位号按 `,` 分隔，`R5-R8` 视为 4 个） | `第 {n} 行位号个数 {x} 与用量 {y} 不一致`（警告，不阻止） |
| ENG-BOM-R06 | 提交 | 至少一行明细；多级展开层数不超过参数 `eng.bom.max-level` | `BOM 展开超过 {n} 层，请检查数据` |
| ENG-BOM-R07 | 设为默认 | 同一父件只能有一个默认版本；只有已审核版本能设为默认 | — |
| ENG-BOM-R08 | 修改 | 只有草稿可以修改、删除 | `只有草稿状态的 BOM 可以修改` |
| ENG-BOM-R09 | 反审核 | 默认版本不能反审核；被生产订单引用（`ProductionQueryApi.isBomUsed`）的版本不能反审核 | `默认版本不能反审核` / `该 BOM 已被生产订单使用，不能反审核，请新建版本` |
| ENG-BOM-R10 | 停用 | 默认版本不能停用（需先把其他版本设为默认） | `默认版本不能停用` |
| ENG-BOM-R11 | 审核 | 审核通过后重新计算全部物料的低位码（异步，数量大时后台任务），写入 `eng_material.low_level_code` | — |
| ENG-BOM-R12 | 虚拟件 | 虚拟件必须有已审核 BOM 才能被其他 BOM 审核通过 | `虚拟件「{code}」没有已审核的 BOM` |

## 7. 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /engineering/boms | `eng:bom:query` | 分页 |
| GET | /engineering/boms/{id} | `eng:bom:query` | 详情（含行、替代料） |
| GET | /engineering/boms/{id}/explode?qty=&levels= | `eng:bom:query` | 多级展开 |
| GET | /engineering/boms/where-used?materialId= | `eng:bom:query` | 反查 |
| GET | /engineering/boms/compare?leftId=&rightId= | `eng:bom:query` | 版本比较 |
| GET | /engineering/boms/{id}/cost | `eng:bom:cost` | 成本卷算 |
| POST | /engineering/boms | `eng:bom:create` | 新建 |
| PUT | /engineering/boms/{id} | `eng:bom:update` | 修改草稿 |
| POST | /engineering/boms/{id}/new-version | `eng:bom:create` | 新建版本 |
| POST | /engineering/boms/{id}/submit | `eng:bom:submit` | |
| POST | /engineering/boms/{id}/unapprove | `eng:bom:unapprove` | reason |
| POST | /engineering/boms/{id}/set-default | `eng:bom:set-default` | |
| POST | /engineering/boms/{id}/disable | `eng:bom:disable` | |
| DELETE | /engineering/boms/{id} | `eng:bom:delete` | |
| GET | /engineering/boms/{id}/print-data | `eng:bom:print` | 打印数据 |
| 导入导出 | /engineering/boms/import-template、/import/check、/import、/export | import / export | |

## 8. 验收用例

| 编号 | 前置条件 | 操作 | 预期结果 |
|---|---|---|---|
| ENG-BOM-T01 | 成品 FG1 无 BOM | 新建 BOM：PCBA×1、外壳×1、螺丝×4（损耗 2%），提交（无审批流） | 已审核，V1 自动成为默认 |
| ENG-BOM-T02 | FG1 V1 默认 | 展开 100 个 FG1 | 螺丝需求 408（100×4×1.02） |
| ENG-BOM-T03 | PCBA 的 BOM 中含 FG1 | 在 FG1 的 BOM 中添加 PCBA 并提交 | 提示“存在循环引用：FG1 → PCBA → FG1” |
| ENG-BOM-T04 | FG1 V1 已审核 | 编辑 V1 | 没有编辑按钮；接口返回“只有草稿状态的 BOM 可以修改” |
| ENG-BOM-T05 | FG1 V1 默认 | 新建版本 → 修改螺丝用量为 6 → 提交 → 设为默认 | V2 为默认，V1 取消默认；发布 BomDefaultChangedEvent |
| ENG-BOM-T06 | V1、V2 | 版本比较 | 螺丝行显示用量 4 → 6（橙色） |
| ENG-BOM-T07 | 半成品 SF1 为虚拟件，含 A×2、B×1；FG2 含 SF1×1 | 展开 FG2（MRP 口径） | 需求中出现 A、B，不出现 SF1 |
| ENG-BOM-T08 | 子件 C 为草稿 | 提交含 C 的 BOM | 提示“子件「C」未启用，不能提交” |
| ENG-BOM-T09 | V1 被生产订单使用 | 设 V2 为默认后反审核 V1 | 提示“该 BOM 已被生产订单使用，不能反审核，请新建版本” |
| ENG-BOM-T10 | 螺丝 | 物料详情 BOM 页签的反查 | 列出 FG1 V1、V2 |
| ENG-BOM-T11 | 位号 `R1,R2,R5-R8`，用量 5 | 保存 | 警告“第 n 行位号个数 6 与用量 5 不一致”，仍可保存 |
