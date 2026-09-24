package com.erp.module.system.service;

import com.erp.common.exception.BizException;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.dal.dataobject.CodeRuleDO;
import com.erp.module.system.dal.mapper.CodeRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 编码规则服务，同时是 {@link CodeRuleApi} 的实现。 */
@Service
public class CodeRuleService implements CodeRuleApi {

    private final CodeRuleMapper codeRuleMapper;
    private final CodeSeqAllocator seqAllocator;
    private final Map<String, CodeRuleDefinition> definitions;
    private final Clock clock;

    @Autowired
    public CodeRuleService(CodeRuleMapper codeRuleMapper, CodeSeqAllocator seqAllocator,
                           List<CodeRuleDefinition> definitions) {
        this(codeRuleMapper, seqAllocator, definitions, Clock.systemDefaultZone());
    }

    CodeRuleService(CodeRuleMapper codeRuleMapper, CodeSeqAllocator seqAllocator,
                    List<CodeRuleDefinition> definitions, Clock clock) {
        this.codeRuleMapper = codeRuleMapper;
        this.seqAllocator = seqAllocator;
        this.definitions = definitions.stream()
                .collect(Collectors.toMap(CodeRuleDefinition::bizCode, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("编码规则重复声明: " + a.bizCode());
                }));
        this.clock = clock;
    }

    @Override
    public String nextCode(String bizCode) {
        CodeRuleDO rule = loadOrInitRule(bizCode);
        LocalDate today = LocalDate.now(clock);
        long seq = seqAllocator.next(bizCode, resetKey(rule.getResetCycle(), today));
        return format(rule, today, seq);
    }

    static String resetKey(CodeRuleDefinition.ResetCycle cycle, LocalDate date) {
        return switch (cycle) {
            case NEVER -> "ALL";
            case YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy"));
            case MONTH -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case DAY -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        };
    }

    static String format(CodeRuleDO rule, LocalDate date, long seq) {
        String datePart = rule.getDatePattern() == null || rule.getDatePattern().isEmpty()
                ? "" : date.format(DateTimeFormatter.ofPattern(rule.getDatePattern()));
        String seqPart = String.valueOf(seq);
        if (seqPart.length() < rule.getSeqLength()) {
            seqPart = "0".repeat(rule.getSeqLength() - seqPart.length()) + seqPart;
        }
        return rule.getPrefix() + datePart + seqPart;
    }

    /** 优先使用数据库中的规则；没有时用模块声明的默认规则初始化。 */
    private CodeRuleDO loadOrInitRule(String bizCode) {
        CodeRuleDO rule = codeRuleMapper.selectByBizCode(bizCode);
        if (rule != null) {
            return rule;
        }
        CodeRuleDefinition def = definitions.get(bizCode);
        if (def == null) {
            throw BizException.of(SystemErrorCodes.CODE_RULE_NOT_FOUND, bizCode);
        }
        CodeRuleDO created = new CodeRuleDO();
        created.setBizCode(def.bizCode());
        created.setName(def.name());
        created.setPrefix(def.prefix());
        created.setDatePattern(def.datePattern());
        created.setSeqLength(def.seqLength());
        created.setResetCycle(def.resetCycle());
        try {
            codeRuleMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return codeRuleMapper.selectByBizCode(bizCode);
        }
    }
}
