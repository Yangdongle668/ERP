package com.erp.it.sales;

import com.erp.module.sales.api.forecast.ForecastApi;
import com.erp.module.sales.api.forecast.NetForecastDTO;
import com.erp.module.sales.service.quotation.QuotationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 价格表、RFQ 与报价、销售预测、销售退货、销售报表（需求 04-01、02、05、06、08 验收用例） */
class SalesDocsIntegrationTest extends SalesTestSupport {

    static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    ForecastApi forecastApi;
    @Autowired
    QuotationService quotationService;

    // ==================== 价格表 ====================

    private String priceList(String scope, String customerId, String level, boolean taxIncluded, List<List<String>> items) throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "价格表" + uniq());
        p.put("scope", scope);
        p.put("customerId", customerId);
        p.put("customerLevel", level);
        p.put("currency", "CNY");
        p.put("taxIncluded", taxIncluded);
        p.put("effectiveFrom", LocalDate.now().minusDays(1).toString());
        p.put("items", items.stream().map(i -> Map.of("materialId", i.get(0), "uom", "PCS", "minQty", i.get(1), "price", i.get(2))).toList());
        return ok(doPost("/api/sales/price-lists", admin, p)).asText();
    }

    private JsonNode lookup(String customerId, String materialId, String qty) throws Exception {
        return ok(doGet("/api/sales/prices/lookup?customerId=" + customerId + "&materialId=" + materialId + "&qty=" + qty + "&uom=PCS&currency=CNY", admin));
    }

    /** PL-T01 客户价格表阶梯；T02 等级价格表；T03 最近成交价；T04 毛利率低于参数；R01 缺 0 档 */
    @Test
    void priceLists() throws Exception {
        String fg1 = fg("FG1");
        String abc = customer("ABC", false, TERM_NET30, "A");
        String pl = priceList("CUSTOMER", abc, null, false, List.of(List.of(fg1, "0", "10"), List.of(fg1, "1000", "9.5")));
        assertThat(ok(doPost("/api/sales/price-lists/" + pl + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        JsonNode p = lookup(abc, fg1, "1200");
        assertThat(p.at("/price").decimalValue()).isEqualByComparingTo("9.5");
        assertThat(p.at("/sourceLabel").asText()).startsWith("客户价格表 PL-");
        assertThat(lookup(abc, fg1, "999").at("/price").decimalValue()).isEqualByComparingTo("10");
        assertError(doPut("/api/sales/price-lists/" + pl, admin, Map.of("name", "x", "scope", "ALL", "currency", "CNY",
                "effectiveFrom", LocalDate.now().toString())), "已审核的价格表不能修改");

        // T02：B 客户（A 级）没有客户价格表 → 等级价格表
        String b = customer("Bravo", false, TERM_NET30, "A");
        String lv = priceList("LEVEL", null, "A", true, List.of(List.of(fg1, "0", "11")));
        ok(doPost("/api/sales/price-lists/" + lv + "/submit", admin, null));
        p = lookup(b, fg1, "100");
        assertThat(p.at("/price").decimalValue()).isEqualByComparingTo("11");
        assertThat(p.at("/sourceType").asText()).isEqualTo("PRICE_LIST_LEVEL");

        // T03：都没有 → 最近成交价
        String fg2 = fg("FG2");
        String c = customer("Charlie");
        String so = approvedOrder(c, List.of(line(fg2, "10", "10.8", null)));
        p = lookup(c, fg2, "5");
        assertThat(p.at("/price").decimalValue()).isEqualByComparingTo("10.8");
        assertThat(p.at("/sourceLabel").asText()).isEqualTo("最近成交价 " + order(so).at("/docNo").asText());
        // 订单不填单价时自动取价
        JsonNode r = ok(doPost("/api/sales/orders", admin, orderBody(abc, List.of(line(fg1, "1200", null, null)))));
        JsonNode o = order(r.at("/id").asText());
        assertThat(o.at("/lines/0/priceInclTax").decimalValue()).isEqualByComparingTo("10.735");
        assertThat(o.at("/lines/0/priceSource").asText()).startsWith("客户价格表");

        // T04：标准成本 9.00、最低毛利 15%，价格 10.00 → 毛利 10%，低于底价
        ItSalesConfig.COSTS.put(Long.valueOf(fg1), new BigDecimal("9"));
        JsonNode d = ok(doGet("/api/sales/price-lists/" + pl, admin));
        assertThat(d.at("/items/0/marginRate").decimalValue()).isEqualByComparingTo("0.1");
        assertThat(d.at("/items/0/belowFloor").asBoolean()).isTrue();

        // R01：同一物料 + 单位必须有 0 档；R03 日期
        String bad = priceList("ALL", null, null, true, List.of(List.of(fg2, "100", "5")));
        assertError(doPost("/api/sales/price-lists/" + bad + "/submit", admin, null), "物料「" + code(fg2) + "」必须有起始数量为 0 的阶梯");
        ok(doPost("/api/sales/price-lists/" + pl + "/close", admin, Map.of("reason", "年度更新")));
        assertThat(ok(doGet("/api/sales/price-lists/" + pl, admin)).at("/status").asText()).isEqualTo("CLOSED");
        assertThat(lookup(abc, fg1, "1200").at("/sourceType").asText()).isNotEqualTo("PRICE_LIST_CUSTOMER");
    }

    // ==================== RFQ 与报价 ====================

    /** 成品 + BOM（原料 A × 1）+ 工艺路线（1 小时，人工 1 / 制费 0.5 每小时） */
    private String costedProduct() throws Exception {
        String fg1 = fg("RFQ-FG");
        String a = raw("原料A");
        ItSalesConfig.COSTS.put(Long.valueOf(a), new BigDecimal("5"));
        String bom = ok(doPost("/api/engineering/boms", admin, Map.of("materialId", fg1, "baseQty", 1,
                "lines", List.of(Map.of("componentId", a, "qtyPer", 1, "scrapRate", 0))))).at("/id").asText();
        assertThat(ok(doPost("/api/engineering/boms/" + bom + "/submit", admin, null)).asText()).isEqualTo("APPROVED");
        Map<String, Object> w = new HashMap<>();
        w.put("code", "WC" + uniq());
        w.put("name", "SMT 线");
        w.put("deptId", "100");
        w.put("wcType", "LINE");
        w.put("hoursPerShift", 8);
        w.put("shiftCount", 1);
        w.put("efficiencyPct", 1);
        w.put("laborRate", 1);
        w.put("overheadRate", 0.5);
        String wc = ok(doPost("/api/engineering/work-centers", admin, w)).asText();
        Map<String, Object> step = new HashMap<>();
        step.put("seq", 10);
        step.put("operation", "SMT");
        step.put("workCenterId", wc);
        step.put("runSeconds", 3600);
        step.put("isReportPoint", true);
        String rt = ok(doPost("/api/engineering/routings", admin, Map.of("materialId", fg1, "steps", List.of(step)))).at("/id").asText();
        ok(doPost("/api/engineering/routings/" + rt + "/approve", admin, null));
        return fg1;
    }

    private Map<String, Object> rfqBody(String customerId, String materialId) {
        Map<String, Object> l = new HashMap<>();
        l.put("customerPartNo", "CP-" + uniq());
        l.put("description", "FPC 组件");
        l.put("materialId", materialId);
        l.put("qtyBreaks", "5000, 1000");
        l.put("targetPrice", "8");
        return Map.of("customerId", customerId, "replyDueDate", LocalDate.now().plusDays(5).toString(), "lines", List.of(l));
    }

    /** QT-T01 分派 → 评估 → 核算两个阶梯 → 已核算；T02 成本核算数值；生成报价 → 已报价；T05 修订；T07 转订单按阶梯取价、已成交 */
    @Test
    void rfqCostAndQuotation() throws Exception {
        String fg1 = costedProduct();
        String cust = customer("Quote");
        String rfq = ok(doPost("/api/sales/rfqs", admin, rfqBody(cust, null))).at("/id").asText();
        JsonNode d = ok(doGet("/api/sales/rfqs/" + rfq, admin));
        assertThat(d.at("/lines/0/qtyBreaks").asText()).isEqualTo("1000,5000");
        String me = ok(doGet("/api/system/auth/me", admin)).at("/id").asText();
        ok(doPost("/api/sales/rfqs/" + rfq + "/assign", admin, Map.of("engineerId", me, "costEngineerId", me)));
        assertThat(ItSalesConfig.TODOS.stream().filter(t -> t.getBizId().toString().equals(rfq)).count()).isEqualTo(2);
        String lineId = d.at("/lines/0/id").asText();
        ok(doPost("/api/sales/rfqs/" + rfq + "/feasibility", admin, List.of(Map.of("lineId", lineId, "feasibility", "OK", "remark", "可做",
                "materialId", fg1))));
        // T02：材料 5.00、人工 1.00、制费 0.50、管理 5%、利润 15% → 总成本 6.825、建议售价 7.84875
        JsonNode sheet = ok(doPost("/api/sales/rfqs/" + rfq + "/lines/" + lineId + "/cost-sheets/calc", admin, Map.of("qty", 1000)));
        assertThat(sheet.at("/materialCost").decimalValue()).isEqualByComparingTo("5");
        assertThat(sheet.at("/laborCost").decimalValue()).isEqualByComparingTo("1");
        assertThat(sheet.at("/overheadCost").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(sheet.at("/totalCost").decimalValue()).isEqualByComparingTo("6.825");
        assertThat(sheet.at("/suggestedPrice").decimalValue()).isEqualByComparingTo("7.84875");
        assertThat(sheet.at("/materials/0/source").asText()).isEqualTo("STANDARD");
        ok(doPut("/api/sales/rfqs/" + rfq + "/lines/" + lineId + "/cost-sheets", admin, Map.of("qty", 1000)));
        assertThat(ok(doGet("/api/sales/rfqs/" + rfq, admin)).at("/rfqStatus").asText()).isEqualTo("EVALUATING");
        assertError(doPut("/api/sales/rfqs/" + rfq + "/lines/" + lineId + "/cost-sheets", admin, Map.of("qty", 2000)), "核算数量 2000 不在该行的数量阶梯中");
        ok(doPut("/api/sales/rfqs/" + rfq + "/lines/" + lineId + "/cost-sheets", admin, Map.of("qty", 5000, "materialPrices", Map.of(
                ok(doGet("/api/sales/rfqs/" + rfq + "/lines/" + lineId + "/cost-sheets", admin)).at("/0/materials/0/componentId").asText(), "4.5"))));
        assertThat(ok(doGet("/api/sales/rfqs/" + rfq, admin)).at("/rfqStatus").asText()).isEqualTo("COSTED");
        assertThat(ItSalesConfig.MESSAGES.stream().anyMatch(m -> m.getTitle().equals("RFQ 已完成成本核算") && m.getRoute().endsWith(rfq))).isTrue();

        // 生成报价单：两个阶梯，含税（内销 13%）
        String qid = ok(doPost("/api/sales/rfqs/" + rfq + "/to-quotation", admin, null)).asText();
        assertThat(ok(doGet("/api/sales/rfqs/" + rfq, admin)).at("/rfqStatus").asText()).isEqualTo("QUOTED");
        JsonNode q = ok(doGet("/api/sales/quotations/" + qid, admin));
        assertThat(q.at("/lines").size()).isEqualTo(2);
        assertThat(q.at("/lines/0/minQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(q.at("/lines/0/price").decimalValue()).isEqualByComparingTo("8.869088");
        assertThat(q.at("/lines/0/costPrice").decimalValue()).isEqualByComparingTo("6.825");
        assertThat(q.at("/revision").asInt()).isZero();
        assertThat(ok(doPost("/api/sales/quotations/" + qid + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        ok(doPost("/api/sales/quotations/" + qid + "/send", admin, null));

        // T05：修订 R0 → R1
        String r1 = ok(doPost("/api/sales/quotations/" + qid + "/revise", admin, null)).asText();
        assertThat(ok(doGet("/api/sales/quotations/" + qid, admin)).at("/quoteStatus").asText()).isEqualTo("REVISED");
        assertError(doPost("/api/sales/quotations/" + qid + "/to-order", admin, List.of(Map.of("quotationLineId", q.at("/lines/0/id").asText(),
                "qty", 1200))), "该报价已修订，请使用最新版本");
        JsonNode rq = ok(doGet("/api/sales/quotations/" + r1, admin));
        assertThat(rq.at("/revision").asInt()).isEqualTo(1);
        assertThat(rq.at("/docNo").asText()).isEqualTo(q.at("/docNo").asText());
        assertThat(ok(doPost("/api/sales/quotations/" + r1 + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        assertThat(ok(doGet("/api/sales/quotations/" + r1, admin)).at("/revisions").size()).isEqualTo(2);

        // T07：转订单 1200 个 → 取 1000 阶梯价格；报价已成交
        String oid = ok(doPost("/api/sales/quotations/" + r1 + "/to-order", admin, List.of(Map.of("quotationLineId", rq.at("/lines/1/id").asText(),
                "qty", 1200)))).asText();
        JsonNode o = order(oid);
        assertThat(o.at("/quotationId").asText()).isEqualTo(r1);
        assertThat(o.at("/lines/0/priceInclTax").decimalValue()).isEqualByComparingTo(rq.at("/lines/0/price").decimalValue());
        assertThat(ok(doGet("/api/sales/quotations/" + r1, admin)).at("/quoteStatus").asText()).isEqualTo("WON");
    }

    private Map<String, Object> quoteBody(String customerId, String materialId, String price) {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("minQty", 0);
        l.put("price", price);
        return Map.of("customerId", customerId, "lines", List.of(l));
    }

    /** QT-T03 低于底价；T04 过期不能转订单（可修订）；T06 潜在客户不能转订单；R02 阶梯重复；未成交 */
    @Test
    void quotationRules() throws Exception {
        String fg1 = fg("FG");
        ItSalesConfig.COSTS.put(Long.valueOf(fg1), new BigDecimal("10"));
        String cust = customer("QRule");
        JsonNode r = ok(doPost("/api/sales/quotations", admin, quoteBody(cust, fg1, "11.3")));
        String qid = r.at("/id").asText();
        JsonNode q = ok(doGet("/api/sales/quotations/" + qid, admin));
        assertThat(q.at("/belowFloor").asBoolean()).isTrue();
        assertThat(q.at("/minMarginRate").decimalValue()).isEqualByComparingTo("0");
        assertThat(q.at("/validUntil").asText()).isEqualTo(LocalDate.now().plusDays(30).toString());
        ok(doPost("/api/sales/quotations/" + qid + "/submit", admin, null));

        // T04：过期
        jdbc.update("UPDATE sal_quotation SET valid_until = ? WHERE id = ?", LocalDate.now().minusDays(1), Long.valueOf(qid));
        quotationService.expire();
        assertThat(ok(doGet("/api/sales/quotations/" + qid, admin)).at("/quoteStatus").asText()).isEqualTo("EXPIRED");
        assertError(doPost("/api/sales/quotations/" + qid + "/to-order", admin, List.of(Map.of("quotationLineId", q.at("/lines/0/id").asText(),
                "qty", 10))), "报价已过有效期，请修订后再转订单");
        String r1 = ok(doPost("/api/sales/quotations/" + qid + "/revise", admin, null)).asText();
        assertThat(ok(doGet("/api/sales/quotations/" + r1, admin)).at("/quoteStatus").asText()).isEqualTo("DRAFT");

        // T06：潜在客户
        String prospect = ok(doPost("/api/crm/customers", admin, Map.of("name", "潜在客户" + uniq(), "country", "CN"))).at("/id").asText();
        String pq = ok(doPost("/api/sales/quotations", admin, quoteBody(prospect, fg1, "20"))).at("/id").asText();
        ok(doPost("/api/sales/quotations/" + pq + "/submit", admin, null));
        JsonNode pqd = ok(doGet("/api/sales/quotations/" + pq, admin));
        assertError(doPost("/api/sales/quotations/" + pq + "/to-order", admin, List.of(Map.of("quotationLineId", pqd.at("/lines/0/id").asText(),
                "qty", 10))), "客户「" + pqd.at("/customerName").asText() + "」不是正式客户，请先转为正式客户");
        // 未成交
        assertError(doPost("/api/sales/quotations/" + pq + "/lose", admin, Map.of("lostReason", "")), "请求参数不正确：lostReason 请选择未成交原因");
        ok(doPost("/api/sales/quotations/" + pq + "/lose", admin, Map.of("lostReason", "PRICE", "remark", "价格高")));
        assertThat(ok(doGet("/api/sales/quotations/" + pq, admin)).at("/quoteStatus").asText()).isEqualTo("LOST");

        // R02：阶梯重复、没有 0 或 MOQ 档
        Map<String, Object> l1 = Map.of("materialId", fg1, "minQty", 0, "price", 1);
        assertError(doPost("/api/sales/quotations", admin, Map.of("customerId", cust, "lines", List.of(l1, l1))), "物料「" + code(fg1) + "」的阶梯数量重复");
        assertError(doPost("/api/sales/quotations", admin, Map.of("customerId", cust, "lines", List.of(Map.of("materialId", fg1, "minQty", 100, "price", 1)))),
                "物料「" + code(fg1) + "」必须有一档起始数量为 0 或 MOQ");
    }

    // ==================== 预测 ====================

    private String forecast(String materialId, String customerId, Map<String, String> cells) throws Exception {
        List<String> periods = cells.keySet().stream().sorted().toList();
        Map<String, Object> row = new HashMap<>();
        row.put("materialId", materialId);
        row.put("customerId", customerId);
        row.put("cells", cells.entrySet().stream().map(e -> Map.of("period", e.getKey(), "qty", e.getValue())).toList());
        return ok(doPost("/api/sales/forecasts", admin, Map.of("title", "预测" + uniq(), "startPeriod", periods.get(0),
                "endPeriod", periods.get(periods.size() - 1), "rows", List.of(row)))).asText();
    }

    private Map<String, BigDecimal> consumed(String forecastId) throws Exception {
        Map<String, BigDecimal> map = new HashMap<>();
        for (JsonNode c : ok(doGet("/api/sales/forecasts/" + forecastId, admin)).at("/rows/0/cells")) map.put(c.at("/period").asText(), c.at("/consumedQty").decimalValue());
        return map;
    }

    /** FC-T01 订单 1500（要求交期在 M1）冲销 M1 1000 + M2 500；T02 订单变更为 800 → 回退；R02 发布冲突；T03 已过去月份不计入 */
    @Test
    void forecastConsumption() throws Exception {
        String fg1 = fg("FG");
        String cust = customer("Forecast");
        YearMonth m1 = YearMonth.now().plusMonths(1);
        String p1 = m1.format(YM);
        String p2 = m1.plusMonths(1).format(YM);
        String fc = forecast(fg1, null, Map.of(p1, "1000", p2, "1000"));
        ok(doPost("/api/sales/forecasts/" + fc + "/publish", admin, null));
        assertThat(forecastApi.getNetForecast(p1, p2).stream().filter(x -> x.materialId().toString().equals(fg1)).count()).isEqualTo(2);

        String so = approvedOrder(cust, List.of(line(fg1, "1500", "10", m1.atDay(20))));
        Map<String, BigDecimal> c = consumed(fc);
        assertThat(c.get(p1)).isEqualByComparingTo("1000");
        assertThat(c.get(p2)).isEqualByComparingTo("500");
        List<NetForecastDTO> net = forecastApi.getNetForecast(p1, p2).stream().filter(x -> x.materialId().toString().equals(fg1)).toList();
        assertThat(net).hasSize(1);
        assertThat(net.get(0).netQty()).isEqualByComparingTo("500");

        // T02：变更为 800
        String chg = ok(doPost("/api/sales/order-changes?orderId=" + so, admin, null)).asText();
        ok(doPut("/api/sales/order-changes/" + chg, admin, Map.of("changeReason", "CUSTOMER", "reasonRemark", "减量",
                "lines", List.of(Map.of("changeType", "MODIFY", "orderLineId", lineId(so, 0).toString(), "newQty", "800")))));
        ok(doPost("/api/sales/order-changes/" + chg + "/submit", admin, Map.of()));
        c = consumed(fc);
        assertThat(c.get(p1)).isEqualByComparingTo("800");
        assertThat(c.get(p2)).isEqualByComparingTo("0");
        JsonNode detail = ok(doGet("/api/sales/forecasts/" + fc, admin));
        String cellId = detail.at("/rows/0/cells/0/lineId").asText();
        assertThat(ok(doGet("/api/sales/forecasts/" + fc + "/lines/" + cellId + "/consumptions", admin)).at("/0/qty").decimalValue())
                .isEqualByComparingTo("800");

        // R02：同一物料同一月份不能出现在两张已发布预测中
        String fc2 = forecast(fg1, null, Map.of(p1, "10"));
        assertError(doPost("/api/sales/forecasts/" + fc2 + "/publish", admin, null),
                "物料「" + code(fg1) + "」" + p1.substring(0, 4) + "-" + p1.substring(4) + "已在预测「" + detail.at("/docNo").asText() + "」中");
        // 修订：发布新版时关闭旧版并迁移冲销
        String rev = ok(doPost("/api/sales/forecasts/" + fc + "/revise", admin, null)).asText();
        ok(doPost("/api/sales/forecasts/" + rev + "/publish", admin, null));
        assertThat(ok(doGet("/api/sales/forecasts/" + fc, admin)).at("/status").asText()).isEqualTo("CLOSED");
        assertThat(consumed(rev).get(p1)).isEqualByComparingTo("800");
        // R01：月份不能早于当前月
        assertError(doPost("/api/sales/forecasts", admin, Map.of("title", "旧预测", "startPeriod", YearMonth.now().minusMonths(1).format(YM),
                "endPeriod", p1)), "预测月份不能早于当前月");
        // T03：已过去的月份不计入
        jdbc.update("UPDATE sal_forecast_line SET period = ? WHERE forecast_id = ? AND period = ?", YearMonth.now().minusMonths(1).format(YM),
                Long.valueOf(rev), p2);
        assertThat(forecastApi.getNetForecast(YearMonth.now().minusMonths(2).format(YM), p2).stream()
                .filter(x -> x.materialId().toString().equals(fg1)).toList()).allMatch(x -> x.period().compareTo(YearMonth.now().format(YM)) >= 0);
    }

    // ==================== 退货 ====================

    private String returnBody(String customerId, String handling, Long orderLineId, String qty) throws Exception {
        return ok(doPost("/api/sales/returns", admin, Map.of("customerId", customerId, "returnReason", "QUALITY", "handling", handling,
                "lines", List.of(Map.of("orderLineId", orderLineId.toString(), "qty", qty))))).at("/id").asText();
    }

    private void confirmReturnStockIn(String returnId) throws Exception {
        String in = ok(doGet("/api/sales/returns/" + returnId, admin)).at("/stockInId").asText();
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    /** SR-T01 退货 50（退款）审核生成退货仓入库单；T02 入库确认 → 已收货、财务红字应收事件；T03 判定 40/10 → 完成；T04 换货恢复未出货；T05 超过可退数量 */
    @Test
    void salesReturns() throws Exception {
        String cust = customer("Return");
        String fg1 = fg("FG");
        String so = approvedOrder(cust, List.of(line(fg1, "1000", "11.3", null)));
        Long line = lineId(so, 0);
        ship(line, "1000", null);
        assertThat(order(so).at("/status").asText()).isEqualTo("COMPLETED");
        JsonNode shipped = ok(doGet("/api/sales/shipped-lines?customerId=" + cust, admin));
        assertThat(shipped.at("/0/returnableQty").decimalValue()).isEqualByComparingTo("1000");

        String r1 = returnBody(cust, "REFUND", line, "50");
        assertThat(ok(doPost("/api/sales/returns/" + r1 + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        JsonNode rd = ok(doGet("/api/sales/returns/" + r1, admin));
        assertThat(rd.at("/totalAmount").decimalValue()).isEqualByComparingTo("565");
        String in = rd.at("/stockInId").asText();
        JsonNode stockIn = ok(doGet("/api/inventory/stock-ins/" + in, admin));
        assertThat(stockIn.at("/warehouseName").asText()).isEqualTo("退货仓");
        confirmReturnStockIn(r1);
        rd = ok(doGet("/api/sales/returns/" + r1, admin));
        assertThat(rd.at("/lines/0/receivedQty").decimalValue()).isEqualByComparingTo("50");
        assertThat(order(so).at("/lines/0/returnedQty").decimalValue()).isEqualByComparingTo("50");
        assertThat(ItSalesConfig.RETURNS.stream().filter(e -> e.getReturnId().toString().equals(r1)).findFirst().orElseThrow().getTotalAmount())
                .isEqualByComparingTo("565");
        // T03：判定
        assertError(doPost("/api/sales/returns/" + r1 + "/judge", admin, List.of(Map.of("lineId", rd.at("/lines/0/id").asText(), "goodQty", 45,
                "scrapQty", 10))), "第 1 行判定数量合计超过已收货数量 50");
        ok(doPost("/api/sales/returns/" + r1 + "/judge", admin, List.of(Map.of("lineId", rd.at("/lines/0/id").asText(), "goodQty", 40, "scrapQty", 10))));
        assertThat(ok(doGet("/api/sales/returns/" + r1, admin)).at("/status").asText()).isEqualTo("COMPLETED");
        assertError(doPost("/api/sales/returns/" + r1 + "/void", admin, Map.of("reason", "x")), "当前状态【已完成】不允许执行【作废】操作");

        // T04：换货 50 → 订单行已出货减少，未出货 +50，订单回到执行中
        String r2 = returnBody(cust, "REPLACE", line, "50");
        ok(doPost("/api/sales/returns/" + r2 + "/submit", admin, null));
        confirmReturnStockIn(r2);
        JsonNode o = order(so);
        assertThat(o.at("/lines/0/shippedQty").decimalValue()).isEqualByComparingTo("950");
        assertThat(o.at("/lines/0/openQty").decimalValue()).isEqualByComparingTo("50");
        assertThat(o.at("/status").asText()).isEqualTo("IN_PROGRESS");

        // T05：可退 = 950 − 50（退款） = 900
        assertError(doPost("/api/sales/returns", admin, Map.of("customerId", cust, "returnReason", "QUALITY", "handling", "REFUND",
                "lines", List.of(Map.of("orderLineId", line.toString(), "qty", "901")))), "第 1 行退货数量超过可退数量 900");
        // R06：入库前可作废
        String r3 = returnBody(cust, "REFUND", line, "10");
        ok(doPost("/api/sales/returns/" + r3 + "/submit", admin, null));
        ok(doPost("/api/sales/returns/" + r3 + "/void", admin, Map.of("reason", "客户撤回")));
        assertThat(ok(doGet("/api/sales/returns/" + r3, admin)).at("/status").asText()).isEqualTo("VOIDED");
    }

    // ==================== 报表 ====================

    /** RPT-T01 未交订单 3 行、距交期、可用库存；T02 业务员只看自己的数据；T03 报价成功率；订单执行跟踪 */
    @Test
    void reports() throws Exception {
        String cust = customer("Report");
        String fg1 = fg("FG");
        stock(fg1, "120");
        LocalDate today = LocalDate.now();
        String so = approvedOrder(cust, List.of(line(fg1, "100", "10", today.plusDays(2)), line(fg1, "50", "10", today.plusDays(10)),
                line(fg("FG2"), "30", "10", today)));
        jdbc.update("UPDATE sal_order_line SET required_date = ? WHERE order_id = ? AND line_no = 3", today.minusDays(1), Long.valueOf(so));
        JsonNode rows = ok(doGet("/api/sales/reports/open-orders?pageNo=1&pageSize=20&customerId=" + cust, admin));
        assertThat(rows.at("/total").asInt()).isEqualTo(3);
        assertThat(rows.at("/list/0/daysToDue").asInt()).isEqualTo(-1);
        assertThat(rows.at("/list/1/daysToDue").asInt()).isEqualTo(2);
        assertThat(rows.at("/list/1/availableQty").decimalValue()).isEqualByComparingTo("120");

        Long line = lineId(so, 0);
        writebackApi.onNoticeChanged(line, new BigDecimal("40"), 21L, "SN-21");
        ship(line, "40", today);
        JsonNode trace = ok(doGet("/api/sales/reports/order-trace/" + line, admin));
        assertThat(trace.at("/steps").findValuesAsText("step")).contains("ORDER", "APPROVE", "NOTICE", "SHIP");

        JsonNode perf = ok(doGet("/api/sales/reports/performance?customerId=" + cust, admin));
        assertThat(perf.at("/rows/0/orderAmount").decimalValue()).isEqualByComparingTo("1800");
        assertThat(perf.at("/rows/0/shipAmount").decimalValue()).isEqualByComparingTo("400");
        assertThat(perf.at("/rows/0/newCustomers").asInt()).isEqualTo(1);

        // T02：仅本人的业务员看不到 admin 的订单
        String role = ok(doPost("/api/system/roles", admin, Map.of("code", "S" + uniq(), "name", "业务员" + uniq(), "dataScope", "SELF", "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + role + "/permissions", admin, Map.of("permissions", List.of("sales:report:query", "sales:order:query"))));
        String username = "sal" + uniq();
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("realName", "业务员" + username);
        u.put("deptId", "100");
        u.put("roleIds", List.of(role));
        u.put("password", "Passw0rd!2026");
        u.put("mustChangePassword", false);
        ok(doPost("/api/system/users", admin, u));
        String token = login(username, "Passw0rd!2026");
        assertThat(ok(doGet("/api/sales/reports/performance?customerId=" + cust, token)).at("/rows").size()).isZero();
        assertThat(ok(doGet("/api/sales/orders?pageNo=1&pageSize=10&customerId=" + cust, token)).at("/total").asInt()).isZero();

        // T03：报价 2 张，成交 1 张 → 50%
        String q1 = ok(doPost("/api/sales/quotations", admin, quoteBody(cust, fg1, "10"))).at("/id").asText();
        ok(doPost("/api/sales/quotations/" + q1 + "/submit", admin, null));
        JsonNode q1d = ok(doGet("/api/sales/quotations/" + q1, admin));
        ok(doPost("/api/sales/quotations/" + q1 + "/to-order", admin, List.of(Map.of("quotationLineId", q1d.at("/lines/0/id").asText(), "qty", 5))));
        String q2 = ok(doPost("/api/sales/quotations", admin, quoteBody(cust, fg1, "10"))).at("/id").asText();
        ok(doPost("/api/sales/quotations/" + q2 + "/submit", admin, null));
        ok(doPost("/api/sales/quotations/" + q2 + "/lose", admin, Map.of("lostReason", "PRICE")));
        JsonNode qs = ok(doGet("/api/sales/reports/quotation-success?groupBy=CUSTOMER&customerId=" + cust, admin));
        assertThat(qs.at("/quoteCount").asInt()).isEqualTo(2);
        assertThat(qs.at("/successRate").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(qs.at("/lostReasons/0/label").asText()).isEqualTo("价格高");
        JsonNode rank = ok(doGet("/api/sales/reports/customer-ranking?customerId=" + cust, admin));
        assertThat(rank.at("/0/abcClass").asText()).isEqualTo("A");
    }
}
