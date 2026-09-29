package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.pmc.controller.vo.AlertVOs.AlertQuery;
import com.erp.module.pmc.controller.vo.AlertVOs.AlertRow;
import com.erp.module.pmc.controller.vo.AlertVOs.HandleReq;
import com.erp.module.pmc.controller.vo.AlertVOs.RecalcResult;
import com.erp.module.pmc.controller.vo.AlertVOs.Summary;
import com.erp.module.pmc.service.alert.DeliveryAlertService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PMC - 交期预警")
@RestController
@RequestMapping("/api/pmc/delivery-alerts")
public class PmcAlertController {

    private final DeliveryAlertService service;

    public PmcAlertController(DeliveryAlertService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pmc:alert:query')")
    public CommonResult<PageResult<AlertRow>> page(@Valid AlertQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/summary")
    @PreAuthorize("@ss.has('pmc:alert:query')")
    public CommonResult<Summary> summary(AlertQuery q) {
        return CommonResult.success(service.summary(q));
    }

    @PostMapping("/recalculate")
    @PreAuthorize("@ss.hasAny('pmc:alert:handle', 'pmc:mrp:run')")
    public CommonResult<RecalcResult> recalculate() {
        return CommonResult.success(service.recalculate());
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize("@ss.has('pmc:alert:handle')")
    public CommonResult<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        service.handle(id, req.remark());
        return CommonResult.success();
    }

    @PostMapping("/{id}/ignore")
    @PreAuthorize("@ss.has('pmc:alert:handle')")
    public CommonResult<Void> ignore(@PathVariable Long id, @RequestBody HandleReq req) {
        service.ignore(id, req.remark());
        return CommonResult.success();
    }
}
