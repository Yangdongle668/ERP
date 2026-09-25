package com.erp.module.sales.controller;

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
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.QuoteVOs.LoseReq;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationDetail;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationQuery;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationRow;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationSave;
import com.erp.module.sales.controller.vo.QuoteVOs.ToOrderLine;
import com.erp.module.sales.service.quotation.QuotationService;

import java.util.List;
import java.util.Map;

/** 报价单（需求 04-02） */
@Tag(name = "销售 - 报价单")
@RestController
@RequestMapping("/api/sales")
public class SalesQuotationController {

    private final QuotationService service;

    public SalesQuotationController(QuotationService service) {
        this.service = service;
    }

    @GetMapping("/quotations")
    @PreAuthorize("@ss.has('sales:quotation:query')")
    public CommonResult<PageResult<QuotationRow>> page(@Valid QuotationQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/quotations/{id}")
    @PreAuthorize("@ss.has('sales:quotation:query')")
    public CommonResult<QuotationDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/quotations")
    @PreAuthorize("@ss.has('sales:quotation:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody QuotationSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/quotations/{id}")
    @PreAuthorize("@ss.has('sales:quotation:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody QuotationSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/quotations/{id}")
    @PreAuthorize("@ss.has('sales:quotation:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/quotations/{id}/submit")
    @PreAuthorize("@ss.has('sales:quotation:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/quotations/{id}/send")
    @PreAuthorize("@ss.has('sales:quotation:send')")
    public CommonResult<Void> send(@PathVariable Long id) {
        service.send(id);
        return CommonResult.success();
    }

    @PostMapping("/quotations/{id}/revise")
    @PreAuthorize("@ss.has('sales:quotation:revise')")
    public CommonResult<Long> revise(@PathVariable Long id) {
        return CommonResult.success(service.revise(id));
    }

    @PostMapping("/quotations/{id}/lose")
    @PreAuthorize("@ss.has('sales:quotation:lose')")
    public CommonResult<Void> lose(@PathVariable Long id, @Valid @RequestBody LoseReq req) {
        service.lose(id, req);
        return CommonResult.success();
    }

    @PostMapping("/quotations/{id}/to-order")
    @PreAuthorize("@ss.has('sales:quotation:to-order')")
    public CommonResult<Long> toOrder(@PathVariable Long id, @Valid @RequestBody List<ToOrderLine> lines) {
        return CommonResult.success(service.toOrder(id, lines));
    }

    @GetMapping("/quotations/{id}/print-data")
    @PreAuthorize("@ss.hasAny('sales:quotation:print', 'sales:quotation:send')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String lang) {
        return CommonResult.success(service.printData(id, lang));
    }
}
