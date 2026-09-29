package com.erp.it.finance;

import com.erp.framework.event.DomainEventPublisher;
import com.erp.it.shipping.ShippingTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 财务集成测试的公共准备：本公司银行账户（CNY / USD）、走简化出货流程（不拣货、不装箱）生成出货确认、供应商与采购订单。
 * 每个测试使用独立的新客户 / 供应商与物料，数据互不影响。
 */
abstract class FinanceTestSupport extends ShippingTestSupport {

    protected static String bankCny;
    protected static String bankUsd;

    @Autowired
    protected DomainEventPublisher events;

    @BeforeEach
    void prepareFinance() throws Exception {
        ensureBanks();
    }

    private synchronized void ensureBanks() throws Exception {
        if (bankCny != null) return;
        bankCny = ok(doPost("/api/finance/bank-accounts", admin, bank("招行基本户", "CNY"))).asText();
        bankUsd = ok(doPost("/api/finance/bank-accounts", admin, bank("中行美元户", "USD"))).asText();
    }

    private static Map<String, Object> bank(String name, String currency) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", "B" + uniq());
        b.put("name", name);
        b.put("bankName", name.startsWith("招行") ? "招商银行" : "中国银行");
        b.put("accountNo", "6222" + uniq());
        b.put("currency", currency);
        b.put("isDefault", true);
        return b;
    }

    // ==================== 出货 ====================

    /** 新建订单并按简化流程全部出货（通知 → 出货单 → 出库确认），返回出货单 ID */
    protected String shipOrder(String customerId, String orderId, int lineIndex, String qty) throws Exception {
        setParam("shp.picking.enabled", "false");
        setParam("shp.packing.enabled", "false");
        try {
            String n = notice(customerId, orderLineId(orderId, lineIndex), qty);
            submitNotice(n);
            String s = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of())).asText();
            submitAndConfirm(s);
            return s;
        } finally {
            resetParam("shp.picking.enabled");
            resetParam("shp.packing.enabled");
        }
    }

    /** 出货单生成的应收单（未作废） */
    protected JsonNode arOfShipment(String shipmentId) throws Exception {
        String no = shipment(shipmentId).at("/docNo").asText();
        JsonNode list = ok(doGet("/api/finance/receivables?sourceNo=" + no + "&statuses=DRAFT,PENDING,CONFIRMED", admin)).at("/list");
        assertThat(list.size()).isEqualTo(1);
        return ar(list.get(0).at("/id").asText());
    }

    protected JsonNode ar(String id) throws Exception {
        return ok(doGet("/api/finance/receivables/" + id, admin));
    }

    // ==================== 收款、核销 ====================

    protected String receipt(String customerId, String type, String bankId, String amount, String fee, String rate, String orderId) throws Exception {
        Map<String, Object> r = new HashMap<>();
        r.put("customerId", customerId);
        r.put("receiptType", type);
        r.put("bankAccountId", bankId);
        r.put("settlementMethod", "TT");
        r.put("receiptDate", LocalDate.now().toString());
        r.put("amount", amount);
        r.put("bankFee", fee);
        if (rate != null) r.put("exchangeRate", rate);
        if (orderId != null) r.put("orderId", orderId);
        return ok(doPost("/api/finance/receipts", admin, r)).asText();
    }

    protected String confirmedReceipt(String customerId, String type, String bankId, String amount, String fee, String rate, String orderId) throws Exception {
        String id = receipt(customerId, type, bankId, amount, fee, rate, orderId);
        ok(doPost("/api/finance/receipts/" + id + "/confirm", admin, null));
        return id;
    }

    protected static Map<String, Object> pick(String docType, String docId, String amount) {
        return Map.of("docType", docType, "docId", docId, "amount", amount);
    }

    protected JsonNode verifyReceipt(String customerId, String currency, List<Map<String, Object>> left, List<Map<String, Object>> right) throws Exception {
        return doPost("/api/finance/verifications/receipt", admin, Map.of("partnerId", customerId, "currency", currency, "left", left, "right", right));
    }

    /** 其他应收（无审批流 → 已确认） */
    protected String otherAr(String customerId, String currency, LocalDate bizDate, LocalDate dueDate, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("currency", currency);
        body.put("bizDate", bizDate.toString());
        body.put("dueDate", dueDate == null ? null : dueDate.toString());
        body.put("description", "样品费");
        body.put("lines", List.of(Map.of("description", "样品费", "totalAmount", amount, "taxRate", "0")));
        String id = ok(doPost("/api/finance/receivables/other", admin, body)).asText();
        assertThat(ok(doPost("/api/finance/receivables/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("CONFIRMED");
        return id;
    }

    // ==================== 供应商、采购订单 ====================

    /** 合格供应商（带银行账户），可供应给定物料 */
    protected String supplier(String name, String... materialIds) throws Exception {
        Map<String, Object> s = new HashMap<>();
        s.put("name", name + uniq());
        s.put("shortName", name);
        s.put("supplierType", "MANUFACTURER");
        s.put("country", "CN");
        s.put("currency", "CNY");
        s.put("paymentTermId", "406");
        s.put("purchaseTaxRate", "0.13");
        s.put("contacts", List.of(Map.of("name", "张经理", "mobile", "13800000000", "isPrimary", true)));
        s.put("banks", List.of(Map.of("bankName", "招商银行", "accountName", name, "accountNo", "6225" + uniq(), "isDefault", true)));
        s.put("certs", List.of(Map.of("certType", "LICENSE", "certNo", "C" + uniq(), "fileId", uploadPdf())));
        s.put("materials", java.util.Arrays.stream(materialIds).map(m -> Map.of("materialId", m, "supplyStatus", "QUALIFIED")).toList());
        String id = ok(doPost("/api/purchase/suppliers", admin, s)).asText();
        assertThat(ok(doPost("/api/purchase/suppliers/" + id + "/qualify", admin, null)).asText()).isEqualTo("QUALIFIED");
        return id;
    }

    /** 已审核采购订单（含税价），返回订单 ID */
    protected String purchaseOrder(String supplierId, String materialId, String qty, String priceInclTax) throws Exception {
        ok(doPost("/api/purchase/price-adjusts/" + ok(doPost("/api/purchase/price-adjusts", admin, Map.of("supplierId", supplierId, "adjustReason", "报价",
                "lines", List.of(Map.of("materialId", materialId, "minQty", "0", "newPrice", priceInclTax))))).asText() + "/submit", admin, null));
        Map<String, Object> o = new HashMap<>();
        o.put("supplierId", supplierId);
        o.put("orderType", "STANDARD");
        o.put("taxIncluded", true);
        o.put("lines", List.of(Map.of("materialId", materialId, "qty", qty, "priceInclTax", priceInclTax,
                "requiredDate", LocalDate.now().plusDays(7).toString())));
        String id = ok(doPost("/api/purchase/orders", admin, o)).at("/id").asText();
        assertThat(ok(doPost("/api/purchase/orders/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    private String uploadPdf() throws Exception {
        byte[] pdf = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        String body = mockMvc.perform(multipart("/api/system/files").file(new MockMultipartFile("file", "cert.pdf", "application/pdf", pdf))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ok(read(body)).at("/id").asText();
    }

    protected JsonNode ap(String id) throws Exception {
        return ok(doGet("/api/finance/payables/" + id, admin));
    }

    protected String supplierBank(String supplierId) throws Exception {
        return ok(doGet("/api/finance/payment-requests/supplier-banks?supplierId=" + supplierId, admin)).get(0).at("/id").asText();
    }
}
