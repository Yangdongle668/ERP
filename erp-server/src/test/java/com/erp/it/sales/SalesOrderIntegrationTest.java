package com.erp.it.sales;

import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderApi;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
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

/** 销售订单、回款计划、订单变更（需求 04-03、04-04、04-07 验收用例） */
class SalesOrderIntegrationTest extends SalesTestSupport {

    @Autowired
    SalesOrderApi salesOrderApi;
    @Autowired
    SalesOrderQueryApi queryApi;

    /** SO-T01 外销客户带出 USD、税率 0、付款条件、收货地址；T02 客户 PO 号唯一；T03 客户料号匹配物料 */
    @Test
    void defaultsPoAndCustomerPart() throws Exception {
        String cust = customer("ABC Trading", true, TERM_BL, null);
        JsonNode d = ok(doGet("/api/sales/orders/customer-defaults?customerId=" + cust, admin));
        assertThat(d.at("/currency").asText()).isEqualTo("USD");
        assertThat(d.at("/salesTaxRate").decimalValue()).isEqualByComparingTo("0");
        assertThat(d.at("/taxIncluded").asBoolean()).isFalse();
        assertThat(d.at("/paymentTermId").asText()).isEqualTo(TERM_BL);
        assertThat(blank(d.at("/shipToAddressId"))).isFalse();

        String fg1 = fg("FG1");
        ok(doPost("/api/crm/customer-parts", admin, Map.of("customerId", cust, "customerPartNo", "X-100-" + uniq().substring(0, 4), "materialId", fg1)));
        String partNo = ok(doGet("/api/crm/customer-parts?pageNo=1&pageSize=10&customerId=" + cust, admin)).at("/list/0/customerPartNo").asText();
        Map<String, Object> l = new HashMap<>();
        l.put("customerPartNo", partNo);
        l.put("qty", "100");
        l.put("price", "1.5");
        l.put("requiredDate", LocalDate.now().plusDays(10).toString());
        Map<String, Object> body = orderBody(cust, List.of(l));
        body.put("customerPoNo", "PO-123");
        body.put("exchangeRate", "7.1");
        String id = createOrder(body);
        JsonNode o = order(id);
        assertThat(o.at("/currency").asText()).isEqualTo("USD");
        assertThat(o.at("/taxIncluded").asBoolean()).isFalse();
        assertThat(o.at("/lines/0/materialId").asText()).isEqualTo(fg1);
        assertThat(o.at("/lines/0/customerPartNo").asText()).isEqualTo(partNo);
        assertThat(o.at("/lines/0/taxRate").decimalValue()).isEqualByComparingTo("0");
        assertThat(o.at("/totalAmount").decimalValue()).isEqualByComparingTo("150");
        assertThat(o.at("/totalAmountBase").decimalValue()).isEqualByComparingTo("1065");
        assertThat(o.at("/shipToText").asText()).contains("1 Main St");
        assertError(doPost("/api/sales/orders", admin, body), "客户 PO 号「PO-123」已存在于订单「" + o.at("/docNo").asText() + "」");
        Map<String, Object> bad = new HashMap<>(l);
        bad.put("customerPartNo", "NO-SUCH");
        Map<String, Object> badBody = orderBody(cust, List.of(bad));
        badBody.put("exchangeRate", "7.1");
        assertError(doPost("/api/sales/orders", admin, badBody), "客户料号「NO-SUCH」没有对照的物料，请先维护客户料号对照");
    }

    /**
     * SO-T05 审核：PMC 需求（SalesOrderApprovedEvent）、回款计划；T06 承诺交期晚于要求 → 交期风险 + 通知；T07 已有出货通知不能反审核；
     * T08 出货 600 → 执行中、出货进度 60%；T09 关闭剩余 400 → PMC 移除（SalesOrderClosedEvent）。
     */
    @Test
    void orderLifecycle() throws Exception {
        String cust = customer("Lifecycle");
        String fg1 = fg("FG1");
        LocalDate required = LocalDate.now().plusDays(20);
        String id = approvedOrder(cust, List.of(line(fg1, "1000", "11.3", required)));
        JsonNode o = order(id);
        assertThat(o.at("/taxIncluded").asBoolean()).isTrue();
        assertThat(o.at("/totalAmount").decimalValue()).isEqualByComparingTo("11300");
        assertThat(o.at("/amount").decimalValue()).isEqualByComparingTo("10000");
        Long lineId = lineId(id, 0);
        assertThat(ItSalesConfig.APPROVED.stream().filter(e -> e.getOrderId().toString().equals(id)).findFirst().orElseThrow().getLines().get(0).baseQty())
                .isEqualByComparingTo("1000");
        JsonNode plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin));
        assertThat(plans.at("/plans").size()).isEqualTo(1);
        assertThat(plans.at("/plans/0/planAmount").decimalValue()).isEqualByComparingTo("11300");
        assertThat(blank(plans.at("/plans/0/dueDate"))).isTrue();

        // T06：承诺交期晚于要求交期 5 天
        salesOrderApi.updatePromisedDate(lineId, required.plusDays(5), "物料紧张");
        o = order(id);
        assertThat(o.at("/deliveryRisk").asBoolean()).isTrue();
        assertThat(o.at("/riskLineCount").asInt()).isEqualTo(1);
        assertThat(o.at("/lines/0/delayed").asBoolean()).isTrue();
        assertThatThrownBy(() -> salesOrderApi.updatePromisedDate(lineId, LocalDate.now().minusDays(1), null)).hasMessage("承诺交期不能早于今天");
        String no = o.at("/docNo").asText();
        assertThat(ItSalesConfig.MESSAGES.stream().filter(m -> "订单交期风险".equals(m.getTitle()) && m.getContent().contains(no)).count()).isEqualTo(1);

        // T07：有出货通知不能反审核；生产订单引用也不能
        writebackApi.onNoticeChanged(lineId, new BigDecimal("600"), 1L, "SN-0001");
        assertThat(order(id).at("/status").asText()).isEqualTo("IN_PROGRESS");
        assertError(doPost("/api/sales/orders/" + id + "/unapprove", admin, Map.of("reason", "改单")), "订单当前状态为执行中，不能反审核");
        assertThatThrownBy(() -> salesOrderApi.validateShipmentQty(lineId, new BigDecimal("500")))
                .hasMessage("订单 " + o.at("/docNo").asText() + " 第 1 行可通知数量为 400，本次 500");

        // T08：出货 600
        ship(lineId, "600", null);
        o = order(id);
        assertThat(o.at("/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(o.at("/lines/0/shippedQty").decimalValue()).isEqualByComparingTo("600");
        assertThat(o.at("/lines/0/openQty").decimalValue()).isEqualByComparingTo("400");
        JsonNode row = ok(doGet("/api/sales/orders?pageNo=1&pageSize=10&customerId=" + cust, admin)).at("/list/0");
        assertThat(row.at("/shipProgress").decimalValue()).isEqualByComparingTo("0.6");
        plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans");
        assertThat(plans.size()).isEqualTo(2);
        JsonNode batch = plans.at("/1");
        assertThat(batch.at("/nodeName").asText()).isEqualTo("货款-第 1 批");
        assertThat(batch.at("/planAmount").decimalValue()).isEqualByComparingTo("6780");
        assertThat(batch.at("/dueDate").asText()).isEqualTo(LocalDate.now().plusDays(30).toString());
        assertThat(queryApi.getLine(lineId).orElseThrow().openQty()).isEqualByComparingTo("400");

        // T09：还有未出库的通知时不能关闭；通知全部出库后关闭
        writebackApi.onNoticeChanged(lineId, new BigDecimal("100"), 2L, "SN-0002");
        assertError(doPost("/api/sales/orders/" + id + "/close", admin, Map.of("reason", "客户取消")), "订单还有未完成的出货通知「SN-0002」");
        writebackApi.onNoticeChanged(lineId, new BigDecimal("-100"), 2L, "SN-0002");
        assertError(doPost("/api/sales/orders/" + id + "/close", admin, Map.of("reason", "")), "请填写关闭原因");
        ok(doPost("/api/sales/orders/" + id + "/close", admin, Map.of("reason", "剩余 400 不要了")));
        o = order(id);
        assertThat(o.at("/status").asText()).isEqualTo("CLOSED");
        assertThat(o.at("/lines/0/lineStatus").asText()).isEqualTo("CLOSED");
        assertThat(ItSalesConfig.CLOSED.stream().anyMatch(e -> e.getOrderId().toString().equals(id))).isTrue();
        assertThat(queryApi.getOpenLines(new OpenLineFilter(Long.valueOf(cust), null, null, null, null))).isEmpty();
        plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans");
        assertThat(plans.at("/0/planStatus").asText()).isEqualTo("CANCELED");
    }

    /** SO-T07 生产订单引用时不能反审核；无引用时反审核回到草稿并删除回款计划 */
    @Test
    void unapprove() throws Exception {
        String cust = customer("Unapprove");
        String id = approvedOrder(cust, List.of(line(fg("FG"), "10", "5", null)));
        ItSalesConfig.REFERENCED.add(Long.valueOf(id));
        assertError(doPost("/api/sales/orders/" + id + "/unapprove", admin, Map.of("reason", "改单")),
                "订单已有出货通知/收款/生产订单，不能反审核，请使用订单变更");
        ItSalesConfig.REFERENCED.remove(Long.valueOf(id));
        ok(doPost("/api/sales/orders/" + id + "/unapprove", admin, Map.of("reason", "改单")));
        assertThat(order(id).at("/status").asText()).isEqualTo("DRAFT");
        assertThat(ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans").size()).isZero();
        // 草稿可以修改、复制、作废
        JsonNode copy = ok(doPost("/api/sales/orders/" + id + "/copy", admin, null));
        assertThat(order(copy.at("/id").asText()).at("/status").asText()).isEqualTo("DRAFT");
        ok(doPost("/api/sales/orders/" + id + "/void", admin, Map.of("reason", "重复")));
        assertThat(order(id).at("/status").asText()).isEqualTo("VOIDED");
    }

    /** SO-T04 信用警告需确认，确认后 credit_warning = 1；BLOCK 阻止；补货订单单价 0、不检查信用 */
    @Test
    void creditCheck() throws Exception {
        String cust = customer("Credit");
        ok(doPost("/api/crm/credit-changes", admin, Map.of("customerId", cust, "newLimit", "1000", "newControl", "WARN", "reason", "年度评审")));
        String fg1 = fg("FG");
        String id = createOrder(orderBody(cust, List.of(line(fg1, "100", "20", null))));
        JsonNode r = doPost("/api/sales/orders/" + id + "/submit", admin, Map.of());
        assertThat(r.at("/code").asInt()).isNotZero();
        assertThat(r.at("/data/needConfirm").asBoolean()).isTrue();
        assertThat(r.at("/msg").asText()).contains("信用额度不足");
        assertThat(ok(doPost("/api/sales/orders/" + id + "/submit", admin, Map.of("confirmCredit", true))).at("/status").asText()).isEqualTo("APPROVED");
        assertThat(order(id).at("/creditWarning").asBoolean()).isTrue();
        // 占用刷新为 2000
        JsonNode credit = ok(doGet("/api/crm/credits?pageNo=1&pageSize=10&customerId=" + cust, admin)).at("/list/0");
        assertThat(credit.at("/openOrderAmount").decimalValue()).isEqualByComparingTo("2000");

        ok(doPost("/api/crm/credit-changes", admin, Map.of("customerId", cust, "newLimit", "1000", "newControl", "BLOCK", "reason", "收紧")));
        String blocked = createOrder(orderBody(cust, List.of(line(fg1, "10", "20", null))));
        assertThat(doPost("/api/sales/orders/" + blocked + "/submit", admin, Map.of("confirmCredit", true)).at("/msg").asText()).contains("信用额度不足");

        Map<String, Object> repl = orderBody(cust, List.of(line(fg1, "10", "20", null)));
        repl.put("orderType", "REPLACEMENT");
        String rid = createOrder(repl);
        assertThat(order(rid).at("/totalAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(ok(doPost("/api/sales/orders/" + rid + "/submit", admin, Map.of())).at("/status").asText()).isEqualTo("APPROVED");
        assertThat(ok(doGet("/api/sales/orders/" + rid + "/payment-plans", admin)).at("/plans").size()).isZero();
    }

    /** R04 MOQ 警告；毛利与底价（字段权限） */
    @Test
    void moqAndMargin() throws Exception {
        String cust = customer("Margin");
        String fg1 = fg("FG", Map.of("moq", "500", "mpq", "100"));
        ItSalesConfig.COSTS.put(Long.valueOf(fg1), new BigDecimal("9"));
        JsonNode r = ok(doPost("/api/sales/orders", admin, orderBody(cust, List.of(line(fg1, "250", "11.3", null)))));
        assertThat(r.at("/warnings").toString()).contains("数量低于最小订购量 500").contains("不是最小包装量 100 的整数倍");
        JsonNode o = order(r.at("/id").asText());
        // 不含税 10，成本 9 → 毛利 10%，低于底价 9 × 1.15
        assertThat(o.at("/lines/0/marginRate").decimalValue()).isEqualByComparingTo("0.1");
        assertThat(o.at("/belowFloor").asBoolean()).isTrue();
        assertThat(o.at("/costVisible").asBoolean()).isTrue();
    }

    /** PP-T01～T05：TT30-70BL 订单 10,000 USD → 定金 3,000；收款 3,000；出货 40% → 尾款第 1 批 2,800；提单日期；逾期 */
    @Test
    void paymentPlans() throws Exception {
        String cust = customer("Plan", true, TERM_BL, null);
        String fg1 = fg("FG");
        Map<String, Object> body = orderBody(cust, List.of(line(fg1, "1000", "10", null)));
        body.put("exchangeRate", "7");
        String id = createOrder(body);
        assertThat(ok(doPost("/api/sales/orders/" + id + "/submit", admin, Map.of())).at("/status").asText()).isEqualTo("APPROVED");
        JsonNode plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans");
        assertThat(plans.size()).isEqualTo(2);
        assertThat(plans.at("/0/nodeName").asText()).isEqualTo("定金");
        assertThat(plans.at("/0/planAmount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(plans.at("/0/dueDate").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(plans.at("/1/planAmount").decimalValue()).isEqualByComparingTo("7000");

        writebackApi.onReceiptAllocated(Long.valueOf(id), new BigDecimal("3000"), LocalDate.now());
        plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans");
        assertThat(plans.at("/0/planStatus").asText()).isEqualTo("RECEIVED");

        Long shipment = ship(lineId(id, 0), "400", LocalDate.now());
        plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans");
        assertThat(plans.size()).isEqualTo(3);
        JsonNode b1 = plans.at("/2");
        assertThat(b1.at("/nodeName").asText()).isEqualTo("尾款-第 1 批");
        assertThat(b1.at("/planAmount").decimalValue()).isEqualByComparingTo("2800");
        assertThat(blank(b1.at("/dueDate"))).isTrue();
        assertThat(plans.at("/1/planAmount").decimalValue()).isEqualByComparingTo("4200");

        LocalDate bl = LocalDate.now().minusDays(2);
        writebackApi.onBillOfLading(shipment, bl);
        ok(doGet("/api/sales/payment-plans/summary", admin));
        JsonNode rows = ok(doGet("/api/sales/payment-plans?pageNo=1&pageSize=20&customerId=" + cust, admin)).at("/list");
        JsonNode batchRow = null;
        for (JsonNode x : rows) if (x.at("/batchNo").asInt() == 1) batchRow = x;
        assertThat(batchRow).isNotNull();
        assertThat(batchRow.at("/dueDate").asText()).isEqualTo(bl.toString());
        // 重算状态：到期日已过 → 逾期
        paymentStatusJob();
        rows = ok(doGet("/api/sales/payment-plans?pageNo=1&pageSize=20&overdueOnly=true&customerId=" + cust, admin)).at("/list");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.at("/0/overdueDays").asInt()).isEqualTo(2);
        ok(doPost("/api/sales/payment-plans/" + rows.at("/0/id").asText() + "/follow-up", admin, Map.of("remark", "客户承诺下周付", "promisedPayDate",
                LocalDate.now().plusDays(7).toString())));
        assertThat(ok(doGet("/api/sales/payment-plans?pageNo=1&pageSize=20&overdueOnly=true&customerId=" + cust, admin)).at("/list/0/remark").asText())
                .isEqualTo("客户承诺下周付");
        // 出货冲销：批次计划退回节点余额
        writebackApi.onShipmentReversed(shipment);
        plans = ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans");
        assertThat(plans.size()).isEqualTo(2);
        assertThat(plans.at("/1/planAmount").decimalValue()).isEqualByComparingTo("7000");
        assertThat(order(id).at("/lines/0/shippedQty").decimalValue()).isEqualByComparingTo("0");
    }

    @Autowired
    com.erp.module.sales.service.order.PaymentPlanService paymentPlanService;

    private void paymentStatusJob() {
        paymentPlanService.refreshStatuses();
    }

    /** SC-T01 变更 1000 → 800 并推后交期（已通知 300）→ V2、快照；T02 小于已通知；T03 已有未完成变更单；T05 版本冲突 */
    @Test
    void orderChange() throws Exception {
        String cust = customer("Change");
        String fg1 = fg("FG");
        LocalDate required = LocalDate.now().plusDays(20);
        String id = approvedOrder(cust, List.of(line(fg1, "1000", "10", required)));
        Long lineId = lineId(id, 0);
        writebackApi.onNoticeChanged(lineId, new BigDecimal("300"), 11L, "SN-11");

        String chg = ok(doPost("/api/sales/order-changes?orderId=" + id, admin, null)).asText();
        assertError(doPost("/api/sales/order-changes?orderId=" + id, admin, null), "订单已有未完成的变更单「" + detailNo(chg) + "」");
        Map<String, Object> modify = new HashMap<>();
        modify.put("changeType", "MODIFY");
        modify.put("orderLineId", lineId.toString());
        modify.put("newQty", "200");
        assertError(doPut("/api/sales/order-changes/" + chg, admin, Map.of("changeReason", "CUSTOMER", "reasonRemark", "客户减量",
                "lines", List.of(modify))), "第 1 行新数量不能小于已通知出货数量 300");
        modify.put("newQty", "800");
        modify.put("newRequiredDate", required.plusDays(10).toString());
        Map<String, Object> add = new HashMap<>();
        add.put("changeType", "ADD");
        add.put("materialId", fg("FG2"));
        add.put("newQty", "50");
        add.put("newPrice", "2");
        add.put("newRequiredDate", required.toString());
        ok(doPut("/api/sales/order-changes/" + chg, admin, Map.of("changeReason", "CUSTOMER", "reasonRemark", "客户减量",
                "header", Map.of("customerPoNo", "PO-NEW", "paymentTermId", TERM_NET30, "shipToAddressId", order(id).at("/shipToAddressId").asText(),
                        "contactId", order(id).at("/contactId").asText()), "lines", List.of(modify, add))));
        JsonNode c = ok(doGet("/api/sales/order-changes/" + chg, admin));
        assertThat(c.at("/amountBefore").decimalValue()).isEqualByComparingTo("10000");
        assertThat(c.at("/amountAfter").decimalValue()).isEqualByComparingTo("8100");
        assertThat(c.at("/headerChanges/0/field").asText()).isEqualTo("customerPoNo");
        assertThat(ok(doPost("/api/sales/order-changes/" + chg + "/submit", admin, Map.of())).at("/status").asText()).isEqualTo("APPROVED");

        JsonNode o = order(id);
        assertThat(o.at("/orderVersion").asInt()).isEqualTo(2);
        assertThat(o.at("/customerPoNo").asText()).isEqualTo("PO-NEW");
        assertThat(o.at("/lines/0/qty").decimalValue()).isEqualByComparingTo("800");
        assertThat(o.at("/lines/0/requiredDate").asText()).isEqualTo(required.plusDays(10).toString());
        assertThat(o.at("/lines").size()).isEqualTo(2);
        assertThat(o.at("/totalAmount").decimalValue()).isEqualByComparingTo("8100");
        JsonNode snaps = ok(doGet("/api/sales/orders/" + id + "/snapshots", admin));
        assertThat(snaps.size()).isEqualTo(1);
        assertThat(snaps.at("/0/orderVersion").asInt()).isEqualTo(1);
        assertThat(snaps.at("/0/content").asText()).contains("\"qty\":1000");
        assertThat(ItSalesConfig.CHANGED.stream().filter(e -> e.getOrderId().toString().equals(id)).findFirst().orElseThrow().getChanges().get(0).decreased())
                .isTrue();
        assertThat(ok(doGet("/api/sales/orders/" + id + "/payment-plans", admin)).at("/plans/0/planAmount").decimalValue()).isEqualByComparingTo("8100");

        // T05：变更单待审批期间订单被修改（模拟版本变化）
        String chg2 = ok(doPost("/api/sales/order-changes?orderId=" + id, admin, null)).asText();
        Map<String, Object> cancel = Map.of("changeType", "CANCEL", "orderLineId", o.at("/lines/1/id").asText());
        ok(doPut("/api/sales/order-changes/" + chg2, admin, Map.of("changeReason", "INTERNAL", "reasonRemark", "取消", "lines", List.of(cancel))));
        jdbc.update("UPDATE sal_order SET order_version = order_version + 1 WHERE id = ?", Long.valueOf(id));
        assertError(doPost("/api/sales/order-changes/" + chg2 + "/submit", admin, Map.of()), "订单已被修改，请作废本变更单后重新发起");
        ok(doPost("/api/sales/order-changes/" + chg2 + "/void", admin, Map.of("reason", "重新发起")));
        // 取消已通知的行不允许
        String chg3 = ok(doPost("/api/sales/order-changes?orderId=" + id, admin, null)).asText();
        assertError(doPut("/api/sales/order-changes/" + chg3, admin, Map.of("changeReason", "INTERNAL", "reasonRemark", "取消",
                "lines", List.of(Map.of("changeType", "CANCEL", "orderLineId", lineId.toString())))), "第 1 行已有出货通知，不能取消");
    }

    private String detailNo(String changeId) throws Exception {
        return ok(doGet("/api/sales/order-changes/" + changeId, admin)).at("/docNo").asText();
    }

    /** 导出、执行情况、打印数据、出货选单 */
    @Test
    void exportPrintAndOpenLines() throws Exception {
        String cust = customer("Print");
        String id = approvedOrder(cust, List.of(line(fg("FG"), "10", "5", null)));
        ship(lineId(id, 0), "4", null);
        JsonNode exec = ok(doGet("/api/sales/orders/" + id + "/execution", admin));
        assertThat(exec.size()).isEqualTo(1);
        assertThat(exec.at("/0/execType").asText()).isEqualTo("SHIP");
        JsonNode print = ok(doGet("/api/sales/orders/" + id + "/print-data?template=PI", admin));
        assertThat(print.at("/lines/0/qty").decimalValue()).isEqualByComparingTo("10");
        assertThat(print.at("/taxIncludedText").asText()).isEqualTo("Tax included");
        JsonNode open = ok(doGet("/api/sales/order-lines/open?customerId=" + cust, admin));
        assertThat(open.at("/total").asInt()).isEqualTo(1);
        assertThat(open.at("/list/0/openQty").decimalValue()).isEqualByComparingTo("6");
        var export = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/sales/orders/export?level=LINE&customerId=" + cust).header("Authorization", admin)).andReturn().getResponse();
        assertThat(export.getStatus()).isEqualTo(200);
        assertThat(export.getContentAsByteArray().length).isGreaterThan(100);
    }
}
