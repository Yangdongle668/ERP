# 10-06 SCAR（供应商纠正措施要求）

## 1. 功能说明

来料问题（责任为供应商）时向供应商发出纠正措施要求，跟踪回复、验证效果、结案；结果计入供应商评估。

- 使用者：SQE
- 优先级：P1
- 供应商通过邮件/线下回复，SQE 在系统中登记回复内容和附件（供应商门户 P2）。

## 2. 数据表

### qc_scar（BaseDocDO，编码规则 QC_SCAR）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| supplier_id | id | 是 | |
| ncr_id | id | 否 | |
| material_id | id | 是 | |
| batch_no | str(64) | 否 | |
| problem_description | text | 是 | |
| requirement | text | 是 | 要求供应商做什么（8D 报告、改善措施、期限） |
| reply_due_date | date | 是 | 默认发出日 + 参数天数 |
| sent_at | datetime | 否 | 发出时间 |
| reply_content | text | 否 | 供应商回复摘要 |
| replied_at | datetime | 否 | |
| verify_plan | str(512) | 否 | 验证方式（如后续 3 批加严检验） |
| verify_result | enum(EFFECTIVE/INEFFECTIVE) | 否 | |
| scar_status | enum(DRAFT/SENT/REPLIED/VERIFYING/CLOSED/CANCELED) | 是 | |

## 3. 页面

列表（T1）：单号、供应商、物料、问题摘要、发出时间、回复期限（逾期红）、回复时间、状态、SQE、操作。
详情（T5 变体）：按钮 [发出]（草稿 → 已发出；打印/导出 SCAR 文件（中/英）发给供应商）、[登记回复]（回复摘要*、供应商 8D 报告附件* → 已回复）、[开始验证]（验证方式 → 验证中）、[验证结果]（有效 → 结案；无效 → 退回已发出并要求重新回复，记录次数）、[取消]。

## 4. 业务规则

| 编号 | 规则 | 提示原文 |
|---|---|---|
| QC-SCAR-R01 | 登记回复必须上传供应商回复文件 | `请上传供应商的回复文件` |
| QC-SCAR-R02 | 逾期未回复每 3 天提醒 SQE，并抄送负责采购员 | — |
| QC-SCAR-R03 | 结案或逾期未回复的 SCAR 数量计入当期供应商服务评分参考（在评估明细中列出） | — |
| QC-SCAR-R04 | 验证期间：参数可设置该供应商该物料的后续 IQC 使用加严抽样（检验水平提高一级，P2） | — |

## 5. 接口

`/quality/scars`（CRUD、send、reply、start-verify、verify、cancel、print-data）。

## 6. 验收用例

| 编号 | 前置条件 | 操作 | 预期结果 |
|---|---|---|---|
| QC-SCAR-T01 | NCR 责任供应商 | 生成 SCAR 并发出 | 状态已发出，回复期限 7 天后 |
| QC-SCAR-T02 | 无附件 | 登记回复 | 提示“请上传供应商的回复文件” |
| QC-SCAR-T03 | 验证无效 | — | 回到已发出，无效次数 1 |
