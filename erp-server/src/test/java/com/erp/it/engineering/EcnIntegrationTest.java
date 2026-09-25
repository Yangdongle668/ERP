package com.erp.it.engineering;

import com.erp.module.engineering.api.ecn.EcnImpact;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ECN（需求 05-05 验收用例 ENG-ECN-T01、T03、T04、T06、T07） */
class EcnIntegrationTest extends EngineeringTestSupport {

    private String raw;
    private String fg;

    @BeforeEach
    void categories() throws Exception {
        String r = catCode();
        raw = createCategory(null, r, r, "RAW");
        String f = catCode();
        fg = createCategory(null, f, f, "FINISHED");
    }

    private String approvedBom(String parentId, String componentId, Object qty) throws Exception {
        Map<String, Object> l = new HashMap<>();
        l.put("componentId", componentId);
        l.put("qtyPer", qty);
        Map<String, Object> b = new HashMap<>();
        b.put("materialId", parentId);
        b.put("baseQty", 1);
        b.put("lines", List.of(l));
        String id = ok(doPost("/api/engineering/boms", admin, b)).at("/id").asText();
        ok(doPost("/api/engineering/boms/" + id + "/submit", admin, null));
        return id;
    }

    private Map<String, Object> ecn(List<Map<String, Object>> lines) {
        Map<String, Object> e = new HashMap<>();
        e.put("title", "连接器 A 替换为 B");
        e.put("ecnType", "SUBSTITUTE");
        e.put("reasonType", "SUPPLY");
        e.put("reason", "A 停产");
        e.put("effectiveMode", "IMMEDIATE");
        e.put("lines", lines);
        return e;
    }

    private static List<Map<String, Object>> toLines(JsonNode preview) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode p : preview) {
            Map<String, Object> l = new HashMap<>();
            l.put("bomId", p.at("/bomId").asText());
            l.put("action", p.at("/action").asText());
            l.put("oldComponentId", p.at("/oldComponentId").asText());
            l.put("newComponentId", p.at("/newComponentId").asText());
            l.put("newQtyPer", p.at("/newQtyPer").decimalValue());
            lines.add(l);
        }
        return lines;
    }

    private JsonNode detail(String id) throws Exception {
        return ok(doGet("/api/engineering/ecns/" + id, admin));
    }

    /** T01 批量替换、T03 未选处理方式、T04 审批生成新版本并生效、T06 任务未完成不能关闭 */
    @Test
    void batchReplaceApproveEffectClose() throws Exception {
        String fg1 = enabledMaterial(fg, "FG1", "FINISHED");
        String fg2 = enabledMaterial(fg, "FG2", "FINISHED");
        String a = enabledMaterial(raw, "连接器 A", "RAW");
        String b = enabledMaterial(raw, "连接器 B", "RAW");
        String bom1 = approvedBom(fg1, a, 2);
        String bom2 = approvedBom(fg2, a, 1);

        // T01：批量替换 A → B 生成 2 行 REPLACE，原用量带出
        JsonNode preview = ok(doPost("/api/engineering/ecns/batch-replace-preview", admin, Map.of("oldComponentId", a, "newComponentId", b)));
        assertThat(preview.size()).isEqualTo(2);
        assertThat(preview.findValuesAsText("action")).containsOnly("REPLACE");

        String id = ok(doPost("/api/engineering/ecns", admin, ecn(toLines(preview)))).asText();
        JsonNode d = detail(id);
        assertThat(d.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(d.at("/lines").size()).isEqualTo(2);

        // T03：未分析影响 → 提交失败；分析后在途采购未选处理方式 → 仍失败
        assertError(doPost("/api/engineering/ecns/" + id + "/submit", admin, null), "请完成影响分析并选择处理方式");
        ItEngineeringConfig.IMPACTS.put(Long.valueOf(a), List.of(new EcnImpact(Long.valueOf(a), "PURCHASE", "PO-T-001", new BigDecimal("1000")),
                new EcnImpact(Long.valueOf(a), "WIP", "MO-T-001", new BigDecimal("50"))));
        try {
            ok(doPost("/api/engineering/ecns/" + id + "/analyze", admin, null));
        } finally {
            ItEngineeringConfig.IMPACTS.remove(Long.valueOf(a));
        }
        d = detail(id);
        assertThat(d.at("/analyzed").asBoolean()).isTrue();
        assertThat(d.at("/impacts").size()).isEqualTo(2);
        assertThat(d.at("/tasks").findValuesAsText("deptRole")).contains("PURCHASE", "PRODUCTION", "QUALITY");
        assertError(doPost("/api/engineering/ecns/" + id + "/submit", admin, null), "请完成影响分析并选择处理方式");

        // 选择处理方式，并把全部任务指派给 admin
        Map<String, Object> save = ecn(toLines(preview));
        List<Map<String, Object>> impacts = new ArrayList<>();
        for (JsonNode i : d.at("/impacts")) {
            impacts.add(Map.of("id", i.at("/id").asText(), "handling", "PURCHASE".equals(i.at("/impactType").asText()) ? "CANCEL" : "UPDATE_WIP"));
        }
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (JsonNode t : d.at("/tasks")) {
            tasks.add(Map.of("deptRole", t.at("/deptRole").asText(), "assigneeId", "1", "content", t.at("/content").asText()));
        }
        save.put("impacts", impacts);
        save.put("tasks", tasks);
        save.put("version", d.at("/version").asInt());
        ok(doPut("/api/engineering/ecns/" + id, admin, save));
        assertThat(detail(id).at("/analyzed").asBoolean()).isTrue();

        // T04：无审批流 → 直接审核，立即生效：各生成新版本（B 替换 A）并成为默认
        JsonNode r = ok(doPost("/api/engineering/ecns/" + id + "/submit", admin, null));
        assertThat(r.at("/status").asText()).isEqualTo("IN_PROGRESS");
        d = detail(id);
        assertThat(d.at("/status").asText()).isEqualTo("IN_PROGRESS");
        for (JsonNode l : d.at("/lines")) {
            String newBom = l.at("/newBomId").asText();
            assertThat(newBom).isNotEmpty();
            JsonNode nb = ok(doGet("/api/engineering/boms/" + newBom, admin));
            assertThat(nb.at("/status").asText()).isEqualTo("APPROVED");
            assertThat(nb.at("/isDefault").asBoolean()).isTrue();
            assertThat(nb.at("/version").asInt()).isEqualTo(2);
            assertThat(nb.at("/lines/0/componentId").asText()).isEqualTo(b);
        }
        assertThat(ok(doGet("/api/engineering/boms/" + bom1, admin)).at("/isDefault").asBoolean()).isFalse();
        assertThat(ok(doGet("/api/engineering/boms/" + bom2, admin)).at("/isDefault").asBoolean()).isFalse();

        // T06：任务未完成 → 不能关闭
        int n = d.at("/pendingTasks").asInt();
        assertThat(n).isEqualTo(3);
        assertError(doPost("/api/engineering/ecns/" + id + "/close", admin, null), "还有 " + n + " 项执行任务未完成");
        for (JsonNode t : d.at("/tasks")) {
            assertThat(t.at("/mine").asBoolean()).isTrue();
            ok(doPost("/api/engineering/ecns/" + id + "/tasks/" + t.at("/id").asText() + "/done", admin, Map.of("remark", "已处理")));
        }
        ok(doPost("/api/engineering/ecns/" + id + "/close", admin, null));
        assertThat(detail(id).at("/status").asText()).isEqualTo("COMPLETED");

        JsonNode print = ok(doGet("/api/engineering/ecns/" + id + "/print-data", admin));
        assertThat(print.at("/lines/0/action").asText()).isEqualTo("替换");
        assertThat(print.at("/status").asText()).isEqualTo("已关闭");
    }

    /** T07：BOM 已在另一张待审批/已审核的 ECN 中；R01 非默认版本；DATE 方式审核后不切换默认 */
    @Test
    void lockedAndDateMode() throws Exception {
        String fg1 = enabledMaterial(fg, "FG1", "FINISHED");
        String a = enabledMaterial(raw, "电阻", "RAW");
        String bom1 = approvedBom(fg1, a, 1);
        Map<String, Object> line = new HashMap<>();
        line.put("bomId", bom1);
        line.put("action", "CHANGE_QTY");
        line.put("oldComponentId", a);
        line.put("newQtyPer", 3);

        Map<String, Object> first = ecn(List.of(line));
        first.put("effectiveMode", "DATE");
        first.put("effectiveDate", java.time.LocalDate.now().plusDays(10).toString());
        String e1 = ok(doPost("/api/engineering/ecns", admin, first)).asText();
        ok(doPost("/api/engineering/ecns/" + e1 + "/analyze", admin, null));
        assertThat(ok(doPost("/api/engineering/ecns/" + e1 + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        JsonNode d1 = detail(e1);
        String newBom = d1.at("/lines/0/newBomId").asText();
        JsonNode nb = ok(doGet("/api/engineering/boms/" + newBom, admin));
        assertThat(nb.at("/isDefault").asBoolean()).isFalse();
        assertThat(nb.at("/lines/0/qtyPer").decimalValue()).isEqualByComparingTo("3");

        // T07：同一 BOM 已在已审核的 ECN 中
        String e2 = ok(doPost("/api/engineering/ecns", admin, ecn(List.of(line)))).asText();
        ok(doPost("/api/engineering/ecns/" + e2 + "/analyze", admin, null));
        assertError(doPost("/api/engineering/ecns/" + e2 + "/submit", admin, null),
                "BOM「" + d1.at("/lines/0/bomNo").asText() + "」正在 ECN「" + d1.at("/docNo").asText() + "」中变更");

        // 手工生效后旧版本不再是默认 → 第二张提交提示 R01
        ok(doPost("/api/engineering/ecns/" + e1 + "/effect", admin, null));
        assertThat(ok(doGet("/api/engineering/boms/" + newBom, admin)).at("/isDefault").asBoolean()).isTrue();
        assertError(doPost("/api/engineering/ecns/" + e2 + "/submit", admin, null),
                "BOM「" + d1.at("/lines/0/bomNo").asText() + "」不是当前默认版本，请重新选择");

        // R02：同一子件两个动作
        Map<String, Object> remove = new HashMap<>(line);
        remove.put("bomId", newBom);
        remove.put("action", "REMOVE");
        Map<String, Object> change = new HashMap<>(remove);
        change.put("action", "CHANGE_QTY");
        String e3 = ok(doPost("/api/engineering/ecns", admin, ecn(List.of(remove, change)))).asText();
        ok(doPost("/api/engineering/ecns/" + e3 + "/analyze", admin, null));
        assertError(doPost("/api/engineering/ecns/" + e3 + "/submit", admin, null),
                "BOM「" + nb.at("/docNo").asText() + "」中子件「" + getMaterial(a).at("/code").asText() + "」存在多个变更");
        // 草稿可作废
        ok(doPost("/api/engineering/ecns/" + e3 + "/void", admin, Map.of("reason", "重复")));
        assertThat(detail(e3).at("/status").asText()).isEqualTo("VOIDED");
    }
}
