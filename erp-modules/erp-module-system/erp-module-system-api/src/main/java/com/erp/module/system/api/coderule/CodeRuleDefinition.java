package com.erp.module.system.api.coderule;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编码规则默认值（01-05 编码规则）。各业务模块把自己需要的规则声明为 Spring Bean：
 * <pre>{@code
 * @Bean
 * public CodeRuleDefinition salesOrderCodeRule() {
 *     return CodeRuleDefinition.of("SAL_ORDER", "销售订单", "sales", "SO-", "yyyyMM", "-", 4, ResetCycle.MONTH).manual(true);
 * }
 * }</pre>
 * 启动时写入规则表（已存在的不覆盖管理员的修改），之后以界面上的配置为准。
 * 这样每个模块自带默认规则，不需要往系统管理模块的表里写初始化脚本。
 *
 * <p>编码 = 前缀 + 日期 + 分隔符 + 流水号。前缀可含变量 {@code {变量名}}，由调用方生成时传入，
 * 不同的前缀取值独立计数；变量必须在 {@code vars} 中声明。
 *
 * @param bizCode     业务编码，全局唯一，大写下划线
 * @param moduleCode  所属模块
 * @param prefix      前缀（可含分隔符和变量，如 {@code SO-}、{@code {categoryPrefix}}）
 * @param datePattern 日期格式：空 / yyyy / yyMM / yyyyMM / yyMMdd / yyyyMMdd
 * @param separator   日期与流水号之间的分隔符：空或 {@code -}；日期为空时忽略
 * @param seqLength   流水号位数 1~12，不足左补 0
 * @param resetCycle  流水号重置周期，必须与日期格式匹配
 * @param allowManual 是否允许手工输入编码
 * @param vars        前缀中允许使用的变量
 */
public record CodeRuleDefinition(String bizCode, String name, String moduleCode, String prefix, String datePattern,
                                 String separator, int seqLength, ResetCycle resetCycle, boolean allowManual, List<String> vars) {

    public enum ResetCycle {
        NEVER, YEAR, MONTH, DAY
    }

    public static final List<String> DATE_PATTERNS = List.of("", "yyyy", "yyMM", "yyyyMM", "yyMMdd", "yyyyMMdd");
    public static final Pattern VAR = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

    public CodeRuleDefinition {
        if (bizCode == null || !bizCode.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("bizCode 必须是大写字母、数字、下划线: " + bizCode);
        }
        if (seqLength < 1 || seqLength > 12) {
            throw new IllegalArgumentException("流水号位数必须在 1~12 之间");
        }
        prefix = prefix == null ? "" : prefix;
        datePattern = datePattern == null ? "" : datePattern;
        separator = separator == null ? "" : separator;
        resetCycle = resetCycle == null ? ResetCycle.NEVER : resetCycle;
        moduleCode = moduleCode == null ? "" : moduleCode;
        vars = vars == null ? List.of() : List.copyOf(vars);
        if (!DATE_PATTERNS.contains(datePattern)) {
            throw new IllegalArgumentException("不支持的日期格式: " + datePattern);
        }
        Matcher m = VAR.matcher(prefix);
        while (m.find()) {
            if (!vars.contains(m.group(1))) {
                throw new IllegalArgumentException("前缀中的变量未声明: " + m.group(1));
            }
        }
    }

    /** 兼容旧声明：前缀 + 日期 + 流水号 */
    public CodeRuleDefinition(String bizCode, String name, String prefix, String datePattern, int seqLength, ResetCycle resetCycle) {
        this(bizCode, name, "", prefix, datePattern, "", seqLength, resetCycle, false, List.of());
    }

    public static CodeRuleDefinition of(String bizCode, String name, String moduleCode, String prefix, String datePattern,
                                        String separator, int seqLength, ResetCycle resetCycle) {
        return new CodeRuleDefinition(bizCode, name, moduleCode, prefix, datePattern, separator, seqLength, resetCycle, false, List.of());
    }

    public CodeRuleDefinition manual(boolean allow) {
        return new CodeRuleDefinition(bizCode, name, moduleCode, prefix, datePattern, separator, seqLength, resetCycle, allow, vars);
    }

    /** 声明前缀中可用的变量（须在 of 之前的 prefix 中使用时先声明，可用 withVars 构造） */
    public static CodeRuleDefinition withVars(String bizCode, String name, String moduleCode, String prefix, String datePattern,
                                              String separator, int seqLength, ResetCycle resetCycle, String... vars) {
        return new CodeRuleDefinition(bizCode, name, moduleCode, prefix, datePattern, separator, seqLength, resetCycle, false, List.of(vars));
    }
}
