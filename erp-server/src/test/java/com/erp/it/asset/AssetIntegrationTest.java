package com.erp.it.asset;

import com.erp.it.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 固定资产（需求 15-固定资产，《编码规则管理制度》5.4） */
class AssetIntegrationTest extends AbstractIntegrationTest {

    private String admin;

    @BeforeEach
    void login() throws Exception {
        admin = loginAsAdmin();
    }

    private Map<String, Object> asset(String company, String cls, String abbr, String date) {
        Map<String, Object> m = new HashMap<>();
        m.put("companyNo", company);
        m.put("assetClass", cls);
        m.put("name", "冲片机");
        m.put("nameAbbr", abbr);
        m.put("purchaseDate", date);
        m.put("originalValue", "125000");
        m.put("usefulLifeMonths", 120);
        return m;
    }

    /** T01 编码 LD1-PD-CPJ-264-001；同前缀连续；10 月为 A；缩写不足补 X；东莞蓝电为 LD0 */
    @Test
    void codes() throws Exception {
        String abbr = "Q" + (char) ('A' + (int) (System.nanoTime() % 26)) + "J";
        String a = ok(doPost("/api/asset/assets", admin, asset("11", "PD", abbr, "2026-04-15"))).asText();
        String code = ok(doGet("/api/asset/assets/" + a, admin)).at("/code").asText();
        assertThat(code).matches("LD1-PD-" + abbr + "-264-\\d{3}");
        String b = ok(doPost("/api/asset/assets", admin, asset("11", "PD", abbr, "2026-04-20"))).asText();
        String code2 = ok(doGet("/api/asset/assets/" + b, admin)).at("/code").asText();
        assertThat(Integer.parseInt(code2.substring(code2.length() - 3))).isEqualTo(Integer.parseInt(code.substring(code.length() - 3)) + 1);
        String c = ok(doPost("/api/asset/assets", admin, asset("10", "QA", "ab", "2026-10-01"))).asText();
        assertThat(ok(doGet("/api/asset/assets/" + c, admin)).at("/code").asText()).matches("LD0-QA-ABX-26A-\\d{3}");
        JsonNode preview = ok(doGet("/api/asset/assets/code-preview?companyNo=11&assetClass=EN&nameAbbr=k&purchaseDate=2026-12-01", admin));
        assertThat(preview.at("/prefix").asText()).isEqualTo("LD1-EN-KXX-26C-");

        assertThat(doPost("/api/asset/assets", admin, asset("99", "PD", "CPJ", "2026-04-15")).at("/code").asInt()).isNotZero();
        assertError(doPost("/api/asset/assets", admin, asset("11", "CU", "MJX", "2026-04-15")), "客户资产请填写所属客户");
    }

    /** T02 编码字段锁定；状态流转；报废后不能修改、不能再操作 */
    @Test
    void lifecycle() throws Exception {
        String id = ok(doPost("/api/asset/assets", admin, asset("11", "EN", "KYJ", "2026-03-02"))).asText();
        JsonNode d = ok(doGet("/api/asset/assets/" + id, admin));
        Map<String, Object> upd = asset("11", "PD", "KYJ", "2026-03-02");
        upd.put("version", d.at("/version").asInt());
        assertError(doPut("/api/asset/assets/" + id, admin, upd), "资产编码已生成，所属公司、分类、名称缩写、购置日期不能修改");
        Map<String, Object> ok = asset("11", "EN", "KYJ", "2026-03-20");
        ok.put("name", "空压机");
        ok.put("location", "动力房");
        ok.put("version", d.at("/version").asInt());
        ok(doPut("/api/asset/assets/" + id, admin, ok));
        assertThat(ok(doGet("/api/asset/assets/" + id, admin)).at("/location").asText()).isEqualTo("动力房");

        ok(doPost("/api/asset/assets/" + id + "/status", admin, Map.of("op", "IDLE")));
        ok(doPost("/api/asset/assets/" + id + "/status", admin, Map.of("op", "REPAIR")));
        String code = d.at("/code").asText();
        assertError(doPost("/api/asset/assets/" + id + "/status", admin, Map.of("op", "IDLE")), "资产「" + code + "」当前状态为维修中，不能闲置");
        ok(doPost("/api/asset/assets/" + id + "/status", admin, Map.of("op", "REPAIR_END")));
        ok(doPost("/api/asset/assets/" + id + "/scrap", admin, Map.of("scrappedDate", "2026-09-30", "reason", "老化")));
        JsonNode s = ok(doGet("/api/asset/assets/" + id, admin));
        assertThat(s.at("/assetStatus").asText()).isEqualTo("SCRAPPED");
        assertError(doPost("/api/asset/assets/" + id + "/status", admin, Map.of("op", "USE")), "资产「" + code + "」当前状态为已报废，不能启用");
        ok.put("version", s.at("/version").asInt());
        assertError(doPut("/api/asset/assets/" + id, admin, ok), "已报废的资产不能修改");
    }
}
