package com.erp.it.engineering;

import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.tooling.ToolingApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 研发工程第 2 批：工作中心与工艺路线、研发项目、样品、工装、认证的验收用例 */
class Batch2IntegrationTest extends EngineeringTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n%âãÏÓ\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired
    ToolingApi toolingApi;
    @Autowired
    DomainEventPublisher eventPublisher;

    private String raw;
    private String fg;

    @BeforeEach
    void categories() throws Exception {
        String r = catCode();
        raw = createCategory(null, r, r, "RAW");
        String f = catCode();
        fg = createCategory(null, f, f, "FINISHED");
    }

    // ==================== 工作中心与工艺路线 ====================

    private String workCenter(String code, String type) throws Exception {
        Map<String, Object> w = new HashMap<>();
        w.put("code", code);
        w.put("name", "工作中心" + code);
        w.put("deptId", "100");
        w.put("wcType", type);
        w.put("hoursPerShift", 10);
        w.put("shiftCount", 2);
        w.put("efficiencyPct", 0.85);
        w.put("laborRate", 30);
        return ok(doPost("/api/engineering/work-centers", admin, w)).asText();
    }

    private static Map<String, Object> step(int seq, String op, String wc, boolean report) {
        Map<String, Object> s = new HashMap<>();
        s.put("seq", seq);
        s.put("operation", op);
        s.put("workCenterId", wc);
        s.put("runSeconds", 30);
        s.put("isReportPoint", report);
        return s;
    }

    /** RTG-T01 日产能；RTG-T02 最后一道工序必须是报工点；审核后首个版本为默认；无费率权限显示 null */
    @Test
    void workCenterAndRouting() throws Exception {
        String code = "SMT" + uniq();
        String wc = workCenter(code, "LINE");
        JsonNode rows = ok(doGet("/api/engineering/work-centers?keyword=" + code, admin)).at("/list");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.at("/0/capacityHoursPerDay").decimalValue()).isEqualByComparingTo("17");
        assertThat(rows.at("/0/laborRate").decimalValue()).isEqualByComparingTo("30");
        String viewer = userWith(List.of("eng:work-center:query"));
        JsonNode masked = ok(doGet("/api/engineering/work-centers?keyword=" + code, viewer)).at("/list/0");
        assertThat(masked.at("/rateVisible").asBoolean()).isFalse();
        assertThat(masked.at("/laborRate").isMissingNode()).isTrue();

        String fg1 = enabledMaterial(fg, "FG1", "FINISHED");
        Map<String, Object> r = new HashMap<>();
        r.put("materialId", fg1);
        r.put("steps", List.of(step(10, "SMT", wc, true), step(20, "DIP", wc, true), step(30, "ASSY", wc, true), step(40, "PACK", wc, false)));
        String id = ok(doPost("/api/engineering/routings", admin, r)).at("/id").asText();
        assertError(doPost("/api/engineering/routings/" + id + "/approve", admin, null), "最后一道工序必须是报工点");

        JsonNode d = ok(doGet("/api/engineering/routings/" + id, admin));
        r.put("steps", List.of(step(10, "SMT", wc, true), step(20, "DIP", wc, true), step(30, "ASSY", wc, true), step(40, "PACK", wc, true)));
        r.put("rowVersion", d.at("/rowVersion").asInt());
        ok(doPut("/api/engineering/routings/" + id, admin, r));
        ok(doPost("/api/engineering/routings/" + id + "/approve", admin, null));
        d = ok(doGet("/api/engineering/routings/" + id, admin));
        assertThat(d.at("/status").asText()).isEqualTo("APPROVED");
        assertThat(d.at("/isDefault").asBoolean()).isTrue();
        assertThat(d.at("/steps").size()).isEqualTo(4);
        assertThat(d.at("/totalRunSeconds").decimalValue()).isEqualByComparingTo("120");
        // 被已审核路线使用的工作中心不能删除
        assertError(doDelete("/api/engineering/work-centers/" + wc, admin), "该工作中心已被使用，只能停用");
    }

    // ==================== 研发项目 ====================

    private static Map<String, Object> task(String name, String ownerId) {
        Map<String, Object> t = new HashMap<>();
        t.put("stage", "DESIGN");
        t.put("name", name);
        t.put("ownerId", ownerId);
        t.put("planStart", LocalDate.now().toString());
        t.put("planEnd", LocalDate.now().plusDays(7).toString());
        t.put("weight", 1);
        return t;
    }

    /** PRJ-T01～T04 */
    @Test
    void projectProgress() throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "蓝牙耳机 NPI");
        p.put("projectType", "NPI");
        p.put("pmUserId", "1");
        p.put("planStart", LocalDate.now().toString());
        p.put("planEnd", LocalDate.now().plusMonths(3).toString());
        String id = ok(doPost("/api/engineering/projects", admin, p)).asText();
        JsonNode d = ok(doGet("/api/engineering/projects/" + id, admin));
        assertThat(d.at("/projectStatus").asText()).isEqualTo("PLANNING");
        assertThat(d.at("/members").size()).isEqualTo(1);

        // T04：非成员不能做任务负责人
        assertError(doPost("/api/engineering/projects/" + id + "/tasks", admin, task("结构设计", "999999")), "任务负责人必须是项目成员");

        // T01：4 个任务 → 进度 0
        String t1 = ok(doPost("/api/engineering/projects/" + id + "/tasks", admin, Map.of("stage", "CONCEPT", "name", "立项评审", "ownerId", "1",
                "planStart", LocalDate.now().toString(), "planEnd", LocalDate.now().plusDays(3).toString()))).asText();
        ok(doPost("/api/engineering/projects/" + id + "/tasks", admin, task("结构设计", "1")));
        ok(doPost("/api/engineering/projects/" + id + "/tasks", admin, task("电子设计", "1")));
        ok(doPost("/api/engineering/projects/" + id + "/tasks", admin, task("软件设计", "1")));
        assertThat(ok(doGet("/api/engineering/projects/" + id, admin)).at("/progressPct").decimalValue()).isEqualByComparingTo("0");

        // T02：完成 1 个 → 25%，项目自动进行中
        ok(doPost("/api/engineering/projects/" + id + "/tasks/" + t1 + "/status", admin, Map.of("status", "DONE")));
        d = ok(doGet("/api/engineering/projects/" + id, admin));
        assertThat(d.at("/progressPct").decimalValue()).isEqualByComparingTo("0.25");
        assertThat(d.at("/projectStatus").asText()).isEqualTo("IN_PROGRESS");

        // T03：推进到设计阶段，设计阶段 3 个未完成；可以推进；不能回退
        ok(doPost("/api/engineering/projects/" + id + "/stage", admin, Map.of("stage", "DESIGN")));
        d = ok(doGet("/api/engineering/projects/" + id, admin));
        assertThat(d.at("/stage").asText()).isEqualTo("DESIGN");
        assertThat(d.at("/undoneInStage").asInt()).isEqualTo(3);
        ok(doPost("/api/engineering/projects/" + id + "/stage", admin, Map.of("stage", "EVT")));
        assertError(doPost("/api/engineering/projects/" + id + "/stage", admin, Map.of("stage", "DESIGN")), "只能推进到后续阶段");
        // 未完成任务不能完成项目
        assertError(doPost("/api/engineering/projects/" + id + "/complete", admin, null), "还有 3 个未完成任务");
    }

    // ==================== 样品 ====================

    private String sample(String materialId, String makeMethod) throws Exception {
        Map<String, Object> s = new HashMap<>();
        s.put("sampleType", "ENGINEERING");
        s.put("materialId", materialId);
        s.put("qty", 5);
        s.put("requiredDate", LocalDate.now().plusDays(7).toString());
        s.put("makeMethod", makeMethod);
        s.put("purpose", "工程验证");
        return ok(doPost("/api/engineering/samples", admin, s)).asText();
    }

    /** SMP-T05 无 BOM 不能生成生产订单；SMP-T03 出库确认后登记寄出、反馈、关闭 */
    @Test
    void sampleFlow() throws Exception {
        String fg1 = enabledMaterial(fg, "耳机", "FINISHED");
        String code = getMaterial(fg1).at("/code").asText();

        String s1 = sample(fg1, "PRODUCE");
        assertThat(ok(doPost("/api/engineering/samples/" + s1 + "/submit", admin, null)).asText()).isEqualTo("APPROVED");
        assertError(doPost("/api/engineering/samples/" + s1 + "/create-prod-order", admin, null), "物料「" + code + "」没有已审核的 BOM，不能生成生产订单");

        String s2 = sample(fg1, "FROM_STOCK");
        ok(doPost("/api/engineering/samples/" + s2 + "/submit", admin, null));
        // 申请出库 → 仓库“其他出库”草稿；出库单确认前不能登记寄出
        String outId = ok(doPost("/api/engineering/samples/" + s2 + "/request-stock-out", admin, null)).asText();
        Map<String, Object> ship = Map.of("shipDate", LocalDate.now().toString(), "courier", "顺丰", "trackingNo", "SF123");
        assertError(doPost("/api/engineering/samples/" + s2 + "/ship", admin, ship), "样品单当前状态【已审批】不允许登记寄出");
        JsonNode sd = ok(doGet("/api/engineering/samples/" + s2, admin));
        eventPublisher.publish(new StockOutConfirmedEvent(Long.valueOf(outId), "OUT-T-001", StockOutType.OTHER_OUT,
                new SourceRef("ENG_SAMPLE", Long.valueOf(s2), sd.at("/docNo").asText()), null, List.of()));
        sd = ok(doGet("/api/engineering/samples/" + s2, admin));
        assertThat(sd.at("/sampleStatus").asText()).isEqualTo("READY");
        assertThat(sd.at("/stockOutDone").asBoolean()).isTrue();
        ok(doPost("/api/engineering/samples/" + s2 + "/ship", admin, ship));
        ok(doPost("/api/engineering/samples/" + s2 + "/feedback", admin, Map.of("result", "REJECTED", "feedbackDate", LocalDate.now().toString())));
        JsonNode row = ok(doGet("/api/engineering/samples?docNo=" + sd.at("/docNo").asText(), admin)).at("/list/0");
        assertThat(row.at("/sampleStatus").asText()).isEqualTo("FEEDBACK");
        assertThat(row.at("/feedbackResult").asText()).isEqualTo("REJECTED");
        ok(doPost("/api/engineering/samples/" + s2 + "/close", admin, null));
        assertThat(ok(doGet("/api/engineering/samples/" + s2, admin)).at("/sampleStatus").asText()).isEqualTo("CLOSED");
    }

    // ==================== 工装 ====================

    private String tooling(int cavity, Integer designLife, int used) throws Exception {
        Map<String, Object> t = new HashMap<>();
        t.put("name", "外壳模具");
        t.put("toolingType", "MOLD");
        t.put("cavity", cavity);
        t.put("designLife", designLife);
        t.put("initialUsedCount", used);
        return ok(doPost("/api/engineering/toolings", admin, t)).asText();
    }

    /** TL-T01 使用次数按模穴折算；TL-T03 达到寿命不能使用；TL-T05 维修中不能借出 */
    @Test
    void toolingLifeAndStatus() throws Exception {
        String t1 = tooling(4, 50_000, 1000);
        int count = toolingApi.usageOf(Long.valueOf(t1), new BigDecimal("400"));
        assertThat(count).isEqualTo(100);
        toolingApi.addUsage(Long.valueOf(t1), count, "RPT-T-001");
        JsonNode d = ok(doGet("/api/engineering/toolings/" + t1, admin));
        assertThat(d.at("/usedCount").asInt()).isEqualTo(1100);
        assertThat(ok(doGet("/api/engineering/toolings/" + t1 + "/records", admin)).findValuesAsText("recordType")).contains("USAGE");

        String t2 = tooling(1, 50_000, 50_000);
        String code2 = ok(doGet("/api/engineering/toolings/" + t2, admin)).at("/code").asText();
        assertThatThrownBy(() -> toolingApi.validateUsable(Long.valueOf(t2))).hasMessage("工装「" + code2 + "」已达到设计寿命，不能继续使用");

        String code1 = d.at("/code").asText();
        ok(doPost("/api/engineering/toolings/" + t1 + "/repair-start", admin, Map.of("content", "顶针断裂")));
        assertError(doPost("/api/engineering/toolings/" + t1 + "/lend", admin, Map.of("userId", "1")), "工装「" + code1 + "」当前状态为维修中，不能借出");
        ok(doPost("/api/engineering/toolings/" + t1 + "/repair-end", admin, Map.of("content", "更换顶针")));
        ok(doPost("/api/engineering/toolings/" + t1 + "/lend", admin, Map.of("userId", "1")));
        // 有使用记录不能删除
        ok(doPost("/api/engineering/toolings/" + t1 + "/return", admin, Map.of()));
        assertError(doDelete("/api/engineering/toolings/" + t1, admin), "工装已有使用记录，不能删除");
    }

    // ==================== 认证 ====================

    private String uploadPdf() throws Exception {
        String body = mockMvc.perform(multipart("/api/system/files").file(new MockMultipartFile("file", "cert.pdf", "application/pdf", PDF))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ok(read(body)).at("/id").asText();
    }

    private Map<String, Object> cert(String no, LocalDate issue, LocalDate expire, List<String> materials, List<String> files) {
        Map<String, Object> c = new HashMap<>();
        c.put("certType", "CE");
        c.put("certNo", no);
        c.put("name", "CE 证书 " + no);
        c.put("issuingBody", "TUV");
        c.put("issueDate", issue.toString());
        c.put("expireDate", expire == null ? null : expire.toString());
        c.put("materialIds", materials);
        c.put("fileIds", files);
        return c;
    }

    /** CRT-T01 物料“认证”页签；CRT-T03 已过期不在“有效”中；CRT-T04 未上传文件 */
    @Test
    void certification() throws Exception {
        String fg1 = enabledMaterial(fg, "FG1", "FINISHED");
        String fg2 = enabledMaterial(fg, "FG2", "FINISHED");
        String no = "CE" + uniq();
        LocalDate today = LocalDate.now();
        assertError(doPost("/api/engineering/certifications", admin, cert(no, today.minusYears(1), today.plusMonths(3), List.of(fg1, fg2), List.of())),
                "请上传证书文件");
        String id = ok(doPost("/api/engineering/certifications", admin,
                cert(no, today.minusYears(1), today.plusMonths(3), List.of(fg1, fg2), List.of(uploadPdf())))).asText();
        JsonNode byMaterial = ok(doGet("/api/engineering/certifications/by-material/" + fg1, admin));
        assertThat(byMaterial.findValuesAsText("certNo")).containsExactly(no);
        assertThat(byMaterial.at("/0/materials").size()).isEqualTo(2);

        String expiredNo = "CE" + uniq();
        ok(doPost("/api/engineering/certifications", admin,
                cert(expiredNo, today.minusYears(2), today.minusDays(1), List.of(fg2), List.of(uploadPdf()))));
        JsonNode valid = ok(doGet("/api/engineering/certifications?validity=VALID&materialId=" + fg2, admin)).at("/list");
        assertThat(valid.findValuesAsText("certNo")).contains(no).doesNotContain(expiredNo);
        JsonNode expired = ok(doGet("/api/engineering/certifications?validity=EXPIRED&materialId=" + fg2, admin)).at("/list");
        assertThat(expired.findValuesAsText("certNo")).containsExactly(expiredNo);
        // 同类型证书编号唯一
        assertError(doPost("/api/engineering/certifications", admin, cert(no, today, null, List.of(), List.of(uploadPdf()))), "该证书已存在");
        ok(doPost("/api/engineering/certifications/" + id + "/revoke", admin, Map.of("reason", "产品停产")));
        assertThat(ok(doGet("/api/engineering/certifications/by-material/" + fg1, admin)).at("/0/certStatus").asText()).isEqualTo("REVOKED");
    }
}
