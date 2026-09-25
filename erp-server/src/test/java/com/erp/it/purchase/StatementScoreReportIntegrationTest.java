package com.erp.it.purchase;

import com.erp.module.purchase.api.receipt.PurchaseReceiptApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 供应商对账（07-09）、供应商评估（07-10）、采购报表（07-11）验收用例 */
class StatementScoreReportIntegrationTest extends PurchaseTestSupport {

    @Autowired
    PurchaseReceiptApi receiptApi;

    // ==================== 对账 ====================

    /** PUR-ST-T01～T04：货款 3 行 + 退款退货 1 行（负数）、检验未完成不出现、扣款、确认需签字对账单 */
    @Test
    void statement() throws Exception {
        String a = elec("ST-A");
        String b = aux("ST-B");
        String c = aux("ST-C");
        String v = qualifiedSupplier("ST供应商", a, b, c);
        String po = approvedOrder(v, List.of(orderLine(a, "150", "1.13", null), orderLine(b, "10", "2.26", null), orderLine(c, "10", "3.39", null)));
        JsonNode o = order(po);
        String la = o.at("/lines/0/id").asText();
        String lb = o.at("/lines/1/id").asText();
        String lc = o.at("/lines/2/id").asText();

        // A 100 检验合格；B、C 免检
        String r1 = approvedReceipt(v, List.of(receiptLine(la, "100")));
        confirmStockIns(r1);
        receiptApi.applyInspection(receipt(r1).at("/lines/0/id").asLong(), "IQC-" + uniq(), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
        String r2 = approvedReceipt(v, List.of(receiptLine(lb, "10")));
        confirmStockIns(r2);
        String r3 = approvedReceipt(v, List.of(receiptLine(lc, "10")));
        confirmStockIns(r3);
        // T02：A 另一批 50 待检
        String r4 = approvedReceipt(v, List.of(receiptLine(la, "50")));
        confirmStockIns(r4);
        // 退款退货 B 2 个
        Map<String, Object> ret = new HashMap<>();
        ret.put("supplierId", v);
        ret.put("returnReason", "STOCK_DEFECT");
        ret.put("handling", "REFUND");
        ret.put("warehouseId", W_AUX);
        ret.put("lines", List.of(Map.of("receiptLineId", receipt(r2).at("/lines/0/id").asText(), "qty", "2")));
        String rt = ok(doPost("/api/purchase/returns", admin, ret)).at("/id").asText();
        ok(doPost("/api/purchase/returns/" + rt + "/submit", admin, null));
        String out = ok(doGet("/api/purchase/returns/" + rt, admin)).at("/stockOutId").asText();
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of()));

        LocalDate from = YearMonth.now().atDay(1);
        LocalDate to = LocalDate.now();
        JsonNode cands = ok(doGet("/api/purchase/statements/candidates?supplierId=" + v + "&from=" + from + "&to=" + to, admin));
        assertThat(cands.size()).isEqualTo(4);
        List<Map<String, Object>> lines = new ArrayList<>();
        int goods = 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode n : cands) {
            if ("GOODS".equals(n.at("/lineType").asText())) goods++;
            else {
                assertThat(n.at("/lineType").asText()).isEqualTo("RETURN");
                assertThat(n.at("/qty").decimalValue()).isEqualByComparingTo("-2");
                assertThat(n.at("/totalAmount").decimalValue()).isEqualByComparingTo("-4.52");
            }
            assertThat(n.at("/sourceNo").asText()).isNotEqualTo(receipt(r4).at("/docNo").asText());
            sum = sum.add(n.at("/totalAmount").decimalValue());
            Map<String, Object> l = new HashMap<>();
            l.put("lineType", n.at("/lineType").asText());
            l.put("sourceType", n.at("/sourceType").asText());
            l.put("sourceLineId", n.at("/sourceLineId").asText());
            l.put("qty", n.at("/qty").decimalValue());
            lines.add(l);
        }
        assertThat(goods).isEqualTo(3);
        // 113 + 22.6 + 33.9 − 4.52
        assertThat(sum).isEqualByComparingTo("164.98");

        // T03：扣款 −500
        Map<String, Object> adjust = new HashMap<>();
        adjust.put("lineType", "ADJUST");
        adjust.put("totalAmount", "-500");
        adjust.put("taxRate", "0.13");
        adjust.put("remark", "来料不良");
        lines.add(adjust);
        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", v);
        body.put("periodFrom", from.toString());
        body.put("periodTo", to.toString());
        body.put("lines", lines);
        String st = ok(doPost("/api/purchase/statements", admin, body)).asText();
        JsonNode d = ok(doGet("/api/purchase/statements/" + st, admin));
        assertThat(d.at("/goodsAmount").decimalValue()).isEqualByComparingTo("169.50");
        assertThat(d.at("/returnAmount").decimalValue()).isEqualByComparingTo("-4.52");
        assertThat(d.at("/adjustAmount").decimalValue()).isEqualByComparingTo("-500");
        assertThat(d.at("/totalAmount").decimalValue()).isEqualByComparingTo("-335.02");
        // 已加入对账单的来源行不再出现在候选中
        assertThat(ok(doGet("/api/purchase/statements/candidates?supplierId=" + v + "&from=" + from + "&to=" + to, admin)).size()).isZero();

        // T04：确认需上传签字对账单
        assertThat(ok(doPost("/api/purchase/statements/" + st + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        assertError(doPost("/api/purchase/statements/" + st + "/confirm", admin, Map.of("confirmer", "李会计")), "请上传供应商确认的对账单");
        ok(doPost("/api/purchase/statements/" + st + "/confirm", admin, Map.of("confirmer", "李会计", "fileIds", List.of(uploadPdf()))));
        assertThat(ok(doGet("/api/purchase/statements/" + st, admin)).at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(order(po).at("/lines/0/statementQty").decimalValue()).isEqualByComparingTo("100");
    }

    // ==================== 评估 ====================

    private JsonNode score(String supplierId, String period) throws Exception {
        JsonNode list = ok(doGet("/api/purchase/scores?period=" + period + "&supplierId=" + supplierId + "&pageNo=1&pageSize=10", admin)).at("/list");
        assertThat(list.size()).isEqualTo(1);
        return list.get(0);
    }

    /** PUR-SC-T01～T03：10 批合格 9 → 质量 90；应到 20 行准时 18 → 交期 90；价格 80、服务 100 → 89 分 B 级，发布后供应商等级 B */
    @Test
    void scoreGradeB() throws Exception {
        String a = elec("SC-A");
        String v = qualifiedSupplier("SC供应商", a);
        List<Map<String, Object>> ols = new ArrayList<>();
        for (int i = 0; i < 20; i++) ols.add(orderLine(a, "1", "1", LocalDate.now()));
        String po = approvedOrder(v, ols);
        JsonNode o = order(po);
        List<Map<String, Object>> rls = new ArrayList<>();
        for (int i = 0; i < 18; i++) rls.add(receiptLine(o.at("/lines/" + i + "/id").asText(), "1"));
        String rc = approvedReceipt(v, rls);
        JsonNode r = receipt(rc);
        for (int i = 0; i < 10; i++) {
            boolean pass = i < 9;
            receiptApi.applyInspection(r.at("/lines/" + i + "/id").asLong(), "IQC-" + uniq(), pass ? BigDecimal.ONE : BigDecimal.ZERO,
                    BigDecimal.ZERO, pass ? BigDecimal.ZERO : BigDecimal.ONE);
        }
        String period = YearMonth.now().toString();
        ok(doPost("/api/purchase/scores/calculate", admin, Map.of("period", period)));
        JsonNode s = score(v, period);
        assertThat(s.at("/lotCount").asInt()).isEqualTo(10);
        assertThat(s.at("/qualityScore").decimalValue()).isEqualByComparingTo("90");
        assertThat(s.at("/dueLineCount").asInt()).isEqualTo(20);
        assertThat(s.at("/deliveryScore").decimalValue()).isEqualByComparingTo("90");
        assertThat(s.at("/status").asText()).isEqualTo("CALCULATED");
        String id = s.at("/id").asText();
        assertError(doPost("/api/purchase/scores/publish", admin, Map.of("ids", List.of(id))), "请先完成价格和服务评分");

        JsonNode saved = ok(doPut("/api/purchase/scores/" + id, admin, Map.of("priceScore", "80", "serviceScore", "100")));
        assertThat(saved.at("/totalScore").decimalValue()).isEqualByComparingTo("89");
        assertThat(saved.at("/grade").asText()).isEqualTo("B");
        assertThat(saved.at("/status").asText()).isEqualTo("SCORED");
        assertThat(ok(doPost("/api/purchase/scores/publish", admin, Map.of("ids", List.of(id)))).asInt()).isEqualTo(1);
        assertThat(ok(doGet("/api/purchase/suppliers/" + v, admin)).at("/level").asText()).isEqualTo("B");
        assertThat(score(v, period).at("/status").asText()).isEqualTo("PUBLISHED");
    }

    /** PUR-SC-T04：总分低于 70 → D 级，发布后供应商等级 D */
    @Test
    void scoreGradeD() throws Exception {
        String a = aux("SC-D");
        String v = qualifiedSupplier("SC-D供应商", a);
        approvedOrder(v, List.of(orderLine(a, "1", "1", LocalDate.now())));
        String period = YearMonth.now().toString();
        ok(doPost("/api/purchase/scores/calculate", admin, Map.of("period", period)));
        JsonNode s = score(v, period);
        assertThat(s.at("/deliveryScore").decimalValue()).isEqualByComparingTo("0");
        assertThat(s.at("/qualityScore").isNumber()).isFalse();
        String id = s.at("/id").asText();
        // (0×30 + 100×20 + 100×10) ÷ 60 = 50
        JsonNode saved = ok(doPut("/api/purchase/scores/" + id, admin, Map.of("priceScore", "100", "serviceScore", "100")));
        assertThat(saved.at("/totalScore").decimalValue()).isEqualByComparingTo("50");
        assertThat(saved.at("/grade").asText()).isEqualTo("D");
        ok(doPost("/api/purchase/scores/publish", admin, Map.of("ids", List.of(id))));
        assertThat(ok(doGet("/api/purchase/suppliers/" + v, admin)).at("/level").asText()).isEqualTo("D");
    }

    // ==================== 报表 ====================

    /** PUR-RPT-T01、T02：确认交期 4 天前未到货 → 逾期 4 天；跟催新承诺日期后逾期消失、订单日志有记录 */
    @Test
    void deliveryTrackingAndFollowUp() throws Exception {
        String a = aux("RPT-A");
        String v = qualifiedSupplier("RPT供应商", a);
        String po = approvedOrder(v, List.of(orderLine(a, "10", "1", LocalDate.now().minusDays(10))));
        String line = order(po).at("/lines/0/id").asText();
        ok(doPut("/api/purchase/orders/" + po + "/confirmed-dates", admin,
                Map.of("lines", List.of(Map.of("lineId", line, "confirmedDate", LocalDate.now().minusDays(4).toString())))));
        JsonNode rows = ok(doGet("/api/purchase/reports/delivery-tracking?supplierId=" + v + "&pageNo=1&pageSize=10", admin)).at("/list");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.at("/0/overdueDays").asLong()).isEqualTo(4);

        LocalDate promise = LocalDate.now().plusDays(4);
        ok(doPost("/api/purchase/reports/delivery-tracking/" + line + "/follow-up", admin, Map.of("content", "电话催货", "newDate", promise.toString())));
        assertThat(order(po).at("/lines/0/confirmedDate").asText()).isEqualTo(promise.toString());
        assertThat(ok(doGet("/api/purchase/reports/delivery-tracking?supplierId=" + v + "&pageNo=1&pageSize=10", admin)).at("/list").size()).isZero();
        JsonNode all = ok(doGet("/api/purchase/reports/delivery-tracking?supplierId=" + v + "&overdueOnly=false&pageNo=1&pageSize=10", admin)).at("/list");
        assertThat(all.at("/0/overdueDays").asLong()).isZero();
        assertThat(all.at("/0/lastFollowUp").asText()).isEqualTo("电话催货");
        assertThat(ok(doGet("/api/system/doc-logs?bizType=PUR_ORDER&bizId=" + po, admin)).toString()).contains("电话催货");
    }
}
