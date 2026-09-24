package com.erp.it.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 日志审计（01-11）与导入导出 */
class LogImportIntegrationTest extends SystemTestSupport {

    @Test
    void operLogMasksPassword_LOG_T02_andLoginLog_T01() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "log" + uniq();
        createUser(username, dept, List.of(role), false);
        loginRaw(username, "wrong-password");

        // 操作日志异步写入，稍等
        JsonNode found = null;
        for (int i = 0; i < 30 && found == null; i++) {
            JsonNode page = ok(doGet("/api/system/oper-logs?action=新建用户&pageNo=1&pageSize=50", admin));
            for (JsonNode l : page.at("/list")) {
                JsonNode detail = ok(doGet("/api/system/oper-logs/" + l.at("/id").asText(), admin));
                if (detail.at("/params").asText().contains(username)) found = detail;
            }
            if (found == null) Thread.sleep(100);
        }
        assertThat(found).isNotNull();
        assertThat(found.at("/params").asText()).contains("\"password\":\"******\"").doesNotContain(PASSWORD);
        assertThat(found.at("/result").asText()).isEqualTo("SUCCESS");
        assertThat(found.at("/moduleCode").asText()).isEqualTo("system");

        JsonNode logins = ok(doGet("/api/system/login-logs?username=" + username + "&pageNo=1&pageSize=10", admin));
        assertThat(logins.at("/list/0/result").asText()).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void logRangeLimit_LOG_T05() throws Exception {
        assertError(doGet("/api/system/login-logs?timeFrom=2026-01-01 00:00:00&timeTo=2026-06-01 00:00:00&pageNo=1&pageSize=10", admin),
                "查询时间范围不能超过 3 个月");
    }

    @Test
    void docLogApi() throws Exception {
        JsonNode list = ok(doGet("/api/system/doc-logs?bizType=TEST&bizId=1", admin));
        assertThat(list.isArray()).isTrue();
    }

    @Test
    void userImport_USR_T08() throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", "sys.user.init-password", "value", "Init2026abc"))));
        try {
            String deptCode = "IMP" + uniq();
            createOrg(HQ, "DEPT", deptCode, "导入部门" + uniq());
            String roleId = createRole("SELF", List.of());
            String roleCode = ok(doGet("/api/system/roles/" + roleId, admin)).at("/code").asText();
            String u1 = "imp" + uniq();
            String u2 = "imp" + uniq();
            byte[] bad = xlsx(List.of(List.of(u1, "张三", deptCode, roleCode, ""), List.of(u2, "李四", "NOPE", roleCode, u1)));
            JsonNode check = upload("/api/system/users/import/check", bad);
            assertThat(check.at("/data/total").asInt()).isEqualTo(2);
            assertThat(check.at("/data/errorCount").asInt()).isEqualTo(1);
            assertThat(check.at("/data/rows/1/errors/0").asText()).isEqualTo("部门编码 NOPE 不存在");
            assertThat(upload("/api/system/users/import", bad).at("/code").asInt()).isNotEqualTo(0);

            byte[] good = xlsx(List.of(List.of(u1, "张三", deptCode, roleCode, ""), List.of(u2, "李四", deptCode, roleCode, u1)));
            JsonNode result = upload("/api/system/users/import", good);
            assertThat(result.at("/data/success").asInt()).isEqualTo(2);
            assertThat(ok(loginRaw(u2, "Init2026abc")).at("/mustChangePassword").asBoolean()).isTrue();
        } finally {
            ok(doPost("/api/system/params/sys.user.init-password/reset", admin, null));
        }
    }

    @Test
    void exportAndTemplateAreXlsx() throws Exception {
        for (String url : List.of("/api/system/users/export", "/api/system/users/import-template", "/api/system/exchange-rates/import-template")) {
            var resp = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).header("Authorization", admin))
                    .andReturn().getResponse();
            assertThat(resp.getContentType()).startsWith("application/vnd.openxmlformats");
            assertThat(resp.getHeader("Content-Disposition")).contains("filename*=UTF-8''");
            assertThat(resp.getContentAsByteArray().length).isGreaterThan(100);
        }
    }

    private JsonNode upload(String url, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        String body = mockMvc.perform(multipart(url).file(file).header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body);
    }

    /** 按模板格式生成用户导入文件：列名行、说明行、数据行 */
    private static byte[] xlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("用户");
            String[] head = {"用户名*", "姓名*", "主部门编码*", "角色编码*", "直属上级用户名"};
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            s.createRow(1).createCell(0).setCellValue("说明");
            for (int r = 0; r < rows.size(); r++) {
                Row row = s.createRow(r + 2);
                for (int c = 0; c < rows.get(r).size(); c++) row.createCell(c).setCellValue(rows.get(r).get(c));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
