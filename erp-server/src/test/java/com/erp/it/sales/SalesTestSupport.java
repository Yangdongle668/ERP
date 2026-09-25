package com.erp.it.sales;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 销售集成测试的公共准备：库存期间与期初（全局状态，首次运行时初始化）、成品物料、正式客户、订单、出货回写。
 * 每个测试使用独立的新客户与新物料，数据互不影响。
 */
abstract class SalesTestSupport extends AbstractIntegrationTest {

    static final String CAT_FG = "507";
    static final String CAT_RAW = "501";
    static final String W_FG = "803";
    static final String W_RAW = "801";
    static final String TERM_NET30 = "404";
    static final String TERM_BL = "403";
    static final AtomicLong SHIPMENT_ID = new AtomicLong(9_100_000);
    private static final AtomicLong SOURCE_ID = new AtomicLong(9_000_000);
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

    /** 库存期间与期初（与仓库、资材集成测试兼容）：未初始化时设置本月为启用期间并直接完成期初 */
    private synchronized void ensureInventoryReady() throws Exception {
        if (ready) return;
        JsonNode info = ok(doGet("/api/inventory/opening", admin));
        if (info.at("/period").isMissingNode() || info.at("/period").isNull()) {
            ok(doPost("/api/inventory/periods/init", admin, Map.of("period", YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
            info = ok(doGet("/api/inventory/opening", admin));
        }
        if (!info.at("/completed").asBoolean()) {
            // 与资材、仓库测试一致：至少导入一行期初再完成
            String m = raw("期初物料");
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(code(m))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        ready = true;
    }

    /** JSON 中的空值（null 字段不输出） */
    protected static boolean blank(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    private JsonNode upload(String url, byte[] content) throws Exception {
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

    // ==================== 物料 ====================

    /** 新建并启用成品（不管理批次）；extra 覆盖默认属性 */
    protected String fg(String name, Map<String, Object> extra) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", CAT_FG);
        m.put("name", name + uniq());
        m.put("nameEn", "Item " + name);
        m.put("materialType", "FINISHED");
        m.put("baseUom", "PCS");
        m.put("tracking", "NONE");
        m.put("iqcRequired", false);
        m.putAll(extra);
        String id = ok(doPost("/api/engineering/materials", admin, m)).asText();
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    protected String fg(String name) throws Exception {
        return fg(name, Map.of());
    }

    protected String raw(String name) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", CAT_RAW);
        m.put("name", name + uniq());
        m.put("materialType", "RAW");
        m.put("baseUom", "PCS");
        m.put("tracking", "NONE");
        m.put("iqcRequired", false);
        String id = ok(doPost("/api/engineering/materials", admin, m)).asText();
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    /** 成品库存（其他入库并确认） */
    protected void stock(String materialId, String qty) {
        long sid = SOURCE_ID.incrementAndGet();
        Long in = docApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, new SourceRef("SAL_IT", sid, "SAL_IT-" + sid), Long.valueOf(W_FG), null, null,
                null, List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), null, null, null, null, null)))).get(0);
        try {
            ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== 客户 ====================

    /** 新建并转正式的客户：主联系人、默认收货地址、付款条件；foreign 为外销（USD、税率 0） */
    protected String customer(String name, boolean foreign, String term, String level) throws Exception {
        Map<String, Object> c = new HashMap<>();
        String full = name + " " + uniq();
        c.put("name", full);
        c.put("nameEn", foreign ? full : null);
        c.put("country", foreign ? "US" : "CN");
        c.put("paymentTermId", term);
        if (level != null) c.put("level", level);
        c.put("contacts", List.of(Map.of("name", "John", "email", "john@example.com", "isPrimary", true)));
        if (!foreign) c.put("taxNo", "91440300" + uniq());
        c.put("addresses", List.of(
                Map.of("addressType", "SHIP_TO", "companyName", full, "country", foreign ? "US" : "CN", "addressLine", "1 Main St", "isDefault", true),
                Map.of("addressType", "BILL_TO", "companyName", full, "country", foreign ? "US" : "CN", "addressLine", "1 Main St", "isDefault", true)));
        String id = ok(doPost("/api/crm/customers", admin, c)).at("/id").asText();
        assertThat(ok(doPost("/api/crm/customers/" + id + "/activate", admin, null)).at("/customerStatus").asText()).isEqualTo("ACTIVE");
        return id;
    }

    protected String customer(String name) throws Exception {
        return customer(name, false, TERM_NET30, null);
    }

    // ==================== 订单 ====================

    protected static Map<String, Object> line(String materialId, String qty, String price, LocalDate required) {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("price", price);
        l.put("requiredDate", (required == null ? LocalDate.now().plusDays(20) : required).toString());
        return l;
    }

    protected Map<String, Object> orderBody(String customerId, List<Map<String, Object>> lines) {
        Map<String, Object> o = new HashMap<>();
        o.put("customerId", customerId);
        o.put("lines", lines);
        return o;
    }

    protected String createOrder(Map<String, Object> body) throws Exception {
        return ok(doPost("/api/sales/orders", admin, body)).at("/id").asText();
    }

    /** 新建并提交（无审批流 → 已审核） */
    protected String approvedOrder(String customerId, List<Map<String, Object>> lines) throws Exception {
        String id = createOrder(orderBody(customerId, lines));
        assertThat(ok(doPost("/api/sales/orders/" + id + "/submit", admin, Map.of())).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    protected JsonNode order(String id) throws Exception {
        return ok(doGet("/api/sales/orders/" + id, admin));
    }

    protected Long lineId(String orderId, int index) throws Exception {
        return Long.valueOf(order(orderId).at("/lines/" + index + "/id").asText());
    }

    /** 出货模块回写：出货确认，返回出货单 ID */
    protected Long ship(Long orderLineId, String qty, LocalDate date) {
        long sid = SHIPMENT_ID.incrementAndGet();
        writebackApi.onShipped(new SalesOrderWritebackApi.ShipmentRecord(sid, "SH-" + sid, date == null ? LocalDate.now() : date,
                List.of(new SalesOrderWritebackApi.Line(orderLineId, new BigDecimal(qty)))));
        return sid;
    }
}
