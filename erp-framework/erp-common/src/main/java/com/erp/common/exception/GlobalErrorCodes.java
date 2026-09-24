package com.erp.common.exception;

/** 全局通用错误码。 */
public interface GlobalErrorCodes {

    ErrorCode SUCCESS = new ErrorCode(0, "成功");

    ErrorCode BAD_REQUEST = new ErrorCode(400, "请求参数不正确：{}");
    ErrorCode UNAUTHORIZED = new ErrorCode(401, "未登录或登录已过期");
    ErrorCode FORBIDDEN = new ErrorCode(403, "没有该操作权限");
    ErrorCode NOT_FOUND = new ErrorCode(404, "请求的资源不存在");
    ErrorCode METHOD_NOT_ALLOWED = new ErrorCode(405, "请求方法不正确");
    ErrorCode CONFLICT = new ErrorCode(409, "数据已存在或违反唯一约束");
    ErrorCode INTERNAL_ERROR = new ErrorCode(500, "系统异常，请联系管理员（traceId: {}）");
    ErrorCode NOT_IMPLEMENTED = new ErrorCode(501, "功能尚未实现：{}");

    ErrorCode CONCURRENT_MODIFICATION = new ErrorCode(1_000_000_001, "数据已被他人修改，请刷新后重试");
    ErrorCode ILLEGAL_STATE_TRANSITION = new ErrorCode(1_000_000_002, "当前状态【{}】不允许执行【{}】操作");
    ErrorCode DATA_NOT_EXISTS = new ErrorCode(1_000_000_003, "{}不存在");
    ErrorCode PASSWORD_CHANGE_REQUIRED = new ErrorCode(1_000_000_004, "请先修改密码");
    ErrorCode TOKEN_REVOKED = new ErrorCode(1_000_000_005, "登录已失效，请重新登录");
    ErrorCode IMPORT_FILE_INVALID = new ErrorCode(1_000_000_006, "导入文件格式不正确：{}");
    ErrorCode IMPORT_TOO_MANY_ROWS = new ErrorCode(1_000_000_007, "单次最多导入 {} 行，请拆分文件");
    ErrorCode IMPORT_HAS_ERRORS = new ErrorCode(1_000_000_008, "导入数据有 {} 行错误，请修正后重新导入");
    ErrorCode QUERY_RANGE_TOO_LARGE = new ErrorCode(1_000_000_009, "查询时间范围不能超过 {}");
}
