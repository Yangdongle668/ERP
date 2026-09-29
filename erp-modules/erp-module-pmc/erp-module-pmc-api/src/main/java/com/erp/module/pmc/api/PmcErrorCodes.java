package com.erp.module.pmc.api;

import com.erp.common.exception.ErrorCode;

/** PMC 模块错误码，号段 1_006_000_000 ~ 1_006_999_999。 */
public interface PmcErrorCodes {

    // ========== 通用 000 ==========
    ErrorCode PMC_ERROR = new ErrorCode(1_006_000_000, "PMC模块错误");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_006_000_001, "请填写{}原因");
    ErrorCode STATUS_NOT_ALLOWED = new ErrorCode(1_006_000_002, "当前状态【{}】不能{}");
    ErrorCode WEEK_INVALID = new ErrorCode(1_006_000_003, "周格式不正确：{}（应为 2026-W40）");

    // ========== 需求池与交期回复 001 ==========
    ErrorCode DEMAND_NOT_EXISTS = new ErrorCode(1_006_001_000, "需求不存在");
    ErrorCode DEMAND_REMARK_REQUIRED = new ErrorCode(1_006_001_001, "请填写需求说明");
    ErrorCode DEMAND_NOT_MANUAL = new ErrorCode(1_006_001_002, "只能修改、关闭手工需求");
    ErrorCode PROMISED_DATE_PAST = new ErrorCode(1_006_001_003, "承诺交期不能早于今天");
    ErrorCode DEMAND_QTY_INVALID = new ErrorCode(1_006_001_004, "需求数量必须大于 0");

    // ========== MPS 002 ==========
    ErrorCode MPS_NOT_EXISTS = new ErrorCode(1_006_002_000, "MPS 不存在");
    ErrorCode MPS_NOT_MAKE = new ErrorCode(1_006_002_001, "物料「{}」不是自制件或没有 BOM");
    ErrorCode MPS_WEEK_RANGE = new ErrorCode(1_006_002_002, "MPS 周期最多 26 周，且结束周不能早于开始周");
    ErrorCode MPS_NOT_DRAFT = new ErrorCode(1_006_002_003, "只有草稿状态的 MPS 可以修改");
    ErrorCode MPS_PAST_WEEK = new ErrorCode(1_006_002_004, "{} 已过去，不能修改");

    // ========== MRP 运算 003 ==========
    ErrorCode MRP_RUNNING = new ErrorCode(1_006_003_000, "MRP 正在运算中（{}，{} 发起于 {}）");
    ErrorCode MRP_BOM_LOOP = new ErrorCode(1_006_003_001, "MRP 运算失败：物料「{}」的 BOM 存在循环引用");
    ErrorCode MRP_RUN_NOT_EXISTS = new ErrorCode(1_006_003_002, "MRP 运算记录不存在");
    ErrorCode MRP_ORDER_SCOPE_REQUIRED = new ErrorCode(1_006_003_003, "请选择要运算的销售订单行");

    // ========== MRP 建议 004 ==========
    ErrorCode SUGGESTION_NOT_EXISTS = new ErrorCode(1_006_004_000, "建议不存在");
    ErrorCode SUGGESTION_SUPERSEDED = new ErrorCode(1_006_004_001, "该建议已过期，请使用最新的运算结果");
    ErrorCode SUGGESTION_NOT_PENDING = new ErrorCode(1_006_004_002, "建议已处理（{}），不能修改");
    ErrorCode SUGGESTION_MATERIAL_DISABLED = new ErrorCode(1_006_004_003, "物料「{}」已停用");
    ErrorCode SUGGESTION_QTY_INVALID = new ErrorCode(1_006_004_004, "建议数量必须大于 0");
    ErrorCode SUGGESTION_TYPE_MISMATCH = new ErrorCode(1_006_004_005, "所选建议的类型不一致");
    ErrorCode EXCEPTION_NOT_EXISTS = new ErrorCode(1_006_004_006, "例外信息不存在");

    // ========== 排产与产能 005 ==========
    ErrorCode SCHEDULE_RUNNING = new ErrorCode(1_006_005_000, "排产正在运行中");
    ErrorCode SCHEDULE_BEFORE_PREV = new ErrorCode(1_006_005_001, "不能早于上道工序结束时间");
    ErrorCode SCHEDULE_NOT_EXISTS = new ErrorCode(1_006_005_002, "排产记录不存在");
    ErrorCode SCHEDULE_WC_INVALID = new ErrorCode(1_006_005_003, "不能调整到其他车间的工作中心");
    ErrorCode CALENDAR_HOURS_INVALID = new ErrorCode(1_006_005_004, "可用工时必须在 0～24 之间");
    ErrorCode CALENDAR_RANGE_INVALID = new ErrorCode(1_006_005_005, "日期范围不正确（最多 366 天）");
    ErrorCode SCHEDULE_LOCKED = new ErrorCode(1_006_005_006, "该工序已锁定，请先解锁");

    // ========== 缺料 006 ==========
    ErrorCode SHORTAGE_SNAPSHOT_NOT_EXISTS = new ErrorCode(1_006_006_000, "缺料分析快照不存在");

    // ========== 交期预警 007 ==========
    ErrorCode ALERT_NOT_EXISTS = new ErrorCode(1_006_007_000, "交期预警不存在");

    // ========== 出货计划 008 ==========
    ErrorCode SHIP_PLAN_NOT_EXISTS = new ErrorCode(1_006_008_000, "出货计划不存在");
    ErrorCode SHIP_PLAN_QTY_EXCEEDED = new ErrorCode(1_006_008_001, "计划数量不能超过未出货数量 {}");
    ErrorCode SHIP_PLAN_LINE_DUPLICATED = new ErrorCode(1_006_008_002, "订单 {} 行 {} 在本周已有出货计划");
    ErrorCode SHIP_PLAN_LINE_NOTICED = new ErrorCode(1_006_008_003, "订单 {} 行 {} 已生成出货通知，不能删除或减少到已通知数量以下");
    ErrorCode SHIP_PLAN_NOT_EDITABLE = new ErrorCode(1_006_008_004, "已关闭的出货计划不能修改");
    ErrorCode SHIP_PLAN_ORDER_LINE_INVALID = new ErrorCode(1_006_008_005, "销售订单行不存在或已关闭");
}
