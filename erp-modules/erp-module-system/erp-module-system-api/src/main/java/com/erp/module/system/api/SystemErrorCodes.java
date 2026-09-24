package com.erp.module.system.api;

import com.erp.common.exception.ErrorCode;

/** 系统管理模块错误码，号段 1_001_000_000 ~ 1_001_999_999。 */
public interface SystemErrorCodes {

    // ========== 认证 1_001_001_xxx ==========
    ErrorCode AUTH_BAD_CREDENTIALS = new ErrorCode(1_001_001_000, "用户名或密码错误");
    ErrorCode AUTH_USER_DISABLED = new ErrorCode(1_001_001_001, "账号已停用");
    ErrorCode AUTH_USER_LOCKED = new ErrorCode(1_001_001_002, "密码错误次数过多，账号已锁定，请 {} 分钟后再试");
    ErrorCode AUTH_REFRESH_TOKEN_INVALID = new ErrorCode(1_001_001_003, "登录已过期，请重新登录");

    // ========== 编码规则 1_001_002_xxx ==========
    ErrorCode CODE_RULE_NOT_FOUND = new ErrorCode(1_001_002_000, "未配置编码规则【{}】");
}
