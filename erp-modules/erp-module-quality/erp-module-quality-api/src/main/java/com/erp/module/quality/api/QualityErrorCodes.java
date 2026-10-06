package com.erp.module.quality.api;

import com.erp.common.exception.ErrorCode;

/** 品质模块错误码，号段 1_010_000_000 ~ 1_010_999_999。 */
public interface QualityErrorCodes {

    // ========== 通用 000 ==========
    ErrorCode QUALITY_ERROR = new ErrorCode(1_010_000_000, "品质模块错误");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_010_000_001, "请填写{}");
    ErrorCode STATUS_NOT_ALLOWED = new ErrorCode(1_010_000_002, "当前状态【{}】不能{}");
    ErrorCode DOC_PENDING = new ErrorCode(1_010_000_003, "单据审批中，请先撤回");
    ErrorCode CODE_DUPLICATE = new ErrorCode(1_010_000_004, "编码「{}」已存在");
    ErrorCode NOT_EXISTS = new ErrorCode(1_010_000_005, "{}不存在");
    ErrorCode IN_USE = new ErrorCode(1_010_000_006, "{}已被使用，不能删除");

    // ========== 检验基础数据 001 ==========
    ErrorCode STD_ITEM_LIMIT_REQUIRED = new ErrorCode(1_010_001_000, "项目「{}」请填写规格上下限");
    ErrorCode STD_ITEM_LIMIT_INVALID = new ErrorCode(1_010_001_001, "项目「{}」的上限不能小于下限");
    ErrorCode STD_EFFECTIVE_LOCKED = new ErrorCode(1_010_001_002, "已生效的检验标准不能修改");
    ErrorCode SAMPLING_AQL_REQUIRED = new ErrorCode(1_010_001_003, "请填写检验水平和 AQL");
    ErrorCode SAMPLING_AQL_INVALID = new ErrorCode(1_010_001_004, "AQL 值「{}」不在标准 AQL 系列中");
    ErrorCode SAMPLING_FIXED_QTY = new ErrorCode(1_010_001_005, "固定数量方案请填写样本数量");
    ErrorCode STD_SCOPE_REQUIRED = new ErrorCode(1_010_001_006, "请选择适用的物料或物料类别");
    ErrorCode STD_NO_ITEMS = new ErrorCode(1_010_001_007, "请至少添加一个检验项目");
    ErrorCode STD_NOT_DRAFT = new ErrorCode(1_010_001_008, "只有草稿状态的检验标准可以{}");
    ErrorCode STD_DRAFT_EXISTS = new ErrorCode(1_010_001_009, "该标准已有草稿版本 V{}");
    ErrorCode SAMPLING_DISABLED = new ErrorCode(1_010_001_010, "抽样方案「{}」已停用");
    ErrorCode LOT_QTY_INVALID = new ErrorCode(1_010_001_011, "批量必须大于 0");
    ErrorCode AQL_LETTER_INVALID = new ErrorCode(1_010_001_012, "样本量字码「{}」不正确");
    ErrorCode AQL_PLAN_INVALID = new ErrorCode(1_010_001_013, "样本量必须大于 0，且 0 ≤ Ac < Re");

    // ========== 检验单 002 ==========
    ErrorCode INS_JUDGE_QTY = new ErrorCode(1_010_002_000, "判定数量合计必须等于批量 {}");
    ErrorCode INS_CONCESSION_MRB = new ErrorCode(1_010_002_001, "特采需要提交 MRB 审批");
    ErrorCode INS_TRANSFER_DONE = new ErrorCode(1_010_002_002, "检验调拨已完成，请先由仓库反确认");
    ErrorCode INS_JUDGED_REVERSE = new ErrorCode(1_010_002_003, "已检验判定，不能反确认");
    ErrorCode INS_FIRST_ARTICLE = new ErrorCode(1_010_002_004, "首件检验未通过，不能报工");
    ErrorCode INS_PASS_REASON = new ErrorCode(1_010_002_005, "建议结果为不合格，判定合格请填写让步理由");
    ErrorCode INS_NO_PERMISSION = new ErrorCode(1_010_002_006, "没有{}的{}权限");
    ErrorCode INS_SAMPLE_VALUES = new ErrorCode(1_010_002_007, "项目「{}」的测量值数量不能超过样本量 {}");
    ErrorCode INS_MANUAL_TYPE = new ErrorCode(1_010_002_008, "手工只能新建 IPQC（首件、巡检、末件）或复检检验单");
    ErrorCode INS_SORT_ONLY = new ErrorCode(1_010_002_009, "MRB 处置为挑选，请录入挑选后的良品和不良数量后按“挑选”判定");
    ErrorCode INS_NOT_JUDGED = new ErrorCode(1_010_002_010, "检验单还未判定，不能重判");
    ErrorCode INS_BATCH_JUDGE_SKIP = new ErrorCode(1_010_002_011, "只有已录入且建议结果为合格的检验单可以批量判定");
    ErrorCode INS_REJECTED_LOCKED = new ErrorCode(1_010_002_012, "MRB 已判定不合格数量 {}，挑选不良数量不能少于该数量");
    ErrorCode INS_PROD_ORDER = new ErrorCode(1_010_002_013, "生产订单不存在或已完工");

    // ========== NCR 003 ==========
    ErrorCode NCR_DISP_SUM = new ErrorCode(1_010_003_000, "处置数量合计 {} 必须等于不合格数量 {}");
    ErrorCode NCR_DISP_NOT_ALLOWED = new ErrorCode(1_010_003_001, "该来源不支持处置方式「{}」");
    ErrorCode NCR_DISP_UNDONE = new ErrorCode(1_010_003_002, "还有处置未完成");
    ErrorCode NCR_CAPA_REQUIRED = new ErrorCode(1_010_003_003, "请先生成 CAPA");
    ErrorCode NCR_SCAR_REQUIRED = new ErrorCode(1_010_003_004, "请先生成 SCAR");
    ErrorCode NCR_QTY_INVALID = new ErrorCode(1_010_003_005, "不合格数量必须大于 0");
    ErrorCode NCR_NO_DISP = new ErrorCode(1_010_003_006, "请填写处置明细");
    ErrorCode NCR_FOLLOW_EXISTS = new ErrorCode(1_010_003_007, "已生成{}：{}");
    ErrorCode NCR_NO_DISP_OF = new ErrorCode(1_010_003_008, "NCR 没有「{}」处置");
    ErrorCode NCR_SCAR_NO_SUPPLIER = new ErrorCode(1_010_003_009, "NCR 没有供应商，不能生成 SCAR");
    ErrorCode NCR_DOWNGRADE_TARGET = new ErrorCode(1_010_003_010, "降级使用请选择降级后的物料（不能与不合格物料相同）");

    // ========== CAPA 004 ==========
    ErrorCode CAPA_STEP_ORDER = new ErrorCode(1_010_004_000, "请按顺序完成步骤，当前为 D{}");
    ErrorCode CAPA_STEP_EMPTY = new ErrorCode(1_010_004_001, "请填写 D{} 内容");
    ErrorCode CAPA_NOT_MEMBER = new ErrorCode(1_010_004_002, "只有负责人或小组成员可以编辑");
    ErrorCode CAPA_VERIFY_STEP = new ErrorCode(1_010_004_003, "D5 完成后才能进行效果验证");
    ErrorCode CAPA_CLOSE_STEP = new ErrorCode(1_010_004_004, "D1～D7 全部完成后才能结案");

    // ========== 客诉 005 ==========
    ErrorCode CPL_CLOSE_MISSING = new ErrorCode(1_010_005_000, "结案前需要：{}");
    ErrorCode CPL_REPLY_REQUIRED = new ErrorCode(1_010_005_001, "请填写回复内容");

    // ========== SCAR 006 ==========
    ErrorCode SCAR_REPLY_FILE = new ErrorCode(1_010_006_000, "请上传供应商的回复文件");
    ErrorCode SCAR_REPLY_REQUIRED = new ErrorCode(1_010_006_001, "请填写供应商回复摘要");

    // ========== 追溯 007 ==========
    ErrorCode TRACE_NCR_REQUIRED = new ErrorCode(1_010_007_000, "冻结批次需要关联 NCR");
}
