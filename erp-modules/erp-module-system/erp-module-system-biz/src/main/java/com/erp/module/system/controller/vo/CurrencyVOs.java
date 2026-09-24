package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 币别与汇率 VO */
public final class CurrencyVOs {

    private CurrencyVOs() {
    }

    public record CurrencyResp(Long id, String code, String name, String nameEn, String symbol, int amountPrecision,
                               boolean base, int sort, String status, Integer version) {
    }

    public record CurrencySave(
            @NotBlank(message = "请输入代码") @Pattern(regexp = "[A-Za-z]{3}", message = "代码为 3 位字母") String code,
            @NotBlank(message = "请输入名称") @Size(max = 16) String name,
            @Size(max = 32) String nameEn,
            @Size(max = 4) String symbol,
            @NotNull @Min(value = 0, message = "金额精度必须在 0～2 之间") @Max(value = 2, message = "金额精度必须在 0～2 之间") Integer amountPrecision,
            @NotNull Integer sort,
            Integer version) {
    }

    public record CurrencySimple(String code, String name, String symbol, int amountPrecision, boolean base) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RateQuery extends PageParam {
        private String currency;
        private String rateType;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        /** asc / desc（按生效日期，默认倒序） */
        private String sortOrder;
    }

    public record RateResp(Long id, String currency, String rateType, LocalDate effectiveDate, BigDecimal rate, String source,
                           String remark, String updatedByName, LocalDateTime updatedAt, Integer version) {
    }

    public record RateSave(
            @NotBlank(message = "请选择币别") String currency,
            @NotBlank(message = "请选择汇率类型") String rateType,
            @NotNull(message = "请选择生效日期") LocalDate effectiveDate,
            @NotNull(message = "请输入汇率") @DecimalMin(value = "0", inclusive = false, message = "汇率必须大于 0") BigDecimal rate,
            @Size(max = 128) String remark,
            Integer version) {
    }

    /** 批量录入：同一日期、类型，每个外币一行 */
    public record RateBatch(@NotBlank String rateType, @NotNull LocalDate effectiveDate, @NotEmpty List<@Valid BatchLine> lines) {
    }

    public record BatchLine(@NotBlank String currency,
                            @NotNull @DecimalMin(value = "0", inclusive = false, message = "汇率必须大于 0") BigDecimal rate) {
    }

    /** 单据带出汇率：rate 与实际命中的生效日期 */
    public record RateLookup(BigDecimal rate, LocalDate effectiveDate) {
    }
}
