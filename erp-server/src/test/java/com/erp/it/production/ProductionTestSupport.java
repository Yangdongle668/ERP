package com.erp.it.production;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
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
 * 生产集成测试的公共准备：库存期间与期初、物料（原材料 / 辅料 / 半成品 / 成品）、BOM、工艺路线、库存、生产订单、仓库单据确认。
 * 每个测试使用独立的新物料，数据互不影响。
 */
abstract class ProductionTestSupport extends AbstractIntegrationTest {

    static final String CAT_RAW = "501";
    static final String CAT_AUX = "505";
    static final String CAT_SEMI = "506";
    static final String CAT_FG = "507";
    static final String W_RAW = "801";
    static final String W_SEMI = "802";
    static final String W_FG = "803";
    static final String W_AUX = "807";
    private static final AtomicLong SOURCE_ID = new AtomicLong(9_500_000);
    private static volatile boolean ready;

    protected String admin;

    @Autowired
    protected InventoryDocApi docApi;
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
            String m = material("期初物料", CAT_AUX, "AUXILIARY", "NONE");
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(code(m))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        ready = true;
    }

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
        String[] row = {"W-AUX", "", materialCode, "", "", "", "", "10", "2", ""};
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

    /** 新建并启用物料；tracking：NONE / BATCH */
    protected String material(String name, String category, String type, String tracking) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", category);
        m.put("name", name + uniq());
        m.put("materialType", type);
        m.put("baseUom", "PCS");
        m.put("tracking", tracking);
        m.put("iqcRequired", false);
        m.put("fqcRequired", false);
        String id = ok(doPost("/api/engineering/materials", admin, m)).asText();
        ok(doPost("/api/engineering/materials/" + id + "/enable", admin, null));
        return id;
    }

    protected String fg(String name) throws Exception {
        return material(name, CAT_FG, "FINISHED", "NONE");
    }

    protected String raw(String name) throws Exception {
        return material(name, CAT_RAW, "RAW", "NONE");
    }

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    protected static Map<String, Object> bomLine(String componentId, Object qtyPer, Object scrap, String method, Integer seq) {
        Map<String, Object> l = new HashMap<>();
        l.put("componentId", componentId);
        l.put("qtyPer", qtyPer);
        l.put("scrapRate", scrap);
        l.put("issueMethod", method);
        l.put("operationSeq", seq);
        return l;
    }

    /** 新建并审核 BOM（基数 1） */
    protected String bom(String parentId, List<Map<String, Object>> lines) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("materialId", parentId);
        b.put("baseQty", 1);
        b.put("lines", lines);
        String id = ok(doPost("/api/engineering/boms", admin, b)).at("/id").asText();
        assertThat(ok(doPost("/api/engineering/boms/" + id + "/submit", admin, null)).asText()).isEqualTo("APPROVED");
        return id;
    }

    protected String workCenter() throws Exception {
        Map<String, Object> w = new HashMap<>();
        w.put("code", "WC" + uniq());
        w.put("name", "产线" + uniq());
        w.put("deptId", "100");
        w.put("wcType", "LINE");
        w.put("hoursPerShift", 10);
        w.put("shiftCount", 2);
        w.put("efficiencyPct", 0.85);
        w.put("laborRate", 30);
        return ok(doPost("/api/engineering/work-centers", admin, w)).asText();
    }

    /** 新建并审核工艺路线；steps：[工序号, 工序, 是否报工点, 是否检验点] */
    protected String routing(String materialId, String wc, Object[]... steps) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] s : steps) {
            Map<String, Object> m = new HashMap<>();
            m.put("seq", s[0]);
            m.put("operation", s[1]);
            m.put("workCenterId", wc);
            m.put("runSeconds", 36);
            m.put("isReportPoint", s[2]);
            m.put("isInspectionPoint", s.length > 3 && Boolean.TRUE.equals(s[3]));
            list.add(m);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("materialId", materialId);
        r.put("steps", list);
        String id = ok(doPost("/api/engineering/routings", admin, r)).at("/id").asText();
        ok(doPost("/api/engineering/routings/" + id + "/approve", admin, null));
        return id;
    }

    // ==================== 库存 ====================

    /** 其他入库并确认 */
    protected void stock(String materialId, String warehouseId, String qty, String batchNo) throws Exception {
        long sid = SOURCE_ID.incrementAndGet();
        Long in = docApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, new SourceRef("MFG_IT", sid, "MFG_IT-" + sid), Long.valueOf(warehouseId),
                null, null, null, List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), batchNo, null,
                LocalDate.now(), null, null)))).get(0);
        ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    /** 确认出库单；qty / batch 按物料覆盖实发数量与批次（为空按申请数量、不指定批次） */
    protected void confirmStockOut(String outId, Map<String, String> qty, Map<String, String> batch) throws Exception {
        JsonNode d = ok(doGet("/api/inventory/stock-outs/" + outId, admin));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode l : d.at("/lines")) {
            String mid = l.at("/materialId").asText();
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.at("/id").asText());
            m.put("materialId", mid);
            m.put("uom", l.at("/uom").asText());
            m.put("requestQty", l.at("/requestQty").decimalValue());
            m.put("qty", qty != null && qty.containsKey(mid) ? new BigDecimal(qty.get(mid)) : l.at("/requestQty").decimalValue());
            if (batch != null && batch.containsKey(mid)) m.put("batchNo", batch.get(mid));
            m.put("sourceLineId", l.at("/sourceLineId").asText());
            lines.add(m);
        }
        ok(doPost("/api/inventory/stock-outs/" + outId + "/confirm", admin, Map.of("outLines", lines)));
    }

    /** 确认领料单生成的出库单 */
    protected void confirmIssue(String issueId, Map<String, String> qty, Map<String, String> batch) throws Exception {
        String outs = ok(doGet("/api/production/issues/" + issueId, admin)).at("/stockOutIds").asText();
        for (String out : outs.split(",")) confirmStockOut(out, qty, batch);
    }

    /** 确认入库单（退料、完工入库） */
    protected void confirmStockIns(String ids) throws Exception {
        for (String in : ids.split(",")) ok(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()));
    }

    // ==================== 生产订单 ====================

    protected Map<String, Object> orderBody(String materialId, String qty) {
        Map<String, Object> o = new HashMap<>();
        o.put("materialId", materialId);
        o.put("qty", qty);
        o.put("planStart", LocalDate.now().toString());
        o.put("planEnd", LocalDate.now().plusDays(7).toString());
        return o;
    }

    /** 新建 → 提交（未配置审批流时直接已计划） */
    protected String plannedOrder(String materialId, String qty) throws Exception {
        String id = ok(doPost("/api/production/prod-orders", admin, orderBody(materialId, qty))).at("/id").asText();
        assertThat(ok(doPost("/api/production/prod-orders/" + id + "/submit", admin, null)).at("/status").asText()).isEqualTo("PLANNED");
        return id;
    }

    /** 已计划 → 下达（缺料时确认） */
    protected String releasedOrder(String materialId, String qty) throws Exception {
        String id = plannedOrder(materialId, qty);
        ok(doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of("confirmShortage", true)));
        return id;
    }

    protected JsonNode order(String id) throws Exception {
        return ok(doGet("/api/production/prod-orders/" + id, admin));
    }

    /** 用料行（按子件） */
    protected JsonNode materialLine(String orderId, String componentId) throws Exception {
        for (JsonNode m : order(orderId).at("/materials")) if (m.at("/componentId").asText().equals(componentId)) return m;
        throw new AssertionError("用料行不存在：" + componentId);
    }

    protected static Map<String, Object> report(String orderId, int seq, Object good, Object defect, Object scrap) {
        Map<String, Object> r = new HashMap<>();
        r.put("prodOrderId", orderId);
        r.put("operationSeq", seq);
        r.put("goodQty", good);
        r.put("defectQty", defect);
        r.put("scrapQty", scrap);
        r.put("workHours", 8);
        r.put("shift", "DAY");
        if (scrap != null && new BigDecimal(String.valueOf(scrap)).signum() > 0) r.put("scrapReason", "PROCESS");
        return r;
    }

    /** 报工（参数默认自动审核），返回报工单 ID */
    protected String reportOk(Map<String, Object> body) throws Exception {
        JsonNode r = ok(doPost("/api/production/reports", admin, body));
        assertThat(r.at("/status").asText()).isEqualTo("APPROVED");
        return r.at("/id").asText();
    }

    /** 按订单领全部未领（按行），提交并返回领料单 ID */
    protected List<String> issueAll(String orderId) throws Exception {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode c : ok(doGet("/api/production/issues/candidates?prodOrderId=" + orderId, admin))) {
            lines.add(Map.of("materialLineId", c.at("/materialLineId").asText(), "requestQty", c.at("/requestQty").decimalValue()));
        }
        JsonNode r = ok(doPost("/api/production/issues", admin, Map.of("prodOrderId", orderId, "lines", lines)));
        List<String> ids = new ArrayList<>();
        for (JsonNode id : r.at("/ids")) {
            ok(doPost("/api/production/issues/" + id.asText() + "/submit", admin, null));
            ids.add(id.asText());
        }
        return ids;
    }
}
