package com.erp.module.shipping.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.shipping.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.shipping.controller.vo.NoticeVOs.CustomerDefaults;
import com.erp.module.shipping.controller.vo.NoticeVOs.FromOrdersReq;
import com.erp.module.shipping.controller.vo.NoticeVOs.FromPlanReq;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeDetail;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeQuery;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeRow;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeSave;
import com.erp.module.shipping.controller.vo.NoticeVOs.OrderLineOption;
import com.erp.module.shipping.controller.vo.NoticeVOs.PlanLineOption;
import com.erp.module.shipping.controller.vo.NoticeVOs.SaveResult;
import com.erp.module.shipping.controller.vo.PackingVOs.BatchPackReq;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonSave;
import com.erp.module.shipping.controller.vo.PackingVOs.PackCompleteReq;
import com.erp.module.shipping.controller.vo.PackingVOs.PackingView;
import com.erp.module.shipping.controller.vo.ShipmentVOs.GenerateReq;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.ShpExportSupport;
import com.erp.module.shipping.service.notice.NoticeService;
import com.erp.module.shipping.service.picking.PackingService;
import com.erp.module.shipping.service.shipment.ShipmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "出货 - 出货通知 / 装箱")
@RestController
@RequestMapping("/api/shipping/notices")
public class ShpNoticeController {

    static final List<ExcelColumn<NoticeRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", NoticeRow::docNo),
            ExcelColumn.text("customerName", "客户", NoticeRow::customerName),
            ExcelColumn.date("shipDate", "出货日期", NoticeRow::shipDate),
            ExcelColumn.text("transportMode", "运输方式", NoticeRow::transportMode),
            ExcelColumn.text("portOfDestination", "目的港", NoticeRow::portOfDestination),
            ExcelColumn.number("lineCount", "行数", NoticeRow::lineCount),
            ExcelColumn.number("totalQty", "数量", NoticeRow::totalQty),
            ExcelColumn.text("currency", "币别", NoticeRow::currency),
            ExcelColumn.number("totalAmount", "金额", NoticeRow::totalAmount),
            ExcelColumn.number("pickedQty", "已拣货", NoticeRow::pickedQty),
            ExcelColumn.number("packedQty", "已装箱", NoticeRow::packedQty),
            ExcelColumn.number("shippedQty", "已出货", NoticeRow::shippedQty),
            ExcelColumn.text("oqcResult", "OQC", NoticeRow::oqcResult),
            ExcelColumn.text("noticeStatus", "状态", r -> NoticeStatus.valueOf(r.noticeStatus()).label()),
            ExcelColumn.text("ownerName", "船务", NoticeRow::ownerName),
            ExcelColumn.dateTime("createdAt", "创建时间", NoticeRow::createdAt));

    private final NoticeService service;
    private final PackingService packingService;
    private final ShipmentService shipmentService;
    private final ShpExportSupport exportSupport;

    public ShpNoticeController(NoticeService service, PackingService packingService, ShipmentService shipmentService, ShpExportSupport exportSupport) {
        this.service = service;
        this.packingService = packingService;
        this.shipmentService = shipmentService;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('shp:notice:query')")
    public CommonResult<PageResult<NoticeRow>> page(@Valid NoticeQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('shp:notice:query')")
    public void export(@Valid NoticeQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "出货通知", EXPORT_COLUMNS, columns, limit -> service.list(q).stream().limit(limit).toList());
    }

    @GetMapping("/order-lines")
    @PreAuthorize("@ss.hasAny('shp:notice:create','shp:notice:update')")
    public CommonResult<List<OrderLineOption>> orderLines(@RequestParam(required = false) Long customerId, @RequestParam(required = false) String orderNo,
                                                          @RequestParam(required = false) Long materialId, @RequestParam(required = false) Long warehouseId) {
        return CommonResult.success(service.orderLineOptions(customerId, orderNo, materialId, warehouseId));
    }

    @GetMapping("/customer-defaults")
    @PreAuthorize("@ss.hasAny('shp:notice:create','shp:notice:update')")
    public CommonResult<CustomerDefaults> customerDefaults(@RequestParam Long customerId, @RequestParam(required = false) Long orderId) {
        return CommonResult.success(service.customerDefaults(customerId, orderId));
    }

    @GetMapping("/plan-lines")
    @PreAuthorize("@ss.has('shp:notice:create')")
    public CommonResult<List<PlanLineOption>> planLines(@RequestParam String week) {
        return CommonResult.success(service.planLineOptions(week));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('shp:notice:query')")
    public CommonResult<NoticeDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('shp:notice:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody NoticeSave req) {
        return CommonResult.success(service.create(req));
    }

    @PostMapping("/from-orders")
    @PreAuthorize("@ss.has('shp:notice:create')")
    public CommonResult<SaveResult> fromOrders(@Valid @RequestBody FromOrdersReq req) {
        return CommonResult.success(service.fromOrders(req));
    }

    @PostMapping("/from-plan")
    @PreAuthorize("@ss.has('shp:notice:create')")
    public CommonResult<List<Long>> fromPlan(@Valid @RequestBody FromPlanReq req) {
        return CommonResult.success(service.fromPlan(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('shp:notice:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody NoticeSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('shp:notice:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('shp:notice:submit')")
    public CommonResult<SaveResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/{id}/unapprove")
    @PreAuthorize("@ss.has('shp:notice:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id) {
        service.unapprove(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('shp:notice:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.close(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('shp:notice:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    // ==================== 装箱 ====================

    @GetMapping("/{id}/cartons")
    @PreAuthorize("@ss.hasAny('shp:packing:query','shp:notice:query')")
    public CommonResult<PackingView> cartons(@PathVariable Long id) {
        return CommonResult.success(packingService.view(id));
    }

    @PostMapping("/{id}/cartons/batch")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<List<Long>> batchPack(@PathVariable Long id, @Valid @RequestBody BatchPackReq req) {
        return CommonResult.success(packingService.batchPack(id, req));
    }

    @PostMapping("/{id}/cartons")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<Long> addCarton(@PathVariable Long id, @Valid @RequestBody CartonSave req) {
        return CommonResult.success(packingService.addCarton(id, req));
    }

    @DeleteMapping("/{id}/cartons")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<Void> clearCartons(@PathVariable Long id) {
        packingService.clearCartons(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/pack-complete")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<Void> packComplete(@PathVariable Long id, @RequestBody(required = false) PackCompleteReq req) {
        packingService.packComplete(id, req != null && req.requestOqc());
        return CommonResult.success();
    }

    @PostMapping("/{id}/request-oqc")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<Void> requestOqc(@PathVariable Long id) {
        packingService.requestOqc(id);
        return CommonResult.success();
    }

    @GetMapping("/{id}/labels")
    @PreAuthorize("@ss.has('shp:packing:print-label')")
    public CommonResult<Map<String, Object>> labels(@PathVariable Long id) {
        return CommonResult.success(packingService.labels(id));
    }

    // ==================== 生成出货单 ====================

    @PostMapping("/{id}/shipments")
    @PreAuthorize("@ss.has('shp:shipment:create')")
    public CommonResult<Long> generate(@PathVariable Long id, @RequestBody(required = false) GenerateReq req) {
        return CommonResult.success(shipmentService.generate(id, req));
    }
}
