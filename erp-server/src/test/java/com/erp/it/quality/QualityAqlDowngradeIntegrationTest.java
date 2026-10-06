package com.erp.it.quality;

import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AQL 抽样表维护（10-01）、SCAR 验证期间加严抽样（QC-SCAR-R04）、NCR 降级使用（10-03 DOWNGRADE） */
class QualityAqlDowngradeIntegrationTest extends QualityTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aqlTableMaintenance() throws Exception {
        JsonNode t = ok(doGet("/api/quality/aql-table", admin));
        assertThat(t.at("/codes").size()).isEqualTo(105);
        assertThat(t.at("/rows").size()).isEqualTo(272);
        JsonNode row = null;
        for (JsonNode r : t.at("/rows")) if ("J".equals(r.at("/codeLetter").asText()) && "0.65".equals(r.at("/aql").asText())) row = r;
        assertThat(row).isNotNull();
        assertThat(row.at("/sampleSize").asInt()).isEqualTo(80);
        String id = row.at("/id").asText();

        assertError(doPut("/api/quality/aql-table/rows/" + id, admin, Map.of("sampleLetter", "J", "sampleSize", 80, "ac", 2, "re", 2,
                "version", row.at("/version").asInt())), "样本量必须大于 0，且 0 ≤ Ac < Re");
        ok(doPut("/api/quality/aql-table/rows/" + id, admin, Map.of("sampleLetter", "J", "sampleSize", 80, "ac", 2, "re", 3,
                "version", row.at("/version").asInt())));
        try {
            JsonNode r = ok(doPost("/api/quality/sampling-plans/preview", admin, Map.of("planId", "1001", "lotQty", "1000")));
            assertThat(level(r, "MA")).isEqualTo("80/2/3");
        } finally {
            ok(doPut("/api/quality/aql-table/rows/" + id, admin, Map.of("sampleLetter", "J", "sampleSize", 80, "ac", 1, "re", 2, "version", version(id))));
        }
        JsonNode r = ok(doPost("/api/quality/sampling-plans/preview", admin, Map.of("planId", "1001", "lotQty", "1000")));
        assertThat(level(r, "MA")).isEqualTo("80/1/2");
    }

    @Test
    void scarTightenedIqc() throws Exception {
        String m = iqcRaw("加严料");
        String s = supplier(m);
        receiveIntoQc(s, m, "1000");
        assertThat(inspection(inspectionOf(m, "IQC").at("/id").asText()).at("/sampleQty").asInt()).isEqualTo(80);

        // SCAR 进入验证 → 后续 IQC 检验水平 II → III（1000 件：J → K，样本量 125）
        Map<String, Object> b = new HashMap<>();
        b.put("source", "INVENTORY");
        b.put("materialId", m);
        b.put("ncrQty", "10");
        b.put("supplierId", s);
        b.put("defectDescription", "来料尺寸超差");
        b.put("severity", "MAJOR");
        b.put("responsibility", "SUPPLIER");
        b.put("dispositions", List.of(Map.of("disposition", "SCRAP", "qty", "10")));
        String ncr = ok(doPost("/api/quality/ncrs", admin, b)).asText();
        ok(doPost("/api/quality/ncrs/" + ncr + "/submit", admin, null));
        String scar = ok(doPost("/api/quality/ncrs/" + ncr + "/create-scar", admin, null)).asText();
        ok(doPost("/api/quality/scars/" + scar + "/send", admin, null));
        ok(doPost("/api/quality/scars/" + scar + "/reply", admin, Map.of("content", "8D", "fileIds", List.of(uploadPdf()))));
        ok(doPost("/api/quality/scars/" + scar + "/start-verify", admin, Map.of("verifyPlan", "后续 3 批加严检验")));

        receiveIntoQc(s, m, "1000");
        JsonNode d = inspection(inspectionOf(m, "IQC").at("/id").asText());
        assertThat(d.at("/sampleQty").asInt()).isEqualTo(125);
        assertThat(d.at("/sampling/inspectionLevel").asText()).isEqualTo("III");
        assertThat(d.at("/sampling/text").asText()).contains("加严");

        // 参数关闭 → 正常抽样；其他供应商不受影响
        setParam("qc.scar.tightened-iqc", "false");
        try {
            receiveIntoQc(s, m, "1000");
            assertThat(inspection(inspectionOf(m, "IQC").at("/id").asText()).at("/sampleQty").asInt()).isEqualTo(80);
        } finally {
            resetParam("qc.scar.tightened-iqc");
        }

        // 验证有效结案 → 恢复正常抽样
        ok(doPost("/api/quality/scars/" + scar + "/verify", admin, Map.of("result", "EFFECTIVE")));
        receiveIntoQc(s, m, "1000");
        assertThat(inspection(inspectionOf(m, "IQC").at("/id").asText()).at("/sampleQty").asInt()).isEqualTo(80);
    }

    @Test
    void ncrDowngrade() throws Exception {
        String m = material("降级料", CAT_RAW, "RAW", Map.of("sourceType", "PURCHASE"));
        String target = material("降级后物料", CAT_RAW, "RAW", Map.of("sourceType", "PURCHASE"));
        Long in = docApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, new SourceRef("QC_IT", Long.valueOf(uniq()), "QC_IT"), Long.valueOf(W_NG),
                null, null, null, List.of(new StockInRequest.Line(1L, Long.valueOf(m), null, new BigDecimal("10"), null, null, LocalDate.now(), null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
        BigDecimal targetBefore = onHand(target, W_RAW);

        Map<String, Object> b = new HashMap<>();
        b.put("source", "INVENTORY");
        b.put("materialId", m);
        b.put("ncrQty", "10");
        b.put("defectDescription", "外观轻微划伤，可降级用于内部治具");
        b.put("severity", "MINOR");
        b.put("responsibility", "PROCESS");
        b.put("capaRequired", false);
        b.put("scarRequired", false);
        b.put("dispositions", List.of(Map.of("disposition", "DOWNGRADE", "qty", "10")));
        assertError(doPost("/api/quality/ncrs", admin, b), "降级使用请选择降级后的物料（不能与不合格物料相同）");
        b.put("dispositions", List.of(Map.of("disposition", "DOWNGRADE", "qty", "10", "targetMaterialId", target)));
        String ncr = ok(doPost("/api/quality/ncrs", admin, b)).asText();
        ok(doPost("/api/quality/ncrs/" + ncr + "/submit", admin, null));
        JsonNode n = ok(doGet("/api/quality/ncrs/" + ncr, admin));
        assertThat(n.at("/status").asText()).isEqualTo("APPROVED");
        assertThat(n.at("/dispositions/0/targetMaterialId").asText()).isEqualTo(target);

        String msg = ok(doPost("/api/quality/ncrs/" + ncr + "/create-downgrade", admin, null)).asText();
        assertThat(msg).startsWith("降级 ");
        assertError(doPost("/api/quality/ncrs/" + ncr + "/create-downgrade", admin, null), "已生成降级使用单据：" + msg);

        String dispId = n.at("/dispositions/0/id").asText();
        Long outId = jdbc.queryForObject("select id from inv_stock_out where source_type = 'QC_NCR_DOWNGRADE' and source_id = ?", Long.class, Long.valueOf(dispId));
        Long inId = jdbc.queryForObject("select id from inv_stock_in where source_type = 'QC_NCR_DOWNGRADE' and source_id = ?", Long.class, Long.valueOf(dispId));
        confirmStockOut(String.valueOf(outId));
        ok(doPost("/api/inventory/stock-ins/" + inId + "/confirm", admin, Map.of()));
        assertThat(onHand(m, W_NG)).isEqualByComparingTo("0");
        assertThat(onHand(target, W_RAW).subtract(targetBefore)).isEqualByComparingTo("10");

        // 报废处置：从不良品仓生成报废出库（原因 SCRAP，备注带 NCR 说明）
        Long in2 = docApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, new SourceRef("QC_IT", Long.valueOf(uniq()), "QC_IT"), Long.valueOf(W_NG),
                null, null, null, List.of(new StockInRequest.Line(1L, Long.valueOf(m), null, new BigDecimal("5"), null, null, LocalDate.now(), null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in2 + "/confirm", admin, Map.of()));
        b.put("ncrQty", "5");
        b.put("dispositions", List.of(Map.of("disposition", "SCRAP", "qty", "5")));
        String ncr2 = ok(doPost("/api/quality/ncrs", admin, b)).asText();
        ok(doPost("/api/quality/ncrs/" + ncr2 + "/submit", admin, null));
        ok(doPost("/api/quality/ncrs/" + ncr2 + "/create-scrap-out", admin, null));
        Long scrapOut = jdbc.queryForObject("select id from inv_stock_out where source_type = 'QC_NCR' and source_id = ?", Long.class, Long.valueOf(ncr2));
        confirmStockOut(String.valueOf(scrapOut));
        assertThat(onHand(m, W_NG)).isEqualByComparingTo("0");
    }

    private void confirmStockOut(String outId) throws Exception {
        JsonNode d = ok(doGet("/api/inventory/stock-outs/" + outId, admin));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode l : d.at("/lines")) {
            Map<String, Object> x = new HashMap<>();
            x.put("id", l.at("/id").asText());
            x.put("materialId", l.at("/materialId").asText());
            x.put("uom", l.at("/uom").asText());
            x.put("requestQty", l.at("/requestQty").decimalValue());
            x.put("qty", l.at("/requestQty").decimalValue());
            x.put("sourceLineId", l.at("/sourceLineId").asText());
            lines.add(x);
        }
        ok(doPost("/api/inventory/stock-outs/" + outId + "/confirm", admin, Map.of("outLines", lines)));
    }

    private int version(String rowId) throws Exception {
        for (JsonNode r : ok(doGet("/api/quality/aql-table", admin)).at("/rows")) if (r.at("/id").asText().equals(rowId)) return r.at("/version").asInt();
        return 0;
    }

    private static String level(JsonNode r, String level) {
        for (JsonNode l : r.at("/levels")) {
            if (level.equals(l.at("/level").asText())) return l.at("/n").asInt() + "/" + l.at("/ac").asInt() + "/" + l.at("/re").asInt();
        }
        return null;
    }
}
