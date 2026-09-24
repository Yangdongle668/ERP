package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 打印模板 VO（需求 01-系统管理/09） */
public final class PrintVOs {

    private PrintVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TemplateQuery extends PageParam {
        private String bizType;
        private String language;
        private String status;
    }

    public record TemplateResp(Long id, String bizType, String bizTypeName, String name, String language, String paper, Integer paperWidth,
                               Integer paperHeight, String margin, boolean isDefault, boolean isBuiltin, String status, String remark,
                               LocalDateTime updatedAt, Integer version) {
    }

    public record TemplateDetail(Long id, String bizType, String bizTypeName, String name, String language, String paper, Integer paperWidth,
                                 Integer paperHeight, String margin, String content, boolean isDefault, boolean isBuiltin, String status,
                                 String remark, Integer version) {
    }

    public record TemplateSave(
            @NotBlank(message = "请选择单据类型") String bizType,
            @NotBlank(message = "请输入模板名称") @Size(max = 64, message = "模板名称不能超过 64 字") String name,
            @NotBlank @Pattern(regexp = "zh-CN|en", message = "语言只能是 zh-CN 或 en") String language,
            @NotBlank(message = "请选择纸张") String paper,
            @Min(value = 20, message = "纸张宽度 20～1000mm") @Max(value = 1000, message = "纸张宽度 20～1000mm") Integer paperWidth,
            @Min(value = 20, message = "纸张高度 20～2000mm") @Max(value = 2000, message = "纸张高度 20～2000mm") Integer paperHeight,
            @NotBlank(message = "请输入边距") @Size(max = 32) String margin,
            @NotBlank(message = "模板内容不能为空") String content,
            @Size(max = 256) String remark,
            Integer version) {
    }

    /** 打印按钮：该单据类型的启用模板 */
    public record Available(String bizType, String bizName, String dataApi, List<TemplateBrief> templates) {
    }

    public record TemplateBrief(Long id, String name, String language, boolean isDefault, String paper) {
    }

    /** 打印时取模板（登录即可） */
    public record ForPrint(Long id, String name, String language, String paper, Integer paperWidth, Integer paperHeight, String margin,
                           String content) {
    }

    public record BizResp(String bizType, String name, String moduleCode, String moduleName, String dataApi, JsonNode variables,
                          JsonNode sampleData) {
    }

    public record PrintLogReq(@NotBlank String bizType, @NotEmpty List<Long> bizIds, Long templateId) {
    }

    public record PrintCount(Long bizId, long count) {
    }

    public record ValidateReq(@NotNull String content) {
    }
}
