package com.erp.module.fx.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/** 实时汇率（需求 16-实时汇率）。汇率均为中国银行现汇买入价（每 1 单位外币），6 位小数。 */
public interface FxRateApi {

    /** 最新汇率（内存缓存，每 15 分钟刷新）；从未取得过时为空 */
    Optional<FxQuoteDTO> latest(FxPair pair);

    /** 日平均汇率（仅美元保存历史）：当天未结束时为截至目前的平均值 */
    Optional<BigDecimal> dailyAverage(FxPair pair, LocalDate date);

    /** 月平均汇率（仅美元，当月各日平均汇率的平均值），月份结束后计算 */
    Optional<BigDecimal> monthlyAverage(FxPair pair, YearMonth month);
}
