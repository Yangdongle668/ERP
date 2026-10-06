package com.erp.it.backup;

import com.erp.framework.maintenance.MaintenanceMode;
import com.erp.it.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 系统备份（需求 14）：超级管理员才能访问；全量备份 → 修改数据 → 一键恢复（含附件）；版本不一致拒绝；维护模式拒绝请求 */
class BackupIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MaintenanceMode maintenance;

    private String admin;

    @BeforeEach
    void login() throws Exception {
        admin = loginAsAdmin();
    }

    private JsonNode waitRecord(String id) throws Exception {
        for (int i = 0; i < 300; i++) {
            JsonNode r = ok(doGet("/api/backup/records/" + id, admin));
            if (!"RUNNING".equals(r.at("/status").asText())) return r;
            Thread.sleep(100);
        }
        throw new AssertionError("备份超时");
    }

    private JsonNode waitRestore() throws Exception {
        for (int i = 0; i < 600; i++) {
            JsonNode r = doGet("/api/backup/restore/status", admin);
            if (r.at("/code").asInt() == 0 && !"RUNNING".equals(r.at("/data/status").asText())) return r.at("/data");
            Thread.sleep(100);
        }
        throw new AssertionError("恢复超时");
    }

    private String paramValue(String key) {
        return jdbc.queryForObject("SELECT param_value FROM sys_param WHERE param_key = ?", String.class, key);
    }

    @Test
    void onlySuperAdmin() throws Exception {
        String code = "R" + uniq();
        String role = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", "ALL", "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + role + "/permissions", admin, Map.of("permissions", List.of("system:user:query", "system:role:query"))));
        String username = "bk" + uniq();
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("realName", "备份测试");
        u.put("deptId", "100");
        u.put("roleIds", List.of(role));
        u.put("password", "Passw0rd!2026");
        u.put("mustChangePassword", false);
        ok(doPost("/api/system/users", admin, u));
        String token = login(username, "Passw0rd!2026");
        assertThat(doGet("/api/backup/info", token).at("/code").asInt()).isEqualTo(403);
        assertThat(doPost("/api/backup/records", token, Map.of()).at("/code").asInt()).isEqualTo(403);
        assertThat(ok(doGet("/api/backup/info", admin)).at("/confirmText").asText()).isEqualTo("确认恢复");
    }

    @Test
    void backupAndRestore() throws Exception {
        // 备份前的数据：参数值 9、一个附件
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", "bak.auto.keep", "value", "9"))));
        String body = mockMvc.perform(multipart("/api/system/files").file(new MockMultipartFile("file", "backup.pdf", "application/pdf", PDF))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String fileId = ok(read(body)).at("/id").asText();

        String id = ok(doPost("/api/backup/records", admin, Map.of("includeFiles", true, "remark", "集成测试"))).asText();
        JsonNode rec = waitRecord(id);
        assertThat(rec.at("/status").asText()).as(rec.at("/errorMsg").asText()).isEqualTo("SUCCESS");
        assertThat(rec.at("/tableCount").asInt()).isGreaterThan(100);
        assertThat(rec.at("/rowCount").asLong()).isGreaterThan(0);
        assertThat(rec.at("/attachmentCount").asInt()).isGreaterThan(0);
        assertThat(rec.at("/restorable").asBoolean()).isTrue();
        assertThat(rec.at("/schemaVersions/system").asText()).isNotBlank();

        // 下载得到 zip
        MockHttpServletResponse dl = mockMvc.perform(get("/api/backup/records/" + id + "/download").header("Authorization", admin)).andReturn().getResponse();
        assertThat(dl.getStatus()).isEqualTo(200);
        assertThat(dl.getContentAsByteArray()[0]).isEqualTo((byte) 'P');

        // 备份后修改：参数改为 3、新建角色、删除附件
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", "bak.auto.keep", "value", "3"))));
        String code = "R" + uniq();
        ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "备份后角色" + code, "dataScope", "SELF", "sort", 10)));
        ok(doDelete("/api/system/files/" + fileId, admin));
        assertThat(paramValue("bak.auto.keep")).isEqualTo("3");

        // 确认文字必填；检查通过
        assertError(doPost("/api/backup/records/" + id + "/restore", admin, Map.of("confirm", "恢复")), "请输入“确认恢复”");
        JsonNode check = ok(doGet("/api/backup/records/" + id + "/check", admin));
        assertThat(check.at("/ok").asBoolean()).as(check.at("/problems").toString()).isTrue();

        // 一键恢复
        ok(doPost("/api/backup/records/" + id + "/restore", admin, Map.of("confirm", "确认恢复")));
        JsonNode st = waitRestore();
        assertThat(st.at("/status").asText()).as(st.at("/errorMsg").asText()).isEqualTo("SUCCESS");
        assertThat(st.at("/preBackupId").isNull()).isFalse();
        assertThat(st.at("/rowCount").asLong()).isGreaterThan(0);
        assertThat(maintenance.active()).isFalse();

        admin = loginAsAdmin();
        assertThat(paramValue("bak.auto.keep")).isEqualTo("9");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE code = ?", Long.class, code)).isZero();
        MockHttpServletResponse f = mockMvc.perform(get("/api/system/files/" + fileId + "/download").header("Authorization", admin)).andReturn().getResponse();
        assertThat(f.getContentAsByteArray()).isEqualTo(PDF);
        // 恢复前自动备份已登记，记录列表包含两份
        JsonNode list = ok(doGet("/api/backup/records?pageNo=1&pageSize=50", admin)).at("/list");
        assertThat(list.findValuesAsText("backupType")).contains("PRE_RESTORE", "MANUAL");
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", "bak.auto.keep", "value", "7"))));
    }

    @Test
    void schemaMismatchAndMaintenance() throws Exception {
        String id = ok(doPost("/api/backup/records", admin, Map.of("includeFiles", false))).asText();
        assertThat(waitRecord(id).at("/status").asText()).isEqualTo("SUCCESS");
        // 备份版本与当前不同 → 不能恢复
        jdbc.update("UPDATE bak_record SET schema_versions = ? WHERE id = ?", "{\"system\":\"1\"}", Long.valueOf(id));
        JsonNode rec = ok(doGet("/api/backup/records/" + id, admin));
        assertThat(rec.at("/restorable").asBoolean()).isFalse();
        assertThat(rec.at("/mismatch").asText()).contains("system");

        // 维护模式：其他接口返回 503，恢复进度可查询
        assertThat(maintenance.enter("测试")).isTrue();
        try {
            JsonNode r = doGet("/api/system/auth/me", admin);
            assertThat(r.at("/code").asInt()).isEqualTo(503);
            assertThat(r.at("/msg").asText()).contains("系统维护中");
            assertThat(doGet("/api/backup/restore/status", admin).at("/code").asInt()).isZero();
        } finally {
            maintenance.exit();
        }
        ok(doDelete("/api/backup/records/" + id, admin));
    }
}
