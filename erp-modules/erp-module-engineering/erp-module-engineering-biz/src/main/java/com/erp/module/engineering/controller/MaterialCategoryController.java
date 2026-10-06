package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategoryNode;
import com.erp.module.engineering.controller.vo.CategoryVOs.CodeScheme;
import com.erp.module.engineering.controller.vo.CategoryVOs.CodeSchemeSave;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategorySave;
import com.erp.module.engineering.controller.vo.CategoryVOs.SimpleNode;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.service.CategoryImportService;
import com.erp.module.engineering.service.CodeSegmentService;
import com.erp.module.engineering.service.ImportBatchService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import com.erp.module.engineering.service.MaterialCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

/** 物料类别（需求 05-01 第 6 节） */
@Tag(name = "研发工程 - 物料类别")
@RestController
@RequestMapping("/api/engineering/categories")
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;
    private final CodeSegmentService segmentService;
    private final CategoryImportService importService;
    private final ImportBatchService batchService;

    public MaterialCategoryController(MaterialCategoryService categoryService, CodeSegmentService segmentService,
                                      CategoryImportService importService, ImportBatchService batchService) {
        this.categoryService = categoryService;
        this.segmentService = segmentService;
        this.importService = importService;
        this.batchService = batchService;
    }

    @Operation(summary = "树形表格（含停用）")
    @GetMapping("/tree")
    @PreAuthorize("@ss.has('eng:category:query')")
    public CommonResult<List<CategoryNode>> tree(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return CommonResult.success(categoryService.tree(keyword, status));
    }

    @Operation(summary = "启用类别精简树（登录即可，供选择器）")
    @GetMapping("/simple-tree")
    public CommonResult<List<SimpleNode>> simpleTree() {
        return CommonResult.success(categoryService.simpleTree());
    }

    @Operation(summary = "新增下级时的默认排序")
    @GetMapping("/next-sort")
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<Integer> nextSort(@RequestParam(required = false) Long parentId) {
        return CommonResult.success(categoryService.nextSort(parentId));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<Long> create(@Valid @RequestBody CategorySave req) {
        return CommonResult.success(categoryService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySave req) {
        categoryService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:category:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        categoryService.enable(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        categoryService.disable(id);
        return CommonResult.success();
    }

    @Operation(summary = "编码方案（编码段与特征值）")
    @GetMapping("/{id}/code-scheme")
    @PreAuthorize("@ss.hasAny('eng:category:query', 'eng:material:create', 'eng:material:update')")
    public CommonResult<CodeScheme> codeScheme(@PathVariable Long id) {
        return CommonResult.success(segmentService.scheme(id));
    }

    @Operation(summary = "保存编码方案")
    @PutMapping("/{id}/code-scheme")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> saveCodeScheme(@PathVariable Long id, @Valid @RequestBody CodeSchemeSave req) {
        segmentService.save(id, req);
        return CommonResult.success();
    }

    // ==================== 导入（05-01 第 9 节） ====================

    @GetMapping("/import-template")
    @PreAuthorize("@ss.has('eng:category:create')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "物料类别", CategoryImportService.IMPORT_COLUMNS);
    }

    @PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, CategoryImportService.IMPORT_COLUMNS);
        Map<Integer, String> actions = importService.check(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "物料类别导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(CategoryImportService.IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean partial) {
        List<ImportRow> rows = ExcelSupport.read(file, CategoryImportService.IMPORT_COLUMNS);
        importService.check(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(importService.doImport(rows.stream().filter(r -> !r.hasError()).toList(), file.getOriginalFilename()));
    }

    @Operation(summary = "导入记录")
    @GetMapping("/import/batches")
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<List<ImportBatchService.BatchRow>> importBatches() {
        return CommonResult.success(batchService.list(ImportBatchService.CATEGORY));
    }

    @Operation(summary = "回滚导入批次：删除本批新增的类别")
    @PostMapping("/import/batches/{batchId}/rollback")
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<Void> rollbackImport(@PathVariable Long batchId) {
        batchService.rollback(ImportBatchService.CATEGORY, batchId);
        return CommonResult.success();
    }
}
