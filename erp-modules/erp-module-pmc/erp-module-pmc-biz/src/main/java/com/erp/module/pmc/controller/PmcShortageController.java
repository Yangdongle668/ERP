package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.pmc.controller.vo.CommonVOs.BatchResult;
import com.erp.module.pmc.controller.vo.ShortageVOs.AnalyzeReq;
import com.erp.module.pmc.controller.vo.ShortageVOs.AnalyzeResult;
import com.erp.module.pmc.controller.vo.ShortageVOs.LineRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.MaterialRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.OrderRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.PushReq;
import com.erp.module.pmc.controller.vo.ShortageVOs.SnapshotRow;
import com.erp.module.pmc.service.PmcExportSupport;
import com.erp.module.pmc.service.shortage.ShortageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Tag(name = "PMC - 缺料分析")
@RestController
@RequestMapping("/api/pmc/shortages")
public class PmcShortageController {

    static final List<ExcelColumn<LineRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("componentCode", "物料编码", LineRow::componentCode),
            ExcelColumn.text("componentName", "名称", LineRow::componentName),
            ExcelColumn.number("unissuedQty", "未领", LineRow::unissuedQty),
            ExcelColumn.number("allocatedQty", "可分配", LineRow::allocatedQty),
            ExcelColumn.number("shortageQty", "缺料", LineRow::shortageQty),
            ExcelColumn.date("etaDate", "预计齐套", LineRow::etaDate),
            ExcelColumn.number("noSupplyQty", "无供应", LineRow::noSupplyQty),
            ExcelColumn.text("buyerName", "采购员", LineRow::buyerName));

    private final ShortageService service;
    private final PmcExportSupport exportSupport;

    public PmcShortageController(ShortageService service, PmcExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @PostMapping("/analyze")
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public CommonResult<AnalyzeResult> analyze(@RequestBody(required = false) AnalyzeReq req) {
        return CommonResult.success(service.analyze(req));
    }

    /** view：ORDER（默认）/ MATERIAL；为空取最近一次分析 */
    @GetMapping
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public CommonResult<List<?>> list(@RequestParam(required = false) String snapshotNo, @RequestParam(required = false) String view) {
        return CommonResult.success("MATERIAL".equals(view) ? service.materials(snapshotNo) : service.orders(snapshotNo));
    }

    @GetMapping("/orders")
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public CommonResult<List<OrderRow>> orders(@RequestParam(required = false) String snapshotNo) {
        return CommonResult.success(service.orders(snapshotNo));
    }

    @GetMapping("/materials")
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public CommonResult<List<MaterialRow>> materials(@RequestParam(required = false) String snapshotNo) {
        return CommonResult.success(service.materials(snapshotNo));
    }

    @GetMapping("/lines")
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public CommonResult<List<LineRow>> lines(@RequestParam(required = false) String snapshotNo, @RequestParam(required = false) Long prodOrderId,
                                             @RequestParam(defaultValue = "false") boolean shortOnly) {
        return CommonResult.success(service.lines(snapshotNo, prodOrderId, shortOnly));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public void export(@RequestParam(required = false) String snapshotNo, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "缺料明细", EXPORT_COLUMNS, null, limit -> service.lines(snapshotNo, null, true));
    }

    @GetMapping("/snapshots")
    @PreAuthorize("@ss.has('pmc:shortage:query')")
    public CommonResult<List<SnapshotRow>> snapshots() {
        return CommonResult.success(service.snapshots());
    }

    @PostMapping("/push")
    @PreAuthorize("@ss.has('pmc:shortage:push')")
    public CommonResult<BatchResult> push(@RequestBody PushReq req) {
        return CommonResult.success(service.push(req.snapshotNo(), req.componentIds()));
    }
}
