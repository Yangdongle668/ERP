package com.erp.it.quality;

import com.erp.common.exception.BizException;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.production.api.report.IpqcTriggerEvent;
import com.erp.module.quality.api.inspection.InspectionApi;
import com.erp.module.quality.api.inspection.InspectionQueryApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 10-01 检验基础数据、10-02 检验单、10-03 NCR/MRB（与检验单联动部分） */
class QualityInspectionIntegrationTest extends QualityTestSupport {

    @Autowired
    InspectionApi inspectionApi;
    @Autowired
    InspectionQueryApi inspectionQueryApi;

    /** QC-STD-T01、T02：GB2828 样本量与 Ac/Re；5000 → 200（L 字码） */
    @Test
    void samplingPreview() throws Exception {
        JsonNode r = ok(doPost("/api/quality/sampling-plans/preview", admin, Map.of("planId", "1001", "lotQty", "1000")));
        assertThat(r.at("/sampleQty").asInt()).isEqualTo(80);
        assertThat(r.at("/letter").asText()).isEqualTo("J");
        assertThat(level(r, "MA")).isEqualTo("80/1/2");
        assertThat(level(r, "MI")).isEqualTo("80/3/4");
        assertThat(level(r, "CR")).isEqualTo("80/0/1");

        r = ok(doPost("/api/quality/sampling-plans/preview", admin, Map.of("planId", "1001", "lotQty", "5")));
        assertThat(r.at("/sampleQty").asInt()).isEqualTo(5);
        assertThat(r.at("/full").asBoolean()).isTrue();

        r = ok(doPost("/api/quality/sampling-plans/preview", admin, Map.of("planId", "1001", "lotQty", "5000")));
        assertThat(r.at("/sampleQty").asInt()).isEqualTo(200);
        assertThat(level(r, "MA")).isEqualTo("200/3/4");
        assertThat(level(r, "MI")).isEqualTo("200/7/8");

        // QC-STD-R04
        assertError(doPost("/api/quality/sampling-plans", admin, Map.of("code", "P" + uniq(), "name", "缺 AQL", "planType", "GB2828", "inspectionLevel", "II")),
                "请填写检验水平和 AQL");
    }

    private static String level(JsonNode r, String level) {
        for (JsonNode l : r.at("/levels")) {
            if (level.equals(l.at("/level").asText())) return l.at("/n").asInt() + "/" + l.at("/ac").asInt() + "/" + l.at("/re").asInt();
        }
        return null;
    }

    /** QC-INS-T01、T03：到货入待检仓生成 IQC → 录入 → 判定合格 → 检验调拨 → 已处理；到货行回写；已判定不能反确认入库 */
    @Test
    void iqcQualified() throws Exception {
        String m = iqcRaw("电阻");
        String s = supplier(m);
        String rc = receiveIntoQc(s, m, "5000");
        JsonNode row = inspectionOf(m, "IQC,RECHECK");
        String id = row.at("/id").asText();
        assertThat(row.at("/status").asText()).isEqualTo("PENDING");
        assertThat(row.at("/sampleQty").asInt()).isEqualTo(200);
        assertThat(row.at("/supplierId").asText()).isEqualTo(s);
        JsonNode d = inspection(id);
        assertThat(d.at("/standardCode").asText()).isEqualTo("QS-GEN-IQC");
        assertThat(d.at("/items").size()).isEqualTo(2);

        // 外观（MI）不良 2 → MI 2 < Re 8，建议合格
        String appearance = d.at("/items/0/id").asText();
        String label = d.at("/items/1/id").asText();
        ok(doPut("/api/quality/inspections/" + id + "/results", admin, Map.of("items", List.of(Map.of("id", appearance, "ngCount", 2), Map.of("id", label, "ngCount", 0)))));
        d = inspection(id);
        assertThat(d.at("/status").asText()).isEqualTo("INSPECTING");
        assertThat(d.at("/miCount").asInt()).isEqualTo(2);
        assertThat(d.at("/suggestedResult").asText()).isEqualTo("PASS");

        // 特采只能走 MRB
        assertError(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "CONCESSION")), "特采需要提交 MRB 审批");
        ok(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "QUALIFIED")));
        d = inspection(id);
        assertThat(d.at("/status").asText()).isEqualTo("JUDGED");
        assertThat(d.at("/qualifiedQty").decimalValue()).isEqualByComparingTo("5000");
        assertThat(d.at("/transferIds").size()).isEqualTo(1);
        JsonNode line = receipt(rc).at("/lines/0");
        assertThat(line.at("/inspectStatus").asText()).isEqualTo("QUALIFIED");
        assertThat(line.at("/qualifiedQty").decimalValue()).isEqualByComparingTo("5000");
        assertThat(line.at("/inspectionNo").asText()).isEqualTo(d.at("/docNo").asText());

        // QC-INS-R07：已判定的入库单不能反确认
        JsonNode unconfirm = doPost("/api/inventory/stock-ins/" + line.at("/stockInId").asText() + "/unconfirm", admin, Map.of("reason", "测试"));
        assertThat(unconfirm.at("/code").asInt()).isNotEqualTo(0);

        confirmTransfers(id);
        assertThat(inspection(id).at("/status").asText()).isEqualTo("HANDLED");
        assertThat(onHand(m, W_RAW)).isEqualByComparingTo("5000");
        assertThat(onHand(m, W_QC)).isEqualByComparingTo("0");

        // 已处理不能重判
        assertError(doPost("/api/quality/inspections/" + id + "/rejudge", admin, Map.of("reason", "复核")), "检验调拨已完成，请先由仓库反确认");

        // 来料质量报表：该供应商 1 批合格
        JsonNode rpt = ok(doGet("/api/quality/reports/iqc?supplierId=" + s, admin));
        assertThat(rpt.at("/total/lots").asInt()).isEqualTo(1);
        assertThat(rpt.at("/total/passRate").decimalValue()).isEqualByComparingTo("100");
    }

    /** QC-INS-T02、T04；QC-STD-T03、T04：物料标准定量项目超限、MA 缺陷达到 Re 建议不合格；新版本生效旧版作废，已建检验单仍按旧版 */
    @Test
    void standardAndSuggestFail() throws Exception {
        String m = iqcRaw("连接器");
        String s = supplier(m);
        String std = ok(doPost("/api/quality/standards", admin, Map.of("name", "连接器 IQC", "inspectType", "IQC", "scopeType", "MATERIAL", "materialId", m,
                "samplingPlanId", "1001", "items", List.of(
                        Map.of("libItemId", "1101", "spec", "无划伤"),
                        Map.of("libItemId", "1104", "name", "长度", "target", "10", "upperLimit", "10.1", "lowerLimit", "9.9", "samplingPlanId", "1003"))))).asText();
        // QC-STD-R02
        assertError(doPost("/api/quality/standards", admin, Map.of("name", "缺上下限", "inspectType", "IQC", "scopeType", "MATERIAL", "materialId", m,
                "samplingPlanId", "1001", "items", List.of(Map.of("libItemId", "1104")))), "项目「尺寸」请填写规格上下限");
        ok(doPost("/api/quality/standards/" + std + "/activate", admin, null));
        JsonNode match = ok(doGet("/api/quality/standards/match?materialId=" + m + "&inspectType=IQC", admin));
        assertThat(match.at("/id").asText()).isEqualTo(std);

        receiveIntoQc(s, m, "5000");
        String id = inspectionOf(m, "IQC").at("/id").asText();
        JsonNode d = inspection(id);
        assertThat(d.at("/standardVersion").asInt()).isEqualTo(1);
        String appearance = d.at("/items/0/id").asText();
        String length = d.at("/items/1/id").asText();
        assertThat(d.at("/items/1/sampleQty").asInt()).isEqualTo(5);

        ok(doPut("/api/quality/inspections/" + id + "/results", admin, Map.of(
                "items", List.of(Map.of("id", appearance, "ngCount", 0), Map.of("id", length, "measuredValues", List.of("10.0", "10.15", "9.95"))),
                "defects", List.of(Map.of("defectCode", "D-BROKEN", "qty", 4)))));
        d = inspection(id);
        assertThat(d.at("/items/1/ngCount").asInt()).isEqualTo(1);
        assertThat(d.at("/items/1/itemResult").asText()).isEqualTo("NG");
        assertThat(d.at("/maCount").asInt()).isEqualTo(4);
        assertThat(d.at("/suggestedResult").asText()).isEqualTo("FAIL");
        assertError(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "QUALIFIED")), "建议结果为不合格，判定合格请填写让步理由");

        // 已生效不能修改；新版本生效后旧版作废
        assertError(doPut("/api/quality/standards/" + std, admin, Map.of("name", "x", "inspectType", "IQC", "scopeType", "MATERIAL", "materialId", m,
                "samplingPlanId", "1001", "items", List.of(Map.of("libItemId", "1101")))), "已生效的检验标准不能修改");
        String v2 = ok(doPost("/api/quality/standards/" + std + "/new-version", admin, null)).asText();
        ok(doPost("/api/quality/standards/" + v2 + "/activate", admin, null));
        assertThat(ok(doGet("/api/quality/standards/" + std, admin)).at("/status").asText()).isEqualTo("OBSOLETE");
        assertThat(ok(doGet("/api/quality/standards/" + v2, admin)).at("/stdVersion").asInt()).isEqualTo(2);
        assertThat(inspection(id).at("/standardVersion").asInt()).isEqualTo(1);

        // 类别标准（成品类别）→ 该类别物料匹配到类别标准
        String cat = ok(doPost("/api/quality/standards", admin, Map.of("name", "成品类 IQC", "inspectType", "IQC", "scopeType", "CATEGORY", "categoryId", CAT_FG,
                "samplingPlanId", "1002", "items", List.of(Map.of("libItemId", "1101"))))).asText();
        ok(doPost("/api/quality/standards/" + cat + "/activate", admin, null));
        String fg = material("外购成品", CAT_FG, "FINISHED", Map.of("sourceType", "PURCHASE", "iqcRequired", true));
        assertThat(ok(doGet("/api/quality/standards/match?materialId=" + fg + "&inspectType=IQC", admin)).at("/id").asText()).isEqualTo(cat);
        ok(doPost("/api/quality/standards/" + cat + "/obsolete", admin, null));
    }

    /** QC-INS-T05、QC-NCR-T01、T03：提交 MRB → NCR 处置特采 4000 + 退货 1000 → 审批通过 → 检验单判定特采，调拨拆两张 */
    @Test
    void mrbConcession() throws Exception {
        String m = iqcRaw("电容");
        String s = supplier(m);
        String rc = receiveIntoQc(s, m, "5000");
        String id = inspectionOf(m, "IQC").at("/id").asText();
        String ncrId = ok(doPost("/api/quality/inspections/" + id + "/to-mrb", admin, null)).asText();
        assertThat(inspection(id).at("/status").asText()).isEqualTo("WAIT_MRB");
        JsonNode ncr = ok(doGet("/api/quality/ncrs/" + ncrId, admin));
        assertThat(ncr.at("/source").asText()).isEqualTo("IQC");
        assertThat(ncr.at("/ncrQty").decimalValue()).isEqualByComparingTo("5000");
        assertThat(ncr.at("/responsibility").asText()).isEqualTo("SUPPLIER");
        assertThat(ncr.at("/scarRequired").asBoolean()).isTrue();

        ok(doPut("/api/quality/ncrs/" + ncrId, admin, ncrBody(ncr, List.of(Map.of("disposition", "CONCESSION", "qty", "4000"),
                Map.of("disposition", "RETURN", "qty", "900")))));
        assertError(doPost("/api/quality/ncrs/" + ncrId + "/submit", admin, null), "处置数量合计 4900 必须等于不合格数量 5000");
        ok(doPut("/api/quality/ncrs/" + ncrId, admin, ncrBody(ncr, List.of(Map.of("disposition", "REWORK", "qty", "5000")))));
        assertError(doPost("/api/quality/ncrs/" + ncrId + "/submit", admin, null), "该来源不支持处置方式「返工」");
        ok(doPut("/api/quality/ncrs/" + ncrId, admin, ncrBody(ncr, List.of(Map.of("disposition", "CONCESSION", "qty", "4000", "remark", "仅用于 FG1"),
                Map.of("disposition", "RETURN", "qty", "1000")))));
        ok(doPost("/api/quality/ncrs/" + ncrId + "/submit", admin, null));
        assertThat(ok(doGet("/api/quality/ncrs/" + ncrId, admin)).at("/status").asText()).isEqualTo("APPROVED");

        JsonNode d = inspection(id);
        assertThat(d.at("/status").asText()).isEqualTo("JUDGED");
        assertThat(d.at("/result").asText()).isEqualTo("CONCESSION");
        assertThat(d.at("/concessionQty").decimalValue()).isEqualByComparingTo("4000");
        assertThat(d.at("/rejectedQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(d.at("/transferIds").size()).isEqualTo(2);
        JsonNode line = receipt(rc).at("/lines/0");
        assertThat(line.at("/concessionQty").decimalValue()).isEqualByComparingTo("4000");
        assertThat(line.at("/rejectedQty").decimalValue()).isEqualByComparingTo("1000");
        confirmTransfers(id);
        assertThat(onHand(m, W_RAW)).isEqualByComparingTo("4000");
        assertThat(onHand(m, W_NG)).isEqualByComparingTo("1000");
        assertThat(inspection(id).at("/status").asText()).isEqualTo("HANDLED");

        // 关闭：退货处置未完成 → 标记完成 → 需要 SCAR → 生成 SCAR → 关闭
        assertError(doPost("/api/quality/ncrs/" + ncrId + "/close", admin, Map.of()), "还有处置未完成");
        for (JsonNode x : ok(doGet("/api/quality/ncrs/" + ncrId, admin)).at("/dispositions")) {
            if (!x.at("/done").asBoolean()) ok(doPost("/api/quality/ncrs/" + ncrId + "/dispositions/" + x.at("/id").asText() + "/done", admin, Map.of("followDocNo", "PRT-TEST")));
        }
        assertError(doPost("/api/quality/ncrs/" + ncrId + "/close", admin, Map.of()), "请先生成 SCAR");
        ok(doPost("/api/quality/ncrs/" + ncrId + "/create-scar", admin, null));
        ok(doPost("/api/quality/ncrs/" + ncrId + "/close", admin, Map.of()));
        assertThat(ok(doGet("/api/quality/ncrs/" + ncrId, admin)).at("/status").asText()).isEqualTo("CLOSED");
    }

    private static Map<String, Object> ncrBody(JsonNode ncr, List<Map<String, Object>> dispositions) {
        return Map.of("defectDescription", ncr.at("/defectDescription").asText(), "severity", ncr.at("/severity").asText(),
                "responsibility", ncr.at("/responsibility").asText(), "capaRequired", false, "scarRequired", true, "dispositions", dispositions);
    }

    /** QC-INS-T06、T07：挑选 4900/100 → 重判（调拨未确认）→ 原调拨作废、回到检验中、到货行恢复待检 → 拒收自动生成 NCR */
    @Test
    void sortRejudgeReject() throws Exception {
        String m = iqcRaw("螺丝");
        String s = supplier(m);
        String rc = receiveIntoQc(s, m, "5000");
        String id = inspectionOf(m, "IQC").at("/id").asText();
        assertError(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "SORTED", "qualifiedQty", "4900", "rejectedQty", "50")),
                "判定数量合计必须等于批量 5000");
        ok(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "SORTED", "qualifiedQty", "4900", "rejectedQty", "100")));
        JsonNode d = inspection(id);
        assertThat(d.at("/transferIds").size()).isEqualTo(2);
        List<String> oldTransfers = List.of(d.at("/transferIds/0").asText(), d.at("/transferIds/1").asText());
        assertThat(receipt(rc).at("/lines/0/inspectStatus").asText()).isEqualTo("PARTIAL");

        ok(doPost("/api/quality/inspections/" + id + "/rejudge", admin, Map.of("reason", "挑选数量录错")));
        d = inspection(id);
        assertThat(d.at("/status").asText()).isEqualTo("INSPECTING");
        assertThat(d.at("/rejudgeCount").asInt()).isEqualTo(1);
        assertThat(d.at("/transferIds").size()).isEqualTo(0);
        for (String t : oldTransfers) assertThat(ok(doGet("/api/inventory/transfers/" + t, admin)).at("/status").asText()).isEqualTo("VOIDED");
        assertThat(receipt(rc).at("/lines/0/inspectStatus").asText()).isEqualTo("PENDING");

        ok(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "REJECTED")));
        d = inspection(id);
        assertThat(d.at("/rejectedQty").decimalValue()).isEqualByComparingTo("5000");
        assertThat(d.at("/ncrId").isNull()).isFalse();
        JsonNode ncr = ok(doGet("/api/quality/ncrs/" + d.at("/ncrId").asText(), admin));
        assertThat(ncr.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(ncr.at("/inspectionNo").asText()).isEqualTo(d.at("/docNo").asText());
        assertThat(receipt(rc).at("/lines/0/inspectStatus").asText()).isEqualTo("REJECTED");
    }

    /** OQC（出货通知）与首件检验（QC-INS-T08）接口 */
    @Test
    void oqcAndFirstArticle() throws Exception {
        String m = material("出货成品", CAT_FG, "FINISHED", Map.of("sourceType", "MAKE", "oqcRequired", true));
        long notice = System.nanoTime() % 1_000_000_000L;
        List<Long> ids = inspectionApi.requestOqc(new InspectionApi.OqcRequest(notice, "SN-" + notice, null, null,
                List.of(new InspectionApi.OqcRequest.Line(1L, Long.valueOf(m), null, new BigDecimal("300")))));
        assertThat(ids).hasSize(1);
        assertThat(inspectionApi.requestOqc(new InspectionApi.OqcRequest(notice, "SN-" + notice, null, null,
                List.of(new InspectionApi.OqcRequest.Line(1L, Long.valueOf(m), null, new BigDecimal("300")))))).isEqualTo(ids);
        assertThat(inspectionQueryApi.isOqcPassed(notice)).isFalse();
        ok(doPost("/api/quality/inspections/" + ids.get(0) + "/judge", admin, Map.of("result", "QUALIFIED")));
        assertThat(inspectionQueryApi.isOqcPassed(notice)).isTrue();
        assertThat(inspectionQueryApi.getByBiz("SHP_NOTICE", notice)).hasSize(1);
        JsonNode counts = ok(doGet("/api/quality/inspections/counts?types=OQC", admin));
        assertThat(counts.at("/todayJudged").asLong()).isGreaterThanOrEqualTo(1);

        setParam("qc.ipqc.first-article", "true");
        try {
            assertThatThrownBy(() -> inspectionQueryApi.checkFirstArticle(-1L)).isInstanceOf(BizException.class).hasMessage("首件检验未通过，不能报工");
        } finally {
            resetParam("qc.ipqc.first-article");
        }
        inspectionQueryApi.checkFirstArticle(-1L);
    }

    @Autowired
    DomainEventPublisher domainEvents;
    @Autowired
    JdbcTemplate jdbc;

    /** 参数 qc.defect.alert-threshold：同一不良当日累计达到阈值时发预警（每个缺陷代码每天一次） */
    @Test
    void sameDefectDailyAlert() throws Exception {
        long base = 970000000L + System.nanoTime() % 1000000 * 10;
        String code = "D-T" + base;
        jdbc.update("INSERT INTO qc_defect_code (id, code, name, category, default_level, code_status, created_at, updated_at) VALUES (?, ?, '测试不良', "
                + "'APPEARANCE', 'MI', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", base, code);
        String alertKey = "QC_DEFECT_" + code + "_" + java.time.LocalDate.now();
        String m = iqcRaw("制程不良预警");
        setParam("qc.defect.alert-threshold", "5");
        try {
            for (int i = 1; i <= 3; i++) {
                long reportId = base + i;
                domainEvents.publish(new IpqcTriggerEvent(reportId, "RPT-" + reportId, 1L, "MO-TEST", Long.valueOf(m), null, 10, null, new BigDecimal("100")));
                JsonNode list = ok(doGet("/api/quality/inspections?types=IPQC&statuses=PENDING&materialId=" + m, admin)).at("/list");
                assertThat(list.size()).as("待检 IPQC").isEqualTo(1);
                String id = list.get(0).at("/id").asText();
                ok(doPut("/api/quality/inspections/" + id + "/results", admin, Map.of("items", List.of(), "defects", List.of(Map.of("defectCode", code, "qty", 3)))));
                ok(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", "REJECTED")));
                Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM wb_alert WHERE alert_key = ?", Integer.class, alertKey);
                // 第 1 单累计 3 件未达阈值；第 2 单累计 6 件触发；第 3 单不重复触发
                assertThat(n).as("第 %d 单", i).isEqualTo(i == 1 ? 0 : 1);
            }
            assertThat(jdbc.queryForObject("SELECT title FROM wb_alert WHERE alert_key = ?", String.class, alertKey)).contains(code);
        } finally {
            resetParam("qc.defect.alert-threshold");
        }
    }

    /** QC-INS-R09：工序最近一次 IPQC 拒收时生产订单显示警示；之后同工序判定合格则解除 */
    @Test
    void ipqcRejectedOperationWarning() throws Exception {
        long base = 960000000L + System.nanoTime() % 1000000 * 10;
        Long prodOrderId = base;
        String m = iqcRaw("IPQC 警示");
        String[] results = {"REJECTED", "QUALIFIED"};
        for (int i = 0; i < results.length; i++) {
            domainEvents.publish(new IpqcTriggerEvent(base + i + 1, "RPT-" + (base + i + 1), prodOrderId, "MO-" + base, Long.valueOf(m), null, 20, null,
                    new BigDecimal("10")));
            JsonNode list = ok(doGet("/api/quality/inspections?types=IPQC&statuses=PENDING&materialId=" + m, admin)).at("/list");
            String id = list.get(0).at("/id").asText();
            ok(doPost("/api/quality/inspections/" + id + "/judge", admin, Map.of("result", results[i])));
            var rejected = inspectionQueryApi.getIpqcRejected(prodOrderId);
            if (i == 0) {
                assertThat(rejected).hasSize(1);
                assertThat(rejected.get(0).operationSeq()).isEqualTo(20);
                assertThat(rejected.get(0).inspectionId()).isEqualTo(Long.valueOf(id));
            } else {
                assertThat(rejected).isEmpty();
            }
        }
    }
}
