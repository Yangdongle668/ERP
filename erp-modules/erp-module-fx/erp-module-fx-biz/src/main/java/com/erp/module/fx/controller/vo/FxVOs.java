package com.erp.module.fx.controller.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 实时汇率（需求 16-实时汇率） */
public final class FxVOs {

    private FxVOs() {
    }

    public record QuoteRow(String pair, String label, BigDecimal rate, LocalDateTime publishTime, LocalDateTime fetchedAt, boolean stale,
                           BigDecimal todayAverage) {
    }

    /**
     * @param consecutiveFailures 连续失败次数（退避重试中）
     * @param pushTarget          推送到系统汇率表的本位币；本位币不是人民币时为空（只在本模块保存）
     */
    public record FxStatus(boolean enabled, boolean polling, LocalDateTime lastSuccessAt, LocalDateTime lastAttemptAt, String lastError,
                           int consecutiveFailures, LocalDateTime nextRunAt, String pushTarget, List<QuoteRow> quotes) {
    }

    public record DailyRow(String pair, LocalDate rateDate, BigDecimal avgRate, BigDecimal minRate, BigDecimal maxRate, int sampleCount,
                           boolean finalized) {
    }

    public record MonthlyRow(String pair, String rateMonth, BigDecimal avgRate, int dayCount) {
    }
}
