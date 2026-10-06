package com.erp.module.backup.api;

import com.erp.common.exception.ErrorCode;

/** 系统备份模块错误码，号段 1_014_000_000 ~ 1_014_999_999。 */
public interface BackupErrorCodes {

    ErrorCode NOT_EXISTS = new ErrorCode(1_014_000_000, "备份记录不存在");
    ErrorCode BUSY = new ErrorCode(1_014_000_001, "正在执行{}，请稍后再试");
    ErrorCode FILE_MISSING = new ErrorCode(1_014_000_002, "备份文件不存在：{}");
    ErrorCode NOT_SUCCESS = new ErrorCode(1_014_000_003, "只能使用成功的备份");

    ErrorCode INVALID_FILE = new ErrorCode(1_014_001_000, "不是有效的系统备份文件：{}");
    ErrorCode SCHEMA_MISMATCH = new ErrorCode(1_014_001_001, "备份的数据库版本与当前系统不一致（{}），不能恢复");
    ErrorCode TABLE_MISMATCH = new ErrorCode(1_014_001_002, "备份的数据表与当前系统不一致（{}），不能恢复");
    ErrorCode CONFIRM_REQUIRED = new ErrorCode(1_014_001_003, "请输入“确认恢复”");
    ErrorCode CHECKSUM_MISMATCH = new ErrorCode(1_014_001_004, "备份文件校验失败（文件已损坏或被修改）");

    ErrorCode BACKUP_FAILED = new ErrorCode(1_014_002_000, "备份失败：{}");
    ErrorCode RESTORE_FAILED = new ErrorCode(1_014_002_001, "恢复失败：{}");
}
