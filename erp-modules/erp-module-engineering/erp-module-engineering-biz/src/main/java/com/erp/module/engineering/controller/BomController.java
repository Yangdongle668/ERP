package com.erp.module.engineering.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.controller.vo.BomVOs.BomDetail;
import com.erp.module.engineering.controller.vo.BomVOs.BomQuery;
import com.erp.module.engineering.controller.vo.BomVOs.BomRow;
import com.erp.module.engineering.controller.vo.BomVOs.BomSave;
import com.erp.module.engineering.controller.vo.BomVOs.CompareResult;
import com.erp.module.engineering.controller.vo.BomVOs.CostResult;
import com.erp.module.engineering.controller.vo.BomVOs.ExplodeRow;
import com.erp.module.engineering.controller.vo.BomVOs.NextVersion;
import com.erp.module.engineering.controller.vo.BomVOs.ReasonReq;
import com.erp.module.engineering.controller.vo.BomVOs.SaveResult;
import com.erp.module.engineering.controller.vo.BomVOs.WhereUsedRow;
import com.erp.module.engineering.service.BomExcelService;
import com.erp.module.engineering.service.BomQueryService;
import com.erp.module.engineering.service.BomService;
import com.erp.module.engineering.service.ExportSupport;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** BOM（需求 05-03 第 7 节） */
@Tag(name = "研发工程 - BOM")
@RestController
@RequestMapping("/api/engineering/boms")
public class BomController {

    private final BomService bomService;
    private final BomQueryService queryService;
    private final BomExcelService excelService;
    private final ExportSupport exportSupport;

    public BomController(BomService bomService, BomQueryService queryService, BomExcelService excelService, ExportSupport exportSupport) {
        this.bomService = bomService;
        this.queryService = queryService;
        this.excelService = excelService;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('eng:bom:query')")
    public CommonResult<PageResult<BomRow>> page(@Valid BomQuery q) {
        return CommonResult.success(queryService.page(q));
    }

    @Operation(summary = "详情（含行、替代料）")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:bom:query')")
    public CommonResult<BomDetail> get(@PathVariable Long id) {
        return CommonResult.success(queryService.detail(id));
    }

    @Operation(summary = "父件的下一版本号（新建页显示 V{n}）")
    @GetMapping("/next-version")
    @PreAuthorize("@ss.has('eng:bom:create')")
    public CommonResult<NextVersion> nextVersion(@RequestParam Long materialId) {
        return CommonResult.success(new NextVersion(materialId, queryService.nextVersion(materialId)));
    }

    @Operation(summary = "多级展开（qty 为父件数量，levels ≤ 0 表示全部）")
    @GetMapping("/{id}/explode")
    @PreAuthorize("@ss.has('eng:bom:query')")
    public CommonResult<List<ExplodeRow>> explode(@PathVariable Long id, @RequestParam(defaultValue = "1") BigDecimal qty,
                                                  @RequestParam(defaultValue = "0") int levels) {
        return CommonResult.success(queryService.explodeTree(id, qty, levels));
    }

    @Operation(summary = "反查（多级向上，列出顶层）")
    @GetMapping("/where-used")
    @PreAuthorize("@ss.has('eng:bom:query')")
    public CommonResult<List<WhereUsedRow>> whereUsed(@RequestParam Long materialId) {
        return CommonResult.success(queryService.whereUsedTree(materialId));
    }

    @Operation(summary = "版本比较")
    @GetMapping("/compare")
    @PreAuthorize("@ss.has('eng:bom:query')")
    public CommonResult<CompareResult> compare(@RequestParam Long leftId, @RequestParam Long rightId) {
        return CommonResult.success(queryService.compare(leftId, rightId));
    }

    @Operation(summary = "成本卷算（材料标准成本）")
    @GetMapping("/{id}/cost")
    @PreAuthorize("@ss.has('eng:bom:cost')")
    public CommonResult<CostResult> cost(@PathVariable Long id) {
        return CommonResult.success(queryService.cost(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:bom:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody BomSave req) {
        return CommonResult.success(bomService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:bom:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody BomSave req) {
        return CommonResult.success(bomService.update(id, req));
    }

    @Operation(summary = "新建版本（复制为新草稿），返回新 BOM ID")
    @PostMapping("/{id}/new-version")
    @PreAuthorize("@ss.has('eng:bom:create')")
    public CommonResult<Long> newVersion(@PathVariable Long id) {
        return CommonResult.success(bomService.newVersion(id));
    }

    @Operation(summary = "提交：发起审批；未配置审批流时直接审核。返回结果状态")
    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('eng:bom:submit')")
    public CommonResult<String> submit(@PathVariable Long id) {
        return CommonResult.success(bomService.submit(id));
    }

    @PostMapping("/{id}/unapprove")
    @PreAuthorize("@ss.has('eng:bom:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody ReasonReq req) {
        if (req == null || req.reason() == null || req.reason().isBlank()) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "请填写反审核原因");
        bomService.unapprove(id, req.reason().trim());
        return CommonResult.success();
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("@ss.has('eng:bom:set-default')")
    public CommonResult<Void> setDefault(@PathVariable Long id) {
        bomService.setDefault(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('eng:bom:disable')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        bomService.disable(id);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:bom:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        bomService.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "打印数据")
    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('eng:bom:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(bomService.printData(id));
    }

    // ==================== 导入导出 ====================

    @Operation(summary = "导出：mode=SINGLE 单层 / MULTI 多级展开；ids 为勾选行")
    @GetMapping("/export")
    @PreAuthorize("@ss.has('eng:bom:export')")
    public void export(@Valid BomQuery q, HttpServletResponse response) throws IOException {
        boolean multi = "MULTI".equals(q.getMode());
        exportSupport.export(response, multi ? "BOM多级展开" : "BOM", multi ? BomExcelService.multiColumns() : BomExcelService.singleColumns(),
                null, limit -> excelService.exportRows(queryService.listForExport(q, limit), multi));
    }

    @GetMapping("/import-template")
    @PreAuthorize("@ss.has('eng:bom:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "BOM", BomExcelService.IMPORT_COLUMNS);
    }

    @PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:bom:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, BomExcelService.IMPORT_COLUMNS);
        Map<Integer, String> actions = excelService.check(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "BOM导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(BomExcelService.IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    /** @param submit 导入后提交审核（需要提交权限） */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:bom:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file,
                                               @RequestParam(defaultValue = "false") boolean partial,
                                               @RequestParam(defaultValue = "false") boolean submit) {
        if (submit && !SecurityUtils.getLoginUser().hasPermission("eng:bom:submit")) throw new BizException(GlobalErrorCodes.FORBIDDEN);
        List<ImportRow> rows = ExcelSupport.read(file, BomExcelService.IMPORT_COLUMNS);
        excelService.check(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        // 部分导入：同一 BOM 中有错误行时整张 BOM 不导入
        return CommonResult.success(excelService.doImport(rows, submit));
    }
}
