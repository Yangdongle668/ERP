package com.erp.module.fx.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 最新汇率（15 分钟缓存）。
 *
 * @param publishTime 银行发布时间
 * @param fetchedAt   本系统取得时间
 * @param stale       超过 15 分钟未成功刷新（取数失败时返回最后一次成功的值）
 */
public record FxQuoteDTO(FxPair pair, BigDecimal rate, LocalDateTime publishTime, LocalDateTime fetchedAt, boolean stale) {
}
