package com.erp.module.quality.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.quality.controller.vo.BasicVOs.DefectCodeQuery;
import com.erp.module.quality.controller.vo.BasicVOs.DefectCodeRow;
import com.erp.module.quality.controller.vo.BasicVOs.DefectCodeSave;
import com.erp.module.quality.controller.vo.BasicVOs.ItemLibQuery;
import com.erp.module.quality.controller.vo.BasicVOs.ItemLibRow;
import com.erp.module.quality.controller.vo.BasicVOs.ItemLibSave;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingPreviewReq;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingQuery;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingResult;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingRow;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingSave;
import com.erp.module.quality.service.basic.BasicDataService;
import com.erp.module.quality.service.basic.SamplingService;
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

import java.util.List;

@Tag(name = "品质 - 检验项目库 / 抽样方案 / 缺陷代码")
@RestController
@RequestMapping("/api/quality")
public class QcBasicController {

    private static final String READ = "@ss.hasAny('qc:standard:query','qc:iqc:query','qc:ipqc:query','qc:fqc:query','qc:oqc:query','qc:return:query','qc:ncr:query')";

    private final BasicDataService basicDataService;
    private final SamplingService samplingService;

    public QcBasicController(BasicDataService basicDataService, SamplingService samplingService) {
        this.basicDataService = basicDataService;
        this.samplingService = samplingService;
    }

    // ==================== 项目库 ====================

    @GetMapping("/inspection-items")
    @PreAuthorize("@ss.has('qc:standard:query')")
    public CommonResult<PageResult<ItemLibRow>> items(@Valid ItemLibQuery q) {
        return CommonResult.success(basicDataService.pageItems(q));
    }

    @PostMapping("/inspection-items")
    @PreAuthorize("@ss.has('qc:defect-code:manage')")
    public CommonResult<Long> createItem(@Valid @RequestBody ItemLibSave req) {
        return CommonResult.success(basicDataService.createItem(req));
    }

    @PutMapping("/inspection-items/{id}")
    @PreAuthorize("@ss.has('qc:defect-code:manage')")
    public CommonResult<Void> updateItem(@PathVariable Long id, @Valid @RequestBody ItemLibSave req) {
        basicDataService.updateItem(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/inspection-items/{id}")
    @PreAuthorize("@ss.has('qc:defect-code:manage')")
    public CommonResult<Void> deleteItem(@PathVariable Long id) {
        basicDataService.deleteItem(id);
        return CommonResult.success();
    }

    // ==================== 抽样方案 ====================

    @GetMapping("/sampling-plans")
    @PreAuthorize("@ss.has('qc:standard:query')")
    public CommonResult<PageResult<SamplingRow>> samplingPlans(@Valid SamplingQuery q) {
        return CommonResult.success(samplingService.page(q));
    }

    @GetMapping("/sampling-plans/enabled")
    @PreAuthorize(READ)
    public CommonResult<List<SamplingRow>> enabledPlans() {
        return CommonResult.success(samplingService.listEnabled());
    }

    @PostMapping("/sampling-plans")
    @PreAuthorize("@ss.has('qc:sampling:manage')")
    public CommonResult<Long> createPlan(@Valid @RequestBody SamplingSave req) {
        return CommonResult.success(samplingService.create(req));
    }

    @PutMapping("/sampling-plans/{id}")
    @PreAuthorize("@ss.has('qc:sampling:manage')")
    public CommonResult<Void> updatePlan(@PathVariable Long id, @Valid @RequestBody SamplingSave req) {
        samplingService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/sampling-plans/{id}")
    @PreAuthorize("@ss.has('qc:sampling:manage')")
    public CommonResult<Void> deletePlan(@PathVariable Long id) {
        samplingService.delete(id);
        return CommonResult.success();
    }

    /** 计算预览：输入批量 → 样本量和各等级 Ac/Re */
    @PostMapping("/sampling-plans/preview")
    @PreAuthorize(READ)
    public CommonResult<SamplingResult> preview(@Valid @RequestBody SamplingPreviewReq req) {
        return CommonResult.success(samplingService.preview(req));
    }

    // ==================== 缺陷代码 ====================

    @GetMapping("/defect-codes")
    @PreAuthorize("@ss.has('qc:standard:query')")
    public CommonResult<PageResult<DefectCodeRow>> defects(@Valid DefectCodeQuery q) {
        return CommonResult.success(basicDataService.pageDefects(q));
    }

    @GetMapping("/defect-codes/enabled")
    @PreAuthorize(READ)
    public CommonResult<List<DefectCodeRow>> enabledDefects() {
        return CommonResult.success(basicDataService.listDefects());
    }

    @PostMapping("/defect-codes")
    @PreAuthorize("@ss.has('qc:defect-code:manage')")
    public CommonResult<Long> createDefect(@Valid @RequestBody DefectCodeSave req) {
        return CommonResult.success(basicDataService.createDefect(req));
    }

    @PutMapping("/defect-codes/{id}")
    @PreAuthorize("@ss.has('qc:defect-code:manage')")
    public CommonResult<Void> updateDefect(@PathVariable Long id, @Valid @RequestBody DefectCodeSave req) {
        basicDataService.updateDefect(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/defect-codes/{id}")
    @PreAuthorize("@ss.has('qc:defect-code:manage')")
    public CommonResult<Void> deleteDefect(@PathVariable Long id) {
        basicDataService.deleteDefect(id);
        return CommonResult.success();
    }
}
