package com.erp.it.purchase;

import com.erp.module.purchase.api.price.PurchasePriceApi;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 供应商（07-01）与采购价格（07-02）验收用例 */
class SupplierPriceIntegrationTest extends PurchaseTestSupport {

    @Autowired
    SupplierApi supplierApi;

    @Autowired
    PurchasePriceApi priceApi;

    /** PUR-SUP-T01、T02、R06、R07、搜索 */
    @Test
    void qualifyLifecycle() throws Exception {
        String m = elec("电阻");
        Map<String, Object> body = supplierBody("华强", List.of(m), "QUALIFIED");
        body.put("certs", List.of());
        String id = ok(doPost("/api/purchase/suppliers", admin, body)).asText();
        JsonNode d = ok(doGet("/api/purchase/suppliers/" + id, admin));
        assertThat(d.at("/status").asText()).isEqualTo("POTENTIAL");
        assertThat(d.at("/code").asText()).startsWith("V");

        // T01：没有营业执照
        assertError(doPost("/api/purchase/suppliers/" + id + "/qualify", admin, null), "提交准入需要：营业执照资质");
        // R01：名称唯一
        Map<String, Object> dup = supplierBody("华强", List.of(), "TRIAL");
        dup.put("name", d.at("/name").asText());
        assertError(doPost("/api/purchase/suppliers", admin, dup), "供应商「" + d.at("/name").asText() + "」已存在");

        body.put("certs", List.of(cert("LICENSE", LocalDate.now().plusYears(1))));
        body.put("version", d.at("/version").asInt());
        ok(doPut("/api/purchase/suppliers/" + id, admin, body));
        // T02：无审批流时直接合格，准入日期为今天
        assertThat(ok(doPost("/api/purchase/suppliers/" + id + "/qualify", admin, null)).asText()).isEqualTo("QUALIFIED");
        d = ok(doGet("/api/purchase/suppliers/" + id, admin));
        assertThat(d.at("/qualifiedAt").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(supplierApi.validateQualified(Long.valueOf(id)).code()).isEqualTo(d.at("/code").asText());

        // 选择器搜索
        JsonNode found = ok(doGet("/api/purchase/suppliers/search?statuses=QUALIFIED&keyword=" + d.at("/code").asText(), admin));
        assertThat(found.at("/0/id").asText()).isEqualTo(id);
        assertThat(found.at("/0/paymentTermId").asText()).isEqualTo(TERM);

        // R07：合格供应商不能删除；R06 暂停原因必填
        assertError(doDelete("/api/purchase/suppliers/" + id, admin), "只有潜在状态的供应商可以删除");
        assertError(doPost("/api/purchase/suppliers/" + id + "/suspend", admin, Map.of()), "请填写暂停原因");
        JsonNode s = ok(doPost("/api/purchase/suppliers/" + id + "/suspend", admin, Map.of("reason", "质量问题")));
        assertThat(s.at("/status").asText()).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> supplierApi.validateQualified(Long.valueOf(id))).hasMessage("供应商「" + d.at("/name").asText() + "」不是合格供应商");
        ok(doPost("/api/purchase/suppliers/" + id + "/resume", admin, null));
        ok(doPost("/api/purchase/suppliers/" + id + "/eliminate", admin, Map.of("reason", "停止合作")));
        assertError(doPost("/api/purchase/suppliers/" + id + "/resume", admin, null), "当前状态【ELIMINATED】不允许执行【恢复】操作");

        // 潜在、无业务数据可删除
        String p = ok(doPost("/api/purchase/suppliers", admin, supplierBody("待删", List.of(), "TRIAL"))).asText();
        ok(doDelete("/api/purchase/suppliers/" + p, admin));
    }

    /** PUR-SUP-T03：设置新默认时取消其他供应商的默认 */
    @Test
    void defaultSupplierIsUnique() throws Exception {
        String a = elec("电容");
        String v1 = qualifiedSupplier("V1", a);
        String v2 = qualifiedSupplier("V2", a);
        long l1 = ok(doGet("/api/purchase/suppliers/" + v1 + "/materials", admin)).at("/0/id").asLong();
        ok(doPut("/api/purchase/suppliers/" + v1 + "/materials/" + l1, admin, Map.of("materialId", a, "supplyStatus", "QUALIFIED", "isDefault", true)));
        assertThat(supplierApi.getDefaultSupplier(Long.valueOf(a)).orElseThrow().id()).isEqualTo(Long.valueOf(v1));
        long l2 = ok(doGet("/api/purchase/suppliers/" + v2 + "/materials", admin)).at("/0/id").asLong();
        ok(doPut("/api/purchase/suppliers/" + v2 + "/materials/" + l2, admin, Map.of("materialId", a, "supplyStatus", "QUALIFIED", "isDefault", true)));
        assertThat(supplierApi.getDefaultSupplier(Long.valueOf(a)).orElseThrow().id()).isEqualTo(Long.valueOf(v2));
        JsonNode list = ok(doGet("/api/purchase/supplier-materials?materialId=" + a, admin));
        assertThat(list).hasSize(2);
        for (JsonNode n : list) assertThat(n.at("/isDefault").asBoolean()).isEqualTo(n.at("/supplierId").asText().equals(v2));
    }

    /** PUR-SUP-T04：营业执照过期不能下标准订单；T05：试用物料只能下样品订单 */
    @Test
    void orderChecksSupplierQualification() throws Exception {
        String a = elec("晶振");
        String b = elec("二极管");
        Map<String, Object> body = supplierBody("资质", List.of(a), "QUALIFIED");
        body.put("materials", List.of(Map.of("materialId", a, "supplyStatus", "QUALIFIED"), Map.of("materialId", b, "supplyStatus", "TRIAL")));
        String v = ok(doPost("/api/purchase/suppliers", admin, body)).asText();
        ok(doPost("/api/purchase/suppliers/" + v + "/qualify", admin, null));
        price(v, List.of(List.of(a, "0", "1"), List.of(b, "0", "2")));

        // T05
        String trial = createOrder(v, "STANDARD", List.of(orderLine(b, "10", "2.26", null)));
        assertError(doPost("/api/purchase/orders/" + trial + "/submit", admin, null), "物料「" + code(b) + "」在该供应商处为试用状态，只能下样品订单");
        String sample = createOrder(v, "SAMPLE", List.of(orderLine(b, "10", "2.26", null)));
        assertThat(ok(doPost("/api/purchase/orders/" + sample + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");

        // T04：营业执照过期
        JsonNode d = ok(doGet("/api/purchase/suppliers/" + v, admin));
        Map<String, Object> upd = new HashMap<>(body);
        upd.put("certs", List.of(cert("LICENSE", LocalDate.now().minusDays(1))));
        upd.remove("materials");
        upd.put("version", d.at("/version").asInt());
        ok(doPut("/api/purchase/suppliers/" + v, admin, upd));
        String o = createOrder(v, "STANDARD", List.of(orderLine(a, "10", "1.13", null)));
        assertError(doPost("/api/purchase/orders/" + o + "/submit", admin, null), "供应商「" + d.at("/name").asText() + "」的资质「营业执照」已过期");
        JsonNode rows = ok(doGet("/api/purchase/suppliers?certExpiry=EXPIRED&keyword=" + d.at("/code").asText(), admin)).at("/list");
        assertThat(rows.at("/0/certExpired").asBoolean()).isTrue();
    }

    /** PUR-PRC-T01～T04：阶梯价、取价、未来生效、必须有 0 阶梯 */
    @Test
    void priceTiersAndEffectiveDates() throws Exception {
        String a = elec("排线");
        String v = qualifiedSupplier("价格", a);
        // T04
        String bad = ok(doPost("/api/purchase/price-adjusts", admin, Map.of("supplierId", v, "adjustReason", "报价",
                "lines", List.of(Map.of("materialId", a, "minQty", "1000", "newPrice", "0.95"))))).asText();
        assertError(doPost("/api/purchase/price-adjusts/" + bad + "/submit", admin, null), "物料「" + code(a) + "」必须有起始数量为 0 的阶梯");

        // T01：两档有效价格
        price(v, List.of(List.of(a, "0", "1.00"), List.of(a, "1000", "0.95")));
        JsonNode list = ok(doGet("/api/purchase/prices?materialId=" + a, admin)).at("/list");
        assertThat(list).hasSize(2);
        assertThat(list.at("/0/priceInclTax").decimalValue()).isEqualByComparingTo("1.13");
        // T02：数量 1500 带出 0.95
        assertThat(ok(doGet("/api/purchase/prices/effective?supplierId=" + v + "&materialId=" + a + "&qty=1500&currency=CNY", admin))
                .at("/price").decimalValue()).isEqualByComparingTo("0.95");
        assertThat(priceApi.getEffectivePrice(Long.valueOf(v), Long.valueOf(a), new BigDecimal("10"), LocalDate.now(), "CNY").orElseThrow().price())
                .isEqualByComparingTo("1.00");

        // T03：新调价 1.20，下月 1 日生效
        LocalDate next = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        String adj = ok(doPost("/api/purchase/price-adjusts", admin, Map.of("supplierId", v, "adjustReason", "涨价",
                "lines", List.of(Map.of("materialId", a, "minQty", "0", "newPrice", "1.20", "effectiveFrom", next.toString()))))).asText();
        JsonNode ad = ok(doGet("/api/purchase/price-adjusts/" + adj, admin));
        assertThat(ad.at("/lines/0/oldPrice").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(ad.at("/lines/0/overThreshold").asBoolean()).isTrue();
        JsonNode sub = ok(doPost("/api/purchase/price-adjusts/" + adj + "/submit", admin, null));
        assertThat(sub.at("/warnings/0").asText()).contains("涨幅 20%");
        assertThat(priceApi.getEffectivePrice(Long.valueOf(v), Long.valueOf(a), BigDecimal.ONE, LocalDate.now(), "CNY").orElseThrow().price())
                .isEqualByComparingTo("1.00");
        assertThat(priceApi.getEffectivePrice(Long.valueOf(v), Long.valueOf(a), new BigDecimal("2000"), next, "CNY").orElseThrow().price())
                .isEqualByComparingTo("1.20");
        // 历史价格：三条
        assertThat(ok(doGet("/api/purchase/prices/history?supplierId=" + v + "&materialId=" + a, admin))).hasSize(3);

        // 立即生效的新价格替代旧价格
        price(v, List.of(List.of(a, "0", "0.90")));
        JsonNode replaced = ok(doGet("/api/purchase/prices?materialId=" + a + "&statuses=REPLACED", admin)).at("/list");
        assertThat(replaced).hasSize(2);
        assertThat(replaced.at("/0/effectiveTo").asText()).isEqualTo(LocalDate.now().minusDays(1).toString());
        assertThat(priceApi.getLatestPrice(Long.valueOf(a)).orElseThrow().price()).isEqualByComparingTo("0.90");
    }
}
