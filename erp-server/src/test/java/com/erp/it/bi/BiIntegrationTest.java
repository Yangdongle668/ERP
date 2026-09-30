package com.erp.it.bi;

import com.erp.it.shipping.ShippingTestSupport;
import com.erp.module.bi.service.ai.AiAnomalyService;
import com.erp.module.bi.service.ai.LlmAdapter.LlmResult;
import com.erp.module.bi.service.ai.LlmAdapter.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** BI / AI（需求 13）：汇总与校对、通用查询与数据范围、驾驶舱、AI 问数 / 脱敏 / 异常 / 周报 */
class BiIntegrationTest extends ShippingTestSupport {

    static final String PASSWORD = "Passw0rd!2026";
    private static final AtomicLong AGG_ID = new AtomicLong(7_700_000_000L);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ItBiConfig.ScriptedLlm llm;
    @Autowired
    AiAnomalyService anomalyService;

    @BeforeEach
    void resetLlm() {
        llm.reset();
    }

    @AfterEach
    void resetParams() throws Exception {
        for (String k : List.of("ai.enabled", "ai.api-key", "ai.mask-sensitive", "ai.daily-quota-per-user")) resetParam(k);
    }

    // ==================== 辅助 ====================

    record User(Long id, String token) {
    }

    private User user(String dataScope, List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String roleId = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", dataScope, "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + roleId + "/permissions", admin, Map.of("permissions", permissions)));
        String username = "bi" + uniq();
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("realName", "分析" + username);
        u.put("deptId", "100");
        u.put("roleIds", List.of(roleId));
        u.put("password", PASSWORD);
        u.put("mustChangePassword", false);
        Long id = Long.valueOf(ok(doPost("/api/system/users", admin, u)).at("/id").asText());
        return new User(id, login(username, PASSWORD));
    }

    /** 直接写入一行销售日汇总（隔离的历史日期，不受增量 / 校对重算影响） */
    private void salesAgg(LocalDate date, long customerId, Long materialId, Long ownerId, String ship, String cost, String order) {
        jdbc.update("INSERT INTO bi_agg_sales_daily (id, stat_date, period, customer_id, material_id, owner_id, dept_id, order_amount, ship_amount, ship_qty, "
                        + "ship_cost, costed_ship_amount, return_amount, ship_line_count, on_time_line_count, receipt_amount, version, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, 1, ?, ?, 0, 0, 0, 0, 0, ?, ?, 0)",
                AGG_ID.incrementAndGet(), date, date.toString().substring(0, 7).replace("-", ""), customerId, materialId, ownerId, new BigDecimal(order),
                new BigDecimal(ship), new BigDecimal(cost), cost.equals("0") ? BigDecimal.ZERO : new BigDecimal(ship), LocalDateTime.now(), LocalDateTime.now());
    }

    private JsonNode query(String token, Map<String, Object> body) throws Exception {
        return doPost("/api/bi/query", token, body);
    }

    private JsonNode runJob(String code) throws Exception {
        JsonNode j = ok(doPost("/api/bi/etl/jobs/" + code + "/run", admin, null));
        assertThat(j.at("/lastResult").asText()).as(j.at("/lastMessage").asText()).isEqualTo("SUCCESS");
        return j;
    }

    private BigDecimal kpi(JsonNode dash, String code) {
        for (JsonNode k : dash.at("/kpis")) {
            if (code.equals(k.at("/code").asText())) return k.at("/value").isNull() ? BigDecimal.ZERO : k.at("/value").decimalValue();
        }
        return null;
    }

    private void enableAi() throws Exception {
        setParam("ai.enabled", "true");
        setParam("ai.api-key", "sk-test-abcd1234");
    }

    private String conversation(String token) throws Exception {
        return ok(doPost("/api/bi/ai/conversations", token, Map.of())).at("/id").asText();
    }

    // ==================== 汇总、校对、查询 ====================

    /** BI-DATA-T01 出货确认后增量更新，驾驶舱本月出货额增加；BI-DATA-T02 全量校对修正被改坏的汇总行 */
    @Test
    void incrementalAndReconcile() throws Exception {
        runJob("INCREMENTAL");
        BigDecimal before = kpi(ok(doGet("/api/bi/dashboard?period=THIS_MONTH", admin)), "sales_ship_amount");

        // 出货：100 × 113（含税 13%）→ 不含税 10,000
        String m = fg("BI成品", Map.of());
        stock(m, "100", null);
        String c = customer("BI客户", false);
        String o = approvedOrder(c, List.of(orderLine(m, "100", "113", null)));
        String n = notice(c, orderLineId(o, 0), "100");
        submitNotice(n);
        pickAsSuggested(n);
        String nl = noticeDetail(n).at("/lines/0/id").asText();
        ok(batchPack(n, nl, null, "50", null));
        ok(doPost("/api/shipping/notices/" + n + "/pack-complete", admin, Map.of()));
        String s = ok(doPost("/api/shipping/notices/" + n + "/shipments", admin, Map.of())).asText();
        submitAndConfirm(s);

        JsonNode job = runJob("INCREMENTAL");
        assertThat(job.at("/lastRows").asInt()).isGreaterThan(0);
        JsonNode dash = ok(doGet("/api/bi/dashboard?period=THIS_MONTH", admin));
        assertThat(kpi(dash, "sales_ship_amount").subtract(before)).isEqualByComparingTo("10000");
        assertThat(dash.at("/dataUpdatedAt").isNull()).isFalse();
        // 客户维度可查到这笔出货
        JsonNode r = ok(query(admin, Map.of("metrics", List.of("sales_ship_amount", "sales_order_amount"), "dimensions", List.of("customer"),
                "filters", Map.of("customer", List.of(c)))));
        assertThat(r.at("/rows/0/sales_ship_amount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(r.at("/rows/0/sales_order_amount").decimalValue()).isEqualByComparingTo("11300");
        assertThat(r.at("/rows/0/customer_label").asText()).startsWith("BI客户");

        // BI-DATA-T02：先校对一次使数据一致，再改坏一行
        runJob("RECON_SALES");
        assertThat(runJob("RECON_SALES").at("/lastDiffRows").asInt()).isZero();
        jdbc.update("UPDATE bi_agg_sales_daily SET ship_amount = ship_amount + 1 WHERE customer_id = ?", Long.valueOf(c));
        JsonNode fixed = runJob("RECON_SALES");
        assertThat(fixed.at("/lastDiffRows").asInt()).isEqualTo(1);
        r = ok(query(admin, Map.of("metrics", List.of("sales_ship_amount"), "filters", Map.of("customer", List.of(c)))));
        assertThat(r.at("/rows/0/sales_ship_amount").decimalValue()).isEqualByComparingTo("10000");
        // 任务列表与日志
        JsonNode jobs = ok(doGet("/api/bi/etl/jobs", admin));
        assertThat(jobs.size()).isEqualTo(8);
        assertThat(ok(doGet("/api/bi/etl/logs?jobCode=RECON_SALES", admin)).get(0).at("/diffRows").asInt()).isEqualTo(1);
    }

    /** BI-DATA-T03 / BI-TOP-T03：业务员只看到自己的数据；指标权限与维度白名单 */
    @Test
    void queryDataScopeAndPermissions() throws Exception {
        User sales = user("SELF", List.of("bi:sales:view"));
        LocalDate d = LocalDate.of(2020, 1, 10);
        salesAgg(d, 990_001L, null, sales.id(), "100", "0", "150");
        salesAgg(d, 990_002L, null, 1L, "200", "0", "250");
        Map<String, Object> body = Map.of("metrics", List.of("sales_order_amount", "sales_ship_amount"), "dimensions", List.of("customer"),
                "from", "2020-01-01", "to", "2020-01-31");
        JsonNode mine = ok(query(sales.token(), body));
        assertThat(mine.at("/rows").size()).isEqualTo(1);
        assertThat(mine.at("/rows/0/customer").asText()).isEqualTo("990001");
        assertThat(mine.at("/rows/0/sales_order_amount").decimalValue()).isEqualByComparingTo("150");
        JsonNode all = ok(query(admin, Map.of("metrics", List.of("sales_order_amount"), "from", "2020-01-01", "to", "2020-01-31")));
        assertThat(all.at("/rows/0/sales_order_amount").decimalValue()).isEqualByComparingTo("400");
        assertThat(all.at("/columns/0/label").asText()).isEqualTo("接单额");

        // 日期维度 + 粒度
        JsonNode trend = ok(query(admin, Map.of("metrics", List.of("sales_ship_amount"), "dimensions", List.of("date"), "granularity", "month",
                "from", "2020-01-01", "to", "2020-02-29")));
        assertThat(trend.at("/rows/0/date").asText()).isEqualTo("2020-01");
        assertThat(trend.at("/rows/0/sales_ship_amount").decimalValue()).isEqualByComparingTo("300");

        assertError(query(sales.token(), Map.of("metrics", List.of("gross_margin"))), "没有指标「毛利率」的查看权限");
        assertError(query(sales.token(), Map.of("metrics", List.of("sales_ship_amount"), "dimensions", List.of("supplier"))),
                "指标「出货额」不支持维度「供应商」");
        assertError(query(sales.token(), Map.of("metrics", List.of("nope"))), "指标「nope」不存在");
        assertError(query(sales.token(), Map.of("metrics", List.of("sales_ship_amount"), "from", "2020-02-01", "to", "2020-01-01")),
                "查询参数不正确：开始日期晚于结束日期");
        // 专题页配置只含有权限的指标
        JsonNode cfg = ok(doGet("/api/bi/pages/sales/config", sales.token()));
        assertThat(cfg.at("/metrics").findValuesAsText("code")).contains("sales_ship_amount").doesNotContain("gross_margin");
        assertError(doGet("/api/bi/metrics", sales.token()), "没有该操作权限");
    }

    /** BI-DSH-T02：无财务权限不显示毛利、应收卡片；指标库说明修改 */
    @Test
    void dashboardFinanceCardsAndMetricLibrary() throws Exception {
        User boss = user("ALL", List.of("bi:dashboard:view"));
        JsonNode d = ok(doGet("/api/bi/dashboard?period=LAST_MONTH&compare=YOY", boss.token()));
        assertThat(d.at("/finance").asBoolean()).isFalse();
        assertThat(d.at("/kpis").findValuesAsText("code")).contains("sales_ship_amount", "inventory_amount").doesNotContain("gross_profit", "ar_balance");
        assertThat(d.at("/trend").size()).isEqualTo(12);
        YearMonth last = YearMonth.now().minusMonths(1);
        assertThat(d.at("/from").asText()).isEqualTo(last.atDay(1).toString());
        assertThat(d.at("/compareFrom").asText()).isEqualTo(last.atDay(1).minusYears(1).toString());
        JsonNode full = ok(doGet("/api/bi/dashboard", admin));
        assertThat(full.at("/kpis").findValuesAsText("code")).contains("gross_profit", "ar_balance");

        JsonNode metric = ok(doPut("/api/bi/metrics/sales_ship_amount", admin, Map.of("displayName", "出货额（不含税）", "ownerName", "财务部")));
        assertThat(metric.at("/name").asText()).isEqualTo("出货额（不含税）");
        assertThat(ok(doGet("/api/bi/metrics", admin)).findValuesAsText("code")).contains("inventory_turnover_days", "dso");
        ok(doPut("/api/bi/metrics/sales_ship_amount", admin, Map.of()));
        assertError(doPut("/api/bi/metrics/nope", admin, Map.of()), "指标「nope」不存在");
    }

    // ==================== AI ====================

    /** AI-R01 未启用；AI-T01 问数回答与驾驶舱一致并标注口径；反馈、日志、额度 */
    @Test
    void aiQuestionAnswer() throws Exception {
        String conv = conversation(admin);
        assertError(doPost("/api/bi/ai/conversations/" + conv + "/messages", admin, Map.of("question", "上个月出货额是多少")),
                "AI 分析未启用，请联系管理员配置");
        enableAi();
        YearMonth last = YearMonth.now().minusMonths(1);
        llm.script.add((req, tools) -> {
            ToolOutcome o = tools.handle("bi_query", Map.of("metrics", List.of("sales_ship_amount"), "from", last.atDay(1).toString(),
                    "to", last.atEndOfMonth().toString()));
            return new LlmResult("上个月出货额见下表。\n口径：指标 出货额；期间 " + last.atDay(1) + " ~ " + last.atEndOfMonth() + "；筛选 无\n图表：table",
                    o.error() ? "error" : "end_turn", 100, 50);
        });
        JsonNode msg = ok(doPost("/api/bi/ai/conversations/" + conv + "/messages", admin, Map.of("question", "上个月出货额是多少")));
        assertThat(msg.at("/content").asText()).contains("口径：指标 出货额").doesNotContain("图表：");
        assertThat(msg.at("/chart").asText()).isEqualTo("table");
        BigDecimal answered = msg.at("/results/0/rows/0/sales_ship_amount").decimalValue();
        BigDecimal dashboard = kpi(ok(doGet("/api/bi/dashboard?period=LAST_MONTH", admin)), "sales_ship_amount");
        assertThat(answered).isEqualByComparingTo(dashboard);
        assertThat(msg.at("/results/0/metrics/0/name").asText()).isEqualTo("出货额");
        // 系统提示包含日期与指标清单，工具只有 bi_query
        assertThat(llm.requests.get(0).system()).contains(LocalDate.now().toString()).contains("sales_ship_amount");
        assertThat(llm.requests.get(0).tools()).hasSize(1);
        assertThat(llm.requests.get(0).tools().get(0).name()).isEqualTo("bi_query");
        // 会话标题取第一个问题
        assertThat(ok(doGet("/api/bi/ai/conversations", admin)).findValuesAsText("title")).contains("上个月出货额是多少");
        assertThat(ok(doGet("/api/bi/ai/conversations/" + conv + "/messages", admin)).size()).isEqualTo(2);

        String mid = msg.at("/id").asText();
        ok(doPost("/api/bi/ai/messages/" + mid + "/feedback", admin, Map.of("feedback", "UP", "remark", "准确")));
        JsonNode logs = ok(doGet("/api/bi/ai/logs?pageSize=5", admin)).at("/list");
        assertThat(logs.get(0).at("/question").asText()).isEqualTo("上个月出货额是多少");
        assertThat(logs.get(0).at("/feedback").asText()).isEqualTo("UP");
        assertThat(logs.get(0).at("/toolCalls").asText()).contains("sales_ship_amount");
        assertThat(ok(doGet("/api/bi/ai/usage", admin)).get(0).at("/questions").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(ok(doGet("/api/bi/ai/settings", admin)).at("/maskedKey").asText()).isEqualTo("****1234");

        // AI-R05 模型调用失败
        llm.script.add((req, tools) -> {
            throw new IllegalStateException("timeout");
        });
        assertError(doPost("/api/bi/ai/conversations/" + conv + "/messages", admin, Map.of("question", "再问")), "AI 服务暂时不可用，请稍后重试");
        assertThat(ok(doGet("/api/bi/ai/logs?pageSize=5&success=false", admin)).at("/list/0/error").asText()).contains("timeout");

        // AI-R04 额度
        User u = user("ALL", List.of("ai:query:use", "bi:sales:view"));
        setParam("ai.daily-quota-per-user", "1");
        String c2 = conversation(u.token());
        ok(doPost("/api/bi/ai/conversations/" + c2 + "/messages", u.token(), Map.of("question", "你好")));
        assertError(doPost("/api/bi/ai/conversations/" + c2 + "/messages", u.token(), Map.of("question", "再问一次")), "今日提问次数已达上限 1");
        // 别人的会话不可见
        assertError(doGet("/api/bi/ai/conversations/" + conv + "/messages", u.token()), "会话不存在");

    }

    /** AI-T02 业务员问全公司毛利率：指标清单不含毛利率，工具调用被拒绝，不给出数字 */
    @Test
    void aiWithoutFinancePermission() throws Exception {
        enableAi();
        User sales = user("SELF", List.of("ai:query:use", "bi:sales:view"));
        llm.script.add((req, tools) -> {
            ToolOutcome o = tools.handle("bi_query", Map.of("metrics", List.of("gross_margin"), "from", "2026-01-01", "to", "2026-01-31"));
            return new LlmResult(o.error() ? "当前账号没有「毛利率」指标的查看权限，无法回答。" : "毛利率为 20%", "end_turn", 10, 5);
        });
        String conv = conversation(sales.token());
        JsonNode msg = ok(doPost("/api/bi/ai/conversations/" + conv + "/messages", sales.token(), Map.of("question", "全公司毛利率是多少")));
        assertThat(msg.at("/content").asText()).contains("没有「毛利率」指标的查看权限").doesNotContainPattern("\\d");
        assertThat(llm.outcomes.get(0).error()).isTrue();
        assertThat(llm.outcomes.get(0).content()).isEqualTo("没有指标「毛利率」的查看权限");
        assertThat(llm.requests.get(0).system()).doesNotContain("gross_margin").doesNotContain("gross_profit");
        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) ((Map<String, Object>) ((Map<String, Object>) llm.requests.get(0).tools().get(0).properties().get("metrics"))
                .get("items")).get("enum");
        assertThat(allowed).contains("sales_ship_amount").doesNotContain("gross_margin");
    }

    /** AI-T03 脱敏：发给模型的只有排名 / 占比，页面表格显示真实毛利额 */
    @Test
    void aiMasksSensitiveValues() throws Exception {
        enableAi();
        LocalDate d = LocalDate.of(2020, 3, 5);
        salesAgg(d, 990_101L, 880_001L, 1L, "1000", "623.45", "0");
        salesAgg(d, 990_101L, 880_002L, 1L, "500", "400", "0");
        llm.script.add((req, tools) -> {
            ToolOutcome o = tools.handle("bi_query", Map.of("metrics", List.of("gross_profit", "gross_margin"), "dimensions", List.of("material"),
                    "from", "2020-03-01", "to", "2020-03-31", "sort", "gross_profit", "order", "desc"));
            return new LlmResult("毛利排名第一的产品毛利率 37.66%。\n图表：bar", "end_turn", 10, 5);
        });
        String conv = conversation(admin);
        JsonNode msg = ok(doPost("/api/bi/ai/conversations/" + conv + "/messages", admin, Map.of("question", "3 月产品毛利排名")));
        String sent = llm.outcomes.get(0).content();
        assertThat(sent).contains("毛利额_排名").contains("毛利额_占比%").contains("37.66").doesNotContain("376.55").doesNotContain("100.00");
        assertThat(sent).contains("脱敏");
        assertThat(msg.at("/results/0/rows/0/gross_profit").decimalValue()).isEqualByComparingTo("376.55");
        assertThat(msg.at("/chart").asText()).isEqualTo("bar");

        // 关闭脱敏后发送真实数值
        setParam("ai.mask-sensitive", "false");
        llm.reset();
        llm.script.add((req, tools) -> {
            tools.handle("bi_query", Map.of("metrics", List.of("gross_profit"), "from", "2020-03-01", "to", "2020-03-31"));
            return new LlmResult("毛利 476.55", "end_turn", 10, 5);
        });
        ok(doPost("/api/bi/ai/conversations/" + conv + "/messages", admin, Map.of("question", "3 月毛利额")));
        assertThat(llm.outcomes.get(0).content()).contains("476.55");
    }

    /** AI-T04 客户出货环比下降 60% → 异常页签出现该客户并附解读；周报生成 */
    @Test
    void anomalyAndWeeklyReport() throws Exception {
        enableAi();
        LocalDate today = LocalDate.now();
        LocalDate day = today.minusDays(1);
        long customer = 990_000_000L + AGG_ID.incrementAndGet() % 100_000;
        salesAgg(day.minusDays(45), customer, null, 1L, "1000", "0", "0");
        salesAgg(day.minusDays(10), customer, null, 1L, "400", "0", "0");
        int n = anomalyService.detect(today);
        assertThat(n).isGreaterThanOrEqualTo(1);
        JsonNode list = ok(doGet("/api/bi/ai/anomalies", admin));
        JsonNode found = null;
        for (JsonNode a : list) {
            if (String.valueOf(customer).equals(a.at("/dimValue").asText()) && "sales_ship_amount".equals(a.at("/metricCode").asText())) found = a;
        }
        assertThat(found).isNotNull();
        assertThat(found.at("/changePct").decimalValue()).isEqualByComparingTo("-60");
        assertThat(found.at("/level").asText()).isEqualTo("WARNING");
        assertThat(found.at("/method").asText()).isEqualTo("MOM");
        assertThat(found.at("/explanation").asText()).startsWith("模拟解释：");
        // 重复检测覆盖当日结果
        anomalyService.detect(today);
        long same = 0;
        for (JsonNode a : ok(doGet("/api/bi/ai/anomalies", admin))) {
            if (String.valueOf(customer).equals(a.at("/dimValue").asText()) && "sales_ship_amount".equals(a.at("/metricCode").asText())) same++;
        }
        assertThat(same).isEqualTo(1);

        JsonNode report = ok(doPost("/api/bi/ai/weekly-reports/generate", admin, null));
        assertThat(report.at("/title").asText()).startsWith("经营周报");
        assertThat(report.at("/summary").asText()).isEqualTo("模拟总结");
        assertThat(ok(doGet("/api/bi/ai/weekly-reports", admin)).size()).isGreaterThanOrEqualTo(1);
        User self = user("SELF", List.of("bi:dashboard:view"));
        assertThat(ok(doGet("/api/bi/ai/weekly-reports", self.token())).size()).isZero();
    }
}
