package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeDetail;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeQuery;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeRow;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeSave;
import com.erp.module.purchase.controller.vo.OrderVOs.ConfirmDatesReq;
import com.erp.module.purchase.controller.vo.OrderVOs.FromRequisitionReq;
import com.erp.module.purchase.controller.vo.OrderVOs.FromRequisitionResult;
import com.erp.module.purchase.controller.vo.OrderVOs.OpenLine;
import com.erp.module.purchase.controller.vo.OrderVOs.OpenLineQuery;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderDetail;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderLineResp;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderQuery;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderRow;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderSave;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderSaveResult;
import com.erp.module.purchase.service.PurExportSupport;
import com.erp.module.purchase.service.order.OrderChangeService;
import com.erp.module.purchase.service.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
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

/** 采购订单与订单变更（需求 07-05 第 6 节） */
@Tag(name = "资材 - 采购订单")
@RestController
@RequestMapping("/api/purchase")
public class OrderController {

    static final Map<String, String> STATUS = Map.of("DRAFT", "草稿", "PENDING_APPROVAL", "待审批", "APPROVED", "已审核", "IN_PROGRESS", "执行中",
            "COMPLETED", "已完成", "CLOSED", "已关闭", "VOIDED", "已作废");

    static final List<ExcelColumn<OrderRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", OrderRow::docNo),
            ExcelColumn.text("orderType", "类型", r -> "SAMPLE".equals(r.orderType()) ? "样品" : "标准"),
            ExcelColumn.text("supplierName", "供应商", OrderRow::supplierName),
            ExcelColumn.text("ownerName", "采购员", OrderRow::ownerName),
            ExcelColumn.text("currency", "币别", OrderRow::currency),
            ExcelColumn.number("totalAmount", "价税合计", OrderRow::totalAmount),
            ExcelColumn.date("earliestDate", "最早交期", OrderRow::earliestDate),
            ExcelColumn.number("orderedQty", "订购数量", OrderRow::orderedQty),
            ExcelColumn.number("receivedQty", "已到货", OrderRow::receivedQty),
            ExcelColumn.number("overdueLines", "逾期行数", OrderRow::overdueLines),
            ExcelColumn.dateTime("sentAt", "发送时间", OrderRow::sentAt),
            ExcelColumn.number("orderVersion", "版本", OrderRow::orderVersion),
            ExcelColumn.text("status", "状态", r -> STATUS.get(r.status())),
            ExcelColumn.date("docDate", "单据日期", OrderRow::docDate));

    private final OrderService service;
    private final OrderChangeService changeService;
    private final PurExportSupport exportSupport;

    public OrderController(OrderService service, OrderChangeService changeService, PurExportSupport exportSupport) {
        this.service = service;
        this.changeService = changeService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/orders")
    @PreAuthorize("@ss.has('pur:order:query')")
    public CommonResult<PageResult<OrderRow>> page(@Valid OrderQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/orders/export")
    @PreAuthorize("@ss.has('pur:order:export')")
    public void export(@Valid OrderQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "采购订单", EXPORT_COLUMNS, q.getColumns(), limit -> service.listForExport(q, limit));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@ss.has('pur:order:query')")
    public CommonResult<OrderDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/orders")
    @PreAuthorize("@ss.has('pur:order:create')")
    public CommonResult<OrderSaveResult> create(@Valid @RequestBody OrderSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("@ss.has('pur:order:update')")
    public CommonResult<OrderSaveResult> update(@PathVariable Long id, @Valid @RequestBody OrderSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/orders/{id}")
    @PreAuthorize("@ss.has('pur:order:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "从申请生成：按供应商分组，每个供应商生成一张草稿订单")
    @PostMapping("/orders/from-requisitions")
    @PreAuthorize("@ss.has('pur:order:create')")
    public CommonResult<FromRequisitionResult> fromRequisitions(@Valid @RequestBody FromRequisitionReq req) {
        return CommonResult.success(service.fromRequisitions(req.lines()));
    }

    @PostMapping("/orders/{id}/submit")
    @PreAuthorize("@ss.has('pur:order:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/orders/{id}/unapprove")
    @PreAuthorize("@ss.has('pur:order:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unapprove(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/orders/{id}/close")
    @PreAuthorize("@ss.has('pur:order:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.close(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/orders/{id}/void")
    @PreAuthorize("@ss.has('pur:order:void')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @Operation(summary = "回复交期")
    @PutMapping("/orders/{id}/confirmed-dates")
    @PreAuthorize("@ss.has('pur:order:confirm-date')")
    public CommonResult<Void> confirmDates(@PathVariable Long id, @Valid @RequestBody ConfirmDatesReq req) {
        service.confirmDates(id, req.lines());
        return CommonResult.success();
    }

    @Operation(summary = "发送给供应商：记录发送时间")
    @PostMapping("/orders/{id}/sent")
    @PreAuthorize("@ss.has('pur:order:print')")
    public CommonResult<Void> sent(@PathVariable Long id) {
        service.markSent(id);
        return CommonResult.success();
    }

    @GetMapping("/orders/{id}/print-data")
    @PreAuthorize("@ss.has('pur:order:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String lang) {
        return CommonResult.success(service.printData(id, lang));
    }

    @Operation(summary = "到货选单：已审核/执行中订单未关闭、未到货数量 > 0 的行")
    @GetMapping("/order-lines/open")
    @PreAuthorize("@ss.hasAny('pur:receipt:create', 'pur:receipt:update')")
    public CommonResult<PageResult<OpenLine>> openLines(@Valid OpenLineQuery q) {
        return CommonResult.success(service.openLines(q));
    }

    // ==================== 变更单 ====================

    @GetMapping("/order-changes")
    @PreAuthorize("@ss.has('pur:order:query')")
    public CommonResult<PageResult<ChangeRow>> changes(@Valid ChangeQuery q) {
        return CommonResult.success(changeService.page(q));
    }

    @Operation(summary = "新建变更单时带出订单当前行")
    @GetMapping("/order-changes/template")
    @PreAuthorize("@ss.has('pur:order:change')")
    public CommonResult<List<OrderLineResp>> changeTemplate(@RequestParam Long orderId) {
        return CommonResult.success(changeService.template(orderId));
    }

    @GetMapping("/order-changes/{id}")
    @PreAuthorize("@ss.has('pur:order:query')")
    public CommonResult<ChangeDetail> changeDetail(@PathVariable Long id) {
        return CommonResult.success(changeService.detail(id));
    }

    @PostMapping("/order-changes")
    @PreAuthorize("@ss.has('pur:order:change')")
    public CommonResult<Long> createChange(@Valid @RequestBody ChangeSave req) {
        return CommonResult.success(changeService.create(req));
    }

    @PutMapping("/order-changes/{id}")
    @PreAuthorize("@ss.has('pur:order:change')")
    public CommonResult<Void> updateChange(@PathVariable Long id, @Valid @RequestBody ChangeSave req) {
        changeService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/order-changes/{id}")
    @PreAuthorize("@ss.has('pur:order:change')")
    public CommonResult<Void> deleteChange(@PathVariable Long id) {
        changeService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/order-changes/{id}/submit")
    @PreAuthorize("@ss.has('pur:order:change')")
    public CommonResult<DocResult> submitChange(@PathVariable Long id) {
        return CommonResult.success(changeService.submit(id));
    }
}
