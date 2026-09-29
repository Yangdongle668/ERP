package com.erp.module.production.controller;

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
import com.erp.module.production.controller.vo.CommonVOs.DocResult;
import com.erp.module.production.controller.vo.MaterialDocVOs.ByKitReq;
import com.erp.module.production.controller.vo.MaterialDocVOs.CreateResult;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueCandidate;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueDetail;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueQuery;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueRow;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueSave;
import com.erp.module.production.controller.vo.MaterialDocVOs.OverReq;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnCandidate;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnDetail;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnQuery;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnRow;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnSave;
import com.erp.module.production.service.material.IssueService;
import com.erp.module.production.service.material.ReturnService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 领料单、超领单、退料单（需求 09-03） */
@Tag(name = "生产 - 领料与退料")
@RestController
@RequestMapping("/api/production")
public class ProductionMaterialController {

    private final IssueService issueService;
    private final ReturnService returnService;

    public ProductionMaterialController(IssueService issueService, ReturnService returnService) {
        this.issueService = issueService;
        this.returnService = returnService;
    }

    // ==================== 领料 ====================

    @GetMapping("/issues")
    @PreAuthorize("@ss.has('mfg:issue:query')")
    public CommonResult<PageResult<IssueRow>> issues(@Valid IssueQuery q) {
        return CommonResult.success(issueService.page(q));
    }

    @GetMapping("/issues/candidates")
    @PreAuthorize("@ss.hasAny('mfg:issue:create', 'mfg:issue:update', 'mfg:issue:over')")
    public CommonResult<List<IssueCandidate>> issueCandidates(@RequestParam Long prodOrderId, @RequestParam(required = false) BigDecimal kitQty) {
        return CommonResult.success(issueService.candidates(prodOrderId, kitQty));
    }

    @GetMapping("/issues/{id}")
    @PreAuthorize("@ss.has('mfg:issue:query')")
    public CommonResult<IssueDetail> issue(@PathVariable Long id) {
        return CommonResult.success(issueService.detail(id));
    }

    @PostMapping("/issues")
    @PreAuthorize("@ss.has('mfg:issue:create')")
    public CommonResult<CreateResult> createIssue(@Valid @RequestBody IssueSave req) {
        return CommonResult.success(issueService.create(req));
    }

    @PutMapping("/issues/{id}")
    @PreAuthorize("@ss.has('mfg:issue:update')")
    public CommonResult<Void> updateIssue(@PathVariable Long id, @Valid @RequestBody IssueSave req) {
        issueService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/issues/{id}")
    @PreAuthorize("@ss.has('mfg:issue:delete')")
    public CommonResult<Void> deleteIssue(@PathVariable Long id) {
        issueService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/issues/{id}/submit")
    @PreAuthorize("@ss.hasAny('mfg:issue:submit', 'mfg:issue:over')")
    public CommonResult<DocResult> submitIssue(@PathVariable Long id) {
        return CommonResult.success(issueService.submit(id));
    }

    @PostMapping("/issues/{id}/withdraw")
    @PreAuthorize("@ss.hasAny('mfg:issue:submit', 'mfg:issue:over')")
    public CommonResult<Void> withdrawIssue(@PathVariable Long id) {
        issueService.withdraw(id);
        return CommonResult.success();
    }

    @PostMapping("/issues/by-kit")
    @PreAuthorize("@ss.has('mfg:issue:create')")
    public CommonResult<CreateResult> byKit(@Valid @RequestBody ByKitReq req) {
        return CommonResult.success(issueService.byKit(req));
    }

    @PostMapping("/issues/over")
    @PreAuthorize("@ss.has('mfg:issue:over')")
    public CommonResult<CreateResult> over(@Valid @RequestBody OverReq req) {
        return CommonResult.success(issueService.over(req));
    }

    @GetMapping("/issues/{id}/print-data")
    @PreAuthorize("@ss.has('mfg:issue:print')")
    public CommonResult<Map<String, Object>> issuePrint(@PathVariable Long id) {
        return CommonResult.success(issueService.printData(id));
    }

    // ==================== 退料 ====================

    @GetMapping("/returns")
    @PreAuthorize("@ss.has('mfg:return:query')")
    public CommonResult<PageResult<ReturnRow>> returns(@Valid ReturnQuery q) {
        return CommonResult.success(returnService.page(q));
    }

    @GetMapping("/returns/candidates")
    @PreAuthorize("@ss.hasAny('mfg:return:create', 'mfg:return:update')")
    public CommonResult<List<ReturnCandidate>> returnCandidates(@RequestParam Long prodOrderId, @RequestParam String returnType,
                                                                @RequestParam(required = false) Long excludeId) {
        return CommonResult.success(returnService.candidates(prodOrderId, returnType, excludeId));
    }

    @GetMapping("/returns/{id}")
    @PreAuthorize("@ss.has('mfg:return:query')")
    public CommonResult<ReturnDetail> returnDetail(@PathVariable Long id) {
        return CommonResult.success(returnService.detail(id));
    }

    @PostMapping("/returns")
    @PreAuthorize("@ss.has('mfg:return:create')")
    public CommonResult<CreateResult> createReturn(@Valid @RequestBody ReturnSave req) {
        return CommonResult.success(returnService.create(req));
    }

    @PutMapping("/returns/{id}")
    @PreAuthorize("@ss.has('mfg:return:update')")
    public CommonResult<Void> updateReturn(@PathVariable Long id, @Valid @RequestBody ReturnSave req) {
        returnService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/returns/{id}")
    @PreAuthorize("@ss.has('mfg:return:delete')")
    public CommonResult<Void> deleteReturn(@PathVariable Long id) {
        returnService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/returns/{id}/submit")
    @PreAuthorize("@ss.has('mfg:return:submit')")
    public CommonResult<DocResult> submitReturn(@PathVariable Long id) {
        return CommonResult.success(returnService.submit(id));
    }

    @PostMapping("/returns/{id}/withdraw")
    @PreAuthorize("@ss.has('mfg:return:submit')")
    public CommonResult<Void> withdrawReturn(@PathVariable Long id) {
        returnService.withdraw(id);
        return CommonResult.success();
    }

    @GetMapping("/returns/{id}/print-data")
    @PreAuthorize("@ss.has('mfg:return:print')")
    public CommonResult<Map<String, Object>> returnPrint(@PathVariable Long id) {
        return CommonResult.success(returnService.printData(id));
    }
}
