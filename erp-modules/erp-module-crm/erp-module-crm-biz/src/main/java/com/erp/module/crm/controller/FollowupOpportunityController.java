package com.erp.module.crm.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.crm.controller.vo.FollowupVOs.FollowupQuery;
import com.erp.module.crm.controller.vo.FollowupVOs.FollowupRow;
import com.erp.module.crm.controller.vo.FollowupVOs.FollowupSave;
import com.erp.module.crm.controller.vo.OpportunityVOs.Funnel;
import com.erp.module.crm.controller.vo.OpportunityVOs.LoseReq;
import com.erp.module.crm.controller.vo.OpportunityVOs.OppQuery;
import com.erp.module.crm.controller.vo.OpportunityVOs.OppRow;
import com.erp.module.crm.controller.vo.OpportunityVOs.OppSave;
import com.erp.module.crm.controller.vo.OpportunityVOs.RemarkReq;
import com.erp.module.crm.controller.vo.OpportunityVOs.StageReq;
import com.erp.module.crm.controller.vo.OpportunityVOs.WinReq;
import com.erp.module.crm.service.FollowupService;
import com.erp.module.crm.service.OpportunityService;
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

/** 跟进记录（需求 03-04）与商机（需求 03-05） */
@Tag(name = "CRM - 跟进与商机")
@RestController
@RequestMapping("/api/crm")
public class FollowupOpportunityController {

    private final FollowupService followupService;
    private final OpportunityService opportunityService;

    public FollowupOpportunityController(FollowupService followupService, OpportunityService opportunityService) {
        this.followupService = followupService;
        this.opportunityService = opportunityService;
    }

    // ==================== 跟进 ====================

    @GetMapping("/followups")
    @PreAuthorize("@ss.has('crm:followup:query') or @ss.has('crm:customer:query')")
    public CommonResult<PageResult<FollowupRow>> followups(@Valid FollowupQuery q) {
        return CommonResult.success(followupService.page(q));
    }

    @PostMapping("/followups")
    @PreAuthorize("@ss.has('crm:followup:create')")
    public CommonResult<Long> createFollowup(@Valid @RequestBody FollowupSave req) {
        return CommonResult.success(followupService.create(req));
    }

    @PutMapping("/followups/{id}")
    @PreAuthorize("@ss.has('crm:followup:update')")
    public CommonResult<Void> updateFollowup(@PathVariable Long id, @Valid @RequestBody FollowupSave req) {
        followupService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/followups/{id}")
    @PreAuthorize("@ss.has('crm:followup:delete')")
    public CommonResult<Void> deleteFollowup(@PathVariable Long id) {
        followupService.delete(id);
        return CommonResult.success();
    }

    // ==================== 商机 ====================

    @GetMapping("/opportunities")
    @PreAuthorize("@ss.has('crm:opportunity:query') or @ss.has('crm:customer:query')")
    public CommonResult<PageResult<OppRow>> opportunities(@Valid OppQuery q) {
        return CommonResult.success(opportunityService.page(q));
    }

    @GetMapping("/opportunities/funnel")
    @PreAuthorize("@ss.has('crm:opportunity:query')")
    public CommonResult<Funnel> funnel(@Valid OppQuery q) {
        return CommonResult.success(opportunityService.funnel(q));
    }

    @GetMapping("/opportunities/{id}")
    @PreAuthorize("@ss.has('crm:opportunity:query')")
    public CommonResult<OppRow> opportunity(@PathVariable Long id) {
        return CommonResult.success(opportunityService.detail(id));
    }

    @PostMapping("/opportunities")
    @PreAuthorize("@ss.has('crm:opportunity:create')")
    public CommonResult<Long> createOpportunity(@Valid @RequestBody OppSave req) {
        return CommonResult.success(opportunityService.create(req));
    }

    @PutMapping("/opportunities/{id}")
    @PreAuthorize("@ss.has('crm:opportunity:update')")
    public CommonResult<Void> updateOpportunity(@PathVariable Long id, @Valid @RequestBody OppSave req) {
        opportunityService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/opportunities/{id}")
    @PreAuthorize("@ss.has('crm:opportunity:delete')")
    public CommonResult<Void> deleteOpportunity(@PathVariable Long id) {
        opportunityService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/opportunities/{id}/stage")
    @PreAuthorize("@ss.has('crm:opportunity:update')")
    public CommonResult<Void> stage(@PathVariable Long id, @Valid @RequestBody StageReq req) {
        opportunityService.changeStage(id, req.stage());
        return CommonResult.success();
    }

    @PostMapping("/opportunities/{id}/win")
    @PreAuthorize("@ss.has('crm:opportunity:close')")
    public CommonResult<Void> win(@PathVariable Long id, @Valid @RequestBody(required = false) WinReq req) {
        opportunityService.win(id, req == null ? null : req.orderNo());
        return CommonResult.success();
    }

    @PostMapping("/opportunities/{id}/lose")
    @PreAuthorize("@ss.has('crm:opportunity:close')")
    public CommonResult<Void> lose(@PathVariable Long id, @Valid @RequestBody LoseReq req) {
        opportunityService.lose(id, req.lostReason(), req.remark());
        return CommonResult.success();
    }

    @PostMapping("/opportunities/{id}/shelve")
    @PreAuthorize("@ss.has('crm:opportunity:close')")
    public CommonResult<Void> shelve(@PathVariable Long id, @Valid @RequestBody(required = false) RemarkReq req) {
        opportunityService.shelve(id, req == null ? null : req.remark());
        return CommonResult.success();
    }

    @PostMapping("/opportunities/{id}/resume")
    @PreAuthorize("@ss.has('crm:opportunity:close')")
    public CommonResult<Void> resume(@PathVariable Long id) {
        opportunityService.resume(id);
        return CommonResult.success();
    }
}
