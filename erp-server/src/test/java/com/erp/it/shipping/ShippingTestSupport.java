package com.erp.it.shipping;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 出货集成测试的公共准备：库存期间、成品物料与库存（可带批次）、正式客户、已审核订单、出货通知到出货确认的各步。
 * 每个测试使用独立的新客户与新物料，数据互不影响。
 */
public abstract class ShippingTestSupport extends AbstractIntegrationTest {

    static final String CAT_FG = "507";
    static final String CAT_RAW = "501";
    static final String W_FG = "803";
    static final String TERM_NET30 = "404";
    private static final AtomicLong SOURCE_ID = new AtomicLong(9_500_000);
    private static volatile boolean ready;

    protected String admin;

    @Autowired
    protected InventoryDocApi docApi;
    @Autowired
    protected SalesOrderQueryApi orderQueryApi;

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
            String m = material("期初物料", CAT_RAW, "RAW", Map.of());
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(code(m))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        // 外销出货按出货日期取 USD 汇率（已存在时忽略）
        doPost("/api/system/exchange-rates", admin, Map.of("currency", "USD", "rateType", "DAILY", "effectiveDate", "2000-01-01", "rate", "7.1"));
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

    // ==================== 物料、库存 ====================

    protected String material(String name, String category, String type, Map<String, Object> extra) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", category);
        m.put("name", name + uniq());
        m.put("nameEn", "Item " + name);
        m.put("materialType", type);
        m.put("baseUom", "PCS");
        m.put("tracking", "NONE");
        m.put("iqcRequired", false);
        m.put("fqcRequired", false);
        m.put("oqcRequired", false);
        m.putAll(extra);
        String id = ok(doPost("/api/engineering/materials", admin, m)).asText();
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    /** 成品；extra 覆盖默认属性（如 tracking=BATCH、oqcRequired=true、hsCode） */
    protected String fg(String name, Map<String, Object> extra) throws Exception {
        return material(name, CAT_FG, "FINISHED", extra);
    }

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    /** 成品仓入库并确认（batchNo 可空） */
    protected void stock(String materialId, String qty, String batchNo) throws Exception {
        long sid = SOURCE_ID.incrementAndGet();
        Long in = docApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, new SourceRef("SHP_IT", sid, "SHP_IT-" + sid), Long.valueOf(W_FG), null, null,
                null, List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), batchNo, null, null, null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    // ==================== 客户、订单 ====================

    /** 正式客户：默认收货 / 开票地址；foreign 为外销（USD、税率 0） */
    protected String customer(String name, boolean foreign) throws Exception {
        Map<String, Object> c = new HashMap<>();
        String full = name + " " + uniq();
        c.put("name", full);
        c.put("shortName", full);
        c.put("nameEn", foreign ? full : null);
        c.put("country", foreign ? "US" : "CN");
        c.put("paymentTermId", TERM_NET30);
        c.put("contacts", List.of(Map.of("name", "John", "email", "john@example.com", "isPrimary", true)));
        if (!foreign) c.put("taxNo", "91440300" + uniq());
        c.put("addresses", List.of(
                Map.of("addressType", "SHIP_TO", "companyName", full, "country", foreign ? "US" : "CN", "addressLine", "1 Main St", "isDefault", true),
                Map.of("addressType", "BILL_TO", "companyName", full, "country", foreign ? "US" : "CN", "addressLine", "9 Bill Rd", "isDefault", true)));
        String id = ok(doPost("/api/crm/customers", admin, c)).at("/id").asText();
        ok(doPost("/api/crm/customers/" + id + "/activate", admin, null));
        return id;
    }

    protected static Map<String, Object> orderLine(String materialId, String qty, String price, LocalDate required) {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("price", price);
        l.put("requiredDate", (required == null ? LocalDate.now().plusDays(20) : required).toString());
        return l;
    }

    /** 新建并提交（无审批流 → 已审核），返回订单 ID */
    protected String approvedOrder(String customerId, List<Map<String, Object>> lines) throws Exception {
        String id = ok(doPost("/api/sales/orders", admin, Map.of("customerId", customerId, "lines", lines))).at("/id").asText();
        assertThat(ok(doPost("/api/sales/orders/" + id + "/submit", admin, Map.of())).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    protected Long orderLineId(String orderId, int index) throws Exception {
        return Long.valueOf(ok(doGet("/api/sales/orders/" + orderId, admin)).at("/lines/" + index + "/id").asText());
    }

    protected SalesOrderLineDTO orderLineDto(Long lineId) {
        return orderQueryApi.getLine(lineId).orElseThrow();
    }

    // ==================== 出货通知 ====================

    protected Map<String, Object> noticeBody(String customerId, List<Map<String, Object>> lines) throws Exception {
        JsonNode d = ok(doGet("/api/shipping/notices/customer-defaults?customerId=" + customerId, admin));
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("shipDate", LocalDate.now().toString());
        body.put("transportMode", "LAND");
        body.put("shipToAddressId", d.at("/shipToAddressId").asText());
        body.put("warehouseId", W_FG);
        body.put("lines", lines);
        return body;
    }

    protected static Map<String, Object> noticeLine(Long orderLineId, String qty) {
        return Map.of("orderLineId", orderLineId, "qty", qty);
    }

    /** 新建出货通知草稿，返回 ID */
    protected String notice(String customerId, Long orderLineId, String qty) throws Exception {
        return ok(doPost("/api/shipping/notices", admin, noticeBody(customerId, List.of(noticeLine(orderLineId, qty))))).at("/id").asText();
    }

    protected JsonNode noticeDetail(String id) throws Exception {
        return ok(doGet("/api/shipping/notices/" + id, admin));
    }

    /** 提交（无审批流 → 已审核并生成拣货单） */
    protected void submitNotice(String id) throws Exception {
        ok(doPost("/api/shipping/notices/" + id + "/submit", admin, null));
        assertThat(noticeDetail(id).at("/noticeStatus").asText()).isIn("APPROVED", "PACKED");
    }

    protected String pickingOf(String noticeId) throws Exception {
        JsonNode ps = noticeDetail(noticeId).at("/pickings");
        return ps.get(ps.size() - 1).at("/id").asText();
    }

    protected JsonNode picking(String id) throws Exception {
        return ok(doGet("/api/shipping/pickings/" + id, admin));
    }

    /** 按推荐数量拣货并完成 */
    protected void pickAsSuggested(String noticeId) throws Exception {
        String p = pickingOf(noticeId);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode l : picking(p).at("/lines")) {
            Map<String, Object> m = new HashMap<>();
            m.put("noticeLineId", l.at("/noticeLineId").asText());
            m.put("batchNo", blank(l.at("/batchNo")) ? null : l.at("/batchNo").asText());
            m.put("suggestedQty", l.at("/suggestedQty").decimalValue());
            m.put("pickedQty", l.at("/shortage").asBoolean() ? BigDecimal.ZERO : l.at("/suggestedQty").decimalValue());
            lines.add(m);
        }
        ok(doPut("/api/shipping/pickings/" + p + "/lines", admin, Map.of("lines", lines)));
        ok(doPost("/api/shipping/pickings/" + p + "/complete", admin, Map.of("acceptShort", false)));
    }

    /** 按规格批量装箱 */
    protected JsonNode batchPack(String noticeId, String noticeLineId, String batchNo, String perCarton, String total) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("noticeLineId", noticeLineId);
        b.put("batchNo", batchNo);
        b.put("qtyPerCarton", perCarton);
        if (total != null) b.put("totalQty", total);
        b.put("lengthCm", "60");
        b.put("widthCm", "40");
        b.put("heightCm", "40");
        b.put("tareWeightKg", "1.5");
        return doPost("/api/shipping/notices/" + noticeId + "/cartons/batch", admin, b);
    }

    protected JsonNode packing(String noticeId) throws Exception {
        return ok(doGet("/api/shipping/notices/" + noticeId + "/cartons", admin));
    }

    // ==================== 出货单 ====================

    protected JsonNode shipment(String id) throws Exception {
        return ok(doGet("/api/shipping/shipments/" + id, admin));
    }

    /** 提交出货单并确认生成的销售出库单 */
    protected void submitAndConfirm(String shipmentId) throws Exception {
        ok(doPost("/api/shipping/shipments/" + shipmentId + "/submit", admin, null));
        JsonNode s = shipment(shipmentId);
        assertThat(s.at("/shipmentStatus").asText()).isEqualTo("SUBMITTED");
        ok(doPost("/api/inventory/stock-outs/" + s.at("/stockOutId").asText() + "/confirm", admin, Map.of()));
        assertThat(shipment(shipmentId).at("/shipmentStatus").asText()).isEqualTo("SHIPPED");
    }

    protected static boolean blank(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    protected void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    protected void resetParam(String key) throws Exception {
        ok(doPost("/api/system/params/" + key + "/reset", admin, null));
    }
}
