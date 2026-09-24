package com.erp.it.engineering;

import com.erp.module.engineering.api.material.MaterialApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 物料（需求 05-02 验收用例 ENG-MAT-T01～T12 中不依赖其他模块的部分） */
class MaterialIntegrationTest extends EngineeringTestSupport {

    @Autowired
    MaterialApi materialApi;

    /** T01：编码留空按类别前缀生成；类型、单位、批次管理、来料检验取类别默认值 */
    @Test
    void createWithCategoryDefaults() throws Exception {
        String code = catCode();
        String cat = createCategory(null, code, code, "RAW");
        String id = createMaterial(material(cat, "FPC 排线 20P", "RAW"));
        JsonNode m = getMaterial(id);
        assertThat(m.at("/code").asText()).isEqualTo(code + "00001");
        assertThat(m.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(m.at("/tracking").asText()).isEqualTo("BATCH");
        assertThat(m.at("/iqcRequired").asBoolean()).isTrue();
        assertThat(m.at("/sourceType").asText()).isEqualTo("PURCHASE");
        assertThat(m.at("/orderPolicy").asText()).isEqualTo("LOT_FOR_LOT");
        assertThat(m.at("/purchaseTaxRate").decimalValue()).isEqualByComparingTo("0.13");
        assertThat(m.at("/categoryCode").asText()).isEqualTo(code);
        // 第二个物料继续计数
        String id2 = createMaterial(material(cat, "FPC 排线 30P", "RAW"));
        assertThat(getMaterial(id2).at("/code").asText()).isEqualTo(code + "00002");
        // 成品默认自制、完工检验、出货检验
        String fg = createMaterial(material(cat, "成品A", "FINISHED"));
        JsonNode f = getMaterial(fg);
        assertThat(f.at("/sourceType").asText()).isEqualTo("MAKE");
        assertThat(f.at("/fqcRequired").asBoolean()).isTrue();
        assertThat(f.at("/oqcRequired").asBoolean()).isTrue();
    }

    /** T02 / T03：查重（WARN 提示可保存；BLOCK 阻止） */
    @Test
    void duplicateCheck() throws Exception {
        String code = catCode();
        String cat = createCategory(null, code, code, "RAW");
        Map<String, Object> body = material(cat, "FPC 排线 20P", "RAW");
        body.put("spec", "0.5mm");
        body.put("mpn", "ab-123 x");
        String first = createMaterial(body);
        String firstCode = getMaterial(first).at("/code").asText();

        JsonNode r = ok(doPost("/api/engineering/materials/duplicate-check", admin,
                Map.of("categoryId", cat, "name", "FPC排线 20p", "spec", "0.5 MM")));
        assertThat(r.at("/mode").asText()).isEqualTo("WARN");
        assertThat(r.at("/suspects/0/code").asText()).isEqualTo(firstCode);
        // 制造商料号忽略空格与大小写
        r = ok(doPost("/api/engineering/materials/duplicate-check", admin, Map.of("mpn", "AB-123X")));
        assertThat(r.at("/suspects/0/reason").asText()).isEqualTo("MPN");
        createMaterial(body); // WARN 允许保存

        setParam("eng.material.duplicate-check", "BLOCK");
        try {
            assertError(doPost("/api/engineering/materials", admin, body), "发现疑似重复物料：" + firstCode + "、" + code + "00002");
        } finally {
            resetParam("eng.material.duplicate-check");
        }
    }

    /** T05：已启用物料不能修改基本单位；R06 FEFO 需保质期；T08 采购单位需有换算 */
    @Test
    void lockedFieldsAndUnitRules() throws Exception {
        String code = catCode();
        String cat = createCategory(null, code, code, "RAW");
        Map<String, Object> body = material(cat, "螺丝", "RAW");
        body.put("purchaseUom", "BOX");
        assertError(doPost("/api/engineering/materials", admin, body), "采购单位 BOX 没有与基本单位的换算关系");
        body.put("uoms", List.of(Map.of("uom", "BOX", "rate", 100, "remark", "每箱 100 个")));
        body.put("issueRule", "FEFO");
        assertError(doPost("/api/engineering/materials", admin, body), "出库规则为“先到期先出”时必须填写保质期");
        body.put("shelfLifeDays", 365);
        String id = createMaterial(body);
        JsonNode m = getMaterial(id);
        assertThat(m.at("/uoms/0/uom").asText()).isEqualTo("BOX");

        // T07：convertToBase 使用物料换算
        assertThat(materialApi.convertToBase(Long.valueOf(id), new BigDecimal("3"), "BOX")).isEqualByComparingTo("300");

        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        body.put("baseUom", "KG");
        body.put("version", getMaterial(id).at("/version").asInt());
        assertError(doPut("/api/engineering/materials/" + id, admin, body), "物料「" + m.at("/code").asText() + "」已启用，只能修改名称、规格等描述信息");
        // 描述信息可以修改
        body.put("baseUom", "PCS");
        body.put("name", "螺丝 M3");
        ok(doPut("/api/engineering/materials/" + id, admin, body));
        assertThat(getMaterial(id).at("/name").asText()).isEqualTo("螺丝 M3");

        // 旧版本号再次提交 → 并发冲突
        assertThat(doPut("/api/engineering/materials/" + id, admin, body).at("/code").asInt()).isEqualTo(1_000_000_001);
    }

    /** 状态流转、删除限制、批量启停、按编码查询（T12） */
    @Test
    void lifecycle() throws Exception {
        String code = catCode();
        String cat = createCategory(null, code, code, "RAW");
        String id = createMaterial(material(cat, "电阻 10K", "RAW"));
        String mcode = getMaterial(id).at("/code").asText();
        // 草稿按编码查不到（只返回启用物料）
        JsonNode none = ok(doGet("/api/engineering/materials/by-code/" + mcode.toLowerCase(), admin));
        assertThat(none.isNull() || none.isMissingNode()).isTrue();
        assertThat(ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null)).asText()).isEqualTo("ENABLED");
        assertThat(ok(doGet("/api/engineering/materials/by-code/" + mcode.toLowerCase(), admin)).at("/name").asText()).isEqualTo("电阻 10K");
        assertError(doDelete("/api/engineering/materials/" + id, admin), "只有草稿状态的物料可以删除，已启用的物料请停用");
        JsonNode refs = ok(doGet("/api/engineering/materials/" + id + "/references", admin));
        assertThat(refs.at("/bomCount").asInt()).isZero();
        ok(doPost("/api/engineering/materials/" + id + "/disable", admin, null));
        assertError(doPost("/api/engineering/materials/" + id + "/disable", admin, null), "当前状态【停用】不允许执行【停用】操作");

        String d1 = createMaterial(material(cat, "电容 1", "RAW"));
        String d2 = createMaterial(material(cat, "电容 2", "RAW"));
        JsonNode batch = ok(doPost("/api/engineering/materials/batch-enable", admin, Map.of("ids", List.of(d1, d2, id))));
        assertThat(batch.at("/success").asInt()).isEqualTo(3);
        batch = ok(doPost("/api/engineering/materials/batch-enable", admin, Map.of("ids", List.of(d1))));
        assertThat(batch.at("/failures/0/message").asText()).contains("不允许执行");

        String draft = createMaterial(material(cat, "待删除", "RAW"));
        ok(doDelete("/api/engineering/materials/" + draft, admin));

        // 分页：类别筛选含下级、按编码排序
        JsonNode page = ok(doGet("/api/engineering/materials?categoryId=" + cat + "&sortField=code&sortOrder=desc", admin));
        assertThat(page.at("/total").asInt()).isEqualTo(3);
        assertThat(page.at("/list/0/code").asText()).isEqualTo(code + "00003");
    }

    /** T10 / R13：无成本字段权限时标准成本为空，保存不会清空已有成本 */
    @Test
    void costFieldPermission() throws Exception {
        String code = catCode();
        String cat = createCategory(null, code, code, "RAW");
        Map<String, Object> body = material(cat, "铜箔", "RAW");
        body.put("standardCost", new BigDecimal("12.5"));
        String id = createMaterial(body);
        assertThat(getMaterial(id).at("/standardCost").decimalValue()).isEqualByComparingTo("12.5");

        String user = userWith(List.of("eng:material:query", "eng:material:update"));
        JsonNode m = ok(doGet("/api/engineering/materials/" + id, user));
        assertThat(m.at("/standardCost").isNull() || m.at("/standardCost").isMissingNode()).isTrue();
        body.put("standardCost", null);
        body.put("version", m.at("/version").asInt());
        ok(doPut("/api/engineering/materials/" + id, user, body));
        assertThat(getMaterial(id).at("/standardCost").decimalValue()).isEqualByComparingTo("12.5");
    }

    /** 启用审批：参数开启后草稿进入待审批（未配置流程时直接启用） */
    @Test
    void enableWithApprovalParam() throws Exception {
        String code = catCode();
        String cat = createCategory(null, code, code, "RAW");
        String id = createMaterial(material(cat, "审批物料", "RAW"));
        setParam("eng.material.enable-approval", "true");
        try {
            JsonNode settings = ok(doGet("/api/engineering/materials/settings", admin));
            assertThat(settings.at("/enableApproval").asBoolean()).isTrue();
            // 未配置 ENG_MATERIAL 审批流 → NOT_REQUIRED → 直接启用
            assertThat(ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null)).asText()).isEqualTo("ENABLED");
        } finally {
            resetParam("eng.material.enable-approval");
        }
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        JsonNode r = call(org.springframework.http.HttpMethod.POST, "/api/engineering/materials", admin, Map.of("name", ""));
        assertThat(r.at("/code").asInt()).isEqualTo(400);
    }
}
