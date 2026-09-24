package com.erp.it.engineering;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 物料类别（需求 05-01 验收用例 ENG-CAT-T01～T04 与规则 R02～R07） */
class CategoryIntegrationTest extends EngineeringTestSupport {

    @Test
    void initialCategoriesExist() throws Exception {
        JsonNode tree = ok(doGet("/api/engineering/categories/tree", admin));
        assertThat(tree.findValuesAsText("code")).contains("RAW", "FPC", "ELEC", "PKG", "AUX", "SEMI", "FG");
        JsonNode simple = ok(doGet("/api/engineering/categories/simple-tree", admin));
        assertThat(simple.findValuesAsText("code")).contains("FPC");
    }

    /** T01 新增下级继承默认值（前端带出，后端按请求保存）；T02 已有物料不能新增下级；T03 在下级中建物料编码用下级前缀 */
    @Test
    void childCategoryAndPrefix() throws Exception {
        String top = catCode();
        String parent = createCategory(null, top, top, "RAW");
        String childCode = catCode();
        String child = createCategory(parent, childCode, childCode, "RAW");

        // 物料只能挂在末级类别
        assertError(doPost("/api/engineering/materials", admin, material(parent, "IC 芯片", "RAW")), "请选择末级物料类别");
        String m = createMaterial(material(child, "IC 芯片", "RAW"));
        assertThat(getMaterial(m).at("/code").asText()).isEqualTo(childCode + "00001");

        // T02：已有物料的类别不能新增下级
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", child);
        body.put("code", catCode());
        body.put("name", "下级");
        body.put("codePrefix", "X1");
        body.put("defaultMaterialType", "RAW");
        body.put("defaultTracking", "NONE");
        body.put("defaultIqcRequired", true);
        body.put("sort", 10);
        assertError(doPost("/api/engineering/categories", admin, body), "该类别下已有物料，不能新增下级类别");

        // T04 / R05：有下级或物料时不能删除
        assertError(doDelete("/api/engineering/categories/" + parent, admin), "该类别下有下级类别或物料，不能删除");
        assertError(doDelete("/api/engineering/categories/" + child, admin), "该类别下有下级类别或物料，不能删除");

        // R07：已被物料使用的类别编码不能修改
        JsonNode node = findNode(ok(doGet("/api/engineering/categories/tree", admin)), child);
        Map<String, Object> update = updateBody(node, parent);
        update.put("code", catCode());
        assertError(doPut("/api/engineering/categories/" + child, admin, update), "该类别已被物料使用，不能修改编码");

        // 树中物料数含下级
        JsonNode p = findNode(ok(doGet("/api/engineering/categories/tree", admin)), parent);
        assertThat(p.at("/materialCount").asInt()).isZero();  // 草稿不计
        ok(doPost("/api/engineering/materials/" + m + "/enable", admin, null));
        p = findNode(ok(doGet("/api/engineering/categories/tree", admin)), parent);
        assertThat(p.at("/materialCount").asInt()).isEqualTo(1);
    }

    @Test
    void hierarchyRules() throws Exception {
        String a = createCategory(null, catCode(), "A", "RAW");
        String b = createCategory(a, catCode(), "B", "RAW");
        // R02：上级不能是自己的下级
        JsonNode nodeA = findNode(ok(doGet("/api/engineering/categories/tree", admin)), a);
        assertError(doPut("/api/engineering/categories/" + a, admin, updateBody(nodeA, b)), "上级类别不能是自己或自己的下级");
        // R03：最多 5 级
        String c = createCategory(b, catCode(), "C", "RAW");
        String d = createCategory(c, catCode(), "D", "RAW");
        String e = createCategory(d, catCode(), "E", "RAW");
        Map<String, Object> sixth = new HashMap<>();
        sixth.put("parentId", e);
        sixth.put("code", catCode());
        sixth.put("name", "六级");
        sixth.put("codePrefix", "F");
        sixth.put("defaultMaterialType", "RAW");
        sixth.put("defaultTracking", "NONE");
        sixth.put("defaultIqcRequired", true);
        sixth.put("sort", 10);
        assertError(doPost("/api/engineering/categories", admin, sixth), "物料类别不能超过 5 级");
        // R06：有启用的下级不能停用
        assertError(doPost("/api/engineering/categories/" + d + "/disable", admin, null), "请先停用下级类别");
        ok(doPost("/api/engineering/categories/" + e + "/disable", admin, null));
        ok(doPost("/api/engineering/categories/" + d + "/disable", admin, null));
        // 停用的类别不出现在选择器
        assertThat(ok(doGet("/api/engineering/categories/simple-tree", admin)).findValuesAsText("id")).doesNotContain(d, e);
        // R01：编码唯一
        String dup = catCode();
        createCategory(null, dup, "DUP", "RAW");
        Map<String, Object> again = new HashMap<>(sixth);
        again.put("parentId", null);
        again.put("code", dup);
        again.put("name", "重复");
        assertError(doPost("/api/engineering/categories", admin, again), "类别编码「" + dup + "」已存在");
        // 没有下级和物料的可以删除
        ok(doDelete("/api/engineering/categories/" + e, admin));
    }

    private static Map<String, Object> updateBody(JsonNode node, String parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", parentId);
        body.put("code", node.at("/code").asText());
        body.put("name", node.at("/name").asText());
        body.put("codePrefix", node.at("/codePrefix").asText());
        body.put("defaultMaterialType", node.at("/defaultMaterialType").asText());
        body.put("defaultTracking", node.at("/defaultTracking").asText());
        body.put("defaultIqcRequired", node.at("/defaultIqcRequired").asBoolean());
        body.put("sort", node.at("/sort").asInt());
        body.put("version", node.at("/version").asInt());
        return body;
    }

    static JsonNode findNode(JsonNode nodes, String id) {
        for (JsonNode n : nodes) {
            if (n.at("/id").asText().equals(id)) return n;
            JsonNode c = findNode(n.at("/children"), id);
            if (c != null) return c;
        }
        return null;
    }
}
