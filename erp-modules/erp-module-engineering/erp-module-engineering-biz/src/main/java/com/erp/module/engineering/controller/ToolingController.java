package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.controller.vo.ToolingVOs.RecordReq;
import com.erp.module.engineering.controller.vo.ToolingVOs.RecordRow;
import com.erp.module.engineering.controller.vo.ToolingVOs.ToolingQuery;
import com.erp.module.engineering.controller.vo.ToolingVOs.ToolingRow;
import com.erp.module.engineering.controller.vo.ToolingVOs.ToolingSave;
import com.erp.module.engineering.service.ExportSupport;
import com.erp.module.engineering.service.ToolingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 工装台账（需求 05-08 第 5 节） */
@Tag(name = "研发工程 - 工装")
@RestController
@RequestMapping("/api/engineering/toolings")
public class ToolingController {

    static final List<ExcelColumn<ToolingRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("code", "工装编号", ToolingRow::code),
            ExcelColumn.text("name", "名称", ToolingRow::name),
            ExcelColumn.text("toolingType", "类型", ToolingRow::toolingType),
            ExcelColumn.text("spec", "规格", ToolingRow::spec),
            ExcelColumn.text("ownership", "归属", r -> "CUSTOMER".equals(r.ownership()) ? "客户资产" : "自有"),
            ExcelColumn.number("cavity", "模穴数", ToolingRow::cavity),
            ExcelColumn.number("usedCount", "使用次数", ToolingRow::usedCount),
            ExcelColumn.number("designLife", "设计寿命", ToolingRow::designLife),
            ExcelColumn.number("toMaintain", "距下次保养", ToolingRow::toMaintain),
            ExcelColumn.text("location", "存放位置", ToolingRow::location),
            ExcelColumn.text("toolingStatus", "状态", r -> ToolingService.State.valueOf(r.toolingStatus()).label()),
            ExcelColumn.text("holderName", "持有人", ToolingRow::holderName));

    static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("code", "工装编号", false, "为空时自动生成"),
            ExcelColumn.input("name", "名称", true, null),
            ExcelColumn.input("toolingType", "类型编码", true, "MOLD/JIG/FIXTURE/GAUGE/STENCIL"),
            ExcelColumn.input("spec", "规格", false, null),
            ExcelColumn.<Object>input("cavity", "模穴数", false, "默认 1").ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.<Object>input("designLife", "设计寿命", false, "为空不管控").ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.<Object>input("usedCount", "已使用次数", false, "旧模具建档").ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.<Object>input("maintainCycle", "保养周期", false, null).ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.input("location", "存放位置", false, null),
            ExcelColumn.input("supplierName", "制作厂商", false, null),
            ExcelColumn.input("remark", "备注", false, null));

    private final ToolingService service;
    private final ExportSupport exportSupport;

    public ToolingController(ToolingService service, ExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('eng:tooling:query')")
    public CommonResult<PageResult<ToolingRow>> page(@Valid ToolingQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "报工选择（登录即可）：只返回可用工装")
    @GetMapping("/simple")
    public CommonResult<List<ToolingRow>> simple(@RequestParam(required = false) Long materialId) {
        return CommonResult.success(service.simple(materialId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:tooling:query')")
    public CommonResult<ToolingRow> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @GetMapping("/{id}/records")
    @PreAuthorize("@ss.has('eng:tooling:query')")
    public CommonResult<List<RecordRow>> records(@PathVariable Long id) {
        return CommonResult.success(service.records(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:tooling:create')")
    public CommonResult<Long> create(@Valid @RequestBody ToolingSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:tooling:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ToolingSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:tooling:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/lend")
    @PreAuthorize("@ss.has('eng:tooling:record')")
    public CommonResult<Void> lend(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.lend(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("@ss.has('eng:tooling:record')")
    public CommonResult<Void> giveBack(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.giveBack(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/maintain")
    @PreAuthorize("@ss.has('eng:tooling:record')")
    public CommonResult<Void> maintain(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.maintain(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/repair-start")
    @PreAuthorize("@ss.has('eng:tooling:record')")
    public CommonResult<Void> repairStart(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.repairStart(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/repair-end")
    @PreAuthorize("@ss.has('eng:tooling:record')")
    public CommonResult<Void> repairEnd(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.repairEnd(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/scrap")
    @PreAuthorize("@ss.has('eng:tooling:record')")
    public CommonResult<Void> scrap(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.scrap(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize("@ss.has('eng:tooling:update')")
    public CommonResult<Void> adjust(@PathVariable Long id, @Valid @RequestBody RecordReq req) {
        service.adjust(id, req);
        return CommonResult.success();
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('eng:tooling:export')")
    public void export(@Valid ToolingQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "工装台账", EXPORT_COLUMNS, columns, limit -> service.listForExport(q, limit));
    }

    @GetMapping("/import-template")
    @PreAuthorize("@ss.has('eng:tooling:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "工装", IMPORT_COLUMNS);
    }

    @PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:tooling:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        Map<Integer, String> actions = new HashMap<>();
        for (ImportRow r : rows) {
            if (!StringUtils.hasText(r.get("name"))) r.error("名称不能为空");
            if (!StringUtils.hasText(r.get("toolingType"))) r.error("类型编码不能为空");
            if (!r.hasError()) actions.put(r.rowNo(), "新增");
        }
        if (report) {
            ExcelSupport.writeBytes(response, "工装导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('eng:tooling:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file) {
        return CommonResult.success(service.doImport(ExcelSupport.read(file, IMPORT_COLUMNS)));
    }
}
