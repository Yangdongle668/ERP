package com.erp.module.inventory.api;

import com.erp.common.exception.ErrorCode;

/** 仓库模块错误码，号段 1_008_000_000 ~ 1_008_999_999。提示原文与需求文档一致。 */
public interface InventoryErrorCodes {

    // ========== 过账 1_008_001_xxx ==========
    ErrorCode STOCK_NOT_ENOUGH = new ErrorCode(1_008_001_000, "物料「{}」在仓库「{}」{}库存不足，需要 {}，现存 {}");
    ErrorCode WAREHOUSE_NOT_AVAILABLE = new ErrorCode(1_008_001_001, "「{}」是不可用仓，不能执行该出库操作");
    ErrorCode DUPLICATE_POSTING = new ErrorCode(1_008_001_002, "单据「{}」已过账，不能重复过账");
    ErrorCode PERIOD_CLOSED = new ErrorCode(1_008_001_003, "库存期间 {} 已结账，不能过账");
    ErrorCode BATCH_REQUIRED = new ErrorCode(1_008_001_004, "物料「{}」需要填写批次号");
    ErrorCode SERIAL_COUNT_MISMATCH = new ErrorCode(1_008_001_005, "物料「{}」序列号数量 {} 与数量 {} 不一致");
    ErrorCode BATCH_FROZEN = new ErrorCode(1_008_001_006, "批次「{}」已冻结，不能出库");
    ErrorCode BATCH_EXPIRED = new ErrorCode(1_008_001_007, "批次「{}」已过期（{}），不能出库");
    ErrorCode REVERSE_STOCK_USED = new ErrorCode(1_008_001_008, "入库的物料已被使用，不能反确认：物料「{}」现存 {}，需要冲回 {}");
    ErrorCode STOCK_BUSY = new ErrorCode(1_008_001_009, "库存数据繁忙，请稍后重试");
    ErrorCode SERIAL_IN_STOCK = new ErrorCode(1_008_001_010, "序列号「{}」已在库");
    ErrorCode SERIAL_NOT_IN_WAREHOUSE = new ErrorCode(1_008_001_011, "序列号「{}」不在仓库「{}」");
    ErrorCode QTY_PRECISION = new ErrorCode(1_008_001_012, "物料「{}」数量精度不能超过 {} 位小数");
    ErrorCode LOCATION_REQUIRED = new ErrorCode(1_008_001_013, "仓库「{}」启用了库位管理，请选择库位");
    ErrorCode COUNT_FROZEN = new ErrorCode(1_008_001_014, "该物料正在盘点中（盘点单 {}），不能出入库");
    ErrorCode BEFORE_OPENING_PERIOD = new ErrorCode(1_008_001_015, "单据日期早于系统启用期间 {}");
    ErrorCode OPENING_NOT_COMPLETED = new ErrorCode(1_008_001_016, "请先完成期初库存导入");
    ErrorCode MATERIAL_NOT_USABLE = new ErrorCode(1_008_001_017, "物料「{}」未启用");
    ErrorCode WAREHOUSE_DISABLED = new ErrorCode(1_008_001_018, "仓库「{}」已停用");
    ErrorCode QTY_NOT_POSITIVE = new ErrorCode(1_008_001_019, "数量必须大于 0");
    ErrorCode BATCH_MATERIAL_MISMATCH = new ErrorCode(1_008_001_020, "批次「{}」不属于物料「{}」");

    // ========== 仓库与库位 1_008_002_xxx ==========
    ErrorCode WAREHOUSE_NOT_EXISTS = new ErrorCode(1_008_002_000, "仓库不存在");
    ErrorCode WAREHOUSE_CODE_DUPLICATE = new ErrorCode(1_008_002_001, "仓库编码「{}」已存在");
    ErrorCode WAREHOUSE_IN_USE = new ErrorCode(1_008_002_002, "仓库还有库存或未完成单据，不能停用");
    ErrorCode WAREHOUSE_HAS_TXN = new ErrorCode(1_008_002_003, "仓库已有出入库记录，不能删除");
    ErrorCode WAREHOUSE_LOCATION_STOCK = new ErrorCode(1_008_002_004, "仓库有库存，不能关闭库位管理");
    ErrorCode LOCATION_CODE_DUPLICATE = new ErrorCode(1_008_002_005, "库位「{}」已存在");
    ErrorCode LOCATION_HAS_STOCK = new ErrorCode(1_008_002_006, "库位「{}」有库存，不能停用");
    ErrorCode CATEGORY_WAREHOUSE_UNAVAILABLE = new ErrorCode(1_008_002_007, "默认仓必须是可用仓（不能是待检仓、不良品仓、退货仓）");
    ErrorCode NO_DEFAULT_WAREHOUSE = new ErrorCode(1_008_002_008, "物料「{}」没有默认仓库，请在仓库管理中配置类别默认仓");
    ErrorCode LOCATION_NOT_EXISTS = new ErrorCode(1_008_002_009, "库位不存在");
    ErrorCode CATEGORY_WAREHOUSE_DUPLICATE = new ErrorCode(1_008_002_010, "该类别已配置默认仓");
    ErrorCode WAREHOUSE_NO_PERMISSION = new ErrorCode(1_008_002_011, "没有仓库「{}」的操作权限");
    ErrorCode LOCATION_GENERATE_TOO_MANY = new ErrorCode(1_008_002_012, "一次最多生成 {} 个库位");

    // ========== 出入库单 1_008_003_xxx ==========
    ErrorCode DOC_NOT_EXISTS = new ErrorCode(1_008_003_000, "单据不存在");
    ErrorCode SOURCE_LINE_DUPLICATE = new ErrorCode(1_008_003_001, "来源单据行已生成入库单「{}」");
    ErrorCode IN_WAREHOUSE_TYPE = new ErrorCode(1_008_003_002, "{}只能入【{}】");
    ErrorCode LINE_FIELD_REQUIRED = new ErrorCode(1_008_003_003, "第 {} 行：{}不能为空");
    ErrorCode REJECT_REASON_REQUIRED = new ErrorCode(1_008_003_004, "请填写退回原因");
    ErrorCode SOURCE_DOC_CONFIRMED = new ErrorCode(1_008_003_005, "已生成的{}「{}」已{}，请先反确认");
    ErrorCode DOC_DATE_BEFORE_SOURCE = new ErrorCode(1_008_003_006, "{}日期不能早于来源单据日期 {}");
    ErrorCode DOC_DATE_FUTURE = new ErrorCode(1_008_003_007, "单据日期不能晚于今天");
    ErrorCode OUT_WAREHOUSE_TYPE = new ErrorCode(1_008_003_008, "{}只能从【{}】出库");
    ErrorCode OUT_QTY_EXCEED = new ErrorCode(1_008_003_009, "第 {} 行实发数量不能大于申请数量");
    ErrorCode OUT_QTY_MUST_EQUAL = new ErrorCode(1_008_003_010, "{}实发数量必须等于申请数量 {}");
    ErrorCode SCRAP_NEEDS_EXPLAIN = new ErrorCode(1_008_003_011, "报废出库请上传附件或填写说明");
    ErrorCode DOC_NOT_EDITABLE = new ErrorCode(1_008_003_012, "只有草稿状态的单据可以修改");
    ErrorCode DOC_QTY_LOCKED = new ErrorCode(1_008_003_013, "业务生成的单据不能修改物料和数量");
    ErrorCode OTHER_REASON_REQUIRED = new ErrorCode(1_008_003_014, "请选择{}原因");
    ErrorCode NO_LINES = new ErrorCode(1_008_003_015, "请至少添加一行明细");
    ErrorCode OUT_REQUEST_EXCEED = new ErrorCode(1_008_003_016, "申请出库数量超过来源单据未出数量");
    ErrorCode UNCONFIRM_REASON_REQUIRED = new ErrorCode(1_008_003_017, "请填写反确认原因");
    ErrorCode UOM_NO_CONVERSION = new ErrorCode(1_008_003_018, "物料「{}」的单位 {} 没有与基本单位的换算关系");
    ErrorCode ONLY_OTHER_MANUAL = new ErrorCode(1_008_003_019, "只有其他入库、其他出库可以手工新建");

    // ========== 调拨 1_008_004_xxx ==========
    ErrorCode TRANSFER_NOT_ALLOWED = new ErrorCode(1_008_004_000, "不允许从【{}】调拨到【{}】");
    ErrorCode TRANSFER_SAME_LOCATION = new ErrorCode(1_008_004_001, "同一仓库调拨请选择不同的库位");
    ErrorCode TRANSFER_INSPECTION_LOCKED = new ErrorCode(1_008_004_002, "检验调拨单不能修改数量");
    ErrorCode TRANSFER_INSPECTION_VOID = new ErrorCode(1_008_004_003, "检验调拨单不能作废，请由品质重新判定");
    ErrorCode TRANSFER_REASON_REQUIRED = new ErrorCode(1_008_004_004, "请填写调拨原因");

    // ========== 盘点 1_008_005_xxx ==========
    ErrorCode COUNT_ALREADY_IN = new ErrorCode(1_008_005_000, "物料「{}」已在盘点单「{}」中");
    ErrorCode COUNT_LINES_UNINPUT = new ErrorCode(1_008_005_001, "还有 {} 行未录入实盘数量");
    ErrorCode COUNT_RECOUNT_UNINPUT = new ErrorCode(1_008_005_002, "还有 {} 行需要复盘，请录入复盘数量");
    ErrorCode COUNT_REASON_REQUIRED = new ErrorCode(1_008_005_003, "第 {} 行有差异，请选择差异原因");
    ErrorCode COUNT_BATCH_REQUIRED = new ErrorCode(1_008_005_004, "请填写批次号");
    ErrorCode COUNT_STATUS = new ErrorCode(1_008_005_005, "盘点单当前状态【{}】不允许执行该操作");
    ErrorCode COUNT_EMPTY = new ErrorCode(1_008_005_006, "盘点范围内没有库存");

    // ========== 批次 1_008_006_xxx ==========
    ErrorCode BATCH_NOT_EXISTS = new ErrorCode(1_008_006_000, "批次不存在");
    ErrorCode BATCH_FROZEN_BY_QUALITY = new ErrorCode(1_008_006_001, "该批次由品质冻结（{}），请联系品质部门解冻");
    ErrorCode BATCH_REASON_REQUIRED = new ErrorCode(1_008_006_002, "请填写原因");

    // ========== 期间 1_008_007_xxx ==========
    ErrorCode PERIOD_ALREADY_INIT = new ErrorCode(1_008_007_000, "已设置启用期间 {}");
    ErrorCode PERIOD_NOT_INIT = new ErrorCode(1_008_007_001, "尚未设置启用期间");
    ErrorCode OPENING_COMPLETED = new ErrorCode(1_008_007_002, "期初已完成，不能再导入或清空");
    ErrorCode OPENING_CLEAR_BLOCKED = new ErrorCode(1_008_007_003, "启用期间已有其他单据过账，不能清空期初");
    ErrorCode PERIOD_CLOSE_BLOCKED = new ErrorCode(1_008_007_004, "月结检查未通过：{}");
    ErrorCode PERIOD_REOPEN_LATEST = new ErrorCode(1_008_007_005, "只能反结账最近一个已结账期间 {}");
    ErrorCode PERIOD_FINANCE_CLOSED = new ErrorCode(1_008_007_006, "财务期间已结账，请先由财务反结账");
    ErrorCode PERIOD_NOT_EXISTS = new ErrorCode(1_008_007_007, "库存期间 {} 不存在");
    ErrorCode PERIOD_STATUS = new ErrorCode(1_008_007_008, "库存期间 {} 当前状态不允许该操作");
    ErrorCode QUERY_RANGE_TOO_LARGE = new ErrorCode(1_008_007_009, "查询时间范围不能超过 1 年");
}
