package com.erp.module.finance.api;

import com.erp.common.exception.ErrorCode;

/** 财务模块错误码，号段 1_012_000_000 ~ 1_012_999_999。 */
public interface FinanceErrorCodes {

    // ========== 通用 000 ==========
    ErrorCode FINANCE_ERROR = new ErrorCode(1_012_000_000, "财务模块错误");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_012_000_001, "请填写{}");
    ErrorCode STATUS_NOT_ALLOWED = new ErrorCode(1_012_000_002, "当前状态【{}】不能{}");
    ErrorCode DOC_PENDING = new ErrorCode(1_012_000_003, "单据审批中，请先撤回");
    ErrorCode NOT_EXISTS = new ErrorCode(1_012_000_004, "{}不存在");
    ErrorCode NO_LINES = new ErrorCode(1_012_000_005, "请至少添加一行明细");
    ErrorCode PERIOD_CLOSED = new ErrorCode(1_012_000_006, "会计期间 {} 已结账");
    ErrorCode AMOUNT_INVALID = new ErrorCode(1_012_000_007, "{}必须大于 0");
    ErrorCode CODE_DUPLICATE = new ErrorCode(1_012_000_008, "编码「{}」已存在");

    // ========== 基础设置 001 ==========
    ErrorCode SET_ACCOUNT_USED = new ErrorCode(1_012_001_000, "科目已使用，不能新增下级");
    ErrorCode SET_NOT_LEAF = new ErrorCode(1_012_001_001, "科目「{}」不是末级科目");
    ErrorCode SET_PARENT_PREFIX = new ErrorCode(1_012_001_002, "下级科目编码必须以上级编码「{}」开头");
    ErrorCode SET_DEFAULT_MAPPING_EXISTS = new ErrorCode(1_012_001_003, "业务类型「{}」已有默认映射");
    ErrorCode SET_ACCOUNT_DISABLED = new ErrorCode(1_012_001_004, "科目「{}」已停用");
    ErrorCode SET_ACCOUNT_HAS_CHILDREN = new ErrorCode(1_012_001_005, "科目「{}」有下级科目，不能删除");
    ErrorCode SET_YEAR_EXISTS = new ErrorCode(1_012_001_006, "{} 年的会计期间已初始化");

    // ========== 应收 002 ==========
    ErrorCode AR_BLOCK_REVERSE = new ErrorCode(1_012_002_000, "该出货已开票/已收款核销，不能反确认");
    ErrorCode AR_INVOICE_NO_DUPLICATE = new ErrorCode(1_012_002_001, "发票号码「{}」已登记");
    ErrorCode AR_INVOICE_QTY_EXCEED = new ErrorCode(1_012_002_002, "第 {} 行开票数量超过未开票数量 {}");
    ErrorCode AR_CUSTOMER_MIXED = new ErrorCode(1_012_002_003, "请选择同一客户、同一币别的应收");
    ErrorCode AR_PROCESSED = new ErrorCode(1_012_002_004, "应收已核销或已开票，不能{}");
    ErrorCode AR_INVOICE_DIFF = new ErrorCode(1_012_002_005, "开票金额调整 {} 超过尾差 1 元");

    // ========== 收款与核销 003 ==========
    ErrorCode RV_CURRENCY_MISMATCH = new ErrorCode(1_012_003_000, "收款币别必须与收款账户币别一致");
    ErrorCode RV_UNBALANCED = new ErrorCode(1_012_003_001, "核销金额不平衡");
    ErrorCode RV_VERIFIED = new ErrorCode(1_012_003_002, "收款已核销，请先反核销");
    ErrorCode RV_ORDER_REQUIRED = new ErrorCode(1_012_003_003, "预收款请选择销售订单");
    ErrorCode VF_EXCEED = new ErrorCode(1_012_003_004, "「{}」本次核销金额超过未核销金额 {}");
    ErrorCode VF_PARTNER_MISMATCH = new ErrorCode(1_012_003_005, "核销双方必须是同一{}、同一币别");
    ErrorCode VF_PERIOD_CLOSED = new ErrorCode(1_012_003_006, "会计期间已结账，不能反核销");
    ErrorCode VF_ADVANCE_ORDER = new ErrorCode(1_012_003_007, "预收款只能核销订单「{}」产生的应收");
    ErrorCode VF_NOTHING = new ErrorCode(1_012_003_008, "请选择要核销的单据并输入金额");
    ErrorCode VF_REFUND = new ErrorCode(1_012_003_009, "退款只能核销红字应收");
    ErrorCode RV_EXCHANGE_RATE = new ErrorCode(1_012_003_010, "汇率必须大于 0");

    // ========== 应付与发票 004 ==========
    ErrorCode AP_BLOCK_UNCONFIRM = new ErrorCode(1_012_004_000, "财务已根据此对账单生成应付并已处理，不能取消确认");
    ErrorCode AP_INVOICE_DUPLICATE = new ErrorCode(1_012_004_001, "发票「{}」已登记");
    ErrorCode AP_INVOICE_DIFF = new ErrorCode(1_012_004_002, "发票金额与明细合计差异 {} 超过容差");
    ErrorCode AP_INVOICE_QTY_EXCEED = new ErrorCode(1_012_004_003, "第 {} 行发票数量超过未开票数量 {}");
    ErrorCode AP_DIFF_REASON = new ErrorCode(1_012_004_004, "第 {} 行单价差异 {}% 超过容差，请填写差异原因");
    ErrorCode AP_PROCESSED = new ErrorCode(1_012_004_005, "应付已匹配发票、已申请付款或已付款，不能{}");

    // ========== 付款 005 ==========
    ErrorCode PAY_REQUEST_EXCEED = new ErrorCode(1_012_005_000, "应付「{}」可申请金额为 {}");
    ErrorCode PAY_AMOUNT_EXCEED = new ErrorCode(1_012_005_001, "付款金额超过申请未付金额");
    ErrorCode PAY_CURRENCY_MISMATCH = new ErrorCode(1_012_005_002, "付款账户币别必须与申请币别一致");
    ErrorCode PAY_PREPAY_EXCEED = new ErrorCode(1_012_005_003, "预付金额超过订单金额");
    ErrorCode PAY_ORDER_REQUIRED = new ErrorCode(1_012_005_004, "预付款请选择采购订单");
    ErrorCode PAY_REQUEST_NOT_APPROVED = new ErrorCode(1_012_005_005, "付款申请尚未审批通过");
    ErrorCode PAY_SUPPLIER_MISMATCH = new ErrorCode(1_012_005_006, "应付「{}」不属于该供应商或币别不同");
    ErrorCode PAY_VERIFIED = new ErrorCode(1_012_005_007, "付款已用于核销，请先反核销");
    ErrorCode PAY_UNINVOICED = new ErrorCode(1_012_005_008, "应付「{}」尚未收到发票，不能申请付款");

    // ========== 凭证 006 ==========
    ErrorCode VCH_UNBALANCED = new ErrorCode(1_012_006_000, "借贷不平衡，差额 {}");
    ErrorCode VCH_SAME_AUDITOR = new ErrorCode(1_012_006_001, "审核人不能与制单人相同");
    ErrorCode VCH_AUDITED = new ErrorCode(1_012_006_002, "单据的凭证已审核，请先反审核凭证");
    ErrorCode VCH_NO_MAPPING = new ErrorCode(1_012_006_003, "业务类型「{}」没有可用的科目映射");
    ErrorCode VCH_AUX_REQUIRED = new ErrorCode(1_012_006_004, "第 {} 行科目「{}」需要辅助核算：{}");
    ErrorCode VCH_EMPTY = new ErrorCode(1_012_006_005, "没有可生成凭证的单据");

    // ========== 成本 007 ==========
    ErrorCode CST_INV_NOT_CLOSED = new ErrorCode(1_012_007_000, "库存期间 {} 尚未月结");
    ErrorCode CST_RUNNING = new ErrorCode(1_012_007_001, "成本计算正在进行中");
    ErrorCode CST_LOCKED = new ErrorCode(1_012_007_002, "期间 {} 成本已锁定");
    ErrorCode CST_NOT_CALCULATED = new ErrorCode(1_012_007_003, "期间 {} 尚未成功计算成本");

    // ========== 月结 008 ==========
    ErrorCode CLS_ORDER = new ErrorCode(1_012_008_000, "请先结账 {}");
    ErrorCode CLS_RATE_MISSING = new ErrorCode(1_012_008_001, "请先维护 {} {} 的月末汇率");
    ErrorCode CLS_BLOCKED = new ErrorCode(1_012_008_002, "结账检查未通过：{}");
    ErrorCode CLS_REOPEN_LATEST = new ErrorCode(1_012_008_003, "只能反结账最近一个已结账期间 {}");
    ErrorCode CLS_INV_CLOSED = new ErrorCode(1_012_008_004, "财务期间 {} 已结账，请先由财务反结账");
}
