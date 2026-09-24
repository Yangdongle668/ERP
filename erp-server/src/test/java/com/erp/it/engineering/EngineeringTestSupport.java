package com.erp.it.engineering;

import com.erp.it.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 研发工程集成测试的公共数据准备：每个测试使用独立的类别（独立的编码前缀和流水号） */
abstract class EngineeringTestSupport extends AbstractIntegrationTest {

    protected String admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = loginAsAdmin();
    }

    /** 新建一级类别，返回 ID；编码与前缀使用唯一后缀 */
    protected String createCategory(String parentId, String code, String prefix, String type) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", parentId);
        body.put("code", code);
        body.put("name", "类别" + code);
        body.put("codePrefix", prefix);
        body.put("defaultMaterialType", type);
        body.put("defaultBaseUom", "PCS");
        body.put("defaultTracking", "BATCH");
        body.put("defaultIqcRequired", true);
        body.put("sort", 10);
        return ok(doPost("/api/engineering/categories", admin, body)).asText();
    }

    /** 唯一的类别编码（大写字母 + 数字，≤ 8 位，可同时作为前缀） */
    protected static String catCode() {
        String u = uniq();
        return "C" + u.substring(Math.max(0, u.length() - 7));
    }

    protected Map<String, Object> material(String categoryId, String name, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", categoryId);
        m.put("name", name);
        m.put("materialType", type);
        m.put("baseUom", "PCS");
        return m;
    }

    protected String createMaterial(Map<String, Object> body) throws Exception {
        return ok(doPost("/api/engineering/materials", admin, body)).asText();
    }

    /** 新建并启用 */
    protected String enabledMaterial(String categoryId, String name, String type) throws Exception {
        String id = createMaterial(material(categoryId, name, type));
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    protected JsonNode getMaterial(String id) throws Exception {
        return ok(doGet("/api/engineering/materials/" + id, admin));
    }

    protected void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    protected void resetParam(String key) throws Exception {
        ok(doPost("/api/system/params/" + key + "/reset", admin, null));
    }

    /** 新建只有指定权限的用户并登录，返回令牌 */
    protected String userWith(List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String roleId = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", "ALL", "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + roleId + "/permissions", admin, Map.of("permissions", permissions)));
        Map<String, Object> dept = new HashMap<>();
        dept.put("parentId", "100");
        dept.put("orgType", "DEPT");
        dept.put("code", "D" + uniq());
        dept.put("name", "研发部" + uniq());
        dept.put("sort", 10);
        String deptId = ok(doPost("/api/system/orgs", admin, dept)).asText();
        String username = "eng" + uniq();
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("realName", "用户" + username);
        user.put("deptId", deptId);
        user.put("roleIds", List.of(roleId));
        user.put("password", "Passw0rd!2026");
        user.put("mustChangePassword", false);
        ok(doPost("/api/system/users", admin, user));
        return login(username, "Passw0rd!2026");
    }
}
