package com.erp.it.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 组织架构验收用例（01-01 SYS-ORG-T01～T08） */
class OrgIntegrationTest extends SystemTestSupport {

    @Test
    void createDeptUnderCompany_T01() throws Exception {
        String id = createDept(HQ, "采购部" + uniq());
        JsonNode node = findNode(orgTree(null), id);
        assertThat(node).isNotNull();
        assertThat(node.at("/parentId").asText()).isEqualTo(HQ);
        assertThat(node.at("/level").asInt()).isEqualTo(2);
    }

    @Test
    void companyUnderDeptRejected_T02() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        Map<String, Object> body = new HashMap<>(Map.of("parentId", dept, "orgType", "COMPANY", "code", "C" + uniq(), "name", "子公司", "sort", 10));
        assertError(doPost("/api/system/orgs", admin, body), "部门下不能创建公司");
        Map<String, Object> root = new HashMap<>(Map.of("orgType", "DEPT", "code", "C" + uniq(), "name", "根部门", "sort", 10));
        assertError(doPost("/api/system/orgs", admin, root), "顶级组织必须是公司");
    }

    @Test
    void parentCannotBeDescendant_T03() throws Exception {
        String a = createDept(HQ, "A" + uniq());
        String b = createDept(a, "B");
        String c = createDept(b, "C");
        JsonNode detail = ok(doGet("/api/system/orgs/" + a, admin));
        Map<String, Object> body = orgBody(detail);
        body.put("parentId", c);
        assertError(doPut("/api/system/orgs/" + a, admin, body), "上级组织不能是自己或自己的下级");
    }

    @Test
    void codeUniqueIgnoringCase_T04() throws Exception {
        String code = "PUR" + uniq();
        createOrg(HQ, "DEPT", code, "采购" + uniq());
        Map<String, Object> body = new HashMap<>(Map.of("parentId", HQ, "orgType", "DEPT", "code", code.toLowerCase(), "name", "采购2" + uniq(), "sort", 10));
        assertError(doPost("/api/system/orgs", admin, body), "组织编码「" + code + "」已存在");
    }

    @Test
    void cannotDisableDeptWithEnabledUsers_T05() throws Exception {
        String dept = createDept(HQ, "采购" + uniq());
        String role = createRole("SELF", List.of());
        createUser("u" + uniq(), dept, List.of(role), false);
        createUser("u" + uniq(), dept, List.of(role), false);
        assertError(doPost("/api/system/orgs/" + dept + "/disable", admin, null), "该部门下还有 2 个启用的用户，请先调整用户部门或停用用户");
    }

    @Test
    void moveUpdatesDescendantPaths_T06() throws Exception {
        String company2 = createOrg(HQ, "COMPANY", "CO" + uniq(), "子公司" + uniq());
        String pur = createDept(HQ, "采购" + uniq());
        String sub = createDept(pur, "采购一组");
        String subsub = createDept(sub, "采购一组A");
        Map<String, Object> body = orgBody(ok(doGet("/api/system/orgs/" + pur, admin)));
        body.put("parentId", company2);
        ok(doPut("/api/system/orgs/" + pur, admin, body));
        JsonNode tree = orgTree(null);
        assertThat(findNode(tree, pur).at("/level").asInt()).isEqualTo(3);
        assertThat(findNode(tree, sub).at("/level").asInt()).isEqualTo(4);
        assertThat(findNode(tree, subsub).at("/level").asInt()).isEqualTo(5);
        assertThat(findNode(findNode(tree, company2).at("/children"), subsub)).isNotNull();
    }

    @Test
    void keywordShowsAncestors_T08() throws Exception {
        String name = "关键字" + uniq();
        String parent = createDept(HQ, "上级" + uniq());
        String child = createDept(parent, name);
        createDept(HQ, "无关" + uniq());
        JsonNode tree = orgTree(name);
        assertThat(tree.size()).isEqualTo(1);
        assertThat(findNode(tree, parent)).isNotNull();
        assertThat(findNode(tree, child)).isNotNull();
    }

    @Test
    void deleteRules_R07() throws Exception {
        String parent = createDept(HQ, "删除测试" + uniq());
        createDept(parent, "子部门");
        assertError(doDelete("/api/system/orgs/" + parent, admin), "该组织存在下级组织，不能删除");
        String lonely = createDept(HQ, "空部门" + uniq());
        ok(doDelete("/api/system/orgs/" + lonely, admin));
    }

    @Test
    void lastRootCompanyCannotBeDisabled_R10() throws Exception {
        assertThat(doPost("/api/system/orgs/" + HQ + "/disable", admin, null).at("/code").asInt()).isNotEqualTo(0);
    }

    private static Map<String, Object> orgBody(JsonNode d) {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", d.at("/parentId").isMissingNode() || d.at("/parentId").isNull() ? null : d.at("/parentId").asText());
        body.put("orgType", d.at("/orgType").asText());
        body.put("code", d.at("/code").asText());
        body.put("name", d.at("/name").asText());
        body.put("sort", d.at("/sort").asInt());
        body.put("version", d.at("/version").asInt());
        return body;
    }
}
