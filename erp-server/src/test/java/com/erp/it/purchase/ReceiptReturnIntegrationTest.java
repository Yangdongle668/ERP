package com.erp.it.purchase;

import com.erp.module.purchase.api.receipt.PurchaseReceiptApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 到货（07-06）与采购退货（07-08）验收用例 */
class ReceiptReturnIntegrationTest extends PurchaseTestSupport {

    @Autowired
    PurchaseReceiptApi receiptApi;

    /** PUR-RC-T01、T03、T04、T05：分仓入库、入库后待检、IQC 判定回写、已入库不能反审核 */
    @Test
    void receiveInspectAndUnapproveBlocked() throws Exception {
        String a = elec("RC-A");
        String b = aux("RC-B");
        String v = qualifiedSupplier("RC供应商", a, b);
        String po = approvedOrder(v, List.of(orderLine(a, "1000", "1.13", null), orderLine(b, "50", "2.26", null)));
        JsonNode o = order(po);
        String la = o.at("/lines/0/id").asText();
        String lb = o.at("/lines/1/id").asText();

        String rc = approvedReceipt(v, List.of(receiptLine(la, "1000"), receiptLine(lb, "50")));
        JsonNode r = receipt(rc);
        assertThat(r.at("/status").asText()).isEqualTo("APPROVED");
        assertThat(r.at("/lines/0/targetWarehouseId").asText()).isEqualTo(W_QC);
        assertThat(r.at("/lines/0/inspectRequired").asBoolean()).isTrue();
        assertThat(r.at("/lines/1/targetWarehouseId").asText()).isEqualTo(W_AUX);
        String inA = r.at("/lines/0/stockInId").asText();
        assertThat(inA).isNotEqualTo(r.at("/lines/1/stockInId").asText());
        o = order(po);
        assertThat(o.at("/lines/0/receivedQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(o.at("/lines/1/receivedQty").decimalValue()).isEqualByComparingTo("50");
        assertThat(o.at("/status").asText()).isEqualTo("COMPLETED");

        // T03：入库确认后到货行显示已入库、待检
        confirmStockIns(rc);
        r = receipt(rc);
        assertThat(r.at("/lines/0/stockedQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(r.at("/lines/0/inspectStatus").asText()).isEqualTo("PENDING");
        assertThat(r.at("/lines/1/stockedQty").decimalValue()).isEqualByComparingTo("50");
        assertThat(onHand(a, W_QC)).isEqualByComparingTo("1000");

        // T05：入库单已确认，不能反审核
        String inNo = ok(doGet("/api/inventory/stock-ins/" + inA, admin)).at("/docNo").asText();
        assertError(doPost("/api/purchase/receipts/" + rc + "/unapprove", admin, Map.of("reason", "录错")), "入库单「" + inNo + "」已入库，不能反审核");

        // T04：IQC 判定合格 980、不合格 20
        receiptApi.applyInspection(r.at("/lines/0/id").asLong(), "IQC-" + uniq(), new BigDecimal("980"), BigDecimal.ZERO, new BigDecimal("20"));
        r = receipt(rc);
        assertThat(r.at("/lines/0/qualifiedQty").decimalValue()).isEqualByComparingTo("980");
        assertThat(r.at("/lines/0/rejectedQty").decimalValue()).isEqualByComparingTo("20");
        assertThat(r.at("/lines/0/inspectStatus").asText()).isEqualTo("PARTIAL");
        assertThat(r.at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(order(po).at("/lines/0/qualifiedQty").decimalValue()).isEqualByComparingTo("980");
    }

    /** PUR-RC-T02：超收比例 5%，到货 1060 超过允许数量 1050 */
    @Test
    void overReceiveBlocked() throws Exception {
        String a = elec("超收");
        String v = qualifiedSupplier("超收供应商", a);
        String po = approvedOrder(v, List.of(orderLine(a, "1000", "1", null)));
        String la = order(po).at("/lines/0/id").asText();
        setParam("pur.receipt.over-receive-default-pct", "5");
        try {
            assertError(doPost("/api/purchase/receipts", admin, receiptBody(v, "PURCHASE", List.of(receiptLine(la, "1060")))),
                    "第 1 行到货数量超过允许数量 1050");
            ok(doPost("/api/purchase/receipts", admin, receiptBody(v, "PURCHASE", List.of(receiptLine(la, "1050")))));
        } finally {
            resetParam("pur.receipt.over-receive-default-pct");
        }
    }

    /** PUR-RC-T06：保质期 180 天、最小剩余 2/3，生产日期 90 天前 → 剩余 50% */
    @Test
    void remainingShelfLife() throws Exception {
        String a = material(CAT_AUX, "效期料", Map.of("tracking", "NONE", "iqcRequired", false, "shelfLifeDays", 180, "minRemainingLifePct", "0.666667"));
        String v = qualifiedSupplier("效期供应商", a);
        String po = approvedOrder(v, List.of(orderLine(a, "10", "1", null)));
        Map<String, Object> line = receiptLine(order(po).at("/lines/0/id").asText(), "10");
        assertError(doPost("/api/purchase/receipts", admin, receiptBody(v, "PURCHASE", List.of(line))), "第 1 行请填写生产日期（不能晚于今天）");
        line.put("productionDate", LocalDate.now().minusDays(90).toString());
        JsonNode saved = ok(doPost("/api/purchase/receipts", admin, receiptBody(v, "PURCHASE", List.of(line))));
        assertThat(saved.at("/warnings/0").asText()).isEqualTo("第 1 行剩余保质期 50%，低于要求 66.67%");
        setParam("pur.receipt.min-remaining-life-check", "BLOCK");
        try {
            assertError(doPost("/api/purchase/receipts", admin, receiptBody(v, "PURCHASE", List.of(line))), "第 1 行剩余保质期 50%，低于要求 66.67%");
        } finally {
            resetParam("pur.receipt.min-remaining-life-check");
        }
    }

    /** 需检物料到货 → 入库 → IQC 判退 rejected → 不良品仓，返回到货行 ID */
    private String rejectedReceiptLine(String material, String supplier, String orderLine, String qty, String rejected) throws Exception {
        String rc = approvedReceipt(supplier, List.of(receiptLine(orderLine, qty)));
        confirmStockIns(rc);
        JsonNode l = receipt(rc).at("/lines/0");
        BigDecimal good = new BigDecimal(qty).subtract(new BigDecimal(rejected));
        receiptApi.applyInspection(l.at("/id").asLong(), "IQC-" + uniq(), good, BigDecimal.ZERO, new BigDecimal(rejected));
        String batch = l.at("/batchNo").isNull() ? null : l.at("/batchNo").asText();
        inspectionTransfer(material, batch, good.toPlainString(), rejected);
        return l.at("/id").asText();
    }

    private String confirmReturnOut(String returnId) throws Exception {
        JsonNode d = ok(doGet("/api/purchase/returns/" + returnId, admin));
        String out = d.at("/stockOutId").asText();
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of()));
        return out;
    }

    /** PUR-RT-T01、T02、T04：从不良品生成退货（换货）→ 出库确认 → 到货行已退货、订单行可再次到货；超过可退数量 */
    @Test
    void returnFromDefectsReplace() throws Exception {
        String a = elec("RT-A");
        String v = qualifiedSupplier("RT供应商", a);
        String po = approvedOrder(v, List.of(orderLine(a, "100", "1.13", null)));
        String ol = order(po).at("/lines/0/id").asText();
        String rl = rejectedReceiptLine(a, v, ol, "100", "20");
        assertThat(onHand(a, W_NG)).isEqualByComparingTo("20");

        // T04：入库 100，草稿退货单占用 80 → 可退 20，退 25
        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", v);
        body.put("returnReason", "IQC_REJECT");
        body.put("handling", "REPLACE");
        body.put("warehouseId", W_ELEC);
        body.put("lines", List.of(Map.of("receiptLineId", rl, "qty", "80")));
        String draft = ok(doPost("/api/purchase/returns", admin, body)).at("/id").asText();
        body.put("warehouseId", W_NG);
        body.put("lines", List.of(Map.of("receiptLineId", rl, "qty", "25")));
        assertError(doPost("/api/purchase/returns", admin, body), "第 1 行退货数量超过可退数量 20");
        ok(doDelete("/api/purchase/returns/" + draft, admin));

        // T01：从不良品生成
        JsonNode cands = ok(doGet("/api/purchase/returns/defect-candidates?supplierId=" + v, admin));
        assertThat(cands.size()).isEqualTo(1);
        assertThat(cands.at("/0/receiptLineId").asText()).isEqualTo(rl);
        assertThat(cands.at("/0/returnableQty").decimalValue()).isEqualByComparingTo("20");
        List<Map<String, Object>> items = List.of(Map.of("receiptLineId", rl, "warehouseId", W_NG));
        String rt = ok(doPost("/api/purchase/returns/from-defects", admin, Map.of("items", items))).get(0).asText();
        JsonNode d = ok(doGet("/api/purchase/returns/" + rt, admin));
        assertThat(d.at("/handling").asText()).isEqualTo("REPLACE");
        assertThat(d.at("/lines/0/qty").decimalValue()).isEqualByComparingTo("20");
        assertThat(ok(doPost("/api/purchase/returns/" + rt + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        confirmReturnOut(rt);
        assertThat(onHand(a, W_NG)).isEqualByComparingTo("0");
        JsonNode rline = ok(doGet("/api/purchase/returns/" + rt, admin));
        assertThat(rline.at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(rline.at("/lines/0/outQty").decimalValue()).isEqualByComparingTo("20");
        assertThat(receipt(rline.at("/lines/0/receiptId").asText()).at("/lines/0/returnedQty").decimalValue()).isEqualByComparingTo("20");

        // T02：换货 → 订单行未到货增加 20，可再次到货
        JsonNode o = order(po).at("/lines/0");
        assertThat(o.at("/returnedQty").decimalValue()).isEqualByComparingTo("0");
        assertThat(o.at("/receivedQty").decimalValue()).isEqualByComparingTo("80");
        assertThat(o.at("/replaceQty").decimalValue()).isEqualByComparingTo("20");
        assertThat(o.at("/openQty").decimalValue()).isEqualByComparingTo("20");
        approvedReceipt(v, List.of(receiptLine(ol, "20")));
        assertThat(order(po).at("/lines/0/openQty").decimalValue()).isEqualByComparingTo("0");
    }

    /** PUR-RT-T03（退货部分）：退款方式出库后订单行未到货数量不变 */
    @Test
    void returnRefund() throws Exception {
        String a = aux("RT-B");
        String v = qualifiedSupplier("退款供应商", a);
        String po = approvedOrder(v, List.of(orderLine(a, "50", "2.26", null)));
        String ol = order(po).at("/lines/0/id").asText();
        String rc = approvedReceipt(v, List.of(receiptLine(ol, "50")));
        confirmStockIns(rc);
        JsonNode lines = ok(doGet("/api/purchase/receipt-lines/returnable?supplierId=" + v, admin)).at("/list");
        assertThat(lines.size()).isEqualTo(1);
        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", v);
        body.put("returnReason", "STOCK_DEFECT");
        body.put("handling", "REFUND");
        body.put("warehouseId", W_AUX);
        body.put("lines", List.of(Map.of("receiptLineId", lines.at("/0/id").asText(), "qty", "5")));
        String rt = ok(doPost("/api/purchase/returns", admin, body)).at("/id").asText();
        JsonNode d = ok(doGet("/api/purchase/returns/" + rt, admin));
        assertThat(d.at("/totalAmount").decimalValue()).isEqualByComparingTo("11.30");
        ok(doPost("/api/purchase/returns/" + rt + "/submit", admin, null));
        confirmReturnOut(rt);
        JsonNode o = order(po).at("/lines/0");
        assertThat(o.at("/returnedQty").decimalValue()).isEqualByComparingTo("5");
        assertThat(o.at("/openQty").decimalValue()).isEqualByComparingTo("0");
        assertThat(onHand(a, W_AUX)).isEqualByComparingTo("45");
    }
}
