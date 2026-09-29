package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.pmc.controller.vo.MpsVOs.CapRow;
import com.erp.module.pmc.controller.vo.MpsVOs.GenerateReq;
import com.erp.module.pmc.controller.vo.MpsVOs.Matrix;
import com.erp.module.pmc.controller.vo.MpsVOs.MatrixSave;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsDetail;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsQuery;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsRow;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsSave;
import com.erp.module.pmc.service.mps.MpsService;
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

@Tag(name = "PMC - MPS")
@RestController
@RequestMapping("/api/pmc/mps")
public class PmcMpsController {

    private final MpsService service;

    public PmcMpsController(MpsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pmc:mps:query')")
    public CommonResult<PageResult<MpsRow>> page(@Valid MpsQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('pmc:mps:query')")
    public CommonResult<MpsDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('pmc:mps:create')")
    public CommonResult<Long> create(@Valid @RequestBody MpsSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pmc:mps:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody MpsSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('pmc:mps:update')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.has('pmc:mps:publish')")
    public CommonResult<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('pmc:mps:publish')")
    public CommonResult<Void> close(@PathVariable Long id) {
        service.close(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("@ss.has('pmc:mps:create')")
    public CommonResult<Long> copy(@PathVariable Long id) {
        return CommonResult.success(service.copy(id));
    }

    @GetMapping("/{id}/matrix")
    @PreAuthorize("@ss.has('pmc:mps:query')")
    public CommonResult<Matrix> matrix(@PathVariable Long id) {
        return CommonResult.success(service.matrix(id));
    }

    @PutMapping("/{id}/matrix")
    @PreAuthorize("@ss.has('pmc:mps:update')")
    public CommonResult<Matrix> saveMatrix(@PathVariable Long id, @RequestBody MatrixSave req) {
        service.saveMatrix(id, req.rows(), req.version());
        return CommonResult.success(service.matrix(id));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("@ss.has('pmc:mps:update')")
    public CommonResult<Matrix> generate(@PathVariable Long id, @RequestBody(required = false) GenerateReq req) {
        service.generate(id, req == null ? null : req.materialIds());
        return CommonResult.success(service.matrix(id));
    }

    @PostMapping("/{id}/capacity-check")
    @PreAuthorize("@ss.has('pmc:mps:query')")
    public CommonResult<List<CapRow>> capacityCheck(@PathVariable Long id) {
        return CommonResult.success(service.capacityCheck(id));
    }
}
