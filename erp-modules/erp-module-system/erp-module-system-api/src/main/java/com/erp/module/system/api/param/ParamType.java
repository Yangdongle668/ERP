package com.erp.module.system.api.param;

/** 参数值类型：字符串 / 整数 / 小数 / 布尔 / 枚举 / 用户列表（逗号分隔的用户 ID）/ 时间（HH:mm） */
public enum ParamType {
    STRING, INT, DECIMAL, BOOL, ENUM, USER_LIST, TIME
}
