package com.erp.module.shipping.api;

import com.erp.common.exception.ErrorCode;

/** 出货模块错误码，号段 1_011_000_000 ~ 1_011_999_999。 */
public interface ShippingErrorCodes {

    // ========== 通用 000 ==========
    ErrorCode SHIPPING_ERROR = new ErrorCode(1_011_000_000, "出货模块错误");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_011_000_001, "请填写{}");
    ErrorCode STATUS_NOT_ALLOWED = new ErrorCode(1_011_000_002, "当前状态【{}】不能{}");
    ErrorCode DOC_PENDING = new ErrorCode(1_011_000_003, "单据审批中，请先撤回");
    ErrorCode NOT_EXISTS = new ErrorCode(1_011_000_004, "{}不存在");
    ErrorCode NO_LINES = new ErrorCode(1_011_000_005, "请至少添加一行明细");
    ErrorCode CODE_DUPLICATE = new ErrorCode(1_011_000_006, "编码「{}」已存在");

    // ========== 出货通知 001 ==========
    ErrorCode SN_BLACKLIST = new ErrorCode(1_011_001_000, "客户「{}」在黑名单中，不能出货");
    ErrorCode SN_CURRENCY_MIXED = new ErrorCode(1_011_001_001, "不同币别的订单请分开出货");
    ErrorCode SN_QTY_EXCEED = new ErrorCode(1_011_001_002, "第 {} 行通知数量超过可通知数量 {}");
    ErrorCode SN_CUSTOMER_MISMATCH = new ErrorCode(1_011_001_003, "第 {} 行订单不属于客户「{}」");
    ErrorCode SN_PICKING_STARTED = new ErrorCode(1_011_001_004, "拣货已开始，不能反审核");
    ErrorCode SN_ORDER_INVALID = new ErrorCode(1_011_001_005, "第 {} 行订单行不存在或已关闭");
    ErrorCode SN_CREDIT_BLOCK = new ErrorCode(1_011_001_006, "{}");
    ErrorCode SN_QTY_INVALID = new ErrorCode(1_011_001_007, "第 {} 行通知数量必须大于 0");
    ErrorCode SN_NO_PLAN_LINES = new ErrorCode(1_011_001_008, "所选出货计划没有可通知的行");
    ErrorCode SN_SHIPMENT_OPEN = new ErrorCode(1_011_001_009, "出货单「{}」尚未出库，请先撤回或作废");
    ErrorCode SN_OQC_PENDING = new ErrorCode(1_011_001_010, "OQC 检验中，不能修改装箱；如需修改请联系品质取消检验");

    // ========== 拣货与装箱 002 ==========
    ErrorCode PK_BATCH_SHORT = new ErrorCode(1_011_002_000, "批次「{}」可用数量不足（可用 {}）");
    ErrorCode PK_QTY_MISMATCH = new ErrorCode(1_011_002_001, "第 {} 行拣货数量 {} 与通知数量 {} 不一致");
    ErrorCode PK_PACK_MISMATCH = new ErrorCode(1_011_002_002, "装箱数量与拣货数量不一致：{}");
    ErrorCode PK_NOT_PICKED = new ErrorCode(1_011_002_003, "拣货尚未完成，不能装箱");
    ErrorCode PK_PACK_EXCEED = new ErrorCode(1_011_002_004, "{} 装箱数量超过可装数量 {}");
    ErrorCode PK_CARTON_SHIPPED = new ErrorCode(1_011_002_005, "箱号 {} 已出货，不能修改");
    ErrorCode PK_OQC_NOT_REQUIRED = new ErrorCode(1_011_002_006, "该出货通知不需要 OQC");
    ErrorCode PK_PICK_EXCEED = new ErrorCode(1_011_002_007, "第 {} 行拣货数量超过通知数量 {}");

    // ========== 出货单 003 ==========
    ErrorCode SH_NOTICE_NOT_READY = new ErrorCode(1_011_003_000, "出货通知尚未完成装箱/OQC");
    ErrorCode SH_ORDER_CLOSED = new ErrorCode(1_011_003_001, "订单「{}」行 {} 已关闭");
    ErrorCode SH_OQC_NOT_PASSED = new ErrorCode(1_011_003_002, "物料「{}」OQC 未合格，不能出货");
    ErrorCode SH_NOTHING_TO_SHIP = new ErrorCode(1_011_003_003, "没有可出货的数量");
    ErrorCode SH_BL_DATE = new ErrorCode(1_011_003_004, "提单日期不能早于出货日期");
    ErrorCode SH_PREPAYMENT_BLOCK = new ErrorCode(1_011_003_005, "订单「{}」出货前款项未收齐（{}），不能出货");
    ErrorCode SH_STOCK_OUT_DONE = new ErrorCode(1_011_003_006, "出库单已确认，不能撤回");
    ErrorCode SH_QTY_EXCEED = new ErrorCode(1_011_003_007, "订单「{}」行 {} 出货数量超过未出货数量 {}");

    // ========== 单证 004 ==========
    ErrorCode DOC_HS_REQUIRED = new ErrorCode(1_011_004_000, "第 {} 行请填写 HS 编码");
    ErrorCode DOC_NO_DUPLICATE = new ErrorCode(1_011_004_001, "单证号「{}」已存在");
    ErrorCode DOC_EXISTS = new ErrorCode(1_011_004_002, "出货单已生成{}：{}");
    ErrorCode DOC_SHIPMENT_DRAFT = new ErrorCode(1_011_004_003, "出货单提交后才能生成单证");
    ErrorCode DOC_MERGE_COUNT = new ErrorCode(1_011_004_004, "请选择 2 张以上的出货单合并");
    ErrorCode DOC_MERGE_CUSTOMER = new ErrorCode(1_011_004_005, "出货单 {} 与 {} 的客户不同，不能合并");
    ErrorCode DOC_MERGE_ADDRESS = new ErrorCode(1_011_004_006, "出货单 {} 与 {} 的收货地址不同，不能合并");

    // ========== 物流 005 ==========
    ErrorCode LOG_BACKWARD = new ErrorCode(1_011_005_000, "物流状态不能倒退，如需更正请填写说明");
    ErrorCode LOG_NOT_SHIPPED = new ErrorCode(1_011_005_001, "出货单尚未出货");
}
