package com.erp.module.quality.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.erp.module.quality.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.quality.controller.vo.NcrVOs.CapaSuggestReq;
import com.erp.module.quality.controller.vo.NcrVOs.CloseReq;
import com.erp.module.quality.controller.vo.NcrVOs.DoneReq;
import com.erp.module.quality.controller.vo.NcrVOs.NcrDetail;
import com.erp.module.quality.controller.vo.NcrVOs.NcrQuery;
import com.erp.module.quality.controller.vo.NcrVOs.NcrRow;
import com.erp.module.quality.controller.vo.NcrVOs.NcrSave;
import com.erp.module.quality.service.ncr.NcrService;

@Tag(name = "品质 - NCR 与 MRB")
@RestController
@RequestMapping("/api/quality/ncrs")
public class QcNcrController {

    private final NcrService service;

    public QcNcrController(NcrService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('qc:ncr:query')")
    public CommonResult<PageResult<NcrRow>> page(@Valid NcrQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('qc:ncr:query')")
    public CommonResult<NcrDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/suggest-capa")
    @PreAuthorize("@ss.hasAny('qc:ncr:create','qc:ncr:update')")
    public CommonResult<Boolean> suggestCapa(@RequestBody CapaSuggestReq req) {
        return CommonResult.success(service.suggestCapa(req));
    }

    @PostMapping
    @PreAuthorize("@ss.has('qc:ncr:create')")
    public CommonResult<Long> create(@Valid @RequestBody NcrSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('qc:ncr:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody NcrSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('qc:ncr:submit')")
    public CommonResult<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/dispositions/{dispId}/done")
    @PreAuthorize("@ss.has('qc:ncr:update')")
    public CommonResult<Void> done(@PathVariable Long id, @PathVariable Long dispId, @RequestBody(required = false) DoneReq req) {
        service.markDone(id, dispId, req == null ? null : req.followDocNo());
        return CommonResult.success();
    }

    @PostMapping("/{id}/create-purchase-return")
    @PreAuthorize("@ss.has('qc:ncr:update')")
    public CommonResult<String> purchaseReturn(@PathVariable Long id) {
        return CommonResult.success(service.notifyPurchaseReturn(id));
    }

    @PostMapping("/{id}/create-rework-order")
    @PreAuthorize("@ss.has('qc:ncr:update')")
    public CommonResult<String> reworkOrder(@PathVariable Long id) {
        return CommonResult.success(service.createReworkOrder(id));
    }

    @PostMapping("/{id}/create-scrap-out")
    @PreAuthorize("@ss.has('qc:ncr:update')")
    public CommonResult<String> scrapOut(@PathVariable Long id) {
        return CommonResult.success(service.createScrapOut(id));
    }

    @PostMapping("/{id}/create-capa")
    @PreAuthorize("@ss.has('qc:capa:create')")
    public CommonResult<Long> createCapa(@PathVariable Long id) {
        return CommonResult.success(service.createCapa(id));
    }

    @PostMapping("/{id}/create-scar")
    @PreAuthorize("@ss.has('qc:scar:create')")
    public CommonResult<Long> createScar(@PathVariable Long id) {
        return CommonResult.success(service.createScar(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('qc:ncr:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody(required = false) CloseReq req) {
        service.close(id, req != null && Boolean.TRUE.equals(req.unfreeze()));
        return CommonResult.success();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('qc:ncr:void')")
    public CommonResult<Void> voidNcr(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidNcr(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.hasAny('qc:ncr:print','qc:ncr:query')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String lang) {
        return CommonResult.success(service.printData(id));
    }
}
