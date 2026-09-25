package com.erp.it.purchase;

import com.erp.module.purchase.api.order.InTransitDTO;
import com.erp.module.purchase.api.order.PurchaseQueryApi;
import com.erp.module.purchase.api.requisition.MrpPurchaseSuggestion;
import com.erp.module.purchase.api.requisition.PurchaseRequisitionApi;
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

/** 采购申请（07-03）与采购订单（07-05）验收用例 */
class RequisitionOrderIntegrationTest extends PurchaseTestSupport {

    @Autowired
    PurchaseRequisitionApi requisitionApi;

    @Autowired
    PurchaseQueryApi queryApi;

    private Map<String, Object> reqLine(String materialId, String qty, String supplierId) {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("requiredDate", LocalDate.now().plusDays(10).toString());
        l.put("suggestedSupplierId", supplierId);
        return l;
    }

    private String approvedRequisition(List<Map<String, Object>> lines) throws Exception {
        String id = ok(doPost("/api/purchase/requisitions", admin, Map.of("requisitionType", "MANUAL", "lines", lines))).asText();
        assertThat(ok(doPost("/api/purchase/requisitions/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    /** PUR-REQ-T04：自制物料不能申请采购 */
    @Test
    void makeMaterialCannotBeRequested() throws Exception {
        String m = material(CAT_ELEC, "自制件", Map.of("tracking", "NONE", "sourceType", "MAKE"));
        String id = ok(doPost("/api/purchase/requisitions", admin, Map.of("lines", List.of(reqLine(m, "10", null))))).asText();
        assertError(doPost("/api/purchase/requisitions/" + id + "/submit", admin, null), "物料「" + code(m) + "」取得方式为自制，不能申请采购");
    }

    /** PUR-PO-T01、PUR-REQ-T02、T03：从申请生成（按供应商分组、MPQ 取整）、回写已转数量、不能反审核 */
    @Test
    void requisitionToOrder() throws Exception {
        String a = elec("A");
        String b = elec("B");
        String c = elec("C");
        String v1 = qualifiedSupplier("V1", a, b, c);
        price(v1, List.of(List.of(a, "0", "1"), List.of(b, "0", "2"), List.of(c, "0", "3")));
        // A 的供应商 MPQ 400：1000 → 1200
        long lineA = ok(doGet("/api/purchase/suppliers/" + v1 + "/materials", admin)).findValues("id").get(0).asLong();
        JsonNode sm = ok(doGet("/api/purchase/suppliers/" + v1 + "/materials", admin));
        for (JsonNode n : sm) {
            if (n.at("/materialId").asText().equals(a)) lineA = n.at("/id").asLong();
        }
        ok(doPut("/api/purchase/suppliers/" + v1 + "/materials/" + lineA, admin, Map.of("materialId", a, "supplyStatus", "QUALIFIED", "mpq", "400")));

        String r1 = approvedRequisition(List.of(reqLine(a, "1000", v1), reqLine(b, "500", v1)));
        String r2 = approvedRequisition(List.of(reqLine(c, "100", v1), reqLine(a, "50", v1)));
        JsonNode pending = ok(doGet("/api/purchase/requisition-lines/pending?materialId=" + a, admin)).at("/list");
        assertThat(pending).hasSize(2);
        JsonNode d1 = ok(doGet("/api/purchase/requisitions/" + r1, admin));
        assertThat(d1.at("/lines/0/referencePrice").decimalValue()).isEqualByComparingTo("1");

        // T01：两行同一供应商 → 一张草稿订单
        List<Map<String, Object>> sel = new ArrayList<>();
        for (JsonNode l : d1.at("/lines")) sel.add(Map.of("requisitionLineId", l.at("/id").asText()));
        JsonNode res = ok(doPost("/api/purchase/orders/from-requisitions", admin, Map.of("lines", sel)));
        assertThat(res.at("/orderIds")).hasSize(1);
        assertThat(res.at("/messages/0").asText()).contains("已按 MOQ/MPQ 取整为 1200");
        String po = res.at("/orderIds/0").asText();
        JsonNode o = order(po);
        assertThat(o.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(o.at("/lines")).hasSize(2);
        assertThat(o.at("/lines/0/qty").decimalValue()).isEqualByComparingTo("1200");
        assertThat(o.at("/lines/0/priceInclTax").decimalValue()).isEqualByComparingTo("1.13");
        assertThat(o.at("/lines/0/requisitionNo").asText()).isEqualTo(d1.at("/docNo").asText());

        ok(doPost("/api/purchase/orders/" + po + "/submit", admin, null));
        // REQ-T02：已转 1000，单据已完成
        JsonNode after = ok(doGet("/api/purchase/requisitions/" + r1, admin));
        assertThat(after.at("/lines/0/orderedQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(after.at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(after.at("/related/0/docNo").asText()).isEqualTo(o.at("/docNo").asText());

        // REQ-T03：申请 2 行，转了 1 行 → 不能反审核
        JsonNode d2 = ok(doGet("/api/purchase/requisitions/" + r2, admin));
        String po2 = ok(doPost("/api/purchase/orders/from-requisitions", admin, Map.of("lines",
                List.of(Map.of("requisitionLineId", d2.at("/lines/0/id").asText()))))).at("/orderIds/0").asText();
        ok(doPost("/api/purchase/orders/" + po2 + "/submit", admin, null));
        assertThat(ok(doGet("/api/purchase/requisitions/" + r2, admin)).at("/status").asText()).isEqualTo("IN_PROGRESS");
        assertError(doPost("/api/purchase/requisitions/" + r2 + "/unapprove", admin, Map.of("reason", "修改")), "申请已转采购订单，不能反审核");
        // 订单反审核扣回申请已转数量
        ok(doPost("/api/purchase/orders/" + po2 + "/unapprove", admin, Map.of("reason", "调整")));
        assertThat(ok(doGet("/api/purchase/requisitions/" + r2, admin)).at("/status").asText()).isEqualTo("APPROVED");
        // 关闭：剩余行关闭
        ok(doPost("/api/purchase/orders/" + po2 + "/void", admin, Map.of("reason", "不用了")));
        ok(doPost("/api/purchase/requisitions/" + r2 + "/close", admin, Map.of("reason", "需求取消")));
        assertThat(ok(doGet("/api/purchase/requisition-lines/pending?materialId=" + c, admin)).at("/total").asInt()).isZero();
    }

    /** PUR-REQ-T01：MRP 建议按计划员合并为一张申请单 */
    @Test
    void createFromMrp() throws Exception {
        String a = elec("MRP-A");
        String b = elec("MRP-B");
        String c = elec("MRP-C");
        List<Long> ids = requisitionApi.createFromMrp(List.of(
                new MrpPurchaseSuggestion(1L, 11L, Long.valueOf(a), new BigDecimal("100"), LocalDate.now().plusDays(5), 1L, null, "SO-001"),
                new MrpPurchaseSuggestion(1L, 12L, Long.valueOf(b), new BigDecimal("200"), LocalDate.now().plusDays(5), 1L, null, "SO-002"),
                new MrpPurchaseSuggestion(1L, 13L, Long.valueOf(c), new BigDecimal("300"), LocalDate.now().plusDays(5), 1L, null, "MO-003")));
        assertThat(ids).hasSize(1);
        JsonNode d = ok(doGet("/api/purchase/requisitions/" + ids.get(0), admin));
        assertThat(d.at("/requisitionType").asText()).isEqualTo("MRP");
        assertThat(d.at("/lines")).hasSize(3);
        assertThat(d.at("/lines/2/sourceDemand").asText()).isEqualTo("MO-003");
        assertThat(d.at("/lines/0/mrpResultId").asText()).isEqualTo("11");
    }

    /** PUR-PO-T02、T03、T04、T08：金额计算、超价、回复交期、暂停供应商 */
    @Test
    void orderAmountsOverrunAndDates() throws Exception {
        String a = elec("PO-A");
        String v = qualifiedSupplier("PO", a);
        price(v, List.of(List.of(a, "0", "1.00")));
        // T02：含税 1.13、税率 13%、数量 1000
        String po = createOrder(v, "STANDARD", List.of(orderLine(a, "1000", "1.13", null)));
        JsonNode o = order(po);
        assertThat(o.at("/lines/0/price").decimalValue()).isEqualByComparingTo("1.0");
        assertThat(o.at("/totalAmount").decimalValue()).isEqualByComparingTo("1130.00");
        assertThat(o.at("/amount").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(o.at("/taxAmount").decimalValue()).isEqualByComparingTo("130.00");
        assertThat(o.at("/hasPriceOverrun").asBoolean()).isFalse();

        // T03：单价改为 1.05（不含税）→ 超价
        Map<String, Object> l = orderLine(a, "1000", null, null);
        l.put("price", "1.05");
        String po2 = ok(doPost("/api/purchase/orders", admin, Map.of("supplierId", v, "taxIncluded", false, "lines", List.of(l)))).at("/id").asText();
        JsonNode o2 = order(po2);
        assertThat(o2.at("/lines/0/priceOverrun").asBoolean()).isTrue();
        assertThat(o2.at("/hasPriceOverrun").asBoolean()).isTrue();
        assertThat(o2.at("/lines/0/listPrice").decimalValue()).isEqualByComparingTo("1.00");

        // T04：回复交期晚于要求 5 天 → 交期延误
        ok(doPost("/api/purchase/orders/" + po + "/submit", admin, null));
        LocalDate required = LocalDate.parse(order(po).at("/lines/0/requiredDate").asText());
        String lineId = order(po).at("/lines/0/id").asText();
        ok(doPut("/api/purchase/orders/" + po + "/confirmed-dates", admin, Map.of("lines", List.of(Map.of("lineId", lineId,
                "confirmedDate", required.plusDays(5).toString())))));
        assertThat(order(po).at("/lines/0/delayed").asBoolean()).isTrue();
        assertThat(ok(doGet("/api/purchase/orders?docNo=" + o.at("/docNo").asText(), admin)).at("/list/0/delayedLines").asInt()).isEqualTo(1);
        // 在途量
        InTransitDTO t = queryApi.getInTransitQty(List.of(Long.valueOf(a))).get(Long.valueOf(a));
        assertThat(t.qty()).isEqualByComparingTo("1000");
        assertThat(t.details().get(0).expectedDate()).isEqualTo(required.plusDays(5));

        // T07：关闭后在途不再包含
        ok(doPost("/api/purchase/orders/" + po + "/close", admin, Map.of("reason", "供应商停产")));
        assertThat(order(po).at("/status").asText()).isEqualTo("CLOSED");
        assertThat(queryApi.getOpenQtyByMaterial(Long.valueOf(a))).isEqualByComparingTo("0");

        // T08：供应商暂停 → 标准订单提交失败
        JsonNode sup = ok(doGet("/api/purchase/suppliers/" + v, admin));
        ok(doPost("/api/purchase/suppliers/" + v + "/suspend", admin, Map.of("reason", "整改")));
        assertError(doPost("/api/purchase/orders/" + po2 + "/submit", admin, null), "供应商「" + sup.at("/name").asText() + "」不是合格供应商");
        // R04 BLOCK：无有效价格时阻止
        ok(doPost("/api/purchase/suppliers/" + v + "/resume", admin, null));
        String b = elec("无价");
        ok(doPost("/api/purchase/suppliers/" + v + "/materials", admin, Map.of("materialId", b, "supplyStatus", "QUALIFIED")));
        String po3 = createOrder(v, "STANDARD", List.of(orderLine(b, "5", "1", null)));
        setParam("pur.order.require-price", "BLOCK");
        try {
            assertError(doPost("/api/purchase/orders/" + po3 + "/submit", admin, null), "物料「" + code(b) + "」没有有效的采购价格");
        } finally {
            resetParam("pur.order.require-price");
        }
        JsonNode warn = ok(doPost("/api/purchase/orders/" + po3 + "/submit", admin, null));
        assertThat(warn.at("/warnings/0").asText()).contains("无有效采购价格");
        // 打印数据（英文）
        JsonNode print = ok(doGet("/api/purchase/orders/" + po3 + "/print-data?lang=en", admin));
        assertThat(print.at("/orderTypeName").asText()).isEqualTo("Standard");
    }

    /** PUR-PO-T05、T06：有到货后反审核被拒绝；变更数量不能小于已到货 */
    @Test
    void orderChangeAfterReceipt() throws Exception {
        String a = aux("CHG-A");
        String v = qualifiedSupplier("CHG", a);
        price(v, List.of(List.of(a, "0", "1")));
        String po = approvedOrder(v, List.of(orderLine(a, "1000", "1.13", null), orderLine(a, "200", "1.13", null)));
        JsonNode o = order(po);
        String l1 = o.at("/lines/0/id").asText();
        String l2 = o.at("/lines/1/id").asText();
        approvedReceipt(v, List.of(receiptLine(l1, "600")));
        assertThat(order(po).at("/status").asText()).isEqualTo("IN_PROGRESS");
        // T06
        assertError(doPost("/api/purchase/orders/" + po + "/unapprove", admin, Map.of("reason", "改")), "订单已有到货，不能反审核，请使用变更");
        // T05
        assertError(doPost("/api/purchase/order-changes", admin, Map.of("orderId", po, "changeReason", "减量",
                "lines", List.of(Map.of("orderLineId", l1, "changeType", "MODIFY", "newQty", "500")))), "第 1 行变更后数量不能小于已到货数量 600");
        // 变更：第 1 行改为 700、第 2 行取消、新增一行
        String chg = ok(doPost("/api/purchase/order-changes", admin, Map.of("orderId", po, "changeReason", "客户减单", "lines", List.of(
                Map.of("orderLineId", l1, "changeType", "MODIFY", "newQty", "700"),
                Map.of("orderLineId", l2, "changeType", "CANCEL"),
                Map.of("changeType", "ADD", "materialId", a, "newQty", "50", "newPrice", "1", "newRequiredDate", LocalDate.now().plusDays(3).toString()))))).asText();
        assertError(doPost("/api/purchase/orders/" + po + "/close", admin, Map.of("reason", "x")), "订单有未完成的变更单「"
                + ok(doGet("/api/purchase/order-changes/" + chg, admin)).at("/docNo").asText() + "」");
        assertThat(ok(doPost("/api/purchase/order-changes/" + chg + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        JsonNode after = order(po);
        assertThat(after.at("/orderVersion").asInt()).isEqualTo(2);
        assertThat(after.at("/lines")).hasSize(2);
        assertThat(after.at("/lines/0/qty").decimalValue()).isEqualByComparingTo("700");
        assertThat(after.at("/lines/1/qty").decimalValue()).isEqualByComparingTo("50");
        assertThat(after.at("/totalAmount").decimalValue()).isEqualByComparingTo("847.50");
    }
}
