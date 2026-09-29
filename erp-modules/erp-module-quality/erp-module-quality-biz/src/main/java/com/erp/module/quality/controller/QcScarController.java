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
import com.erp.module.quality.controller.vo.ScarVOs.ReplyReq;
import com.erp.module.quality.controller.vo.ScarVOs.ScarDetail;
import com.erp.module.quality.controller.vo.ScarVOs.ScarQuery;
import com.erp.module.quality.controller.vo.ScarVOs.ScarRow;
import com.erp.module.quality.controller.vo.ScarVOs.ScarSave;
import com.erp.module.quality.controller.vo.ScarVOs.VerifyPlanReq;
import com.erp.module.quality.controller.vo.ScarVOs.VerifyReq;
import com.erp.module.quality.service.scar.ScarService;

@Tag(name = "品质 - SCAR")
@RestController
@RequestMapping("/api/quality/scars")
public class QcScarController {

    private final ScarService service;

    public QcScarController(ScarService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('qc:scar:query')")
    public CommonResult<PageResult<ScarRow>> page(@Valid ScarQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('qc:scar:query')")
    public CommonResult<ScarDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('qc:scar:create')")
    public CommonResult<Long> create(@Valid @RequestBody ScarSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('qc:scar:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ScarSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("@ss.has('qc:scar:send')")
    public CommonResult<Void> send(@PathVariable Long id) {
        service.send(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("@ss.has('qc:scar:update')")
    public CommonResult<Void> reply(@PathVariable Long id, @Valid @RequestBody ReplyReq req) {
        service.reply(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/start-verify")
    @PreAuthorize("@ss.has('qc:scar:verify')")
    public CommonResult<Void> startVerify(@PathVariable Long id, @Valid @RequestBody VerifyPlanReq req) {
        service.startVerify(id, req.verifyPlan());
        return CommonResult.success();
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("@ss.has('qc:scar:verify')")
    public CommonResult<Void> verify(@PathVariable Long id, @Valid @RequestBody VerifyReq req) {
        service.verify(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.has('qc:scar:close')")
    public CommonResult<Void> cancel(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.cancel(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('qc:scar:query')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String lang) {
        return CommonResult.success(service.printData(id));
    }
}
