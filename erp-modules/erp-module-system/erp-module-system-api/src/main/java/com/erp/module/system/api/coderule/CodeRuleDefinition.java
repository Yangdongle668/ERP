package com.erp.module.system.api.coderule;

/**
 * 编码规则默认值。各业务模块把自己需要的规则声明为 Spring Bean：
 * <pre>{@code
 * @Bean
 * public CodeRuleDefinition materialCodeRule() {
 *     return new CodeRuleDefinition("MATERIAL", "物料编码", "M", "", 6, CodeRuleDefinition.ResetCycle.NEVER);
 * }
 * }</pre>
 * 首次使用时系统管理模块会把它写入规则表，之后以管理员在界面上的配置为准。
 * 这样每个模块自带默认规则，不需要往系统管理模块的表里写初始化脚本。
 *
 * @param bizCode     业务编码，全局唯一，大写下划线
 * @param name        规则名称
 * @param prefix      固定前缀（可含分隔符，如 {@code SO-}）
 * @param datePattern 日期片段格式，如 {@code yyyyMM}；空字符串表示不含日期
 * @param seqLength   流水号位数，不足左补 0
 * @param resetCycle  流水号重置周期
 */
public record CodeRuleDefinition(String bizCode, String name, String prefix, String datePattern,
                                 int seqLength, ResetCycle resetCycle) {

    public enum ResetCycle {
        NEVER, YEAR, MONTH, DAY
    }

    public CodeRuleDefinition {
        if (bizCode == null || !bizCode.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("bizCode 必须是大写字母、数字、下划线: " + bizCode);
        }
        if (seqLength < 1 || seqLength > 12) {
            throw new IllegalArgumentException("流水号位数必须在 1~12 之间");
        }
        prefix = prefix == null ? "" : prefix;
        datePattern = datePattern == null ? "" : datePattern;
        resetCycle = resetCycle == null ? ResetCycle.NEVER : resetCycle;
    }
}
