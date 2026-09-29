package com.erp.it.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.finance.api.query.ReceivableQueryApi;
import com.erp.module.finance.dal.dataobject.FinPeriodDO;
import com.erp.module.finance.dal.mapper.FinPeriodMapper;
import com.erp.module.quality.api.complaint.ComplaintClaimAgreedEvent;
import com.erp.module.sales.api.returns.SalesReturnReceivedEvent;
import com.erp.module.shipping.api.shipment.ShipmentConfirmedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 应收、收款与核销、销项发票（12-02、12-03） */
class FinanceArIntegrationTest extends FinanceTestSupport {

    @Autowired
    ReceivableQueryApi receivableQueryApi;

    /** FIN-AR-T01 / T02 / T04，FIN-RV-T01 / T04 / T05 */
    @Test
    void shipmentReceivableReceiptAndVerification() throws Exception {
        String m = fg("外销成品", Map.of());
        stock(m, "1000", null);
        String c = customer("美国客户", true);
        String order = approvedOrder(c, List.of(orderLine(m, "400", "10", null)));
        String s = shipOrder(c, order, 0, "400");

        // FIN-AR-T01：出库确认 → 应收 4,000 USD（自动确认），本位币按出货汇率；到期日 = 出货日 + 30（NET30）
        JsonNode ar = arOfShipment(s);
        String arId = ar.at("/header/id").asText();
        assertThat(ar.at("/header/arType").asText()).isEqualTo("SALES");
        assertThat(ar.at("/header/status").asText()).isEqualTo("CONFIRMED");
        assertThat(ar.at("/header/currency").asText()).isEqualTo("USD");
        assertThat(ar.at("/header/totalAmount").decimalValue()).isEqualByComparingTo("4000");
        BigDecimal rate = ar.at("/header/exchangeRate").decimalValue();
        assertThat(ar.at("/header/totalAmountBase").decimalValue()).isEqualByComparingTo(rate.multiply(new BigDecimal("4000")));
        assertThat(ar.at("/header/dueDate").asText()).isEqualTo(LocalDate.now().plusDays(30).toString());
        assertThat(ar.at("/lines").size()).isEqualTo(1);
        assertThat(receivableQueryApi.getBalance(Long.valueOf(c))).isEqualByComparingTo(rate.multiply(new BigDecimal("4000")));

        // FIN-AR-T02：同一出货事件重复投递不重复生成
        events.publish(new ShipmentConfirmedEvent(Long.valueOf(s), "DUP", Long.valueOf(c), "USD", rate, LocalDate.now(), new BigDecimal("4000"),
                BigDecimal.ZERO, List.of(new ShipmentConfirmedEvent.Line(1L, Long.valueOf(order), orderLineId(order, 0), Long.valueOf(m), null,
                new BigDecimal("400"), BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("4000")))));
        arOfShipment(s);

        // 收款：到账 3,975 + 手续费 25，汇率 7.123；币别跟随收款账户
        String rv = receipt(c, "SALES", bankUsd, "3975", "25", "7.123", null);
        JsonNode rvd = ok(doGet("/api/finance/receipts/" + rv, admin));
        assertThat(rvd.at("/header/currency").asText()).isEqualTo("USD");
        assertThat(rvd.at("/header/unallocatedAmount").decimalValue()).isEqualByComparingTo("4000");
        // 未确认的收款不能核销
        assertError(verifyReceipt(c, "USD", List.of(pick("RECEIPT", rv, "4000")), List.of(pick("RECEIVABLE", arId, "4000"))), "已确认的收款单不存在");
        ok(doPost("/api/finance/receipts/" + rv + "/confirm", admin, null));

        // 核销候选：左侧收款、右侧应收
        JsonNode cand = ok(doGet("/api/finance/verifications/candidates?customerId=" + c + "&currency=USD", admin));
        assertThat(cand.at("/left/0/docNo").asText()).isEqualTo(rvd.at("/header/docNo").asText());
        assertThat(cand.at("/right/0/available").decimalValue()).isEqualByComparingTo("4000");
        // 自动核销建议
        JsonNode auto = ok(doPost("/api/finance/verifications/auto", admin, Map.of("partnerId", c, "currency", "USD",
                "left", List.of(pick("RECEIPT", rv, "4000")))));
        assertThat(auto.at("/0/amount").decimalValue()).isEqualByComparingTo("4000");

        // FIN-RV-T04：两侧不平衡
        assertError(verifyReceipt(c, "USD", List.of(pick("RECEIPT", rv, "4000")), List.of(pick("RECEIVABLE", arId, "3000"))), "核销金额不平衡");
        assertError(verifyReceipt(c, "USD", List.of(pick("RECEIPT", rv, "4001")), List.of(pick("RECEIVABLE", arId, "4001"))),
                "「" + rvd.at("/header/docNo").asText() + "」本次核销金额超过未核销金额 4000.00");

        // FIN-RV-T01：核销 4,000 → 汇兑差异 = 4000 × 7.123 − 4000 × 出货汇率
        JsonNode vr = ok(verifyReceipt(c, "USD", List.of(pick("RECEIPT", rv, "4000")), List.of(pick("RECEIVABLE", arId, "4000"))));
        BigDecimal fx = new BigDecimal("4000").multiply(new BigDecimal("7.123")).subtract(new BigDecimal("4000").multiply(rate));
        assertThat(vr.at("/fxDiff").decimalValue()).isEqualByComparingTo(fx);
        ar = ar(arId);
        assertThat(ar.at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("4000");
        assertThat(ar.at("/header/unverifiedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(ar.at("/verifications/0/verifyType").asText()).isEqualTo("RECEIPT_AR");
        assertThat(receivableQueryApi.getOrderReceived(Long.valueOf(order))).isEqualByComparingTo("4000");
        assertThat(receivableQueryApi.getBalance(Long.valueOf(c))).isEqualByComparingTo("0");

        // FIN-RV-T05：已核销的收款不能反确认
        assertError(doPost("/api/finance/receipts/" + rv + "/unconfirm", admin, Map.of("reason", "录错")), "收款已核销，请先反核销");
        // FIN-AR-T04：应收已核销 → 仓库不能反确认出库
        String stockOut = shipment(s).at("/stockOutId").asText();
        assertError(doPost("/api/inventory/stock-outs/" + stockOut + "/unconfirm", admin, Map.of("reason", "发错货")), "该出货已开票/已收款核销，不能反确认");

        // 反核销 → 双方恢复；回款冲回
        String vId = ar.at("/verifications/0/id").asText();
        ok(doPost("/api/finance/verifications/" + vId + "/reverse", admin, null));
        assertThat(ar(arId).at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(ar(arId).at("/verifications/0/reversed").asBoolean()).isTrue();
        assertThat(receivableQueryApi.getOrderReceived(Long.valueOf(order))).isEqualByComparingTo("0");
        assertError(doPost("/api/finance/verifications/" + vId + "/reverse", admin, null), "核销记录不存在");
        ok(doPost("/api/finance/receipts/" + rv + "/unconfirm", admin, Map.of("reason", "录错")));

        // FIN-AR-R02：未处理的应收在出库反确认后作废
        ok(doPost("/api/inventory/stock-outs/" + stockOut + "/unconfirm", admin, Map.of("reason", "发错货")));
        assertThat(ar(arId).at("/header/status").asText()).isEqualTo("VOIDED");
    }

    /** FIN-RV-T02 / T03：预收款回写回款计划，出货后预收 + 新收款冲销应收，回款只增加新收款 */
    @Test
    void advanceReceiptAndInvoice() throws Exception {
        String m = fg("内销成品", Map.of());
        stock(m, "1000", null);
        String c = customer("内销客户", false);
        String order = approvedOrder(c, List.of(orderLine(m, "700", "10", null)));
        String other = approvedOrder(c, List.of(orderLine(m, "10", "10", null)));

        // 预收款必须选择订单
        assertError(doPost("/api/finance/receipts", admin, Map.of("customerId", c, "receiptType", "ADVANCE", "bankAccountId", bankCny,
                "settlementMethod", "TT", "receiptDate", LocalDate.now().toString(), "amount", "3000")), "预收款请选择销售订单");
        JsonNode opts = ok(doGet("/api/finance/receipts/order-options?customerId=" + c, admin));
        assertThat(opts.size()).isEqualTo(2);
        String adv = confirmedReceipt(c, "ADVANCE", bankCny, "3000", "0", null, order);
        assertThat(receivableQueryApi.getOrderReceived(Long.valueOf(order))).isEqualByComparingTo("3000");
        // 未核销预收冲减应收余额
        assertThat(receivableQueryApi.getBalance(Long.valueOf(c))).isEqualByComparingTo("-3000");

        String s = shipOrder(c, order, 0, "700");
        JsonNode ar = arOfShipment(s);
        String arId = ar.at("/header/id").asText();
        assertThat(ar.at("/header/totalAmount").decimalValue()).isEqualByComparingTo("7000");
        assertThat(ar.at("/lines/0/taxAmount").decimalValue()).isEqualByComparingTo("805.31");

        // 预收只能冲该订单的应收：其他订单的预收不能冲
        String adv2 = confirmedReceipt(c, "ADVANCE", bankCny, "50", "0", null, other);
        assertError(verifyReceipt(c, "CNY", List.of(pick("RECEIPT", adv2, "50")), List.of(pick("RECEIVABLE", arId, "50"))),
                "预收款只能核销订单「" + ok(doGet("/api/sales/orders/" + other, admin)).at("/docNo").asText() + "」产生的应收");

        // FIN-RV-T03：预收 3,000 + 新收款 4,000 核销应收 7,000
        String rv = confirmedReceipt(c, "SALES", bankCny, "4000", "0", null, null);
        JsonNode vr = ok(verifyReceipt(c, "CNY", List.of(pick("RECEIPT", adv, "3000"), pick("RECEIPT", rv, "4000")), List.of(pick("RECEIVABLE", arId, "7000"))));
        assertThat(vr.at("/count").asInt()).isEqualTo(2);
        assertThat(vr.at("/fxDiff").decimalValue()).isEqualByComparingTo("0");
        assertThat(ar(arId).at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("7000");
        assertThat(receivableQueryApi.getOrderReceived(Long.valueOf(order))).isEqualByComparingTo("7000");
        JsonNode vs = ar(arId).at("/verifications");
        assertThat(List.of(vs.get(0).at("/verifyType").asText(), vs.get(1).at("/verifyType").asText())).containsExactlyInAnyOrder("ADVANCE_AR", "RECEIPT_AR");

        // 开票登记（FIN-AR-T05 / R08）：部分开票 → 应收已开票增加、订单已开票数量回写
        JsonNode lines = ok(doGet("/api/finance/sales-invoices/uninvoiced-lines?customerId=" + c, admin));
        assertThat(lines.size()).isEqualTo(1);
        String arLine = lines.get(0).at("/receivableLineId").asText();
        String invNo = "INV" + uniq();
        assertError(doPost("/api/finance/sales-invoices", admin, invoice(c, invNo, arLine, "701")), "第 1 行开票数量超过未开票数量 700");
        String inv = ok(doPost("/api/finance/sales-invoices", admin, invoice(c, invNo, arLine, "300"))).asText();
        assertThat(ar(arId).at("/header/invoicedAmount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(orderLineDto(orderLineId(order, 0)).invoicedQty()).isEqualByComparingTo("300");
        assertError(doPost("/api/finance/sales-invoices", admin, invoice(c, invNo, arLine, "100")), "发票号码「" + invNo + "」已登记");
        JsonNode invd = ok(doGet("/api/finance/sales-invoices/" + inv, admin));
        assertThat(invd.at("/header/totalAmount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(invd.at("/lines/0/receivableNo").asText()).isEqualTo(ar.at("/header/docNo").asText());
        // 已开票 → 不能反确认
        assertError(doPost("/api/finance/receivables/" + arId + "/unconfirm", admin, Map.of("reason", "x")), "应收已核销或已开票，不能反确认");
        // 作废发票 → 回退
        ok(doPost("/api/finance/sales-invoices/" + inv + "/void", admin, Map.of("reason", "开错")));
        assertThat(ar(arId).at("/header/invoicedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(orderLineDto(orderLineId(order, 0)).invoicedQty()).isEqualByComparingTo("0");
    }

    private static Map<String, Object> invoice(String customerId, String invoiceNo, String arLineId, String qty) {
        return Map.of("customerId", customerId, "invoiceType", "VAT_SPECIAL", "invoiceNo", invoiceNo, "invoiceDate", LocalDate.now().toString(),
                "lines", List.of(Map.of("receivableLineId", arLineId, "qty", qty)));
    }

    /** FIN-AR-T03 退货红字、FIN-AR-R04 客诉折让、红蓝对冲、退款、其他应收与期间结账 */
    @Test
    void redReceivablesAndOther() throws Exception {
        String c = customer("红字客户", true);
        Long cid = Long.valueOf(c);
        String blue = otherAr(c, "USD", LocalDate.now(), LocalDate.now().plusDays(10), "2000");
        assertThat(ar(blue).at("/header/arType").asText()).isEqualTo("OTHER");

        // FIN-AR-T03：退货入库（退款）50 × 10 USD → 红字 −500；重复投递幂等；入库反确认 → 作废
        SalesReturnReceivedEvent.Line rl = new SalesReturnReceivedEvent.Line(1L, null, null, null, new BigDecimal("50"), BigDecimal.TEN, BigDecimal.ZERO,
                new BigDecimal("500"));
        long returnId = System.nanoTime();
        events.publish(new SalesReturnReceivedEvent(returnId, "SR-IT", cid, "REFUND", "USD", new BigDecimal("7.1"), 88L, false, List.of(rl)));
        events.publish(new SalesReturnReceivedEvent(returnId, "SR-IT", cid, "REFUND", "USD", new BigDecimal("7.1"), 88L, false, List.of(rl)));
        JsonNode reds = ok(doGet("/api/finance/receivables?customerId=" + c + "&arTypes=SALES_RETURN&statuses=CONFIRMED", admin)).at("/list");
        assertThat(reds.size()).isEqualTo(1);
        assertThat(reds.get(0).at("/totalAmount").decimalValue()).isEqualByComparingTo("-500");
        events.publish(new SalesReturnReceivedEvent(returnId, "SR-IT", cid, "REFUND", "USD", new BigDecimal("7.1"), 88L, true, List.of(rl)));
        assertThat(ar(reds.get(0).at("/id").asText()).at("/header/status").asText()).isEqualTo("VOIDED");
        // 换货不生成应收
        events.publish(new SalesReturnReceivedEvent(returnId + 1, "SR-IT2", cid, "REPLACE", "USD", new BigDecimal("7.1"), 89L, false, List.of(rl)));
        assertThat(ok(doGet("/api/finance/receivables?customerId=" + c + "&arTypes=SALES_RETURN&statuses=CONFIRMED", admin)).at("/total").asInt()).isZero();

        // 客诉同意赔偿 300 → 折让红字，冲销蓝字应收
        events.publish(new ComplaintClaimAgreedEvent(System.nanoTime(), "CPL-IT", cid, "USD", new BigDecimal("300"), "CREDIT"));
        JsonNode disc = ok(doGet("/api/finance/receivables?customerId=" + c + "&arTypes=DISCOUNT", admin)).at("/list/0");
        assertThat(disc.at("/totalAmount").decimalValue()).isEqualByComparingTo("-300");
        String discId = disc.at("/id").asText();
        JsonNode cand = ok(doGet("/api/finance/verifications/candidates?customerId=" + c + "&currency=USD", admin));
        assertThat(cand.at("/left/0/kind").asText()).isEqualTo("RED");
        assertThat(cand.at("/left/0/available").decimalValue()).isEqualByComparingTo("-300");
        ok(verifyReceipt(c, "USD", List.of(pick("RECEIVABLE", discId, "300")), List.of(pick("RECEIVABLE", blue, "300"))));
        assertThat(ar(blue).at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("300");
        assertThat(ar(discId).at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("-300");
        assertThat(ar(blue).at("/verifications/0/verifyType").asText()).isEqualTo("RED_BLUE_AR");

        // FIN-RV-R07：退款只能核销红字应收（或预收）
        events.publish(new ComplaintClaimAgreedEvent(System.nanoTime(), "CPL-IT2", cid, "USD", new BigDecimal("100"), "CREDIT"));
        String disc2 = ok(doGet("/api/finance/receivables?customerId=" + c + "&arTypes=DISCOUNT&verifyState=NONE", admin)).at("/list/0/id").asText();
        String refund = confirmedReceipt(c, "REFUND", bankUsd, "100", "0", "7.1", null);
        assertThat(ok(doGet("/api/finance/receipts/" + refund, admin)).at("/header/amount").decimalValue()).isEqualByComparingTo("-100");
        assertError(verifyReceipt(c, "USD", List.of(pick("RECEIPT", refund, "100")), List.of(pick("RECEIVABLE", blue, "100"))), "核销金额不平衡");
        ok(verifyReceipt(c, "USD", List.of(pick("RECEIPT", refund, "100"), pick("RECEIVABLE", disc2, "100")), List.of()));
        assertThat(ar(disc2).at("/header/verifiedAmount").decimalValue()).isEqualByComparingTo("-100");

        // 应收单列表：仅逾期、合计
        String overdue = otherAr(c, "USD", LocalDate.now().minusDays(40), LocalDate.now().minusDays(35), "100");
        JsonNode od = ok(doGet("/api/finance/receivables?customerId=" + c + "&overdueOnly=true", admin));
        assertThat(od.at("/total").asInt()).isEqualTo(1);
        assertThat(od.at("/list/0/id").asText()).isEqualTo(overdue);
        assertThat(od.at("/list/0/overdueDays").asInt()).isEqualTo(35);
        assertThat(receivableQueryApi.getOverdue(cid)).isEqualByComparingTo(ar(overdue).at("/header/totalAmountBase").decimalValue());

        // 反确认（未处理）→ 草稿 → 作废
        ok(doPost("/api/finance/receivables/" + overdue + "/unconfirm", admin, Map.of("reason", "重复")));
        ok(doPost("/api/finance/receivables/" + overdue + "/void", admin, Map.of("reason", "重复")));
        assertThat(ar(overdue).at("/header/status").asText()).isEqualTo("VOIDED");

        // FIN-AR-R05：业务日期所在期间已结账
        closedPeriodTest(c);
    }

    private void closedPeriodTest(String c) throws Exception {
        doPost("/api/finance/periods/init-year", admin, Map.of("year", 2001));
        JsonNode periods = ok(doGet("/api/finance/periods?year=2001", admin));
        assertThat(periods.size()).isEqualTo(12);
        closePeriod("200101");
        Map<String, Object> body = Map.of("customerId", c, "currency", "USD", "bizDate", "2001-01-15", "description", "旧账",
                "lines", List.of(Map.of("description", "旧账", "totalAmount", "10")));
        String id = ok(doPost("/api/finance/receivables/other", admin, body)).asText();
        assertError(doPost("/api/finance/receivables/" + id + "/submit", admin, null), "会计期间 200101 已结账");
    }

    @Autowired
    FinPeriodMapper periodMapper;

    /** 月结功能在第 2 批实现，这里直接改期间状态 */
    private void closePeriod(String period) {
        FinPeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, period));
        p.setPeriodStatus("CLOSED");
        periodMapper.updateByIdOrFail(p);
    }
}
