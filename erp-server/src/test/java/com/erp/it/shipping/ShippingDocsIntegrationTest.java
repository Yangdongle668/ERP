package com.erp.it.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 出货单证、物流跟踪、货代、出货报表（需求 11-04～11-06 验收用例） */
class ShippingDocsIntegrationTest extends ShippingTestSupport {

    /** 走完一张出货：订单（单价 10）→ 通知 → 按推荐拣货 → 按每箱数量装箱 → 出货单 → 确认出库，返回出货单 ID */
    private String exportShipment(String customerId, String materialId, String qty, String perCarton) throws Exception {
        String order = approvedOrder(customerId, List.of(orderLine(materialId, qty, "10", null)));
        Long ol = orderLineId(order, 0);
        String n = notice(customerId, ol, qty);
        submitNotice(n);
        pickAsSuggested(n);
        ok(batchPack(n, noticeDetail(n).at("/lines/0/id").asText(), null, perCarton, null));
        ok(doPost("/api/shipping/notices/" + n + "/pack-complete", admin, Map.of()));
        String s = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of())).asText();
        submitAndConfirm(s);
        return s;
    }

    /** SHP-DOC-T01/T02/T04、R03、R04；报关资料按 HS 编码合并 */
    @Test
    void packingListInvoiceCustoms() throws Exception {
        String m = fg("外销成品", Map.of());
        stock(m, "1250", null);
        String c = customer("Export Buyer", true);
        String s = exportShipment(c, m, "1250", "250");
        assertThat(shipment(s).at("/currency").asText()).isEqualTo("USD");

        // SHP-DOC-T01：箱号区间、每箱数量、毛净重、CBM 与装箱一致，合计正确
        String pl = ok(doPost("/api/shipping/shipments/" + s + "/packing-list", admin, null)).asText();
        assertError(doPost("/api/shipping/shipments/" + s + "/packing-list", admin, null),
                "出货单已生成Packing List：" + ok(doGet("/api/shipping/packing-lists/" + pl, admin)).at("/plNo").asText());
        JsonNode pd = ok(doGet("/api/shipping/packing-lists/" + pl, admin));
        assertThat(pd.at("/lines").size()).isEqualTo(1);
        assertThat(pd.at("/lines/0/cartonRange").asText()).isEqualTo("1-5");
        assertThat(pd.at("/lines/0/qtyPerCarton").decimalValue()).isEqualByComparingTo("250");
        assertThat(pd.at("/lines/0/cartons").asInt()).isEqualTo(5);
        assertThat(pd.at("/lines/0/cbm").decimalValue()).isEqualByComparingTo("0.48");
        assertThat(pd.at("/totals/cartons").asInt()).isEqualTo(5);
        assertThat(pd.at("/totals/qty").decimalValue()).isEqualByComparingTo("1250");
        assertThat(pd.at("/consignee").asText()).contains("1 Main St");

        // SHP-DOC-T02：金额 12,500.00 USD → 英文大写
        String inv = ok(doPost("/api/shipping/shipments/" + s + "/invoice", admin, null)).asText();
        JsonNode id = ok(doGet("/api/shipping/invoices/" + inv, admin));
        assertThat(id.at("/totalAmount").decimalValue()).isEqualByComparingTo("12500");
        assertThat(id.at("/amountInWords").asText()).isEqualTo("SAY US DOLLARS TWELVE THOUSAND FIVE HUNDRED ONLY");
        assertThat(id.at("/billTo").asText()).contains("9 Bill Rd");
        assertThat(id.at("/lines/0/origin").asText()).isEqualTo("CHINA");

        // SHP-DOC-T04：物料无 HS 编码 → 保存提示；R04 单号唯一
        Map<String, Object> save = new HashMap<>();
        save.put("invoiceNo", id.at("/invoiceNo").asText());
        save.put("invoiceDate", LocalDate.now().toString());
        save.put("billTo", id.at("/billTo").asText());
        save.put("vesselFlight", "COSCO V.123");
        save.put("lines", List.of(Map.of("id", id.at("/lines/0/id").asText(), "description", "PCBA Board", "origin", "CHINA")));
        assertError(doPut("/api/shipping/invoices/" + inv, admin, save), "第 1 行请填写 HS 编码");
        save.put("lines", List.of(Map.of("id", id.at("/lines/0/id").asText(), "description", "PCBA Board", "hsCode", "8537109090", "origin", "CHINA")));
        ok(doPut("/api/shipping/invoices/" + inv, admin, save));
        id = ok(doGet("/api/shipping/invoices/" + inv, admin));
        assertThat(id.at("/lines/0/hsCode").asText()).isEqualTo("8537109090");
        assertThat(id.at("/vesselFlight").asText()).isEqualTo("COSCO V.123");
        // 金额不可修改（仍与出货单一致）
        assertThat(id.at("/lines/0/amount").decimalValue()).isEqualByComparingTo("12500");
        JsonNode print = ok(doGet("/api/shipping/invoices/" + inv + "/print-data", admin));
        assertThat(print.at("/amountInWords").asText()).startsWith("SAY US DOLLARS");

        Map<String, Object> plSave = new HashMap<>();
        plSave.put("plDate", LocalDate.now().toString());
        String s2 = exportShipment(c, m("第二外销成品"), "10", "10");
        String pl2 = ok(doPost("/api/shipping/shipments/" + s2 + "/packing-list", admin, null)).asText();
        String pl1No = pd.at("/plNo").asText();
        plSave.put("plNo", pl1No);
        assertError(doPut("/api/shipping/packing-lists/" + pl2, admin, plSave), "单证号「" + pl1No + "」已存在");

        // 报关资料：同 HS 编码合并
        String cd = ok(doPost("/api/shipping/shipments/" + s + "/customs", admin, null)).asText();
        JsonNode cdd = ok(doGet("/api/shipping/customs/" + cd, admin));
        assertThat(cdd.at("/items").size()).isEqualTo(1);
        assertThat(cdd.at("/items/0/qty").decimalValue()).isEqualByComparingTo("1250");
        assertThat(cdd.at("/destinationCountry").asText()).isEqualTo("US");
        ok(doPut("/api/shipping/customs/" + cd, admin, Map.of("customsNo", "530120260001", "declareDate", LocalDate.now().toString(),
                "items", List.of(Map.of("id", cdd.at("/items/0/id").asText(), "hsCode", "8537109090", "declareName", "控制板")))));
        assertThat(ok(doGet("/api/shipping/customs/" + cd, admin)).at("/customsNo").asText()).isEqualTo("530120260001");

        // SHP-DOC-R03：出货单反确认 → 单证失效
        ok(doPost("/api/inventory/stock-outs/" + shipment(s).at("/stockOutId").asText() + "/unconfirm", admin, Map.of("reason", "测试反确认")));
        assertThat(ok(doGet("/api/shipping/packing-lists/" + pl, admin)).at("/invalid").asBoolean()).isTrue();
        assertThat(ok(doGet("/api/shipping/invoices/" + inv, admin)).at("/invalid").asBoolean()).isTrue();
        assertThat(ok(doGet("/api/shipping/customs/" + cd, admin)).at("/invalid").asBoolean()).isTrue();
        assertThat(ok(doGet("/api/shipping/invoices?no=" + id.at("/invoiceNo").asText(), admin)).at("/list/0/invalid").asBoolean()).isTrue();
    }

    /** 多张出货单合并一张 Packing List：箱号接续、行带出货单号、合计相加；不同客户 / 已有 PL 的不能合并；任一出货单反确认使之失效 */
    @Test
    void mergePackingList() throws Exception {
        String m1 = fg("合并成品甲", Map.of());
        String m2 = fg("合并成品乙", Map.of());
        stock(m1, "500", null);
        stock(m2, "300", null);
        String c = customer("Merge Buyer", true);
        String s1 = exportShipment(c, m1, "500", "100");   // 5 箱
        String s2 = exportShipment(c, m2, "300", "100");   // 3 箱
        String s1No = shipment(s1).at("/docNo").asText();
        String s2No = shipment(s2).at("/docNo").asText();

        assertError(doPost("/api/shipping/packing-lists/merge", admin, Map.of("shipmentIds", List.of(s1))), "请选择 2 张以上的出货单合并");
        // 不同客户
        String other = customer("Other Buyer", true);
        String m3 = fg("合并成品丙", Map.of());
        stock(m3, "10", null);
        String s3 = exportShipment(other, m3, "10", "10");
        assertError(doPost("/api/shipping/packing-lists/merge", admin, Map.of("shipmentIds", List.of(s1, s3))),
                "出货单 " + s1No + " 与 " + shipment(s3).at("/docNo").asText() + " 的客户不同，不能合并");

        String pl = ok(doPost("/api/shipping/packing-lists/merge", admin, Map.of("shipmentIds", List.of(s1, s2)))).asText();
        JsonNode d = ok(doGet("/api/shipping/packing-lists/" + pl, admin));
        assertThat(d.at("/lines").size()).isEqualTo(2);
        assertThat(d.at("/lines/0/cartonRange").asText()).isEqualTo("1-5");
        assertThat(d.at("/lines/0/shipmentNo").asText()).isEqualTo(s1No);
        assertThat(d.at("/lines/1/cartonRange").asText()).isEqualTo("6-8");
        assertThat(d.at("/lines/1/shipmentNo").asText()).isEqualTo(s2No);
        assertThat(d.at("/totals/cartons").asInt()).isEqualTo(8);
        assertThat(d.at("/totals/qty").decimalValue()).isEqualByComparingTo("800");
        assertThat(d.at("/shippingMarks").asText()).contains("C/NO. 1-8");
        assertThat(d.at("/shipmentNos").size()).isEqualTo(2);
        // 出货单详情显示这张 PL；已在 PL 中的出货单不能再单独生成或合并
        assertThat(shipment(s2).at("/packingList/id").asText()).isEqualTo(pl);
        assertError(doPost("/api/shipping/shipments/" + s2 + "/packing-list", admin, null), "出货单已生成Packing List：" + d.at("/plNo").asText());
        assertError(doPost("/api/shipping/packing-lists/merge", admin, Map.of("shipmentIds", List.of(s1, s2))), "出货单已生成Packing List：" + d.at("/plNo").asText());
        assertThat(ok(doGet("/api/shipping/packing-lists?no=" + d.at("/plNo").asText(), admin)).at("/list/0/shipmentNo").asText()).contains(s1No).contains(s2No);

        // 合并的任一出货单反确认 → PL 失效
        ok(doPost("/api/inventory/stock-outs/" + shipment(s2).at("/stockOutId").asText() + "/unconfirm", admin, Map.of("reason", "测试反确认")));
        assertThat(ok(doGet("/api/shipping/packing-lists/" + pl, admin)).at("/invalid").asBoolean()).isTrue();
    }

    private String m(String name) throws Exception {
        String id = fg(name, Map.of());
        stock(id, "10", null);
        return id;
    }

    /** SHP-LOG-T01/T02、R01、SH-R07；货代；报表 SHP-RPT-T01/T02 */
    @Test
    void logisticsForwarderReports() throws Exception {
        // 货代：编码唯一
        String code = "FW" + uniq();
        String fw = ok(doPost("/api/shipping/forwarders", admin, Map.of("code", code, "name", "顺达货代", "services", List.of("SEA", "AIR")))).asText();
        assertError(doPost("/api/shipping/forwarders", admin, Map.of("code", code, "name", "重复")), "编码「" + code + "」已存在");
        assertThat(ok(doGet("/api/shipping/forwarders/options", admin)).findValuesAsText("id")).contains(fw);

        String m = fg("物流成品", Map.of());
        stock(m, "100", null);
        String c = customer("物流客户", false);
        String order = approvedOrder(c, List.of(orderLine(m, "100", "3", LocalDate.now().plusDays(10))));
        Long ol = orderLineId(order, 0);
        String n = notice(c, ol, "100");
        submitNotice(n);
        pickAsSuggested(n);
        ok(batchPack(n, noticeDetail(n).at("/lines/0/id").asText(), null, "50", null));
        ok(doPost("/api/shipping/notices/" + n + "/pack-complete", admin, Map.of()));
        String s = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of())).asText();
        ok(doPut("/api/shipping/shipments/" + s, admin, Map.of("shipDate", LocalDate.now().toString(), "forwarderId", fw, "containerNo", "TGHU1234567")));
        // 未出货不能登记物流
        assertError(doPost("/api/shipping/shipments/" + s + "/logistics-events", admin, Map.of("logisticsStatus", "BOOKED",
                "occurredAt", LocalDateTime.now().withNano(0).toString().replace('T', ' '))), "出货单尚未出货");
        submitAndConfirm(s);

        // SHP-SH-R07：提单日期不能早于出货日期
        assertError(doPut("/api/shipping/shipments/" + s + "/logistics", admin, Map.of("blNo", "BL001", "blDate", LocalDate.now().minusDays(1).toString())),
                "提单日期不能早于出货日期");
        ok(doPut("/api/shipping/shipments/" + s + "/logistics", admin, Map.of("blNo", "BL001", "blDate", LocalDate.now().toString(),
                "etd", LocalDate.now().toString(), "eta", LocalDate.now().plusDays(3).toString())));
        // 内销登记提单不自动完成
        assertThat(shipment(s).at("/shipmentStatus").asText()).isEqualTo("SHIPPED");

        // SHP-LOG-T01：更新为已离港；R01 不可倒退（倒退需说明）
        String now = LocalDateTime.now().withNano(0).toString().replace('T', ' ');
        ok(doPost("/api/shipping/shipments/" + s + "/logistics-events", admin, Map.of("logisticsStatus", "DEPARTED", "occurredAt", now, "location", "深圳")));
        JsonNode lg = ok(doGet("/api/shipping/logistics?customerId=" + c, admin)).at("/list/0");
        assertThat(lg.at("/logisticsStatus").asText()).isEqualTo("DEPARTED");
        assertThat(lg.at("/forwarderName").asText()).isEqualTo("顺达货代");
        assertError(doPost("/api/shipping/shipments/" + s + "/logistics-events", admin, Map.of("logisticsStatus", "BOOKED", "occurredAt", now)),
                "物流状态不能倒退，如需更正请填写说明");
        ok(doPost("/api/shipping/shipments/" + s + "/logistics-events", admin, Map.of("logisticsStatus", "BOOKED", "occurredAt", now, "remark", "录错更正")));
        // SHP-LOG-T02：更新为已签收 → 出货单已完成
        ok(doPost("/api/shipping/shipments/" + s + "/logistics-events", admin, Map.of("logisticsStatus", "DELIVERED", "occurredAt", now)));
        JsonNode sd = shipment(s);
        assertThat(sd.at("/shipmentStatus").asText()).isEqualTo("COMPLETED");
        assertThat(sd.at("/events").size()).isEqualTo(3);
        assertThat(sd.at("/signedAt").isNull()).isFalse();

        // SHP-RPT-T02：承诺交期 5 天后未通知 → 待出货清单“未通知”
        String m2 = fg("待出货成品", Map.of());
        String order2 = approvedOrder(c, List.of(orderLine(m2, "40", "3", LocalDate.now().plusDays(5))));
        JsonNode pending = ok(doGet("/api/shipping/reports/pending?customerId=" + c, admin));
        boolean found = false;
        for (JsonNode r : pending) {
            if (r.at("/orderId").asText().equals(order2)) {
                found = true;
                assertThat(blank(r.at("/noticeId"))).isTrue();
                assertThat(r.at("/qty").decimalValue()).isEqualByComparingTo("40");
            }
        }
        assertThat(found).isTrue();

        // 出货明细
        JsonNode det = ok(doGet("/api/shipping/reports/details?customerId=" + c, admin));
        assertThat(det.at("/rows").size()).isEqualTo(1);
        assertThat(det.at("/rows/0/qty").decimalValue()).isEqualByComparingTo("100");
        assertThat(det.at("/summary/0/amountBase").decimalValue()).isEqualByComparingTo("300");
        // SHP-RPT-T01（准时率）：交期 10 天后，今天出货 → 按期
        JsonNode ot = ok(doGet("/api/shipping/reports/on-time?customerId=" + c, admin));
        assertThat(ot.at("/total").asInt()).isEqualTo(1);
        assertThat(ot.at("/rate").decimalValue()).isEqualByComparingTo("100");
        JsonNode st = ok(doGet("/api/shipping/reports/export-stats?customerId=" + c, admin));
        assertThat(st.get(0).at("/country").asText()).isEqualTo("CN");
    }
}
