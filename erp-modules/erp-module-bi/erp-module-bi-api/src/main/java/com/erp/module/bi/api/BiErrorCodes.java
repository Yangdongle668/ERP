package com.erp.module.bi.api;

import com.erp.common.exception.ErrorCode;

/** BI/AI模块错误码，号段 1_013_000_000 ~ 1_013_999_999。 */
public interface BiErrorCodes {

    ErrorCode NOT_EXISTS = new ErrorCode(1_013_000_000, "{}不存在");
    ErrorCode METRIC_NOT_EXISTS = new ErrorCode(1_013_001_000, "指标「{}」不存在");
    ErrorCode METRIC_FORBIDDEN = new ErrorCode(1_013_001_001, "没有指标「{}」的查看权限");
    ErrorCode DIMENSION_NOT_ALLOWED = new ErrorCode(1_013_001_002, "指标「{}」不支持维度「{}」");
    ErrorCode QUERY_INVALID = new ErrorCode(1_013_001_003, "查询参数不正确：{}");
    ErrorCode ETL_RUNNING = new ErrorCode(1_013_002_000, "数据任务「{}」正在运行");

    ErrorCode AI_DISABLED = new ErrorCode(1_013_003_000, "AI 分析未启用，请联系管理员配置");
    ErrorCode AI_QUOTA = new ErrorCode(1_013_003_001, "今日提问次数已达上限 {}");
    ErrorCode AI_UNAVAILABLE = new ErrorCode(1_013_003_002, "AI 服务暂时不可用，请稍后重试");
    ErrorCode AI_QUESTION_REQUIRED = new ErrorCode(1_013_003_003, "请输入问题");

    ErrorCode FORECAST_SCOPE = new ErrorCode(1_013_004_000, "销售预测建议基于全公司出货数据，需要数据范围为“全部”");
    ErrorCode FORECAST_NO_SELECTION = new ErrorCode(1_013_004_001, "请选择要生成预测的物料");
    ErrorCode FORECAST_NOT_AVAILABLE = new ErrorCode(1_013_004_002, "所选物料没有可用的预测建议（历史出货不足 12 个月）");
    ErrorCode FORECAST_NO_CREATE_PERMISSION = new ErrorCode(1_013_004_003, "没有新建销售预测的权限");
}
