package com.erp.module.system.api.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** 币别与汇率（01-07 币别汇率）。 */
public interface CurrencyApi {

    /** 本位币代码 */
    String getBaseCurrency();

    Optional<CurrencyDTO> get(String code);

    /** 校验币别存在且启用 */
    CurrencyDTO validate(String code);

    /** 日汇率，等同 getRate(currency, date, DAILY) */
    BigDecimal getRate(String currency, LocalDate date);

    /**
     * 汇率（SYS-CUR-R05）：本位币返回 1；否则取生效日期 ≤ date 的最近一条；
     * 一条都没有时抛出“未维护币别 {currency} 在 {date} 及之前的汇率，请先维护汇率”。
     */
    BigDecimal getRate(String currency, LocalDate date, RateType type);

    /** 金额精度（小数位） */
    int getPrecision(String currency);

    /** 按币别精度舍入金额（HALF_UP） */
    BigDecimal roundAmount(BigDecimal amount, String currency);

    /** 本位币金额 = ROUND(原币金额 × 汇率, 本位币精度)（SYS-CUR-R08） */
    BigDecimal toBase(BigDecimal amount, BigDecimal rate);
}
