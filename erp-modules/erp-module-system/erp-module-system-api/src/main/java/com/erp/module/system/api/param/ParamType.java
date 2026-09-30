package com.erp.module.system.api.param;

/** 参数值类型：字符串 / 整数 / 小数 / 布尔 / 枚举 / 用户列表（逗号分隔的用户 ID）/ 时间（HH:mm） */
public enum ParamType {
    STRING, INT, DECIMAL, BOOL, ENUM, USER_LIST, TIME,
    /** 敏感字符串（如 API Key）：加密存储，页面只显示后 4 位，ParamApi.getString 返回明文 */
    SECRET
}
