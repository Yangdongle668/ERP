package com.erp.it.importing;

import com.erp.it.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 导入批次与回滚（05-01 第 9 节、05-02 3.5、03-01 3.5）；导入编码推进流水号 */
class ImportRollbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    private String admin;

    @BeforeEach
    void login() throws Exception {
        admin = loginAsAdmin();
    }

    /** 按模板格式生成文件：第一行列名、第二行说明（忽略）、之后为数据 */
    private static byte[] xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("导入");
            Row h = s.createRow(0);
            for (int i = 0; i < headers.size(); i++) h.createCell(i).setCellValue(headers.get(i));
            s.createRow(1).createCell(0).setCellValue("说明");
            for (int r = 0; r < rows.size(); r++) {
                Row row = s.createRow(r + 2);
                for (int i = 0; i < rows.get(r).size(); i++) row.createCell(i).setCellValue(rows.get(r).get(i));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode importFile(String url, byte[] file, String params) throws Exception {
        String body = mockMvc.perform(multipart(url + (params == null ? "" : "?" + params))
                        .file(new MockMultipartFile("file", "导入.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file))
                        .header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body);
    }

    /** 类别导入 → 物料导入（新增 + 更新）→ 回滚物料（删除新增、恢复更新）→ 回滚类别；可重新导入 */
    @Test
    void categoryAndMaterialRollback() throws Exception {
        String u = uniq();
        String group = "G" + u.substring(Math.max(0, u.length() - 6));
        String cat = "C" + u.substring(Math.max(0, u.length() - 6));
        String prefix = "T" + u.substring(u.length() - 2) + "-";
        byte[] cats = xlsx(List.of("类别编码", "名称", "上级类别编码", "编码前缀", "流水号位数", "默认物料类型", "默认基本单位", "默认库存管理", "默认来料检验"),
                List.of(List.of(group, "分组" + u, "", group, "", "原材料", "PCS", "不管理", "是"),
                        List.of(cat, "连接片" + u, group, prefix, "7", "原材料", "PCS", "批次", "否")));
        JsonNode r1 = ok(importFile("/api/engineering/categories/import", cats, null));
        assertThat(r1.at("/success").asInt()).isEqualTo(2);
        // 再导入一次：编码已存在跳过
        JsonNode check = ok(importFile("/api/engineering/categories/import/check", cats, null));
        assertThat(check.at("/rows/0/action").asText()).isEqualTo("跳过（已存在）");

        // 已有物料，之后被导入更新
        String existCode = prefix + "0000009";
        ok(doPost("/api/engineering/materials", admin, Map.of("code", existCode, "name", "原名称", "spec", "原规格", "materialType", "RAW",
                "categoryId", jdbc.queryForObject("SELECT id FROM eng_material_category WHERE code = ?", Long.class, cat).toString(), "baseUom", "PCS")));
        String newCode = prefix + "0010001";
        byte[] mats = xlsx(List.of("物料类别编码", "编码", "名称", "规格型号", "物料类型", "基本单位", "备注"),
                List.of(List.of(cat, newCode, "钢镀镍", "连接片/钢镀镍/75*10*0.2mm", "原材料", "PCS", "原规格：钢镀镍75*10"),
                        List.of(cat, existCode, "新名称", "新规格", "原材料", "PCS", "")));
        JsonNode r2 = ok(importFile("/api/engineering/materials/import", mats, "mode=UPDATE"));
        assertThat(r2.at("/success").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT name FROM eng_material WHERE code = ?", String.class, existCode)).isEqualTo("新名称");
        // 导入的编码推进流水号：之后自动生成 prefix + 0010002
        String auto = ok(doPost("/api/engineering/materials", admin, Map.of("name", "自动", "materialType", "RAW", "baseUom", "PCS",
                "categoryId", jdbc.queryForObject("SELECT id FROM eng_material_category WHERE code = ?", Long.class, cat).toString()))).asText();
        assertThat(jdbc.queryForObject("SELECT code FROM eng_material WHERE id = ?", String.class, Long.valueOf(auto))).isEqualTo(prefix + "0010002");

        JsonNode batches = ok(doGet("/api/engineering/materials/import/batches", admin));
        String batchId = batches.at("/0/id").asText();
        assertThat(batches.at("/0/createdCount").asInt()).isEqualTo(1);
        assertThat(batches.at("/0/updatedCount").asInt()).isEqualTo(1);
        // 类别下有物料：类别批次不能回滚
        String catBatch = ok(doGet("/api/engineering/categories/import/batches", admin)).at("/0/id").asText();
        assertThat(doPost("/api/engineering/categories/import/batches/" + catBatch + "/rollback", admin, null).at("/code").asInt()).isNotZero();

        ok(doPost("/api/engineering/materials/import/batches/" + batchId + "/rollback", admin, null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eng_material WHERE code = ?", Long.class, newCode)).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM eng_material WHERE code = ? AND deleted = 0", String.class, existCode)).isEqualTo("原名称");
        assertThat(jdbc.queryForObject("SELECT spec FROM eng_material WHERE code = ? AND deleted = 0", String.class, existCode)).isEqualTo("原规格");
        assertError(doPost("/api/engineering/materials/import/batches/" + batchId + "/rollback", admin, null), "该批次已回滚");
        // 回滚后可以重新导入同一编码
        assertThat(ok(importFile("/api/engineering/materials/import", mats, "mode=SKIP")).at("/success").asInt()).isEqualTo(1);

        // 删掉本测试建的物料后可以回滚类别
        jdbc.update("DELETE FROM eng_material WHERE category_id = (SELECT id FROM eng_material_category WHERE code = ?)", cat);
        ok(doPost("/api/engineering/categories/import/batches/" + catBatch + "/rollback", admin, null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eng_material_category WHERE code IN (?, ?)", Long.class, group, cat)).isZero();
    }

    /** 客户导入（手工编码 LD-G-0011）→ 推进流水号 → 回滚（物理删除，可重新导入）；有业务数据时不能回滚 */
    @Test
    void customerRollback() throws Exception {
        String u = uniq();
        String code = "LD-G-9" + u.substring(u.length() - 3);
        byte[] file = xlsx(List.of("编码", "客户名称", "英文名称", "国家", "应用领域", "主联系人", "联系人邮箱"),
                List.of(List.of(code, "Warp " + u, "Warp " + u, "KR", "", "Kim", "kim@example.com")));
        JsonNode r = ok(importFile("/api/crm/customers/import", file, null));
        assertThat(r.at("/success").asInt()).as(r.toString()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT app_domain FROM crm_customer WHERE code = ?", String.class, code)).isEqualTo("G");
        // 之后自动生成的 G 领域编码在导入的编码之后
        Map<String, Object> c = Map.of("name", "Auto " + u, "nameEn", "Auto " + u, "country", "US", "appDomain", "G");
        String id = ok(doPost("/api/crm/customers", admin, c)).at("/id").asText();
        String autoCode = jdbc.queryForObject("SELECT code FROM crm_customer WHERE id = ?", String.class, Long.valueOf(id));
        assertThat(Integer.parseInt(autoCode.substring(5))).isGreaterThan(Integer.parseInt(code.substring(5)));

        String batch = ok(doGet("/api/crm/customers/import/batches", admin)).at("/0/id").asText();
        ok(doPost("/api/crm/customers/import/batches/" + batch + "/rollback", admin, null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE code = ?", Long.class, code)).isZero();
        assertThat(ok(importFile("/api/crm/customers/import", file, null)).at("/success").asInt()).isEqualTo(1);

        // 有跟进记录后不能回滚
        String cid = jdbc.queryForObject("SELECT id FROM crm_customer WHERE code = ?", Long.class, code).toString();
        jdbc.update("INSERT INTO crm_followup (id, customer_id, followup_type, followup_at, subject, content, owner_id, created_at, updated_at) "
                + "VALUES (?, ?, 'CALL', CURRENT_TIMESTAMP, '电话', '电话', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", System.nanoTime(), Long.valueOf(cid));
        String batch2 = ok(doGet("/api/crm/customers/import/batches", admin)).at("/0/id").asText();
        assertThat(doPost("/api/crm/customers/import/batches/" + batch2 + "/rollback", admin, null).at("/msg").asText()).contains(code);
    }
}
