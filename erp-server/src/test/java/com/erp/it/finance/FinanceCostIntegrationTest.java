package com.erp.it.finance;

import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 成本核算（12-07）：本期库存月结后按月加权平均计算材料成本、归集订单成本、分配人工制费、回填出库成本。
 * 测试期间临时月结本期库存，结束后解锁成本、反结账库存，不影响其他用例。
 */
class FinanceCostIntegrationTest extends FinanceTestSupport {

    static final String PERIOD = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    static final String W_RAW = "801";
    static final String CAT_RAW = "501";
    static final String W_FG = "803";
    private static final AtomicLong SOURCE_ID = new AtomicLong(9_700_000);

    @Autowired
    JdbcTemplate jdbc;

    /** FIN-CST-T01 ~ T04 */
    @Test
    void monthlyWeightedCost() throws Exception {
        String dept = dept();
        String screw = material("螺丝", CAT_RAW, "RAW", Map.of());
        String pcba = material("PCBA", CAT_RAW, "RAW", Map.of());
        String fg = fg("成本成品", Map.of());
        // T01：螺丝 1000 × 0.10 + 3000 × 0.12 → 加权单价 0.115
        purchaseIn(screw, "1000", "0.10");
        purchaseIn(screw, "3000", "0.12");
        purchaseIn(pcba, "100", "20");
        // T02：订单 100，领螺丝 400、PCBA 100；报工 8 小时；完工 100
        bom(fg, List.of(bomLine(screw, 4), bomLine(pcba, 1)));
        routing(fg, workCenter());
        Map<String, Object> o = new HashMap<>();
        o.put("materialId", fg);
        o.put("qty", "100");
        o.put("deptId", dept);
        o.put("planStart", LocalDate.now().toString());
        o.put("planEnd", LocalDate.now().plusDays(7).toString());
        String mo = ok(doPost("/api/production/prod-orders", admin, o)).at("/id").asText();
        ok(doPost("/api/production/prod-orders/" + mo + "/submit", admin, null));
        ok(doPost("/api/production/prod-orders/" + mo + "/release", admin, Map.of("confirmShortage", true)));
        for (String issue : issueAll(mo)) confirmIssue(issue);
        Map<String, Object> rep = new HashMap<>();
        rep.put("prodOrderId", mo);
        rep.put("operationSeq", 10);
        rep.put("goodQty", 100);
        rep.put("defectQty", 0);
        rep.put("scrapQty", 0);
        rep.put("workHours", 8);
        rep.put("shift", "DAY");
        assertThat(ok(doPost("/api/production/reports", admin, rep)).at("/status").asText()).isEqualTo("APPROVED");
        String fin = ok(doPost("/api/production/prod-orders/" + mo + "/finish", admin, Map.of("qty", 100))).asText();
        for (String in : jdbc.queryForObject("select stock_in_ids from mfg_finish where id = ?", String.class, Long.valueOf(fin)).split(",")) {
            ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
        }
        // T03：成品销售出库 60（按订单批号出库）
        String lot = jdbc.queryForObject("select batch_no from mfg_finish where id = ?", String.class, Long.valueOf(fin));
        long sid = SOURCE_ID.incrementAndGet();
        Long out = docApi.createStockOut(new StockOutRequest(StockOutType.SALES_OUT, new SourceRef("FIN_IT", sid, "FIN_IT-" + sid), Long.valueOf(W_FG),
                null, null, null, null, null, List.of(new StockOutRequest.Line(1L, Long.valueOf(fg), null, new BigDecimal("60"), lot, null)))).get(0);
        confirmOut(String.valueOf(out));
        // 毛利：另一成品按单价 10 入库后出货 5
        String fg2 = fg("毛利成品", Map.of());
        purchaseIn(fg2, W_FG, "20", "10");
        String c = customer("成本客户", false);
        String order = approvedOrder(c, List.of(orderLine(fg2, "5", "50", LocalDate.now().plusDays(5))));
        JsonNode ar = arOfShipment(shipOrder(c, order, 0, "5"));
        if (!"CONFIRMED".equals(ar.at("/header/status").asText())) ok(doPost("/api/finance/receivables/" + ar.at("/header/id").asText() + "/confirm", admin, null));

        ok(doPut("/api/finance/cost/expenses", admin, Map.of("period", PERIOD,
                "lines", List.of(Map.of("deptId", dept, "laborAmount", "1000", "overheadAmount", "500")))));
        JsonNode expenses = ok(doGet("/api/finance/cost/expenses?period=" + PERIOD, admin));
        JsonNode mine = null;
        for (JsonNode e : expenses) if (dept.equals(e.at("/deptId").asText())) mine = e;
        assertThat(mine).isNotNull();
        assertThat(mine.at("/workHours").decimalValue()).isEqualByComparingTo("8");

        // T04：库存未月结不能计算
        assertError(doPost("/api/finance/cost/runs?period=" + PERIOD, admin, null), "库存期间 " + PERIOD + " 尚未月结");
        JsonNode invCheck = ok(doPost("/api/inventory/periods/" + PERIOD + "/check", admin, null));
        assertThat(invCheck.at("/passed").asBoolean()).as("库存月结检查：%s", invCheck).isTrue();
        ok(doPost("/api/inventory/periods/" + PERIOD + "/close", admin, null));
        String voucherId = null;
        try {
            JsonNode check = ok(doGet("/api/finance/cost/check?period=" + PERIOD, admin));
            assertThat(check.at("/inventoryClosed").asBoolean()).isTrue();
            assertThat(check.at("/canCalculate").asBoolean()).as("%s", check).isTrue();
            JsonNode run = ok(doPost("/api/finance/cost/runs?period=" + PERIOD, admin, null));
            assertThat(run.at("/status").asText()).as("%s", run).isEqualTo("SUCCESS");
            assertThat(ok(doGet("/api/finance/cost/runs/" + run.at("/id").asText(), admin)).at("/materialCount").asInt()).isGreaterThanOrEqualTo(3);

            JsonNode screwRow = materialRow(screw);
            assertThat(screwRow.at("/unitCost").decimalValue()).isEqualByComparingTo("0.115");
            assertThat(screwRow.at("/outAmount").decimalValue()).isEqualByComparingTo("46.00");
            JsonNode oc = ok(doGet("/api/finance/cost/orders/" + mo + "?period=" + PERIOD, admin));
            assertThat(oc.at("/materialCost").decimalValue()).isEqualByComparingTo("2046.00");
            assertThat(oc.at("/laborCost").decimalValue()).isEqualByComparingTo("1000.00");
            assertThat(oc.at("/overheadCost").decimalValue()).isEqualByComparingTo("500.00");
            assertThat(oc.at("/finishedCost").decimalValue()).isEqualByComparingTo("3546.00");
            assertThat(oc.at("/unitCost").decimalValue()).isEqualByComparingTo("35.46");
            assertThat(oc.at("/materials").size()).isEqualTo(2);

            JsonNode fgRow = materialRow(fg);
            assertThat(fgRow.at("/unitCost").decimalValue()).isEqualByComparingTo("35.46");
            assertThat(fgRow.at("/outQty").decimalValue()).isEqualByComparingTo("60");
            BigDecimal salesUnit = jdbc.queryForObject("select unit_cost from inv_stock_txn where material_id = ? and biz_type = 'SALES_OUT'",
                    BigDecimal.class, Long.valueOf(fg));
            assertThat(salesUnit).as("销售出库流水回填加权单价").isEqualByComparingTo("35.46");

            JsonNode products = ok(doGet("/api/finance/cost/products?period=" + PERIOD + "&materialId=" + fg, admin));
            assertThat(products.at("/0/finishedQty").decimalValue()).isEqualByComparingTo("100");
            assertThat(products.at("/0/unitCost").decimalValue()).isEqualByComparingTo("35.46");
            ok(doGet("/api/finance/cost/exceptions?period=" + PERIOD, admin));

            // 毛利：成本 = 5 × 10
            JsonNode margin = ok(doGet("/api/finance/reports/order-margin?customerId=" + c, admin));
            assertThat(margin.at("/rows/0/cost").decimalValue()).isEqualByComparingTo("50.00");
            assertThat(margin.at("/uncalculatedPeriods").size()).isZero();

            // 成本类凭证生成后不能重算，删除凭证后可重算
            JsonNode gen = ok(doPost("/api/finance/vouchers/generate", admin, Map.of("period", PERIOD, "bizTypes", List.of("STOCK_OUT_SALES_COST"), "mode", "SUMMARY")));
            assertThat(gen.at("/voucherCount").asInt()).isEqualTo(1);
            voucherId = gen.at("/voucherIds/0").asText();
            JsonNode v = ok(doGet("/api/finance/vouchers/" + voucherId, admin));
            assertThat(v.at("/lines").findValuesAsText("accountCode")).containsExactly("6401", "1405");
            assertError(doPost("/api/finance/cost/runs?period=" + PERIOD, admin, null), "期间 " + PERIOD + " 的成本凭证已生成，请先删除凭证再重新计算");
            ok(doDelete("/api/finance/vouchers/" + voucherId, admin));
            voucherId = null;
            assertThat(ok(doPost("/api/finance/cost/runs?period=" + PERIOD, admin, null)).at("/status").asText()).isEqualTo("SUCCESS");
            assertThat(materialRow(screw).at("/unitCost").decimalValue()).isEqualByComparingTo("0.115");

            // 锁定后不能计算；结账检查中成本项通过
            ok(doPost("/api/finance/cost/lock?period=" + PERIOD, admin, null));
            assertError(doPost("/api/finance/cost/runs?period=" + PERIOD, admin, null), "期间 " + PERIOD + " 成本已锁定");
            JsonNode close = ok(doGet("/api/finance/close/" + PERIOD + "/check", admin));
            for (JsonNode i : close.at("/items")) {
                if ("COST".equals(i.at("/key").asText()) || "INVENTORY".equals(i.at("/key").asText())) assertThat(i.at("/passed").asBoolean()).isTrue();
            }
        } finally {
            if (voucherId != null) doDelete("/api/finance/vouchers/" + voucherId, admin);
            ok(doPost("/api/finance/cost/unlock?period=" + PERIOD, admin, null));
            ok(doPost("/api/inventory/periods/" + PERIOD + "/reopen", admin, null));
        }
    }

    private JsonNode materialRow(String materialId) throws Exception {
        for (JsonNode r : ok(doGet("/api/finance/cost/materials?period=" + PERIOD + "&keyword=" + code(materialId), admin))) {
            if (materialId.equals(r.at("/materialId").asText())) return r;
        }
        throw new AssertionError("物料单价表没有物料 " + materialId);
    }

    private void purchaseIn(String materialId, String qty, String unitCost) throws Exception {
        purchaseIn(materialId, W_RAW, qty, unitCost);
    }

    private void purchaseIn(String materialId, String warehouseId, String qty, String unitCost) throws Exception {
        long sid = SOURCE_ID.incrementAndGet();
        Long in = docApi.createStockIn(new StockInRequest(StockInType.PURCHASE_IN, new SourceRef("FIN_IT", sid, "FIN_IT-" + sid), Long.valueOf(warehouseId), null,
                null, null, List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), null, null, null,
                new BigDecimal(unitCost), null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    private String dept() throws Exception {
        Map<String, Object> d = new HashMap<>();
        d.put("parentId", "100");
        d.put("orgType", "DEPT");
        d.put("code", "D" + uniq());
        d.put("name", "装配车间" + uniq());
        d.put("sort", 10);
        return ok(doPost("/api/system/orgs", admin, d)).asText();
    }

    private static Map<String, Object> bomLine(String componentId, Object qtyPer) {
        Map<String, Object> l = new HashMap<>();
        l.put("componentId", componentId);
        l.put("qtyPer", qtyPer);
        l.put("scrapRate", 0);
        l.put("issueMethod", "PICK");
        l.put("operationSeq", 10);
        return l;
    }

    private void bom(String parentId, List<Map<String, Object>> lines) throws Exception {
        String id = ok(doPost("/api/engineering/boms", admin, Map.of("materialId", parentId, "baseQty", 1, "lines", lines))).at("/id").asText();
        assertThat(ok(doPost("/api/engineering/boms/" + id + "/submit", admin, null)).asText()).isEqualTo("APPROVED");
    }

    private String workCenter() throws Exception {
        Map<String, Object> w = new HashMap<>();
        w.put("code", "WC" + uniq());
        w.put("name", "产线" + uniq());
        w.put("deptId", "100");
        w.put("wcType", "LINE");
        w.put("hoursPerShift", 10);
        w.put("shiftCount", 2);
        w.put("efficiencyPct", 0.85);
        w.put("laborRate", 30);
        return ok(doPost("/api/engineering/work-centers", admin, w)).asText();
    }

    private void routing(String materialId, String wc) throws Exception {
        Map<String, Object> step = new HashMap<>();
        step.put("seq", 10);
        step.put("operation", "组装");
        step.put("workCenterId", wc);
        step.put("runSeconds", 36);
        step.put("isReportPoint", true);
        step.put("isInspectionPoint", false);
        String id = ok(doPost("/api/engineering/routings", admin, Map.of("materialId", materialId, "steps", List.of(step)))).at("/id").asText();
        ok(doPost("/api/engineering/routings/" + id + "/approve", admin, null));
    }

    private List<String> issueAll(String orderId) throws Exception {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode c : ok(doGet("/api/production/issues/candidates?prodOrderId=" + orderId, admin))) {
            lines.add(Map.of("materialLineId", c.at("/materialLineId").asText(), "requestQty", c.at("/requestQty").decimalValue()));
        }
        JsonNode r = ok(doPost("/api/production/issues", admin, Map.of("prodOrderId", orderId, "lines", lines)));
        List<String> ids = new ArrayList<>();
        for (JsonNode id : r.at("/ids")) {
            ok(doPost("/api/production/issues/" + id.asText() + "/submit", admin, null));
            ids.add(id.asText());
        }
        return ids;
    }

    private void confirmIssue(String issueId) throws Exception {
        for (String out : ok(doGet("/api/production/issues/" + issueId, admin)).at("/stockOutIds").asText().split(",")) confirmOut(out);
    }

    private void confirmOut(String out) throws Exception {
        JsonNode d = ok(doGet("/api/inventory/stock-outs/" + out, admin));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode l : d.at("/lines")) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.at("/id").asText());
            m.put("materialId", l.at("/materialId").asText());
            m.put("uom", l.at("/uom").asText());
            m.put("requestQty", l.at("/requestQty").decimalValue());
            m.put("qty", l.at("/requestQty").decimalValue());
            m.put("sourceLineId", l.at("/sourceLineId").asText());
            if (!l.at("/batchNo").isMissingNode() && !l.at("/batchNo").isNull()) m.put("batchNo", l.at("/batchNo").asText());
            lines.add(m);
        }
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of("outLines", lines)));
    }
}
