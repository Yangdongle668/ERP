package com.erp.it.pmc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MRP 净变更（06-03 run_type = NET_CHANGE）与按工艺工时换算的生产提前期（06-03 3.2 节） */
class PmcNetChangeIntegrationTest extends PmcTestSupport {

    @Test
    void netChangeKeepsUnchangedSuggestions() throws Exception {
        String rawA = raw("净变更料A", Map.of("leadTimeDays", 10));
        String fgA = fg("净变更成品A", Map.of("leadTimeDays", 3));
        bom(fgA, List.of(bomLine(rawA, 2)));
        String rawB = raw("净变更料B", Map.of("leadTimeDays", 5));
        String fgB = fg("净变更成品B", Map.of("leadTimeDays", 2));
        bom(fgB, List.of(bomLine(rawB, 1)));
        String cust = customer();
        LocalDate due = LocalDate.now().plusDays(40);
        salesOrder(cust, List.of(soLine(fgA, "100", due), soLine(fgB, "50", due)));

        runMrp();
        JsonNode a = suggestions(fgA).get(0);
        assertThat(a.at("/qty").decimalValue()).isEqualByComparingTo("100");
        ok(doPut("/api/pmc/mrp/suggestions/" + a.at("/id").asText(), admin, Map.of("qty", 120)));
        ok(doPost("/api/pmc/mrp/suggestions/ignore", admin, Map.of("ids", List.of(suggestions(rawB).get(0).at("/id").asText()), "reason", "客供料")));

        // 没有变化：计划员修改保留，已忽略的建议不再出现
        JsonNode run = runMrp(Map.of("runType", "NET_CHANGE"));
        assertThat(run.at("/runStatus").asText()).as(run.at("/errorMsg").asText()).isEqualTo("SUCCESS");
        assertThat(run.at("/params").asText()).contains("baseRunNo");
        JsonNode a2 = suggestions(fgA).get(0);
        assertThat(a2.at("/id").asText()).isNotEqualTo(a.at("/id").asText());
        assertThat(a2.at("/qty").decimalValue()).isEqualByComparingTo("120");
        assertThat(a2.at("/status").asText()).isEqualTo("PENDING");
        assertThat(suggestions(rawB)).isEmpty();
        assertThat(suggestions(rawA)).hasSize(1);
        assertThat(ok(doGet("/api/pmc/mrp/balance?materialId=" + rawA, admin)).at("/rows").size()).isGreaterThanOrEqualTo(2);

        // 成品 B 新增订单：B 及其子件重新计算，A 不受影响
        salesOrder(cust, List.of(soLine(fgB, "30", due)));
        runMrp(Map.of("runType", "NET_CHANGE"));
        assertThat(total(suggestions(fgB))).isEqualByComparingTo("80");
        assertThat(total(suggestions(rawB))).isEqualByComparingTo("80");
        assertThat(suggestions(fgA).get(0).at("/qty").decimalValue()).isEqualByComparingTo("120");

        // 全量运算：全部重新计算
        runMrp();
        assertThat(suggestions(fgA).get(0).at("/qty").decimalValue()).isEqualByComparingTo("100");
    }

    private static java.math.BigDecimal total(List<JsonNode> list) {
        return list.stream().map(x -> x.at("/qty").decimalValue()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    @Test
    void routingLeadTime() throws Exception {
        String wc = workCenter(8, 1);
        String fg = fg("工艺提前期成品", Map.of("leadTimeDays", 10));
        String comp = raw("工艺提前期料", Map.of("leadTimeDays", 1));
        bom(fg, List.of(bomLine(comp, 1)));
        routing(fg, new Object[]{10, "组装", wc, 288});
        LocalDate due = LocalDate.now().plusDays(60);
        salesOrder(customer(), List.of(soLine(fg, "300", due)));

        runMrp();
        assertThat(suggestions(fg).get(0).at("/releaseDate").asText()).isEqualTo(due.minusDays(10).toString());

        // 按工艺：300 件 × 288 秒 = 24 小时，日产能 8 小时 → 3 天
        setParam("pmc.lead-time.basis", "ROUTING");
        try {
            runMrp();
            JsonNode s = suggestions(fg).get(0);
            assertThat(s.at("/releaseDate").asText()).isEqualTo(due.minusDays(3).toString());
            assertThat(suggestions(comp).get(0).at("/requiredDate").asText()).isEqualTo(due.minusDays(3).toString());
        } finally {
            resetParam("pmc.lead-time.basis");
        }
    }
}
