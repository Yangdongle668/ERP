package com.erp.module.production.api;

import com.erp.common.exception.ErrorCode;

/** 生产模块错误码，号段 1_009_000_000 ~ 1_009_999_999。消息中的 {} 由 BizException.of 填充。 */
public interface ProductionErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode PRODUCTION_PLACEHOLDER = new ErrorCode(1_009_000_000, "生产模块错误");

    // ==================== 通用 000 ====================
    ErrorCode DOC_NOT_EDITABLE = new ErrorCode(1_009_000_001, "只有草稿状态的单据可以修改");
    ErrorCode DOC_NO_LINES = new ErrorCode(1_009_000_002, "请至少添加一行明细");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_009_000_003, "请填写{}原因");
    ErrorCode LINE_FIELD_REQUIRED = new ErrorCode(1_009_000_004, "第 {} 行：{}不能为空");
    ErrorCode DOC_PENDING = new ErrorCode(1_009_000_005, "单据正在审批中，请先撤回");
    ErrorCode LINE_QTY_POSITIVE = new ErrorCode(1_009_000_006, "第 {} 行数量必须大于 0");
    ErrorCode STATUS_NOT_ALLOWED = new ErrorCode(1_009_000_007, "当前状态【{}】不能{}");

    // ==================== 生产订单 001 ====================
    ErrorCode ORDER_NOT_EXISTS = new ErrorCode(1_009_001_000, "生产订单不存在");
    ErrorCode ORDER_NO_BOM = new ErrorCode(1_009_001_001, "产品「{}」没有已审核的 BOM");
    ErrorCode ORDER_DATE_RANGE = new ErrorCode(1_009_001_002, "计划完工日期不能早于开工日期");
    ErrorCode ORDER_NOT_KIT = new ErrorCode(1_009_001_003, "以下物料不齐套：{}");
    ErrorCode ORDER_MATERIAL_NOT_MAKE = new ErrorCode(1_009_001_004, "物料「{}」不是自制件，不能生产");
    ErrorCode ORDER_UNRELEASE_BLOCKED = new ErrorCode(1_009_001_005, "生产订单已领料或已报工，不能撤销下达");
    ErrorCode ORDER_REQUIRED_BELOW_ISSUED = new ErrorCode(1_009_001_006, "物料「{}」应领数量不能小于已领数量 {}");
    ErrorCode ORDER_QTY_BELOW_COMPLETED = new ErrorCode(1_009_001_007, "计划数量不能小于已完工数量 {}");
    ErrorCode ORDER_OPEN_DOCS = new ErrorCode(1_009_001_008, "还有未完成的领料/退料/入库单据：{}");
    ErrorCode ORDER_REMAINING_MATERIAL = new ErrorCode(1_009_001_009, "还有余料未退回：{}");
    ErrorCode ORDER_SUSPENDED = new ErrorCode(1_009_001_010, "生产订单已暂停");
    ErrorCode ORDER_NOT_RUNNING = new ErrorCode(1_009_001_011, "生产订单【{}】不是已下达或生产中状态");
    ErrorCode ORDER_MATERIAL_DELETE_ISSUED = new ErrorCode(1_009_001_012, "物料「{}」已领料，不能删除用料行");
    ErrorCode ORDER_PENDING_DEFECT = new ErrorCode(1_009_001_013, "还有 {} 待处理不良");
    ErrorCode ORDER_SALES_LINE_MISMATCH = new ErrorCode(1_009_001_014, "销售订单行的物料与生产订单产品不一致");
    ErrorCode ORDER_SUBSTITUTE_INVALID = new ErrorCode(1_009_001_015, "物料「{}」不是该用料行在 BOM 中定义的替代料");
    ErrorCode ORDER_ADJUST_REASON = new ErrorCode(1_009_001_016, "请填写调整用料的原因");
    ErrorCode ORDER_UNREPORTED_WIP = new ErrorCode(1_009_001_017, "还有未完工的在制数量 {}，关闭后按报废处理，请确认");
    ErrorCode ORDER_CLOSE_CONFIRM = new ErrorCode(1_009_001_018, "关闭前请确认：{}");
    ErrorCode ORDER_TYPE_UNSUPPORTED = new ErrorCode(1_009_001_019, "暂不支持{}类型的生产订单");
    ErrorCode ORDER_REWORK_MATERIALS = new ErrorCode(1_009_001_020, "返工订单请至少录入一行投入物料");
    ErrorCode ORDER_PRIORITY = new ErrorCode(1_009_001_021, "优先级必须在 1～9 之间");

    // ==================== 工单 002 ====================
    ErrorCode WORK_ORDER_NOT_EXISTS = new ErrorCode(1_009_002_000, "工单不存在");
    ErrorCode WORK_ORDER_OVER_QTY = new ErrorCode(1_009_002_001, "派工数量超过工序可派数量 {}");
    ErrorCode WORK_ORDER_REPORTED = new ErrorCode(1_009_002_002, "工单已有报工，不能取消");
    ErrorCode WORK_ORDER_WC_DEPT = new ErrorCode(1_009_002_003, "工作中心「{}」不属于生产订单的车间");
    ErrorCode WORK_ORDER_CLOSED = new ErrorCode(1_009_002_004, "工单已完成或已取消");

    // ==================== 领料 003 ====================
    ErrorCode ISSUE_NOT_EXISTS = new ErrorCode(1_009_003_000, "领料单不存在");
    ErrorCode ISSUE_OVER = new ErrorCode(1_009_003_001, "物料「{}」申请数量超过未领数量 {}，请走超领");
    ErrorCode ISSUE_BACKFLUSH = new ErrorCode(1_009_003_002, "物料「{}」为倒冲物料，不需要领料");
    ErrorCode ISSUE_BACKFLUSH_SHORT = new ErrorCode(1_009_003_003, "倒冲物料「{}」库存不足，需要 {}，可用 {}");
    ErrorCode ISSUE_OVER_REASON = new ErrorCode(1_009_003_004, "超领必须选择超领原因");
    ErrorCode ISSUE_WITHDRAW_CONFIRMED = new ErrorCode(1_009_003_005, "出库单已确认，不能撤回");
    ErrorCode ISSUE_NOTHING = new ErrorCode(1_009_003_006, "没有需要领料的物料");
    ErrorCode ISSUE_LINE_INVALID = new ErrorCode(1_009_003_007, "第 {} 行不是该生产订单的用料");
    ErrorCode ISSUE_BACKFLUSH_CONFIRMED = new ErrorCode(1_009_003_008, "倒冲出库单已确认（{}），请先在仓库反确认后再反审核报工");
    ErrorCode ISSUE_OVER_QTY = new ErrorCode(1_009_003_009, "超领数量必须大于 0");

    // ==================== 退料 004 ====================
    ErrorCode RETURN_NOT_EXISTS = new ErrorCode(1_009_004_000, "退料单不存在");
    ErrorCode RETURN_OVER = new ErrorCode(1_009_004_001, "物料「{}」退料数量超过可退数量 {}");
    ErrorCode RETURN_DEFECT_DESC = new ErrorCode(1_009_004_002, "物料「{}」不良退料必须填写不良描述");
    ErrorCode RETURN_OUTPUT_NOT_DISASSEMBLY = new ErrorCode(1_009_004_003, "只有拆解订单可以办理拆解入库");

    // ==================== 报工 005 ====================
    ErrorCode REPORT_NOT_EXISTS = new ErrorCode(1_009_005_000, "报工单不存在");
    ErrorCode REPORT_NOT_POINT = new ErrorCode(1_009_005_001, "工序 {} 不是报工点");
    ErrorCode REPORT_OVER_QTY = new ErrorCode(1_009_005_002, "本工序可报数量为 {}");
    ErrorCode REPORT_DATE_INVALID = new ErrorCode(1_009_005_003, "报工日期不能晚于今天，也不能早于生产订单下达日期 {}");
    ErrorCode REPORT_SCRAP_REASON = new ErrorCode(1_009_005_004, "报废数量大于 0 时必须选择报废原因");
    ErrorCode REPORT_HOURS_POSITIVE = new ErrorCode(1_009_005_005, "工时必须大于 0");
    ErrorCode REPORT_OPERATOR_HOURS = new ErrorCode(1_009_005_006, "人员工时合计 {} 与报工工时 {} 不一致");
    ErrorCode REPORT_NEXT_REPORTED = new ErrorCode(1_009_005_007, "下道工序已报工，不能反审核");
    ErrorCode REPORT_FINISH_REQUESTED = new ErrorCode(1_009_005_008, "已申请完工入库，不能反审核");
    ErrorCode REPORT_WORK_ORDER_REQUIRED = new ErrorCode(1_009_005_009, "报工必须选择工单");
    ErrorCode REPORT_QTY_ZERO = new ErrorCode(1_009_005_010, "合格、不良、报废数量不能都为 0");
    ErrorCode REPORT_NOT_DRAFT = new ErrorCode(1_009_005_011, "只有草稿状态的报工单可以修改或审核");
    ErrorCode REPORT_BARCODE_UNKNOWN = new ErrorCode(1_009_005_012, "条码「{}」不是有效的工单或生产订单");
    ErrorCode REPORT_WORK_ORDER_MISMATCH = new ErrorCode(1_009_005_013, "工单不属于该生产订单的工序 {}");

    // ==================== 完工入库 006 ====================
    ErrorCode FINISH_NOT_EXISTS = new ErrorCode(1_009_006_000, "完工入库申请不存在");
    ErrorCode FINISH_OVER = new ErrorCode(1_009_006_001, "可申请入库数量为 {}");
    ErrorCode FINISH_STOCKED = new ErrorCode(1_009_006_002, "入库单已确认，不能取消");
    ErrorCode FINISH_SERIAL_REQUIRED = new ErrorCode(1_009_006_003, "产品「{}」为序列号管理，请录入 {} 个序列号");
    ErrorCode FINISH_DISASSEMBLY = new ErrorCode(1_009_006_004, "拆解订单没有产品完工入库，子件请通过“拆解入库”退料单入库");

    // ==================== 不良 007 ====================
    ErrorCode DEFECT_NOT_EXISTS = new ErrorCode(1_009_007_000, "不良记录不存在");
    ErrorCode DEFECT_SUM_MISMATCH = new ErrorCode(1_009_007_001, "不良明细合计 {} 与不良数 {} 不一致");
    ErrorCode DEFECT_OVER_HANDLE = new ErrorCode(1_009_007_002, "处置数量超过待处理数量");
    ErrorCode DEFECT_NCR_UNAVAILABLE = new ErrorCode(1_009_007_003, "品质模块尚未启用，不能生成 NCR");
    ErrorCode DEFECT_NCR_EXISTS = new ErrorCode(1_009_007_004, "该不良已生成 NCR {}");
    ErrorCode DEFECT_HANDLED = new ErrorCode(1_009_007_005, "报工登记的不良已处置（返修/报废），不能反审核");

    // ==================== 追溯 008 ====================
    ErrorCode TRACE_NO_BATCH = new ErrorCode(1_009_008_001, "该物料未启用批次管理，无法精确追溯");
}
