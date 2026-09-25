package com.erp.module.purchase.api;

import com.erp.common.exception.ErrorCode;

/** 资材模块错误码，号段 1_007_000_000 ~ 1_007_999_999。提示原文与需求文档（07-资材）一致。 */
public interface PurchaseErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode PURCHASE_PLACEHOLDER = new ErrorCode(1_007_000_000, "资材模块错误");

    // ========== 通用 1_007_000_xxx ==========
    ErrorCode DOC_NOT_EDITABLE = new ErrorCode(1_007_000_001, "只有草稿状态的单据可以修改");
    ErrorCode DOC_NO_LINES = new ErrorCode(1_007_000_002, "请至少添加一行明细");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_007_000_003, "请填写{}原因");
    ErrorCode LINE_FIELD_REQUIRED = new ErrorCode(1_007_000_004, "第 {} 行：{}不能为空");
    ErrorCode DOC_PENDING = new ErrorCode(1_007_000_005, "单据正在审批中，请先撤回");
    ErrorCode NO_PRICE_VIEW = new ErrorCode(1_007_000_006, "没有查看采购价格的权限，不能新建或编辑{}");
    ErrorCode MATERIAL_NOT_PURCHASABLE = new ErrorCode(1_007_000_007, "物料「{}」取得方式为自制，不能申请采购");
    ErrorCode REQUIRED_DATE_PAST = new ErrorCode(1_007_000_008, "第 {} 行需求日期不能早于今天");
    ErrorCode LINE_QTY_POSITIVE = new ErrorCode(1_007_000_009, "第 {} 行数量必须大于 0");

    // ========== 供应商 1_007_001_xxx ==========
    ErrorCode SUPPLIER_NOT_EXISTS = new ErrorCode(1_007_001_000, "供应商不存在");
    ErrorCode SUPPLIER_DUPLICATE = new ErrorCode(1_007_001_001, "供应商「{}」已存在");
    ErrorCode SUPPLIER_QUALIFY_MISSING = new ErrorCode(1_007_001_002, "提交准入需要：{}");
    ErrorCode SUPPLIER_NOT_QUALIFIED = new ErrorCode(1_007_001_003, "供应商「{}」不是合格供应商");
    ErrorCode SUPPLIER_CERT_EXPIRED = new ErrorCode(1_007_001_004, "供应商「{}」的资质「{}」已过期");
    ErrorCode SUPPLIER_HAS_BIZ = new ErrorCode(1_007_001_005, "供应商已有业务数据，不能删除");
    ErrorCode SUPPLIER_MATERIAL_TRIAL = new ErrorCode(1_007_001_006, "物料「{}」在该供应商处为试用状态，只能下样品订单");
    ErrorCode SUPPLIER_SUSPENDED = new ErrorCode(1_007_001_007, "供应商「{}」已暂停合作");
    ErrorCode SUPPLIER_ELIMINATED = new ErrorCode(1_007_001_008, "供应商「{}」已淘汰");
    ErrorCode SUPPLIER_CONTACT_WAY = new ErrorCode(1_007_001_009, "联系人「{}」至少填写一种联系方式");
    ErrorCode SUPPLIER_MATERIAL_DUPLICATE = new ErrorCode(1_007_001_010, "可供物料「{}」重复");
    ErrorCode SUPPLIER_CODE_REQUIRED = new ErrorCode(1_007_001_011, "请填写供应商编码");
    ErrorCode SUPPLIER_TAX_NO_DUPLICATE = new ErrorCode(1_007_001_012, "税号「{}」已被供应商「{}」使用");
    ErrorCode SUPPLIER_NOT_DELETABLE = new ErrorCode(1_007_001_013, "只有潜在状态的供应商可以删除");
    ErrorCode SUPPLIER_OPEN_ORDERS = new ErrorCode(1_007_001_014, "该供应商还有 {} 张未完成采购订单");

    // ========== 采购价格 1_007_002_xxx ==========
    ErrorCode PRICE_ADJUST_NOT_EXISTS = new ErrorCode(1_007_002_000, "调价单不存在");
    ErrorCode PRICE_ZERO_TIER = new ErrorCode(1_007_002_001, "物料「{}」必须有起始数量为 0 的阶梯");
    ErrorCode PRICE_TIER_DUPLICATE = new ErrorCode(1_007_002_002, "物料「{}」的阶梯起始数量 {} 重复");
    ErrorCode PRICE_EFFECTIVE_PAST = new ErrorCode(1_007_002_003, "第 {} 行生效日期不能早于今天");
    ErrorCode PRICE_EFFECTIVE_RANGE = new ErrorCode(1_007_002_004, "第 {} 行失效日期不能早于生效日期");
    ErrorCode PRICE_NOT_POSITIVE = new ErrorCode(1_007_002_005, "第 {} 行新价格必须大于 0");
    ErrorCode PRICE_SUPPLIER_STATUS = new ErrorCode(1_007_002_006, "调价单只能选择潜在或合格供应商");
    ErrorCode PRICE_MATERIAL_NOT_SUPPLIED = new ErrorCode(1_007_002_007, "物料「{}」不是供应商「{}」的可供物料");

    // ========== 采购申请 1_007_003_xxx ==========
    ErrorCode REQ_NOT_EXISTS = new ErrorCode(1_007_003_000, "采购申请不存在");
    ErrorCode REQ_HAS_ORDER = new ErrorCode(1_007_003_001, "申请已转采购订单，不能反审核");
    ErrorCode REQ_LINE_NOT_PENDING = new ErrorCode(1_007_003_002, "申请「{}」第 {} 行不是待转订单状态");

    // ========== 询价 1_007_004_xxx ==========
    ErrorCode RFQ_NOT_EXISTS = new ErrorCode(1_007_004_000, "询价单不存在");
    ErrorCode RFQ_NEED_LINES = new ErrorCode(1_007_004_001, "发出询价至少需要 1 个物料、1 家供应商");
    ErrorCode RFQ_DEADLINE_PAST = new ErrorCode(1_007_004_002, "报价截止日期不能早于今天");
    ErrorCode RFQ_AWARD_PCT = new ErrorCode(1_007_004_003, "物料「{}」的中标份额合计必须为 100%");
    ErrorCode RFQ_AWARD_REQUIRED = new ErrorCode(1_007_004_004, "物料「{}」请选择中标供应商");
    ErrorCode RFQ_QUOTE_MISSING = new ErrorCode(1_007_004_005, "供应商「{}」没有对物料「{}」报价，不能中标");
    ErrorCode RFQ_DUPLICATE = new ErrorCode(1_007_004_006, "询价{}「{}」重复");

    // ========== 采购订单 1_007_005_xxx ==========
    ErrorCode ORDER_NOT_EXISTS = new ErrorCode(1_007_005_000, "采购订单不存在");
    ErrorCode ORDER_MATERIAL_NOT_APPROVED = new ErrorCode(1_007_005_001, "物料「{}」不是供应商「{}」的合格可供物料");
    ErrorCode ORDER_NO_PRICE = new ErrorCode(1_007_005_002, "物料「{}」没有有效的采购价格");
    ErrorCode ORDER_CHANGE_QTY_LT_RECEIVED = new ErrorCode(1_007_005_003, "第 {} 行变更后数量不能小于已到货数量 {}");
    ErrorCode ORDER_CHANGE_PRICE_LOCKED = new ErrorCode(1_007_005_004, "第 {} 行已到货完，不能修改单价");
    ErrorCode ORDER_CHANGE_STATEMENT_LOCKED = new ErrorCode(1_007_005_005, "第 {} 行已对账，不能修改");
    ErrorCode ORDER_HAS_PENDING_RECEIPT = new ErrorCode(1_007_005_006, "订单还有未处理的到货单「{}」");
    ErrorCode ORDER_HAS_RECEIPT = new ErrorCode(1_007_005_007, "订单已有到货，不能反审核，请使用变更");
    ErrorCode ORDER_CHANGE_NOT_EXISTS = new ErrorCode(1_007_005_008, "订单变更单不存在");
    ErrorCode ORDER_CHANGE_RUNNING = new ErrorCode(1_007_005_009, "订单有未完成的变更单「{}」");
    ErrorCode ORDER_CHANGE_NOTHING = new ErrorCode(1_007_005_010, "变更单没有任何变化");
    ErrorCode ORDER_SUPPLIER_REQUIRED = new ErrorCode(1_007_005_011, "申请「{}」第 {} 行没有建议供应商，请先选择供应商");
    ErrorCode ORDER_LINE_NOT_EXISTS = new ErrorCode(1_007_005_012, "采购订单行不存在");
    ErrorCode ORDER_LINE_RECEIVED = new ErrorCode(1_007_005_013, "第 {} 行已有到货，不能取消");

    // ========== 到货 1_007_006_xxx ==========
    ErrorCode RECEIPT_NOT_EXISTS = new ErrorCode(1_007_006_000, "到货单不存在");
    ErrorCode RECEIPT_ORDER_STATUS = new ErrorCode(1_007_006_001, "采购订单「{}」不是已审核状态");
    ErrorCode RECEIPT_OVER = new ErrorCode(1_007_006_002, "第 {} 行到货数量超过允许数量 {}");
    ErrorCode RECEIPT_LIFE = new ErrorCode(1_007_006_003, "第 {} 行剩余保质期 {}%，低于要求 {}%");
    ErrorCode RECEIPT_PRODUCTION_DATE = new ErrorCode(1_007_006_004, "第 {} 行请填写生产日期（不能晚于今天）");
    ErrorCode RECEIPT_SUPPLIER_ELIMINATED = new ErrorCode(1_007_006_005, "供应商已淘汰，不能收货");
    ErrorCode RECEIPT_STOCKED = new ErrorCode(1_007_006_006, "入库单「{}」已入库，不能反审核");
    ErrorCode RECEIPT_SAMPLE_ONLY = new ErrorCode(1_007_006_007, "样品到货只能选择样品订单");
    ErrorCode RECEIPT_ORDER_SUPPLIER = new ErrorCode(1_007_006_008, "第 {} 行不属于该供应商");
    ErrorCode RECEIPT_LINE_CLOSED = new ErrorCode(1_007_006_009, "采购订单「{}」第 {} 行已关闭");
    ErrorCode RECEIPT_REVERSE_BLOCKED = new ErrorCode(1_007_006_010, "到货单「{}」第 {} 行已有检验、退货或对账记录，不能反确认入库");
    ErrorCode RECEIPT_OUTSOURCE_OVER = new ErrorCode(1_007_006_011, "收货数量超过委外剩余数量");
    ErrorCode RECEIPT_TYPE_MISMATCH = new ErrorCode(1_007_006_012, "第 {} 行来源单据与到货类型不一致");
    ErrorCode RECEIPT_LINE_NOT_EXISTS = new ErrorCode(1_007_006_013, "到货行不存在");

    // ========== 委外 1_007_007_xxx ==========
    ErrorCode OS_NOT_EXISTS = new ErrorCode(1_007_007_000, "委外单不存在");
    ErrorCode OS_MATERIAL = new ErrorCode(1_007_007_001, "物料「{}」不是委外件或没有已审核的 BOM");
    ErrorCode OS_ISSUE_OVER = new ErrorCode(1_007_007_002, "物料「{}」发料超过应发数量");
    ErrorCode OS_LOSS_REASON = new ErrorCode(1_007_007_003, "物料「{}」超耗 {}，请填写原因");
    ErrorCode OS_NOT_RECEIVED = new ErrorCode(1_007_007_004, "委外单还没有收齐合格品，不能核销");
    ErrorCode OS_ADJUST_REASON = new ErrorCode(1_007_007_005, "物料「{}」调整了应发数量，请填写原因");
    ErrorCode OS_RETURN_OVER = new ErrorCode(1_007_007_006, "物料「{}」退回数量超过已发未退数量");
    ErrorCode OS_HAS_EXECUTION = new ErrorCode(1_007_007_007, "委外单已发料或收货，不能反审核");
    ErrorCode OS_NOTHING = new ErrorCode(1_007_007_008, "请填写本次数量");

    // ========== 采购退货 1_007_008_xxx ==========
    ErrorCode RETURN_NOT_EXISTS = new ErrorCode(1_007_008_000, "退货单不存在");
    ErrorCode RETURN_OVER = new ErrorCode(1_007_008_001, "第 {} 行退货数量超过可退数量 {}");
    ErrorCode RETURN_STATEMENT = new ErrorCode(1_007_008_002, "退货单已对账，不能作废");
    ErrorCode RETURN_OUT_CONFIRMED = new ErrorCode(1_007_008_003, "出库单「{}」已出库，不能作废");
    ErrorCode RETURN_SUPPLIER_MISMATCH = new ErrorCode(1_007_008_004, "第 {} 行不是该供应商的到货");
    ErrorCode RETURN_CURRENCY = new ErrorCode(1_007_008_005, "第 {} 行来源订单币别与其他行不一致");
    ErrorCode RETURN_REVERSE_BLOCKED = new ErrorCode(1_007_008_006, "退货单「{}」已对账，不能反确认出库");

    // ========== 对账 1_007_009_xxx ==========
    ErrorCode STATEMENT_NOT_EXISTS = new ErrorCode(1_007_009_000, "对账单不存在");
    ErrorCode STATEMENT_CURRENCY = new ErrorCode(1_007_009_001, "明细币别与对账单币别不一致");
    ErrorCode STATEMENT_ATTACHMENT = new ErrorCode(1_007_009_002, "请上传供应商确认的对账单");
    ErrorCode STATEMENT_LINE_TAKEN = new ErrorCode(1_007_009_003, "第 {} 行对账数量超过剩余可对账数量 {}，请重新加载");
    ErrorCode STATEMENT_PERIOD = new ErrorCode(1_007_009_004, "对账区间开始日期不能晚于结束日期");
    ErrorCode STATEMENT_ADJUST_REMARK = new ErrorCode(1_007_009_005, "调整行请填写说明");
    ErrorCode STATEMENT_CONFIRMER = new ErrorCode(1_007_009_006, "请填写供应商确认人");

    // ========== 评估 1_007_010_xxx ==========
    ErrorCode SCORE_NOT_EXISTS = new ErrorCode(1_007_010_000, "评估记录不存在");
    ErrorCode SCORE_WEIGHTS = new ErrorCode(1_007_010_001, "评估权重合计必须为 100");
    ErrorCode SCORE_NOT_SCORED = new ErrorCode(1_007_010_002, "请先完成价格和服务评分");
    ErrorCode SCORE_PUBLISHED = new ErrorCode(1_007_010_003, "已发布的评估不能修改");
    ErrorCode SCORE_PERIOD = new ErrorCode(1_007_010_004, "评估期格式应为 2026-09 或 2026-Q3");
    ErrorCode SCORE_RANGE = new ErrorCode(1_007_010_005, "评分必须在 0～100 之间");
}
