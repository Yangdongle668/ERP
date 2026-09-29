package com.erp.it.quality;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 10-03 NCR（手工）、10-04 CAPA、10-05 客诉、10-06 SCAR、10-07 报表 */
class QualityFollowupIntegrationTest extends QualityTestSupport {

    private String manualNcr(String material, String supplier, String severity, List<Map<String, Object>> disps) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("source", "INVENTORY");
        b.put("materialId", material);
        b.put("ncrQty", "10");
        b.put("supplierId", supplier);
        b.put("defectDescription", "库存盘点发现外观破损 10 件");
        b.put("defectCodes", List.of("D-BROKEN"));
        b.put("severity", severity);
        b.put("responsibility", "SUPPLIER");
        b.put("dispositions", disps);
        return ok(doPost("/api/quality/ncrs", admin, b)).asText();
    }

    /** QC-CAPA-T01～T03：NCR 生成 CAPA（D2 带出描述）→ 按顺序完成 → D5 后待验证 → 无效退回 D4 → 有效 → D7 → 结案 */
    @Test
    void capaEightD() throws Exception {
        String m = material("CAPA料", CAT_RAW, "RAW", Map.of("sourceType", "PURCHASE"));
        String s = supplier(m);
        String ncr = manualNcr(m, s, "MAJOR", List.of(Map.of("disposition", "SCRAP", "qty", "10")));
        String capa = ok(doPost("/api/quality/ncrs/" + ncr + "/create-capa", admin, null)).asText();
        JsonNode c = ok(doGet("/api/quality/capas/" + capa, admin));
        assertThat(c.at("/source").asText()).isEqualTo("NCR");
        assertThat(c.at("/d2Problem").asText()).isEqualTo("库存盘点发现外观破损 10 件");
        assertThat(c.at("/currentStep").asInt()).isEqualTo(1);
        assertThat(ok(doGet("/api/quality/ncrs/" + ncr, admin)).at("/capaId").asText()).isEqualTo(capa);

        assertError(doPost("/api/quality/capas/" + capa + "/steps/2/complete", admin, Map.of()), "请按顺序完成步骤，当前为 D1");
        complete(capa, 1, "QE、工程、生产");
        complete(capa, 2, null);
        complete(capa, 3, "冻结库存，全检在制品");
        assertError(doPost("/api/quality/capas/" + capa + "/steps/4/complete", admin, Map.of()), "请填写 D4 内容");
        complete(capa, 4, "包装防护不足");
        complete(capa, 5, "改用加厚纸箱");
        assertThat(ok(doGet("/api/quality/capas/" + capa, admin)).at("/status").asText()).isEqualTo("VERIFYING");

        ok(doPost("/api/quality/capas/" + capa + "/verify", admin, Map.of("result", "INEFFECTIVE", "content", "后续 2 批仍有破损")));
        c = ok(doGet("/api/quality/capas/" + capa, admin));
        assertThat(c.at("/status").asText()).isEqualTo("OPEN");
        assertThat(c.at("/currentStep").asInt()).isEqualTo(4);
        assertThat(c.at("/invalidCount").asInt()).isEqualTo(1);
        assertThat(c.at("/verifyHistory").asText()).contains("无效");

        complete(capa, 4, "搬运跌落");
        complete(capa, 5, "增加护角");
        ok(doPost("/api/quality/capas/" + capa + "/verify", admin, Map.of("result", "EFFECTIVE", "content", "后续 3 批无破损")));
        assertThat(ok(doGet("/api/quality/capas/" + capa, admin)).at("/currentStep").asInt()).isEqualTo(7);
        assertError(doPost("/api/quality/capas/" + capa + "/close", admin, Map.of("text", "x")), "D1～D7 全部完成后才能结案");
        complete(capa, 7, "修订包装规范并横向展开");
        ok(doPost("/api/quality/capas/" + capa + "/close", admin, Map.of("text", "问题关闭")));
        c = ok(doGet("/api/quality/capas/" + capa, admin));
        assertThat(c.at("/status").asText()).isEqualTo("CLOSED");
        assertThat(ok(doGet("/api/quality/capas/" + capa + "/print-data", admin)).at("/d8Summary").asText()).isEqualTo("问题关闭");
    }

    private void complete(String capa, int step, String content) throws Exception {
        ok(doPost("/api/quality/capas/" + capa + "/steps/" + step + "/complete", admin, content == null ? Map.of() : Map.of("content", content)));
    }

    /** QC-NCR-T05（重复次数触发 CAPA）、QC-SCAR-T01～T03 */
    @Test
    void ncrRepeatAndScar() throws Exception {
        String m = material("SCAR料", CAT_RAW, "RAW", Map.of("sourceType", "PURCHASE"));
        String s = supplier(m);
        List<Map<String, Object>> scrap = List.of(Map.of("disposition", "SCRAP", "qty", "10"));
        String n1 = manualNcr(m, s, "MINOR", scrap);
        assertThat(ok(doGet("/api/quality/ncrs/" + n1, admin)).at("/capaRequired").asBoolean()).isFalse();
        manualNcr(m, s, "MINOR", scrap);
        String n3 = manualNcr(m, s, "MINOR", scrap);
        assertThat(ok(doGet("/api/quality/ncrs/" + n3, admin)).at("/capaRequired").asBoolean()).isTrue();

        ok(doPost("/api/quality/ncrs/" + n1 + "/submit", admin, null));
        String scar = ok(doPost("/api/quality/ncrs/" + n1 + "/create-scar", admin, null)).asText();
        JsonNode sc = ok(doGet("/api/quality/scars/" + scar, admin));
        assertThat(sc.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(sc.at("/supplierId").asText()).isEqualTo(s);
        ok(doPost("/api/quality/scars/" + scar + "/send", admin, null));
        sc = ok(doGet("/api/quality/scars/" + scar, admin));
        assertThat(sc.at("/status").asText()).isEqualTo("SENT");
        assertThat(sc.at("/replyDueDate").asText()).isEqualTo(LocalDate.now().plusDays(7).toString());

        assertError(doPost("/api/quality/scars/" + scar + "/reply", admin, Map.of("content", "已改善")), "请上传供应商的回复文件");
        ok(doPost("/api/quality/scars/" + scar + "/reply", admin, Map.of("content", "8D 报告见附件", "fileIds", List.of(uploadPdf()))));
        ok(doPost("/api/quality/scars/" + scar + "/start-verify", admin, Map.of("verifyPlan", "后续 3 批加严检验")));
        ok(doPost("/api/quality/scars/" + scar + "/verify", admin, Map.of("result", "INEFFECTIVE")));
        sc = ok(doGet("/api/quality/scars/" + scar, admin));
        assertThat(sc.at("/status").asText()).isEqualTo("SENT");
        assertThat(sc.at("/invalidCount").asInt()).isEqualTo(1);
        ok(doPost("/api/quality/scars/" + scar + "/reply", admin, Map.of("content", "二次改善", "fileIds", List.of(uploadPdf()))));
        ok(doPost("/api/quality/scars/" + scar + "/start-verify", admin, Map.of("verifyPlan", "再跟踪 3 批")));
        ok(doPost("/api/quality/scars/" + scar + "/verify", admin, Map.of("result", "EFFECTIVE")));
        assertThat(ok(doGet("/api/quality/scars/" + scar, admin)).at("/status").asText()).isEqualTo("CLOSED");

        JsonNode rpt = ok(doGet("/api/quality/reports/ncr-capa-complaint?supplierId=" + s, admin));
        assertThat(rpt.at("/ncrCount").asInt()).isEqualTo(3);
    }

    /** QC-CPL-T01、T03、T04：致命客诉自动生成 CAPA；未回复不能结案；回复 + 处理结果后结案 */
    @Test
    void complaint() throws Exception {
        String cus = customer();
        Long me = adminId();
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", cus);
        b.put("complaintType", "QUALITY");
        b.put("severity", "CRITICAL");
        b.put("description", "客户产线发现批量短路");
        b.put("receivedAt", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        b.put("qeId", me);
        String critical = ok(doPost("/api/quality/complaints", admin, b)).asText();
        JsonNode c = ok(doGet("/api/quality/complaints/" + critical, admin));
        assertThat(c.at("/capaId").isNull()).isFalse();
        assertThat(c.at("/replyDueDate").asText()).isEqualTo(LocalDate.now().plusDays(3).toString());

        b.put("severity", "MINOR");
        b.put("description", "包装箱有压痕");
        String minor = ok(doPost("/api/quality/complaints", admin, b)).asText();
        assertError(doPost("/api/quality/complaints/" + minor + "/close", admin, null), "结案前需要：客户回复、处理结果");
        ok(doPost("/api/quality/complaints/" + minor + "/start", admin, null));
        ok(doPost("/api/quality/complaints/" + minor + "/reply", admin, Map.of("content", "已加强包装防护", "rootCause", "运输挤压")));
        ok(doPost("/api/quality/complaints/" + minor + "/handling", admin, Map.of("handling", "CREDIT", "claimAmount", "800", "agreedAmount", "500", "currency", "USD")));
        ok(doPost("/api/quality/complaints/" + minor + "/close", admin, null));
        c = ok(doGet("/api/quality/complaints/" + minor, admin));
        assertThat(c.at("/status").asText()).isEqualTo("CLOSED");
        assertThat(c.at("/repliedAt").isNull()).isFalse();

        JsonNode list = ok(doGet("/api/quality/complaints?customerId=" + cus + "&statuses=OPEN", admin)).at("/list");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).at("/id").asText()).isEqualTo(critical);
    }
}
