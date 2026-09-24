package com.erp.it.inventory;

import com.erp.module.inventory.api.doc.StockOutType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 入库单、出库单（需求 08-03、08-04 验收用例） */
class StockDocIntegrationTest extends InventoryTestSupport {

    @Test
    void purchaseInRoutesByInspectionAndUnconfirmReverses() throws Exception {
        String iqc = material(CAT_ELEC, "需检料", Map.of("tracking", "BATCH", "iqcRequired", true));
        String free = material(CAT_ELEC, "免检料", Map.of("tracking", "BATCH", "iqcRequired", false));
        // 需检 → 待检仓；免检 → 类别默认仓（电子料仓）
        Long a = receive(iqc, null, "100", "BQ" + uniq(), null);
        Long b = receive(free, null, "50", null, null);
        assertThat(onHand(iqc, W_QC)).isEqualByComparingTo("100");
        assertThat(onHand(free, W_ELEC)).isEqualByComparingTo("50");
        JsonNode detail = ok(doGet("/api/inventory/stock-ins/" + b, admin));
        assertThat(detail.at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.at("/lines/0/batchNo").asText()).as("批次物料未填批次号时自动生成").isNotBlank();

        // 流水
        JsonNode txns = ok(doGet("/api/inventory/stock-txns?materialId=" + iqc, admin));
        assertThat(txns.at("/total").asInt()).isEqualTo(1);
        assertThat(txns.at("/list/0/inQty").decimalValue()).isEqualByComparingTo("100");

        // 重复确认
        assertError(doPost("/api/inventory/stock-ins/" + a + "/confirm", admin, Map.of()), "当前状态【已完成】不允许执行【确认入库】操作");
        // 反确认：库存冲回，单据回到草稿
        assertError(doPost("/api/inventory/stock-ins/" + a + "/unconfirm", admin, Map.of("reason", "")), "请填写反确认原因");
        ok(doPost("/api/inventory/stock-ins/" + a + "/unconfirm", admin, Map.of("reason", "数量错误")));
        assertThat(onHand(iqc, W_QC)).isEqualByComparingTo("0");
        assertThat(ok(doGet("/api/inventory/stock-ins/" + a, admin)).at("/status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void otherInWarehouseTypeAndDirectApproval() throws Exception {
        String m = material(CAT_AUX, "辅料", Map.of("tracking", "NONE", "iqcRequired", false));
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", W_QC);
        body.put("reason", "FOUND");
        body.put("lines", List.of(Map.of("materialId", m, "qty", 5)));
        assertError(doPost("/api/inventory/stock-ins", admin, body), "其他入库只能入【可用仓、不良品仓】");
        body.put("warehouseId", W_AUX);
        body.remove("reason");
        assertError(doPost("/api/inventory/stock-ins", admin, body), "请选择入库原因");
        body.put("reason", "FOUND");
        String id = ok(doPost("/api/inventory/stock-ins", admin, body)).asText();
        // 未配置审批流：提交即审核并确认入库
        assertThat(ok(doPost("/api/inventory/stock-ins/" + id + "/submit", admin, null)).asText()).isEqualTo("COMPLETED");
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("5");
    }

    /** INV-OUT-T01、T02、T03：FIFO 自动分配、拆行、库存不足 */
    @Test
    void autoAllocateFifoAndShortage() throws Exception {
        String m = material(CAT_ELEC, "FIFO料", Map.of("tracking", "BATCH", "iqcRequired", false, "issueRule", "FIFO"));
        String b1 = "B1" + uniq();
        String b2 = "B2" + uniq();
        receive(m, W_ELEC, "300", b1, monthStart());
        receive(m, W_ELEC, "400", b2, null);

        Long out = issue(StockOutType.PRODUCTION_ISSUE, m, W_ELEC, "500");
        JsonNode lines = ok(doPost("/api/inventory/stock-outs/" + out + "/auto-allocate", admin, null));
        assertThat(lines.size()).isEqualTo(2);
        assertThat(lines.at("/0/batchNo").asText()).isEqualTo(b1);
        assertThat(lines.at("/0/qty").decimalValue()).isEqualByComparingTo("300");
        assertThat(lines.at("/1/batchNo").asText()).isEqualTo(b2);
        assertThat(lines.at("/1/qty").decimalValue()).isEqualByComparingTo("200");

        // 库存只有 700，申请 800：多出一行库存不足 100
        Long big = issue(StockOutType.PRODUCTION_ISSUE, m, W_ELEC, "800");
        JsonNode l2 = ok(doPost("/api/inventory/stock-outs/" + big + "/auto-allocate", admin, null));
        assertThat(l2.size()).isEqualTo(3);
        assertThat(l2.at("/2/shortage").decimalValue()).isEqualByComparingTo("100");
        assertThat(l2.at("/2/batchNo").isMissingNode()).isTrue();

        // 用分配结果确认第一张（实发 500）
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of("outLines", toSave(lines))));
        assertThat(onHand(m, W_ELEC)).isEqualByComparingTo("200");
        JsonNode detail = ok(doGet("/api/inventory/stock-outs/" + out, admin));
        assertThat(detail.at("/status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.at("/lines").size()).isEqualTo(2);

        // 领料可以少发：第二张只剩 200 可发，去掉不足行后实发 200 ≤ 申请 800
        JsonNode l3 = ok(doPost("/api/inventory/stock-outs/" + big + "/auto-allocate", admin, null));
        List<Map<String, Object>> save = new ArrayList<>();
        for (JsonNode n : l3) if (!n.hasNonNull("shortage")) save.addAll(toSave(List.of(n)));
        ok(doPost("/api/inventory/stock-outs/" + big + "/confirm", admin, Map.of("outLines", save)));
        assertThat(onHand(m, W_ELEC)).isEqualByComparingTo("0");
    }

    /** INV-OUT-T05：FEFO 不分配过期批次 */
    @Test
    void fefoSkipsExpiredBatch() throws Exception {
        String m = material(CAT_ELEC, "FEFO料", Map.of("tracking", "BATCH", "iqcRequired", false, "issueRule", "FEFO", "shelfLifeDays", 30));
        String expired = "BX" + uniq();
        String fresh = "BF" + uniq();
        // 过期批次：生产日期 60 天前（保质期 30 天）
        Long in1 = docApi.createStockIn(new com.erp.module.inventory.api.doc.StockInRequest(com.erp.module.inventory.api.doc.StockInType.PURCHASE_IN,
                source("PUR_RECEIPT"), Long.valueOf(W_ELEC), null, null, null, List.of(new com.erp.module.inventory.api.doc.StockInRequest.Line(1L,
                Long.valueOf(m), null, new java.math.BigDecimal("50"), expired, null, java.time.LocalDate.now().minusDays(60), null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in1 + "/confirm", admin, Map.of()));
        Long in2 = docApi.createStockIn(new com.erp.module.inventory.api.doc.StockInRequest(com.erp.module.inventory.api.doc.StockInType.PURCHASE_IN,
                source("PUR_RECEIPT"), Long.valueOf(W_ELEC), null, null, null, List.of(new com.erp.module.inventory.api.doc.StockInRequest.Line(1L,
                Long.valueOf(m), null, new java.math.BigDecimal("50"), fresh, null, java.time.LocalDate.now(), null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in2 + "/confirm", admin, Map.of()));

        Long out = issue(StockOutType.PRODUCTION_ISSUE, m, W_ELEC, "30");
        JsonNode lines = ok(doPost("/api/inventory/stock-outs/" + out + "/auto-allocate", admin, null));
        assertThat(lines.size()).isEqualTo(1);
        assertThat(lines.at("/0/batchNo").asText()).isEqualTo(fresh);
        // 指定过期批次出库被拒绝
        Map<String, Object> line = new HashMap<>(toSave(lines).get(0));
        line.put("batchNo", expired);
        JsonNode r = doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of("outLines", List.of(line)));
        assertThat(r.at("/msg").asText()).startsWith("批次「" + expired + "」已过期");
        // 可用量不含过期批次
        assertThat(materialSummary(m).at("/availableQty").decimalValue()).isEqualByComparingTo("50");
    }

    /** INV-OUT-T04：销售出库实发必须等于申请 */
    @Test
    void salesOutMustEqualRequest() throws Exception {
        String m = material(CAT_AUX, "成品辅料", Map.of("tracking", "NONE", "iqcRequired", false));
        receive(m, W_AUX, "200", null, null);
        Long out = issue(StockOutType.SALES_OUT, m, W_AUX, "100");
        JsonNode d = ok(doGet("/api/inventory/stock-outs/" + out, admin));
        Map<String, Object> line = new HashMap<>();
        line.put("id", d.at("/lines/0/id").asText());
        line.put("sourceLineId", d.at("/lines/0/sourceLineId").asText());
        line.put("qty", 90);
        assertError(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of("outLines", List.of(line))), "销售出库实发数量必须等于申请数量 100");
        line.put("qty", 120);
        assertError(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of("outLines", List.of(line))), "销售出库实发数量必须等于申请数量 100");
        line.put("qty", 100);
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of("outLines", List.of(line))));
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("100");
        // 反确认
        ok(doPost("/api/inventory/stock-outs/" + out + "/unconfirm", admin, Map.of("reason", "客户取消")));
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("200");
    }

    /** INV-OUT-T06 + 库存不足 */
    @Test
    void scrapNeedsExplainAndStockCheck() throws Exception {
        String m = material(CAT_AUX, "报废料", Map.of("tracking", "NONE", "iqcRequired", false));
        receive(m, W_AUX, "10", null, null);
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", W_AUX);
        body.put("reason", "SCRAP");
        body.put("lines", List.of(Map.of("materialId", m, "qty", 3)));
        String id = ok(doPost("/api/inventory/stock-outs", admin, body)).asText();
        assertError(doPost("/api/inventory/stock-outs/" + id + "/submit", admin, null), "报废出库请上传附件或填写说明");
        JsonNode d = ok(doGet("/api/inventory/stock-outs/" + id, admin));
        body.put("remark", "受潮报废");
        body.put("version", d.at("/version").asInt());
        ok(doPut("/api/inventory/stock-outs/" + id, admin, body));
        assertThat(ok(doPost("/api/inventory/stock-outs/" + id + "/submit", admin, null)).asText()).isEqualTo("COMPLETED");
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("7");

        // 库存不足
        body.put("reason", "SAMPLE");
        body.put("lines", List.of(Map.of("materialId", m, "qty", 8)));
        body.remove("version");
        String id2 = ok(doPost("/api/inventory/stock-outs", admin, body)).asText();
        JsonNode r = doPost("/api/inventory/stock-outs/" + id2 + "/submit", admin, null);
        assertThat(r.at("/msg").asText()).isEqualTo("物料「" + code(m) + "」在仓库「辅料仓」库存不足，需要 8，现存 7");
        // 其他出库不能从待检仓出
        body.put("warehouseId", W_QC);
        assertError(doPost("/api/inventory/stock-outs", admin, body), "其他出库只能从【可用仓】出库");
    }

    private static List<Map<String, Object>> toSave(Iterable<JsonNode> allocated) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode n : allocated) {
            Map<String, Object> m = new HashMap<>();
            if (n.hasNonNull("id")) m.put("id", n.get("id").asText());
            if (n.hasNonNull("sourceLineId")) m.put("sourceLineId", n.get("sourceLineId").asText());
            m.put("uom", n.get("uom").asText());
            m.put("qty", n.get("qty").decimalValue());
            if (n.hasNonNull("batchNo")) m.put("batchNo", n.get("batchNo").asText());
            if (n.hasNonNull("locationId")) m.put("locationId", n.get("locationId").asText());
            list.add(m);
        }
        return list;
    }
}
