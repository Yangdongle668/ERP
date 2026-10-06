package com.erp.module.fx.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 汇率数据源（默认中国银行；测试中替换为固定数据） */
public interface FxQuoteSource {

    /**
     * 取得外币对人民币的现汇买入价（每 1 单位外币）。
     *
     * @return 币别代码（USD、EUR）→ 报价；缺少某个币别时抛出异常
     */
    Map<String, Quote> fetch() throws Exception;

    record Quote(String currency, BigDecimal rate, LocalDateTime publishTime) {
    }
}
