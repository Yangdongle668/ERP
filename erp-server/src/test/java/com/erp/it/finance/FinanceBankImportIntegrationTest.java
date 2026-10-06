package com.erp.it.finance;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 银行流水导入：精确匹配、去公司后缀的模糊匹配、历史付款方、多个相似客户不自动选择 */
class FinanceBankImportIntegrationTest extends FinanceTestSupport {

    private String customerNamed(String name) throws Exception {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name);
        c.put("country", "CN");
        c.put("appDomain", "C");
        c.put("paymentTermId", "404");
        c.put("taxNo", "91440300" + uniq());
        c.put("contacts", List.of(Map.of("name", "John", "email", "john@example.com", "isPrimary", true)));
        c.put("addresses", List.of(
                Map.of("addressType", "SHIP_TO", "companyName", name, "country", "CN", "addressLine", "1 Main St", "isDefault", true),
                Map.of("addressType", "BILL_TO", "companyName", name, "country", "CN", "addressLine", "9 Bill Rd", "isDefault", true)));
        String id = ok(doPost("/api/crm/customers", admin, c)).at("/id").asText();
        ok(doPost("/api/crm/customers/" + id + "/activate", admin, null));
        return id;
    }

    private JsonNode importRows(String[]... rows) throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("银行流水");
            String[] head = {"日期*", "金额*", "币别", "付款方名称*", "流水号"};
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            s.createRow(1).createCell(0).setCellValue("说明");
            int r = 2;
            for (String[] row : rows) {
                Row x = s.createRow(r++);
                for (int i = 0; i < row.length; i++) if (row[i] != null) x.createCell(i).setCellValue(row[i]);
            }
            wb.write(out);
            xlsx = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "bank.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
        String body = mockMvc.perform(multipart("/api/finance/receipts/import-bank").file(file).param("bankAccountId", bankCny).param("settlementMethod", "TT")
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ok(read(body));
    }

    @Test
    void fuzzyMatch() throws Exception {
        String u = uniq();
        String hq = customerNamed("华强" + u + "电子有限公司");
        String zen = customerNamed("Zenith" + u + " Trading Co., Ltd.");
        // 两个相似客户
        customerNamed("联合" + u + "科技有限公司");
        customerNamed("联合" + u + "科技（深圳）有限公司");

        JsonNode r = importRows(
                new String[]{"2026-09-01", "1000", null, "华强" + u + "电子有限公司", "R" + u + "1"},          // 精确
                new String[]{"2026-09-02", "2000", null, "华强" + u + "电子", "R" + u + "2"},                  // 去后缀
                new String[]{"2026-09-03", "3000", null, "Zenith" + u + " TRADING CO LTD", "R" + u + "3"},    // 英文大小写与公司后缀（H2 的 LIKE 区分大小写，首个单词保持原样；MySQL 不区分）
                new String[]{"2026-09-04", "4000", null, "联合" + u + "科技", "R" + u + "4"},                   // 多个相似
                new String[]{"2026-09-05", "5000", null, "完全无关的公司" + u, "R" + u + "5"});                  // 无
        assertThat(r.at("/created").asInt()).isEqualTo(3);
        assertThat(r.at("/unmatched").size()).isEqualTo(2);
        assertThat(r.at("/unmatched/0/message").asText()).startsWith("匹配到多个相似客户：").contains("联合" + u + "科技有限公司");
        assertThat(r.at("/unmatched/1/message").asText()).isEqualTo("未匹配到客户");
        for (JsonNode id : r.at("/receiptIds")) {
            String cid = ok(doGet("/api/finance/receipts/" + id.asText(), admin)).at("/header/customerId").asText();
            assertThat(cid).isIn(hq, zen);
        }

        // 历史付款方：同一个付款方名称曾登记过，之后按历史匹配（即使名称与客户差别较大）
        JsonNode again = importRows(new String[]{"2026-09-06", "600", null, "Zenith" + u + " TRADING CO LTD", "R" + u + "6"});
        assertThat(again.at("/created").asInt()).isEqualTo(1);
    }
}
