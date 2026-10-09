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

    /** 针式多联模板（01-09 第 4 节）：内置针式模板导入后为默认；固定行数、联次、打印方式随模板保存；打印抬头 */
    @Test
    void dotMatrixTemplates() throws Exception {
        for (String biz : List.of("INV_STOCK_IN", "INV_STOCK_OUT", "INV_TRANSFER", "MFG_ISSUE", "MFG_RETURN", "MFG_FINISH", "PUR_RECEIPT", "PUR_RETURN",
                "PUR_ORDER", "SHP_SHIPMENT", "SAL_RETURN", "SAL_ORDER")) {
            JsonNode av = ok(doGet("/api/system/print-templates/available?bizType=" + biz, admin));
            JsonNode def = null;
            for (JsonNode t : av.at("/templates")) if (t.at("/isDefault").asBoolean() && "zh-CN".equals(t.at("/language").asText())) def = t;
            assertThat(def).as(biz + " 默认模板").isNotNull();
            assertThat(def.at("/paper").asText()).as(biz).isEqualTo("DOT_241_140");
            assertThat(def.at("/name").asText()).as(biz).endsWith("（针式二等分）");
        }
        JsonNode av = ok(doGet("/api/system/print-templates/available?bizType=MFG_ISSUE", admin));
        String id = av.at("/templates/0/id").asText();
        JsonNode fp = ok(doGet("/api/system/print-templates/" + id + "/for-print", admin));
        assertThat(fp.at("/rowsPerPage").asInt()).isEqualTo(16);
        assertThat(fp.at("/copiesNote").asText()).isEqualTo("①白 存根|②红 仓库|③黄 车间");
        assertThat(fp.at("/copyMode").asText()).isEqualTo("CARBON");
        assertThat(fp.at("/content").asText()).contains("{{#each page.lines}}", "{{company.name}}", "领料人");

        Map<String, Object> b = body("针式自定义", "<div>{{#each page.lines}}{{lineNo}}{{/each}}</div>");
        b.put("paper", "DOT_241_140");
        b.put("rowsPerPage", 8);
        b.put("copiesNote", "①白 存根|②红 客户");
        b.put("copyMode", "REPEAT");
        String custom = ok(doPost("/api/system/print-templates", admin, b)).asText();
        JsonNode d = ok(doGet("/api/system/print-templates/" + custom, admin));
        assertThat(d.at("/rowsPerPage").asInt()).isEqualTo(8);
        assertThat(d.at("/copiesNote").asText()).isEqualTo("①白 存根|②红 客户");
        assertThat(d.at("/copyMode").asText()).isEqualTo("REPEAT");
        b.put("rowsPerPage", 0);
        assertError(doPost("/api/system/print-templates", admin, b), "请求参数不正确：rowsPerPage 每页行数 1～60");

        JsonNode header = ok(doGet("/api/system/print-header", admin));
        assertThat(header.at("/name").asText()).isNotBlank();
    }

    /** 可视化版式（01-09 第 9 节）：内置针式模板带版式；版式随模板保存、复制；格式不正确拒绝 */
    @Test
    void printLayout() throws Exception {
        JsonNode av = ok(doGet("/api/system/print-templates/available?bizType=MFG_ISSUE", admin));
        String id = av.at("/templates/0/id").asText();
        JsonNode dot = ok(doGet("/api/system/print-templates/" + id, admin));
        JsonNode layout = objectMapper.readTree(dot.at("/layout").asText());
        assertThat(layout.at("/columns").size()).isGreaterThan(3);
        assertThat(layout.at("/info").size()).isGreaterThan(3);

        String copy = ok(doPost("/api/system/print-templates/" + id + "/copy", admin, null)).asText();
        JsonNode c = ok(doGet("/api/system/print-templates/" + copy, admin));
        assertThat(c.at("/layout").asText()).isEqualTo(dot.at("/layout").asText());

        Map<String, Object> save = body("领料单（精简）", "<div>{{docNo}}</div>");
        save.put("version", c.at("/version").asInt());
        save.put("layout", "{\"version\":1,\"info\":[],\"columns\":[],\"signs\":[]}");
        ok(doPut("/api/system/print-templates/" + copy, admin, save));
        JsonNode saved = ok(doGet("/api/system/print-templates/" + copy, admin));
        assertThat(objectMapper.readTree(saved.at("/layout").asText()).at("/version").asInt()).isEqualTo(1);

        save.put("version", saved.at("/version").asInt());
        save.put("layout", "[1,2]");
        assertError(doPut("/api/system/print-templates/" + copy, admin, save), "版式格式不正确");
        save.remove("layout");
        ok(doPut("/api/system/print-templates/" + copy, admin, save));
        assertThat(ok(doGet("/api/system/print-templates/" + copy, admin)).at("/layout").isTextual()).isFalse();
        ok(doDelete("/api/system/print-templates/" + copy, admin));
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
