package com.erp.it.engineering;

import com.erp.it.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 物料全流程：新建（自动编码）→ 查询 → 修改 → 启用 → 限制修改 → 停用 → 删除限制。 */
class MaterialIntegrationTest extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = loginAsAdmin();
    }

    @Test
    void materialLifecycle() throws Exception {
        // 新建：编码为空时自动生成
        String id = createMaterial(null, "FPC 排线", "RAW");
        JsonNode detail = getMaterial(id);
        assertThat(detail.at("/data/code").asText()).matches("M\\d{6}");
        assertThat(detail.at("/data/status").asText()).isEqualTo("DRAFT");
        int version = detail.at("/data/version").asInt();

        // 草稿可以修改类型和单位
        Map<String, Object> update = body(null, "FPC 排线 20P", "RAW");
        update.put("baseUom", "PCS");
        update.put("version", version);
        mockMvc.perform(put("/api/engineering/materials/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(jsonPath("$.code").value(0));

        // 旧版本号再次提交 → 并发冲突
        mockMvc.perform(put("/api/engineering/materials/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(jsonPath("$.code").value(1_000_000_001));

        // 启用后不能改基本单位
        mockMvc.perform(post("/api/engineering/materials/" + id + "/enable").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0));
        Map<String, Object> changeUom = body(null, "FPC 排线 20P", "RAW");
        changeUom.put("baseUom", "KG");
        mockMvc.perform(put("/api/engineering/materials/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(changeUom)))
                .andExpect(jsonPath("$.code").value(1_005_001_003));

        // 已启用不能删除
        mockMvc.perform(delete("/api/engineering/materials/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.code").value(1_005_001_004));

        // 停用后再次停用 → 非法状态流转
        mockMvc.perform(post("/api/engineering/materials/" + id + "/disable").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/engineering/materials/" + id + "/disable").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(1_000_000_002))
                .andExpect(jsonPath("$.msg").value("当前状态【停用】不允许执行【停用】操作"));
    }

    @Test
    void duplicateCodeRejected() throws Exception {
        createMaterial("DUP-001", "物料A", "PACKAGING");
        mockMvc.perform(post("/api/engineering/materials").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body("DUP-001", "物料B", "PACKAGING"))))
                .andExpect(jsonPath("$.code").value(1_005_001_001));
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mockMvc.perform(post("/api/engineering/materials").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void pageQueryAndLongIdsAsString() throws Exception {
        createMaterial(null, "电阻 10K 0603", "RAW");
        mockMvc.perform(get("/api/engineering/materials").header("Authorization", token)
                        .param("name", "电阻").param("pageSize", "10"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").isString())
                .andExpect(jsonPath("$.data.list[0].createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.data.list[0].name").value("电阻 10K 0603"));
    }

    private String createMaterial(String code, String name, String type) throws Exception {
        String resp = mockMvc.perform(post("/api/engineering/materials").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body(code, name, type))))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return read(resp).at("/data").asText();
    }

    private JsonNode getMaterial(String id) throws Exception {
        return read(mockMvc.perform(get("/api/engineering/materials/" + id).header("Authorization", token))
                .andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> body(String code, String name, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("materialType", type);
        m.put("baseUom", "PCS");
        return m;
    }
}
