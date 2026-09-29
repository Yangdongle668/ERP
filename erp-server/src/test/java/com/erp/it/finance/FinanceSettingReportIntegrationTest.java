package com.erp.it.finance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 财务基础设置（12-01）与账龄、对账单（12-08） */
class FinanceSettingReportIntegrationTest extends FinanceTestSupport {

    /** FIN-SET-T01、FIN-SET-R01 ~ R03 */
    @Test
    void accountsAndMappings() throws Exception {
        String code = "1002" + (10 + (int) (System.nanoTime() % 89));
        assertError(doPost("/api/finance/accounts", admin, Map.of("code", "2202" + code.substring(4), "name", "X", "parentCode", "1002")),
                "下级科目编码必须以上级编码「1002」开头");
        String id = ok(doPost("/api/finance/accounts", admin, Map.of("code", code, "name", "中行美元户", "parentCode", "1002",
                "currencyAccounting", true))).asText();
        assertError(doPost("/api/finance/accounts", admin, Map.of("code", code, "name", "重复", "parentCode", "1002")), "编码「" + code + "」已存在");
        JsonNode tree = ok(doGet("/api/finance/accounts", admin));
        JsonNode bankAcc = null;
        for (JsonNode n : tree) if ("1002".equals(n.at("/code").asText())) bankAcc = n;
        assertThat(bankAcc).isNotNull();
        assertThat(bankAcc.at("/leaf").asBoolean()).isFalse();
        JsonNode child = null;
        for (JsonNode n : bankAcc.at("/children")) if (code.equals(n.at("/code").asText())) child = n;
        assertThat(child).isNotNull();
        assertThat(child.at("/accountType").asText()).isEqualTo("ASSET");
        assertThat(child.at("/direction").asText()).isEqualTo("DEBIT");
        assertThat(child.at("/currencyAccounting").asBoolean()).isTrue();
        assertThat(child.at("/level").asInt()).isEqualTo(2);

        // FIN-SET-R02：映射引用的科目必须是启用的末级科目
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("bizType", "SALES_AR_" + uniq());
        mapping.put("entries", List.of(Map.of("direction", "DEBIT", "accountCode", "2221", "amountField", "totalAmount")));
        assertError(doPost("/api/finance/account-mappings", admin, mapping), "科目「2221」不是末级科目");
        // FIN-SET-T02 映射：借 1122 / 贷 6001（不含税）/ 贷 222101（税额）
        mapping.put("entries", List.of(
                Map.of("direction", "DEBIT", "accountCode", "1122", "amountField", "totalAmount", "auxFrom", "CUSTOMER"),
                Map.of("direction", "CREDIT", "accountCode", "6001", "amountField", "amount"),
                Map.of("direction", "CREDIT", "accountCode", "222101", "amountField", "tax")));
        ok(doPost("/api/finance/account-mappings", admin, mapping));
        // FIN-SET-R03：同一业务类型只能有一条默认映射
        assertError(doPost("/api/finance/account-mappings", admin, mapping), "业务类型「" + mapping.get("bizType") + "」已有默认映射");
        mapping.put("matchCondition", "{\"currency\":\"USD\"}");
        mapping.put("conditionDesc", "外币");
        mapping.put("priority", 10);
        ok(doPost("/api/finance/account-mappings", admin, mapping));
        assertThat(ok(doGet("/api/finance/account-mappings?bizType=" + mapping.get("bizType"), admin)).size()).isEqualTo(2);

        // FIN-SET-R01：已使用的末级科目不能新增下级、不能删除
        String bank = ok(doPost("/api/finance/bank-accounts", admin, Map.of("code", "B" + uniq(), "name", "中行美元户", "bankName", "中国银行",
                "accountNo", "6" + uniq(), "currency", "USD", "accountCode", code))).asText();
        assertError(doPost("/api/finance/accounts", admin, Map.of("code", code + "01", "name", "子户", "parentCode", code)), "科目已使用，不能新增下级");
        assertError(doDelete("/api/finance/accounts/" + id, admin), "科目已使用，不能新增下级");
        // 银行账户未被收付款引用 → 可删除；之后科目可删除
        ok(doDelete("/api/finance/bank-accounts/" + bank, admin));
        ok(doDelete("/api/finance/accounts/" + id, admin));
        assertThat(ok(doGet("/api/finance/accounts/options", admin)).findValuesAsText("code")).doesNotContain(code).contains("1122", "222101");
        // 收款账户下拉按币别
        assertThat(ok(doGet("/api/finance/bank-accounts/simple?currency=USD", admin)).findValuesAsText("currency")).containsOnly("USD");
    }

    /** FIN-RPT-T01 账龄、FIN-RPT-T02 客户对账单 */
    @Test
    void agingAndStatement() throws Exception {
        String c = customer("账龄客户", false);
        LocalDate today = LocalDate.now();
        // A：到期日 35 天前，未收 5,000；B：到期日 16 天后，未收 3,000
        otherAr(c, "CNY", today.minusDays(40), today.minusDays(35), "5000");
        otherAr(c, "CNY", today, today.plusDays(16), "3000");
        JsonNode aging = ok(doGet("/api/finance/reports/ar-aging?customerId=" + c, admin));
        assertThat(aging.at("/rows").size()).isEqualTo(1);
        JsonNode row = aging.at("/rows/0");
        assertThat(row.at("/total").decimalValue()).isEqualByComparingTo("8000");
        assertThat(row.at("/notDue").decimalValue()).isEqualByComparingTo("3000");
        assertThat(row.at("/d31to60").decimalValue()).isEqualByComparingTo("5000");
        assertThat(row.at("/totalBase").decimalValue()).isEqualByComparingTo("8000");
        assertThat(row.at("/overdueBase").decimalValue()).isEqualByComparingTo("5000");
        JsonNode docs = ok(doGet("/api/finance/reports/ar-aging/docs?customerId=" + c, admin));
        assertThat(docs.at("/0/overdueDays").asInt()).isEqualTo(35);
        assertThat(docs.at("/0/bucket").asText()).isEqualTo("D31_60");

        // FIN-RPT-T02：期初 2,000，本期应收 10,000、收款 6,000 → 期末 6,000
        String c2 = customer("对账客户", false);
        LocalDate first = today.withDayOfMonth(1);
        otherAr(c2, "CNY", first.minusDays(1), null, "2000");
        otherAr(c2, "CNY", first, null, "10000");
        confirmedReceipt(c2, "SALES", bankCny, "6000", "0", null, null);
        JsonNode s = ok(doGet("/api/finance/reports/customer-statement?customerId=" + c2 + "&dateFrom=" + first, admin));
        assertThat(s.at("/opening").decimalValue()).isEqualByComparingTo("2000");
        assertThat(s.at("/debit").decimalValue()).isEqualByComparingTo("10000");
        assertThat(s.at("/credit").decimalValue()).isEqualByComparingTo("6000");
        assertThat(s.at("/closing").decimalValue()).isEqualByComparingTo("6000");
        assertThat(s.at("/lines").size()).isEqualTo(2);
        assertThat(s.at("/lines/1/balance").decimalValue()).isEqualByComparingTo("6000");
        assertThat(s.at("/ledgerBalance").decimalValue()).isEqualByComparingTo("6000");
        assertThat(s.at("/mismatch").asBoolean()).isFalse();
        JsonNode print = ok(doGet("/api/finance/reports/customer-statement/print-data?customerId=" + c2 + "&dateFrom=" + first, admin));
        assertThat(print.at("/closing").decimalValue()).isEqualByComparingTo("6000");
    }
}
