package com.erp.module.fx.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BocQuoteSourceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 11, 0);

    /** 中国银行牌价：日期、时间分两格（2026.10.06 / 10:30:00），每 100 外币 */
    @Test
    void parseSeparateDateTime() {
        String html = """
                <table><tr><th>货币名称</th><th>现汇买入价</th><th>现钞买入价</th><th>现汇卖出价</th><th>现钞卖出价</th><th>中行折算价</th><th>发布日期</th><th>发布时间</th></tr>
                <tr><td>阿联酋迪拉姆</td><td></td><td>186.69</td><td></td><td>200.53</td><td>193.68</td><td class="pjrq">2026.10.06</td><td class="pjrq">10:30:00</td></tr>
                <tr>
                  <td>欧元</td><td>780.12</td><td>755.88</td><td>785.88</td><td>788.42</td><td>778.35</td>
                  <td class="pjrq">2026.10.06</td><td class="pjrq">10:30:00</td>
                </tr>
                <tr><td>美元</td><td>712.34</td><td>712.34</td><td>715.33</td><td>715.33</td><td>711.0</td><td class="pjrq">2026.10.06</td><td class="pjrq">10:29:45</td></tr>
                </table>""";
        Map<String, FxQuoteSource.Quote> q = BocQuoteSource.parse(html, NOW);
        assertThat(q.get("USD").rate()).isEqualByComparingTo("7.1234");
        assertThat(q.get("EUR").rate()).isEqualByComparingTo("7.8012");
        assertThat(q.get("USD").publishTime()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 29, 45));
    }

    /** 日期时间同一格（2026-10-06 10:30:00）；缺少欧元时报错 */
    @Test
    void parseCombinedAndMissing() {
        String html = "<tr><th>货币名称</th><th>现汇买入价</th></tr><tr><td>美元</td><td>712.34</td><td>2026-10-06 10:30:00</td></tr>"
                + "<tr><td>欧元</td><td>780.00</td><td>2026-10-06 10:30:00</td></tr>";
        assertThat(BocQuoteSource.parse(html, NOW).get("EUR").publishTime()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 30));
        assertThatThrownBy(() -> BocQuoteSource.parse("<tr><td>美元</td><td>712.34</td></tr>", NOW)).hasMessageContaining("EUR");
    }

    /** 退避：1、2、4、8、16、32、60、60 分钟 */
    @Test
    void backoff() {
        assertThat(FxPoller.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(FxPoller.backoff(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(FxPoller.backoff(6)).isEqualTo(Duration.ofMinutes(32));
        assertThat(FxPoller.backoff(7)).isEqualTo(Duration.ofMinutes(60));
        assertThat(FxPoller.backoff(30)).isEqualTo(Duration.ofMinutes(60));
        assertThat(FxPoller.backoff(0)).isEqualTo(Duration.ofMinutes(15));
    }
}
