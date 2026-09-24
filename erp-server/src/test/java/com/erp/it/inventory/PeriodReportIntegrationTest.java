package com.erp.it.inventory;

import com.erp.module.inventory.api.doc.StockOutType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 期初与月结（08-09）、库存查询与报表（08-08）验收用例 */
class PeriodReportIntegrationTest extends InventoryTestSupport {

    static final String PERIOD = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

    @Test
    void openingCompletedCannotImportAgain() throws Exception {
        JsonNode info = ok(doGet("/api/inventory/opening", admin));
        assertThat(info.at("/completed").asBoolean()).isTrue();
        assertThat(info.at("/docCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(info.at("/openingDate").asText()).isEqualTo(LocalDate.now().withDayOfMonth(1).minusDays(1).toString());
        assertError(upload("/api/inventory/opening/import", openingXlsx(List.of(List.of("W-AUX", "", "X", "", "", "", "", "1", "1", "")))),
                "期初已完成，不能再导入或清空");
        assertError(doPost("/api/inventory/periods/init", admin, Map.of("period", PERIOD)), "已设置启用期间 " + PERIOD);
        // 单据日期早于启用期间
        String m = material(CAT_AUX, "早期料", Map.of("tracking", "NONE", "iqcRequired", false));
        Long in = purchaseIn(m, W_AUX, "1", null, LocalDate.now().withDayOfMonth(1).minusDays(3)).get(0);
        assertError(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()), "单据日期早于系统启用期间 " + PERIOD);
    }

    /** INV-RPT-T02：收发存；INV-RPT-T03：低于安全库存；流水查询范围 */
    @Test
    void reports() throws Exception {
        String m = material(CAT_AUX, "报表料", Map.of("tracking", "NONE", "iqcRequired", false, "safetyStock", 800));
        receive(m, W_AUX, "1000", null, null);
        Long out = issue(StockOutType.PRODUCTION_ISSUE, m, W_AUX, "300");
        ok(doPost("/api/inventory/stock-outs/" + out + "/confirm", admin, Map.of()));

        JsonNode sum = ok(doGet("/api/inventory/reports/in-out-summary?keyword=" + code(m), admin));
        JsonNode row = sum.at("/rows/0");
        assertThat(row.at("/inQty").decimalValue()).isEqualByComparingTo("1000");
        assertThat(row.at("/inDetail/PURCHASE").decimalValue()).isEqualByComparingTo("1000");
        assertThat(row.at("/outQty").decimalValue()).isEqualByComparingTo("300");
        assertThat(row.at("/outDetail/ISSUE").decimalValue()).isEqualByComparingTo("300");
        assertThat(row.at("/closingQty").decimalValue()).isEqualByComparingTo(row.at("/openingQty").decimalValue().add(new java.math.BigDecimal("700")));
        assertThat(sum.at("/costCalculated").asBoolean()).isFalse();

        // 可用 700 < 安全库存 800：缺口 100
        JsonNode alerts = ok(doGet("/api/inventory/alerts?type=LOW", admin));
        JsonNode hit = null;
        for (JsonNode a : alerts) if (a.at("/materialId").asText().equals(m)) hit = a;
        assertThat(hit).isNotNull();
        assertThat(hit.at("/gap").decimalValue()).isEqualByComparingTo("100");

        // 库存查询按物料 + 仓库、明细
        JsonNode stocks = ok(doGet("/api/inventory/stocks?materialId=" + m + "&groupBy=BATCH", admin));
        assertThat(stocks.at("/total").asInt()).isEqualTo(1);
        assertThat(stocks.at("/list/0/warehouseName").asText()).isEqualTo("辅料仓");

        assertError(doGet("/api/inventory/stock-txns?dateFrom=2024-01-01&dateTo=2025-06-30", admin), "查询时间范围不能超过 1 年");
        ok(doGet("/api/inventory/reports/aging", admin));
        ok(doGet("/api/inventory/reports/slow-moving?days=1", admin));
    }

    /** INV-PRD-T04、T05：月结后不能过账；财务已结账不能反结账 */
    @Test
    void closeAndReopen() throws Exception {
        String m = material(CAT_AUX, "月结料", Map.of("tracking", "NONE", "iqcRequired", false));
        receive(m, W_AUX, "20", null, null);
        JsonNode check = ok(doPost("/api/inventory/periods/" + PERIOD + "/check", admin, null));
        assertThat(check.at("/passed").asBoolean()).as("月结检查：%s", check).isTrue();
        Long pending = purchaseIn(m, W_AUX, "5", null, null).get(0);
        ok(doPost("/api/inventory/periods/" + PERIOD + "/close", admin, null));
        try {
            JsonNode periods = ok(doGet("/api/inventory/periods", admin));
            assertThat(periods.at("/1/periodStatus").asText()).isEqualTo("CLOSED");
            assertThat(periods.at("/0/periodStatus").asText()).as("自动创建下一期间").isEqualTo("OPEN");
            assertError(doPost("/api/inventory/stock-ins/" + pending + "/confirm", admin, Map.of()), "库存期间 " + PERIOD + " 已结账，不能过账");
            ItFinanceConfig.CLOSED.add(PERIOD);
            assertError(doPost("/api/inventory/periods/" + PERIOD + "/reopen", admin, null), "财务期间已结账，请先由财务反结账");
        } finally {
            ItFinanceConfig.CLOSED.remove(PERIOD);
            ok(doPost("/api/inventory/periods/" + PERIOD + "/reopen", admin, null));
        }
        ok(doPost("/api/inventory/stock-ins/" + pending + "/confirm", admin, Map.of()));
        assertThat(onHand(m, W_AUX)).isEqualByComparingTo("25");
    }
}
