package com.erp.common.exception;

/**
 * 错误码。
 *
 * <p>编码分段约定（每个模块在自己的 api 模块中定义 {@code XxxErrorCodes}，号段互不重叠，
 * 并行开发时不会冲突）：
 * <pre>
 *  0              成功
 *  400~599        HTTP 语义通用错误（见 {@link GlobalErrorCodes}）
 *  1_000_000_000  框架
 *  1_001_xxx_xxx  系统管理     1_002_xxx_xxx  工作台
 *  1_003_xxx_xxx  CRM          1_004_xxx_xxx  销售
 *  1_005_xxx_xxx  研发工程     1_006_xxx_xxx  PMC
 *  1_007_xxx_xxx  资材         1_008_xxx_xxx  仓库
 *  1_009_xxx_xxx  生产         1_010_xxx_xxx  品质
 *  1_011_xxx_xxx  出货         1_012_xxx_xxx  财务
 *  1_013_xxx_xxx  BI/AI
 * </pre>
 * 消息支持 {@code {}} 占位符，由 {@link BizException#of(ErrorCode, Object...)} 填充。
 */
public record ErrorCode(int code, String message) {
}
