package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.RfqVOs.AwardReq;
import com.erp.module.purchase.controller.vo.RfqVOs.AwardResult;
import com.erp.module.purchase.controller.vo.RfqVOs.QuoteMatrix;
import com.erp.module.purchase.controller.vo.RfqVOs.QuotesReq;
import com.erp.module.purchase.controller.vo.RfqVOs.QuotesResult;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqDetail;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqQuery;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqRow;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqSave;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierMaterialResp;
import com.erp.module.purchase.service.rfq.RfqService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 询价比价（需求 07-04 第 5 节）。报价、比价需要 pur:price:view（R05） */
@Tag(name = "资材 - 询价比价")
@RestController
@RequestMapping("/api/purchase/rfqs")
public class RfqController {

    private final RfqService service;

    public RfqController(RfqService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pur:rfq:query')")
    public CommonResult<PageResult<RfqRow>> page(@Valid RfqQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('pur:rfq:query')")
    public CommonResult<RfqDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @Operation(summary = "默认带出这些物料的可供供应商")
    @GetMapping("/default-suppliers")
    @PreAuthorize("@ss.hasAny('pur:rfq:create', 'pur:rfq:update')")
    public CommonResult<List<SupplierMaterialResp>> defaultSuppliers(@RequestParam String materialIds) {
        return CommonResult.success(service.defaultSuppliers(Arrays.stream(materialIds.split(",")).map(String::trim)
                .filter(s -> s.matches("\\d+")).map(Long::valueOf).toList()));
    }

    @PostMapping
    @PreAuthorize("@ss.has('pur:rfq:create')")
    public CommonResult<Long> create(@Valid @RequestBody RfqSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pur:rfq:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody RfqSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('pur:rfq:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "发出询价（R01）")
    @PostMapping("/{id}/send")
    @PreAuthorize("@ss.has('pur:rfq:update')")
    public CommonResult<Void> send(@PathVariable Long id) {
        service.send(id);
        return CommonResult.success();
    }

    @GetMapping("/{id}/quotes")
    @PreAuthorize("@ss.has('pur:rfq:query') and @ss.has('pur:price:view')")
    public CommonResult<QuoteMatrix> quotes(@PathVariable Long id) {
        return CommonResult.success(service.matrix(id));
    }

    @PutMapping("/{id}/quotes")
    @PreAuthorize("@ss.has('pur:rfq:quote') and @ss.has('pur:price:view')")
    public CommonResult<QuotesResult> saveQuotes(@PathVariable Long id, @Valid @RequestBody QuotesReq req) {
        return CommonResult.success(service.saveQuotes(id, req.quotes()));
    }

    @PostMapping("/{id}/end-quote")
    @PreAuthorize("@ss.has('pur:rfq:quote')")
    public CommonResult<Void> endQuote(@PathVariable Long id) {
        service.endQuote(id);
        return CommonResult.success();
    }

    @Operation(summary = "定标（R02）：每家中标供应商生成一张草稿调价单")
    @PostMapping("/{id}/award")
    @PreAuthorize("@ss.has('pur:rfq:award') and @ss.has('pur:price:view')")
    public CommonResult<AwardResult> award(@PathVariable Long id, @Valid @RequestBody AwardReq req) {
        return CommonResult.success(new AwardResult(service.award(id, req.lines())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.has('pur:rfq:update')")
    public CommonResult<Void> cancel(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.cancel(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('pur:rfq:query')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
