package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanDetail;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanQuery;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanRow;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanSave;
import com.erp.module.pmc.service.shipping.ShippingPlanService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PMC - 出货计划")
@RestController
@RequestMapping("/api/pmc/shipping-plans")
public class PmcShippingPlanController {

    private final ShippingPlanService service;

    public PmcShippingPlanController(ShippingPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pmc:shipping-plan:query')")
    public CommonResult<PageResult<PlanRow>> page(@Valid PlanQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('pmc:shipping-plan:query')")
    public CommonResult<PlanDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('pmc:shipping-plan:create')")
    public CommonResult<Long> create(@Valid @RequestBody PlanSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pmc:shipping-plan:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody PlanSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('pmc:shipping-plan:update')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("@ss.hasAny('pmc:shipping-plan:create', 'pmc:shipping-plan:update')")
    public CommonResult<PlanDetail> generate(@PathVariable Long id) {
        return CommonResult.success(service.generate(id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.has('pmc:shipping-plan:publish')")
    public CommonResult<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('pmc:shipping-plan:publish')")
    public CommonResult<Void> close(@PathVariable Long id) {
        service.close(id);
        return CommonResult.success();
    }
}
