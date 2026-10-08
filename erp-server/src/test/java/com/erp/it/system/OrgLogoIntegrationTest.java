package com.erp.it.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 公司 Logo（01-01 组织架构）：PNG / SVG 上传，SVG 过滤脚本；系统左上角、登录页、打印抬头使用同一个 Logo */
class OrgLogoIntegrationTest extends SystemTestSupport {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120\" height=\"40\"><rect width=\"120\" height=\"40\"/></svg>";

    private JsonNode upload(String orgId, String name, String type, byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart("/api/system/orgs/" + orgId + "/logo").file(new MockMultipartFile("file", name, type, bytes))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    @Test
    void uploadLogoUsedEverywhere() throws Exception {
        // 部门不能设置 Logo；不支持的格式、伪装的 PNG、带脚本的 SVG 都拒绝
        String dept = createDept(HQ, "Logo 测试部门" + uniq());
        assertError(upload(dept, "a.png", "image/png", PNG), "只有公司可以设置 Logo");
        assertError(upload(HQ, "a.gif", "image/gif", PNG), "Logo 只支持 PNG、SVG、JPG 图片");
        assertError(upload(HQ, "a.png", "image/png", "not png".getBytes()), "图片内容与扩展名不符");
        assertError(upload(HQ, "a.svg", "image/svg+xml", "<svg onload=\"alert(1)\"></svg>".getBytes()),
                "SVG 文件无效或包含脚本、外部链接，请导出为纯图形 SVG 或改用 PNG");
        assertError(upload(HQ, "a.svg", "image/svg+xml", "<svg><script>alert(1)</script></svg>".getBytes()),
                "SVG 文件无效或包含脚本、外部链接，请导出为纯图形 SVG 或改用 PNG");

        String pngId = ok(upload(HQ, "logo.png", "image/png", PNG)).asText();
        assertThat(ok(doGet("/api/system/orgs/" + HQ, admin)).at("/logoFileId").asText()).isEqualTo(pngId);
        String svgId = ok(upload(HQ, "logo.svg", "image/svg+xml", SVG.getBytes(StandardCharsets.UTF_8))).asText();

        // 登录页 / 左上角：公开参数带 logoVersion，Logo 图片不需要登录
        JsonNode pub = ok(doGet("/api/system/params/public", null));
        assertThat(pub.at("/logoVersion").asText()).isEqualTo(svgId);
        var resp = mockMvc.perform(get("/api/system/params/public/logo")).andReturn().getResponse();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentType()).startsWith("image/svg+xml");
        assertThat(resp.getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(resp.getContentAsString(StandardCharsets.UTF_8)).contains("<svg");

        // 打印抬头用同一个 Logo
        JsonNode header = ok(doGet("/api/system/print-header", admin));
        assertThat(header.at("/logo").asText()).startsWith("data:image/svg+xml;base64,");

        ok(doDelete("/api/system/orgs/" + HQ + "/logo", admin));
        JsonNode org = ok(doGet("/api/system/orgs/" + HQ, admin));
        assertThat(org.at("/logoFileId").isMissingNode() || org.at("/logoFileId").isNull()).isTrue();
        assertThat(mockMvc.perform(get("/api/system/params/public/logo")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }
}
