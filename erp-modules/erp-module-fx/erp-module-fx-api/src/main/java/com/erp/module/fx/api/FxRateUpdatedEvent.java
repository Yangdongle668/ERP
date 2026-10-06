package com.erp.module.fx.api;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 汇率更新（需求 16-实时汇率 R05）：取得新报价、日平均汇率结算、月平均汇率结算时发布，事务提交后通知。
 * 本位币为人民币时，USD / EUR 的日平均汇率、月平均汇率同时写入系统管理的汇率表（来源“自动”）。
 *
 * @param kind  QUOTE 实时报价、DAILY 日平均（finalized 为当天已结束）、MONTHLY 月平均
 * @param date  报价日期 / 日期 / 月份最后一天
 */
public class FxRateUpdatedEvent extends DomainEvent {

    public enum Kind { QUOTE, DAILY, MONTHLY }

    private final FxPair pair;
    private final Kind kind;
    private final LocalDate date;
    private final BigDecimal rate;
    private final boolean finalized;

    public FxRateUpdatedEvent(FxPair pair, Kind kind, LocalDate date, BigDecimal rate, boolean finalized) {
        this.pair = pair;
        this.kind = kind;
        this.date = date;
        this.rate = rate;
        this.finalized = finalized;
    }

    public FxPair getPair() {
        return pair;
    }

    public Kind getKind() {
        return kind;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public boolean isFinalized() {
        return finalized;
    }
}
