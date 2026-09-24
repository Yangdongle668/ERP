package com.erp.it.engineering;

import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.bom.BomExplodeLine;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** BOM（需求 05-03 验收用例 ENG-BOM-T01～T11） */
class BomIntegrationTest extends EngineeringTestSupport {

    @Autowired
    BomApi bomApi;

    private String raw;
    private String fg;

    @BeforeEach
    void categories() throws Exception {
        String r = catCode();
        raw = createCategory(null, r, r, "RAW");
        String f = catCode();
        fg = createCategory(null, f, f, "FINISHED");
    }

    private static Map<String, Object> line(String componentId, Object qty, Object scrap) {
        Map<String, Object> l = new HashMap<>();
        l.put("componentId", componentId);
        l.put("qtyPer", qty);
        l.put("scrapRate", scrap);
        return l;
    }

    private Map<String, Object> bom(String parentId, List<Map<String, Object>> lines) {
        Map<String, Object> b = new HashMap<>();
        b.put("materialId", parentId);
        b.put("baseQty", 1);
        b.put("lines", lines);
        return b;
    }

    private String createBom(String parentId, List<Map<String, Object>> lines) throws Exception {
        return ok(doPost("/api/engineering/boms", admin, bom(parentId, lines))).at("/id").asText();
    }

    private JsonNode detail(String id) throws Exception {
        return ok(doGet("/api/engineering/boms/" + id, admin));
    }

    /** T01、T02、T04、T05、T06、T10 */
    @Test
    void lifecycleExplodeCompare() throws Exception {
        String fg1 = enabledMaterial(fg, "成品 FG1", "FINISHED");
        String pcba = enabledMaterial(fg, "PCBA", "SEMI_FINISHED");
        String shell = enabledMaterial(raw, "外壳", "RAW");
        String screw = enabledMaterial(raw, "螺丝", "RAW");

        // T01：无审批流 → 提交即审核，V1 自动成为默认
        String v1 = createBom(fg1, List.of(line(pcba, 1, 0), line(shell, 1, 0), line(screw, 4, 0.02)));
        assertThat(detail(v1).at("/version").asInt()).isEqualTo(1);
        assertThat(ok(doPost("/api/engineering/boms/" + v1 + "/submit", admin, null)).asText()).isEqualTo("APPROVED");
        JsonNode d1 = detail(v1);
        assertThat(d1.at("/isDefault").asBoolean()).isTrue();
        assertThat(d1.at("/docNo").asText()).endsWith("-V1");

        // T02：展开 100 个 FG1 → 螺丝需求 408
        JsonNode rows = ok(doGet("/api/engineering/boms/" + v1 + "/explode?qty=100", admin));
        JsonNode screwRow = find(rows, screw);
        assertThat(screwRow.at("/requiredQty").decimalValue()).isEqualByComparingTo("408");
        assertThat(screwRow.at("/totalQtyPer").decimalValue()).isEqualByComparingTo("4.08");

        // T04：已审核不能修改
        assertError(doPut("/api/engineering/boms/" + v1, admin, bom(fg1, List.of(line(shell, 1, 0)))), "只有草稿状态的 BOM 可以修改");
        // 默认版本不能反审核、不能停用
        assertError(doPost("/api/engineering/boms/" + v1 + "/unapprove", admin, Map.of("reason", "测试")), "默认版本不能反审核");
        assertError(doPost("/api/engineering/boms/" + v1 + "/disable", admin, null), "默认版本不能停用");

        // T05：新建版本 → 螺丝用量改为 6 → 提交 → 设为默认
        String v2 = ok(doPost("/api/engineering/boms/" + v1 + "/new-version", admin, null)).asText();
        JsonNode d2 = detail(v2);
        assertThat(d2.at("/version").asInt()).isEqualTo(2);
        assertThat(d2.at("/lines").size()).isEqualTo(3);
        assertError(doPost("/api/engineering/boms/" + v2 + "/submit", admin, null), "新建版本时请填写版本说明");
        Map<String, Object> save = bom(fg1, List.of(line(pcba, 1, 0), line(shell, 1, 0), line(screw, 6, 0.02)));
        save.put("description", "螺丝改为 6 颗");
        save.put("rowVersion", d2.at("/rowVersion").asInt());
        ok(doPut("/api/engineering/boms/" + v2, admin, save));
        ok(doPost("/api/engineering/boms/" + v2 + "/submit", admin, null));
        assertThat(detail(v2).at("/isDefault").asBoolean()).isFalse();
        ok(doPost("/api/engineering/boms/" + v2 + "/set-default", admin, null));
        assertThat(detail(v2).at("/isDefault").asBoolean()).isTrue();
        assertThat(detail(v1).at("/isDefault").asBoolean()).isFalse();

        // T06：版本比较：螺丝 4 → 6
        JsonNode cmp = ok(doGet("/api/engineering/boms/compare?leftId=" + v1 + "&rightId=" + v2, admin));
        JsonNode screwCmp = null;
        for (JsonNode r : cmp.at("/rows")) if (r.at("/componentId").asText().equals(screw)) screwCmp = r;
        assertThat(screwCmp.at("/change").asText()).isEqualTo("CHANGED");
        assertThat(screwCmp.at("/left/qtyPer").decimalValue()).isEqualByComparingTo("4");
        assertThat(screwCmp.at("/right/qtyPer").decimalValue()).isEqualByComparingTo("6");

        // T10：反查螺丝 → FG1 V1、V2
        JsonNode used = ok(doGet("/api/engineering/boms/where-used?materialId=" + screw, admin));
        assertThat(used.findValuesAsText("docNo")).hasSize(2);
        JsonNode mb = ok(doGet("/api/engineering/materials/" + screw + "/boms", admin));
        assertThat(mb.at("/asComponent").size()).isEqualTo(2);
        // 停用前统计被已审核 BOM 使用的数量
        assertThat(ok(doGet("/api/engineering/materials/" + screw + "/references", admin)).at("/bomCount").asInt()).isEqualTo(2);
        // 被 BOM 使用的草稿物料不能删除由 R09 保证；非默认已审核版本可反审核、停用
        ok(doPost("/api/engineering/boms/" + v1 + "/unapprove", admin, Map.of("reason", "调整")));
        assertThat(detail(v1).at("/status").asText()).isEqualTo("DRAFT");
        ok(doPost("/api/engineering/boms/" + v1 + "/submit", admin, null));
        ok(doPost("/api/engineering/boms/" + v1 + "/disable", admin, null));
        assertThat(detail(v1).at("/status").asText()).isEqualTo("CLOSED");

        // 低位码：螺丝在第 1 层（FG1 → 螺丝），PCBA 同层
        assertThat(getMaterial(screw).at("/lowLevelCode").asInt()).isEqualTo(1);
        // 打印数据
        JsonNode print = ok(doGet("/api/engineering/boms/" + v2 + "/print-data", admin));
        assertThat(print.at("/lines/2/qtyPer").decimalValue()).isEqualByComparingTo("6");
        assertThat(print.at("/version").asText()).isEqualTo("V2");
    }

    /** T03：循环引用；R01 父件类型；R02 子件重复 */
    @Test
    void cycleAndLineRules() throws Exception {
        String fg1 = enabledMaterial(fg, "FG1", "FINISHED");
        String pcba = enabledMaterial(fg, "PCBA", "SEMI_FINISHED");
        String r1 = enabledMaterial(raw, "电阻", "RAW");
        String pcbaBom = createBom(pcba, List.of(line(fg1, 1, 0)));
        ok(doPost("/api/engineering/boms/" + pcbaBom + "/submit", admin, null));
        String fgCode = getMaterial(fg1).at("/code").asText();
        String pcbaCode = getMaterial(pcba).at("/code").asText();
        assertError(doPost("/api/engineering/boms", admin, bom(fg1, List.of(line(pcba, 1, 0)))),
                "存在循环引用：" + fgCode + " → " + pcbaCode + " → " + fgCode);
        assertError(doPost("/api/engineering/boms", admin, bom(r1, List.of(line(fg1, 1, 0)))), "原材料、包材、辅料不能作为 BOM 父件");
        assertError(doPost("/api/engineering/boms", admin, bom(fg1, List.of(line(fg1, 1, 0)))), "子件不能与父件相同");
        String r1Code = getMaterial(r1).at("/code").asText();
        assertError(doPost("/api/engineering/boms", admin, bom(fg1, List.of(line(r1, 1, 0), line(r1, 2, 0)))),
                "子件「" + r1Code + "」重复，请合并为一行");
        assertError(doPost("/api/engineering/boms/" + createBom(fg1, List.of()) + "/submit", admin, null), "请至少添加一行明细");
    }

    /** T07：虚拟件透过；T08：草稿子件不能提交；T11：位号个数警告 */
    @Test
    void phantomDraftAndPositions() throws Exception {
        String a = enabledMaterial(raw, "A", "RAW");
        String b = enabledMaterial(raw, "B", "RAW");
        String sf1 = enabledMaterial(fg, "SF1 虚拟件", "PHANTOM");
        String fg2 = enabledMaterial(fg, "FG2", "FINISHED");

        String fgBom = createBom(fg2, List.of(line(sf1, 1, 0)));
        // R12：虚拟件没有已审核 BOM 时不能提交
        assertError(doPost("/api/engineering/boms/" + fgBom + "/submit", admin, null),
                "虚拟件「" + getMaterial(sf1).at("/code").asText() + "」没有已审核的 BOM");
        String sfBom = createBom(sf1, List.of(line(a, 2, 0), line(b, 1, 0)));
        ok(doPost("/api/engineering/boms/" + sfBom + "/submit", admin, null));
        ok(doPost("/api/engineering/boms/" + fgBom + "/submit", admin, null));

        List<BomExplodeLine> lines = bomApi.explode(Long.valueOf(fg2), new BigDecimal("10"), null, 0);
        List<String> ids = new ArrayList<>();
        lines.forEach(l -> ids.add(String.valueOf(l.componentId())));
        assertThat(ids).containsExactlyInAnyOrder(a, b);
        assertThat(lines.stream().filter(l -> String.valueOf(l.componentId()).equals(a)).findFirst().orElseThrow().requiredQty())
                .isEqualByComparingTo("20");
        // 详情页展开显示虚拟件行
        JsonNode tree = ok(doGet("/api/engineering/boms/" + fgBom + "/explode", admin));
        assertThat(tree.at("/0/phantom").asBoolean()).isTrue();
        assertThat(tree.at("/0/children").size()).isEqualTo(2);

        // T08：草稿子件可保存（参数允许），提交时报错
        String c = createMaterial(material(raw, "C 草稿", "RAW"));
        String fg3 = enabledMaterial(fg, "FG3", "FINISHED");
        String draftBom = createBom(fg3, List.of(line(c, 1, 0)));
        assertError(doPost("/api/engineering/boms/" + draftBom + "/submit", admin, null),
                "子件「" + getMaterial(c).at("/code").asText() + "」未启用，不能提交");

        // T11：位号 R1,R2,R5-R8 共 6 个，用量 5 → 警告但保存成功
        Map<String, Object> l = line(a, 5, 0);
        l.put("positionNo", "R1,R2,R5-R8");
        JsonNode saved = ok(doPost("/api/engineering/boms", admin, bom(fg3, List.of(l))));
        assertThat(saved.at("/warnings/0").asText()).isEqualTo("第 1 行位号个数 6 与用量 5 不一致");
        // 草稿可删除，版本号可复用
        ok(doDelete("/api/engineering/boms/" + saved.at("/id").asText(), admin));
        assertThat(ok(doGet("/api/engineering/boms/next-version?materialId=" + fg3, admin)).at("/version").asInt()).isEqualTo(2);
    }

    @Test
    void pageFilters() throws Exception {
        String p = enabledMaterial(fg, "列表父件", "FINISHED");
        String c = enabledMaterial(raw, "列表子件", "RAW");
        String id = createBom(p, List.of(line(c, 1, 0)));
        JsonNode page = ok(doGet("/api/engineering/boms?componentId=" + c + "&statuses=DRAFT,PENDING_APPROVAL,APPROVED", admin));
        assertThat(page.at("/total").asInt()).isEqualTo(1);
        assertThat(page.at("/list/0/id").asText()).isEqualTo(id);
        assertThat(page.at("/list/0/lineCount").asInt()).isEqualTo(1);
        page = ok(doGet("/api/engineering/boms?materialId=" + p + "&defaultOnly=true", admin));
        assertThat(page.at("/total").asInt()).isZero();
    }

    private static JsonNode find(JsonNode rows, String componentId) {
        for (JsonNode r : rows) {
            if (r.at("/componentId").asText().equals(componentId)) return r;
            JsonNode c = find(r.at("/children"), componentId);
            if (c != null) return c;
        }
        return null;
    }
}
