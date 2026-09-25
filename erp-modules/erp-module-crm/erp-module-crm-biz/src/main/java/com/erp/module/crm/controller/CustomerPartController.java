package com.erp.module.crm.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.part.CustomerPartDTO;
import com.erp.module.crm.controller.vo.PartVOs.PartQuery;
import com.erp.module.crm.controller.vo.PartVOs.PartRow;
import com.erp.module.crm.controller.vo.PartVOs.PartSave;
import com.erp.module.crm.service.CustomerPartService;
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

/** 客户料号对照（需求 03-02 第 5 节） */
@Tag(name = "CRM - 客户料号")
@RestController
@RequestMapping("/api/crm/customer-parts")
public class CustomerPartController {

    private final CustomerPartService service;

    public CustomerPartController(CustomerPartService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('crm:customer-part:query')")
    public CommonResult<PageResult<PartRow>> page(@Valid PartQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "按客户料号查本厂物料（登录即可，销售订单行输入客户料号时使用）")
    @GetMapping("/lookup")
    public CommonResult<CustomerPartDTO> lookup(@RequestParam Long customerId, @RequestParam String partNo) {
        return CommonResult.success(service.toMaterial(customerId, partNo).orElse(null));
    }

    @PostMapping
    @PreAuthorize("@ss.has('crm:customer-part:create')")
    public CommonResult<Long> create(@Valid @RequestBody PartSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('crm:customer-part:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody PartSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('crm:customer-part:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        service.setStatus(id, true);
        return CommonResult.success();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('crm:customer-part:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        service.setStatus(id, false);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('crm:customer-part:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @GetMapping("/import-template")
    @PreAuthorize("@ss.has('crm:customer-part:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "客户料号", CustomerPartService.IMPORT_COLUMNS);
    }

    @PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('crm:customer-part:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, CustomerPartService.IMPORT_COLUMNS);
        Map<Integer, String> actions = service.check(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "客户料号导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(CustomerPartService.IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('crm:customer-part:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean partial) {
        List<ImportRow> rows = ExcelSupport.read(file, CustomerPartService.IMPORT_COLUMNS);
        service.check(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(service.doImport(rows.stream().filter(r -> !r.hasError()).toList()));
    }
}
