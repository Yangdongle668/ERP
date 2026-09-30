package com.erp.it.shipping;

import com.erp.module.inventory.api.stock.BatchSuggestion;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 出货通知 → 拣货 → 装箱 → OQC → 出货单 → 出库确认 / 反确认（需求 11-01～11-03 验收用例） */
class ShippingFlowIntegrationTest extends ShippingTestSupport {

    /** SHP-SN-T01/T02/T05、SHP-PK-T01～T05、SHP-SH-T01/T02/T06、反确认后再出库 */
    @Test
    void noticePickPackShip() throws Exception {
        String u = uniq();
        String b1 = u + "-B1";
        String b2 = u + "-B2";
        String m = fg("批次成品", Map.of("tracking", "BATCH"));
        stock(m, "200", b1);
        stock(m, "500", b2);
        String c = customer("出货客户", false);
        String order = approvedOrder(c, List.of(orderLine(m, "1000", "10", null)));
        Long ol = orderLineId(order, 0);

        // SHP-SN-T01：通知 600 → 订单行已通知 600，可通知 400
        String n = notice(c, ol, "600");
        assertThat(orderLineDto(ol).noticedQty()).isEqualByComparingTo("600");
        assertThat(orderLineDto(ol).noticeableQty()).isEqualByComparingTo("400");
        // 草稿修改数量：先释放后占用
        ok(doPut("/api/shipping/notices/" + n, admin, noticeBody(c, List.of(noticeLine(ol, "600")))));
        assertThat(orderLineDto(ol).noticedQty()).isEqualByComparingTo("600");

        // SHP-SN-T02：另一通知 500 → 超过可通知数量 400
        assertError(doPost("/api/shipping/notices", admin, noticeBody(c, List.of(noticeLine(ol, "500")))), "第 1 行通知数量超过可通知数量 400");

        // SHP-SN-T05：审核生成拣货单；SHP-PK-T01：推荐 B1 200、B2 400
        submitNotice(n);
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("APPROVED");
        String p = pickingOf(n);
        JsonNode pk = picking(p);
        assertThat(pk.at("/pickingStatus").asText()).isEqualTo("WAITING");
        assertThat(pk.at("/lines").size()).isEqualTo(2);
        assertThat(pk.at("/lines/0/batchNo").asText()).isEqualTo(b1);
        assertThat(pk.at("/lines/0/suggestedQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(pk.at("/lines/1/batchNo").asText()).isEqualTo(b2);
        assertThat(pk.at("/lines/1/suggestedQty").decimalValue()).isEqualByComparingTo("400");
        String nl = pk.at("/lines/0/noticeLineId").asText();

        // 未完成拣货不能装箱
        assertError(batchPack(n, nl, b1, "100", null), "拣货尚未完成，不能装箱");

        // SHP-PK-R02：批次可用数量不足
        assertError(doPut("/api/shipping/pickings/" + p + "/lines", admin, Map.of("lines", List.of(pickLine(nl, b1, "250")))),
                "批次「" + b1 + "」可用数量不足（可用 200）");
        // SHP-PK-T02：实拣 580 → 数量不一致；按实拣完成后缺货 20
        ok(doPut("/api/shipping/pickings/" + p + "/lines", admin, Map.of("lines", List.of(pickLine(nl, b1, "200"), pickLine(nl, b2, "380")))));
        // SHP-PK-R02 预留：所拣批次被预留，其他单据推荐批次时扣除（B1 全部预留，B2 只剩 120）
        assertThat(reserved(m)).isEqualByComparingTo("580");
        List<BatchSuggestion> others = inventoryQueryApi.suggestBatches(Long.valueOf(m), Long.valueOf(W_FG), new BigDecimal("1000"));
        assertThat(others).extracting(BatchSuggestion::batchNo).containsExactly(b2);
        assertThat(others.get(0).qty()).isEqualByComparingTo("120");
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("PICKING");
        assertThat(picking(p).at("/pickingStatus").asText()).isEqualTo("PICKING");
        // 拣货已开始不能反审核（此时状态已是拣货中）
        assertError(doPost("/api/shipping/notices/" + n + "/unapprove", admin, null), "当前状态【拣货中】不能反审核");
        assertError(doPost("/api/shipping/pickings/" + p + "/complete", admin, Map.of("acceptShort", false)), "第 1 行拣货数量 580 与通知数量 600 不一致");
        ok(doPost("/api/shipping/pickings/" + p + "/complete", admin, Map.of("acceptShort", true)));
        JsonNode d = noticeDetail(n);
        assertThat(d.at("/lines/0/pickedQty").decimalValue()).isEqualByComparingTo("580");
        assertThat(d.at("/lines/0/shortageQty").decimalValue()).isEqualByComparingTo("20");
        assertThat(orderLineDto(ol).noticedQty()).isEqualByComparingTo("580");

        // SHP-PK-T04：装箱合计 570 → 装箱数量与拣货数量不一致
        ok(batchPack(n, nl, b1, "100", null));
        ok(batchPack(n, nl, b2, "100", "300"));
        String err = doPost("/api/shipping/notices/" + n + "/pack-complete", admin, Map.of()).at("/msg").asText();
        assertThat(err).startsWith("装箱数量与拣货数量不一致：").contains(b2).contains("装箱 300 / 拣货 380");
        // 超过可装数量
        assertError(batchPack(n, nl, b2, "100", "100"), code(m) + " 批次 " + b2 + " 装箱数量超过可装数量 80");
        // SHP-PK-T03：尾箱为余数，共 6 箱：1-5 每箱 100，第 6 箱 80
        ok(batchPack(n, nl, b2, "100", null));
        JsonNode pv = packing(n);
        assertThat(pv.at("/cartons").size()).isEqualTo(6);
        assertThat(pv.at("/cartons/5/cartonNo").asInt()).isEqualTo(6);
        assertThat(pv.at("/cartons/5/lines/0/qty").decimalValue()).isEqualByComparingTo("80");
        assertThat(pv.at("/cartons/0/cbm").decimalValue()).isEqualByComparingTo("0.096");
        assertThat(pv.at("/totals/qty").decimalValue()).isEqualByComparingTo("580");
        assertThat(pv.at("/complete").asBoolean()).isTrue();
        ok(doPost("/api/shipping/notices/" + n + "/pack-complete", admin, Map.of()));
        d = noticeDetail(n);
        assertThat(d.at("/noticeStatus").asText()).isEqualTo("PACKED");
        assertThat(d.at("/canShip").asBoolean()).isTrue();
        // 不需要 OQC
        assertError(doPost("/api/shipping/notices/" + n + "/request-oqc", admin, null), "该出货通知不需要 OQC");

        // SHP-PK-T05：箱唛每箱一张，“1/6” … “6/6”
        JsonNode labels = ok(doGet("/api/shipping/notices/" + n + "/labels", admin)).at("/cartons");
        assertThat(labels.size()).isEqualTo(6);
        assertThat(labels.get(0).at("/cartonText").asText()).isEqualTo("1/6");
        assertThat(labels.get(5).at("/cartonText").asText()).isEqualTo("6/6");

        // SHP-SH-T06：分批出货，第一批勾选箱 1-2（B1 200）
        List<String> first = List.of(pv.at("/cartons/0/id").asText(), pv.at("/cartons/1/id").asText());
        String s1 = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of("cartonIds", first))).asText();
        JsonNode sd = shipment(s1);
        assertThat(sd.at("/lines").size()).isEqualTo(1);
        assertThat(sd.at("/lines/0/batchNo").asText()).isEqualTo(b1);
        assertThat(sd.at("/totalQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(sd.at("/cartonCount").asInt()).isEqualTo(2);
        assertThat(sd.at("/totalAmount").decimalValue()).isEqualByComparingTo("2000");
        // 已被出货单占用的箱不能修改
        assertError(doDelete("/api/shipping/cartons/" + first.get(0), admin), "箱号 1 已出货，不能修改");

        // SHP-SH-T01：提交 → 仓库出现销售出库单（带批次）
        ok(doPost("/api/shipping/shipments/" + s1 + "/submit", admin, null));
        sd = shipment(s1);
        assertThat(sd.at("/shipmentStatus").asText()).isEqualTo("SUBMITTED");
        JsonNode out = ok(doGet("/api/inventory/stock-outs/" + sd.at("/stockOutId").asText(), admin));
        assertThat(out.at("/outType").asText()).isEqualTo("SALES_OUT");
        assertThat(out.at("/sourceType").asText()).isEqualTo("SHP_SHIPMENT");
        assertThat(out.at("/lines/0/batchNo").asText()).isEqualTo(b1);
        // 撤回 → 出库单作废，回到草稿；再提交
        ok(doPost("/api/shipping/shipments/" + s1 + "/withdraw", admin, null));
        assertThat(shipment(s1).at("/shipmentStatus").asText()).isEqualTo("DRAFT");
        assertThat(ok(doGet("/api/inventory/stock-outs/" + out.at("/id").asText(), admin)).at("/status").asText()).isEqualTo("VOIDED");

        // SHP-SH-T02：确认出库 → 已出货，订单已出货数量增加
        submitAndConfirm(s1);
        assertThat(orderLineDto(ol).shippedQty()).isEqualByComparingTo("200");
        assertThat(reserved(m)).as("出库后预留减少").isEqualByComparingTo("380");
        d = noticeDetail(n);
        assertThat(d.at("/lines/0/shippedQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(d.at("/noticeStatus").asText()).isEqualTo("PACKED");
        assertThat(shipment(s1).at("/lines/0/outQty").decimalValue()).isEqualByComparingTo("200");
        // 已出库不能撤回
        assertError(doPost("/api/shipping/shipments/" + s1 + "/withdraw", admin, null), "出库单已确认，不能撤回");

        // 第二批：其余全部未出货箱 → 通知已出货
        String s2 = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of())).asText();
        assertThat(shipment(s2).at("/totalQty").decimalValue()).isEqualByComparingTo("380");
        assertThat(shipment(s2).at("/cartonCount").asInt()).isEqualTo(4);
        submitAndConfirm(s2);
        assertThat(reserved(m)).as("全部出货后释放").isEqualByComparingTo("0");
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("SHIPPED");
        assertThat(orderLineDto(ol).shippedQty()).isEqualByComparingTo("580");
        assertError(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of()), "出货通知尚未完成装箱/OQC");

        // 仓库反确认 → 出货单待出库，订单与通知冲回；再次确认后恢复
        String out2 = shipment(s2).at("/stockOutId").asText();
        ok(doPost("/api/inventory/stock-outs/" + out2 + "/unconfirm", admin, Map.of("reason", "数量点错")));
        assertThat(shipment(s2).at("/shipmentStatus").asText()).isEqualTo("SUBMITTED");
        assertThat(orderLineDto(ol).shippedQty()).isEqualByComparingTo("200");
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("PACKED");
        ok(doPost("/api/inventory/stock-outs/" + out2 + "/confirm", admin, Map.of()));
        assertThat(shipment(s2).at("/shipmentStatus").asText()).isEqualTo("SHIPPED");
        assertThat(orderLineDto(ol).shippedQty()).isEqualByComparingTo("580");
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("SHIPPED");

        // ShipmentQueryApi（退货选单 / 追溯）
        JsonNode lines = ok(doGet("/api/shipping/shipments?customerId=" + c, admin)).at("/list");
        assertThat(lines.size()).isEqualTo(2);
    }

    /** SHP-SN-T06 关闭释放、SHP-SN-R06 反审核、SHP-SN-T03 黑名单、未启用拣货 / 装箱 */
    @Test
    void unapproveCloseAndSimpleMode() throws Exception {
        String m = fg("普通成品", Map.of());
        stock(m, "1000", null);
        String c = customer("关闭客户", false);
        String order = approvedOrder(c, List.of(orderLine(m, "1000", "5", null)));
        Long ol = orderLineId(order, 0);

        String n = notice(c, ol, "600");
        submitNotice(n);
        // 拣货单未开始 → 可反审核，拣货单作废
        ok(doPost("/api/shipping/notices/" + n + "/unapprove", admin, null));
        JsonNode d = noticeDetail(n);
        assertThat(d.at("/noticeStatus").asText()).isEqualTo("DRAFT");
        assertThat(d.at("/pickings/0/status").asText()).isEqualTo("CANCELED");
        submitNotice(n);
        ok(doPost("/api/shipping/pickings/" + pickingOf(n) + "/start", admin, null));
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("PICKING");

        // SHP-SN-T06：关闭剩余 → 订单可通知数量恢复
        assertError(doPost("/api/shipping/notices/" + n + "/close", admin, Map.of()), "请填写关闭原因");
        ok(doPost("/api/shipping/notices/" + n + "/close", admin, Map.of("reason", "客户取消本批")));
        d = noticeDetail(n);
        assertThat(d.at("/noticeStatus").asText()).isEqualTo("CLOSED");
        assertThat(d.at("/pickings/1/status").asText()).isEqualTo("CANCELED");
        assertThat(orderLineDto(ol).noticedQty()).isEqualByComparingTo("0");
        assertThat(orderLineDto(ol).noticeableQty()).isEqualByComparingTo("1000");

        // 草稿删除释放已通知数量
        String n2 = notice(c, ol, "100");
        assertThat(orderLineDto(ol).noticedQty()).isEqualByComparingTo("100");
        ok(doDelete("/api/shipping/notices/" + n2, admin));
        assertThat(orderLineDto(ol).noticedQty()).isEqualByComparingTo("0");

        // 未启用拣货、装箱：审核后直接已装箱，出货单不带批次，由仓库确认
        setParam("shp.picking.enabled", "false");
        setParam("shp.packing.enabled", "false");
        try {
            String n3 = notice(c, ol, "300");
            submitNotice(n3);
            d = noticeDetail(n3);
            assertThat(d.at("/noticeStatus").asText()).isEqualTo("PACKED");
            assertThat(d.at("/pickings").size()).isEqualTo(0);
            // 分批：先出 100
            List<Map<String, Object>> units = new ArrayList<>();
            Map<String, Object> unit = new HashMap<>();
            unit.put("noticeLineId", d.at("/lines/0/id").asText());
            unit.put("qty", "100");
            units.add(unit);
            String s1 = ok(doPost("/api/shipping/notices/" + n3 + "/shipments", admin, Map.of("units", units))).asText();
            assertThat(shipment(s1).at("/totalQty").decimalValue()).isEqualByComparingTo("100");
            submitAndConfirm(s1);
            String s2 = ok(doPost("/api/shipping/notices/" + n3 + "/shipments", admin, Map.of())).asText();
            assertThat(shipment(s2).at("/totalQty").decimalValue()).isEqualByComparingTo("200");
            // 草稿作废（原因必填）后可重新生成
            assertError(doPost("/api/shipping/shipments/" + s2 + "/void", admin, Map.of()), "请填写作废原因");
            ok(doPost("/api/shipping/shipments/" + s2 + "/void", admin, Map.of("reason", "重开")));
            String s3 = ok(doPost("/api/shipping/notices/" + n3 + "/shipments", admin, Map.of())).asText();
            submitAndConfirm(s3);
            assertThat(noticeDetail(n3).at("/noticeStatus").asText()).isEqualTo("SHIPPED");
            assertThat(orderLineDto(ol).shippedQty()).isEqualByComparingTo("300");
        } finally {
            resetParam("shp.picking.enabled");
            resetParam("shp.packing.enabled");
        }

        // SHP-SN-T03：客户加入黑名单 → 提交提示
        String n4 = notice(c, ol, "50");
        ok(doPost("/api/crm/customers/" + c + "/blacklist", admin, Map.of("reason", "拖欠货款")));
        String msg = doPost("/api/shipping/notices/" + n4 + "/submit", admin, null).at("/msg").asText();
        assertThat(msg).startsWith("客户「").endsWith("在黑名单中，不能出货");
    }

    /** SHP-SH-T03：需要 OQC 未判定不能生成出货单；OQC 合格 → 待出货 */
    @Test
    void oqcFlow() throws Exception {
        String m = fg("OQC成品", Map.of("oqcRequired", true));
        stock(m, "100", null);
        String c = customer("OQC客户", false);
        String order = approvedOrder(c, List.of(orderLine(m, "100", "8", null)));
        Long ol = orderLineId(order, 0);
        String n = notice(c, ol, "100");
        assertThat(noticeDetail(n).at("/oqcRequired").asBoolean()).isTrue();
        submitNotice(n);
        pickAsSuggested(n);
        String nl = noticeDetail(n).at("/lines/0/id").asText();
        ok(batchPack(n, nl, null, "50", null));
        ok(doPost("/api/shipping/notices/" + n + "/pack-complete", admin, Map.of()));
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("PACKED");
        assertError(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of()), "出货通知尚未完成装箱/OQC");

        ok(doPost("/api/shipping/notices/" + n + "/request-oqc", admin, null));
        JsonNode d = noticeDetail(n);
        assertThat(d.at("/noticeStatus").asText()).isEqualTo("OQC");
        assertThat(d.at("/oqcResult").asText()).isEqualTo("PENDING");
        JsonNode ins = ok(doGet("/api/quality/inspections?types=OQC&materialId=" + m, admin)).at("/list");
        assertThat(ins.size()).isEqualTo(1);
        assertThat(ins.get(0).at("/lotQty").decimalValue()).isEqualByComparingTo("100");
        ok(doPost("/api/quality/inspections/" + ins.get(0).at("/id").asText() + "/judge", admin, Map.of("result", "QUALIFIED")));
        d = noticeDetail(n);
        assertThat(d.at("/noticeStatus").asText()).isEqualTo("READY");
        assertThat(d.at("/oqcResult").asText()).isEqualTo("PASSED");

        String s = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of())).asText();
        submitAndConfirm(s);
        assertThat(noticeDetail(n).at("/noticeStatus").asText()).isEqualTo("SHIPPED");
    }

    private static Map<String, Object> pickLine(String noticeLineId, String batchNo, String qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("noticeLineId", noticeLineId);
        m.put("batchNo", batchNo);
        m.put("pickedQty", qty);
        return m;
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    InventoryQueryApi inventoryQueryApi;

    /** 物料的有效库存预留 */
    private BigDecimal reserved(String materialId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(qty - released_qty), 0) FROM inv_reservation WHERE material_id = ? AND status = 'ACTIVE'",
                BigDecimal.class, Long.valueOf(materialId));
    }
}
