package com.erp.it.pmc;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * PMC 集成测试的公共准备：库存期间、物料（含计划属性）、BOM、工艺、库存、客户与销售订单、供应商与采购订单、MRP 运算等待。
 * 每个测试使用独立的新物料；MRP 为全量运算，断言只看本测试的物料。
 */
abstract class PmcTestSupport extends AbstractIntegrationTest {

    static final String CAT_RAW = "501";
    static final String CAT_SEMI = "506";
    static final String CAT_FG = "507";
    static final String W_RAW = "801";
    static final String W_SEMI = "802";
    static final String W_FG = "803";
    static final String TERM = "404";
    static final String SUPPLIER_TERM = "406";
    static final byte[] PDF = "%PDF-1.4\n%âãÏÓ\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    private static final AtomicLong SOURCE_ID = new AtomicLong(9_800_000);
    static final AtomicLong SHIPMENT_ID = new AtomicLong(9_900_000);
    private static volatile boolean ready;

    protected String admin;

    @Autowired
    protected InventoryDocApi docApi;
    @Autowired
    protected SalesOrderWritebackApi writebackApi;
    @Autowired
    protected JdbcTemplate jdbc;

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
            String m = raw("期初物料", Map.of());
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(code(m))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        ready = true;
    }

    protected static boolean blank(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    protected JsonNode upload(String url, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        String body = mockMvc.perform(multipart(url).file(file).header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body);
    }

    private static byte[] openingXlsx(String materialCode) throws Exception {
        String[] head = {"仓库编码*", "库位编码", "物料编码*", "批次号", "供应商批号", "生产日期", "到期日期", "数量*", "单价*", "序列号"};
        String[] row = {"W-RAW", "", materialCode, "", "", "", "", "10", "2", ""};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("期初库存");
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            s.createRow(1).createCell(0).setCellValue("说明");
            Row r = s.createRow(2);
            for (int i = 0; i < row.length; i++) r.createCell(i).setCellValue(row[i]);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ==================== 物料 / BOM / 工艺 ====================

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

    /** 成品（自制）；plan 覆盖计划属性，如 leadTimeDays */
    protected String fg(String name, Map<String, Object> plan) throws Exception {
        Map<String, Object> p = new HashMap<>(Map.of("sourceType", "MAKE"));
        p.putAll(plan);
        return material(name, CAT_FG, "FINISHED", p);
    }

    protected String semi(String name, Map<String, Object> plan) throws Exception {
        Map<String, Object> p = new HashMap<>(Map.of("sourceType", "MAKE"));
        p.putAll(plan);
        return material(name, CAT_SEMI, "SEMI_FINISHED", p);
    }

    protected String raw(String name, Map<String, Object> plan) throws Exception {
        Map<String, Object> p = new HashMap<>(Map.of("sourceType", "PURCHASE"));
        p.putAll(plan);
        return material(name, CAT_RAW, "RAW", p);
    }

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    protected static Map<String, Object> bomLine(String componentId, Object qtyPer) {
        Map<String, Object> l = new HashMap<>();
        l.put("componentId", componentId);
        l.put("qtyPer", qtyPer);
        l.put("scrapRate", 0);
        l.put("issueMethod", "PICK");
        return l;
    }

    protected String bom(String parentId, List<Map<String, Object>> lines) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("materialId", parentId);
        b.put("baseQty", 1);
        b.put("lines", lines);
        String id = ok(doPost("/api/engineering/boms", admin, b)).at("/id").asText();
        assertThat(ok(doPost("/api/engineering/boms/" + id + "/submit", admin, null)).asText()).isEqualTo("APPROVED");
        return id;
    }

    protected String workCenter(Object hoursPerShift, int shifts) throws Exception {
        Map<String, Object> w = new HashMap<>();
        w.put("code", "WC" + uniq());
        w.put("name", "产线" + uniq());
        w.put("deptId", "100");
        w.put("wcType", "LINE");
        w.put("hoursPerShift", hoursPerShift);
        w.put("shiftCount", shifts);
        w.put("efficiencyPct", 1);
        w.put("laborRate", 30);
        return ok(doPost("/api/engineering/work-centers", admin, w)).asText();
    }

    /** 工艺路线：steps = [工序号, 工序, 工作中心, 标准秒/件] */
    protected String routing(String materialId, Object[]... steps) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] s : steps) {
            Map<String, Object> m = new HashMap<>();
            m.put("seq", s[0]);
            m.put("operation", s[1]);
            m.put("workCenterId", s[2]);
            m.put("runSeconds", s[3]);
            m.put("isReportPoint", true);
            m.put("isInspectionPoint", false);
            list.add(m);
        }
        String id = ok(doPost("/api/engineering/routings", admin, Map.of("materialId", materialId, "steps", list))).at("/id").asText();
        ok(doPost("/api/engineering/routings/" + id + "/approve", admin, null));
        return id;
    }

    /** 其他入库并确认 */
    protected void stock(String materialId, String warehouseId, String qty) throws Exception {
        long sid = SOURCE_ID.incrementAndGet();
        Long in = docApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, new SourceRef("PMC_IT", sid, "PMC_IT-" + sid), Long.valueOf(warehouseId),
                null, null, null, List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), null, null,
                LocalDate.now(), null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    // ==================== 销售 ====================

    protected String customer() throws Exception {
        Map<String, Object> c = new HashMap<>();
        String full = "PMC客户 " + uniq();
        c.put("name", full);
        c.put("country", "CN");
        c.put("appDomain", "C");
        c.put("paymentTermId", TERM);
        c.put("taxNo", "91440300" + uniq());
        c.put("contacts", List.of(Map.of("name", "John", "email", "john@example.com", "isPrimary", true)));
        c.put("addresses", List.of(
                Map.of("addressType", "SHIP_TO", "companyName", full, "country", "CN", "addressLine", "1 Main St", "isDefault", true),
                Map.of("addressType", "BILL_TO", "companyName", full, "country", "CN", "addressLine", "1 Main St", "isDefault", true)));
        String id = ok(doPost("/api/crm/customers", admin, c)).at("/id").asText();
        ok(doPost("/api/crm/customers/" + id + "/activate", admin, null));
        return id;
    }

    protected static Map<String, Object> soLine(String materialId, String qty, LocalDate required) {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("price", "10");
        l.put("requiredDate", required.toString());
        return l;
    }

    /** 新建并提交销售订单（无审批流 → 已审核），返回订单 ID */
    protected String salesOrder(String customerId, List<Map<String, Object>> lines) throws Exception {
        String id = ok(doPost("/api/sales/orders", admin, Map.of("customerId", customerId, "lines", lines))).at("/id").asText();
        assertThat(ok(doPost("/api/sales/orders/" + id + "/submit", admin, Map.of())).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    protected Long soLineId(String orderId, int index) throws Exception {
        return Long.valueOf(ok(doGet("/api/sales/orders/" + orderId, admin)).at("/lines/" + index + "/id").asText());
    }

    protected void ship(Long orderLineId, String qty) {
        long sid = SHIPMENT_ID.incrementAndGet();
        writebackApi.onShipped(new SalesOrderWritebackApi.ShipmentRecord(sid, "SH-" + sid, LocalDate.now(),
                List.of(new SalesOrderWritebackApi.Line(orderLineId, new BigDecimal(qty)))));
    }

    // ==================== 采购 ====================

    protected String uploadPdf() throws Exception {
        String body = mockMvc.perform(multipart("/api/system/files").file(new MockMultipartFile("file", "cert.pdf", "application/pdf", PDF))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ok(read(body)).at("/id").asText();
    }

    /** 合格供应商（供应这些物料） */
    protected String supplier(String... materialIds) throws Exception {
        Map<String, Object> s = new HashMap<>();
        s.put("name", "PMC供应商" + uniq());
        s.put("shortName", "PMC供应商");
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

    /** 已审核采购订单（一行），返回订单 ID */
    protected String purchaseOrder(String supplierId, String materialId, String qty, LocalDate required) throws Exception {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("priceInclTax", "1");
        l.put("requiredDate", required.toString());
        Map<String, Object> o = new HashMap<>();
        o.put("supplierId", supplierId);
        o.put("orderType", "STANDARD");
        o.put("taxIncluded", true);
        o.put("lines", List.of(l));
        String id = ok(doPost("/api/purchase/orders", admin, o)).at("/id").asText();
        assertThat(ok(doPost("/api/purchase/orders/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    // ==================== 参数、MRP ====================

    protected void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    protected void resetParam(String key) throws Exception {
        ok(doPost("/api/system/params/" + key + "/reset", admin, null));
    }

    /** 发起 MRP 并等待完成，返回运算记录 */
    protected JsonNode runMrp(Map<String, Object> req) throws Exception {
        String id = ok(doPost("/api/pmc/mrp/runs", admin, req)).asText();
        for (int i = 0; i < 300; i++) {
            JsonNode r = ok(doGet("/api/pmc/mrp/runs/" + id, admin));
            if (!"RUNNING".equals(r.at("/runStatus").asText())) return r;
            Thread.sleep(100);
        }
        throw new AssertionError("MRP 运算超时");
    }

    protected JsonNode runMrp() throws Exception {
        JsonNode r = runMrp(Map.of("runType", "FULL"));
        assertThat(r.at("/runStatus").asText()).as("MRP 失败：%s", r.at("/errorMsg").asText()).isEqualTo("SUCCESS");
        return r;
    }

    /** 最新运算中某物料的建议（任意状态） */
    protected List<JsonNode> suggestions(String materialId) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        ok(doGet("/api/pmc/mrp/suggestions?pageNo=1&pageSize=100&statuses=ALL&materialId=" + materialId, admin)).at("/list").forEach(out::add);
        return out;
    }

    protected List<JsonNode> exceptions(String materialId) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        ok(doGet("/api/pmc/mrp/exceptions?pageNo=1&pageSize=100&materialId=" + materialId, admin)).at("/list").forEach(out::add);
        return out;
    }

    protected JsonNode demands(String materialId) throws Exception {
        return ok(doGet("/api/pmc/demands?pageNo=1&pageSize=50&openOnly=false&materialId=" + materialId, admin)).at("/list");
    }
}
