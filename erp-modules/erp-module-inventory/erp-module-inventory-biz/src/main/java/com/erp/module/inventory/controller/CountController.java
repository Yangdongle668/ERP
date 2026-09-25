package com.erp.module.inventory.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.inventory.controller.vo.CountVOs.AddLine;
import com.erp.module.inventory.controller.vo.CountVOs.CountDetail;
import com.erp.module.inventory.controller.vo.CountVOs.CountLineRow;
import com.erp.module.inventory.controller.vo.CountVOs.CountQuery;
import com.erp.module.inventory.controller.vo.CountVOs.CountRow;
import com.erp.module.inventory.controller.vo.CountVOs.CountSave;
import com.erp.module.inventory.controller.vo.CountVOs.LineInput;
import com.erp.module.inventory.controller.vo.CountVOs.LineQuery;
import com.erp.module.inventory.controller.vo.CountVOs.PendingDocs;
import com.erp.module.inventory.controller.vo.StockDocVOs.ReasonReq;
import com.erp.module.inventory.service.doc.CountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 盘点（需求 08-06 第 6 节） */
@Tag(name = "仓库 - 盘点")
@RestController("invCountController")
@RequestMapping("/api/inventory/counts")
public class CountController {

    /** 盘点表：导出包含行 ID，填写实盘数量后导入 */
    static List<ExcelColumn<CountLineRow>> sheetColumns(boolean book) {
        List<ExcelColumn<CountLineRow>> cols = new ArrayList<>(List.of(
                ExcelColumn.text("id", "行ID", r -> String.valueOf(r.id())),
                ExcelColumn.text("warehouseName", "仓库", CountLineRow::warehouseName),
                ExcelColumn.text("locationCode", "库位", CountLineRow::locationCode),
                ExcelColumn.text("materialCode", "物料编码", CountLineRow::materialCode),
                ExcelColumn.text("materialName", "名称", CountLineRow::materialName),
                ExcelColumn.text("materialSpec", "规格", CountLineRow::materialSpec),
                ExcelColumn.text("baseUom", "单位", CountLineRow::baseUom),
                ExcelColumn.text("batchNo", "批次", CountLineRow::batchNo)));
        if (book) cols.add(ExcelColumn.number("bookQty", "账面数量", CountLineRow::bookQty));
        cols.add(ExcelColumn.number("countQty", "实盘数量", CountLineRow::countQty));
        cols.add(ExcelColumn.text("reason", "差异原因", CountLineRow::reason));
        cols.add(ExcelColumn.text("remark", "备注", CountLineRow::remark));
        return cols;
    }

    static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("id", "行ID", true, null),
            ExcelColumn.<Object>input("countQty", "实盘数量", false, null).ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.input("reason", "差异原因", false, "字典编码"),
            ExcelColumn.input("remark", "备注", false, null));

    private final CountService service;

    public CountController(CountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('inv:count:query')")
    public CommonResult<PageResult<CountRow>> page(@Valid CountQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('inv:count:query')")
    public CommonResult<CountDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('inv:count:create')")
    public CommonResult<Long> create(@Valid @RequestBody CountSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('inv:count:create')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody CountSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('inv:count:create')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "生成盘点表前的提示：范围内未确认的单据")
    @GetMapping("/{id}/pending-docs")
    @PreAuthorize("@ss.has('inv:count:create')")
    public CommonResult<PendingDocs> pendingDocs(@PathVariable Long id) {
        return CommonResult.success(service.pendingDocs(id));
    }

    @Operation(summary = "生成盘点表：快照账面数量并冻结范围，返回行数")
    @PostMapping("/{id}/generate")
    @PreAuthorize("@ss.has('inv:count:create')")
    public CommonResult<Integer> generate(@PathVariable Long id) {
        return CommonResult.success(service.generate(id));
    }

    @GetMapping("/{id}/lines")
    @PreAuthorize("@ss.has('inv:count:query')")
    public CommonResult<PageResult<CountLineRow>> lines(@PathVariable Long id, @Valid LineQuery q) {
        return CommonResult.success(service.lines(id, q));
    }

    @Operation(summary = "批量保存录入（实盘、复盘、差异原因、备注）")
    @PutMapping("/{id}/lines")
    @PreAuthorize("@ss.has('inv:count:input')")
    public CommonResult<Void> input(@PathVariable Long id, @Valid @RequestBody List<LineInput> inputs) {
        service.input(id, inputs);
        return CommonResult.success();
    }

    @Operation(summary = "新增盘点外物料")
    @PostMapping("/{id}/lines/add")
    @PreAuthorize("@ss.has('inv:count:input')")
    public CommonResult<Long> addLine(@PathVariable Long id, @Valid @RequestBody AddLine req) {
        return CommonResult.success(service.addLine(id, req));
    }

    /** 导出盘点表：与导入模板格式一致（第一行列名、第二行说明、第三行起数据），填写实盘数量后可直接导入 */
    @GetMapping("/{id}/export-sheet")
    @PreAuthorize("@ss.has('inv:count:input')")
    public void exportSheet(@PathVariable Long id, HttpServletResponse response) throws IOException {
        List<CountLineRow> rows = service.sheet(id);
        List<ExcelColumn<CountLineRow>> cols = sheetColumns(service.isBookVisible(id));
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("盘点表");
            Row head = sheet.createRow(0);
            Row note = sheet.createRow(1);
            for (int i = 0; i < cols.size(); i++) {
                head.createCell(i).setCellValue(cols.get(i).label());
                sheet.setColumnWidth(i, 16 * 256);
            }
            note.createCell(0).setCellValue("不要修改行ID；填写“实盘数量”，有差异时填写差异原因编码后导入");
            int r = 2;
            for (CountLineRow line : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < cols.size(); i++) {
                    Object v = cols.get(i).getter().apply(line);
                    if (v instanceof BigDecimal b) row.createCell(i).setCellValue(b.doubleValue());
                    else if (v != null) row.createCell(i).setCellValue(String.valueOf(v));
                }
            }
            sheet.createFreezePane(0, 2);
            wb.write(out);
            ExcelSupport.writeBytes(response, "盘点表.xlsx", out.toByteArray());
        }
    }

    @Operation(summary = "导入实盘的模板：即当前盘点表（含行 ID）")
    @GetMapping("/{id}/import-template")
    @PreAuthorize("@ss.has('inv:count:input')")
    public void importTemplate(@PathVariable Long id, HttpServletResponse response) throws IOException {
        exportSheet(id, response);
    }

    @PostMapping(value = "/{id}/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('inv:count:input')")
    public CommonResult<ImportCheckResult> importCheck(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report, HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        Map<Integer, String> actions = service.checkImport(id, rows);
        if (report) {
            ExcelSupport.writeBytes(response, "实盘导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @Operation(summary = "导入实盘：partial=true 时只导入正确行")
    @PostMapping(value = {"/{id}/import", "/{id}/import-count"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('inv:count:input')")
    public CommonResult<ImportResult> importCount(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                  @RequestParam(defaultValue = "false") boolean partial) {
        return CommonResult.success(service.importCount(id, ExcelSupport.read(file, IMPORT_COLUMNS), partial));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('inv:count:submit')")
    public CommonResult<String> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @Operation(summary = "审核（无审批流时）：生成盘盈入库、盘亏出库并自动确认")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.has('inv:count:approve')")
    public CommonResult<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.has('inv:count:approve')")
    public CommonResult<Void> reject(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.reject(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('inv:count:void')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('inv:count:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
