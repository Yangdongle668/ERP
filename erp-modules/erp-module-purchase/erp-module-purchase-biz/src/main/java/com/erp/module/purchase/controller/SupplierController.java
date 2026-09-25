package com.erp.module.purchase.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.SupplierVOs.MaterialSave;
import com.erp.module.purchase.controller.vo.SupplierVOs.QualitySummary;
import com.erp.module.purchase.controller.vo.SupplierVOs.StatusResult;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierBrief;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierDetail;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierMaterialResp;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierQuery;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierRow;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierSave;
import com.erp.module.purchase.service.PurExportSupport;
import com.erp.module.purchase.service.supplier.SupplierImportService;
import com.erp.module.purchase.service.supplier.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 供应商（需求 07-01 第 6 节） */
@Tag(name = "资材 - 供应商")
@RestController
@RequestMapping("/api/purchase")
public class SupplierController {

    static final Map<String, String> STATUS = Map.of("POTENTIAL", "潜在", "PENDING", "审批中", "QUALIFIED", "合格", "SUSPENDED", "暂停", "ELIMINATED", "淘汰");

    static final List<ExcelColumn<SupplierRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("code", "编码", SupplierRow::code),
            ExcelColumn.text("shortName", "简称", SupplierRow::shortName),
            ExcelColumn.text("name", "名称", SupplierRow::name),
            ExcelColumn.text("supplierType", "类型", SupplierRow::supplierType),
            ExcelColumn.text("level", "等级", SupplierRow::level),
            ExcelColumn.text("country", "国家", SupplierRow::country),
            ExcelColumn.text("buyerName", "采购员", SupplierRow::buyerName),
            ExcelColumn.text("currency", "币别", SupplierRow::currency),
            ExcelColumn.text("paymentTermName", "付款条件", SupplierRow::paymentTermName),
            ExcelColumn.text("primaryContact", "主联系人", SupplierRow::primaryContact),
            ExcelColumn.text("certExpired", "资质过期", r -> r.certExpired() ? "是" : ""),
            ExcelColumn.text("status", "状态", r -> STATUS.get(r.status())),
            ExcelColumn.date("qualifiedAt", "准入日期", SupplierRow::qualifiedAt));

    private final SupplierService service;
    private final SupplierImportService importService;
    private final PurExportSupport exportSupport;

    public SupplierController(SupplierService service, SupplierImportService importService, PurExportSupport exportSupport) {
        this.service = service;
        this.importService = importService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("@ss.has('pur:supplier:query')")
    public CommonResult<PageResult<SupplierRow>> page(@Valid SupplierQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "选择器远程搜索（登录即可）：编码前缀、名称、简称；statuses 过滤状态；ids 用于回显")
    @GetMapping("/suppliers/search")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<SupplierBrief>> search(@RequestParam(required = false) String keyword, @RequestParam(required = false) String statuses,
                                                    @RequestParam(required = false) String ids, @RequestParam(defaultValue = "20") int limit) {
        return CommonResult.success(service.search(keyword, statuses, ids, limit));
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("@ss.has('pur:supplier:query')")
    public CommonResult<SupplierDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("@ss.has('pur:supplier:create')")
    public CommonResult<Long> create(@Valid @RequestBody SupplierSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("@ss.has('pur:supplier:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/suppliers/{id}")
    @PreAuthorize("@ss.has('pur:supplier:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "提交准入（R03）：无审批流时直接合格")
    @PostMapping("/suppliers/{id}/qualify")
    @PreAuthorize("@ss.has('pur:supplier:qualify')")
    public CommonResult<String> qualify(@PathVariable Long id) {
        return CommonResult.success(service.qualify(id));
    }

    @PostMapping("/suppliers/{id}/suspend")
    @PreAuthorize("@ss.has('pur:supplier:suspend')")
    public CommonResult<StatusResult> suspend(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        return CommonResult.success(service.suspend(id, req == null ? null : req.reason()));
    }

    @PostMapping("/suppliers/{id}/resume")
    @PreAuthorize("@ss.has('pur:supplier:suspend')")
    public CommonResult<StatusResult> resume(@PathVariable Long id) {
        return CommonResult.success(service.resume(id));
    }

    @PostMapping("/suppliers/{id}/eliminate")
    @PreAuthorize("@ss.has('pur:supplier:eliminate')")
    public CommonResult<StatusResult> eliminate(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        return CommonResult.success(service.eliminate(id, req == null ? null : req.reason()));
    }

    @Operation(summary = "到货与质量：最近 12 个月到货批次、IQC 合格率")
    @GetMapping("/suppliers/{id}/quality")
    @PreAuthorize("@ss.has('pur:supplier:query')")
    public CommonResult<QualitySummary> quality(@PathVariable Long id) {
        return CommonResult.success(service.quality(id));
    }

    // ==================== 可供物料 ====================

    @GetMapping("/suppliers/{id}/materials")
    @PreAuthorize("@ss.has('pur:supplier:query')")
    public CommonResult<List<SupplierMaterialResp>> materials(@PathVariable Long id) {
        return CommonResult.success(service.materials(id));
    }

    @PostMapping("/suppliers/{id}/materials")
    @PreAuthorize("@ss.has('pur:supplier:update')")
    public CommonResult<Long> addMaterial(@PathVariable Long id, @Valid @RequestBody MaterialSave req) {
        return CommonResult.success(service.saveMaterial(id, null, req));
    }

    @PutMapping("/suppliers/{id}/materials/{materialLineId}")
    @PreAuthorize("@ss.has('pur:supplier:update')")
    public CommonResult<Long> updateMaterial(@PathVariable Long id, @PathVariable Long materialLineId, @Valid @RequestBody MaterialSave req) {
        return CommonResult.success(service.saveMaterial(id, materialLineId, req));
    }

    @DeleteMapping("/suppliers/{id}/materials/{materialLineId}")
    @PreAuthorize("@ss.has('pur:supplier:update')")
    public CommonResult<Void> deleteMaterial(@PathVariable Long id, @PathVariable Long materialLineId) {
        service.deleteMaterial(id, materialLineId);
        return CommonResult.success();
    }

    @Operation(summary = "某物料的可供供应商（登录即可，下单、申请选择建议供应商）")
    @GetMapping("/supplier-materials")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<SupplierMaterialResp>> suppliersOfMaterial(@RequestParam Long materialId) {
        return CommonResult.success(service.suppliersOfMaterial(materialId));
    }

    // ==================== 导入导出 ====================

    @GetMapping("/suppliers/export")
    @PreAuthorize("@ss.has('pur:supplier:export')")
    public void export(@Valid SupplierQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "供应商", EXPORT_COLUMNS, q.getColumns(), limit -> service.listForExport(q, limit));
    }

    @GetMapping("/suppliers/import-template")
    @PreAuthorize("@ss.has('pur:supplier:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "供应商", SupplierImportService.COLUMNS);
    }

    @PostMapping(value = "/suppliers/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('pur:supplier:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, SupplierImportService.COLUMNS);
        Map<Integer, String> actions = importService.check(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "供应商导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(SupplierImportService.COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    /** @param paymentTermId 导入的供应商使用的默认付款条件（导入选项） */
    @PostMapping(value = "/suppliers/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('pur:supplier:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean partial,
                                               @RequestParam(required = false) Long paymentTermId) {
        if (paymentTermId == null) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "请选择默认付款条件");
        List<ImportRow> rows = ExcelSupport.read(file, SupplierImportService.COLUMNS);
        importService.check(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(importService.doImport(rows.stream().filter(r -> !r.hasError()).toList(), paymentTermId));
    }
}
