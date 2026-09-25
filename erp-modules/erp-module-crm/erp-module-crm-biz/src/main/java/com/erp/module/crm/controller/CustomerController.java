package com.erp.module.crm.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactQuery;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactRow;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerBrief;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerDetail;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerQuery;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerRow;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerSave;
import com.erp.module.crm.controller.vo.CustomerVOs.DuplicateCheckReq;
import com.erp.module.crm.controller.vo.CustomerVOs.DuplicateRow;
import com.erp.module.crm.controller.vo.CustomerVOs.ReasonReq;
import com.erp.module.crm.controller.vo.CustomerVOs.SaveResult;
import com.erp.module.crm.controller.vo.CustomerVOs.StatusResult;
import com.erp.module.crm.controller.vo.CustomerVOs.TransferLogRow;
import com.erp.module.crm.controller.vo.CustomerVOs.TransferReq;
import com.erp.module.crm.service.CrmExportSupport;
import com.erp.module.crm.service.CustomerImportService;
import com.erp.module.crm.service.CustomerService;
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

/** 客户与联系人（需求 03-01 第 6 节） */
@Tag(name = "CRM - 客户")
@RestController
@RequestMapping("/api/crm")
public class CustomerController {

    static final Map<String, String> STATUS = Map.of("PROSPECT", "潜在", "PENDING", "审批中", "ACTIVE", "正式", "DISABLED", "停用", "BLACKLIST", "黑名单");

    static final List<ExcelColumn<CustomerRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("code", "编码", CustomerRow::code),
            ExcelColumn.text("shortName", "简称", CustomerRow::shortName),
            ExcelColumn.text("name", "名称", CustomerRow::name),
            ExcelColumn.text("nameEn", "英文名称", CustomerRow::nameEn),
            ExcelColumn.text("country", "国家", CustomerRow::country),
            ExcelColumn.text("customerType", "类型", CustomerRow::customerType),
            ExcelColumn.text("level", "等级", CustomerRow::level),
            ExcelColumn.text("ownerName", "负责人", CustomerRow::ownerName),
            ExcelColumn.text("primaryContact", "主联系人", CustomerRow::primaryContact),
            ExcelColumn.text("primaryContactEmail", "联系人邮箱", CustomerRow::primaryContactEmail),
            ExcelColumn.text("currency", "币别", CustomerRow::currency),
            ExcelColumn.number("creditLimit", "信用额度", CustomerRow::creditLimit),
            ExcelColumn.date("lastOrderDate", "最近下单", CustomerRow::lastOrderDate),
            ExcelColumn.text("customerStatus", "状态", r -> STATUS.get(r.customerStatus())));

    private final CustomerService service;
    private final CustomerImportService importService;
    private final CrmExportSupport exportSupport;

    public CustomerController(CustomerService service, CustomerImportService importService, CrmExportSupport exportSupport) {
        this.service = service;
        this.importService = importService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/customers")
    @PreAuthorize("@ss.has('crm:customer:query')")
    public CommonResult<PageResult<CustomerRow>> page(@Valid CustomerQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "客户选择器（登录即可，按数据权限；ids 用于回显）")
    @GetMapping("/customers/search")
    public CommonResult<List<CustomerBrief>> search(@RequestParam(required = false) String keyword, @RequestParam(required = false) String statuses,
                                                    @RequestParam(required = false) String ids, @RequestParam(defaultValue = "20") int limit) {
        return CommonResult.success(service.search(keyword, statuses, ids, limit));
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("@ss.has('crm:customer:query')")
    public CommonResult<CustomerDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/customers/duplicate-check")
    @PreAuthorize("@ss.has('crm:customer:create') or @ss.has('crm:customer:update')")
    public CommonResult<List<DuplicateRow>> duplicateCheck(@RequestBody DuplicateCheckReq req) {
        return CommonResult.success(service.duplicateCheck(req));
    }

    @PostMapping("/customers")
    @PreAuthorize("@ss.has('crm:customer:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody CustomerSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("@ss.has('crm:customer:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody CustomerSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/customers/{id}")
    @PreAuthorize("@ss.has('crm:customer:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/customers/{id}/activate")
    @PreAuthorize("@ss.has('crm:customer:activate')")
    public CommonResult<StatusResult> activate(@PathVariable Long id) {
        return CommonResult.success(new StatusResult(service.activate(id)));
    }

    @PostMapping("/customers/{id}/disable")
    @PreAuthorize("@ss.has('crm:customer:disable')")
    public CommonResult<Void> disable(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.disable(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/customers/{id}/enable")
    @PreAuthorize("@ss.has('crm:customer:disable')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        service.enable(id);
        return CommonResult.success();
    }

    @PostMapping("/customers/{id}/blacklist")
    @PreAuthorize("@ss.has('crm:customer:blacklist')")
    public CommonResult<Void> blacklist(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.blacklist(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/customers/{id}/unblacklist")
    @PreAuthorize("@ss.has('crm:customer:blacklist')")
    public CommonResult<Void> unblacklist(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.unblacklist(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/customers/transfer")
    @PreAuthorize("@ss.has('crm:customer:transfer')")
    public CommonResult<Integer> transfer(@Valid @RequestBody TransferReq req) {
        return CommonResult.success(service.transfer(req));
    }

    @GetMapping("/customers/{id}/transfer-logs")
    @PreAuthorize("@ss.has('crm:customer:query')")
    public CommonResult<List<TransferLogRow>> transferLogs(@PathVariable Long id) {
        return CommonResult.success(service.transferLogs(id));
    }

    @Operation(summary = "客户地址（登录即可，销售、出货选择地址）")
    @GetMapping("/customers/{id}/addresses")
    public CommonResult<List<AddressDTO>> addresses(@PathVariable Long id, @RequestParam(required = false) String type) {
        return CommonResult.success(service.getAddresses(id, type));
    }

    @GetMapping("/contacts")
    @PreAuthorize("@ss.has('crm:customer:query')")
    public CommonResult<PageResult<ContactRow>> contacts(@Valid ContactQuery q) {
        return CommonResult.success(service.contacts(q));
    }

    // ==================== 导入导出 ====================

    @GetMapping("/customers/export")
    @PreAuthorize("@ss.has('crm:customer:export')")
    public void export(@Valid CustomerQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "客户", EXPORT_COLUMNS, q.getColumns(), limit -> service.listForExport(q, limit));
    }

    @GetMapping("/customers/import-template")
    @PreAuthorize("@ss.has('crm:customer:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "客户", CustomerImportService.COLUMNS);
    }

    @PostMapping(value = "/customers/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('crm:customer:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, CustomerImportService.COLUMNS);
        Map<Integer, String> actions = importService.check(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "客户导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(CustomerImportService.COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    /** @param paymentTermId 导入客户的默认付款条件（导入选项，可空） */
    @PostMapping(value = "/customers/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('crm:customer:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean partial,
                                               @RequestParam(required = false) Long paymentTermId) {
        List<ImportRow> rows = ExcelSupport.read(file, CustomerImportService.COLUMNS);
        importService.check(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(importService.doImport(rows.stream().filter(r -> !r.hasError()).toList(), paymentTermId));
    }
}
