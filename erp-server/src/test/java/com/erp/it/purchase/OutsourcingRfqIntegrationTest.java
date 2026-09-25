package com.erp.it.purchase;

import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.purchase.api.receipt.PurchaseReceiptApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 委外加工（07-07）与询价比价（07-04）验收用例 */
class OutsourcingRfqIntegrationTest extends PurchaseTestSupport {

    @Autowired
    PurchaseReceiptApi receiptApi;

    // ==================== 委外 ====================

    /** 期初备料：直接生成并确认入库 */
    private void stock(String materialId, String warehouseId, String qty) throws Exception {
        Long in = docApi.createStockIn(new StockInRequest(StockInType.PURCHASE_IN, source("PUR_IT"), Long.valueOf(warehouseId), null, null, null,
                List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), null, null, null, null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    private String approvedBom(String parentId, Map<String, Object> qtyPer) throws Exception {
        List<Map<String, Object>> lines = new ArrayList<>();
        qtyPer.forEach((k, v) -> lines.add(Map.of("componentId", k, "qtyPer", v)));
        Map<String, Object> b = new HashMap<>();
        b.put("materialId", parentId);
        b.put("baseQty", 1);
        b.put("lines", lines);
        String id = ok(doPost("/api/engineering/boms", admin, b)).at("/id").asText();
        ok(doPost("/api/engineering/boms/" + id + "/submit", admin, null));
        return id;
    }

    private void confirmOuts(JsonNode ids) throws Exception {
        for (JsonNode id : ids.at("/stockDocIds")) ok(doPost("/api/inventory/stock-outs/" + id.asText() + "/confirm", admin, Map.of()));
    }

    private void confirmIns(JsonNode ids) throws Exception {
        for (JsonNode id : ids.at("/stockDocIds")) ok(doPost("/api/inventory/stock-ins/" + id.asText() + "/confirm", admin, Map.of()));
    }

    private JsonNode os(String id) throws Exception {
        return ok(doGet("/api/purchase/outsourcings/" + id, admin));
    }

    private Map<String, Object> qtyLine(String materialLineId, String qty) {
        return Map.of("outsourcingMaterialId", materialLineId, "qty", qty);
    }

    /** PUR-OS-T01～T04：BOM 展开用料、发料不齐套收货警告、超发与余料退回、收货 IQC 合格后核销（超耗需原因） */
    @Test
    void outsourcingFlow() throws Exception {
        String a = aux("OS-A");
        String b = aux("OS-B");
        stock(a, W_AUX, "300");
        stock(b, W_AUX, "200");
        String sf = material("506", "委外件SF1", Map.of("tracking", "NONE", "iqcRequired", true, "sourceType", "OUTSOURCE", "materialType", "SEMI_FINISHED"));
        approvedBom(sf, Map.of(a, 2, b, 1));
        String v = qualifiedSupplier("加工商", sf);

        // T01：新建委外 100 → A 应发 200、B 100
        JsonNode pv = ok(doGet("/api/purchase/outsourcings/bom-preview?materialId=" + sf + "&qty=100", admin));
        Map<String, BigDecimal> req = new HashMap<>();
        for (JsonNode m : pv.at("/materials")) req.put(m.at("/materialId").asText(), m.at("/requiredQty").decimalValue());
        assertThat(req.get(a)).isEqualByComparingTo("200");
        assertThat(req.get(b)).isEqualByComparingTo("100");
        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", v);
        body.put("materialId", sf);
        body.put("qty", "100");
        body.put("processPrice", "5");
        body.put("requiredDate", LocalDate.now().plusDays(10).toString());
        String id = ok(doPost("/api/purchase/outsourcings", admin, body)).asText();
        assertThat(ok(doPost("/api/purchase/outsourcings/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        JsonNode d = os(id);
        String ma = null;
        String mb = null;
        for (JsonNode m : d.at("/materials")) {
            if (m.at("/materialId").asText().equals(a)) ma = m.at("/id").asText();
            else mb = m.at("/id").asText();
        }

        // T04：A 只发了 100（B 已发齐）→ 收货 100 警告
        confirmOuts(ok(doPost("/api/purchase/outsourcings/" + id + "/issue", admin, Map.of("lines", List.of(qtyLine(ma, "100"), qtyLine(mb, "100"))))));
        assertThat(os(id).at("/status").asText()).isEqualTo("IN_PROGRESS");
        Map<String, Object> rl = new HashMap<>();
        rl.put("orderId", id);
        rl.put("qty", "100");
        JsonNode draft = ok(doPost("/api/purchase/receipts", admin, receiptBody(v, "OUTSOURCE", List.of(rl))));
        assertThat(draft.at("/warnings/0").asText()).isEqualTo("第 1 行收货数量超过已发材料可生产的数量（50）");
        ok(doDelete("/api/purchase/receipts/" + draft.at("/id").asText(), admin));

        // 超发：默认不允许超过应发；超发比例 5% 时可发到 210
        assertError(doPost("/api/purchase/outsourcings/" + id + "/issue", admin, Map.of("lines", List.of(qtyLine(ma, "105")))), "物料「" + code(a) + "」发料超过应发数量");
        setParam("pur.outsourcing.over-issue-pct", "5");
        try {
            confirmOuts(ok(doPost("/api/purchase/outsourcings/" + id + "/issue", admin, Map.of("lines", List.of(qtyLine(ma, "105"))))));
        } finally {
            resetParam("pur.outsourcing.over-issue-pct");
        }
        // 余料退回 A 3
        assertError(doPost("/api/purchase/outsourcings/" + id + "/return-material", admin, Map.of("lines", List.of(qtyLine(mb, "101")))),
                "物料「" + code(b) + "」退回数量超过已发未退数量");
        confirmIns(ok(doPost("/api/purchase/outsourcings/" + id + "/return-material", admin, Map.of("lines", List.of(qtyLine(ma, "3"))))));
        assertThat(onHand(a, W_AUX)).isEqualByComparingTo("98");
        d = os(id);
        assertThat(d.at("/kitQty").decimalValue()).isEqualByComparingTo("100");

        // T02：收货 100、IQC 合格 → 收货进度 100%
        assertError(doPost("/api/purchase/outsourcings/" + id + "/settle", admin, Map.of("lines", List.of())), "委外单还没有收齐合格品，不能核销");
        String rc = ok(doPost("/api/purchase/receipts", admin, receiptBody(v, "OUTSOURCE", List.of(rl)))).at("/id").asText();
        ok(doPost("/api/purchase/receipts/" + rc + "/approve", admin, null));
        confirmStockIns(rc);
        receiptApi.applyInspection(receipt(rc).at("/lines/0/id").asLong(), "IQC-" + uniq(), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
        d = os(id);
        assertThat(d.at("/receivedQty").decimalValue()).isEqualByComparingTo("100");
        assertThat(d.at("/qualifiedQty").decimalValue()).isEqualByComparingTo("100");

        // T03：A 超耗 2 需原因，B 超耗 0
        assertError(doPost("/api/purchase/outsourcings/" + id + "/settle", admin, Map.of("lines", List.of())), "物料「" + code(a) + "」超耗 2，请填写原因");
        ok(doPost("/api/purchase/outsourcings/" + id + "/settle", admin, Map.of("lines", List.of(Map.of("outsourcingMaterialId", ma, "lossReason", "加工损耗")))));
        d = os(id);
        assertThat(d.at("/status").asText()).isEqualTo("COMPLETED");
        for (JsonNode m : d.at("/materials")) {
            if (m.at("/id").asText().equals(ma)) {
                assertThat(m.at("/consumedQty").decimalValue()).isEqualByComparingTo("200");
                assertThat(m.at("/lossQty").decimalValue()).isEqualByComparingTo("2");
                assertThat(m.at("/lossReason").asText()).isEqualTo("加工损耗");
            } else {
                assertThat(m.at("/consumedQty").decimalValue()).isEqualByComparingTo("100");
                assertThat(m.at("/lossQty").decimalValue()).isEqualByComparingTo("0");
            }
        }
    }

    // ==================== 询价 ====================

    private Map<String, Object> quote(String line, String supplier, String price) {
        return Map.of("rfqLineId", line, "supplierId", supplier, "price", price);
    }

    /** PUR-RFQ-T01～T03：报价矩阵最低价、份额合计 100%、定标后按供应商生成草稿调价单 */
    @Test
    void rfqQuoteAndAward() throws Exception {
        String m1 = elec("RFQ-1");
        String m2 = elec("RFQ-2");
        String v1 = qualifiedSupplier("RFQ-V1", m1, m2);
        String v2 = qualifiedSupplier("RFQ-V2", m1, m2);
        String v3 = qualifiedSupplier("RFQ-V3", m1, m2);
        Map<String, Object> body = new HashMap<>();
        body.put("title", "年度电子料询价");
        body.put("quoteDeadline", LocalDate.now().plusDays(3).toString());
        body.put("lines", List.of(Map.of("materialId", m1, "qty", "1000"), Map.of("materialId", m2, "qty", "500")));
        body.put("supplierIds", List.of(v1, v2, v3));
        String id = ok(doPost("/api/purchase/rfqs", admin, body)).asText();
        ok(doPost("/api/purchase/rfqs/" + id + "/send", admin, null));
        JsonNode rfq = ok(doGet("/api/purchase/rfqs/" + id, admin));
        String l1 = rfq.at("/lines/0/id").asText();
        String l2 = rfq.at("/lines/1/id").asText();

        // T01：录入报价，每行最低价标记
        JsonNode saved = ok(doPut("/api/purchase/rfqs/" + id + "/quotes", admin, Map.of("quotes", List.of(
                quote(l1, v1, "1.10"), quote(l1, v2, "1.20"), quote(l1, v3, "1.30"),
                quote(l2, v1, "2.30"), quote(l2, v2, "2.10"), quote(l2, v3, "2.20")))));
        assertThat(saved.at("/saved").asInt()).isEqualTo(6);
        JsonNode mx = ok(doGet("/api/purchase/rfqs/" + id + "/quotes", admin));
        assertThat(mx.at("/rows/0/lowestPrice").decimalValue()).isEqualByComparingTo("1.10");
        assertThat(mx.at("/rows/1/lowestPrice").decimalValue()).isEqualByComparingTo("2.10");
        for (JsonNode row : mx.at("/rows")) {
            String lowest = row.at("/rfqLineId").asText().equals(l1) ? v1 : v2;
            for (JsonNode c : row.at("/cells")) assertThat(c.at("/lowest").asBoolean()).isEqualTo(c.at("/supplierId").asText().equals(lowest));
        }
        ok(doPost("/api/purchase/rfqs/" + id + "/end-quote", admin, null));

        // T03：份额 60% + 30%
        List<Map<String, Object>> bad = List.of(
                Map.of("rfqLineId", l1, "awards", List.of(Map.of("supplierId", v1, "pct", "60"), Map.of("supplierId", v2, "pct", "30"))),
                Map.of("rfqLineId", l2, "awards", List.of(Map.of("supplierId", v2))));
        assertError(doPost("/api/purchase/rfqs/" + id + "/award", admin, Map.of("lines", bad)), "物料「" + code(m1) + "」的中标份额合计必须为 100%");

        // T02：物料 1 选 V1、物料 2 选 V2 → 各一张草稿调价单
        List<Map<String, Object>> good = List.of(
                Map.of("rfqLineId", l1, "awards", List.of(Map.of("supplierId", v1))),
                Map.of("rfqLineId", l2, "awards", List.of(Map.of("supplierId", v2))));
        JsonNode adjusts = ok(doPost("/api/purchase/rfqs/" + id + "/award", admin, Map.of("lines", good))).at("/adjustIds");
        assertThat(adjusts.size()).isEqualTo(2);
        JsonNode a1 = ok(doGet("/api/purchase/price-adjusts/" + adjusts.get(0).asText(), admin));
        assertThat(a1.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(a1.at("/supplierId").asText()).isEqualTo(v1);
        assertThat(a1.at("/lines/0/newPrice").decimalValue()).isEqualByComparingTo("1.10");
        JsonNode a2 = ok(doGet("/api/purchase/price-adjusts/" + adjusts.get(1).asText(), admin));
        assertThat(a2.at("/supplierId").asText()).isEqualTo(v2);
        assertThat(a2.at("/lines/0/materialId").asText()).isEqualTo(m2);
        assertThat(ok(doGet("/api/purchase/rfqs/" + id, admin)).at("/status").asText()).isEqualTo("AWARDED");
    }
}
