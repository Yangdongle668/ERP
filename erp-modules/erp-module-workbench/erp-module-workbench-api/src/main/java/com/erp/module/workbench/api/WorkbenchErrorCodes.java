package com.erp.module.workbench.api;

import com.erp.common.exception.ErrorCode;

/** 工作台模块错误码，号段 1_002_000_000 ~ 1_002_999_999。 */
public interface WorkbenchErrorCodes {

    ErrorCode NOT_EXISTS = new ErrorCode(1_002_000_000, "{}不存在");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_002_000_001, "请填写{}");
    ErrorCode STATUS_NOT_ALLOWED = new ErrorCode(1_002_000_002, "当前状态【{}】不能{}");

    ErrorCode TODO_NOT_MANUAL = new ErrorCode(1_002_001_000, "该待办由业务单据自动完成，请到单据中处理");

    ErrorCode NOTICE_DEPT_REQUIRED = new ErrorCode(1_002_002_000, "按部门发布时请选择部门");
    ErrorCode NOTICE_EXPIRE_BEFORE_PUBLISH = new ErrorCode(1_002_002_001, "过期时间必须晚于发布时间");

    ErrorCode SHORTCUT_TOO_MANY = new ErrorCode(1_002_003_000, "快捷入口最多 {} 个");
    ErrorCode CARD_NOT_EXISTS = new ErrorCode(1_002_003_001, "卡片「{}」不存在或无权限");
    ErrorCode CARD_LOAD_FAILED = new ErrorCode(1_002_003_002, "卡片数据加载失败：{}");
    ErrorCode WEATHER_CITY_INVALID = new ErrorCode(1_002_003_003, "{}");
}
