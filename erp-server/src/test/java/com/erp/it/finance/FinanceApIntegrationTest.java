package com.erp.it.finance;

import com.erp.common.exception.BizException;
import com.erp.module.finance.api.query.PayableQueryApi;
import com.erp.module.purchase.api.statement.PurchaseStatementConfirmedEvent;
import com.erp.module.purchase.api.statement.PurchaseStatementUnconfirmingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 应付、进项发票三单匹配、付款申请与付款（12-04、12-05） */
class FinanceApIntegrationTest extends FinanceTestSupport {

    private static final AtomicLong STATEMENT_ID = new AtomicLong(8_800_000);

    @Autowired
    PayableQueryApi payableQueryApi;

    /** 模拟资材对账单确认：货款 100 × 113（税率 13%）、退货 −5 × 113 */
    private long statement(String supplierId, String materialId) {
        long id = STATEMENT_ID.incrementAndGet();
        LocalDate to = LocalDate.now();
        List<PurchaseStatementConfirmedEvent.Line> lines = List.of(
                new PurchaseStatementConfirmedEvent.Line(id * 10 + 1, "GOODS", "PUR_RECEIPT", 1L, 1L, "RC-" + id, "PO-" + id, Long.valueOf(materialId), to,
                        new BigDecimal("100"), new BigDecimal("113"), new BigDecimal("0.13"), new BigDecimal("10000"), new BigDecimal("1300"),
                        new BigDecimal("11300")),
                new PurchaseStatementConfirmedEvent.Line(id * 10 + 2, "RETURN", "PUR_RETURN", 2L, 2L, "PR-" + id, "PO-" + id, Long.valueOf(materialId), to,
                        new BigDecimal("-5"), new BigDecimal("113"), new BigDecimal("0.13"), new BigDecimal("-500"), new BigDecimal("-65"),
                        new BigDecimal("-565")));
        events.publish(new PurchaseStatementConfirmedEvent(id, "ST-" + id, Long.valueOf(supplierId), "CNY", to.withDayOfMonth(1), to,
                new BigDecimal("10735"), new BigDecimal("1235"), lines));
        return id;
    }

    private JsonNode apOfStatement(long statementId) throws Exception {
        JsonNode list = ok(doGet("/api/finance/payables?statementNo=ST-" + statementId + "&statuses=DRAFT,CONFIRMED", admin)).at("/list");
        assertThat(list.size()).isEqualTo(1);
        return ap(list.get(0).at("/id").asText());
    }

    private Map<String, Object> invoiceBody(String supplierId, String invoiceNo, String total, String tax, List<Map<String, Object>> lines) {
        Map<String, Object> b = new HashMap<>();
        b.put("supplierId", supplierId);
        b.put("invoiceType", "VAT_SPECIAL");
        b.put("invoiceNo", invoiceNo);
        b.put("invoiceDate", LocalDate.now().toString());
        b.put("totalAmount", total);
        b.put("taxAmount", tax);
        b.put("lines", lines);
        return b;
    }

    private static Map<String, Object> invLine(String apLineId, String qty, String price, String reason) {
        Map<String, Object> l = new HashMap<>();
        l.put("payableLineId", apLineId);
        if (qty != null) l.put("qty", qty);
        if (price != null) l.put("invoicePrice", price);
        if (reason != null) l.put("diffReason", reason);
        return l;
    }

    /** FIN-AP-T01 / T02 / T04，FIN-PAY-T01 / T02 / T04 */
    @Test
    void statementPayableInvoiceAndPayment() throws Exception {
        String m = fg("外购件", Map.of());
        String v = supplier("应付供应商", m);
        long st = statement(v, m);
        // 重复投递不重复生成
        JsonNode ap = apOfStatement(st);
        String apId = ap.at("/header/id").asText();
        String apNo = ap.at("/header/docNo").asText();
        // FIN-AP-T01：2 行，合计 10,735（参数默认不自动确认）
        assertThat(ap.at("/lines").size()).isEqualTo(2);
        assertThat(ap.at("/header/totalAmount").decimalValue()).isEqualByComparingTo("10735");
        assertThat(ap.at("/header/status").asText()).isEqualTo("DRAFT");
        assertThat(ap.at("/header/apType").asText()).isEqualTo("PURCHASE");
        // 到期日：采购月结 30 天 = 区间结束月末 + 30
        assertThat(ap.at("/header/dueDate").asText()).isEqualTo(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).plusDays(30).toString());
        ok(doPost("/api/finance/payables/" + apId + "/confirm", admin, null));
        assertThat(payableQueryApi.getBalance(Long.valueOf(v))).isEqualByComparingTo("10735");

        // FIN-AP-T02：发票 10,735.50，尾差 0.50 自动调整最后一行 → 已匹配
        JsonNode cands = ok(doGet("/api/finance/payable-lines/uninvoiced?supplierId=" + v, admin));
        assertThat(cands.size()).isEqualTo(2);
        assertThat(cands.get(0).at("/apPrice").decimalValue()).isEqualByComparingTo("100");
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode c : cands) lines.add(invLine(c.at("/payableLineId").asText(), null, null, null));
        String invNo = "PI" + uniq();
        assertError(doPost("/api/finance/purchase-invoices", admin, invoiceBody(v, invNo, "10737", "1235", lines)), "发票金额与明细合计差异 2.00 超过容差");
        String inv = ok(doPost("/api/finance/purchase-invoices", admin, invoiceBody(v, invNo, "10735.50", "1235", lines))).asText();
        JsonNode invd = ok(doGet("/api/finance/purchase-invoices/" + inv, admin));
        assertThat(invd.at("/header/matchStatus").asText()).isEqualTo("MATCHED");
        assertThat(invd.at("/lines/1/totalAmount").decimalValue()).isEqualByComparingTo("-564.50");
        assertThat(ap(apId).at("/header/invoicedAmount").decimalValue()).isEqualByComparingTo("10735");
        assertError(doPost("/api/finance/purchase-invoices", admin, invoiceBody(v, invNo, "1", "0", lines)), "发票「" + invNo + "」已登记");

        // FIN-AP-T04：已匹配发票 → 资材不能取消对账确认
        assertThatThrownBy(() -> events.publish(new PurchaseStatementUnconfirmingEvent(st, "ST-" + st)))
                .isInstanceOf(BizException.class).hasMessage("财务已根据此对账单生成应付并已处理，不能取消确认");
        assertError(doPost("/api/finance/payables/" + apId + "/unconfirm", admin, Map.of("reason", "x")), "应付已匹配发票、已申请付款或已付款，不能反确认");

        // FIN-PAY-T01：申请 10,735 → 审批（无审批流直接通过）→ 占用可申请金额
        String bank = supplierBank(v);
        JsonNode pc = ok(doGet("/api/finance/payables/payable-candidates?supplierId=" + v, admin));
        assertThat(pc.get(0).at("/requestableAmount").decimalValue()).isEqualByComparingTo("10735");
        JsonNode rr = ok(doPost("/api/finance/payment-requests", admin, request(v, bank, apId, "10735")));
        assertThat(rr.at("/warnings").size()).isZero();
        String req = rr.at("/id").asText();
        assertThat(ok(doPost("/api/finance/payment-requests/" + req + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        assertThat(ap(apId).at("/header/requestedAmount").decimalValue()).isEqualByComparingTo("10735");
        // FIN-PAY-T02：再申请 → 可申请金额为 0
        assertError(doPost("/api/finance/payment-requests", admin, request(v, bank, apId, "1")), "应付「" + apNo + "」可申请金额为 0.00");
        JsonNode print = ok(doGet("/api/finance/payment-requests/" + req + "/print-data", admin));
        assertThat(print.at("/amountInWords").asText()).isEqualTo("壹万零柒佰叁拾伍元整");

        // 付款：币别必须与申请一致；金额 ≤ 申请未付
        assertError(doPost("/api/finance/payments", admin, payment(req, bankUsd, "5000")), "付款账户币别必须与申请币别一致");
        assertError(doPost("/api/finance/payments", admin, payment(req, bankCny, "10736")), "付款金额超过申请未付金额");
        // FIN-PAY-T04：付款 5,000 → 申请部分付款；应付已付 5,000
        String p1 = ok(doPost("/api/finance/payments", admin, payment(req, bankCny, "5000"))).asText();
        ok(doPost("/api/finance/payments/" + p1 + "/confirm", admin, null));
        JsonNode rd = ok(doGet("/api/finance/payment-requests/" + req, admin));
        assertThat(rd.at("/header/status").asText()).isEqualTo("PARTIAL");
        assertThat(rd.at("/header/paidAmount").decimalValue()).isEqualByComparingTo("5000");
        ap = ap(apId);
        assertThat(ap.at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(ap.at("/header/requestedAmount").decimalValue()).isEqualByComparingTo("5735");
        assertThat(ap.at("/verifications/0/verifyType").asText()).isEqualTo("PAYMENT_AP");
        // 付清
        String p2 = ok(doPost("/api/finance/payments", admin, payment(req, bankCny, "5735"))).asText();
        ok(doPost("/api/finance/payments/" + p2 + "/confirm", admin, null));
        assertThat(ok(doGet("/api/finance/payment-requests/" + req, admin)).at("/header/status").asText()).isEqualTo("PAID");
        assertThat(ap(apId).at("/header/unpaidAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(payableQueryApi.getBalance(Long.valueOf(v))).isEqualByComparingTo("0");
        // 反确认付款 → 反核销、申请回到部分付款
        ok(doPost("/api/finance/payments/" + p2 + "/unconfirm", admin, Map.of("reason", "银行退回")));
        assertThat(ok(doGet("/api/finance/payment-requests/" + req, admin)).at("/header/status").asText()).isEqualTo("PARTIAL");
        ap = ap(apId);
        assertThat(ap.at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(ap.at("/header/requestedAmount").decimalValue()).isEqualByComparingTo("5735");
        // 关闭申请 → 释放占用
        ok(doPost("/api/finance/payments/" + p2 + "/void", admin, Map.of("reason", "重付")));
        ok(doPost("/api/finance/payment-requests/" + req + "/close", admin, Map.of("reason", "剩余下月付")));
        assertThat(ap(apId).at("/header/requestedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(ap(apId).at("/header/requestableAmount").decimalValue()).isEqualByComparingTo("5735");
    }

    private static Map<String, Object> request(String supplierId, String bankId, String payableId, String amount) {
        Map<String, Object> r = new HashMap<>();
        r.put("supplierId", supplierId);
        r.put("requestType", "PAYABLE");
        r.put("currency", "CNY");
        r.put("planPayDate", LocalDate.now().plusDays(3).toString());
        r.put("supplierBankId", bankId);
        r.put("reason", "到期货款");
        r.put("lines", List.of(Map.of("payableId", payableId, "amount", amount)));
        return r;
    }

    private static Map<String, Object> payment(String requestId, String bankId, String amount) {
        return Map.of("requestId", requestId, "bankAccountId", bankId, "settlementMethod", "TT", "payDate", LocalDate.now().toString(),
                "amount", amount, "bankFee", "10");
    }

    /** FIN-AP-T03 价差超容差、差异确认；FIN-PAY-T03 预付款冲应付；FIN-PAY-R06 预付上限 */
    @Test
    void priceDiffAndPrepayment() throws Exception {
        String m = fg("外购件B", Map.of());
        String v = supplier("预付供应商", m);
        String po = purchaseOrder(v, m, "100", "113");
        String bank = supplierBank(v);

        // FIN-PAY-R06：预付款必须关联订单，累计不超过订单价税合计
        JsonNode oo = ok(doGet("/api/finance/payment-requests/order-options?supplierId=" + v, admin));
        assertThat(oo.get(0).at("/available").decimalValue()).isEqualByComparingTo("11300");
        Map<String, Object> pre = new HashMap<>(Map.of("supplierId", v, "requestType", "PREPAYMENT", "currency", "CNY",
                "planPayDate", LocalDate.now().toString(), "supplierBankId", bank, "amount", "3390"));
        assertError(doPost("/api/finance/payment-requests", admin, pre), "预付款请选择采购订单");
        pre.put("orderId", po);
        String preReq = ok(doPost("/api/finance/payment-requests", admin, pre)).at("/id").asText();
        ok(doPost("/api/finance/payment-requests/" + preReq + "/submit", admin, null));
        pre.put("amount", "7911");
        assertError(doPost("/api/finance/payment-requests", admin, pre), "预付金额超过订单金额");
        String prePay = ok(doPost("/api/finance/payments", admin, payment(preReq, bankCny, "3390"))).asText();
        ok(doPost("/api/finance/payments/" + prePay + "/confirm", admin, null));
        assertThat(ok(doGet("/api/finance/payment-requests/" + preReq, admin)).at("/header/status").asText()).isEqualTo("PAID");
        assertThat(payableQueryApi.getBalance(Long.valueOf(v))).isEqualByComparingTo("-3390");

        // 对账生成应付，确认
        long st = statement(v, m);
        String apId = apOfStatement(st).at("/header/id").asText();
        ok(doPost("/api/finance/payables/" + apId + "/confirm", admin, null));

        // FIN-AP-T03：发票单价高于应付 3%（容差 1%）→ 需填写原因；有差异
        JsonNode cands = ok(doGet("/api/finance/payable-lines/uninvoiced?supplierId=" + v, admin));
        String goodsLine = cands.get(0).at("/payableLineId").asText();
        assertError(doPost("/api/finance/purchase-invoices", admin, invoiceBody(v, "PI" + uniq(), "11639", "1339",
                List.of(invLine(goodsLine, "100", "103", null)))), "第 1 行单价差异 3% 超过容差，请填写差异原因");
        String inv = ok(doPost("/api/finance/purchase-invoices", admin, invoiceBody(v, "PI" + uniq(), "11639", "1339",
                List.of(invLine(goodsLine, "100", "103", "供应商涨价"))))).asText();
        JsonNode invd = ok(doGet("/api/finance/purchase-invoices/" + inv, admin));
        assertThat(invd.at("/header/matchStatus").asText()).isEqualTo("DIFF");
        assertThat(invd.at("/lines/0/overTolerance").asBoolean()).isTrue();
        // 差异确认 → 生成价差调整应付行 339（11639 − 11300）
        ok(doPost("/api/finance/purchase-invoices/" + inv + "/confirm-diff", admin, null));
        JsonNode ap = ap(apId);
        assertThat(ap.at("/header/totalAmount").decimalValue()).isEqualByComparingTo("11074");
        assertThat(ap.at("/lines/2/lineType").asText()).isEqualTo("PRICE_DIFF");
        assertThat(ap.at("/lines/2/totalAmount").decimalValue()).isEqualByComparingTo("339");
        assertThat(ok(doGet("/api/finance/purchase-invoices/" + inv, admin)).at("/header/matchStatus").asText()).isEqualTo("MATCHED");
        // 认证抵扣
        ok(doPost("/api/finance/purchase-invoices/" + inv + "/certify", admin, Map.of("period", LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")))));
        assertError(doPost("/api/finance/purchase-invoices/" + inv + "/void", admin, Map.of("reason", "x")), "当前状态【已认证】不能作废");

        // FIN-PAY-T03：预付冲应付
        JsonNode vc = ok(doGet("/api/finance/verifications/candidates?supplierId=" + v + "&currency=CNY", admin));
        assertThat(vc.at("/left/0/kind").asText()).isEqualTo("PREPAY");
        assertThat(vc.at("/left/0/available").decimalValue()).isEqualByComparingTo("3390");
        ok(doPost("/api/finance/verifications/prepay", admin, Map.of("partnerId", v, "currency", "CNY",
                "left", List.of(pick("PAYMENT", prePay, "3390")), "right", List.of(pick("PAYABLE", apId, "3390")))));
        ap = ap(apId);
        assertThat(ap.at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("3390");
        assertThat(ap.at("/verifications/0/verifyType").asText()).isEqualTo("PREPAY_AP");
        assertThat(payableQueryApi.getBalance(Long.valueOf(v))).isEqualByComparingTo("7684");
        // 预付已用于冲销 → 付款不能反确认
        assertError(doPost("/api/finance/payments/" + prePay + "/unconfirm", admin, Map.of("reason", "x")), "付款已用于核销，请先反核销");

        // 供应商对账单：期末 = 应付 11,074 − 付款 3,390
        JsonNode s = ok(doGet("/api/finance/reports/supplier-statement?supplierId=" + v, admin));
        assertThat(s.at("/closing").decimalValue()).isEqualByComparingTo("7684");
        assertThat(s.at("/mismatch").asBoolean()).isFalse();
    }
}
