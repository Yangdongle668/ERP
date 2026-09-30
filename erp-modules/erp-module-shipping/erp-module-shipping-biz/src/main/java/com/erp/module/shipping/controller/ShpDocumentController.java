package com.erp.module.shipping.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsDetail;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsItemVO;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsSave;
import com.erp.module.shipping.controller.vo.DocumentVOs.DocQuery;
import com.erp.module.shipping.controller.vo.DocumentVOs.DocRow;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceDetail;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceLineVO;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceSave;
import com.erp.module.shipping.controller.vo.DocumentVOs.PackingListDetail;
import com.erp.module.shipping.controller.vo.DocumentVOs.PackingListSave;
import com.erp.module.shipping.controller.vo.DocumentVOs.PlLine;
import com.erp.module.shipping.controller.vo.DocumentVOs.PlMergeReq;
import com.erp.module.shipping.service.document.DocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "出货 - 单证")
@RestController
public class ShpDocumentController {

    static final List<ExcelColumn<PlLine>> PL_COLUMNS = List.of(
            ExcelColumn.text("shipmentNo", "Shipment", PlLine::shipmentNo),
            ExcelColumn.text("cartonRange", "Carton No.", PlLine::cartonRange),
            ExcelColumn.text("description", "Description", PlLine::description),
            ExcelColumn.text("partNo", "Part No.", PlLine::partNo),
            ExcelColumn.number("qtyPerCarton", "Qty/Ctn", PlLine::qtyPerCarton),
            ExcelColumn.number("cartons", "Ctns", PlLine::cartons),
            ExcelColumn.number("qty", "Qty", PlLine::qty),
            ExcelColumn.text("uom", "Unit", PlLine::uom),
            ExcelColumn.number("netWeight", "N.W.(KG)", PlLine::netWeight),
            ExcelColumn.number("grossWeight", "G.W.(KG)", PlLine::grossWeight),
            ExcelColumn.number("cbm", "CBM", PlLine::cbm));

    static final List<ExcelColumn<InvoiceLineVO>> INVOICE_COLUMNS = List.of(
            ExcelColumn.number("lineNo", "No.", InvoiceLineVO::lineNo),
            ExcelColumn.text("customerPoNo", "PO No.", InvoiceLineVO::customerPoNo),
            ExcelColumn.text("customerPartNo", "Part No.", InvoiceLineVO::customerPartNo),
            ExcelColumn.text("description", "Description", InvoiceLineVO::description),
            ExcelColumn.text("hsCode", "HS Code", InvoiceLineVO::hsCode),
            ExcelColumn.text("origin", "Origin", InvoiceLineVO::origin),
            ExcelColumn.number("qty", "Qty", InvoiceLineVO::qty),
            ExcelColumn.text("uom", "Unit", InvoiceLineVO::uom),
            ExcelColumn.number("unitPrice", "Unit Price", InvoiceLineVO::unitPrice),
            ExcelColumn.number("amount", "Amount", InvoiceLineVO::amount));

    static final List<ExcelColumn<CustomsItemVO>> CUSTOMS_COLUMNS = List.of(
            ExcelColumn.number("seq", "项号", CustomsItemVO::seq),
            ExcelColumn.text("hsCode", "商品编号", CustomsItemVO::hsCode),
            ExcelColumn.text("declareName", "商品名称", CustomsItemVO::declareName),
            ExcelColumn.text("declareElements", "规格型号（申报要素）", CustomsItemVO::declareElements),
            ExcelColumn.number("qty", "数量", CustomsItemVO::qty),
            ExcelColumn.text("uom", "单位", CustomsItemVO::uom),
            ExcelColumn.number("secondQty", "第二数量", CustomsItemVO::secondQty),
            ExcelColumn.text("secondUom", "第二单位", CustomsItemVO::secondUom),
            ExcelColumn.number("unitPrice", "单价", CustomsItemVO::unitPrice),
            ExcelColumn.number("amount", "总价", CustomsItemVO::amount),
            ExcelColumn.text("origin", "原产国", CustomsItemVO::origin),
            ExcelColumn.number("netWeight", "净重", CustomsItemVO::netWeight),
            ExcelColumn.number("grossWeight", "毛重", CustomsItemVO::grossWeight));

    private final DocumentService service;

    public ShpDocumentController(DocumentService service) {
        this.service = service;
    }

    // ==================== Packing List ====================

    @GetMapping("/api/shipping/packing-lists")
    @PreAuthorize("@ss.has('shp:document:query')")
    public CommonResult<PageResult<DocRow>> packingLists(@Valid DocQuery q) {
        return CommonResult.success(service.packingLists(q));
    }

    @PostMapping("/api/shipping/packing-lists/merge")
    @PreAuthorize("@ss.has('shp:document:create')")
    public CommonResult<Long> mergePackingList(@Valid @RequestBody PlMergeReq req) {
        return CommonResult.success(service.mergePackingList(req.shipmentIds()));
    }

    @GetMapping("/api/shipping/packing-lists/{id}")
    @PreAuthorize("@ss.has('shp:document:query')")
    public CommonResult<PackingListDetail> packingList(@PathVariable Long id) {
        return CommonResult.success(service.packingList(id));
    }

    @PutMapping("/api/shipping/packing-lists/{id}")
    @PreAuthorize("@ss.has('shp:document:update')")
    public CommonResult<Void> updatePackingList(@PathVariable Long id, @Valid @RequestBody PackingListSave req) {
        service.updatePackingList(id, req);
        return CommonResult.success();
    }

    @GetMapping("/api/shipping/packing-lists/{id}/print-data")
    @PreAuthorize("@ss.has('shp:document:print')")
    public CommonResult<Map<String, Object>> packingListPrint(@PathVariable Long id) {
        return CommonResult.success(service.packingListPrintData(id));
    }

    @GetMapping("/api/shipping/packing-lists/{id}/export")
    @PreAuthorize("@ss.has('shp:document:print')")
    public void packingListExport(@PathVariable Long id, HttpServletResponse response) throws IOException {
        PackingListDetail d = service.packingList(id);
        ExcelSupport.export(response, "Packing List " + d.plNo(), PL_COLUMNS, d.lines(), null);
    }

    // ==================== Invoice ====================

    @GetMapping("/api/shipping/invoices")
    @PreAuthorize("@ss.has('shp:document:query')")
    public CommonResult<PageResult<DocRow>> invoices(@Valid DocQuery q) {
        return CommonResult.success(service.invoices(q));
    }

    @GetMapping("/api/shipping/invoices/{id}")
    @PreAuthorize("@ss.has('shp:document:query')")
    public CommonResult<InvoiceDetail> invoice(@PathVariable Long id) {
        return CommonResult.success(service.invoice(id));
    }

    @PutMapping("/api/shipping/invoices/{id}")
    @PreAuthorize("@ss.has('shp:document:update')")
    public CommonResult<Void> updateInvoice(@PathVariable Long id, @Valid @RequestBody InvoiceSave req) {
        service.updateInvoice(id, req);
        return CommonResult.success();
    }

    @GetMapping("/api/shipping/invoices/{id}/print-data")
    @PreAuthorize("@ss.has('shp:document:print')")
    public CommonResult<Map<String, Object>> invoicePrint(@PathVariable Long id) {
        return CommonResult.success(service.invoicePrintData(id));
    }

    @GetMapping("/api/shipping/invoices/{id}/export")
    @PreAuthorize("@ss.has('shp:document:print')")
    public void invoiceExport(@PathVariable Long id, HttpServletResponse response) throws IOException {
        InvoiceDetail d = service.invoice(id);
        ExcelSupport.export(response, "Invoice " + d.invoiceNo(), INVOICE_COLUMNS, d.lines(), null);
    }

    // ==================== 报关资料 ====================

    @GetMapping("/api/shipping/customs")
    @PreAuthorize("@ss.has('shp:document:query')")
    public CommonResult<PageResult<DocRow>> customsList(@Valid DocQuery q) {
        return CommonResult.success(service.customsList(q));
    }

    @GetMapping("/api/shipping/customs/{id}")
    @PreAuthorize("@ss.has('shp:document:query')")
    public CommonResult<CustomsDetail> customs(@PathVariable Long id) {
        return CommonResult.success(service.customs(id));
    }

    @PutMapping("/api/shipping/customs/{id}")
    @PreAuthorize("@ss.has('shp:document:update')")
    public CommonResult<Void> updateCustoms(@PathVariable Long id, @Valid @RequestBody CustomsSave req) {
        service.updateCustoms(id, req);
        return CommonResult.success();
    }

    @GetMapping("/api/shipping/customs/{id}/export")
    @PreAuthorize("@ss.has('shp:document:print')")
    public void customsExport(@PathVariable Long id, HttpServletResponse response) throws IOException {
        CustomsDetail d = service.customs(id);
        ExcelSupport.export(response, "报关资料 " + d.docCode(), CUSTOMS_COLUMNS, d.items(), null);
    }
}
