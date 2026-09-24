package com.erp.it.system;

import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.service.file.FileService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 附件（01-12 第 1 节） */
class FileIntegrationTest extends SystemTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private FileApi fileApi;
    @Autowired
    private FileService fileService;
    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode upload(String token, String name, byte[] content, Map<String, String> fields) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/system/files").file(new MockMultipartFile("file", name, "application/octet-stream", content));
        fields.forEach(req::param);
        req.header("Authorization", token);
        return read(mockMvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void uploadDownloadAndChineseFileName_R06() throws Exception {
        JsonNode f = ok(upload(admin, "报价单 附件.pdf", PDF, Map.of("bizType", "IT_DOC", "bizId", "1001")));
        assertThat(f.at("/fileName").asText()).isEqualTo("报价单 附件.pdf");
        assertThat(f.at("/fileSize").asLong()).isEqualTo(PDF.length);
        assertThat(f.at("/createdByName").asText()).isNotBlank();

        JsonNode list = ok(doGet("/api/system/files?bizType=IT_DOC&bizId=1001", admin));
        assertThat(list).hasSize(1);

        MockHttpServletResponse resp = mockMvc.perform(get("/api/system/files/" + f.at("/id").asText() + "/download")
                .header("Authorization", admin)).andReturn().getResponse();
        assertThat(resp.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(resp.getHeader("Content-Disposition")).isEqualTo("attachment; filename*=UTF-8''%E6%8A%A5%E4%BB%B7%E5%8D%95%20%E9%99%84%E4%BB%B6.pdf");

        MockHttpServletResponse preview = mockMvc.perform(get("/api/system/files/" + f.at("/id").asText() + "/preview")
                .header("Authorization", admin)).andReturn().getResponse();
        assertThat(preview.getContentType()).isEqualTo("application/pdf");
        assertThat(preview.getHeader("Content-Disposition")).startsWith("inline");
    }

    @Test
    void sizeLimit_T01() throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", "sys.file.max-size-mb", "value", "1"))));
        try {
            byte[] big = Arrays.copyOf(PDF, 1024 * 1024 + 10);
            assertError(upload(admin, "big.pdf", big, Map.of()), "文件大小不能超过 1MB");
        } finally {
            ok(doPut("/api/system/params", admin, List.of(Map.of("key", "sys.file.max-size-mb", "value", "50"))));
        }
    }

    @Test
    void fakeExtensionRejected_T02() throws Exception {
        byte[] exe = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        assertError(upload(admin, "invoice.pdf", exe, Map.of()), "不支持的文件类型：pdf");
        assertError(upload(admin, "tool.exe", exe, Map.of()), "不支持的文件类型：exe");
        assertError(upload(admin, "notes.txt", new byte[]{'a', 0, 'b'}, Map.of()), "不支持的文件类型：txt");
        ok(upload(admin, "notes.txt", "普通文本".getBytes(StandardCharsets.UTF_8), Map.of()));
    }

    @Test
    void unboundFileBindAndCleanup_R03_T03() throws Exception {
        String bound = ok(upload(admin, "a.pdf", PDF, Map.of())).at("/id").asText();
        String stale = ok(upload(admin, "b.pdf", PDF, Map.of())).at("/id").asText();
        // 未绑定文件不出现在单据附件中
        assertThat(ok(doGet("/api/system/files?bizType=IT_ORDER&bizId=2001", admin))).isEmpty();

        fileApi.bind(List.of(Long.valueOf(bound)), "IT_ORDER", 2001L);
        assertThat(fileApi.list("IT_ORDER", 2001L)).extracting(i -> i.fileName()).containsExactly("a.pdf");

        // 超过 24 小时未绑定 → 清理
        jdbc.update("UPDATE sys_file SET created_at = ? WHERE id = ?", java.time.LocalDateTime.now().minusHours(25), Long.valueOf(stale));
        fileService.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_file WHERE id = ?", Long.class, Long.valueOf(stale))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_file WHERE id = ?", Long.class, Long.valueOf(bound))).isOne();
    }

    @Test
    void accessControl_R04_T04() throws Exception {
        String dept = createDept(HQ, "附件部门" + uniq());
        String role = createRole("SELF", List.of());
        String u1 = "fa" + uniq();
        String u2 = "fb" + uniq();
        createUser(u1, dept, List.of(role), false);
        createUser(u2, dept, List.of(role), false);
        String t1 = login(u1, PASSWORD);
        String t2 = login(u2, PASSWORD);

        // 没有 FileAccessChecker 的业务类型：只有上传人和管理员能看、能删
        String id = ok(upload(t1, "c.pdf", PDF, Map.of("bizType", "IT_DOC", "bizId", "3001"))).at("/id").asText();
        assertThat(ok(doGet("/api/system/files?bizType=IT_DOC&bizId=3001", t2))).isEmpty();
        assertThat(ok(doGet("/api/system/files?bizType=IT_DOC&bizId=3001", admin))).hasSize(1);
        assertError(read(mockMvc.perform(get("/api/system/files/" + id + "/download").header("Authorization", t2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)), "没有权限访问该附件");
        assertError(doDelete("/api/system/files/" + id, t2), "没有权限访问该附件");
        ok(doDelete("/api/system/files/" + id, t1));
        assertThat(ok(doGet("/api/system/files?bizType=IT_DOC&bizId=3001", t1))).isEmpty();

        // 有 FileAccessChecker 的业务类型按 checker 判断
        assertError(upload(admin, "d.pdf", PDF, Map.of("bizType", ItFileAccessChecker.LOCKED, "bizId", "1")), "没有权限访问该附件");
        assertError(doGet("/api/system/files?bizType=" + ItFileAccessChecker.LOCKED + "&bizId=1", admin), "没有权限访问该附件");
    }

    @Test
    void generatedFileAndDeleteByBiz() throws Exception {
        Long id = fileApi.saveGenerated("IT_TASK", 9L, "导出结果.xlsx", null, new byte[]{1, 2, 3});
        assertThat(fileApi.list("IT_TASK", 9L)).extracting(i -> i.id()).containsExactly(id);
        fileApi.deleteByBiz("IT_TASK", 9L);
        assertThat(fileApi.list("IT_TASK", 9L)).isEmpty();
    }
}
