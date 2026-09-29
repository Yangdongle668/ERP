package com.erp.it.pmc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 需求池、交期回复、MRP 运算与建议（需求 06-01、06-03、06-04 验收用例） */
class PmcMrpIntegrationTest extends PmcTestSupport {

    /** PMC-DMD-T01/T02 订单审核进入需求池，出货更新已满足；T03 成品库存足够 → 建议交期明天；T04 按齐套日期 + 提前期；保存回复 */
    @Test
    void demandPoolAndReply() throws Exception {
        String pcba = raw("PCBA", Map.of("leadTimeDays", 5));
        String fg1 = fg("控制器", Map.of("leadTimeDays", 3));
        bom(fg1, List.of(bomLine(pcba, 1)));
        String fg2 = fg("电源", Map.of());
        stock(fg2, W_FG, "1200");
        String cust = customer();
        LocalDate due = LocalDate.now().plusDays(30);
        String so = salesOrder(cust, List.of(soLine(fg1, "1000", due), soLine(fg2, "1000", due)));

        JsonNode d = demands(fg1).get(0);
        assertThat(d.at("/demandType").asText()).isEqualTo("SALES_ORDER");
        assertThat(d.at("/qty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(d.at("/requiredDate").asText()).isEqualTo(due.toString());
        ship(soLineId(so, 0), "400");
        d = demands(fg1).get(0);
        assertThat(d.at("/fulfilledQty").decimalValue()).isEqualByComparingTo("400");
        assertThat(d.at("/openQty").decimalValue()).isEqualByComparingTo("600");

        JsonNode pending = ok(doGet("/api/pmc/delivery-replies/pending?customerId=" + cust, admin));
        assertThat(pending.size()).isEqualTo(2);
        JsonNode r1 = null, r2 = null;
        for (JsonNode r : pending) {
            if (r.at("/materialId").asText().equals(fg1)) r1 = r;
            else r2 = r;
        }
        assertThat(r2.at("/suggestedDate").asText()).isEqualTo(LocalDate.now().plusDays(1).toString());
        // PCBA 无库存无在途：齐套 = 今天 + 5；建议 = 齐套 + 3 + 1
        assertThat(r1.at("/suggestedDate").asText()).isEqualTo(LocalDate.now().plusDays(9).toString());
        JsonNode kit = ok(doPost("/api/pmc/delivery-replies/kit-analysis", admin, Map.of("materialId", fg1, "qty", 600)));
        assertThat(kit.at("/lines/0/requiredQty").decimalValue()).isEqualByComparingTo("600");
        assertThat(kit.at("/kitDate").asText()).isEqualTo(LocalDate.now().plusDays(5).toString());

        assertError(doPost("/api/pmc/delivery-replies", admin, Map.of("lines", List.of(Map.of("demandId", r1.at("/demandId").asText(),
                "promisedDate", LocalDate.now().minusDays(1).toString())))), "承诺交期不能早于今天");
        LocalDate promised = due.plusDays(5);
        ok(doPost("/api/pmc/delivery-replies", admin, Map.of("lines", List.of(Map.of("demandId", r1.at("/demandId").asText(),
                "promisedDate", promised.toString(), "remark", "物料紧张")))));
        JsonNode line = ok(doGet("/api/sales/orders/" + so, admin)).at("/lines/0");
        assertThat(line.at("/promisedDate").asText()).isEqualTo(promised.toString());
        assertThat(demands(fg1).get(0).at("/requiredDate").asText()).isEqualTo(promised.toString());
        assertThat(ok(doGet("/api/pmc/delivery-replies/pending?customerId=" + cust, admin)).size()).isEqualTo(1);

        // 手工需求：说明必填
        assertError(doPost("/api/pmc/demands", admin, Map.of("materialId", fg2, "qty", 5, "requiredDate", due.toString())), "请填写需求说明");
        String manual = ok(doPost("/api/pmc/demands", admin, Map.of("materialId", fg2, "qty", 5, "requiredDate", due.toString(), "remark", "展会样机"))).asText();
        ok(doPost("/api/pmc/demands/" + manual + "/close", admin, null));
    }

    /** PMC-MRP-T01 多层计划；T05 需求追溯；SUG-T02 转生产订单并下达 */
    @Test
    void mrpPlannedOrders() throws Exception {
        String pcba = semi("PCBA", Map.of("leadTimeDays", 5));
        String screw = raw("螺丝", Map.of("leadTimeDays", 10, "mpq", 1000));
        String fg = fg("控制器", Map.of("leadTimeDays", 3));
        bom(fg, List.of(bomLine(pcba, 1), bomLine(screw, 4)));
        String cust = customer();
        LocalDate due = LocalDate.now().plusDays(30);
        String so = salesOrder(cust, List.of(soLine(fg, "100", due)));
        String soNo = ok(doGet("/api/sales/orders/" + so, admin)).at("/docNo").asText();

        runMrp();
        List<JsonNode> f = suggestions(fg);
        assertThat(f).hasSize(1);
        assertThat(f.get(0).at("/type").asText()).isEqualTo("MAKE");
        assertThat(f.get(0).at("/qty").decimalValue()).isEqualByComparingTo("100");
        assertThat(f.get(0).at("/releaseDate").asText()).isEqualTo(due.minusDays(3).toString());
        List<JsonNode> p = suggestions(pcba);
        assertThat(p).hasSize(1);
        assertThat(p.get(0).at("/requiredDate").asText()).isEqualTo(due.minusDays(3).toString());
        assertThat(p.get(0).at("/releaseDate").asText()).isEqualTo(due.minusDays(8).toString());
        List<JsonNode> s = suggestions(screw);
        assertThat(s).hasSize(1);
        assertThat(s.get(0).at("/type").asText()).isEqualTo("PURCHASE");
        assertThat(s.get(0).at("/qty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(s.get(0).at("/netRequirement").decimalValue()).isEqualByComparingTo("400");
        assertThat(s.get(0).at("/requiredDate").asText()).isEqualTo(due.minusDays(3).toString());
        assertThat(s.get(0).at("/releaseDate").asText()).isEqualTo(due.minusDays(13).toString());

        // 需求追溯：螺丝 ← 控制器计划订单 ← 销售订单
        JsonNode peg = ok(doGet("/api/pmc/mrp/suggestions/" + s.get(0).at("/id").asText() + "/pegging", admin));
        assertThat(peg.at("/0/demandType").asText()).isEqualTo("PARENT");
        assertThat(peg.at("/0/parentMaterialId").asText()).isEqualTo(fg);
        JsonNode top = ok(doGet("/api/pmc/mrp/suggestions/" + peg.at("/0/parentResultId").asText() + "/pegging", admin));
        assertThat(top.at("/0/sourceNo").asText()).startsWith(soNo);

        // 供需平衡
        JsonNode bal = ok(doGet("/api/pmc/mrp/balance?materialId=" + screw, admin));
        assertThat(bal.at("/rows").size()).isGreaterThanOrEqualTo(3);

        // 调整建议：不是 MPQ 倍数提示；转生产订单并下达
        JsonNode warn = ok(doPut("/api/pmc/mrp/suggestions/" + s.get(0).at("/id").asText(), admin, Map.of("qty", 1500)));
        assertThat(warn.get(0).asText()).contains("最小包装量");
        JsonNode conv = ok(doPost("/api/pmc/mrp/suggestions/convert", admin, Map.of("ids", List.of(f.get(0).at("/id").asText()), "release", true)));
        assertThat(conv.at("/success").asInt()).as(conv.toString()).isEqualTo(1);
        JsonNode after = suggestions(fg).get(0);
        assertThat(after.at("/status").asText()).isEqualTo("CONVERTED");
        String moId = after.at("/convertedDocId").asText();
        assertThat(ok(doGet("/api/production/prod-orders/" + moId, admin)).at("/prodStatus").asText()).isEqualTo("RELEASED");

        // 忽略需要原因
        assertError(doPost("/api/pmc/mrp/suggestions/ignore", admin, Map.of("ids", List.of(p.get(0).at("/id").asText()))), "请填写忽略原因");
        ok(doPost("/api/pmc/mrp/suggestions/ignore", admin, Map.of("ids", List.of(p.get(0).at("/id").asText()), "reason", "外购")));

        // 新运算：在制订单计入，成品不再建议；旧运算的建议已过期
        runMrp();
        assertThat(suggestions(fg)).isEmpty();
        assertError(doPost("/api/pmc/mrp/suggestions/ignore", admin, Map.of("ids", List.of(s.get(0).at("/id").asText()), "reason", "x")),
                "该建议已过期，请使用最新的运算结果");
    }

    /** PMC-MRP-T02 在途采购视为提前（例外），T03 安全库存；R01 同时只能一个运算 */
    @Test
    void inTransitAndSafetyStock() throws Exception {
        String screw = raw("螺丝", Map.of("leadTimeDays", 10, "mpq", 1000));
        String bolt = raw("螺栓", Map.of("leadTimeDays", 10, "mpq", 1000, "safetyStock", 200));
        String fg = fg("机箱", Map.of("leadTimeDays", 3));
        bom(fg, List.of(bomLine(screw, 4), bomLine(bolt, 4)));
        String sup = supplier(screw, bolt);
        LocalDate due = LocalDate.now().plusDays(30);
        String po = purchaseOrder(sup, screw, "500", due.plusDays(6));
        salesOrder(customer(), List.of(soLine(fg, "100", due)));

        runMrp();
        assertThat(suggestions(screw)).isEmpty();
        List<JsonNode> ex = exceptions(screw);
        assertThat(ex).hasSize(1);
        assertThat(ex.get(0).at("/type").asText()).isEqualTo("EXPEDITE");
        assertThat(ex.get(0).at("/suggestedDate").asText()).isEqualTo(due.minusDays(3).toString());
        assertThat(ex.get(0).at("/message").asText()).contains("提前到 " + due.minusDays(3));
        assertThat(ex.get(0).at("/docId").asText()).isEqualTo(po);

        // 螺栓：安全库存 200（今天）按 MPQ 取整为 1000，同时覆盖后面的 400
        List<JsonNode> b = suggestions(bolt);
        assertThat(b).hasSize(1);
        assertThat(b.get(0).at("/netRequirement").decimalValue()).isEqualByComparingTo("200");
        assertThat(b.get(0).at("/qty").decimalValue()).isEqualByComparingTo("1000");
        JsonNode bp = ok(doGet("/api/pmc/mrp/suggestions/" + b.get(0).at("/id").asText() + "/pegging", admin));
        assertThat(bp.size()).isEqualTo(2);
        assertThat(bp.at("/0/demandType").asText()).isEqualTo("SAFETY_STOCK");
        assertThat(b.get(0).at("/late").asBoolean()).isTrue();

        // 推送例外（负责人为采购员，未设置时计入失败）
        JsonNode push = ok(doPost("/api/pmc/mrp/exceptions/push", admin, Map.of("ids", List.of(ex.get(0).at("/id").asText()))));
        assertThat(push.at("/success").asInt() + push.at("/errors").size()).isEqualTo(1);
        ok(doPost("/api/pmc/mrp/exceptions/" + ex.get(0).at("/id").asText() + "/handled", admin, null));

        // 同时只能有一个运算
        jdbc.update("INSERT INTO pmc_mrp_run (id, run_no, run_type, run_status, started_at, material_count, suggestion_count, exception_count, progress, "
                + "operator_id, version, created_at, updated_at, deleted) VALUES (?, ?, 'FULL', 'RUNNING', ?, 0, 0, 0, 0, 1, 0, ?, ?, 0)",
                990000001L, "MRP-TEST-RUNNING", java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        try {
            JsonNode busy = doPost("/api/pmc/mrp/runs", admin, Map.of("runType", "FULL"));
            assertThat(busy.at("/msg").asText()).startsWith("MRP 正在运算中（MRP-TEST-RUNNING");
        } finally {
            jdbc.update("DELETE FROM pmc_mrp_run WHERE id = 990000001");
        }
    }
}
