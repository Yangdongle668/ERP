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
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.controller.vo.MaterialRespVO;
import com.erp.module.engineering.controller.vo.MaterialSaveReqVO;
import com.erp.module.engineering.controller.vo.MaterialVOs.BatchReq;
import com.erp.module.engineering.controller.vo.MaterialVOs.BatchResult;
import com.erp.module.engineering.controller.vo.MaterialVOs.DuplicateCheckReq;
import com.erp.module.engineering.controller.vo.MaterialVOs.DuplicateCheckResp;
import com.erp.module.engineering.controller.vo.MaterialVOs.MaterialBoms;
import com.erp.module.engineering.controller.vo.MaterialVOs.References;
import com.erp.module.engineering.controller.vo.MaterialVOs.Settings;
import com.erp.module.engineering.service.BomQueryService;
import com.erp.module.engineering.service.ExportSupport;
import com.erp.module.engineering.service.MaterialExcelService;
import com.erp.module.engineering.service.MaterialService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 物料（需求 05-02 第 6 节）。作为各模块 CRUD 接口的参考实现。 */
@Tag(name = "研发工程 - 物料")
@RestController
@RequestMapping("/api/engineering/materials")
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialExcelService excelService;
    private final BomQueryService bomQueryService;
    private final ExportSupport exportSupport;

    public MaterialController(MaterialService materialService, MaterialExcelService excelService, BomQueryService bomQueryService,
                              ExportSupport exportSupport) {
        this.materialService = materialService;
        this.excelService = excelService;
        this.bomQueryService = bomQueryService;
        this.exportSupport = exportSupport;
    }

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<PageResult<MaterialRespVO>> page(@Valid MaterialPageReqVO req) {
        return CommonResult.success(materialService.page(req));
    }

    @Operation(summary = "选择器远程搜索（登录即可）：编码前缀或名称/规格模糊；ids 用于回显")
    @GetMapping("/search")
    public CommonResult<List<MaterialRespVO>> search(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String types,
                                                     @RequestParam(required = false, defaultValue = "ENABLED") String status,
                                                     @RequestParam(required = false) String ids,
                                                     @RequestParam(defaultValue = "20") int limit) {
        List<Long> idList = ids == null || ids.isBlank() ? null
                : Arrays.stream(ids.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
        MaterialStatus st = status == null || status.isBlank() ? null : MaterialStatus.valueOf(status);
        return CommonResult.success(materialService.search(keyword, types, st, idList, limit));
    }

    @Operation(summary = "按编码精确查询启用物料（登录即可）")
    @GetMapping("/by-code/{code}")
    public CommonResult<MaterialRespVO> byCode(@PathVariable String code) {
        return CommonResult.success(materialService.getEnabledByCode(code));
    }

    @Operation(summary = "页面开关：启用是否审批、是否允许手工编码、查重方式、能否查看成本")
    @GetMapping("/settings")
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<Settings> settings() {
        return CommonResult.success(materialService.settings());
    }

    @Operation(summary = "详情（含全部属性组、单位换算）")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<MaterialRespVO> get(@PathVariable Long id) {
        return CommonResult.success(materialService.get(id));
    }

    @Operation(summary = "以本物料为父件的 BOM 版本与使用本物料的 BOM")
    @GetMapping("/{id}/boms")
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<MaterialBoms> boms(@PathVariable Long id) {
        return CommonResult.success(bomQueryService.materialBoms(id));
    }

    @Operation(summary = "停用前的引用统计")
    @GetMapping("/{id}/references")
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<References> references(@PathVariable Long id) {
        return CommonResult.success(materialService.references(id));
    }

    @Operation(summary = "查重")
    @PostMapping("/duplicate-check")
    @PreAuthorize("@ss.hasAny('eng:material:create', 'eng:material:update')")
    public CommonResult<DuplicateCheckResp> duplicateCheck(@RequestBody DuplicateCheckReq req) {
        return CommonResult.success(materialService.duplicateCheck(req));
    }

    @Operation(summary = "新建（编码为空时按类别前缀自动生成）")
    @PostMapping
    @PreAuthorize("@ss.has('eng:material:create')")
    public CommonResult<Long> create(@Valid @RequestBody MaterialSaveReqVO req) {
        return CommonResult.success(materialService.create(req));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:material:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody MaterialSaveReqVO req) {
        materialService.update(id, req);
        return CommonResult.success();
    }

    @Operation(summary = "启用（参数要求审批时草稿进入待审批），返回结果状态")
    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('eng:material:enable')")
    public CommonResult<String> enable(@PathVariable Long id) {
        return CommonResult.success(materialService.enable(id).name());
    }

    @Operation(summary = "停用")
    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('eng:material:disable')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        materialService.disable(id);
        return CommonResult.success();
    }

    @Operation(summary = "批量启用")
    @PostMapping("/batch-enable")
    @PreAuthorize("@ss.has('eng:material:enable')")
    public CommonResult<BatchResult> batchEnable(@RequestBody BatchReq req) {
        return CommonResult.success(materialService.batch(req.ids(), true));
    }

    @Operation(summary = "批量停用")
    @PostMapping("/batch-disable")
    @PreAuthorize("@ss.has('eng:material:disable')")
    public CommonResult<BatchResult> batchDisable(@RequestBody BatchReq req) {
        return CommonResult.success(materialService.batch(req.ids(), false));
    }

    @Operation(summary = "删除（仅草稿且未被使用）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:material:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return CommonResult.success();
    }

    // ==================== 导入导出 ====================

    @Operation(summary = "导出（与查询条件、列设置一致）")
    @GetMapping("/export")
    @PreAuthorize("@ss.has('eng:material:export')")
    public void export(@Valid MaterialPageReqVO req, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "物料", MaterialExcelService.exportColumns(), req.getColumns(),
                limit -> materialService.listForExport(req, limit));
    }

    @GetMapping("/import-template")
    @PreAuthorize("@ss.has('eng:material:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "物料", MaterialExcelService.IMPORT_COLUMNS);
    }

    /**
     * 校验；report=true 时返回错误报告文件。
     *
     * @param mode 编码已存在：SKIP 跳过 / UPDATE 更新非空列
     */
    @PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:material:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report,
                                                       @RequestParam(defaultValue = "SKIP") String mode,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, MaterialExcelService.IMPORT_COLUMNS);
        Map<Integer, String> actions = excelService.check(rows, mode);
        if (report) {
            ExcelSupport.writeBytes(response, "物料导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(MaterialExcelService.IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    /** @param enable 导入后直接启用（需要启用权限，且参数不要求审批） */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:material:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file,
                                               @RequestParam(defaultValue = "false") boolean partial,
                                               @RequestParam(defaultValue = "SKIP") String mode,
                                               @RequestParam(defaultValue = "false") boolean enable) {
        List<ImportRow> rows = ExcelSupport.read(file, MaterialExcelService.IMPORT_COLUMNS);
        if (enable && (!SecurityUtils.getLoginUser().hasPermission("eng:material:enable") || !excelService.enableAllowed())) {
            throw new BizException(GlobalErrorCodes.FORBIDDEN);
        }
        excelService.check(rows, mode);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(excelService.doImport(rows.stream().filter(r -> !r.hasError()).toList(), mode, enable));
    }
}
