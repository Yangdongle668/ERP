package com.erp.module.quality.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.controller.vo.BasicVOs.StandardDetail;
import com.erp.module.quality.controller.vo.BasicVOs.StandardQuery;
import com.erp.module.quality.controller.vo.BasicVOs.StandardRow;
import com.erp.module.quality.controller.vo.BasicVOs.StandardSave;
import com.erp.module.quality.dal.dataobject.QcStandardDO;
import com.erp.module.quality.service.basic.StandardService;
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

@Tag(name = "品质 - 检验标准")
@RestController
@RequestMapping("/api/quality/standards")
public class QcStandardController {

    private final StandardService service;

    public QcStandardController(StandardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('qc:standard:query')")
    public CommonResult<PageResult<StandardRow>> page(@Valid StandardQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('qc:standard:query')")
    public CommonResult<StandardDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    /** 匹配生效标准（物料 → 类别逐级 → 通用），没有时返回空 */
    @GetMapping("/match")
    @PreAuthorize("@ss.has('qc:standard:query')")
    public CommonResult<StandardDetail> match(@RequestParam Long materialId, @RequestParam String inspectType, @RequestParam(required = false) String operation) {
        QcStandardDO s = service.match(materialId, InspectType.valueOf(inspectType), operation);
        return CommonResult.success(s == null ? null : service.detail(s.getId()));
    }

    @PostMapping
    @PreAuthorize("@ss.has('qc:standard:create')")
    public CommonResult<Long> create(@Valid @RequestBody StandardSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('qc:standard:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody StandardSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('qc:standard:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/new-version")
    @PreAuthorize("@ss.has('qc:standard:update')")
    public CommonResult<Long> newVersion(@PathVariable Long id) {
        return CommonResult.success(service.newVersion(id));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@ss.has('qc:standard:approve')")
    public CommonResult<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/obsolete")
    @PreAuthorize("@ss.has('qc:standard:delete')")
    public CommonResult<Void> obsolete(@PathVariable Long id) {
        service.obsolete(id);
        return CommonResult.success();
    }
}
