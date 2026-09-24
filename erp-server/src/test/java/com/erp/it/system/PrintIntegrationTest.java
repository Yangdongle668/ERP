package com.erp.it.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 打印模板（01-09） */
class PrintIntegrationTest extends SystemTestSupport {

    private JsonNode templates() throws Exception {
        return ok(doGet("/api/system/print-templates?bizType=" + ItPrintConfig.BIZ + "&pageNo=1&pageSize=50", admin)).at("/list");
    }

    private JsonNode builtin() throws Exception {
        for (JsonNode t : templates()) if (t.at("/isBuiltin").asBoolean()) return t;
        throw new AssertionError("内置模板未导入");
    }

    private Map<String, Object> body(String name, String content) {
        Map<String, Object> b = new HashMap<>();
        b.put("bizType", ItPrintConfig.BIZ);
        b.put("name", name);
        b.put("language", "zh-CN");
        b.put("paper", "A4_P");
        b.put("margin", "10mm 10mm 10mm 10mm");
        b.put("content", content);
        return b;
    }

    @Test
    void builtinImportedAndBizVariables_R05() throws Exception {
        JsonNode b = builtin();
        assertThat(b.at("/name").asText()).isEqualTo("测试单（标准）");
        assertThat(b.at("/language").asText()).isEqualTo("zh-CN");
        JsonNode detail = ok(doGet("/api/system/print-templates/" + b.at("/id").asText(), admin));
        assertThat(detail.at("/content").asText()).contains("{{#each lines}}");

        JsonNode biz = ok(doGet("/api/system/print-biz/" + ItPrintConfig.BIZ, admin));
        assertThat(biz.at("/dataApi").asText()).isEqualTo("/it/print/{id}/print-data");
        assertThat(biz.at("/variables")).hasSize(4);
        assertThat(biz.at("/sampleData/docNo").asText()).isEqualTo("IT-0001");
    }

    @Test
    void copyBuiltinSetDefault_T03() throws Exception {
        JsonNode b = builtin();
        String id = b.at("/id").asText();
        assertError(doPut("/api/system/print-templates/" + id, admin, body("改内置", "<p>x</p>")), "内置模板不能修改或删除，请复制后修改");
        assertError(doDelete("/api/system/print-templates/" + id, admin), "内置模板不能修改或删除，请复制后修改");

        String copy = ok(doPost("/api/system/print-templates/" + id + "/copy", admin, null)).asText();
        JsonNode c = ok(doGet("/api/system/print-templates/" + copy, admin));
        assertThat(c.at("/name").asText()).isEqualTo(b.at("/name").asText() + "-副本");
        assertThat(c.at("/isBuiltin").asBoolean()).isFalse();
        assertThat(c.at("/isDefault").asBoolean()).isFalse();

        Map<String, Object> save = body("测试单（大字号）", c.at("/content").asText().replace("font-size: 18px", "font-size: 24px"));
        save.put("version", c.at("/version").asInt());
        ok(doPut("/api/system/print-templates/" + copy, admin, save));
        ok(doPost("/api/system/print-templates/" + copy + "/set-default", admin, null));

        JsonNode available = ok(doGet("/api/system/print-templates/available?bizType=" + ItPrintConfig.BIZ, admin));
        assertThat(available.at("/bizName").asText()).isEqualTo("测试单");
        assertThat(available.at("/templates/0/id").asText()).isEqualTo(copy);
        assertThat(available.at("/templates/0/isDefault").asBoolean()).isTrue();
        assertThat(ok(doGet("/api/system/print-templates/" + id, admin)).at("/isDefault").asBoolean()).isFalse();
        assertThat(ok(doGet("/api/system/print-templates/" + copy + "/for-print", admin)).at("/content").asText()).contains("font-size: 24px");

        // 默认模板不能停用、删除；恢复内置为默认后删除副本
        assertError(doPost("/api/system/print-templates/" + copy + "/disable", admin, null), "默认模板不能停用或删除");
        ok(doPost("/api/system/print-templates/" + id + "/set-default", admin, null));
        ok(doDelete("/api/system/print-templates/" + copy, admin));
    }

    @Test
    void syntaxErrorWithLine_T04() throws Exception {
        JsonNode r = doPost("/api/system/print-templates", admin, body("坏模板", "<table>\n<tr>\n{{#each lines}}<td>{{name}}</td>\n</table>"));
        assertThat(r.at("/code").asInt()).isNotZero();
        assertThat(r.at("/msg").asText()).startsWith("模板渲染失败：第 ");
    }

    @Test
    void scriptRejected_T05() throws Exception {
        assertError(doPost("/api/system/print-templates", admin, body("脚本", "<p>x</p><script>alert(1)</script>")), "模板中不允许包含脚本");
        assertError(doPost("/api/system/print-templates", admin, body("事件", "<img src=x onerror=alert(1)>")), "模板中不允许包含脚本");
        assertError(doPost("/api/system/print-templates", admin, body("链接", "<a href=\"javascript:alert(1)\">x</a>")), "模板中不允许包含脚本");
        assertError(doPost("/api/system/print-templates/validate", admin, Map.of("content", "<SCRIPT >x</SCRIPT>")), "模板中不允许包含脚本");
    }

    @Test
    void disabledTemplateNotAvailableAndPrintLog() throws Exception {
        String id = ok(doPost("/api/system/print-templates", admin, body("临时模板", "<p>{{docNo}}</p>"))).asText();
        ok(doPost("/api/system/print-templates/" + id + "/disable", admin, null));
        JsonNode available = ok(doGet("/api/system/print-templates/available?bizType=" + ItPrintConfig.BIZ, admin));
        available.at("/templates").forEach(t -> assertThat(t.at("/id").asText()).isNotEqualTo(id));
        assertError(doGet("/api/system/print-templates/" + id + "/for-print", admin), "打印模板已停用");
        ok(doDelete("/api/system/print-templates/" + id, admin));

        ok(doPost("/api/system/print-logs", admin, Map.of("bizType", ItPrintConfig.BIZ, "bizIds", List.of(11, 12), "templateId", builtin().at("/id").asText())));
        ok(doPost("/api/system/print-logs", admin, Map.of("bizType", ItPrintConfig.BIZ, "bizIds", List.of(11))));
        JsonNode counts = ok(doGet("/api/system/print-logs/counts?bizType=" + ItPrintConfig.BIZ + "&bizIds=11,12,13", admin));
        assertThat(counts.at("/0/count").asLong()).isEqualTo(2);
        assertThat(counts.at("/1/count").asLong()).isEqualTo(1);
        assertThat(counts.at("/2/count").asLong()).isZero();
    }
}
