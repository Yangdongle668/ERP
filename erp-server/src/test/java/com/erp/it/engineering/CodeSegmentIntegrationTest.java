package com.erp.it.engineering;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 物料编码段（需求 05-01 第 8 节、05-02 R14，《物料编码原则》） */
class CodeSegmentIntegrationTest extends EngineeringTestSupport {

    private String categoryId(String code) throws Exception {
        JsonNode tree = ok(doGet("/api/engineering/categories/tree", admin));
        for (JsonNode n : tree.findParents("code")) {
            if (code.equals(n.at("/code").asText())) return n.at("/id").asText();
        }
        throw new AssertionError("未找到类别 " + code);
    }

    private Map<String, Object> coded(String categoryId, String name, String type, List<String> values) {
        Map<String, Object> m = material(categoryId, name, type);
        m.put("codeValues", values);
        return m;
    }

    /** 预置大类：线材 12- + 型号 + 颜色 + 5 位流水；同组合连续，不同组合独立计数；泡棉 3 位流水 */
    @Test
    void presetCategories() throws Exception {
        String wire = categoryId("LD12");
        JsonNode scheme = ok(doGet("/api/engineering/categories/" + wire + "/code-scheme", admin));
        assertThat(scheme.at("/codePrefix").asText()).isEqualTo("12-");
        assertThat(scheme.at("/segments/0/name").asText()).isEqualTo("线材型号");
        assertThat(scheme.at("/segments/1/name").asText()).isEqualTo("颜色");
        assertThat(scheme.at("/segments").size()).isEqualTo(2);

        String a = getMaterial(createMaterial(coded(wire, "导线", "RAW", List.of("02", "1")))).at("/code").asText();
        assertThat(a).matches("12-021\\d{5}");
        String b = getMaterial(createMaterial(coded(wire, "导线", "RAW", List.of("02", "1")))).at("/code").asText();
        assertThat(Long.parseLong(b.substring(6))).isEqualTo(Long.parseLong(a.substring(6)) + 1);
        JsonNode c = getMaterial(createMaterial(coded(wire, "导线", "RAW", List.of("06", "2"))));
        assertThat(c.at("/code").asText()).matches("12-062\\d{5}");
        assertThat(c.at("/codeSegments").asText()).isEqualTo("线材型号 06 UL1007；颜色 2 黑色");

        assertError(doPost("/api/engineering/materials", admin, coded(wire, "导线", "RAW", List.of("02"))), "请选择编码段「颜色」");
        assertError(doPost("/api/engineering/materials", admin, coded(wire, "导线", "RAW", List.of("99", "1"))), "编码段「线材型号」没有可用的特征值「99」");

        String foam = categoryId("LD16");
        assertThat(getMaterial(createMaterial(coded(foam, "EVA", "RAW", List.of("10", "1", "1")))).at("/code").asText()).matches("16-1011\\d{3}");
    }

    /** 自定义编码方案；已有物料后不能改结构、不能删特征值，可以新增 / 停用特征值 */
    @Test
    void customSchemeAndLock() throws Exception {
        String code = catCode();
        Map<String, Object> cat = new HashMap<>();
        cat.put("code", code);
        cat.put("name", "类别" + code);
        cat.put("codePrefix", "T" + code.substring(code.length() - 4) + "-");
        cat.put("codeSeqLength", 3);
        cat.put("defaultMaterialType", "RAW");
        cat.put("defaultBaseUom", "PCS");
        cat.put("defaultTracking", "NONE");
        cat.put("defaultIqcRequired", false);
        cat.put("sort", 10);
        String id = ok(doPost("/api/engineering/categories", admin, cat)).asText();
        String prefix = (String) cat.get("codePrefix");

        List<Map<String, Object>> segs = new ArrayList<>(List.of(
                seg(null, "颜色", 1, List.of(val(null, "1", "红色"), val(null, "2", "黑色"))),
                seg(null, "材质", 2, List.of(val(null, "AB", "PET")))));
        assertError(doPut("/api/engineering/categories/" + id + "/code-scheme", admin,
                Map.of("segments", List.of(seg(null, "颜色", 2, List.of(val(null, "1", "红色")))))), "编码段「颜色」为 2 位，特征值「1」位数不对");
        ok(doPut("/api/engineering/categories/" + id + "/code-scheme", admin, Map.of("segments", segs)));

        String m = getMaterial(createMaterial(coded(id, "测试料", "RAW", List.of("2", "ab")))).at("/code").asText();
        assertThat(m).isEqualTo(prefix + "2AB001");

        // 已锁定：改位数不允许；删除特征值不允许；新增特征值、停用、改说明允许
        JsonNode scheme = ok(doGet("/api/engineering/categories/" + id + "/code-scheme", admin));
        assertThat(scheme.at("/locked").asBoolean()).isTrue();
        String s1 = scheme.at("/segments/0/id").asText();
        String s2 = scheme.at("/segments/1/id").asText();
        String red = scheme.at("/segments/0/values/0/id").asText();
        String black = scheme.at("/segments/0/values/1/id").asText();
        String pet = scheme.at("/segments/1/values/0/id").asText();
        String locked = "类别「类别" + code + "」已有物料，编码段的名称以外不能修改，特征值只能新增或停用";
        assertError(doPut("/api/engineering/categories/" + id + "/code-scheme", admin, Map.of("segments", List.of(
                seg(s1, "颜色", 1, List.of(val(red, "1", "红色"), val(black, "2", "黑色"))),
                seg(s2, "材质", 3, List.of(val(pet, "ABC", "PET")))))), locked);
        assertError(doPut("/api/engineering/categories/" + id + "/code-scheme", admin, Map.of("segments", List.of(
                seg(s1, "颜色", 1, List.of(val(red, "1", "红色"))),
                seg(s2, "材质", 2, List.of(val(pet, "AB", "PET")))))), locked);
        Map<String, Object> disabledRed = val(red, "1", "大红");
        disabledRed.put("status", "DISABLED");
        ok(doPut("/api/engineering/categories/" + id + "/code-scheme", admin, Map.of("segments", List.of(
                seg(s1, "颜色", 1, List.of(disabledRed, val(black, "2", "黑色"), val(null, "3", "白色"))),
                seg(s2, "材质名称", 2, List.of(val(pet, "AB", "PET")))))));
        assertError(doPost("/api/engineering/materials", admin, coded(id, "测试料", "RAW", List.of("1", "AB"))), "编码段「颜色」没有可用的特征值「1」");
        assertThat(getMaterial(createMaterial(coded(id, "测试料", "RAW", List.of("3", "AB")))).at("/code").asText()).isEqualTo(prefix + "3AB001");

        // 有编码段的类别不能新增下级
        Map<String, Object> child = new HashMap<>(cat);
        child.put("parentId", id);
        child.put("code", catCode());
        child.put("name", "下级");
        assertError(doPost("/api/engineering/categories", admin, child), "该类别下已有物料，不能新增下级类别");
    }

    private static Map<String, Object> seg(String id, String name, int length, List<Map<String, Object>> values) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("length", length);
        m.put("values", values);
        return m;
    }

    private static Map<String, Object> val(String id, String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("code", code);
        m.put("name", name);
        return m;
    }
}
