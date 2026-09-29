package com.erp.module.production.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
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
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.module.production.controller.vo.FinishVOs.FinishQuery;
import com.erp.module.production.controller.vo.FinishVOs.FinishRow;
import com.erp.module.production.controller.vo.TraceVOs.TraceResult;
import com.erp.module.production.controller.vo.TraceVOs.TraceRow;
import com.erp.module.production.service.finish.FinishService;
import com.erp.module.production.service.trace.TraceService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** 完工入库记录（需求 09-05）与生产追溯（09-07） */
@Tag(name = "生产 - 完工入库与追溯")
@RestController
@RequestMapping("/api/production")
public class ProductionFinishTraceController {

    static final List<ExcelColumn<TraceRow>> TRACE_COLUMNS = List.of(
            ExcelColumn.number("level", "层级", TraceRow::level),
            ExcelColumn.text("prodOrderNo", "生产订单", TraceRow::prodOrderNo),
            ExcelColumn.text("productCode", "产品编码", TraceRow::productCode),
            ExcelColumn.text("productName", "产品名称", TraceRow::productName),
            ExcelColumn.text("productBatchNo", "产品批次", TraceRow::productBatchNo),
            ExcelColumn.text("componentCode", "投入物料编码", TraceRow::componentCode),
            ExcelColumn.text("componentName", "投入物料名称", TraceRow::componentName),
            ExcelColumn.text("componentBatchNo", "投入批次", TraceRow::componentBatchNo),
            ExcelColumn.number("qty", "数量", TraceRow::qty));

    private final FinishService finishService;
    private final TraceService traceService;

    public ProductionFinishTraceController(FinishService finishService, TraceService traceService) {
        this.finishService = finishService;
        this.traceService = traceService;
    }

    @GetMapping("/finishes")
    @PreAuthorize("@ss.hasAny('mfg:finish:query', 'mfg:prod-order:query')")
    public CommonResult<PageResult<FinishRow>> finishes(@Valid FinishQuery q) {
        return CommonResult.success(finishService.page(q));
    }

    @PostMapping("/finishes/{id}/cancel")
    @PreAuthorize("@ss.has('mfg:finish:cancel')")
    public CommonResult<Void> cancel(@PathVariable Long id) {
        finishService.cancel(id);
        return CommonResult.success();
    }

    @GetMapping("/trace/backward")
    @PreAuthorize("@ss.has('mfg:trace:query')")
    public CommonResult<TraceResult> backward(@RequestParam Long materialId, @RequestParam String batchNo) {
        return CommonResult.success(traceService.backwardTree(materialId, batchNo.trim()));
    }

    @GetMapping("/trace/forward")
    @PreAuthorize("@ss.has('mfg:trace:query')")
    public CommonResult<TraceResult> forward(@RequestParam Long materialId, @RequestParam String batchNo) {
        return CommonResult.success(traceService.forwardTree(materialId, batchNo.trim()));
    }

    @GetMapping("/trace/export")
    @PreAuthorize("@ss.has('mfg:trace:query')")
    public void export(@RequestParam String direction, @RequestParam Long materialId, @RequestParam String batchNo, HttpServletResponse response)
            throws IOException {
        ExcelSupport.export(response, "FORWARD".equals(direction) ? "正向追溯（召回清单）" : "反向追溯", TRACE_COLUMNS,
                traceService.exportRows(direction, materialId, batchNo.trim()), null);
    }
}
