package com.erp.module.system.controller.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 编码规则 VO */
public final class CodeRuleVOs {

    private CodeRuleVOs() {
    }

    public record RuleResp(Long id, String moduleCode, String moduleName, String bizCode, String name, String prefix,
                           String datePattern, String separator, int seqLength, String resetCycle, boolean allowManual,
                           List<String> allowedVars, String example, Integer version) {
    }

    public record RuleSave(
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            @Size(max = 32) String prefix,
            String datePattern,
            String separator,
            @NotNull @Min(value = 1, message = "流水号位数必须在 1～12 之间") @Max(value = 12, message = "流水号位数必须在 1～12 之间") Integer seqLength,
            @NotBlank String resetCycle,
            boolean allowManual,
            Integer version) {
    }

    /** 预览：按表单内容计算示例，不占用流水号 */
    public record PreviewReq(@NotBlank String bizCode, String prefix, String datePattern, String separator,
                             @NotNull Integer seqLength, String resetCycle) {
    }

    public record SeqResp(String resetKey, long currentValue, LocalDateTime updatedAt) {
    }

    public record SeqAdjust(@NotBlank String resetKey, @NotNull @Min(1) Long newValue) {
    }

    public record ManualInfo(boolean allowManual) {
    }
}
