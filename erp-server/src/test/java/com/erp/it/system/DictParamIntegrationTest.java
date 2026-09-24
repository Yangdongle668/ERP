package com.erp.it.system;

import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.param.ParamApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 数据字典（01-04）与系统参数（01-10）验收用例 */
class DictParamIntegrationTest extends SystemTestSupport {

    @Autowired
    private DictApi dictApi;
    @Autowired
    private ParamApi paramApi;

    @Test
    void customDictLifecycle_DIC_T01_T03_T04() throws Exception {
        String type = "crm_source_" + uniq();
        ok(doPost("/api/system/dict-types", admin, Map.of("code", type, "name", "客户来源")));
        String expo = ok(doPost("/api/system/dict-items", admin, item(type, "EXPO", "展会", 10, false))).asText();
        String web = ok(doPost("/api/system/dict-items", admin, item(type, "website", "官网", 20, true))).asText();
        assertThat(dictApi.getItems(type)).extracting("value").containsExactly("EXPO", "WEBSITE");
        assertThat(dictApi.label(type, "WEBSITE")).isEqualTo("官网");

        // 设为默认会取消其他项的默认（T04）
        Map<String, Object> expoDefault = item(type, "EXPO", "展会", 10, true);
        expoDefault.put("version", itemVersion(type, expo));
        ok(doPut("/api/system/dict-items/" + expo, admin, expoDefault));
        JsonNode items = ok(doGet("/api/system/dict-items?typeCode=" + type, admin));
        assertThat(items.get(0).at("/isDefault").asBoolean()).isTrue();
        assertThat(items.get(1).at("/isDefault").asBoolean()).isFalse();

        // 停用后：选择器不可选，但标签仍可显示（T03）
        long v1 = ok(doGet("/api/system/dicts/version", admin)).asLong();
        ok(doPost("/api/system/dict-items/" + expo + "/disable", admin, null));
        assertThat(dictApi.isValid(type, "EXPO")).isFalse();
        assertThat(dictApi.label(type, "EXPO")).isEqualTo("展会");
        assertThat(ok(doGet("/api/system/dicts/version", admin)).asLong()).isGreaterThan(v1);
        assertThatThrownBy(() -> dictApi.validate(type, "EXPO", "客户来源")).hasMessage("客户来源的值「EXPO」无效");

        // 自定义字典项可以删除；有项的类型不能删除
        assertError(doDelete("/api/system/dict-types/" + typeId(type), admin), "请先删除该类型下的字典项");
        ok(doDelete("/api/system/dict-items/" + web, admin));
        assertError(doPost("/api/system/dict-items", admin, item(type, "EXPO", "展会2", 30, false)), "字典值「EXPO」已存在");
        assertError(doPost("/api/system/dict-items", admin, item(type, "EXPO2", "展会", 30, false)), "字典标签「展会」已存在");
    }

    @Test
    void builtinDictProtected_DIC_T05() throws Exception {
        JsonNode items = ok(doGet("/api/system/dict-items?typeCode=sys_trade_term", admin));
        assertThat(items.size()).isEqualTo(10);
        String fob = null;
        for (JsonNode i : items) if (i.at("/value").asText().equals("FOB")) fob = i.at("/id").asText();
        assertError(doDelete("/api/system/dict-items/" + fob, admin), "内置字典项不能删除、停用或修改值");
        assertError(doPost("/api/system/dict-items/" + fob + "/disable", admin, null), "内置字典项不能删除、停用或修改值");
        assertError(doDelete("/api/system/dict-types/" + typeId("sys_trade_term"), admin), "内置字典不能删除");
    }

    @Test
    void dictBundleForFrontend() throws Exception {
        JsonNode bundle = ok(doGet("/api/system/dicts/all", admin));
        assertThat(bundle.at("/version").asLong()).isPositive();
        boolean found = false;
        for (JsonNode t : bundle.at("/types")) if (t.at("/code").asText().equals("sys_settlement_method")) found = t.at("/items").size() == 6;
        assertThat(found).isTrue();
    }

    @Test
    void paramRangeAndChanges_PAR_T01_T02_T03() throws Exception {
        assertError(doPut("/api/system/params", admin, List.of(Map.of("key", "sys.login.max-fail-count", "value", "11"))),
                "参数「登录失败锁定次数」的值必须在 3～10 之间");
        JsonNode diffs = ok(doPut("/api/system/params", admin, List.of(
                Map.of("key", "sys.file.max-size-mb", "value", "80"),
                Map.of("key", "sys.company.name", "value", "测试 ERP"))));
        assertThat(diffs.size()).isEqualTo(2);
        assertThat(paramApi.getInt("sys.file.max-size-mb")).isEqualTo(80);
        assertThat(ok(doGet("/api/system/params/public", null)).at("/systemName").asText()).isEqualTo("测试 ERP");

        JsonNode list = ok(doGet("/api/system/params?module=system", admin));
        boolean modified = false;
        for (JsonNode p : list) if (p.at("/key").asText().equals("sys.file.max-size-mb")) modified = p.at("/modified").asBoolean();
        assertThat(modified).isTrue();

        ok(doPost("/api/system/params/sys.file.max-size-mb/reset", admin, null));
        ok(doPost("/api/system/params/sys.company.name/reset", admin, null));
        assertThat(paramApi.getInt("sys.file.max-size-mb")).isEqualTo(50);
        assertError(doPut("/api/system/params", admin, List.of(Map.of("key", "sys.password.complexity", "value", "XYZ"))),
                "参数「密码复杂度」的值格式不正确：不是可选值");
    }

    @Test
    void paramModulesNavigation() throws Exception {
        JsonNode modules = ok(doGet("/api/system/params/modules", admin));
        assertThat(modules.get(0).at("/moduleCode").asText()).isEqualTo("system");
        assertThat(modules.get(0).at("/count").asInt()).isGreaterThanOrEqualTo(18);
    }

    private Map<String, Object> item(String type, String value, String label, int sort, boolean isDefault) {
        Map<String, Object> m = new HashMap<>();
        m.put("typeCode", type);
        m.put("value", value);
        m.put("label", label);
        m.put("tagType", "PRIMARY");
        m.put("sort", sort);
        m.put("isDefault", isDefault);
        return m;
    }

    private int itemVersion(String type, String id) throws Exception {
        for (JsonNode i : ok(doGet("/api/system/dict-items?typeCode=" + type, admin))) {
            if (i.at("/id").asText().equals(id)) return i.at("/version").asInt();
        }
        throw new IllegalStateException();
    }

    private String typeId(String code) throws Exception {
        return ok(doGet("/api/system/dict-types?keyword=" + code + "&pageNo=1&pageSize=10", admin)).at("/list/0/id").asText();
    }
}
