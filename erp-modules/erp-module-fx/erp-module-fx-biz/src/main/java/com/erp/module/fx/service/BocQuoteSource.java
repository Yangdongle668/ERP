package com.erp.module.fx.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中国银行外汇牌价（https://www.boc.cn/sourcedb/whpj/）：取「现汇买入价」，页面单位为每 100 外币，换算为每 1 外币。
 * 表格列：货币名称、现汇买入价、现钞买入价、现汇卖出价、现钞卖出价、中行折算价、发布日期、发布时间（列位置按表头识别）。
 */
@Component
public class BocQuoteSource implements FxQuoteSource {

    /** 牌价中的货币名称 → 币别代码 */
    static final Map<String, String> NAMES = Map.of("美元", "USD", "欧元", "EUR", "日元", "JPY", "韩国元", "KRW", "韩元", "KRW",
            "澳大利亚元", "AUD", "澳元", "AUD");
    private static final Pattern ROW = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    private static final Pattern DATE = Pattern.compile("(\\d{4})[.\\-/年](\\d{1,2})[.\\-/月](\\d{1,2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final String url;
    private final HttpClient client;

    public BocQuoteSource(@Value("${erp.fx.boc-url:https://www.boc.cn/sourcedb/whpj/index.html}") String url) {
        this.url = url;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public Map<String, Quote> fetch() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (ERP exchange-rate service)").header("Accept", "text/html").GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
        return parse(new String(resp.body(), StandardCharsets.UTF_8), LocalDateTime.now());
    }

    /** 解析牌价表；缺少美元或欧元时报错 */
    static Map<String, Quote> parse(String html, LocalDateTime now) {
        int buyCol = 1;
        Map<String, Quote> result = new HashMap<>();
        // 同一币别有多个名称（韩国元 / 韩元）时取第一行
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            List<String> cells = cells(rows.group(1));
            if (cells.isEmpty()) continue;
            int header = cells.indexOf("现汇买入价");
            if (header > 0) {
                buyCol = header;
                continue;
            }
            String code = NAMES.get(cells.get(0));
            if (code == null || cells.size() <= buyCol || result.containsKey(code)) continue;
            String price = cells.get(buyCol).replace(",", "");
            if (!price.matches("\\d+(\\.\\d+)?")) continue;
            BigDecimal rate = new BigDecimal(price).divide(HUNDRED, 6, RoundingMode.HALF_UP);
            result.put(code, new Quote(code, rate, publishTime(String.join(" ", cells.subList(buyCol, cells.size())), now)));
        }
        List<String> missing = new ArrayList<>();
        for (String code : new java.util.TreeSet<>(NAMES.values())) if (!result.containsKey(code)) missing.add(code);
        if (!missing.isEmpty()) throw new IllegalStateException("牌价中没有 " + String.join("、", missing) + " 的现汇买入价");
        return result;
    }

    private static List<String> cells(String row) {
        List<String> list = new ArrayList<>();
        Matcher m = CELL.matcher(row);
        while (m.find()) list.add(m.group(1).replaceAll("(?s)<[^>]+>", "").replace("&nbsp;", " ").trim());
        return list;
    }

    /** 发布日期与时间可能在同一格（2026-10-06 10:30:00）或两格（2026.10.06 / 10:30:00）；识别不到时用取得时间 */
    static LocalDateTime publishTime(String text, LocalDateTime now) {
        Matcher d = DATE.matcher(text);
        if (!d.find()) return now.withNano(0);
        Matcher t = TIME.matcher(text.substring(d.end()));
        int h = 0, mi = 0, s = 0;
        if (t.find()) {
            h = Integer.parseInt(t.group(1));
            mi = Integer.parseInt(t.group(2));
            s = t.group(3) == null ? 0 : Integer.parseInt(t.group(3));
        }
        return LocalDateTime.of(Integer.parseInt(d.group(1)), Integer.parseInt(d.group(2)), Integer.parseInt(d.group(3)), h, mi, s);
    }
}
