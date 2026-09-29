package com.erp.it.pmc;

import com.erp.module.pmc.api.query.PmcQueryApi;
import com.erp.module.pmc.api.shipping.ShippingPlanApi;
import com.erp.module.pmc.api.shipping.ShippingPlanLineDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MPS、产能与排产、缺料分析、交期预警、出货计划（需求 06-02、06-05～06-08 验收用例） */
class PmcPlanningIntegrationTest extends PmcTestSupport {

    @Autowired
    ShippingPlanApi shippingPlanApi;
    @Autowired
    PmcQueryApi pmcQueryApi;

    private static String week(LocalDate d) {
        return String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** 生产订单（已计划） */
    private String prodOrder(String materialId, String qty, LocalDate start, LocalDate end, Integer priority, Long salesLineId) throws Exception {
        Map<String, Object> o = new HashMap<>();
        o.put("materialId", materialId);
        o.put("qty", qty);
        o.put("planStart", start.toString());
        o.put("planEnd", end.toString());
        if (priority != null) o.put("priority", priority);
        if (salesLineId != null) o.put("salesOrderLineId", salesLineId);
        String id = ok(doPost("/api/production/prod-orders", admin, o)).at("/id").asText();
        assertThat(ok(doPost("/api/production/prod-orders/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("PLANNED");
        return id;
    }

    /** 从 from 开始（含）的第 n 个工作日（周日休息） */
    private static LocalDate workday(LocalDate from, int n) {
        LocalDate d = from;
        int count = 0;
        while (true) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY && ++count == n) return d;
            d = d.plusDays(1);
        }
    }

    /** PMC-MPS-T01 按需求生成；T02 产能检查超载；T03 发布并启用 use-mps 后 MRP 取 MPS 计划数量 */
    @Test
    void mps() throws Exception {
        String comp = raw("面板", Map.of());
        String fg = fg("显示器", Map.of("leadTimeDays", 2));
        bom(fg, List.of(bomLine(comp, 1)));
        String wc = workCenter(10, 1);
        routing(fg, new Object[]{10, "组装", wc, 3600});
        stock(fg, W_FG, "200");
        LocalDate monday = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate wed = monday.plusDays(2);
        String w = week(monday);
        salesOrder(customer(), List.of(soLine(fg, "1000", wed)));
        prodOrder(fg, "600", monday, wed, null, null);

        String mps = ok(doPost("/api/pmc/mps", admin, Map.of("title", "显示器 MPS", "startWeek", w, "endWeek", week(monday.plusWeeks(3))))).asText();
        assertError(doPost("/api/pmc/mps", admin, Map.of("title", "x", "startWeek", w, "endWeek", week(monday.plusWeeks(30)))),
                "MPS 周期最多 26 周，且结束周不能早于开始周");
        assertThat(doPut("/api/pmc/mps/" + mps + "/matrix", admin, Map.of("rows", List.of(Map.of("materialId", comp)))).at("/msg").asText())
                .contains("不是自制件或没有 BOM");
        JsonNode m = ok(doPost("/api/pmc/mps/" + mps + "/generate", admin, Map.of("materialIds", List.of(fg))));
        JsonNode row = null;
        for (JsonNode r : m.at("/rows")) if (r.at("/materialId").asText().equals(fg)) row = r;
        assertThat(row).isNotNull();
        assertThat(row.at("/openingQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(row.at("/cells/0/week").asText()).isEqualTo(w);
        assertThat(row.at("/cells/0/demandQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(row.at("/cells/0/wipQty").decimalValue()).isEqualByComparingTo("600");
        assertThat(row.at("/cells/0/plannedQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(row.at("/cells/0/projectedQty").decimalValue()).isEqualByComparingTo("0");

        // 产能检查：200 × 1h = 200h > 周产能 60h
        JsonNode cap = ok(doPost("/api/pmc/mps/" + mps + "/capacity-check", admin, null));
        JsonNode wcRow = null;
        for (JsonNode r : cap) if (r.at("/workCenterId").asText().equals(wc)) wcRow = r;
        assertThat(wcRow.at("/cells/0/loadHours").decimalValue()).isEqualByComparingTo("200");
        assertThat(wcRow.at("/cells/0/capacityHours").decimalValue()).isEqualByComparingTo("60");
        assertThat(wcRow.at("/cells/0/overloaded").asBoolean()).isTrue();

        // 手工改第 2 周计划，保存后结存重算
        List<Map<String, Object>> cells = new ArrayList<>();
        cells.add(Map.of("week", week(monday.plusWeeks(1)), "plannedQty", 50));
        m = ok(doPut("/api/pmc/mps/" + mps + "/matrix", admin, Map.of("rows", List.of(Map.of("materialId", fg, "cells", cells)))));
        assertThat(m.at("/rows/0/cells/1/projectedQty").decimalValue()).isEqualByComparingTo("50");

        ok(doPost("/api/pmc/mps/" + mps + "/publish", admin, null));
        assertThat(ok(doGet("/api/pmc/mps/" + mps, admin)).at("/mpsStatus").asText()).isEqualTo("PUBLISHED");
        // 复制为新草稿，发布时替代旧版本
        String copy = ok(doPost("/api/pmc/mps/" + mps + "/copy", admin, null)).asText();
        ok(doPost("/api/pmc/mps/" + copy + "/publish", admin, null));
        assertThat(ok(doGet("/api/pmc/mps/" + mps, admin)).at("/mpsStatus").asText()).isEqualTo("CLOSED");

        setParam("pmc.mrp.use-mps", "true");
        try {
            runMrp();
            JsonNode bal = ok(doGet("/api/pmc/mrp/balance?materialId=" + fg, admin));
            List<String> types = new ArrayList<>();
            bal.at("/rows").forEach(r -> types.add(r.at("/type").asText()));
            assertThat(types).contains("MPS").doesNotContain("SALES_ORDER");
        } finally {
            resetParam("pmc.mrp.use-mps");
        }
    }

    /** PMC-SCH-T01 有限产能跨天拆分；T02 停工日跳过；T04 不能早于上道；T03 插单模拟；应用到生产订单 */
    @Test
    void schedule() throws Exception {
        String comp = raw("芯片", Map.of());
        String fg = fg("主板", Map.of());
        bom(fg, List.of(bomLine(comp, 1)));
        String wc1 = workCenter(17, 1);
        String wc2 = workCenter(17, 1);
        routing(fg, new Object[]{10, "SMT", wc1, 1080}, new Object[]{20, "DIP", wc2, 360});
        LocalDate today = LocalDate.now();
        String mo = prodOrder(fg, "100", today, today.plusDays(10), 5, null);

        LocalDate d1 = workday(today, 1);
        ok(doPost("/api/pmc/capacity-calendars/batch", admin, Map.of("workCenterId", wc1, "from", d1.toString(), "to", d1.toString(), "hours", 0,
                "reason", "HOLIDAY")));
        JsonNode run = ok(doPost("/api/pmc/schedules/run", admin, Map.of("mode", "FINITE")));
        assertThat(run.at("/mode").asText()).isEqualTo("FINITE");
        List<JsonNode> rows = rows(mo);
        assertThat(rows).hasSize(2);
        JsonNode op10 = rows.get(0);
        LocalDate day1 = workday(d1.plusDays(1), 1);
        LocalDate day2 = workday(day1.plusDays(1), 1);
        assertThat(op10.at("/loadHours").decimalValue()).isEqualByComparingTo("30");
        assertThat(op10.at("/schedStart").asText()).startsWith(day1.toString());
        assertThat(op10.at("/schedEnd").asText()).startsWith(day2.toString());
        JsonNode month = ok(doGet("/api/pmc/capacity-calendars?workCenterId=" + wc1 + "&month=" + day1.toString().substring(0, 7), admin));
        for (JsonNode d : month.at("/days")) {
            if (d.at("/date").asText().equals(day1.toString())) assertThat(d.at("/loadHours").decimalValue()).isEqualByComparingTo("17");
            if (d.at("/date").asText().equals(d1.toString())) assertThat(d.at("/exception").asBoolean()).isTrue();
        }
        JsonNode load = ok(doGet("/api/pmc/capacity/load?from=" + day1 + "&to=" + day2, admin));
        boolean found = false;
        for (JsonNode r : load) {
            if (!r.at("/workCenterId").asText().equals(wc1)) continue;
            found = true;
            assertThat(r.at("/cells/0/loadHours").decimalValue()).isEqualByComparingTo("17");
        }
        assertThat(found).isTrue();

        // 工序 20 不能拖到工序 10 结束之前
        assertError(doPut("/api/pmc/schedules/" + rows.get(1).at("/id").asText(), admin, Map.of("startDate", day1.toString())),
                "不能早于上道工序结束时间");

        // 插单模拟：另一张订单优先级 1 → 本订单延期；模拟不保存
        String urgent = prodOrder(fg, "100", today, today.plusDays(10), 5, null);
        ok(doPost("/api/pmc/schedules/run", admin, Map.of("mode", "FINITE")));
        String before = rows(mo).get(1).at("/schedEnd").asText();
        JsonNode sim = ok(doPost("/api/pmc/schedules/simulate", admin, Map.of("prodOrderId", urgent, "priority", 1)));
        boolean delayed = false;
        for (JsonNode r : sim.at("/delayed")) if (r.at("/prodOrderId").asText().equals(mo)) delayed = r.at("/delayDays").asInt() > 0;
        assertThat(delayed).isTrue();
        assertThat(rows(mo).get(1).at("/schedEnd").asText()).isEqualTo(before);

        // 应用到生产订单
        JsonNode preview = ok(doGet("/api/pmc/schedules/apply-preview", admin));
        JsonNode mine = null;
        for (JsonNode r : preview) if (r.at("/prodOrderId").asText().equals(mo)) mine = r;
        assertThat(mine).isNotNull();
        ok(doPost("/api/pmc/schedules/apply", admin, Map.of("prodOrderIds", List.of(mo))));
        JsonNode order = ok(doGet("/api/production/prod-orders/" + mo, admin));
        assertThat(order.at("/planStart").asText()).isEqualTo(mine.at("/newStart").asText());
        assertThat(order.at("/planEnd").asText()).isEqualTo(mine.at("/newEnd").asText());
    }

    private List<JsonNode> rows(String orderId) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        LocalDate from = LocalDate.now().minusDays(1);
        for (JsonNode r : ok(doGet("/api/pmc/schedules?from=" + from + "&to=" + from.plusDays(60), admin))) {
            if (r.at("/prodOrderId").asText().equals(orderId)) out.add(r);
        }
        out.sort((a, b) -> Integer.compare(a.at("/operationSeq").asInt(), b.at("/operationSeq").asInt()));
        return out;
    }

    /** PMC-SHT-T01 按优先级分配；T02 在途预计齐套；T03 可开工数量；T04 推送催料（一天一次） */
    @Test
    void shortage() throws Exception {
        String screw = raw("螺丝", Map.of("buyerId", "1"));
        String fg = fg("风扇", Map.of());
        bom(fg, List.of(bomLine(screw, 4.08)));
        stock(screw, W_RAW, "500");
        LocalDate today = LocalDate.now();
        String mo1 = prodOrder(fg, "100", today.plusDays(1), today.plusDays(5), 1, null);
        String mo2 = prodOrder(fg, "100", today.plusDays(1), today.plusDays(5), 5, null);
        String pcba = raw("PCBA", Map.of());
        String fg2 = fg("电源板", Map.of());
        bom(fg2, List.of(bomLine(pcba, 1)));
        stock(pcba, W_RAW, "60");
        String mo3 = prodOrder(fg2, "100", today.plusDays(2), today.plusDays(6), 5, null);

        JsonNode a = ok(doPost("/api/pmc/shortages/analyze", admin, Map.of("prodOrderIds", List.of(mo1, mo2, mo3))));
        String snap = a.at("/snapshotNo").asText();
        assertThat(a.at("/orderCount").asInt()).isEqualTo(3);
        Map<String, JsonNode> orders = new HashMap<>();
        for (JsonNode o : ok(doGet("/api/pmc/shortages/orders?snapshotNo=" + snap, admin))) orders.put(o.at("/prodOrderId").asText(), o);
        assertThat(orders.get(mo1).at("/shortLineCount").asInt()).isZero();
        assertThat(orders.get(mo1).at("/lineKitRate").decimalValue()).isEqualByComparingTo("1");
        assertThat(orders.get(mo2).at("/shortLineCount").asInt()).isEqualTo(1);
        assertThat(orders.get(mo2).at("/hasNoSupply").asBoolean()).isTrue();
        assertThat(orders.get(mo3).at("/kitableQty").decimalValue()).isEqualByComparingTo("60");
        JsonNode lines = ok(doGet("/api/pmc/shortages/lines?snapshotNo=" + snap + "&prodOrderId=" + mo2, admin));
        assertThat(lines.at("/0/shortageQty").decimalValue()).isEqualByComparingTo("316");
        assertThat(pmcQueryApi.getShortage(Long.valueOf(mo2))).isNotEmpty();

        // 在途 1000，交期 +5 天 → MO-2 预计齐套
        String sup = supplier(screw);
        purchaseOrder(sup, screw, "1000", today.plusDays(5));
        String snap2 = ok(doPost("/api/pmc/shortages/analyze", admin, Map.of("prodOrderIds", List.of(mo1, mo2)))).at("/snapshotNo").asText();
        for (JsonNode o : ok(doGet("/api/pmc/shortages/orders?snapshotNo=" + snap2, admin))) {
            if (o.at("/prodOrderId").asText().equals(mo2)) {
                assertThat(o.at("/etaDate").asText()).isEqualTo(today.plusDays(5).toString());
                assertThat(o.at("/etaLate").asBoolean()).isTrue();
            }
        }
        JsonNode mats = ok(doGet("/api/pmc/shortages/materials?snapshotNo=" + snap2, admin));
        assertThat(mats.at("/0/componentId").asText()).isEqualTo(screw);
        assertThat(mats.at("/0/supplies/0/qty").decimalValue()).isEqualByComparingTo("316");

        JsonNode push = ok(doPost("/api/pmc/shortages/push", admin, Map.of("snapshotNo", snap2, "componentIds", List.of(screw))));
        assertThat(push.at("/success").asInt()).isEqualTo(1);
        JsonNode again = ok(doPost("/api/pmc/shortages/push", admin, Map.of("snapshotNo", snap2, "componentIds", List.of(screw))));
        assertThat(again.at("/success").asInt()).isZero();
        assertThat(again.at("/errors/0").asText()).contains("今天已推送过");
        assertThat(ok(doGet("/api/pmc/shortages/snapshots", admin)).at("/0/snapshotNo").asText()).isEqualTo(snap2);
    }

    /** PMC-ALT-T01 生产进度延期 → 警告；T02 库存足够无预警；T03 严重；处理与忽略 */
    @Test
    void deliveryAlerts() throws Exception {
        String comp = raw("外壳", Map.of());
        stock(comp, W_RAW, "10000");
        String fg = fg("音箱", Map.of());
        bom(fg, List.of(bomLine(comp, 1)));
        String fg2 = fg("耳机", Map.of());
        stock(fg2, W_FG, "500");
        String fg3 = fg("功放", Map.of());
        bom(fg3, List.of(bomLine(comp, 1)));
        LocalDate today = LocalDate.now();
        String cust = customer();
        String so = salesOrder(cust, List.of(soLine(fg, "100", today.plusDays(10)), soLine(fg2, "100", today.plusDays(10)),
                soLine(fg3, "100", today.plusDays(10))));
        Long l1 = soLineId(so, 0);
        Long l3 = soLineId(so, 2);
        String mo = prodOrder(fg, "100", today.plusDays(5), today.plusDays(15), null, l1);
        String mo3 = prodOrder(fg3, "100", today.plusDays(5), today.plusDays(25), null, l3);
        jdbc.update("DELETE FROM pmc_schedule WHERE prod_order_id IN (?, ?)", Long.valueOf(mo), Long.valueOf(mo3));

        ok(doPost("/api/pmc/delivery-alerts/recalculate", admin, null));
        Map<Long, JsonNode> byLine = new HashMap<>();
        for (JsonNode a : ok(doGet("/api/pmc/delivery-alerts?pageNo=1&pageSize=100&customerId=" + cust, admin)).at("/list")) {
            byLine.put(a.at("/orderLineId").asLong(), a);
        }
        JsonNode a1 = byLine.get(l1);
        assertThat(a1.at("/delayDays").asInt()).isEqualTo(6);
        assertThat(a1.at("/alertLevel").asText()).isEqualTo("WARNING");
        assertThat(a1.at("/cause").asText()).isEqualTo("WO_DELAY");
        assertThat(byLine).doesNotContainKey(soLineId(so, 1));
        assertThat(byLine.get(l3).at("/alertLevel").asText()).isEqualTo("CRITICAL");
        assertThat(pmcQueryApi.getEstimatedDate(l1)).contains(today.plusDays(16));
        JsonNode sum = ok(doGet("/api/pmc/delivery-alerts/summary?customerId=" + cust, admin));
        assertThat(sum.at("/critical").asInt()).isEqualTo(1);
        assertThat(sum.at("/warning").asInt()).isEqualTo(1);

        assertError(doPost("/api/pmc/delivery-alerts/" + a1.at("/id").asText() + "/handle", admin, Map.of()), "请填写处理说明原因");
        ok(doPost("/api/pmc/delivery-alerts/" + a1.at("/id").asText() + "/handle", admin, Map.of("remark", "已与客户沟通延期")));
        ok(doPost("/api/pmc/delivery-alerts/" + byLine.get(l3).at("/id").asText() + "/ignore", admin, Map.of("remark", "客户同意")));
        JsonNode handled = ok(doGet("/api/pmc/delivery-alerts?pageNo=1&pageSize=10&statuses=HANDLED&customerId=" + cust, admin)).at("/list");
        assertThat(handled.size()).isEqualTo(1);
    }

    /** PMC-SP-T01 生成计划（可用不足的行）；R01 计划 ≤ 未出货；发布；出货通知回写 */
    @Test
    void shippingPlan() throws Exception {
        String fg = fg("插座", Map.of());
        stock(fg, W_FG, "250");
        LocalDate today = LocalDate.now();
        LocalDate due = today;
        String cust = customer();
        String so = salesOrder(cust, List.of(soLine(fg, "100", due), soLine(fg, "100", due), soLine(fg, "100", due)));
        String w = week(today);
        String plan = ok(doPost("/api/pmc/shipping-plans", admin, Map.of("planWeek", w))).asText();
        JsonNode d = ok(doPost("/api/pmc/shipping-plans/" + plan + "/generate", admin, null));
        List<JsonNode> mine = new ArrayList<>();
        d.at("/lines").forEach(l -> {
            if (l.at("/materialId").asText().equals(fg)) mine.add(l);
        });
        assertThat(mine).hasSize(3);
        assertThat(mine.get(0).at("/planQty").decimalValue()).isEqualByComparingTo("100");
        assertThat(mine.get(1).at("/planQty").decimalValue()).isEqualByComparingTo("100");
        assertThat(mine.get(2).at("/planQty").decimalValue()).isEqualByComparingTo("50");
        assertThat(mine.get(2).at("/shortage").asBoolean()).isTrue();

        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode l : d.at("/lines")) {
            Map<String, Object> x = new HashMap<>();
            x.put("id", l.at("/id").asText());
            x.put("orderLineId", l.at("/orderLineId").asText());
            x.put("planQty", l.at("/orderLineId").asText().equals(mine.get(0).at("/orderLineId").asText()) ? 150 : l.at("/planQty").decimalValue());
            x.put("transportMode", "LAND");
            lines.add(x);
        }
        assertError(doPut("/api/pmc/shipping-plans/" + plan, admin, Map.of("planWeek", w, "lines", lines)), "计划数量不能超过未出货数量 100");
        lines.forEach(x -> {
            if (x.get("orderLineId").equals(mine.get(0).at("/orderLineId").asText())) x.put("planQty", 100);
        });
        ok(doPut("/api/pmc/shipping-plans/" + plan, admin, Map.of("planWeek", w, "lines", lines)));
        ok(doPost("/api/pmc/shipping-plans/" + plan + "/publish", admin, null));

        ShippingPlanLineDTO first = shippingPlanApi.getPlanLines(w).stream()
                .filter(l -> l.orderLineId().toString().equals(mine.get(0).at("/orderLineId").asText())).findFirst().orElseThrow();
        shippingPlanApi.onNoticed(first.id(), new BigDecimal("100"));
        JsonNode after = ok(doGet("/api/pmc/shipping-plans/" + plan, admin));
        for (JsonNode l : after.at("/lines")) {
            if (l.at("/id").asText().equals(first.id().toString())) assertThat(l.at("/lineStatus").asText()).isEqualTo("NOTICED");
        }
        // 已通知的行不能删除
        List<Map<String, Object>> without = lines.stream().filter(x -> !x.get("orderLineId").equals(mine.get(0).at("/orderLineId").asText())).toList();
        assertThat(doPut("/api/pmc/shipping-plans/" + plan, admin, Map.of("planWeek", w, "lines", without)).at("/msg").asText()).contains("已生成出货通知");
        assertThat(so).isNotBlank();
    }
}
