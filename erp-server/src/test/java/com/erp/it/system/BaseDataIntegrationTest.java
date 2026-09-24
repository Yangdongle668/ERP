package com.erp.it.system;

import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.paymentterm.BaseEvent;
import com.erp.module.system.api.paymentterm.DueNode;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.uom.UomApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 编码规则、计量单位、币别汇率、付款条件验收用例（01-05、06、07、14） */
class BaseDataIntegrationTest extends SystemTestSupport {

    @Autowired
    private UomApi uomApi;
    @Autowired
    private CurrencyApi currencyApi;
    @Autowired
    private PaymentTermApi paymentTermApi;

    // ==================== 计量单位 ====================

    @Test
    void uomConvert_UOM_T01_T02_T03() {
        assertThat(uomApi.convert(new BigDecimal("2.5"), "KG", "G")).isEqualByComparingTo("2500");
        assertThat(uomApi.convert(new BigDecimal("500"), "G", "KG")).isEqualByComparingTo("0.5");
        assertThat(uomApi.convert(BigDecimal.ONE, "T", "G")).isEqualByComparingTo("1000000");
        assertThatThrownBy(() -> uomApi.convert(BigDecimal.ONE, "KG", "M")).hasMessage("单位 KG 与 M 之间没有换算关系");
        assertThat(uomApi.round(new BigDecimal("1.23456"), "KG")).isEqualByComparingTo("1.235");
    }

    @Test
    void uomRules_UOM_T04_T06_T07() throws Exception {
        assertError(doPost("/api/system/uom-conversions", admin, Map.of("fromUom", "KG", "toUom", "M", "rate", "1")), "只能在同类别单位之间换算");
        assertError(doPost("/api/system/uom-conversions", admin, Map.of("fromUom", "G", "toUom", "KG", "rate", "0.001")), "该换算已存在");
        JsonNode kg = find(ok(doGet("/api/system/uoms?keyword=KG", admin)), "code", "KG");
        Map<String, Object> body = new HashMap<>(Map.of("code", "KG", "name", "千克", "category", "WEIGHT", "precision", 2, "sort", 100,
                "version", kg.at("/version").asInt()));
        assertError(doPut("/api/system/uoms/" + kg.at("/id").asText(), admin, body), "单位精度只能调大");
        assertError(doDelete("/api/system/uoms/" + kg.at("/id").asText(), admin), "内置单位不能删除");
        // 新建单位后可删除
        String code = "U" + uniq();
        String id = ok(doPost("/api/system/uoms", admin, Map.of("code", code, "name", "单位" + code, "category", "COUNT", "precision", 0, "sort", 999))).asText();
        assertThat(ok(doGet("/api/system/uoms/simple", admin)).toString()).contains(code);
        ok(doDelete("/api/system/uoms/" + id, admin));
    }

    // ==================== 币别汇率 ====================

    @Test
    void rateLookup_CUR_T01_T02_T03() throws Exception {
        ok(doPost("/api/system/exchange-rates", admin, rate("GBP", "2026-09-20", "9.1")));
        ok(doPost("/api/system/exchange-rates", admin, rate("GBP", "2026-09-23", "9.12")));
        assertThat(currencyApi.getRate("GBP", LocalDate.parse("2026-09-24"))).isEqualByComparingTo("9.12");
        assertThat(currencyApi.getRate("GBP", LocalDate.parse("2026-09-21"))).isEqualByComparingTo("9.1");
        assertThat(currencyApi.getRate("CNY", LocalDate.parse("2026-09-21"))).isEqualByComparingTo("1");
        JsonNode lookup = ok(doGet("/api/system/exchange-rates/lookup?currency=GBP&date=2026-09-24", admin));
        assertThat(lookup.at("/effectiveDate").asText()).isEqualTo("2026-09-23");
        assertError(doGet("/api/system/exchange-rates/lookup?currency=GBP&date=2026-09-19", admin),
                "未维护币别 GBP 在 2026-09-19 及之前的汇率，请先维护汇率");
        assertError(doPost("/api/system/exchange-rates", admin, rate("GBP", "2026-09-20", "9.2")), "GBP 在 2026-09-20 已有日汇率");
        assertError(doPost("/api/system/exchange-rates", admin, rate("CNY", "2026-09-20", "1")), "本位币不需要维护汇率");
    }

    @Test
    void batchRates_CUR_T05_andMonthEnd() throws Exception {
        Map<String, Object> batch = Map.of("rateType", "DAILY", "effectiveDate", "2026-08-24",
                "lines", List.of(Map.of("currency", "USD", "rate", "7.1"), Map.of("currency", "EUR", "rate", "7.8"), Map.of("currency", "HKD", "rate", "0.91")));
        assertThat(ok(doPost("/api/system/exchange-rates/batch", admin, batch)).asInt()).isEqualTo(3);
        // 月末汇率自动调整为当月最后一天
        ok(doPost("/api/system/exchange-rates", admin, Map.of("currency", "USD", "rateType", "MONTH_END", "effectiveDate", "2026-08-15", "rate", "7.12")));
        JsonNode page = ok(doGet("/api/system/exchange-rates?currency=USD&rateType=MONTH_END&pageNo=1&pageSize=10", admin));
        assertThat(page.at("/list/0/effectiveDate").asText()).isEqualTo("2026-08-31");
    }

    @Test
    void baseCurrencyRules_CUR_R02() throws Exception {
        JsonNode cny = find(ok(doGet("/api/system/currencies", admin)), "code", "CNY");
        assertError(doPost("/api/system/currencies/" + cny.at("/id").asText() + "/disable", admin, null), "本位币不能停用");
        assertThat(currencyApi.getBaseCurrency()).isEqualTo("CNY");
        assertThat(currencyApi.toBase(new BigDecimal("100.01"), new BigDecimal("7.12"))).isEqualByComparingTo("712.07");
    }

    // ==================== 付款条件 ====================

    @Test
    void dueDates_PT_T01_T02() {
        Long tt = termId("TT30-70BL");
        List<DueNode> nodes = paymentTermApi.calcDueDates(tt, new BigDecimal("10000.01"), Map.of(BaseEvent.ORDER_DATE, LocalDate.parse("2026-09-01")));
        assertThat(nodes.get(0).amount()).isEqualByComparingTo("3000.00");
        assertThat(nodes.get(0).dueDate()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(nodes.get(1).amount()).isEqualByComparingTo("7000.01");
        assertThat(nodes.get(1).dueDate()).isNull();

        List<DueNode> monthly = paymentTermApi.calcDueDates(termId("MONTHLY60"), new BigDecimal("100"), Map.of(BaseEvent.SHIPMENT, LocalDate.parse("2026-09-15")));
        assertThat(monthly.get(0).dueDate()).isEqualTo(LocalDate.parse("2026-11-29"));
    }

    @Test
    void percentMustSumTo100_PT_T03() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("code", "T" + uniq(), "name", "测试", "settlementMethod", "TT", "usage", "BOTH",
                "nodes", List.of(node("定金", "0.3"), node("尾款", "0.6"))));
        assertError(doPost("/api/system/payment-terms", admin, body), "付款节点比例合计必须等于 100%");
        body.put("nodes", List.of(node("定金", "0.3"), node("尾款", "0.7")));
        String id = ok(doPost("/api/system/payment-terms", admin, body)).asText();
        assertThat(ok(doGet("/api/system/payment-terms/" + id, admin)).at("/nodeSummary").asText()).isEqualTo("30% 下单日 / 70% 下单日 30 天");
        assertThat(ok(doGet("/api/system/payment-terms/simple?usage=PURCHASE", admin)).toString()).contains("PUR_MONTHLY30");
        assertThat(ok(doGet("/api/system/payment-terms/simple?usage=SALES", admin)).toString()).doesNotContain("PUR_MONTHLY30");
    }

    @Test
    void countries() throws Exception {
        JsonNode list = ok(doGet("/api/system/countries", admin));
        assertThat(list.size()).isGreaterThan(200);
        assertThat(find(list, "code", "CN").at("/nameCn").asText()).isEqualTo("中国");
        assertThat(find(list, "code", "US").at("/nameEn").asText()).isEqualTo("United States");
    }

    // ==================== 编码规则 ====================

    @Test
    void codeRuleValidationAndAdjust_COD_T04_T05() throws Exception {
        JsonNode rule = find(ok(doGet("/api/system/code-rules", admin)), "bizCode", "ENG_TOOLING");
        String id = rule.at("/id").asText();
        Map<String, Object> body = new HashMap<>(Map.of("name", "工装编号", "prefix", "T", "datePattern", "", "separator", "",
                "seqLength", 5, "resetCycle", "MONTH", "allowManual", true, "version", rule.at("/version").asInt()));
        assertError(doPut("/api/system/code-rules/" + id, admin, body), "重置周期为“按月”时，日期格式必须包含年和月");
        body.put("prefix", "{categoryPrefix}");
        body.put("resetCycle", "NEVER");
        assertError(doPut("/api/system/code-rules/" + id, admin, body), "前缀中的变量「categoryPrefix」不可用，可用变量：无");

        // 调整流水号：只能调大
        String code = null;
        for (int i = 0; i < 3; i++) code = callNextMaterialCode();
        JsonNode seqs = ok(doGet("/api/system/code-rules/" + id + "/seqs", admin));
        long current = seqs.get(0).at("/currentValue").asLong();
        assertError(doPut("/api/system/code-rules/" + id + "/seqs", admin, Map.of("resetKey", "ALL", "newValue", current - 1)),
                "新值必须大于当前值 " + current);
        ok(doPut("/api/system/code-rules/" + id + "/seqs", admin, Map.of("resetKey", "ALL", "newValue", current + 100)));
        assertThat(callNextMaterialCode()).isEqualTo(String.format("T%05d", current + 101));
        assertThat(code).startsWith("T");
        assertThat(ok(doGet("/api/system/code-rules/by-biz/ENG_TOOLING/allow-manual", admin)).at("/allowManual").asBoolean()).isTrue();
        assertThat(ok(doPost("/api/system/code-rules/preview", admin, Map.of("bizCode", "ENG_TOOLING", "prefix", "MT", "datePattern", "yyMM",
                "separator", "-", "seqLength", 3, "resetCycle", "MONTH"))).asText()).matches("MT\\d{4}-001");
    }

    @Autowired
    private com.erp.module.system.api.coderule.CodeRuleApi codeRuleApi;

    private String callNextMaterialCode() {
        return codeRuleApi.nextCode("ENG_TOOLING");
    }

    // ==================== 工具 ====================

    private Long termId(String code) {
        try {
            for (JsonNode t : ok(doGet("/api/system/payment-terms?keyword=" + code, admin))) {
                if (t.at("/code").asText().equals(code)) return t.at("/id").asLong();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException(code);
    }

    private static Map<String, Object> node(String name, String percent) {
        return Map.of("name", name, "percent", percent, "baseEvent", "ORDER_DATE", "days", name.equals("尾款") ? 30 : 0);
    }

    private static Map<String, Object> rate(String currency, String date, String rate) {
        return Map.of("currency", currency, "rateType", "DAILY", "effectiveDate", date, "rate", rate);
    }

    private static JsonNode find(JsonNode list, String field, String value) {
        for (JsonNode n : list) if (n.at("/" + field).asText().equals(value)) return n;
        throw new IllegalStateException(field + "=" + value);
    }
}
