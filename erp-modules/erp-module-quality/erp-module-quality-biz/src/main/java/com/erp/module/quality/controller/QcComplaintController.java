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
import org.springframework.web.bind.annotation.RestController;

import com.erp.module.quality.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintDetail;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintQuery;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintRow;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintSave;
import com.erp.module.quality.controller.vo.ComplaintVOs.HandlingReq;
import com.erp.module.quality.controller.vo.ComplaintVOs.NcrReq;
import com.erp.module.quality.controller.vo.ComplaintVOs.ReplyReq;
import com.erp.module.quality.service.complaint.ComplaintService;

@Tag(name = "品质 - 客诉")
@RestController
@RequestMapping("/api/quality/complaints")
public class QcComplaintController {

    private final ComplaintService service;

    public QcComplaintController(ComplaintService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('qc:complaint:query')")
    public CommonResult<PageResult<ComplaintRow>> page(@Valid ComplaintQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('qc:complaint:query')")
    public CommonResult<ComplaintDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('qc:complaint:create')")
    public CommonResult<Long> create(@Valid @RequestBody ComplaintSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('qc:complaint:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ComplaintSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@ss.has('qc:complaint:update')")
    public CommonResult<Void> start(@PathVariable Long id) {
        service.start(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("@ss.has('qc:complaint:reply')")
    public CommonResult<Void> reply(@PathVariable Long id, @Valid @RequestBody ReplyReq req) {
        service.reply(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/handling")
    @PreAuthorize("@ss.has('qc:complaint:update')")
    public CommonResult<Void> handling(@PathVariable Long id, @Valid @RequestBody HandlingReq req) {
        service.handling(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/create-ncr")
    @PreAuthorize("@ss.has('qc:ncr:create')")
    public CommonResult<Long> createNcr(@PathVariable Long id, @RequestBody(required = false) NcrReq req) {
        return CommonResult.success(service.createNcr(id, req));
    }

    @PostMapping("/{id}/create-capa")
    @PreAuthorize("@ss.has('qc:capa:create')")
    public CommonResult<Long> createCapa(@PathVariable Long id) {
        return CommonResult.success(service.createCapa(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('qc:complaint:close')")
    public CommonResult<Void> close(@PathVariable Long id) {
        service.close(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.has('qc:complaint:close')")
    public CommonResult<Void> cancel(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.cancel(id, req.reason());
        return CommonResult.success();
    }
}
