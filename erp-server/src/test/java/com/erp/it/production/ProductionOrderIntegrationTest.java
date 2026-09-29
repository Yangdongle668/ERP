package com.erp.it.production;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 生产主流程：下达齐套检查 → 按套领料 / 超领 → 报工倒冲 → 不良处置 → 完工入库 → 退余料关闭 → 用料差异（需求 09-01～09-06、09-08） */
class ProductionOrderIntegrationTest extends ProductionTestSupport {

    @Test
    void mainFlow() throws Exception {
        String prod = fg("控制器");
        String pcba = raw("PCBA");
        String screw = raw("螺丝");
        String glue = material("胶水", CAT_AUX, "AUXILIARY", "NONE");
        bom(prod, List.of(bomLine(pcba, 1, 0, "PICK", 10), bomLine(screw, 4, 0.02, "PICK", 10), bomLine(glue, 0.5, 0, "BACKFLUSH", 20)));
        routing(prod, workCenter(), new Object[]{10, "贴片", true}, new Object[]{20, "组装", true});
        stock(screw, W_RAW, "300", null);
        stock(pcba, W_RAW, "1000", null);
        stock(glue, W_AUX, "1000", null);

        // PO-T01 下达齐套检查：参数 WARN，缺料需确认
        String id = plannedOrder(prod, "100");
        JsonNode resp = doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of());
        assertThat(resp.at("/code").asInt()).isNotZero();
        assertThat(resp.at("/msg").asText()).contains("螺丝").contains("缺 108");
        assertThat(resp.at("/data/needConfirm").asBoolean()).isTrue();
        JsonNode released = ok(doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of("confirmShortage", true)));
        assertThat(released.at("/status").asText()).isEqualTo("RELEASED");
        assertThat(released.at("/warnings/0").asText()).contains("缺 108");
        assertThat(materialLine(id, pcba).at("/requiredQty").decimalValue()).isEqualByComparingTo("100");
        assertThat(materialLine(id, screw).at("/requiredQty").decimalValue()).isEqualByComparingTo("408");
        assertThat(order(id).at("/operations").size()).isEqualTo(2);

        // ISS-T01 按套数领料 50 套，仓库实发螺丝 200
        JsonNode cands = ok(doGet("/api/production/issues/candidates?prodOrderId=" + id + "&kitQty=50", admin));
        assertThat(cands.size()).isEqualTo(2);
        Map<String, String> req = new java.util.HashMap<>();
        for (JsonNode c : cands) req.put(c.at("/materialId").asText(), c.at("/requestQty").decimalValue().stripTrailingZeros().toPlainString());
        assertThat(req).containsEntry(pcba, "50").containsEntry(screw, "204");
        List<Map<String, Object>> lines = new java.util.ArrayList<>();
        for (JsonNode c : cands) lines.add(Map.of("materialLineId", c.at("/materialLineId").asText(), "requestQty", c.at("/requestQty").decimalValue()));
        JsonNode created = ok(doPost("/api/production/issues", admin, Map.of("prodOrderId", id, "kitQty", 50, "lines", lines)));
        String issue1 = created.at("/ids/0").asText();
        assertThat(ok(doPost("/api/production/issues/" + issue1 + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        confirmIssue(issue1, Map.of(screw, "200"), null);
        assertThat(ok(doGet("/api/production/issues/" + issue1, admin)).at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(materialLine(id, screw).at("/issuedQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(materialLine(id, screw).at("/openQty").decimalValue()).isEqualByComparingTo("208");
        assertThat(order(id).at("/prodStatus").asText()).isEqualTo("IN_PROGRESS");

        // 领剩余；再领 → 请走超领；超领需原因
        stock(screw, W_RAW, "500", null);
        for (String i : issueAll(id)) confirmIssue(i, null, null);
        assertThat(materialLine(id, screw).at("/openQty").decimalValue()).isEqualByComparingTo("0");
        String screwLine = materialLine(id, screw).at("/id").asText();
        String more = ok(doPost("/api/production/issues", admin, Map.of("prodOrderId", id, "lines", List.of(Map.of("materialLineId", screwLine, "requestQty", 10))))).at("/ids/0").asText();
        assertThat(doPost("/api/production/issues/" + more + "/submit", admin, null).at("/msg").asText()).contains("请走超领");
        ok(doDelete("/api/production/issues/" + more, admin));
        assertError(doPost("/api/production/issues/over", admin, Map.of("prodOrderId", id, "materialLineId", screwLine, "qty", 10)), "超领必须选择超领原因");
        JsonNode over = ok(doPost("/api/production/issues/over", admin,
                Map.of("prodOrderId", id, "materialLineId", screwLine, "qty", 10, "overReason", "SCRAP", "submit", true)));
        confirmIssue(over.at("/ids/0").asText(), null, null);
        assertThat(materialLine(id, screw).at("/issuedQty").decimalValue()).isEqualByComparingTo("418");
        assertThat(materialLine(id, screw).at("/overIssuedQty").decimalValue()).isEqualByComparingTo("10");

        // PO-T05 调整应领不能小于已领
        JsonNode adj = doPut("/api/production/prod-orders/" + id + "/materials", admin,
                Map.of("reason", "改用量", "lines", List.of(Map.of("id", screwLine, "requiredQty", 300))));
        assertThat(adj.at("/msg").asText()).contains("应领数量不能小于已领数量");

        // RPT-T01 首道报工；后道可报 = 上道合格
        String r10 = reportOk(report(id, 10, 100, 0, 0));
        JsonNode tooMany = doPost("/api/production/reports", admin, report(id, 20, 110, 0, 0));
        assertError(tooMany, "本工序可报数量为 100");
        JsonNode ctx = ok(doGet("/api/production/reports/context?prodOrderId=" + id + "&seq=20", admin));
        assertThat(ctx.at("/reportableQty").decimalValue()).isEqualByComparingTo("100");

        // 不良明细合计需与不良数一致
        Map<String, Object> r = report(id, 20, 90, 5, 5);
        r.put("defects", List.of(Map.of("defectCode", "SHORT", "qty", 2)));
        assertError(doPost("/api/production/reports", admin, r), "不良明细合计 2 与不良数 5 不一致");
        r.put("defects", List.of(Map.of("defectCode", "SHORT", "qty", 3), Map.of("defectCode", "COLD_SOLDER", "qty", 2)));
        String r20 = reportOk(r);
        assertThat(r10).isNotEqualTo(r20);
        assertThat(ItProductionConfig.REPORTS.stream().anyMatch(e -> e.getReportId().toString().equals(r20))).isTrue();

        // ISS-T05 倒冲：(90 + 5) × 0.5 = 47.5，PCS 精度 0 向上取整为 48
        JsonNode rd = ok(doGet("/api/production/reports/" + r20, admin));
        assertThat(rd.at("/backflushNos").size()).isEqualTo(1);
        String bf = jdbc.queryForObject("select id from mfg_issue where report_id = ? and deleted = 0", Long.class, Long.valueOf(r20)).toString();
        JsonNode bfDoc = ok(doGet("/api/production/issues/" + bf, admin));
        assertThat(bfDoc.at("/issueType").asText()).isEqualTo("BACKFLUSH");
        assertThat(bfDoc.at("/lines/0/requestQty").decimalValue()).isEqualByComparingTo("48");
        if (!"COMPLETED".equals(bfDoc.at("/status").asText())) confirmIssue(bf, null, null);
        assertThat(materialLine(id, glue).at("/issuedQty").decimalValue()).isEqualByComparingTo("48");

        // DEF-T01 不良处置：返修 4（第 1 条 SHORT 3 返修 3，第 2 条返修 1、报废 1）
        JsonNode defects = ok(doGet("/api/production/defects?pageNo=1&pageSize=10&prodOrderId=" + id, admin)).at("/list");
        assertThat(defects.size()).isEqualTo(2);
        String dShort = null, dCold = null;
        for (JsonNode d : defects) {
            if ("SHORT".equals(d.at("/defectCode").asText())) dShort = d.at("/id").asText();
            else dCold = d.at("/id").asText();
        }
        assertError(doPost("/api/production/defects/" + dShort + "/repair", admin, Map.of("qty", 4)), "处置数量超过待处理数量");
        ok(doPost("/api/production/defects/" + dShort + "/repair", admin, Map.of("qty", 3)));
        ok(doPost("/api/production/defects/" + dCold + "/repair", admin, Map.of("qty", 1)));
        ok(doPost("/api/production/defects/" + dCold + "/scrap", admin, Map.of("qty", 1, "scrapReason", "PROCESS")));
        JsonNode o = order(id);
        assertThat(o.at("/completedQty").decimalValue()).isEqualByComparingTo("94");
        assertThat(o.at("/scrappedQty").decimalValue()).isEqualByComparingTo("6");
        assertThat(o.at("/pendingDefectQty").decimalValue()).isEqualByComparingTo("0");
        assertThat(o.at("/finishableQty").decimalValue()).isEqualByComparingTo("94");

        // 良率：投入 100，一次合格 90，最终 94
        JsonNode y = ok(doGet("/api/production/reports/yield?materialId=" + prod + "&operationSeq=20&groupBy=PRODUCT", admin));
        assertThat(y.at("/rows/0/inputQty").decimalValue()).isEqualByComparingTo("100");
        assertThat(y.at("/rows/0/firstYield").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(y.at("/rows/0/finalYield").decimalValue()).isEqualByComparingTo("0.94");

        // FIN-T01 完工入库 94 → 仓库确认 → 已完工
        String fin = ok(doPost("/api/production/prod-orders/" + id + "/finish", admin, Map.of("qty", 94))).asText();
        String ins = jdbc.queryForObject("select stock_in_ids from mfg_finish where id = ?", String.class, Long.valueOf(fin));
        confirmStockIns(ins);
        o = order(id);
        assertThat(o.at("/prodStatus").asText()).isEqualTo("COMPLETED");
        assertThat(o.at("/qualifiedStockedQty").decimalValue()).isEqualByComparingTo("94");
        assertThat(ItProductionConfig.COMPLETED.stream().anyMatch(e -> e.getProdOrderId().toString().equals(id))).isTrue();
        assertError(doPost("/api/production/prod-orders/" + id + "/finish", admin, Map.of("qty", 1)), "可申请入库数量为 0");
        JsonNode fins = ok(doGet("/api/production/finishes?pageNo=1&pageSize=10&prodOrderId=" + id, admin)).at("/list");
        assertThat(fins.at("/0/finishStatus").asText()).isEqualTo("JUDGED");

        // 报工已申请入库：不能反审核
        assertError(doPost("/api/production/reports/" + r20 + "/unapprove", admin, null), "已申请完工入库，不能反审核");

        // PO-T08 关闭：余料 418 − 100 × 4 = 18 需确认；退回后关闭
        JsonNode close = doPost("/api/production/prod-orders/" + id + "/close", admin, Map.of("reason", "完工"));
        assertThat(close.at("/msg").asText()).contains("还有余料未退回").contains("18");
        assertThat(close.at("/data/needConfirm").asBoolean()).isTrue();
        JsonNode rc = ok(doGet("/api/production/returns/candidates?prodOrderId=" + id + "&returnType=GOOD", admin));
        JsonNode screwCand = null;
        for (JsonNode c : rc) if (c.at("/materialId").asText().equals(screw)) screwCand = c;
        assertThat(screwCand).isNotNull();
        assertThat(screwCand.at("/returnableQty").decimalValue()).isEqualByComparingTo("18");
        JsonNode ret = ok(doPost("/api/production/returns", admin, Map.of("prodOrderId", id, "returnType", "GOOD",
                "lines", List.of(Map.of("materialLineId", screwLine, "qty", 19)))));
        String retId = ret.at("/ids/0").asText();
        assertThat(doPost("/api/production/returns/" + retId + "/submit", admin, null).at("/msg").asText()).contains("退料数量超过可退数量 18");
        ok(doPut("/api/production/returns/" + retId, admin, Map.of("prodOrderId", id, "returnType", "GOOD",
                "lines", List.of(Map.of("materialLineId", screwLine, "qty", 18)), "version", ok(doGet("/api/production/returns/" + retId, admin)).at("/version").asInt())));
        ok(doPost("/api/production/returns/" + retId + "/submit", admin, null));
        assertThat(doPost("/api/production/prod-orders/" + id + "/close", admin, Map.of("reason", "完工")).at("/msg").asText()).contains(ok(doGet("/api/production/returns/" + retId, admin)).at("/docNo").asText());
        confirmStockIns(ok(doGet("/api/production/returns/" + retId, admin)).at("/stockInIds").asText());
        assertThat(materialLine(id, screw).at("/returnedQty").decimalValue()).isEqualByComparingTo("18");
        ok(doPost("/api/production/prod-orders/" + id + "/close", admin, Map.of("reason", "完工")));
        assertThat(order(id).at("/prodStatus").asText()).isEqualTo("CLOSED");

        // RPT 用料差异：螺丝理论 (94 + 6) × 4 × 1.02 = 408，净耗用 400，超领原因
        JsonNode v = ok(doGet("/api/production/reports/material-variance?prodOrderId=" + id + "&materialId=" + screw, admin));
        assertThat(v.at("/0/theoreticalQty").decimalValue()).isEqualByComparingTo("408");
        assertThat(v.at("/0/netQty").decimalValue()).isEqualByComparingTo("400");
        assertThat(v.at("/0/varianceQty").decimalValue()).isEqualByComparingTo("-8");
        assertThat(v.at("/0/overReasons").asText()).isNotBlank();

        // 进度、计划达成、产出工时报表可查询
        ok(doGet("/api/production/reports/progress?pageNo=1&pageSize=10", admin));
        ok(doGet("/api/production/reports/output-hours", admin));
        ok(doGet("/api/production/reports/plan-achievement", admin));
    }
}
