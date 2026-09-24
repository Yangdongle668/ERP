package com.erp.it.inventory;

import com.erp.module.inventory.api.doc.JudgeResult;
import com.erp.module.inventory.api.doc.TransferRequest;
import com.erp.module.inventory.api.doc.TransferType;
import com.erp.module.inventory.api.doc.StockOutType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 调拨单（08-05）、盘点（08-06）验收用例 */
class TransferCountIntegrationTest extends InventoryTestSupport {

    /** INV-TF-T01、T02、T05：检验调拨按判定拆单，特采批次打标 */
    @Test
    void inspectionTransferSplitsByJudgeResult() throws Exception {
        String m = material(CAT_ELEC, "来料", Map.of("tracking", "BATCH", "iqcRequired", true));
        String batch = "BI" + uniq();
        receive(m, null, "1000", batch, null);
        assertThat(onHand(m, W_QC)).isEqualByComparingTo("1000");

        List<Long> ids = docApi.createTransfer(new TransferRequest(TransferType.INSPECTION, source("QC_INSPECTION"), 777L, Long.valueOf(W_QC), null, null,
                null, List.of(new TransferRequest.Line(1L, Long.valueOf(m), batch, null, new BigDecimal("980"), JudgeResult.CONCESSION, null),
                new TransferRequest.Line(2L, Long.valueOf(m), batch, null, new BigDecimal("20"), JudgeResult.REJECTED, null))));
        assertThat(ids).hasSize(2);
        JsonNode first = ok(doGet("/api/inventory/transfers/" + ids.get(0), admin));
        assertThat(first.at("/toWarehouseId").asText()).isEqualTo(W_ELEC);
        assertThat(ok(doGet("/api/inventory/transfers/" + ids.get(1), admin)).at("/toWarehouseId").asText()).isEqualTo(W_NG);

        // 检验调拨单不能改数量、不能作废
        Map<String, Object> save = new HashMap<>();
        save.put("fromWarehouseId", W_QC);
        save.put("toWarehouseId", W_ELEC);
        save.put("lines", List.of(Map.of("id", first.at("/lines/0/id").asText(), "qty", 900)));
        assertError(doPut("/api/inventory/transfers/" + ids.get(0), admin, save), "检验调拨单不能修改数量");
        assertError(doPost("/api/inventory/transfers/" + ids.get(0) + "/void", admin, Map.of()), "检验调拨单不能作废，请由品质重新判定");

        JsonNode r = ok(doPost("/api/inventory/transfers/batch-confirm", admin, Map.of("ids", ids.stream().map(String::valueOf).toList())));
        assertThat(r.at("/success").asInt()).isEqualTo(2);
        assertThat(onHand(m, W_QC)).isEqualByComparingTo("0");
        assertThat(onHand(m, W_ELEC)).isEqualByComparingTo("980");
        assertThat(onHand(m, W_NG)).isEqualByComparingTo("20");
        JsonNode summary = materialSummary(m);
        assertThat(summary.at("/onHandQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(summary.at("/availableQty").decimalValue()).isEqualByComparingTo("980");
        assertThat(summary.at("/ngQty").decimalValue()).isEqualByComparingTo("20");
        // 特采标记
        JsonNode batches = ok(doGet("/api/inventory/batches?materialId=" + m, admin));
        assertThat(batches.at("/list/0/concession").asBoolean()).isTrue();
    }

    /** INV-TF-T03、T04：不允许的组合；复检送检减少可用量 */
    @Test
    void manualTransferRules() throws Exception {
        String m = material(CAT_AUX, "调拨料", Map.of("tracking", "NONE", "iqcRequired", false));
        receive(m, W_AUX, "300", null, null);
        Map<String, Object> body = new HashMap<>();
        body.put("transferType", "NORMAL");
        body.put("fromWarehouseId", W_NG);
        body.put("toWarehouseId", W_RAW);
        body.put("reason", "整理");
        body.put("lines", List.of(Map.of("materialId", m, "qty", 10)));
        assertError(doPost("/api/inventory/transfers", admin, body), "不允许从【不良品仓】调拨到【原材料仓】");
        body.put("fromWarehouseId", W_AUX);
        body.put("toWarehouseId", W_AUX);
        assertError(doPost("/api/inventory/transfers", admin, body), "同一仓库调拨请选择不同的库位");

        // 普通调拨：辅料仓 → 原材料仓
        body.put("toWarehouseId", W_RAW);
        String t = ok(doPost("/api/inventory/transfers", admin, body)).asText();
        ok(doPost("/api/inventory/transfers/" + t + "/confirm", admin, Map.of()));
        assertThat(onHand(m, W_RAW)).isEqualByComparingTo("10");

        // 复检送检 100 到待检仓：可用量减少 100
        body.put("transferType", "RECHECK");
        body.put("toWarehouseId", W_QC);
        body.put("lines", List.of(Map.of("materialId", m, "qty", 100)));
        String rc = ok(doPost("/api/inventory/transfers", admin, body)).asText();
        ok(doPost("/api/inventory/transfers/" + rc + "/confirm", admin, Map.of()));
        JsonNode s = materialSummary(m);
        assertThat(s.at("/availableQty").decimalValue()).isEqualByComparingTo("200");
        assertThat(s.at("/qcQty").decimalValue()).isEqualByComparingTo("100");
    }

    /** INV-CNT-T01 ~ T07 */
    @Test
    void countFlow() throws Exception {
        setParam("inv.count.recount-threshold-pct", "5");
        String a = material(CAT_AUX, "盘点料", Map.of("tracking", "NONE", "iqcRequired", false));
        String x = material(CAT_AUX, "盘点外物料", Map.of("tracking", "NONE", "iqcRequired", false));
        receive(a, W_AUX, "100", null, null);

        Map<String, Object> body = new HashMap<>();
        body.put("countType", "PARTIAL");
        body.put("warehouseIds", List.of(W_AUX));
        body.put("materialIds", List.of(a));
        body.put("blindCount", true);
        String c = ok(doPost("/api/inventory/counts", admin, body)).asText();
        assertThat(ok(doPost("/api/inventory/counts/" + c + "/generate", admin, null)).asInt()).isEqualTo(1);
        JsonNode detail = ok(doGet("/api/inventory/counts/" + c, admin));
        assertThat(detail.at("/countStatus").asText()).isEqualTo("COUNTING");

        // T01 盲盘：没有审核权限的仓管员看不到账面数量
        String keeper = keeper(List.of("inv:count:query", "inv:count:input"));
        JsonNode keeperLines = ok(doGet("/api/inventory/counts/" + c + "/lines", keeper));
        assertThat(keeperLines.at("/list/0/bookQty").isMissingNode()).isTrue();
        JsonNode lines = ok(doGet("/api/inventory/counts/" + c + "/lines", admin));
        assertThat(lines.at("/list/0/bookQty").decimalValue()).isEqualByComparingTo("100");
        String lineId = lines.at("/list/0/id").asText();

        // T02 冻结：盘点中不能出入库
        Long out = issue(StockOutType.PRODUCTION_ISSUE, a, W_AUX, "1");
        JsonNode r = doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of());
        assertThat(r.at("/msg").asText()).isEqualTo("该物料正在盘点中（盘点单 " + detail.at("/docNo").asText() + "），不能出入库");

        // T06 有未录入行
        assertError(doPost("/api/inventory/counts/" + c + "/submit", admin, null), "还有 1 行未录入实盘数量");
        // T03 实盘 90：差异 10% ≥ 5% 需复盘
        ok(doPut("/api/inventory/counts/" + c + "/lines", keeper, List.of(Map.of("id", lineId, "countQty", 90))));
        assertThat(ok(doGet("/api/inventory/counts/" + c + "/lines", admin)).at("/list/0/needRecount").asBoolean()).isTrue();
        assertError(doPost("/api/inventory/counts/" + c + "/submit", admin, null), "还有 1 行需要复盘，请录入复盘数量");
        ok(doPut("/api/inventory/counts/" + c + "/lines", keeper, List.of(Map.of("id", lineId, "countQty", 90, "recountQty", 92))));
        assertError(doPost("/api/inventory/counts/" + c + "/submit", admin, null), "第 1 行有差异，请选择差异原因");
        ok(doPut("/api/inventory/counts/" + c + "/lines", keeper, List.of(Map.of("id", lineId, "countQty", 90, "recountQty", 92, "reason", "DAMAGE"))));

        // T05 盘点外物料 X 5 个
        ok(doPost("/api/inventory/counts/" + c + "/lines/add", admin, Map.of("warehouseId", W_AUX, "materialId", x, "countQty", 5, "reason", "RECORD_ERROR")));

        // T04 提交 → 审核：盘亏 8、盘盈 5，冻结解除
        assertThat(ok(doPost("/api/inventory/counts/" + c + "/submit", admin, null)).asText()).isEqualTo("SUBMITTED");
        ok(doPost("/api/inventory/counts/" + c + "/approve", admin, null));
        detail = ok(doGet("/api/inventory/counts/" + c, admin));
        assertThat(detail.at("/countStatus").asText()).isEqualTo("APPROVED");
        assertThat(detail.at("/adjustDocs").size()).isEqualTo(2);
        assertThat(onHand(a, W_AUX)).isEqualByComparingTo("92");
        assertThat(onHand(x, W_AUX)).isEqualByComparingTo("5");
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of()));
        assertThat(onHand(a, W_AUX)).isEqualByComparingTo("91");

        // T07 作废盘点中的单据：冻结解除，库存不变
        String c2 = ok(doPost("/api/inventory/counts", admin, body)).asText();
        ok(doPost("/api/inventory/counts/" + c2 + "/generate", admin, null));
        Long out2 = issue(StockOutType.PRODUCTION_ISSUE, a, W_AUX, "1");
        assertThat(doPost("/api/inventory/stock-outs/" + out2 + "/confirm", admin, Map.of()).at("/code").asInt()).isNotZero();
        ok(doPost("/api/inventory/counts/" + c2 + "/void", admin, Map.of()));
        assertThat(onHand(a, W_AUX)).isEqualByComparingTo("91");
        ok(doPost("/api/inventory/stock-outs/" + out2 + "/confirm", admin, Map.of()));
        resetParam("inv.count.recount-threshold-pct");
    }

    private void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    private void resetParam(String key) throws Exception {
        ok(doPost("/api/system/params/" + key + "/reset", admin, null));
    }

    /** 只有指定权限的仓管员（数据范围全部） */
    private String keeper(List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String roleId = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", "ALL", "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + roleId + "/permissions", admin, Map.of("permissions", permissions)));
        Map<String, Object> dept = new HashMap<>();
        dept.put("parentId", "100");
        dept.put("orgType", "DEPT");
        dept.put("code", "D" + uniq());
        dept.put("name", "仓库部" + uniq());
        dept.put("sort", 10);
        String deptId = ok(doPost("/api/system/orgs", admin, dept)).asText();
        String username = "wh" + uniq();
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("realName", "仓管" + username);
        user.put("deptId", deptId);
        user.put("roleIds", List.of(roleId));
        user.put("password", "Passw0rd!2026");
        user.put("mustChangePassword", false);
        ok(doPost("/api/system/users", admin, user));
        return login(username, "Passw0rd!2026");
    }
}
