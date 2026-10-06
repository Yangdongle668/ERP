package com.erp.it.quality;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 品质集成测试的公共准备：库存期间、物料、供应商、采购订单与到货入库、检验单查询 */
abstract class QualityTestSupport extends AbstractIntegrationTest {

    static final String CAT_RAW = "501";
    static final String CAT_FG = "507";
    static final String W_RAW = "801";
    static final String W_NG = "808";
    static final String W_QC = "809";
    static final String SUPPLIER_TERM = "406";
    static final byte[] PDF = "%PDF-1.4\n%âãÏÓ\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    private static volatile boolean ready;

    protected String admin;

    @Autowired
    protected InventoryDocApi docApi;

    @BeforeEach
    void prepare() throws Exception {
        admin = loginAsAdmin();
        ensureInventoryReady();
    }

    private synchronized void ensureInventoryReady() throws Exception {
        if (ready) return;
        JsonNode info = ok(doGet("/api/inventory/opening", admin));
        if (info.at("/period").isMissingNode() || info.at("/period").isNull()) {
            ok(doPost("/api/inventory/periods/init", admin, Map.of("period", YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
            info = ok(doGet("/api/inventory/opening", admin));
        }
        if (!info.at("/completed").asBoolean()) {
            String m = material("期初物料", CAT_RAW, "RAW", Map.of("sourceType", "PURCHASE"));
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(code(m))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        ready = true;
    }

    private JsonNode upload(String url, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        String body = mockMvc.perform(multipart(url).file(file).header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body);
    }

    private static byte[] openingXlsx(String materialCode) throws Exception {
        String[] head = {"仓库编码*", "库位编码", "物料编码*", "批次号", "供应商批号", "生产日期", "到期日期", "数量*", "单价*", "序列号"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("期初库存");
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            s.createRow(1).createCell(0).setCellValue("说明");
            Row r = s.createRow(2);
            String[] v = {"W-RAW", "", materialCode, "", "", "", "", "10", "1", ""};
            for (int i = 0; i < v.length; i++) r.createCell(i).setCellValue(v[i]);
            wb.write(out);
            return out.toByteArray();
        }
    }

    protected String material(String name, String category, String type, Map<String, Object> extra) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", category);
        m.put("name", name + uniq());
        m.put("materialType", type);
        m.put("baseUom", "PCS");
        m.put("tracking", "NONE");
        m.put("iqcRequired", false);
        m.put("fqcRequired", false);
        m.putAll(extra);
        String id = ok(doPost("/api/engineering/materials", admin, m)).asText();
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    /** 需要 IQC 的原材料 */
    protected String iqcRaw(String name) throws Exception {
        return material(name, CAT_RAW, "RAW", Map.of("sourceType", "PURCHASE", "iqcRequired", true));
    }

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    protected String uploadPdf() throws Exception {
        String body = mockMvc.perform(multipart("/api/system/files").file(new MockMultipartFile("file", "cert.pdf", "application/pdf", PDF))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ok(read(body)).at("/id").asText();
    }

    protected String supplier(String... materialIds) throws Exception {
        Map<String, Object> s = new HashMap<>();
        s.put("name", "QC供应商" + uniq());
        s.put("shortName", "QC供应商");
        s.put("supplierType", "MANUFACTURER");
        s.put("country", "CN");
        s.put("currency", "CNY");
        s.put("paymentTermId", SUPPLIER_TERM);
        s.put("purchaseTaxRate", "0.13");
        s.put("contacts", List.of(Map.of("name", "张经理", "mobile", "13800000000", "isPrimary", true)));
        s.put("banks", List.of(Map.of("bankName", "招商银行", "accountName", "供应商", "accountNo", "6225" + uniq(), "isDefault", true)));
        s.put("certs", List.of(Map.of("certType", "LICENSE", "certNo", "C" + uniq(), "fileId", uploadPdf())));
        List<Map<String, Object>> ms = new ArrayList<>();
        for (String m : materialIds) ms.add(Map.of("materialId", m, "supplyStatus", "QUALIFIED"));
        s.put("materials", ms);
        String id = ok(doPost("/api/purchase/suppliers", admin, s)).asText();
        ok(doPost("/api/purchase/suppliers/" + id + "/qualify", admin, null));
        return id;
    }

    /** 采购订单 → 到货单审核 → 入库单确认（需检物料进待检仓，品质自动生成 IQC）。返回到货单 ID */
    protected String receiveIntoQc(String supplierId, String materialId, String qty) throws Exception {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("priceInclTax", "1");
        l.put("requiredDate", LocalDate.now().plusDays(7).toString());
        String order = ok(doPost("/api/purchase/orders", admin, Map.of("supplierId", supplierId, "orderType", "STANDARD", "taxIncluded", true,
                "lines", List.of(l)))).at("/id").asText();
        ok(doPost("/api/purchase/orders/" + order + "/submit", admin, null));
        String orderLine = ok(doGet("/api/purchase/orders/" + order, admin)).at("/lines/0/id").asText();
        String rc = ok(doPost("/api/purchase/receipts", admin, Map.of("supplierId", supplierId, "receiptType", "PURCHASE", "deliveryNoteNo", "DN" + uniq(),
                "lines", List.of(Map.of("orderLineId", orderLine, "qty", qty))))).at("/id").asText();
        ok(doPost("/api/purchase/receipts/" + rc + "/approve", admin, null));
        String stockIn = receipt(rc).at("/lines/0/stockInId").asText();
        ok(doPost("/api/inventory/stock-ins/" + stockIn + "/confirm", admin, Map.of()));
        return rc;
    }

    protected JsonNode receipt(String id) throws Exception {
        return ok(doGet("/api/purchase/receipts/" + id, admin));
    }

    /** 物料最新的一张检验单（列表行） */
    protected JsonNode inspectionOf(String materialId, String types) throws Exception {
        JsonNode list = ok(doGet("/api/quality/inspections?types=" + types + "&materialId=" + materialId, admin)).at("/list");
        assertThat(list.size()).as("物料 %s 的检验单", materialId).isGreaterThan(0);
        return list.get(0);
    }

    protected JsonNode inspection(String id) throws Exception {
        return ok(doGet("/api/quality/inspections/" + id, admin));
    }

    protected BigDecimal onHand(String materialId, String warehouseId) throws Exception {
        JsonNode list = ok(doGet("/api/inventory/stocks?materialId=" + materialId + "&warehouseIds=" + warehouseId + "&groupBy=WAREHOUSE&showZero=true", admin)).at("/list");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode n : list) sum = sum.add(n.at("/onHandQty").decimalValue());
        return sum;
    }

    /** 检验单生成的调拨单全部确认 */
    protected void confirmTransfers(String inspectionId) throws Exception {
        for (JsonNode t : inspection(inspectionId).at("/transferIds")) ok(doPost("/api/inventory/transfers/" + t.asText() + "/confirm", admin, Map.of()));
    }

    protected String customer() throws Exception {
        Map<String, Object> c = new HashMap<>();
        String full = "QC客户 " + uniq();
        c.put("name", full);
        c.put("country", "CN");
        c.put("appDomain", "C");
        c.put("paymentTermId", "404");
        c.put("taxNo", "91440300" + uniq());
        c.put("contacts", List.of(Map.of("name", "John", "email", "john@example.com", "isPrimary", true)));
        c.put("addresses", List.of(
                Map.of("addressType", "SHIP_TO", "companyName", full, "country", "CN", "addressLine", "1 Main St", "isDefault", true),
                Map.of("addressType", "BILL_TO", "companyName", full, "country", "CN", "addressLine", "1 Main St", "isDefault", true)));
        String id = ok(doPost("/api/crm/customers", admin, c)).at("/id").asText();
        ok(doPost("/api/crm/customers/" + id + "/activate", admin, null));
        return id;
    }

    protected Long adminId() throws Exception {
        return ok(doGet("/api/system/auth/me", admin)).at("/id").asLong();
    }

    protected void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    protected void resetParam(String key) throws Exception {
        ok(doPost("/api/system/params/" + key + "/reset", admin, null));
    }
}
