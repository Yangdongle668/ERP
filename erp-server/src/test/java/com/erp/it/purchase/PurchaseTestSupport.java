package com.erp.it.purchase;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.JudgeResult;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.TransferRequest;
import com.erp.module.inventory.api.doc.TransferType;
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
 * 资材集成测试的公共准备：库存期间与期初（全局状态，首次运行时初始化）、物料、合格供应商、价格、订单、到货。
 * 每个测试使用独立的新物料与新供应商，数据互不影响。
 */
abstract class PurchaseTestSupport extends AbstractIntegrationTest {

    static final String CAT_ELEC = "503";
    static final String CAT_AUX = "505";
    static final String W_ELEC = "805";
    static final String W_AUX = "807";
    static final String W_NG = "808";
    static final String W_QC = "809";
    static final String TERM = "406";
    static final byte[] PDF = "%PDF-1.4\n%âãÏÓ\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);

    private static volatile boolean ready;
    private static final AtomicLong SOURCE_ID = new AtomicLong(7_000_000);

    protected String admin;

    @Autowired
    protected InventoryDocApi docApi;

    @BeforeEach
    void prepare() throws Exception {
        admin = loginAsAdmin();
        ensureInventoryReady();
    }

    /** 库存期间与期初：未初始化时设置启用期间（本月）并完成期初（与仓库集成测试兼容） */
    private synchronized void ensureInventoryReady() throws Exception {
        if (ready) return;
        JsonNode info = ok(doGet("/api/inventory/opening", admin));
        if (info.at("/period").isMissingNode() || info.at("/period").isNull()) {
            ok(doPost("/api/inventory/periods/init", admin, Map.of("period", YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
            info = ok(doGet("/api/inventory/opening", admin));
        }
        if (!info.at("/completed").asBoolean()) {
            String m = material(CAT_AUX, "期初物料", Map.of("tracking", "NONE", "iqcRequired", false));
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(List.of(List.of("W-AUX", "", code(m), "", "", "", "", "10", "2", "")))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            Long in = docApi.createStockIn(new StockInRequest(StockInType.PURCHASE_IN, source("PUR_IT"), Long.valueOf(W_AUX), null, null, null,
                    List.of(new StockInRequest.Line(1L, Long.valueOf(m), null, BigDecimal.ONE, null, null, null, null, null)))).get(0);
            assertError(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()), "请先完成期初库存导入");
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        ready = true;
    }

    static SourceRef source(String type) {
        long id = SOURCE_ID.incrementAndGet();
        return new SourceRef(type, id, type + "-" + id);
    }

    // ==================== 物料 ====================

    /** 新建并启用物料；extra 覆盖默认属性 */
    protected String material(String categoryId, String name, Map<String, Object> extra) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", categoryId);
        m.put("name", name + uniq());
        m.put("materialType", CAT_AUX.equals(categoryId) ? "AUXILIARY" : "RAW");
        m.put("baseUom", "PCS");
        m.putAll(extra);
        String id = ok(doPost("/api/engineering/materials", admin, m)).asText();
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    /** 电子料（需检、不管理批次） */
    protected String elec(String name) throws Exception {
        return material(CAT_ELEC, name, Map.of("tracking", "NONE", "iqcRequired", true));
    }

    /** 辅料（免检、不管理批次） */
    protected String aux(String name) throws Exception {
        return material(CAT_AUX, name, Map.of("tracking", "NONE", "iqcRequired", false));
    }

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    // ==================== 供应商 ====================

    protected Map<String, Object> supplierBody(String name, List<String> materialIds, String supplyStatus) throws Exception {
        Map<String, Object> s = new HashMap<>();
        s.put("name", name + uniq());
        s.put("shortName", name);
        s.put("supplierType", "MANUFACTURER");
        s.put("country", "CN");
        s.put("currency", "CNY");
        s.put("paymentTermId", TERM);
        s.put("purchaseTaxRate", "0.13");
        s.put("contacts", List.of(Map.of("name", "张经理", "mobile", "13800000000", "isPrimary", true)));
        s.put("banks", List.of(Map.of("bankName", "招商银行", "accountName", "供应商", "accountNo", "6225" + uniq(), "isDefault", true)));
        s.put("certs", List.of(cert("LICENSE", null)));
        List<Map<String, Object>> ms = new ArrayList<>();
        for (String m : materialIds) ms.add(Map.of("materialId", m, "supplyStatus", supplyStatus));
        s.put("materials", ms);
        return s;
    }

    protected Map<String, Object> cert(String type, LocalDate expire) throws Exception {
        Map<String, Object> c = new HashMap<>();
        c.put("certType", type);
        c.put("certNo", "C" + uniq());
        c.put("expireDate", expire == null ? null : expire.toString());
        c.put("fileId", uploadPdf());
        return c;
    }

    /** 新建供应商并准入（无审批流 → 合格） */
    protected String qualifiedSupplier(String name, String... materialIds) throws Exception {
        String id = ok(doPost("/api/purchase/suppliers", admin, supplierBody(name, List.of(materialIds), "QUALIFIED"))).asText();
        assertThat(ok(doPost("/api/purchase/suppliers/" + id + "/qualify", admin, null)).asText()).isEqualTo("QUALIFIED");
        return id;
    }

    // ==================== 价格、订单、到货 ====================

    /** 调价单并提交生效：lines = [materialId, minQty, price] */
    protected String price(String supplierId, List<List<String>> lines) throws Exception {
        List<Map<String, Object>> ls = new ArrayList<>();
        for (List<String> l : lines) ls.add(Map.of("materialId", l.get(0), "minQty", l.get(1), "newPrice", l.get(2)));
        String id = ok(doPost("/api/purchase/price-adjusts", admin, Map.of("supplierId", supplierId, "adjustReason", "新品报价", "lines", ls))).asText();
        assertThat(ok(doPost("/api/purchase/price-adjusts/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    protected Map<String, Object> orderLine(String materialId, String qty, String priceInclTax, LocalDate required) {
        Map<String, Object> l = new HashMap<>();
        l.put("materialId", materialId);
        l.put("qty", qty);
        l.put("priceInclTax", priceInclTax);
        l.put("requiredDate", (required == null ? LocalDate.now().plusDays(7) : required).toString());
        return l;
    }

    protected String createOrder(String supplierId, String type, List<Map<String, Object>> lines) throws Exception {
        Map<String, Object> o = new HashMap<>();
        o.put("supplierId", supplierId);
        o.put("orderType", type);
        o.put("taxIncluded", true);
        o.put("lines", lines);
        return ok(doPost("/api/purchase/orders", admin, o)).at("/id").asText();
    }

    /** 新建并提交订单（无审批流 → 已审核） */
    protected String approvedOrder(String supplierId, List<Map<String, Object>> lines) throws Exception {
        String id = createOrder(supplierId, "STANDARD", lines);
        assertThat(ok(doPost("/api/purchase/orders/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("APPROVED");
        return id;
    }

    protected JsonNode order(String id) throws Exception {
        return ok(doGet("/api/purchase/orders/" + id, admin));
    }

    protected Map<String, Object> receiptLine(String orderLineId, String qty) {
        Map<String, Object> l = new HashMap<>();
        l.put("orderLineId", orderLineId);
        l.put("qty", qty);
        return l;
    }

    protected Map<String, Object> receiptBody(String supplierId, String type, List<Map<String, Object>> lines) {
        Map<String, Object> r = new HashMap<>();
        r.put("supplierId", supplierId);
        r.put("receiptType", type);
        r.put("deliveryNoteNo", "DN" + uniq());
        r.put("lines", lines);
        return r;
    }

    /** 新建并审核到货单，返回 ID */
    protected String approvedReceipt(String supplierId, List<Map<String, Object>> lines) throws Exception {
        String id = ok(doPost("/api/purchase/receipts", admin, receiptBody(supplierId, "PURCHASE", lines))).at("/id").asText();
        ok(doPost("/api/purchase/receipts/" + id + "/approve", admin, null));
        return id;
    }

    protected JsonNode receipt(String id) throws Exception {
        return ok(doGet("/api/purchase/receipts/" + id, admin));
    }

    /** 确认到货单生成的全部入库单 */
    protected void confirmStockIns(String receiptId) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode l : receipt(receiptId).at("/lines")) {
            String sid = l.at("/stockInId").asText();
            if (!sid.isEmpty() && !"null".equals(sid) && !ids.contains(sid)) ids.add(sid);
        }
        for (String sid : ids) ok(doPost("/api/inventory/stock-ins/" + sid + "/confirm", admin, Map.of()));
    }

    protected BigDecimal onHand(String materialId, String warehouseId) throws Exception {
        JsonNode list = ok(doGet("/api/inventory/stocks?materialId=" + materialId + "&warehouseIds=" + warehouseId + "&groupBy=WAREHOUSE&showZero=true", admin)).at("/list");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode n : list) sum = sum.add(n.at("/onHandQty").decimalValue());
        return sum;
    }

    /** 模拟品质判定后的检验调拨：合格 → 物料默认仓，不合格 → 不良品仓，并确认 */
    protected void inspectionTransfer(String materialId, String batchNo, String qualified, String rejected) throws Exception {
        List<TransferRequest.Line> lines = new ArrayList<>();
        if (new BigDecimal(qualified).signum() > 0) {
            lines.add(new TransferRequest.Line(1L, Long.valueOf(materialId), batchNo, null, new BigDecimal(qualified), JudgeResult.QUALIFIED, null));
        }
        if (new BigDecimal(rejected).signum() > 0) {
            lines.add(new TransferRequest.Line(2L, Long.valueOf(materialId), batchNo, null, new BigDecimal(rejected), JudgeResult.REJECTED, null));
        }
        List<Long> ids = docApi.createTransfer(new TransferRequest(TransferType.INSPECTION, source("QC_INSPECTION"), SOURCE_ID.incrementAndGet(),
                Long.valueOf(W_QC), null, null, null, lines));
        for (Long id : ids) ok(doPost("/api/inventory/transfers/" + id + "/confirm", admin, Map.of()));
    }

    // ==================== 文件 ====================

    protected String uploadPdf() throws Exception {
        String body = mockMvc.perform(multipart("/api/system/files").file(new MockMultipartFile("file", "cert.pdf", "application/pdf", PDF))
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ok(read(body)).at("/id").asText();
    }

    protected JsonNode upload(String url, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        String body = mockMvc.perform(multipart(url).file(file).header("Authorization", admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body);
    }

    static byte[] openingXlsx(List<List<String>> rows) throws Exception {
        String[] head = {"仓库编码*", "库位编码", "物料编码*", "批次号", "供应商批号", "生产日期", "到期日期", "数量*", "单价*", "序列号"};
        return xlsx("期初库存", head, rows);
    }

    static byte[] xlsx(String sheet, String[] head, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet(sheet);
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            s.createRow(1).createCell(0).setCellValue("说明");
            for (int r = 0; r < rows.size(); r++) {
                Row row = s.createRow(r + 2);
                for (int c = 0; c < rows.get(r).size(); c++) row.createCell(c).setCellValue(rows.get(r).get(c));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    protected void setParam(String key, String value) throws Exception {
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", key, "value", value))));
    }

    protected void resetParam(String key) throws Exception {
        ok(doPost("/api/system/params/" + key + "/reset", admin, null));
    }
}
