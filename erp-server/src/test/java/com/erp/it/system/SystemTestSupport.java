package com.erp.it.system;

import com.erp.it.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 系统管理集成测试的公共数据准备 */
abstract class SystemTestSupport extends AbstractIntegrationTest {

    /** 初始根公司 HQ */
    static final String HQ = "100";
    static final String PASSWORD = "Passw0rd!2026";

    protected String admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = loginAsAdmin();
    }

    protected String createOrg(String parentId, String type, String code, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", parentId);
        body.put("orgType", type);
        body.put("code", code);
        body.put("name", name);
        body.put("sort", 10);
        return ok(doPost("/api/system/orgs", admin, body)).asText();
    }

    protected String createDept(String parentId, String name) throws Exception {
        return createOrg(parentId, "DEPT", "D" + uniq(), name);
    }

    protected String createRole(String dataScope, List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String id = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", dataScope, "sort", 10))).asText();
        if (!permissions.isEmpty()) ok(doPut("/api/system/roles/" + id + "/permissions", admin, Map.of("permissions", permissions)));
        return id;
    }

    /** 新建用户（指定密码，默认下次登录不强制改密），返回用户 ID */
    protected String createUser(String username, String deptId, List<String> roleIds, boolean mustChange) throws Exception {
        return ok(doPost("/api/system/users", admin, userBody(username, deptId, roleIds, mustChange))).at("/id").asText();
    }

    protected Map<String, Object> userBody(String username, String deptId, List<String> roleIds, boolean mustChange) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("realName", "用户" + username);
        body.put("deptId", deptId);
        body.put("roleIds", roleIds);
        body.put("password", PASSWORD);
        body.put("mustChangePassword", mustChange);
        return body;
    }

    protected JsonNode orgTree(String keyword) throws Exception {
        return ok(doGet("/api/system/orgs/tree" + (keyword == null ? "" : "?keyword=" + keyword), admin));
    }

    /** 在树中按 ID 查找节点 */
    protected static JsonNode findNode(JsonNode nodes, String id) {
        for (JsonNode n : nodes) {
            if (n.at("/id").asText().equals(id)) return n;
            JsonNode c = findNode(n.at("/children"), id);
            if (c != null) return c;
        }
        return null;
    }
}
