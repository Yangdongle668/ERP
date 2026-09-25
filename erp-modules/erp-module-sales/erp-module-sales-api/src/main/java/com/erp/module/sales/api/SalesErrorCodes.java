package com.erp.module.sales.api;

import com.erp.common.exception.ErrorCode;

/** 销售模块错误码，号段 1_004_000_000 ~ 1_004_999_999。消息中的 {} 由 BizException.of 填充。 */
public interface SalesErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode SALES_PLACEHOLDER = new ErrorCode(1_004_000_000, "销售模块错误");

    // ==================== 通用 000 ====================
    ErrorCode DOC_NOT_EDITABLE = new ErrorCode(1_004_000_001, "只有草稿状态的单据可以修改");
    ErrorCode DOC_NO_LINES = new ErrorCode(1_004_000_002, "请至少添加一行明细");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_004_000_003, "请填写{}原因");
    ErrorCode LINE_FIELD_REQUIRED = new ErrorCode(1_004_000_004, "第 {} 行：{}不能为空");
    ErrorCode DOC_PENDING = new ErrorCode(1_004_000_005, "单据正在审批中，请先撤回");
    ErrorCode LINE_QTY_POSITIVE = new ErrorCode(1_004_000_006, "第 {} 行数量必须大于 0");
    ErrorCode LINE_PRICE_NEGATIVE = new ErrorCode(1_004_000_007, "第 {} 行单价不能为负数");
    ErrorCode EXCHANGE_RATE_POSITIVE = new ErrorCode(1_004_000_008, "汇率必须大于 0");
    ErrorCode CUSTOMER_PART_NOT_MAPPED = new ErrorCode(1_004_000_009, "客户料号「{}」没有对照的物料，请先维护客户料号对照");
    ErrorCode NO_COST_VIEW = new ErrorCode(1_004_000_010, "没有查看成本的权限");

    // ==================== 价格表 001 ====================
    ErrorCode PRICE_LIST_NOT_EXISTS = new ErrorCode(1_004_001_000, "价格表不存在");
    ErrorCode PRICE_ZERO_TIER = new ErrorCode(1_004_001_001, "物料「{}」必须有起始数量为 0 的阶梯");
    ErrorCode PRICE_LIST_APPROVED = new ErrorCode(1_004_001_002, "已审核的价格表不能修改");
    ErrorCode PRICE_LIST_DATE_RANGE = new ErrorCode(1_004_001_003, "失效日期不能早于生效日期");
    ErrorCode PRICE_LIST_SCOPE = new ErrorCode(1_004_001_004, "适用范围为{}时请选择{}");
    ErrorCode PRICE_TIER_DUPLICATE = new ErrorCode(1_004_001_005, "物料「{}」单位 {} 的起始数量 {} 重复");

    // ==================== RFQ 002 ====================
    ErrorCode RFQ_NOT_EXISTS = new ErrorCode(1_004_002_000, "RFQ 不存在");
    ErrorCode RFQ_STATUS = new ErrorCode(1_004_002_001, "RFQ 当前状态为{}，不能{}");
    ErrorCode RFQ_QTY_BREAKS = new ErrorCode(1_004_002_002, "第 {} 行数量阶梯格式不正确，请输入逗号分隔的正数");
    ErrorCode RFQ_ASSIGN_REQUIRED = new ErrorCode(1_004_002_003, "请选择工程师或成本工程师");
    ErrorCode RFQ_LINE_NOT_EXISTS = new ErrorCode(1_004_002_004, "RFQ 行不存在");
    ErrorCode RFQ_NOT_ASSIGNEE = new ErrorCode(1_004_002_005, "只有被分派的工程师或有分派权限的用户可以评估");
    ErrorCode RFQ_LINE_NO_MATERIAL = new ErrorCode(1_004_002_006, "第 {} 行没有关联本厂物料，不能核算");
    ErrorCode RFQ_NO_BOM = new ErrorCode(1_004_002_007, "物料「{}」没有已审核的 BOM，请工程先建立 BOM");
    ErrorCode RFQ_NOTHING_TO_QUOTE = new ErrorCode(1_004_002_008, "没有可报价的行（需要关联物料且可行性不为 NG）");
    ErrorCode RFQ_QTY_NOT_IN_BREAKS = new ErrorCode(1_004_002_009, "核算数量 {} 不在该行的数量阶梯中");

    // ==================== 报价 003 ====================
    ErrorCode QUOTATION_NOT_EXISTS = new ErrorCode(1_004_003_000, "报价单不存在");
    ErrorCode QUOTATION_STATUS = new ErrorCode(1_004_003_001, "报价单当前状态为{}，不能{}");
    ErrorCode QUOTATION_TIER_DUPLICATE = new ErrorCode(1_004_003_002, "物料「{}」的阶梯数量重复");
    ErrorCode QUOTATION_TIER_START = new ErrorCode(1_004_003_003, "物料「{}」必须有一档起始数量为 0 或 MOQ");
    ErrorCode QUOTATION_EXPIRED = new ErrorCode(1_004_003_004, "报价已过有效期，请修订后再转订单");
    ErrorCode QUOTATION_REVISED = new ErrorCode(1_004_003_005, "该报价已修订，请使用最新版本");
    ErrorCode QUOTATION_CUSTOMER_NOT_ACTIVE = new ErrorCode(1_004_003_006, "客户「{}」不是正式客户，请先转为正式客户");
    ErrorCode QUOTATION_VALID_UNTIL = new ErrorCode(1_004_003_007, "有效期不能早于单据日期");
    ErrorCode QUOTATION_LOST_REASON = new ErrorCode(1_004_003_008, "请选择未成交原因");
    ErrorCode QUOTATION_TO_ORDER_EMPTY = new ErrorCode(1_004_003_009, "请选择要下单的行并填写数量");
    ErrorCode QUOTATION_CUSTOMER_BLOCKED = new ErrorCode(1_004_003_010, "客户「{}」已停用或在黑名单中，不能报价");

    // ==================== 订单 004 ====================
    ErrorCode ORDER_NOT_EXISTS = new ErrorCode(1_004_004_000, "销售订单不存在");
    ErrorCode ORDER_LINE_NOT_EXISTS = new ErrorCode(1_004_004_001, "销售订单行不存在");
    ErrorCode ORDER_PO_DUPLICATE = new ErrorCode(1_004_004_002, "客户 PO 号「{}」已存在于订单「{}」");
    ErrorCode ORDER_REQUIRED_BEFORE_DOC = new ErrorCode(1_004_004_003, "第 {} 行要求交期不能早于单据日期");
    ErrorCode ORDER_BELOW_MOQ = new ErrorCode(1_004_004_004, "物料「{}」数量低于最小订购量 {}");
    ErrorCode ORDER_CREDIT_BLOCKED = new ErrorCode(1_004_004_005, "{}");
    ErrorCode ORDER_CREDIT_CONFIRM = new ErrorCode(1_004_004_006, "{}");
    ErrorCode ORDER_CANNOT_UNAPPROVE = new ErrorCode(1_004_004_007, "订单已有出货通知/收款/生产订单，不能反审核，请使用订单变更");
    ErrorCode ORDER_HAS_OPEN_NOTICE = new ErrorCode(1_004_004_008, "订单还有未完成的出货通知「{}」");
    ErrorCode ORDER_STATUS = new ErrorCode(1_004_004_009, "订单当前状态为{}，不能{}");
    ErrorCode ORDER_SHIP_ADDRESS_REQUIRED = new ErrorCode(1_004_004_010, "请选择收货地址");
    ErrorCode ORDER_OVER_NOTICE = new ErrorCode(1_004_004_011, "订单 {} 第 {} 行可通知数量为 {}，本次 {}");
    ErrorCode ORDER_OVER_SHIP = new ErrorCode(1_004_004_012, "订单 {} 第 {} 行已出货 {}，超过允许数量 {}");
    ErrorCode ORDER_LINE_CLOSED = new ErrorCode(1_004_004_013, "订单 {} 第 {} 行已关闭");
    ErrorCode ORDER_PROMISED_PAST = new ErrorCode(1_004_004_014, "承诺交期不能早于今天");
    ErrorCode ORDER_OVER_INVOICE = new ErrorCode(1_004_004_015, "订单 {} 第 {} 行开票数量超过已出货数量");
    ErrorCode ORDER_TYPE_INVALID = new ErrorCode(1_004_004_016, "订单类型不正确");
    ErrorCode ORDER_CHANGE_RUNNING = new ErrorCode(1_004_004_017, "订单已有未完成的变更单「{}」");
    ErrorCode ORDER_IMPORT_CUSTOMER = new ErrorCode(1_004_004_018, "客户编码「{}」不存在");

    // ==================== 订单变更 005 ====================
    ErrorCode CHANGE_NOT_EXISTS = new ErrorCode(1_004_005_000, "订单变更单不存在");
    ErrorCode CHANGE_ORDER_STATUS = new ErrorCode(1_004_005_001, "只有已审核或执行中的订单可以变更");
    ErrorCode CHANGE_QTY_BELOW_NOTICED = new ErrorCode(1_004_005_002, "第 {} 行新数量不能小于已通知出货数量 {}");
    ErrorCode CHANGE_CANCEL_NOTICED = new ErrorCode(1_004_005_003, "第 {} 行已有出货通知，不能取消");
    ErrorCode CHANGE_INVOICED_PRICE = new ErrorCode(1_004_005_004, "第 {} 行已开票，不能修改单价");
    ErrorCode CHANGE_ORDER_MODIFIED = new ErrorCode(1_004_005_005, "订单已被修改，请作废本变更单后重新发起");
    ErrorCode CHANGE_NOTHING = new ErrorCode(1_004_005_006, "没有任何变更内容");
    ErrorCode CHANGE_LINE_INVALID = new ErrorCode(1_004_005_007, "第 {} 行不是该订单的有效行");
    ErrorCode CHANGE_REQUIRED_PAST = new ErrorCode(1_004_005_008, "第 {} 行新要求交期不能早于今天");

    // ==================== 预测 006 ====================
    ErrorCode FORECAST_NOT_EXISTS = new ErrorCode(1_004_006_000, "销售预测不存在");
    ErrorCode FORECAST_PERIOD_PAST = new ErrorCode(1_004_006_001, "预测月份不能早于当前月");
    ErrorCode FORECAST_PERIOD_RANGE = new ErrorCode(1_004_006_002, "预测跨度不能超过 12 个月");
    ErrorCode FORECAST_PERIOD_FORMAT = new ErrorCode(1_004_006_003, "月份格式应为 yyyyMM：{}");
    ErrorCode FORECAST_CONFLICT = new ErrorCode(1_004_006_004, "物料「{}」{}已在预测「{}」中");
    ErrorCode FORECAST_LINE_OUT_OF_RANGE = new ErrorCode(1_004_006_005, "物料「{}」的月份 {} 不在预测起止月份内");
    ErrorCode FORECAST_LINE_DUPLICATE = new ErrorCode(1_004_006_006, "物料「{}」{}重复");
    ErrorCode FORECAST_STATUS = new ErrorCode(1_004_006_007, "预测当前状态为{}，不能{}");

    // ==================== 退货 007 ====================
    ErrorCode RETURN_NOT_EXISTS = new ErrorCode(1_004_007_000, "销售退货单不存在");
    ErrorCode RETURN_OVER_QTY = new ErrorCode(1_004_007_001, "第 {} 行退货数量超过可退数量 {}");
    ErrorCode RETURN_STOCKED = new ErrorCode(1_004_007_002, "退货已入库，不能作废");
    ErrorCode RETURN_CUSTOMER_MISMATCH = new ErrorCode(1_004_007_003, "第 {} 行不是该客户的订单");
    ErrorCode RETURN_JUDGE_OVER = new ErrorCode(1_004_007_004, "第 {} 行判定数量合计超过已收货数量 {}");
    ErrorCode RETURN_STATUS = new ErrorCode(1_004_007_005, "退货单当前状态为{}，不能{}");
    ErrorCode RETURN_LINE_NOT_EXISTS = new ErrorCode(1_004_007_006, "退货单行不存在");

    // ==================== 回款计划 008 ====================
    ErrorCode PAYMENT_PLAN_NOT_EXISTS = new ErrorCode(1_004_008_000, "回款计划不存在");
}
