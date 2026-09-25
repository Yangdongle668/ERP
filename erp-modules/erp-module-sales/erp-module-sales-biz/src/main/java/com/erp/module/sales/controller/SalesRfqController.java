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
import com.erp.module.sales.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.QuoteVOs.AssignReq;
import com.erp.module.sales.controller.vo.QuoteVOs.CostSheetReq;
import com.erp.module.sales.controller.vo.QuoteVOs.CostSheetResp;
import com.erp.module.sales.controller.vo.QuoteVOs.FeasibilityReq;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqDetail;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqQuery;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqRow;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqSave;
import com.erp.module.sales.service.quotation.RfqService;

import java.util.List;

/** 客户询价 RFQ 与成本核算（需求 04-02） */
@Tag(name = "销售 - RFQ")
@RestController
@RequestMapping("/api/sales")
public class SalesRfqController {

    private final RfqService service;

    public SalesRfqController(RfqService service) {
        this.service = service;
    }

    @GetMapping("/rfqs")
    @PreAuthorize("@ss.has('sales:rfq:query')")
    public CommonResult<PageResult<RfqRow>> page(@Valid RfqQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/rfqs/{id}")
    @PreAuthorize("@ss.hasAny('sales:rfq:query', 'sales:rfq:cost')")
    public CommonResult<RfqDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/rfqs")
    @PreAuthorize("@ss.has('sales:rfq:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody RfqSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/rfqs/{id}")
    @PreAuthorize("@ss.has('sales:rfq:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody RfqSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/rfqs/{id}")
    @PreAuthorize("@ss.has('sales:rfq:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/rfqs/{id}/assign")
    @PreAuthorize("@ss.has('sales:rfq:assign')")
    public CommonResult<Void> assign(@PathVariable Long id, @RequestBody AssignReq req) {
        service.assign(id, req);
        return CommonResult.success();
    }

    /** 工程评估（被分派的工程师也可以操作，服务内校验） */
    @PostMapping("/rfqs/{id}/feasibility")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> feasibility(@PathVariable Long id, @Valid @RequestBody List<FeasibilityReq> req) {
        service.feasibility(id, req);
        return CommonResult.success();
    }

    @GetMapping("/rfqs/{id}/lines/{lineId}/cost-sheets")
    @PreAuthorize("@ss.has('sales:rfq:cost')")
    public CommonResult<List<CostSheetResp>> costSheets(@PathVariable Long id, @PathVariable Long lineId) {
        return CommonResult.success(service.costSheets(id, lineId));
    }

    @PostMapping("/rfqs/{id}/lines/{lineId}/cost-sheets/calc")
    @PreAuthorize("@ss.has('sales:rfq:cost')")
    public CommonResult<CostSheetResp> calc(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody CostSheetReq req) {
        return CommonResult.success(service.calc(id, lineId, req));
    }

    @PutMapping("/rfqs/{id}/lines/{lineId}/cost-sheets")
    @PreAuthorize("@ss.has('sales:rfq:cost')")
    public CommonResult<CostSheetResp> saveCostSheet(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody CostSheetReq req) {
        return CommonResult.success(service.saveCostSheet(id, lineId, req));
    }

    @PostMapping("/rfqs/{id}/to-quotation")
    @PreAuthorize("@ss.has('sales:quotation:create')")
    public CommonResult<Long> toQuotation(@PathVariable Long id) {
        return CommonResult.success(service.toQuotation(id));
    }

    @PostMapping("/rfqs/{id}/close")
    @PreAuthorize("@ss.has('sales:rfq:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.close(id, req.reason());
        return CommonResult.success();
    }
}
