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
}
