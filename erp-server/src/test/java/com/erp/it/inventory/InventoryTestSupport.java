package com.erp.it.inventory;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 仓库集成测试的公共准备。库存期间与期初是全局状态：第一次运行时设置启用期间（本月）、导入期初并完成期初，
 * 顺带验证“期初未完成不能确认其他单据”（INV-PRD-T02）。每个测试使用独立的新物料，库存互不影响。
 */
abstract class InventoryTestSupport extends AbstractIntegrationTest {

    /** 初始数据：研发工程类别与仓库 */
    static final String CAT_ELEC = "503";
    static final String CAT_AUX = "505";
    static final String W_RAW = "801";
    static final String W_FG = "803";
    static final String W_ELEC = "805";
    static final String W_AUX = "807";
    static final String W_NG = "808";
    static final String W_QC = "809";

    private static volatile boolean ready;
    private static final AtomicLong SOURCE_ID = new AtomicLong(9_000_000);

    protected String admin;

    @Autowired
    protected InventoryDocApi docApi;

    @BeforeEach
    void prepare() throws Exception {
        admin = loginAsAdmin();
        ensureReady();
    }

    private synchronized void ensureReady() throws Exception {
        if (ready) return;
        JsonNode info = ok(doGet("/api/inventory/opening", admin));
        if (info.at("/period").isMissingNode() || info.at("/period").isNull()) {
            ok(doPost("/api/inventory/periods/init", admin, Map.of("period", YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
            info = ok(doGet("/api/inventory/opening", admin));
        }
        if (!info.at("/completed").asBoolean()) {
            // 期初导入：辅料仓 1 个物料 10 个，单价 2
            String m = material(CAT_AUX, "期初物料", Map.of("tracking", "NONE", "iqcRequired", false));
            JsonNode r = ok(upload("/api/inventory/opening/import", openingXlsx(List.of(List.of("W-AUX", "", code(m), "", "", "", "", "10", "2", "")))));
            assertThat(r.at("/success").asInt()).isEqualTo(1);
            JsonNode stock = ok(doGet("/api/inventory/stocks?materialId=" + m + "&groupBy=MATERIAL", admin));
            assertThat(stock.at("/list/0/onHandQty").decimalValue()).isEqualByComparingTo("10");
            // INV-PRD-T02：期初未完成时确认其他单据
            Long in = docApi.createStockIn(new StockInRequest(StockInType.PURCHASE_IN, source("PUR_RECEIPT"), Long.valueOf(W_AUX), null, null, null,
                    List.of(new StockInRequest.Line(1L, Long.valueOf(m), null, BigDecimal.ONE, null, null, null, null, null)))).get(0);
            assertError(doPost("/api/inventory/stock-ins/" + in + "/confirm", admin, Map.of()), "请先完成期初库存导入");
            ok(doPost("/api/inventory/opening/complete", admin, null));
        }
        ready = true;
    }

    // ==================== 物料 ====================

    /** 新建并启用物料；extra 覆盖默认属性（tracking、iqcRequired、issueRule、shelfLifeDays、safetyStock 等） */
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

    protected String code(String materialId) throws Exception {
        return ok(doGet("/api/engineering/materials/" + materialId, admin)).at("/code").asText();
    }

    // ==================== 单据 ====================

    protected static SourceRef source(String type) {
        long id = SOURCE_ID.incrementAndGet();
        return new SourceRef(type, id, type + "-" + id);
    }

    /** 业务生成采购入库单（仓库为空时按规则路由），返回单据 ID */
    protected List<Long> purchaseIn(String materialId, String warehouseId, String qty, String batchNo, LocalDate date) {
        return docApi.createStockIn(new StockInRequest(StockInType.PURCHASE_IN, source("PUR_RECEIPT"), warehouseId == null ? null : Long.valueOf(warehouseId),
                date, null, null, List.of(new StockInRequest.Line(1L, Long.valueOf(materialId), null, new BigDecimal(qty), batchNo, null, null,
                new BigDecimal("1.5"), null))));
    }

    /** 采购入库并确认 */
    protected Long receive(String materialId, String warehouseId, String qty, String batchNo, LocalDate date) throws Exception {
        Long id = purchaseIn(materialId, warehouseId, qty, batchNo, date).get(0);
        ok(doPost("/api/inventory/stock-ins/" + id + "/confirm", admin, Map.of()));
        return id;
    }

    protected Long issue(StockOutType type, String materialId, String warehouseId, String qty) {
        return docApi.createStockOut(new StockOutRequest(type, source(type.name()), Long.valueOf(warehouseId), null, null, null, null, null,
                List.of(new StockOutRequest.Line(SOURCE_ID.incrementAndGet(), Long.valueOf(materialId), null, new BigDecimal(qty), null, null)))).get(0);
    }

    /** 某物料在某仓库的现存量（按物料 + 仓库汇总） */
    protected BigDecimal onHand(String materialId, String warehouseId) throws Exception {
        JsonNode list = ok(doGet("/api/inventory/stocks?materialId=" + materialId + "&warehouseIds=" + warehouseId + "&groupBy=WAREHOUSE&showZero=true", admin)).at("/list");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode n : list) sum = sum.add(n.at("/onHandQty").decimalValue());
        return sum;
    }

    protected JsonNode materialSummary(String materialId) throws Exception {
        return ok(doGet("/api/inventory/stocks?materialId=" + materialId + "&groupBy=MATERIAL&showZero=true", admin)).at("/list/0");
    }

    protected static LocalDate monthStart() {
        return LocalDate.now().withDayOfMonth(1);
    }

    protected void assertBizError(Runnable r, String message) {
        assertThatThrownBy(r::run).hasMessage(message);
    }

    // ==================== 文件 ====================

    protected JsonNode upload(String url, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        String body = mockMvc.perform(multipart(url).file(file).header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body);
    }

    /** 期初导入文件：列名行、说明行、数据行 */
    protected static byte[] openingXlsx(List<List<String>> rows) throws Exception {
        String[] head = {"仓库编码*", "库位编码", "物料编码*", "批次号", "供应商批号", "生产日期", "到期日期", "数量*", "单价*", "序列号"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("期初库存");
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
}
