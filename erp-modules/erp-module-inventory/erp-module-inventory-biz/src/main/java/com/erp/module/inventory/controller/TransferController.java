package com.erp.module.inventory.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.inventory.controller.vo.StockDocVOs.BatchReq;
import com.erp.module.inventory.controller.vo.StockDocVOs.BatchResult;
import com.erp.module.inventory.controller.vo.StockDocVOs.ConfirmReq;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocQuery;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocRow;
import com.erp.module.inventory.controller.vo.StockDocVOs.QuickCounts;
import com.erp.module.inventory.controller.vo.StockDocVOs.ReasonReq;
import com.erp.module.inventory.controller.vo.StockDocVOs.TransferDetail;
import com.erp.module.inventory.controller.vo.StockDocVOs.TransferSave;
import com.erp.module.inventory.service.InventoryExportSupport;
import com.erp.module.inventory.service.doc.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 调拨单（需求 08-05 第 5 节） */
@Tag(name = "仓库 - 调拨单")
@RestController("invTransferController")
@RequestMapping("/api/inventory/transfers")
public class TransferController {

    static final List<ExcelColumn<DocRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", DocRow::docNo),
            ExcelColumn.text("type", "类型", DocRow::type),
            ExcelColumn.text("warehouseName", "调出仓", DocRow::warehouseName),
            ExcelColumn.text("toWarehouseName", "调入仓", DocRow::toWarehouseName),
            ExcelColumn.text("sourceNo", "来源单号", DocRow::sourceNo),
            ExcelColumn.text("materialSummary", "物料", DocRow::materialSummary),
            ExcelColumn.number("totalQty", "总数量", DocRow::totalQty),
            ExcelColumn.date("docDate", "单据日期", DocRow::docDate),
            ExcelColumn.text("status", "状态", DocRow::status),
            ExcelColumn.text("confirmedByName", "确认人", DocRow::confirmedByName),
            ExcelColumn.dateTime("confirmedAt", "确认时间", DocRow::confirmedAt));

    private final TransferService service;
    private final InventoryExportSupport exportSupport;

    public TransferController(TransferService service, InventoryExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('inv:transfer:query')")
    public CommonResult<PageResult<DocRow>> page(@Valid DocQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "快捷筛选计数：待处理、今日已确认")
    @GetMapping("/quick-counts")
    @PreAuthorize("@ss.has('inv:transfer:query')")
    public CommonResult<QuickCounts> quickCounts() {
        return CommonResult.success(service.quickCounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('inv:transfer:query')")
    public CommonResult<TransferDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('inv:transfer:create')")
    public CommonResult<Long> create(@Valid @RequestBody TransferSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('inv:transfer:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody TransferSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('inv:transfer:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "确认：请求体可带补充信息")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.has('inv:transfer:confirm')")
    public CommonResult<Void> confirm(@PathVariable Long id, @Valid @RequestBody(required = false) ConfirmReq req) {
        service.confirm(id, req == null ? null : req.docDate(), req == null ? null : req.transferLines());
        return CommonResult.success();
    }

    @PostMapping("/batch-confirm")
    @PreAuthorize("@ss.has('inv:transfer:confirm')")
    public CommonResult<BatchResult> batchConfirm(@RequestBody BatchReq req) {
        return CommonResult.success(service.batchConfirm(req.ids()));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('inv:transfer:void')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('inv:transfer:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('inv:transfer:query')")
    public void export(@Valid DocQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "调拨单", EXPORT_COLUMNS, columns, limit -> service.listForExport(q, limit));
    }
}
