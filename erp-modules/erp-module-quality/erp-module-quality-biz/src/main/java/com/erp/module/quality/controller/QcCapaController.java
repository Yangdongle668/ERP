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
import com.erp.module.quality.controller.vo.CapaVOs.CapaDetail;
import com.erp.module.quality.controller.vo.CapaVOs.CapaQuery;
import com.erp.module.quality.controller.vo.CapaVOs.CapaRow;
import com.erp.module.quality.controller.vo.CapaVOs.CapaSave;
import com.erp.module.quality.controller.vo.CapaVOs.StepSave;
import com.erp.module.quality.controller.vo.CapaVOs.VerifyReq;
import com.erp.module.quality.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.quality.controller.vo.CommonVOs.TextReq;
import com.erp.module.quality.service.capa.CapaService;

@Tag(name = "品质 - CAPA / 8D")
@RestController
@RequestMapping("/api/quality/capas")
public class QcCapaController {

    private final CapaService service;

    public QcCapaController(CapaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('qc:capa:query')")
    public CommonResult<PageResult<CapaRow>> page(@Valid CapaQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('qc:capa:query')")
    public CommonResult<CapaDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('qc:capa:create')")
    public CommonResult<Long> create(@Valid @RequestBody CapaSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('qc:capa:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody CapaSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PutMapping("/{id}/steps/{step}")
    @PreAuthorize("@ss.hasAny('qc:capa:update','qc:capa:verify')")
    public CommonResult<Void> saveStep(@PathVariable Long id, @PathVariable int step, @Valid @RequestBody StepSave req) {
        service.saveStep(id, step, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/steps/{step}/complete")
    @PreAuthorize("@ss.has('qc:capa:update')")
    public CommonResult<Void> completeStep(@PathVariable Long id, @PathVariable int step, @Valid @RequestBody(required = false) StepSave req) {
        service.completeStep(id, step, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("@ss.has('qc:capa:verify')")
    public CommonResult<Void> verify(@PathVariable Long id, @Valid @RequestBody VerifyReq req) {
        service.verify(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('qc:capa:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody(required = false) TextReq req) {
        service.close(id, req == null ? null : req.text());
        return CommonResult.success();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.has('qc:capa:close')")
    public CommonResult<Void> cancel(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.cancel(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('qc:capa:query')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String lang) {
        return CommonResult.success(service.printData(id));
    }
}
