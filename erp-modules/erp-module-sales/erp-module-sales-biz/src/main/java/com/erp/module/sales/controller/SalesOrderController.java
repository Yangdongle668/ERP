package com.erp.module.sales.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.OrderVOs.CustomerDefaults;
import com.erp.module.sales.controller.vo.OrderVOs.ExecRow;
import com.erp.module.sales.controller.vo.OrderVOs.FromQuotationLine;
import com.erp.module.sales.controller.vo.OrderVOs.FromQuotationResult;
import com.erp.module.sales.controller.vo.OrderVOs.OpenLineRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderDetail;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderPaymentSummary;
import com.erp.module.sales.controller.vo.OrderVOs.OrderQuery;
import com.erp.module.sales.controller.vo.OrderVOs.OrderRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderSave;
import com.erp.module.sales.controller.vo.OrderVOs.SnapshotRow;
import com.erp.module.sales.controller.vo.OrderVOs.SubmitReq;
import com.erp.module.sales.controller.vo.QuoteVOs.QuoteOpenLine;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.service.SalExportSupport;
import com.erp.module.sales.service.order.OrderExecService;
import com.erp.module.sales.service.order.OrderImportService;
import com.erp.module.sales.service.order.OrderService;
import com.erp.module.sales.service.order.PaymentPlanService;
import com.erp.module.sales.service.quotation.QuotationService;
import com.erp.framework.datascope.DataScopes;
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

/** 销售订单（需求 04-03） */
@Tag(name = "销售 - 订单")
@RestController
@RequestMapping("/api/sales")
public class SalesOrderController {

    static final List<ExcelColumn<OrderRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", OrderRow::docNo),
            ExcelColumn.text("orderType", "类型", OrderRow::orderType),
            ExcelColumn.text("customerName", "客户", OrderRow::customerName),
            ExcelColumn.text("customerPoNo", "客户 PO 号", OrderRow::customerPoNo),
            ExcelColumn.text("currency", "币别", OrderRow::currency),
            ExcelColumn.number("totalAmount", "价税合计", OrderRow::totalAmount),
            ExcelColumn.number("minMarginRate", "最低毛利率", OrderRow::minMarginRate),
            ExcelColumn.date("earliestRequiredDate", "最早要求交期", OrderRow::earliestRequiredDate),
            ExcelColumn.number("shipProgress", "出货进度", OrderRow::shipProgress),
            ExcelColumn.number("receiveProgress", "回款进度", OrderRow::receiveProgress),
            ExcelColumn.text("deliveryRisk", "交期风险", r -> r.deliveryRisk() ? "风险" : ""),
            ExcelColumn.text("status", "状态", OrderRow::status),
            ExcelColumn.text("ownerName", "业务员", OrderRow::ownerName),
            ExcelColumn.date("docDate", "单据日期", OrderRow::docDate));

    static final List<ExcelColumn<OrderLineRow>> LINE_EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "订单号", OrderLineRow::docNo),
            ExcelColumn.date("docDate", "日期", OrderLineRow::docDate),
            ExcelColumn.text("customerName", "客户", OrderLineRow::customerName),
            ExcelColumn.text("customerPoNo", "客户 PO", OrderLineRow::customerPoNo),
            ExcelColumn.text("ownerName", "业务员", OrderLineRow::ownerName),
            ExcelColumn.number("lineNo", "行号", OrderLineRow::lineNo),
            ExcelColumn.text("materialCode", "物料编码", OrderLineRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", OrderLineRow::materialName),
            ExcelColumn.text("customerPartNo", "客户料号", OrderLineRow::customerPartNo),
            ExcelColumn.text("uom", "单位", OrderLineRow::uom),
            ExcelColumn.number("qty", "数量", OrderLineRow::qty),
            ExcelColumn.number("price", "单价", OrderLineRow::price),
            ExcelColumn.text("currency", "币别", OrderLineRow::currency),
            ExcelColumn.number("totalAmount", "金额", OrderLineRow::totalAmount),
            ExcelColumn.number("totalAmountBase", "金额(本位币)", OrderLineRow::totalAmountBase),
            ExcelColumn.date("requiredDate", "要求交期", OrderLineRow::requiredDate),
            ExcelColumn.date("promisedDate", "承诺交期", OrderLineRow::promisedDate),
            ExcelColumn.number("shippedQty", "已出货", OrderLineRow::shippedQty),
            ExcelColumn.number("openQty", "未出货", OrderLineRow::openQty),
            ExcelColumn.number("invoicedQty", "已开票", OrderLineRow::invoicedQty),
            ExcelColumn.text("lineStatus", "行状态", OrderLineRow::lineStatus),
            ExcelColumn.number("marginRate", "毛利率", OrderLineRow::marginRate));

    private final OrderService service;
    private final OrderExecService execService;
    private final OrderImportService importService;
    private final PaymentPlanService paymentPlanService;
    private final QuotationService quotationService;
    private final SalExportSupport exportSupport;

    public SalesOrderController(OrderService service, OrderExecService execService, OrderImportService importService,
                                PaymentPlanService paymentPlanService, QuotationService quotationService, SalExportSupport exportSupport) {
        this.service = service;
        this.execService = execService;
        this.importService = importService;
        this.paymentPlanService = paymentPlanService;
        this.quotationService = quotationService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/orders")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<PageResult<OrderRow>> page(@Valid OrderQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<OrderDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @GetMapping("/orders/customer-defaults")
    @PreAuthorize("@ss.hasAny('sales:order:create', 'sales:order:update', 'sales:quotation:create', 'sales:quotation:update')")
    public CommonResult<CustomerDefaults> customerDefaults(@RequestParam Long customerId) {
        return CommonResult.success(service.customerDefaults(customerId));
    }

    @PostMapping("/orders")
    @PreAuthorize("@ss.has('sales:order:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody OrderSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("@ss.has('sales:order:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody OrderSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/orders/{id}")
    @PreAuthorize("@ss.has('sales:order:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/orders/{id}/submit")
    @PreAuthorize("@ss.has('sales:order:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id, @RequestBody(required = false) SubmitReq req) {
        return CommonResult.success(service.submit(id, req != null && Boolean.TRUE.equals(req.confirmCredit())));
    }

    @PostMapping("/orders/{id}/unapprove")
    @PreAuthorize("@ss.has('sales:order:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.unapprove(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/orders/{id}/close")
    @PreAuthorize("@ss.has('sales:order:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.close(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/orders/{id}/void")
    @PreAuthorize("@ss.has('sales:order:void')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/orders/{id}/copy")
    @PreAuthorize("@ss.has('sales:order:create')")
    public CommonResult<SaveResult> copy(@PathVariable Long id) {
        return CommonResult.success(service.copy(id));
    }

    @GetMapping("/orders/{id}/execution")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<List<ExecRow>> execution(@PathVariable Long id) {
        return CommonResult.success(service.execution(id));
    }

    @GetMapping("/orders/{id}/snapshots")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<List<SnapshotRow>> snapshots(@PathVariable Long id) {
        return CommonResult.success(service.snapshots(id));
    }

    @GetMapping("/orders/{id}/payment-plans")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<OrderPaymentSummary> paymentPlans(@PathVariable Long id) {
        SalOrderDO o = service.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        return CommonResult.success(paymentPlanService.orderSummary(o));
    }

    /** @param template CONTRACT（销售合同）/ PI（Proforma Invoice） */
    @GetMapping("/orders/{id}/print-data")
    @PreAuthorize("@ss.has('sales:order:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String template,
                                                       @RequestParam(required = false) String lang) {
        return CommonResult.success(service.printData(id, template, lang));
    }

    // ==================== 从报价生成 ====================

    @GetMapping("/orders/quotation-lines")
    @PreAuthorize("@ss.has('sales:order:create')")
    public CommonResult<List<QuoteOpenLine>> quotationLines(@RequestParam(required = false) Long customerId) {
        return CommonResult.success(quotationService.openLines(customerId));
    }

    @PostMapping("/orders/from-quotations")
    @PreAuthorize("@ss.has('sales:order:create')")
    public CommonResult<FromQuotationResult> fromQuotations(@Valid @RequestBody List<FromQuotationLine> lines) {
        return CommonResult.success(quotationService.toOrders(lines, null));
    }

    // ==================== 出货选单 ====================

    @GetMapping("/order-lines/open")
    @PreAuthorize("@ss.hasAny('shp:notice:create', 'sales:order:query')")
    public CommonResult<PageResult<OpenLineRow>> openLines(@RequestParam(required = false) Long customerId, @RequestParam(required = false) Long materialId,
                                                           @RequestParam(required = false) String orderNo, @RequestParam(defaultValue = "1") int pageNo,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(execService.openLineRows(customerId, materialId, orderNo, pageNo, pageSize));
    }

    // ==================== 导入导出 ====================

    /** @param level ORDER 订单级 / LINE 明细级 */
    @GetMapping("/orders/export")
    @PreAuthorize("@ss.has('sales:order:export')")
    public void export(@Valid OrderQuery q, @RequestParam(defaultValue = "ORDER") String level, HttpServletResponse response) throws IOException {
        if ("LINE".equalsIgnoreCase(level)) {
            exportSupport.export(response, "销售订单明细", LINE_EXPORT_COLUMNS, q.getColumns(), limit -> service.lineRows(q, limit));
        } else {
            exportSupport.export(response, "销售订单", EXPORT_COLUMNS, q.getColumns(), limit -> service.listForExport(q, limit));
        }
    }

    @GetMapping("/orders/import-template")
    @PreAuthorize("@ss.has('sales:order:create')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "客户PO", OrderImportService.COLUMNS);
    }

    @PostMapping(value = "/orders/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('sales:order:create')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, OrderImportService.COLUMNS);
        Map<Integer, String> actions = importService.check(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "客户PO导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(OrderImportService.COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @PostMapping(value = "/orders/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('sales:order:create')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean partial) {
        List<ImportRow> rows = ExcelSupport.read(file, OrderImportService.COLUMNS);
        importService.check(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(importService.doImport(rows.stream().filter(r -> !r.hasError()).toList()));
    }
}
