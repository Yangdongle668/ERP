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
import com.erp.module.production.controller.vo.CommonVOs.BatchResult;
import com.erp.module.production.controller.vo.CommonVOs.DocResult;
import com.erp.module.production.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.production.controller.vo.CommonVOs.SaveResult;
import com.erp.module.production.controller.vo.FinishVOs.FinishReq;
import com.erp.module.production.controller.vo.ProdOrderVOs.AdjustReq;
import com.erp.module.production.controller.vo.ProdOrderVOs.BatchReleaseReq;
import com.erp.module.production.controller.vo.ProdOrderVOs.CloseReq;
import com.erp.module.production.controller.vo.ProdOrderVOs.KitCheck;
import com.erp.module.production.controller.vo.ProdOrderVOs.Preview;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderDetail;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderQuery;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderRow;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderSave;
import com.erp.module.production.controller.vo.ProdOrderVOs.ReleaseReq;
import com.erp.module.production.service.MfgExportSupport;
import com.erp.module.production.service.finish.FinishService;
import com.erp.module.production.service.order.ProdOrderService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 生产订单（需求 09-01）与完工入库申请（09-05） */
@Tag(name = "生产 - 生产订单")
@RestController
@RequestMapping("/api/production")
public class ProductionOrderController {

    static final List<ExcelColumn<ProdOrderRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", ProdOrderRow::docNo),
            ExcelColumn.text("orderType", "类型", ProdOrderRow::orderType),
            ExcelColumn.text("materialCode", "产品编码", ProdOrderRow::materialCode),
            ExcelColumn.text("materialName", "产品名称", ProdOrderRow::materialName),
            ExcelColumn.text("materialSpec", "规格", ProdOrderRow::materialSpec),
            ExcelColumn.number("qty", "计划数量", ProdOrderRow::qty),
            ExcelColumn.number("completedQty", "完工数量", ProdOrderRow::completedQty),
            ExcelColumn.number("stockedQty", "入库数量", ProdOrderRow::stockedQty),
            ExcelColumn.date("planStart", "计划开工", ProdOrderRow::planStart),
            ExcelColumn.date("planEnd", "计划完工", ProdOrderRow::planEnd),
            ExcelColumn.text("deptName", "车间", ProdOrderRow::deptName),
            ExcelColumn.number("priority", "优先级", ProdOrderRow::priority),
            ExcelColumn.text("salesOrderNo", "销售订单", ProdOrderRow::salesOrderNo),
            ExcelColumn.text("prodStatus", "状态", ProdOrderRow::prodStatus),
            ExcelColumn.text("ownerName", "计划员", ProdOrderRow::ownerName));

    private final ProdOrderService service;
    private final FinishService finishService;
    private final MfgExportSupport exportSupport;

    public ProductionOrderController(ProdOrderService service, FinishService finishService, MfgExportSupport exportSupport) {
        this.service = service;
        this.finishService = finishService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/prod-orders")
    @PreAuthorize("@ss.has('mfg:prod-order:query')")
    public CommonResult<PageResult<ProdOrderRow>> page(@Valid ProdOrderQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/prod-orders/export")
    @PreAuthorize("@ss.has('mfg:prod-order:query')")
    public void export(@Valid ProdOrderQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "生产订单", EXPORT_COLUMNS, q.getColumns(), limit -> service.listForExport(q, limit));
    }

    @GetMapping("/prod-orders/preview-materials")
    @PreAuthorize("@ss.hasAny('mfg:prod-order:query', 'mfg:prod-order:create', 'mfg:prod-order:update')")
    public CommonResult<Preview> preview(@RequestParam Long materialId, @RequestParam(required = false) Long bomId,
                                         @RequestParam(required = false) Long routingId, @RequestParam(required = false) BigDecimal qty,
                                         @RequestParam(required = false) String orderType) {
        return CommonResult.success(service.preview(materialId, bomId, routingId, qty, orderType));
    }

    @GetMapping("/prod-orders/{id}")
    @PreAuthorize("@ss.has('mfg:prod-order:query')")
    public CommonResult<ProdOrderDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/prod-orders")
    @PreAuthorize("@ss.has('mfg:prod-order:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody ProdOrderSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/prod-orders/{id}")
    @PreAuthorize("@ss.has('mfg:prod-order:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody ProdOrderSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/prod-orders/{id}")
    @PreAuthorize("@ss.has('mfg:prod-order:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/submit")
    @PreAuthorize("@ss.has('mfg:prod-order:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/prod-orders/{id}/withdraw")
    @PreAuthorize("@ss.has('mfg:prod-order:submit')")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/release")
    @PreAuthorize("@ss.has('mfg:prod-order:release')")
    public CommonResult<DocResult> release(@PathVariable Long id, @RequestBody(required = false) ReleaseReq req) {
        return CommonResult.success(service.release(id, req != null && Boolean.TRUE.equals(req.confirmShortage())));
    }

    @PostMapping("/prod-orders/batch-release")
    @PreAuthorize("@ss.has('mfg:prod-order:release')")
    public CommonResult<BatchResult> batchRelease(@RequestBody BatchReleaseReq req) {
        return CommonResult.success(service.batchRelease(req.ids(), Boolean.TRUE.equals(req.confirmShortage())));
    }

    @PostMapping("/prod-orders/{id}/unrelease")
    @PreAuthorize("@ss.has('mfg:prod-order:unrelease')")
    public CommonResult<Void> unrelease(@PathVariable Long id) {
        service.unrelease(id);
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/suspend")
    @PreAuthorize("@ss.has('mfg:prod-order:suspend')")
    public CommonResult<Void> suspend(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.suspend(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/resume")
    @PreAuthorize("@ss.has('mfg:prod-order:suspend')")
    public CommonResult<Void> resume(@PathVariable Long id) {
        service.resume(id);
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/close")
    @PreAuthorize("@ss.has('mfg:prod-order:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody CloseReq req) {
        service.close(id, req.reason(), Boolean.TRUE.equals(req.confirmScrap()));
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/void")
    @PreAuthorize("@ss.has('mfg:prod-order:void')")
    public CommonResult<Void> voidOrder(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidOrder(id, req.reason());
        return CommonResult.success();
    }

    @PutMapping("/prod-orders/{id}/materials")
    @PreAuthorize("@ss.has('mfg:prod-order:update')")
    public CommonResult<Void> adjust(@PathVariable Long id, @Valid @RequestBody AdjustReq req) {
        service.adjustMaterials(id, req);
        return CommonResult.success();
    }

    @PostMapping("/prod-orders/{id}/kit-check")
    @PreAuthorize("@ss.has('mfg:prod-order:query')")
    public CommonResult<KitCheck> kitCheck(@PathVariable Long id) {
        return CommonResult.success(service.kitCheck(id));
    }

    @GetMapping("/prod-orders/{id}/print-data")
    @PreAuthorize("@ss.has('mfg:prod-order:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    @PostMapping("/prod-orders/{id}/finish")
    @PreAuthorize("@ss.has('mfg:finish:create')")
    public CommonResult<Long> finish(@PathVariable Long id, @Valid @RequestBody FinishReq req) {
        return CommonResult.success(finishService.finish(id, req));
    }
}
