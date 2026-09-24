package com.erp.common.exception;

/**
 * 业务异常：可预期的、需要提示给用户的错误。
 *
 * <p>业务代码只抛本异常；由框架的全局异常处理器转换为统一响应。
 * 抛出后当前事务回滚（RuntimeException）。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        this(errorCode.code(), errorCode.message());
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(ErrorCode errorCode, Object... args) {
        return new BizException(errorCode.code(), format(errorCode.message(), args));
    }

    public int getCode() {
        return code;
    }

    /** 依次用参数替换消息中的 {} 占位符。 */
    static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + 32);
        int argIndex = 0;
        int cursor = 0;
        while (cursor < template.length()) {
            int idx = template.indexOf("{}", cursor);
            if (idx < 0 || argIndex >= args.length) {
                sb.append(template, cursor, template.length());
                break;
            }
            sb.append(template, cursor, idx).append(args[argIndex++]);
            cursor = idx + 2;
        }
        return sb.toString();
    }
}
