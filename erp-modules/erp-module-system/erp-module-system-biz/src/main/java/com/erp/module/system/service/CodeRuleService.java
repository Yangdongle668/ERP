package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import com.erp.module.system.controller.vo.CodeRuleVOs.PreviewReq;
import com.erp.module.system.controller.vo.CodeRuleVOs.RuleResp;
import com.erp.module.system.controller.vo.CodeRuleVOs.RuleSave;
import com.erp.module.system.controller.vo.CodeRuleVOs.SeqAdjust;
import com.erp.module.system.controller.vo.CodeRuleVOs.SeqResp;
import com.erp.module.system.dal.dataobject.CodeRuleDO;
import com.erp.module.system.dal.mapper.CodeRuleMapper;
import com.erp.module.system.dal.mapper.CodeSeqMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * 编码规则（01-05），同时是 {@link CodeRuleApi} 的实现。
 *
 * <p>编码 = 前缀 + 日期 + 分隔符 + 流水号。前缀含变量时，重置键 = 周期键 + "|" + 解析后的前缀，
 * 即每个不同的前缀取值独立计数。
 */
@Slf4j
@Service
public class CodeRuleService implements CodeRuleApi {

    private static final int MAX_CODE_LENGTH = 64;

    private final CodeRuleMapper codeRuleMapper;
    private final CodeSeqMapper codeSeqMapper;
    private final CodeSeqAllocator seqAllocator;
    private final Map<String, CodeRuleDefinition> definitions;
    private final Map<String, String> moduleNames;
    private final Clock clock;

    @Autowired
    public CodeRuleService(CodeRuleMapper codeRuleMapper, CodeSeqMapper codeSeqMapper, CodeSeqAllocator seqAllocator,
                           List<CodeRuleDefinition> definitions, List<ErpModule> modules) {
        this(codeRuleMapper, codeSeqMapper, seqAllocator, definitions, modules, Clock.systemDefaultZone());
    }

    CodeRuleService(CodeRuleMapper codeRuleMapper, CodeSeqMapper codeSeqMapper, CodeSeqAllocator seqAllocator,
                    List<CodeRuleDefinition> definitions, List<ErpModule> modules, Clock clock) {
        this.codeRuleMapper = codeRuleMapper;
        this.codeSeqMapper = codeSeqMapper;
        this.seqAllocator = seqAllocator;
        this.definitions = definitions.stream()
                .collect(Collectors.toMap(CodeRuleDefinition::bizCode, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("编码规则重复声明: " + a.bizCode());
                }));
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
        this.clock = clock;
    }

    // ==================== 生成 ====================

    @Override
    public String nextCode(String bizCode) {
        return nextCode(bizCode, Map.of());
    }

    @Override
    public String nextCode(String bizCode, Map<String, String> vars) {
        CodeRuleDO rule = loadOrInitRule(bizCode);
        LocalDate today = LocalDate.now(clock);
        String prefix = resolvePrefix(rule, vars);
        String resetKey = resetKey(rule.getResetCycle(), today) + (hasVars(rule.getPrefix()) ? "|" + prefix : "");
        long seq = seqAllocator.next(bizCode, resetKey);
        if (String.valueOf(seq).length() > rule.getSeqLength()) {
            log.warn("[编码规则] {} 流水号 {} 已超过 {} 位，请调整编码规则", bizCode, seq, rule.getSeqLength());
        }
        String code = format(prefix, rule.getDatePattern(), rule.getSeparator(), rule.getSeqLength(), today, seq);
        if (code.length() > MAX_CODE_LENGTH) throw BizException.of(SystemErrorCodes.CODE_RULE_TOO_LONG, rule.getName());
        return code;
    }

    @Override
    public boolean isManualAllowed(String bizCode) {
        return Boolean.TRUE.equals(loadOrInitRule(bizCode).getAllowManual());
    }

    static String resetKey(ResetCycle cycle, LocalDate date) {
        return switch (cycle) {
            case NEVER -> "ALL";
            case YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy"));
            case MONTH -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case DAY -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        };
    }

    static String format(String prefix, String datePattern, String separator, int seqLength, LocalDate date, long seq) {
        boolean hasDate = datePattern != null && !datePattern.isEmpty();
        String datePart = hasDate ? date.format(DateTimeFormatter.ofPattern(datePattern)) : "";
        String sep = hasDate && separator != null ? separator : "";
        String seqPart = String.valueOf(seq);
        if (seqPart.length() < seqLength) seqPart = "0".repeat(seqLength - seqPart.length()) + seqPart;
        return prefix + datePart + sep + seqPart;
    }

    private static boolean hasVars(String prefix) {
        return prefix != null && CodeRuleDefinition.VAR.matcher(prefix).find();
    }

    private static String resolvePrefix(CodeRuleDO rule, Map<String, String> vars) {
        String prefix = rule.getPrefix() == null ? "" : rule.getPrefix();
        Matcher m = CodeRuleDefinition.VAR.matcher(prefix);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars == null ? null : vars.get(m.group(1));
            if (!StringUtils.hasText(v)) throw BizException.of(SystemErrorCodes.CODE_RULE_VAR_MISSING, rule.getName(), m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v.trim()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 优先使用数据库中的规则；没有时用模块声明的默认规则初始化 */
    private CodeRuleDO loadOrInitRule(String bizCode) {
        CodeRuleDO rule = codeRuleMapper.selectByBizCode(bizCode);
        if (rule != null) return rule;
        CodeRuleDefinition def = definitions.get(bizCode);
        if (def == null) throw BizException.of(SystemErrorCodes.CODE_RULE_NOT_FOUND, bizCode);
        try {
            CodeRuleDO created = fromDefinition(def);
            codeRuleMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return codeRuleMapper.selectByBizCode(bizCode);
        }
    }

    private static CodeRuleDO fromDefinition(CodeRuleDefinition def) {
        CodeRuleDO r = new CodeRuleDO();
        r.setBizCode(def.bizCode());
        r.setName(def.name());
        r.setModuleCode(def.moduleCode());
        r.setPrefix(def.prefix());
        r.setDatePattern(def.datePattern());
        r.setSeparator(def.separator());
        r.setSeqLength(def.seqLength());
        r.setResetCycle(def.resetCycle());
        r.setAllowManual(def.allowManual());
        r.setAllowedVars(String.join(",", def.vars()));
        return r;
    }

    // ==================== 声明同步 ====================

    /** 启动时：不存在的规则按声明创建；已存在的只同步所属模块和可用变量，不覆盖管理员的修改 */
    @Transactional(rollbackFor = Exception.class)
    public void sync() {
        for (CodeRuleDefinition def : definitions.values()) {
            CodeRuleDO r = codeRuleMapper.selectByBizCode(def.bizCode());
            if (r == null) {
                codeRuleMapper.insert(fromDefinition(def));
            } else {
                String vars = String.join(",", def.vars());
                String module = StringUtils.hasText(def.moduleCode()) ? def.moduleCode() : r.getModuleCode();
                if (!vars.equals(r.getAllowedVars()) || !module.equals(r.getModuleCode())) {
                    r.setAllowedVars(vars);
                    r.setModuleCode(module);
                    codeRuleMapper.updateByIdOrFail(r);
                }
            }
        }
        log.info("[编码规则] 同步 {} 条规则声明", definitions.size());
    }

    // ==================== 页面 ====================

    public List<RuleResp> list(String moduleCode, String keyword) {
        LambdaQueryWrapper<CodeRuleDO> w = new LambdaQueryWrapper<CodeRuleDO>()
                .eq(StringUtils.hasText(moduleCode), CodeRuleDO::getModuleCode, moduleCode)
                .and(StringUtils.hasText(keyword), x -> x.like(CodeRuleDO::getBizCode, keyword.trim()).or().like(CodeRuleDO::getName, keyword.trim()));
        return codeRuleMapper.selectList(w).stream()
                .sorted(Comparator.comparing(CodeRuleDO::getModuleCode).thenComparing(CodeRuleDO::getBizCode))
                .map(this::toResp).toList();
    }

    public RuleResp get(Long id) {
        return toResp(getRule(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RuleSave req) {
        CodeRuleDO r = getRule(id);
        String prefix = req.prefix() == null ? "" : req.prefix().trim();
        String datePattern = req.datePattern() == null ? "" : req.datePattern();
        ResetCycle cycle = ResetCycle.valueOf(req.resetCycle());
        validate(r, prefix, datePattern, cycle);
        r.setName(req.name().trim());
        r.setPrefix(prefix);
        r.setDatePattern(datePattern);
        r.setSeparator("-".equals(req.separator()) ? "-" : "");
        r.setSeqLength(req.seqLength());
        r.setResetCycle(cycle);
        r.setAllowManual(req.allowManual());
        r.setVersion(req.version());
        codeRuleMapper.updateByIdOrFail(r);
    }

    /** 前缀变量（R02）、日期格式、重置周期与日期匹配（R03） */
    private void validate(CodeRuleDO r, String prefix, String datePattern, ResetCycle cycle) {
        if (!CodeRuleDefinition.DATE_PATTERNS.contains(datePattern)) throw BizException.of(SystemErrorCodes.CODE_RULE_DATE_INVALID, datePattern);
        String withoutVars = CodeRuleDefinition.VAR.matcher(prefix).replaceAll("");
        if (!withoutVars.matches("[\\p{L}\\p{N}\\-_/]*")) throw new BizException(SystemErrorCodes.CODE_RULE_PREFIX_INVALID);
        List<String> allowed = allowedVars(r);
        Matcher m = CodeRuleDefinition.VAR.matcher(prefix);
        while (m.find()) {
            if (!allowed.contains(m.group(1))) {
                throw BizException.of(SystemErrorCodes.CODE_RULE_VAR_INVALID, m.group(1), allowed.isEmpty() ? "无" : String.join("、", allowed));
            }
        }
        switch (cycle) {
            case YEAR -> {
                if (!datePattern.contains("yy")) throw BizException.of(SystemErrorCodes.CODE_RULE_RESET_MISMATCH, "按年", "年");
            }
            case MONTH -> {
                if (!datePattern.contains("yy") || !datePattern.contains("MM")) throw BizException.of(SystemErrorCodes.CODE_RULE_RESET_MISMATCH, "按月", "年和月");
            }
            case DAY -> {
                if (!datePattern.contains("dd")) throw BizException.of(SystemErrorCodes.CODE_RULE_RESET_MISMATCH, "按日", "年月日");
            }
            default -> {
            }
        }
    }

    /** 预览示例：按当前日期和该重置键的下一个流水号计算，不占用流水号；前缀变量原样显示 */
    public String preview(PreviewReq req) {
        CodeRuleDO r = codeRuleMapper.selectByBizCode(req.bizCode());
        if (r == null) throw BizException.of(SystemErrorCodes.CODE_RULE_NOT_FOUND, req.bizCode());
        ResetCycle cycle = req.resetCycle() == null ? ResetCycle.NEVER : ResetCycle.valueOf(req.resetCycle());
        String prefix = req.prefix() == null ? "" : req.prefix();
        String datePattern = req.datePattern() == null ? "" : req.datePattern();
        validate(r, prefix, datePattern, cycle);
        return example(req.bizCode(), prefix, datePattern, req.separator(), req.seqLength(), cycle);
    }

    private String example(String bizCode, String prefix, String datePattern, String separator, int seqLength, ResetCycle cycle) {
        LocalDate today = LocalDate.now(clock);
        long next = 1;
        if (!hasVars(prefix)) {
            Long cur = codeSeqMapper.selectCurrent(bizCode, resetKey(cycle, today));
            next = cur == null ? 1 : cur + 1;
        }
        return format(prefix, datePattern, "-".equals(separator) ? "-" : "", seqLength, today, next);
    }

    public List<SeqResp> seqs(Long id) {
        CodeRuleDO r = getRule(id);
        return codeSeqMapper.selectByBizCode(r.getBizCode()).stream()
                .map(s -> new SeqResp(s.resetKey(), s.currentValue(), s.updatedAt())).toList();
    }

    /** 调整流水号：只能大于当前值（R04） */
    @Transactional(rollbackFor = Exception.class)
    public void adjustSeq(Long id, SeqAdjust req) {
        CodeRuleDO r = getRule(id);
        Long cur = codeSeqMapper.selectCurrent(r.getBizCode(), req.resetKey());
        long current = cur == null ? 0 : cur;
        if (req.newValue() <= current) throw BizException.of(SystemErrorCodes.CODE_RULE_SEQ_TOO_SMALL, current);
        if (cur == null) {
            codeSeqMapper.insertFirst(r.getBizCode(), req.resetKey());
        }
        if (codeSeqMapper.adjust(r.getBizCode(), req.resetKey(), req.newValue()) == 0 && req.newValue() != 1) {
            Long now = codeSeqMapper.selectCurrent(r.getBizCode(), req.resetKey());
            throw BizException.of(SystemErrorCodes.CODE_RULE_SEQ_TOO_SMALL, now);
        }
    }

    public boolean isManualAllowedByBizCode(String bizCode) {
        return isManualAllowed(bizCode);
    }

    private CodeRuleDO getRule(Long id) {
        CodeRuleDO r = codeRuleMapper.selectById(id);
        if (r == null) throw BizException.of(SystemErrorCodes.CODE_RULE_NOT_FOUND, id);
        return r;
    }

    private static List<String> allowedVars(CodeRuleDO r) {
        return StringUtils.hasText(r.getAllowedVars()) ? Arrays.asList(r.getAllowedVars().split(",")) : List.of();
    }

    private RuleResp toResp(CodeRuleDO r) {
        return new RuleResp(r.getId(), r.getModuleCode(), moduleNames.getOrDefault(r.getModuleCode(), r.getModuleCode()),
                r.getBizCode(), r.getName(), r.getPrefix(), r.getDatePattern(), r.getSeparator(), r.getSeqLength(),
                r.getResetCycle().name(), Boolean.TRUE.equals(r.getAllowManual()), allowedVars(r),
                example(r.getBizCode(), r.getPrefix(), r.getDatePattern(), r.getSeparator(), r.getSeqLength(), r.getResetCycle()),
                r.getVersion());
    }
}
