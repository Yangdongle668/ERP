package com.erp.it.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** INV-CNT-R07 序列号物料盘点：按序列号清单比对盘盈 / 盘亏序列号，数量相同但序列号不同也要调整 */
class SerialCountIntegrationTest extends InventoryTestSupport {

    private void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    @AfterEach
    void resetParams() throws Exception {
        for (String k : List.of("inv.count.recount-threshold-pct", "inv.count.recount-threshold-amount")) ok(doPost("/api/system/params/" + k + "/reset", admin, null));
    }

    private void otherIn(String materialId, String warehouseId, List<String> serials) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("reason", "FOUND");
        body.put("lines", List.of(Map.of("materialId", materialId, "qty", serials.size(), "serialNos", serials)));
        String id = ok(doPost("/api/inventory/stock-ins", admin, body)).asText();
        assertThat(ok(doPost("/api/inventory/stock-ins/" + id + "/submit", admin, null)).asText()).isEqualTo("COMPLETED");
    }

    private String newCount(String materialId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("countType", "PARTIAL");
        body.put("warehouseIds", List.of(W_AUX));
        body.put("materialIds", List.of(materialId));
        body.put("blindCount", false);
        String c = ok(doPost("/api/inventory/counts", admin, body)).asText();
        ok(doPost("/api/inventory/counts/" + c + "/generate", admin, null));
        return c;
    }

    private JsonNode line(String count) throws Exception {
        return ok(doGet("/api/inventory/counts/" + count + "/lines", admin)).at("/list/0");
    }

    @Test
    void serialCount() throws Exception {
        setParam("inv.count.recount-threshold-pct", "100");
        setParam("inv.count.recount-threshold-amount", "1000000");
        String u = uniq();
        String m = material(CAT_AUX, "序列号盘点料", Map.of("tracking", "SERIAL", "iqcRequired", false));
        String code = code(m);
        List<String> book = List.of("S" + u + "1", "S" + u + "2", "S" + u + "3", "S" + u + "4", "S" + u + "5");
        otherIn(m, W_AUX, book);
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("5");

        String c = newCount(m);
        JsonNode l = line(c);
        assertThat(l.at("/serialTracked").asBoolean()).isTrue();
        assertThat(l.at("/bookSerials").size()).isEqualTo(5);
        String lineId = l.at("/id").asText();

        // 序列号物料必须录入清单，不能只填数量；不能导入 Excel 实盘数量
        assertError(doPut("/api/inventory/counts/" + c + "/lines", admin, List.of(Map.of("id", lineId, "countQty", 4))),
                "物料「" + code + "」按序列号管理，请录入实盘序列号清单");

        // 别处在库的序列号不能在本仓盘盈
        String other = "X" + u;
        otherIn(m, W_ELEC, List.of(other));
        assertError(doPut("/api/inventory/counts/" + c + "/lines", admin, List.of(Map.of("id", lineId, "countSerials", List.of(book.get(0), other)))),
                "序列号「" + other + "」账面在仓库「电子料仓」，不能在本仓盘盈，请先核对或调拨");

        // 实盘 S1 S2 S3 + 新序列号 N1（账面 S4 S5 找不到）
        String n1 = "N" + u;
        ok(doPut("/api/inventory/counts/" + c + "/lines", admin, List.of(Map.of("id", lineId, "countSerials", List.of(book.get(0), book.get(1), book.get(2), n1)))));
        l = line(c);
        assertThat(l.at("/countQty").decimalValue()).isEqualByComparingTo("4");
        assertThat(l.at("/diffQty").decimalValue()).isEqualByComparingTo("-1");
        assertThat(l.at("/gainSerials").toString()).isEqualTo("[\"" + n1 + "\"]");
        assertThat(l.at("/lossSerials").size()).isEqualTo(2);
        assertError(doPost("/api/inventory/counts/" + c + "/submit", admin, null), "第 1 行有差异，请选择差异原因");
        ok(doPut("/api/inventory/counts/" + c + "/lines", admin, List.of(Map.of("id", lineId, "reason", "DAMAGE"))));   // 只改原因，保持原清单
        assertThat(line(c).at("/countQty").decimalValue()).isEqualByComparingTo("4");
        ok(doPost("/api/inventory/counts/" + c + "/submit", admin, null));
        ok(doPost("/api/inventory/counts/" + c + "/approve", admin, null));
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("4");

        // 第二次盘点：数量相同（4）但序列号被调包 n1 → Z1：差异数量为 0，仍要盘盈 Z1、盘亏 n1
        String z1 = "Z" + u;
        String c2 = newCount(m);
        JsonNode l2 = line(c2);
        assertThat(l2.at("/bookSerials").size()).isEqualTo(4);
        String lineId2 = l2.at("/id").asText();
        ok(doPut("/api/inventory/counts/" + c2 + "/lines", admin, List.of(Map.of("id", lineId2, "countSerials", List.of(book.get(0), book.get(1), book.get(2), z1)))));
        l2 = line(c2);
        assertThat(l2.at("/diffQty").decimalValue()).isEqualByComparingTo("0");
        assertThat(l2.at("/gainSerials").toString()).isEqualTo("[\"" + z1 + "\"]");
        assertThat(l2.at("/lossSerials").toString()).isEqualTo("[\"" + n1 + "\"]");
        assertThat(ok(doGet("/api/inventory/counts/" + c2 + "/lines?filter=DIFF", admin)).at("/total").asInt()).isEqualTo(1);
        // 序列号物料不能导入实盘数量（在线录入序列号清单）
        assertError(doPost("/api/inventory/counts/" + c2 + "/submit", admin, null), "第 1 行有差异，请选择差异原因");
        ok(doPut("/api/inventory/counts/" + c2 + "/lines", admin, List.of(Map.of("id", lineId2, "reason", "RECORD_ERROR"))));
        ok(doPost("/api/inventory/counts/" + c2 + "/submit", admin, null));
        ok(doPost("/api/inventory/counts/" + c2 + "/approve", admin, null));
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("4");

        // 第三次：账面序列号已更新为 S1 S2 S3 Z1，全部找到则没有差异
        String c3 = newCount(m);
        JsonNode l3 = line(c3);
        assertThat(l3.at("/bookSerials").toString()).contains(z1).doesNotContain(n1);
        ok(doPut("/api/inventory/counts/" + c3 + "/lines", admin, List.of(Map.of("id", l3.at("/id").asText(), "countSerials", List.of(book.get(0), book.get(1), book.get(2), z1)))));
        l3 = line(c3);
        assertThat(l3.at("/gainSerials").size()).isEqualTo(0);
        assertThat(l3.at("/lossSerials").size()).isEqualTo(0);
        ok(doPost("/api/inventory/counts/" + c3 + "/submit", admin, null));
        ok(doPost("/api/inventory/counts/" + c3 + "/approve", admin, null));
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("4");
    }
}
