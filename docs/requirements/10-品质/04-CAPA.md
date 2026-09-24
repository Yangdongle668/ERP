# 10-04 CAPA / 8D

## 1. 功能说明

纠正与预防措施，按 8D 方法分步推进：D1 成立小组 → D2 问题描述 → D3 临时围堵 → D4 根本原因 → D5 永久措施 → D6 实施验证 → D7 预防再发 → D8 结案。每一步有负责人和期限，超期提醒。

- 使用者：QE（负责人）、小组成员、品质主管（验证、结案）
- 优先级：P1

## 2. 数据表

### qc_capa（BaseDocDO，编码规则 QC_CAPA）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | str(128) | 是 | |
| source_type | enum(NCR/COMPLAINT/AUDIT/OTHER) | 是 | |
| source_id / source_no | | 否 | |
| material_id | id | 否 | |
| customer_id / supplier_id | id | 否 | |
| leader_id | id | 是 | 负责人 |
| team_members | str(512) | 否 | 小组成员用户 ID（D1） |
| d2_problem | text | 否 | 问题描述（5W2H） |
| d3_containment | text | 否 | 临时围堵措施 |
| d3_due / d3_done_at | date / datetime | | |
| d4_root_cause | text | 否 | 根本原因（发生原因、流出原因） |
| d4_method | str(64) | 否 | 分析方法（鱼骨图、5Why） |
| d5_actions | text | 否 | 永久纠正措施 |
| d6_implementation | text | 否 | 实施情况与验证数据 |
| d7_prevention | text | 否 | 预防措施（文件修订、横向展开） |
| d8_summary | text | 否 | 结案总结 |
| current_step | int | 是 | 1～8 |
| due_date | date | 是 | 整体完成期限 |
| verify_result | enum(EFFECTIVE/INEFFECTIVE) | 否 | 效果验证结果 |
| verify_by / verify_at | | 否 | |
| capa_status | enum(OPEN/VERIFYING/CLOSED/CANCELED) | 是 | 进行中 / 待验证 / 已结案 / 已取消 |

每个步骤可上传附件（sys_file，category = D1…D8）。

## 3. 页面

### 3.1 CAPA 列表（T1）

查询：单号、来源、负责人、状态（默认进行中/待验证）、到期日。列：单号、标题、来源（链接）、负责人、当前步骤（D1～D8 进度点）、到期日（超期红）、状态、操作。

### 3.2 CAPA 详情（专用分步页面）

左侧 D1～D8 步骤导航（已完成打勾、当前高亮、超期红点）；右侧当前步骤的表单（文本域 + 附件）和 [保存] [完成本步]。D5 完成后进入“待验证”；品质主管在 D6 填写验证数据并选择验证结果：有效 → 继续 D7、D8 → [结案]；无效 → 退回 D4 重新分析（记录一次无效验证）。

打印：8D 报告（中文/英文模板，可发给客户）。

## 4. 业务规则

| 编号 | 规则 | 提示原文 |
|---|---|---|
| QC-CAPA-R01 | 只能按顺序完成步骤；完成某步时该步必填内容不能为空 | `请填写 D{n} 内容` |
| QC-CAPA-R02 | D3 期限默认为创建后 24 小时（客诉来源为参数 `qc.complaint.reply-days` 天内） | — |
| QC-CAPA-R03 | 只有负责人和小组成员可以编辑步骤内容；验证和结案需要 `qc:capa:verify` / `close` | `只有负责人或小组成员可以编辑` |
| QC-CAPA-R04 | 到期前 3 天、到期当天、超期每周提醒负责人；超期同时提醒品质主管 | — |
| QC-CAPA-R05 | 结案后回写来源 NCR/客诉的 CAPA 状态 | — |

## 5. 接口

`/quality/capas`（CRUD、cancel）、`PUT /{id}/steps/{n}`、`POST /{id}/steps/{n}/complete`、`POST /{id}/verify`、`POST /{id}/close`、`GET /{id}/print-data?lang=`。

## 6. 验收用例

| 编号 | 前置条件 | 操作 | 预期结果 |
|---|---|---|---|
| QC-CAPA-T01 | NCR 生成 CAPA | — | CAPA 带出 NCR 的物料、描述到 D2 |
| QC-CAPA-T02 | D4 未填 | 完成 D4 | 提示“请填写 D4 内容” |
| QC-CAPA-T03 | 验证无效 | — | 退回 D4，验证记录保留 |
| QC-CAPA-T04 | 超期 | — | 负责人和品质主管收到提醒 |
