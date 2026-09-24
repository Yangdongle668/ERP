package com.erp.module.inventory.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.inventory.controller.vo.PeriodVOs.CheckResult;
import com.erp.module.inventory.controller.vo.PeriodVOs.InitReq;
import com.erp.module.inventory.controller.vo.PeriodVOs.OpeningInfo;
import com.erp.module.inventory.controller.vo.PeriodVOs.PeriodRow;
import com.erp.module.inventory.service.PeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 期初与月结（需求 08-09 第 6 节） */
@Tag(name = "仓库 - 期初与月结")
@RestController("invPeriodController")
@RequestMapping("/api/inventory")
public class PeriodController {

    private final PeriodService service;

    public PeriodController(PeriodService service) {
        this.service = service;
    }

    @GetMapping("/periods")
    @PreAuthorize("@ss.has('inv:period:query')")
    public CommonResult<List<PeriodRow>> list() {
        return CommonResult.success(service.list());
    }

    @Operation(summary = "设置启用期间（只能设置一次）")
    @PostMapping("/periods/init")
    @PreAuthorize("@ss.has('inv:opening:import')")
    public CommonResult<Void> init(@Valid @RequestBody InitReq req) {
        service.init(req.period());
        return CommonResult.success();
    }

    @Operation(summary = "月结检查：BLOCK 阻止 / WARN 警告")
    @PostMapping("/periods/{period}/check")
    @PreAuthorize("@ss.has('inv:period:close')")
    public CommonResult<CheckResult> check(@PathVariable String period) {
        return CommonResult.success(service.check(period));
    }

    @PostMapping("/periods/{period}/close")
    @PreAuthorize("@ss.has('inv:period:close')")
    public CommonResult<Void> close(@PathVariable String period) {
        service.close(period);
        return CommonResult.success();
    }

    @PostMapping("/periods/{period}/reopen")
    @PreAuthorize("@ss.has('inv:period:reopen')")
    public CommonResult<Void> reopen(@PathVariable String period) {
        service.reopen(period);
        return CommonResult.success();
    }

    @GetMapping("/opening")
    @PreAuthorize("@ss.has('inv:period:query')")
    public CommonResult<OpeningInfo> opening() {
        return CommonResult.success(service.openingInfo());
    }

    @GetMapping("/opening/import-template")
    @PreAuthorize("@ss.has('inv:opening:import')")
    public void template(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "期初库存", PeriodService.OPENING_COLUMNS);
    }

    @PostMapping(value = "/opening/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('inv:opening:import')")
    public CommonResult<ImportCheckResult> check(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                 HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, PeriodService.OPENING_COLUMNS);
        Map<Integer, String> actions = service.checkOpening(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "期初导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(PeriodService.OPENING_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @Operation(summary = "期初导入：有错误行时整体不导入")
    @PostMapping(value = "/opening/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('inv:opening:import')")
    public CommonResult<ImportResult> importOpening(@RequestPart("file") MultipartFile file) {
        return CommonResult.success(service.importOpening(ExcelSupport.read(file, PeriodService.OPENING_COLUMNS)));
    }

    @PostMapping("/opening/clear")
    @PreAuthorize("@ss.has('inv:opening:import')")
    public CommonResult<Void> clear() {
        service.clearOpening();
        return CommonResult.success();
    }

    @PostMapping("/opening/complete")
    @PreAuthorize("@ss.has('inv:opening:import')")
    public CommonResult<Void> complete() {
        service.completeOpening();
        return CommonResult.success();
    }
}
