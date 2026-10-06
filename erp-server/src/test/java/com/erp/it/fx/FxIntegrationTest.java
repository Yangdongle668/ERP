package com.erp.it.fx;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.fx.service.FxQuoteSource.Quote;
import com.erp.module.fx.service.FxService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实时汇率（需求 16-实时汇率）。报价日期使用 1999 年：推送到系统汇率表的自动汇率早于其他测试维护的汇率，不影响其他测试。
 */
class FxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FxService fxService;
    @Autowired
    private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void login() throws Exception {
        admin = loginAsAdmin();
        ItFxConfig.FAIL.set(null);
    }

    @AfterEach
    void clear() {
        ItFxConfig.FAIL.set(null);
    }

    private static void quote(String usd, String eur, LocalDateTime t) {
        ItFxConfig.NEXT.set(Map.of("USD", new Quote("USD", new BigDecimal(usd), t), "EUR", new Quote("EUR", new BigDecimal(eur), t),
                "JPY", new Quote("JPY", new BigDecimal("0.047612"), t), "KRW", new Quote("KRW", new BigDecimal("0.005131"), t),
                "AUD", new Quote("AUD", new BigDecimal("4.651200"), t)));
    }

    private long rateCount(String currency, LocalDate date) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_exchange_rate WHERE currency = ? AND effective_date = ? AND deleted = 0",
                Long.class, currency, date);
    }

    private BigDecimal sysRate(String currency, String type, LocalDate date) {
        return jdbc.queryForObject("SELECT rate FROM sys_exchange_rate WHERE currency = ? AND rate_type = ? AND effective_date = ? AND deleted = 0",
                BigDecimal.class, currency, type, date);
    }

    /** T01 取得报价（5 个外币对人民币）；只有美元保存、计算当日平均并推送日汇率（来源 AUTO）；同一发布时间不重复计入；15 分钟缓存 */
    @Test
    void refreshAndDailyAverage() throws Exception {
        LocalDate d = LocalDate.of(1999, 3, 8);
        quote("7.100000", "7.810000", d.atTime(9, 30));
        JsonNode st = ok(doPost("/api/fx/refresh", admin, null));
        assertThat(st.at("/quotes/0/pair").asText()).isEqualTo("USD_CNY");
        assertThat(st.at("/quotes/0/rate").decimalValue()).isEqualByComparingTo("7.1");
        assertThat(st.at("/quotes").findValuesAsText("pair")).containsExactly("USD_CNY", "EUR_CNY", "JPY_CNY", "KRW_CNY", "AUD_CNY");
        assertThat(st.at("/quotes/3/rate").decimalValue()).isEqualByComparingTo("0.005131");
        assertThat(st.at("/pushTarget").asText()).isEqualTo("CNY");

        quote("7.200000", "7.830000", d.atTime(10, 30));
        ok(doPost("/api/fx/refresh", admin, null));
        ok(doPost("/api/fx/refresh", admin, null)); // 同一发布时间不重复计入
        JsonNode daily = ok(doGet("/api/fx/daily?pair=USD_CNY&from=1999-03-08&to=1999-03-08", admin));
        assertThat(daily.at("/0/avgRate").decimalValue()).isEqualByComparingTo("7.15");
        assertThat(daily.at("/0/sampleCount").asInt()).isEqualTo(2);
        assertThat(daily.at("/0/finalized").asBoolean()).isFalse();
        assertThat(sysRate("USD", "DAILY", d)).isEqualByComparingTo("7.15");
        assertThat(jdbc.queryForObject("SELECT source FROM sys_exchange_rate WHERE currency = 'USD' AND rate_type = 'DAILY' AND effective_date = ?",
                String.class, d)).isEqualTo("AUTO");
        // 其他币别只有实时报价：不入库、不推送
        assertThat(rateCount("EUR", d)).isZero();
        assertThat(rateCount("JPY", d)).isZero();
        assertThat(ok(doGet("/api/fx/daily?pair=EUR_CNY&from=1999-03-08&to=1999-03-08", admin)).size()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fx_quote WHERE pair <> 'USD_CNY'", Long.class)).isZero();
        JsonNode st2 = ok(doGet("/api/fx/status", admin));
        assertThat(st2.at("/quotes/1/rate").decimalValue()).isEqualByComparingTo("7.83");
        assertThat(st2.at("/quotes/1/persisted").asBoolean()).isFalse();
        JsonNode todayAvg = st2.at("/quotes/0/todayAverage"); // 1999 年的报价，不是今天
        assertThat(todayAvg.isMissingNode() || todayAvg.isNull()).isTrue();

        // 缓存 15 分钟内不重复取数
        quote("9.000000", "9.000000", d.atTime(11, 0));
        assertThat(fxService.refresh(false)).isFalse();
    }

    /** T02 失败计数与退避；人工维护的汇率不被覆盖 */
    @Test
    void failureAndManualRate() throws Exception {
        LocalDate d = LocalDate.of(1999, 4, 6);
        ok(doPost("/api/system/exchange-rates", admin, Map.of("currency", "USD", "rateType", "DAILY", "effectiveDate", d.toString(), "rate", "8.888")));
        ItFxConfig.FAIL.set("连接超时");
        assertError(doPost("/api/fx/refresh", admin, null), "获取中国银行汇率失败：连接超时");
        JsonNode st = ok(doGet("/api/fx/status", admin));
        assertThat(st.at("/consecutiveFailures").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(st.at("/lastError").asText()).isEqualTo("连接超时");

        ItFxConfig.FAIL.set(null);
        quote("7.000000", "7.700000", d.atTime(9, 0));
        ok(doPost("/api/fx/refresh", admin, null));
        assertThat(ok(doGet("/api/fx/status", admin)).at("/consecutiveFailures").asInt()).isZero();
        assertThat(sysRate("USD", "DAILY", d)).isEqualByComparingTo("8.888");
        assertThat(ok(doGet("/api/fx/daily?pair=USD_CNY&from=1999-04-06&to=1999-04-06", admin)).at("/0/avgRate").decimalValue()).isEqualByComparingTo("7");
    }

    /** T03 日结算、月平均（推送月末汇率）、保留 3 年 */
    @Test
    void settleMonthlyAndPurge() throws Exception {
        quote("6.000000", "7.000000", LocalDateTime.of(1999, 1, 11, 9, 0));
        ok(doPost("/api/fx/refresh", admin, null));
        quote("6.200000", "7.200000", LocalDateTime.of(1999, 1, 12, 9, 0));
        ok(doPost("/api/fx/refresh", admin, null));
        jdbc.update("INSERT INTO fx_quote (id, pair, rate, quote_date, publish_time, fetched_at, source, created_at, updated_at) "
                + "VALUES (990001, 'USD_CNY', 5, '1995-06-01', '1995-06-01 09:00:00', CURRENT_TIMESTAMP, 'BOC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        String r = fxService.settle(LocalDate.of(1999, 2, 2));
        assertThat(r).contains("清理过期");
        JsonNode daily = ok(doGet("/api/fx/daily?pair=USD_CNY&from=1999-01-01&to=1999-01-31", admin));
        assertThat(daily.findValuesAsText("finalized")).containsOnly("true");
        JsonNode monthly = ok(doGet("/api/fx/monthly", admin));
        JsonNode jan = null;
        for (JsonNode m : monthly) if ("1999-01".equals(m.at("/rateMonth").asText()) && "USD_CNY".equals(m.at("/pair").asText())) jan = m;
        assertThat(jan).isNotNull();
        assertThat(jan.at("/avgRate").decimalValue()).isEqualByComparingTo("6.1");
        assertThat(jan.at("/dayCount").asInt()).isEqualTo(2);
        assertThat(sysRate("USD", "MONTH_END", LocalDate.of(1999, 1, 31))).isEqualByComparingTo("6.1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fx_quote WHERE id = 990001", Long.class)).isZero();
        // 再次结算不重复
        assertThat(fxService.settle(LocalDate.of(1999, 2, 2))).startsWith("日平均 0 条，月平均 0 条");
    }
}
