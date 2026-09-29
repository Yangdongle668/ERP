package com.erp.module.shipping.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.shipping.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.LogisticsEventReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.LogisticsReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentDetail;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentQuery;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentRow;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentSave;
import com.erp.module.shipping.controller.vo.ShipmentVOs.SignReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.SubmitResult;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpExportSupport;
import com.erp.module.shipping.service.document.DocumentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "出货 - 出货单 / 物流")
@RestController
public class ShpShipmentController {

    static final List<ExcelColumn<ShipmentRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", ShipmentRow::docNo),
            ExcelColumn.text("noticeNo", "出货通知", ShipmentRow::noticeNo),
            ExcelColumn.text("customerName", "客户", ShipmentRow::customerName),
            ExcelColumn.date("shipDate", "出货日期", ShipmentRow::shipDate),
            ExcelColumn.text("transportMode", "运输方式", ShipmentRow::transportMode),
            ExcelColumn.text("portOfDestination", "目的港", ShipmentRow::portOfDestination),
            ExcelColumn.number("totalQty", "数量", ShipmentRow::totalQty),
            ExcelColumn.text("currency", "币别", ShipmentRow::currency),
            ExcelColumn.number("totalAmount", "金额", ShipmentRow::totalAmount),
            ExcelColumn.number("cartonCount", "箱数", ShipmentRow::cartonCount),
            ExcelColumn.text("blNo", "提单号", ShipmentRow::blNo),
            ExcelColumn.date("etd", "ETD", ShipmentRow::etd),
            ExcelColumn.date("eta", "ETA", ShipmentRow::eta),
            ExcelColumn.text("logisticsStatus", "物流状态", ShipmentRow::logisticsStatus),
            ExcelColumn.text("shipmentStatus", "状态", r -> ShipmentStatus.valueOf(r.shipmentStatus()).label()),
            ExcelColumn.text("ownerName", "船务", ShipmentRow::ownerName));

    private final ShipmentService service;
    private final DocumentService documentService;
    private final ShpExportSupport exportSupport;

    public ShpShipmentController(ShipmentService service, DocumentService documentService, ShpExportSupport exportSupport) {
        this.service = service;
        this.documentService = documentService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/api/shipping/shipments")
    @PreAuthorize("@ss.has('shp:shipment:query')")
    public CommonResult<PageResult<ShipmentRow>> page(@Valid ShipmentQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/api/shipping/shipments/export")
    @PreAuthorize("@ss.has('shp:shipment:export')")
    public void export(@Valid ShipmentQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "出货单", EXPORT_COLUMNS, columns, limit -> service.list(q).stream().limit(limit).toList());
    }

    /** 物流跟踪列表（默认已出货未签收） */
    @GetMapping("/api/shipping/logistics")
    @PreAuthorize("@ss.has('shp:logistics:query')")
    public CommonResult<PageResult<ShipmentRow>> logistics(@Valid ShipmentQuery q) {
        if (q.getStatuses() == null) q.setStatuses(ShipmentStatus.SHIPPED.name() + "," + ShipmentStatus.COMPLETED.name());
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/api/shipping/shipments/{id}")
    @PreAuthorize("@ss.hasAny('shp:shipment:query','shp:logistics:query')")
    public CommonResult<ShipmentDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PutMapping("/api/shipping/shipments/{id}")
    @PreAuthorize("@ss.has('shp:shipment:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ShipmentSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/api/shipping/shipments/{id}")
    @PreAuthorize("@ss.has('shp:shipment:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/api/shipping/shipments/{id}/void")
    @PreAuthorize("@ss.has('shp:shipment:delete')")
    public CommonResult<Void> voidShipment(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidShipment(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/api/shipping/shipments/{id}/submit")
    @PreAuthorize("@ss.has('shp:shipment:submit')")
    public CommonResult<SubmitResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/api/shipping/shipments/{id}/withdraw")
    @PreAuthorize("@ss.has('shp:shipment:withdraw')")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return CommonResult.success();
    }

    @GetMapping("/api/shipping/shipments/{id}/print-data")
    @PreAuthorize("@ss.has('shp:shipment:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    // ==================== 物流 ====================

    @PutMapping("/api/shipping/shipments/{id}/logistics")
    @PreAuthorize("@ss.has('shp:logistics:update')")
    public CommonResult<Void> logistics(@PathVariable Long id, @RequestBody LogisticsReq req) {
        service.logistics(id, req);
        return CommonResult.success();
    }

    @PostMapping("/api/shipping/shipments/{id}/sign")
    @PreAuthorize("@ss.has('shp:logistics:update')")
    public CommonResult<Void> sign(@PathVariable Long id, @Valid @RequestBody SignReq req) {
        service.sign(id, req);
        return CommonResult.success();
    }

    @PostMapping("/api/shipping/shipments/{id}/logistics-events")
    @PreAuthorize("@ss.has('shp:logistics:update')")
    public CommonResult<Void> addEvent(@PathVariable Long id, @Valid @RequestBody LogisticsEventReq req) {
        service.addLogisticsEvent(id, req.logisticsStatus(), req.occurredAt(), req.location(), req.remark());
        return CommonResult.success();
    }

    // ==================== 单证生成 ====================

    @PostMapping("/api/shipping/shipments/{id}/packing-list")
    @PreAuthorize("@ss.has('shp:document:create')")
    public CommonResult<Long> packingList(@PathVariable Long id) {
        return CommonResult.success(documentService.createPackingList(id));
    }

    @PostMapping("/api/shipping/shipments/{id}/invoice")
    @PreAuthorize("@ss.has('shp:document:create')")
    public CommonResult<Long> invoice(@PathVariable Long id) {
        return CommonResult.success(documentService.createInvoice(id));
    }

    @PostMapping("/api/shipping/shipments/{id}/customs")
    @PreAuthorize("@ss.has('shp:document:create')")
    public CommonResult<Long> customs(@PathVariable Long id) {
        return CommonResult.success(documentService.createCustoms(id));
    }
}
