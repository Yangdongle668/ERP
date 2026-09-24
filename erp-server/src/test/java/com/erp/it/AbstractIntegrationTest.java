package com.erp.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 集成测试基类：H2（MySQL 模式）+ 全部模块迁移脚本 + 完整 Spring 上下文。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String loginAsAdmin() throws Exception {
        return login("admin", "admin123");
    }

    /** 登录并返回 "Bearer xxx"；登录失败时断言失败 */
    protected String login(String username, String password) throws Exception {
        JsonNode node = loginRaw(username, password);
        assertThat(node.at("/code").asInt()).as("登录失败: %s", node).isEqualTo(0);
        return "Bearer " + node.at("/data/accessToken").asText();
    }

    protected JsonNode loginRaw(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/system/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    /** 发送 JSON 请求并返回响应体（不论 HTTP 状态） */
    protected JsonNode call(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(method, url);
        if (token != null) req.header("Authorization", token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json(body));
        String resp = mockMvc.perform(req).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return resp.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(resp);
    }

    protected JsonNode doGet(String url, String token) throws Exception {
        return call(HttpMethod.GET, url, token, null);
    }

    protected JsonNode doPost(String url, String token, Object body) throws Exception {
        return call(HttpMethod.POST, url, token, body);
    }

    protected JsonNode doPut(String url, String token, Object body) throws Exception {
        return call(HttpMethod.PUT, url, token, body);
    }

    protected JsonNode doDelete(String url, String token) throws Exception {
        return call(HttpMethod.DELETE, url, token, null);
    }

    /** 断言成功并返回 data */
    protected JsonNode ok(JsonNode resp) {
        assertThat(resp.at("/code").asInt()).as("请求失败: %s", resp).isEqualTo(0);
        return resp.at("/data");
    }

    /** 断言业务错误提示 */
    protected void assertError(JsonNode resp, String message) {
        assertThat(resp.at("/code").asInt()).as("应失败: %s", resp).isNotEqualTo(0);
        assertThat(resp.at("/msg").asText()).isEqualTo(message);
    }

    /** 测试间数据隔离用的唯一后缀 */
    protected static String uniq() {
        return String.valueOf(System.nanoTime() % 1_000_000) + SEQ.incrementAndGet();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
