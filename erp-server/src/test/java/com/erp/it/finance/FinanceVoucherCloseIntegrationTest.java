package com.erp.it.finance;

import com.erp.module.inventory.api.period.FinancePeriodChecker;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 凭证（12-06）、月结（12-09）、分析报表（12-08 P1） */
class FinanceVoucherCloseIntegrationTest extends FinanceTestSupport {

    static final String PERIOD = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

    @Autowired
    List<FinancePeriodChecker> periodCheckers;

    /** FIN-VCH-T01：出货应收按类型汇总生成 1 张凭证（借应收按客户辅助 / 贷收入、销项税） */
    @Test
    void generateSummaryVoucher() throws Exception {
        String c = customer("凭证客户", false);
        String m = fg("凭证成品", Map.of());
        stock(m, "50", null);
        String order = approvedOrder(c, List.of(orderLine(m, "10", "113", LocalDate.now().plusDays(5))));
        JsonNode ar = arOfShipment(shipOrder(c, order, 0, "10"));
        if (!"CONFIRMED".equals(ar.at("/header/status").asText())) ok(doPost("/api/finance/receivables/" + ar.at("/header/id").asText() + "/confirm", admin, null));
        String arId = ar.at("/header/id").asText();
        BigDecimal total = ar(arId).at("/header/totalAmountBase").decimalValue();

        JsonNode pending = ok(doGet("/api/finance/vouchers/pending?period=" + PERIOD, admin));
        assertThat(pending.findValuesAsText("bizType")).contains("SALES_AR", "RECEIPT", "STOCK_OUT_SALES_COST");
        JsonNode r = ok(doPost("/api/finance/vouchers/generate", admin, Map.of("period", PERIOD, "bizTypes", List.of("SALES_AR"), "mode", "SUMMARY")));
        assertThat(r.at("/voucherCount").asInt()).as("%s", r).isEqualTo(1);
        assertThat(r.at("/docCount").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode v = ok(doGet("/api/finance/vouchers/" + r.at("/voucherIds/0").asText(), admin));
        assertThat(v.at("/header/voucherNo").asText()).startsWith("记-" + PERIOD + "-");
        assertThat(v.at("/header/totalDebit").decimalValue()).isEqualByComparingTo(v.at("/header/totalCredit").decimalValue());
        JsonNode mine = null;
        for (JsonNode l : v.at("/lines")) {
            if ("1122".equals(l.at("/accountCode").asText()) && c.equals(l.at("/auxCustomerId").asText())) mine = l;
        }
        assertThat(mine).as("应收按客户辅助核算").isNotNull();
        assertThat(mine.at("/debit").decimalValue()).isEqualByComparingTo(total);
        assertThat(v.at("/lines").findValuesAsText("accountCode")).contains("6001", "222101");
        assertThat(ar(arId).at("/voucherId").asText()).isEqualTo(v.at("/header/id").asText());
        // 已生成凭证的单据不再出现在待生成中；再次生成没有单据
        assertError(doPost("/api/finance/vouchers/generate", admin, Map.of("period", PERIOD, "bizTypes", List.of("SALES_AR"), "mode", "SUMMARY")),
                "没有可生成凭证的单据");
        // 删除凭证后单据可重新生成
        ok(doDelete("/api/finance/vouchers/" + v.at("/header/id").asText(), admin));
        assertThat(ar(arId).at("/voucherId").isNull() || ar(arId).at("/voucherId").isMissingNode()).isTrue();
        JsonNode again = ok(doPost("/api/finance/vouchers/generate", admin, Map.of("period", PERIOD, "bizTypes", List.of("SALES_AR"), "mode", "PER_DOC")));
        assertThat(again.at("/docCount").asInt()).isGreaterThanOrEqualTo(1);
        // 映射测试：选择单据预览
        JsonNode preview = ok(doGet("/api/finance/account-mappings/preview?bizType=SALES_AR&docId=" + arId, admin));
        assertThat(preview.at("/balanced").asBoolean()).isTrue();
        assertThat(preview.at("/totalDebit").decimalValue()).isEqualByComparingTo(total);
    }

    /** FIN-VCH-T02 借贷不平衡、FIN-VCH-T03 审核人不能是制单人；过账后进入科目余额表、明细账、损益 */
    @Test
    void manualVoucherAuditPost() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("voucherDate", LocalDate.now().toString());
        body.put("attachmentCount", 2);
        body.put("lines", List.of(Map.of("summary", "办公费", "accountCode", "6602", "debit", "100"),
                Map.of("summary", "办公费", "accountCode", "1001", "credit", "99")));
        String id = ok(doPost("/api/finance/vouchers", admin, body)).asText();
        assertError(doPost("/api/finance/vouchers/" + id + "/audit", admin, null), "借贷不平衡，差额 1.00");
        assertError(doPost("/api/finance/vouchers", admin, Map.of("voucherDate", LocalDate.now().toString(), "lines",
                List.of(Map.of("accountCode", "6602", "debit", "1", "credit", "1"), Map.of("accountCode", "1001", "credit", "1")))), "第 1 行借方、贷方金额必须且只能填写一个");
        body.put("lines", List.of(Map.of("summary", "办公费", "accountCode", "6602", "debit", "100"),
                Map.of("summary", "办公费", "accountCode", "1001", "credit", "100")));
        ok(doPut("/api/finance/vouchers/" + id, admin, body));
        assertError(doPost("/api/finance/vouchers/" + id + "/audit", admin, null), "审核人不能与制单人相同");

        String auditor = accountant(List.of("fin:voucher:query", "fin:voucher:audit", "fin:voucher:post", "fin:voucher:unpost", "fin:report:query"));
        ok(doPost("/api/finance/vouchers/" + id + "/audit", auditor, null));
        assertError(doDelete("/api/finance/vouchers/" + id, admin), "当前状态【已审核】不能删除");
        JsonNode batch = ok(doPost("/api/finance/vouchers/batch-post", auditor, Map.of("ids", List.of(id))));
        assertThat(batch.at("/success").asInt()).isEqualTo(1);
        JsonNode v = ok(doGet("/api/finance/vouchers/" + id, admin));
        assertThat(v.at("/header/status").asText()).isEqualTo("POSTED");
        assertThat(v.at("/header/auditorName").asText()).isNotBlank();

        JsonNode balance = ok(doGet("/api/finance/reports/account-balance?periodFrom=" + PERIOD + "&periodTo=" + PERIOD, auditor));
        JsonNode row = null;
        for (JsonNode r : balance.at("/rows")) if ("6602".equals(r.at("/accountCode").asText())) row = r;
        assertThat(row).isNotNull();
        assertThat(row.at("/debit").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("100"));
        JsonNode ledger = ok(doGet("/api/finance/reports/ledger?accountCode=6602&periodFrom=" + PERIOD + "&periodTo=" + PERIOD, auditor));
        assertThat(ledger.at("/lines").findValuesAsText("voucherNo")).contains(v.at("/header/voucherNo").asText());
        JsonNode pl = ok(doGet("/api/finance/reports/profit-loss?period=" + PERIOD, auditor));
        BigDecimal admExp = null;
        for (JsonNode i : pl.at("/items")) if ("EXP_6602".equals(i.at("/key").asText())) admExp = i.at("/month").decimalValue();
        assertThat(admExp).isGreaterThanOrEqualTo(new BigDecimal("100"));

        // 反过账 → 反审核 → 删除
        ok(doPost("/api/finance/vouchers/" + id + "/unpost", auditor, null));
        ok(doPost("/api/finance/vouchers/" + id + "/unaudit", auditor, null));
        ok(doDelete("/api/finance/vouchers/" + id, admin));
        ok(doPost("/api/finance/vouchers/renumber?period=" + PERIOD, admin, null));
    }

    /** FIN-CLS-T02：应收 1,000 USD 账面 7,120，月末汇率 7.15 → 差异 +30.00，生成重估凭证 */
    @Test
    void fxRevaluation() throws Exception {
        String c = customer("重估客户", true);
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", c);
        body.put("currency", "USD");
        body.put("exchangeRate", "7.12");
        body.put("bizDate", LocalDate.now().toString());
        body.put("description", "样品费");
        body.put("lines", List.of(Map.of("description", "样品费", "totalAmount", "1000", "taxRate", "0")));
        String arId = ok(doPost("/api/finance/receivables/other", admin, body)).asText();
        assertThat(ok(doPost("/api/finance/receivables/" + arId + "/submit", admin, null)).at("/status").asText()).isEqualTo("CONFIRMED");

        String monthEnd = YearMonth.now().atEndOfMonth().toString();
        doPost("/api/system/exchange-rates", admin, Map.of("currency", "USD", "rateType", "MONTH_END", "effectiveDate", monthEnd, "rate", "7.15"));
        JsonNode preview = ok(doGet("/api/finance/close/" + PERIOD + "/fx-revaluation", admin));
        for (JsonNode cur : preview.at("/missingRates")) {
            doPost("/api/system/exchange-rates", admin, Map.of("currency", cur.asText(), "rateType", "MONTH_END", "effectiveDate", monthEnd, "rate", "1"));
        }
        JsonNode r = ok(doPost("/api/finance/close/" + PERIOD + "/fx-revaluation", admin, null));
        assertThat(r.at("/done").asBoolean()).isTrue();
        JsonNode row = null;
        for (JsonNode x : r.at("/rows")) if ("AR".equals(x.at("/docType").asText()) && arId.equals(x.at("/docId").asText())) row = x;
        assertThat(row).as("%s", r).isNotNull();
        assertThat(row.at("/bookBase").decimalValue()).isEqualByComparingTo("7120");
        assertThat(row.at("/periodEndRate").decimalValue()).isEqualByComparingTo("7.15");
        assertThat(row.at("/diff").decimalValue()).isEqualByComparingTo("30.00");
        JsonNode v = ok(doGet("/api/finance/vouchers/" + r.at("/voucherId").asText(), admin));
        JsonNode line = null;
        for (JsonNode l : v.at("/lines")) if ("1122".equals(l.at("/accountCode").asText()) && c.equals(l.at("/auxCustomerId").asText())) line = l;
        assertThat(line).isNotNull();
        assertThat(line.at("/debit").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(v.at("/lines").findValuesAsText("accountCode")).contains("660301");
        assertThat(v.at("/header/totalDebit").decimalValue()).isEqualByComparingTo(v.at("/header/totalCredit").decimalValue());
        // 重新重估：原草稿凭证删除后重新生成
        JsonNode again = ok(doPost("/api/finance/close/" + PERIOD + "/fx-revaluation", admin, null));
        assertThat(again.at("/voucherId").asText()).isNotEqualTo(r.at("/voucherId").asText());
        assertError(doGet("/api/finance/vouchers/" + r.at("/voucherId").asText(), admin), "凭证不存在");
    }

    /** FIN-CLS-T01 检查阻止项、FIN-CLS-R01 顺序、FIN-CLS-T03 已结账期间阻止反确认、反结账 */
    @Test
    void checkCloseReopen() throws Exception {
        // 本期：成本未锁定 → 阻止项
        JsonNode check = ok(doGet("/api/finance/close/" + PERIOD + "/check", admin));
        assertThat(check.at("/passed").asBoolean()).isFalse();
        JsonNode cost = null;
        for (JsonNode i : check.at("/items")) if ("COST".equals(i.at("/key").asText())) cost = i;
        assertThat(cost.at("/passed").asBoolean()).isFalse();
        assertThat(cost.at("/message").asText()).isEqualTo("成本未锁定");
        JsonNode blocked = doPost("/api/finance/close/" + PERIOD + "/close", admin, null);
        assertThat(blocked.at("/msg").asText()).startsWith("结账检查未通过：").contains("成本未锁定");

        // 早于库存启用的历史期间：成本计算（无业务）→ 锁定 → 结账
        String p1 = "2010" + String.format("%02d", 1 + (int) (System.nanoTime() % 5));
        String p0 = YearMonth.parse(p1, DateTimeFormatter.ofPattern("yyyyMM")).minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
        String p2 = YearMonth.parse(p1, DateTimeFormatter.ofPattern("yyyyMM")).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDate day = YearMonth.parse(p1, DateTimeFormatter.ofPattern("yyyyMM")).atDay(15);
        String c = customer("结账客户", false);
        String arId = otherAr(c, "CNY", day, day.plusDays(30), "500");
        assertError(doPost("/api/finance/cost/lock?period=" + p1, admin, null), "期间 " + p1 + " 尚未成功计算成本");
        JsonNode run = ok(doPost("/api/finance/cost/runs?period=" + p1, admin, null));
        assertThat(run.at("/status").asText()).as("%s", run).isEqualTo("SUCCESS");
        ok(doPost("/api/finance/cost/lock?period=" + p1, admin, null));
        assertError(doPost("/api/finance/cost/runs?period=" + p1, admin, null), "期间 " + p1 + " 成本已锁定");
        // 上期若为已开启未结账，需按顺序结账
        JsonNode periods = ok(doGet("/api/finance/close/periods?year=2010", admin));
        boolean prevOpen = false;
        for (JsonNode p : periods) if (p0.equals(p.at("/period").asText()) && "OPEN".equals(p.at("/status").asText())) prevOpen = true;
        if (prevOpen) assertError(doPost("/api/finance/close/" + p1 + "/close", admin, null), "请先结账 " + p0);
        else {
            JsonNode ck = ok(doGet("/api/finance/close/" + p1 + "/check", admin));
            assertThat(ck.at("/passed").asBoolean()).as("%s", ck).isTrue();
            ok(doPost("/api/finance/close/" + p1 + "/close", admin, null));
            try {
                assertThat(periodCheckers.stream().anyMatch(ch -> ch.isClosed(p1))).as("仓库反结账校验").isTrue();
                assertError(doPost("/api/finance/receivables/" + arId + "/unconfirm", admin, Map.of("reason", "测试")), "会计期间 " + p1 + " 已结账");
                assertError(doPost("/api/finance/cost/unlock?period=" + p1, admin, null), "期间 " + p1 + " 已结账，请先反结账再解锁成本");
                assertError(doPost("/api/finance/close/" + p1 + "/reopen", admin, Map.of("reason", " ")), "请填写反结账原因");
                boolean found = false;
                for (JsonNode p : ok(doGet("/api/finance/close/periods?year=2010", admin))) {
                    if (p1.equals(p.at("/period").asText())) {
                        found = true;
                        assertThat(p.at("/status").asText()).isEqualTo("CLOSED");
                        assertThat(p.at("/canReopen").asBoolean()).isTrue();
                    }
                    if (p2.equals(p.at("/period").asText())) assertThat(p.at("/status").asText()).isEqualTo("OPEN");
                }
                assertThat(found).isTrue();
            } finally {
                ok(doPost("/api/finance/close/" + p1 + "/reopen", admin, Map.of("reason", "测试反结账")));
            }
            assertThat(periodCheckers.stream().anyMatch(ch -> ch.isClosed(p1))).isFalse();
            // 反结账后成本已解锁，可重新计算
            assertThat(ok(doPost("/api/finance/cost/runs?period=" + p1, admin, null)).at("/status").asText()).isEqualTo("SUCCESS");
        }
    }

    /** 收付款日报、毛利（成本未计算显示“未计算”，FIN-RPT-T03） */
    @Test
    void cashDailyAndMargin() throws Exception {
        String c = customer("日报客户", false);
        confirmedReceipt(c, "SALES", bankCny, "800", "0", null, null);
        JsonNode daily = ok(doGet("/api/finance/reports/cash-daily?bankAccountId=" + bankCny, admin));
        JsonNode today = null;
        for (JsonNode r : daily.at("/rows")) if (LocalDate.now().toString().equals(r.at("/date").asText())) today = r;
        assertThat(today).isNotNull();
        assertThat(today.at("/income").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("800"));
        assertThat(today.at("/closing").decimalValue()).isEqualByComparingTo(
                today.at("/opening").decimalValue().add(today.at("/income").decimalValue()).subtract(today.at("/expense").decimalValue()));

        String m = fg("毛利成品", Map.of());
        stock(m, "20", null);
        String order = approvedOrder(c, List.of(orderLine(m, "5", "113", LocalDate.now().plusDays(5))));
        JsonNode ar = arOfShipment(shipOrder(c, order, 0, "5"));
        if (!"CONFIRMED".equals(ar.at("/header/status").asText())) ok(doPost("/api/finance/receivables/" + ar.at("/header/id").asText() + "/confirm", admin, null));
        JsonNode margin = ok(doGet("/api/finance/reports/order-margin?customerId=" + c, admin));
        assertThat(margin.at("/rows").size()).isEqualTo(1);
        JsonNode row = margin.at("/rows/0");
        assertThat(row.at("/revenue").decimalValue()).isEqualByComparingTo(ar(ar.at("/header/id").asText()).at("/amount").decimalValue());
        if (margin.at("/uncalculatedPeriods").size() == 0) {
            assertThat(row.at("/cost").isNumber()).as("本期成本已计算：%s", row).isTrue();
        } else {
            assertThat(row.at("/cost").isNull() || row.at("/cost").isMissingNode()).as("成本未计算：%s", row).isTrue();
            assertThat(margin.at("/uncalculatedPeriods/0").asText()).isEqualTo(PERIOD);
        }
        JsonNode byCustomer = ok(doGet("/api/finance/reports/customer-margin?customerId=" + c, admin));
        assertThat(byCustomer.at("/rows/0/customerId").asText()).isEqualTo(c);
        assertThat(byCustomer.at("/rows/0/rank").asInt()).isEqualTo(1);
        JsonNode byProduct = ok(doGet("/api/finance/reports/product-margin?materialId=" + m, admin));
        assertThat(byProduct.at("/rows/0/qty").decimalValue()).isEqualByComparingTo("5");
    }

    /** 只有指定权限的会计（数据范围全部） */
    private String accountant(List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String roleId = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "会计" + code, "dataScope", "ALL", "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + roleId + "/permissions", admin, Map.of("permissions", permissions)));
        String username = "acc" + uniq();
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("realName", "会计" + username);
        user.put("deptId", "100");
        user.put("roleIds", List.of(roleId));
        user.put("password", "Passw0rd!2026");
        user.put("mustChangePassword", false);
        ok(doPost("/api/system/users", admin, user));
        return login(username, "Passw0rd!2026");
    }
}
