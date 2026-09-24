package com.erp.it.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用户、角色与权限验收用例（01-02、01-03） */
class UserRoleIntegrationTest extends SystemTestSupport {

    @Test
    void usernameUniqueIgnoringCase_USR_T02() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String name = "zs" + uniq();
        createUser(name, dept, List.of(role), false);
        assertError(doPost("/api/system/users", admin, userBody(name.toUpperCase(), dept, List.of(role), false)),
                "用户名「" + name + "」已存在");
    }

    @Test
    void superiorCycle_USR_T03() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String b = createUser("b" + uniq(), dept, List.of(role), false);
        String aName = "a" + uniq();
        var aBody = userBody(aName, dept, List.of(role), false);
        aBody.put("superiorUserId", b);
        String a = ok(doPost("/api/system/users", admin, aBody)).at("/id").asText();
        JsonNode bDetail = ok(doGet("/api/system/users/" + b, admin));
        var bBody = userBody(bDetail.at("/username").asText(), dept, List.of(role), false);
        bBody.put("superiorUserId", a);
        bBody.put("version", bDetail.at("/version").asInt());
        assertError(doPut("/api/system/users/" + b, admin, bBody), "直属上级不能是自己或自己的下属");
    }

    @Test
    void adminCannotDisableSelf_USR_T07() throws Exception {
        assertError(doPost("/api/system/users/1/disable", admin, null), "不能停用当前登录的账号");
    }

    @Test
    void permissionDependenciesAndEffect_ROL_T01_T04_T05() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        // 只授予“新建”，自动补齐依赖“查看”
        String role = createRole("ALL", List.of("system:org:create"));
        List<String> perms = new ArrayList<>();
        ok(doGet("/api/system/roles/" + role + "/permissions", admin)).forEach(n -> perms.add(n.asText()));
        assertThat(perms).containsExactlyInAnyOrder("system:org:create", "system:org:query");

        String username = "p" + uniq();
        createUser(username, dept, List.of(role), false);
        String token = login(username, PASSWORD);
        ok(doGet("/api/system/orgs/tree", token));
        assertThat(doGet("/api/system/users?pageNo=1&pageSize=10", token).at("/code").asInt()).isEqualTo(403);

        // 取消权限后立即生效（无需重新登录）
        ok(doPut("/api/system/roles/" + role + "/permissions", admin, Map.of("permissions", List.of())));
        assertThat(doGet("/api/system/orgs/tree", token).at("/code").asInt()).isEqualTo(403);
    }

    @Test
    void grantUnknownPermissionRejected_R06() throws Exception {
        String role = createRole("SELF", List.of());
        assertError(doPut("/api/system/roles/" + role + "/permissions", admin, Map.of("permissions", List.of("x:y:z"))), "权限点「x:y:z」不存在");
        assertError(doPut("/api/system/roles/" + role + "/permissions", admin, Map.of("permissions", List.of("*"))), "权限点「*」不存在");
    }

    @Test
    void dataScopeOnUserList_USR_R11_ROL_T02_T03() throws Exception {
        String dept = createDept(HQ, "生产部" + uniq());
        String smt = createDept(dept, "SMT 车间");
        String other = createDept(HQ, "其他" + uniq());
        String selfRole = createRole("SELF", List.of("system:user:query"));
        String deptRole = createRole("DEPT_AND_CHILD", List.of("system:user:query"));
        String basic = createRole("SELF", List.of());

        String leader = "ld" + uniq();
        String leaderId = createUser(leader, dept, List.of(selfRole), false);
        String inSmt = createUser("s" + uniq(), smt, List.of(basic), false);
        String outside = createUser("o" + uniq(), other, List.of(basic), false);

        // 仅本人：只看到自己
        String token = login(leader, PASSWORD);
        JsonNode page = ok(doGet("/api/system/users?pageNo=1&pageSize=100", token));
        assertThat(ids(page)).containsExactly(leaderId);

        // 增加“本部门及下级”角色：取并集，看到生产部及 SMT 车间，看不到其他部门
        JsonNode detail = ok(doGet("/api/system/users/" + leaderId, admin));
        var body = userBody(leader, dept, List.of(selfRole, deptRole), false);
        body.put("version", detail.at("/version").asInt());
        ok(doPut("/api/system/users/" + leaderId, admin, body));
        page = ok(doGet("/api/system/users?pageNo=1&pageSize=100", token));
        assertThat(ids(page)).contains(leaderId, inSmt).doesNotContain(outside);
        assertThat(page.at("/total").asInt()).isEqualTo(ids(page).size());
    }

    @Test
    void roleDeleteAndDisableRules_ROL_T06_T07() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        for (int i = 0; i < 3; i++) createUser("rm" + uniq(), dept, List.of(role), false);
        assertError(doDelete("/api/system/roles/" + role, admin), "该角色下还有 3 个用户，请先移除");
        JsonNode resp = doPost("/api/system/roles/" + role + "/disable", admin, null);
        assertThat(resp.at("/msg").asText()).startsWith("停用后以下用户将没有任何可用角色：");
        // 内置角色不能修改
        assertError(doPost("/api/system/roles/1/disable", admin, null), "内置角色不能修改");
    }

    @Test
    void removeLastRoleRejected() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String name = "m" + uniq();
        String uid = createUser(name, dept, List.of(role), false);
        assertError(doDelete("/api/system/roles/" + role + "/users/" + uid, admin), "用户「用户" + name + "」只有这一个角色，不能移除");
    }

    @Test
    void copyRoleCopiesPermissions() throws Exception {
        String role = createRole("DEPT", List.of("system:org:query"));
        String copy = ok(doPost("/api/system/roles/" + role + "/copy", admin, null)).asText();
        JsonNode d = ok(doGet("/api/system/roles/" + copy, admin));
        assertThat(d.at("/code").asText()).endsWith("_COPY");
        assertThat(d.at("/dataScope").asText()).isEqualTo("DEPT");
        assertThat(ok(doGet("/api/system/roles/" + copy + "/permissions", admin)).get(0).asText()).isEqualTo("system:org:query");
    }

    @Test
    void permissionTreeContainsDeclaredModules() throws Exception {
        JsonNode tree = ok(doGet("/api/system/permissions/tree", admin));
        List<String> modules = new ArrayList<>();
        tree.forEach(m -> modules.add(m.at("/code").asText()));
        assertThat(modules).contains("system", "engineering");
    }

    @Test
    void userSimpleSearch() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String name = "sch" + uniq();
        String id = createUser(name, dept, List.of(role), false);
        JsonNode list = ok(doGet("/api/system/users/simple?keyword=" + name, admin));
        assertThat(list.get(0).at("/id").asText()).isEqualTo(id);
        assertThat(ok(doGet("/api/system/users/simple?ids=" + id, admin)).size()).isEqualTo(1);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.at("/list").forEach(n -> ids.add(n.at("/id").asText()));
        return ids;
    }
}
