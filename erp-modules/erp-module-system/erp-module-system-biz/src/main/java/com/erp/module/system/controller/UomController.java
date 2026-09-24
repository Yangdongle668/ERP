package com.erp.module.system.controller;

import com.erp.common.enums.EnableStatus;
import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.UomVOs.ConversionResp;
import com.erp.module.system.controller.vo.UomVOs.ConversionSave;
import com.erp.module.system.controller.vo.UomVOs.UomResp;
import com.erp.module.system.controller.vo.UomVOs.UomSave;
import com.erp.module.system.controller.vo.UomVOs.UomSimple;
import com.erp.module.system.service.UomService;
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

import java.util.List;

/** 计量单位与通用换算（01-06） */
@Tag(name = "系统管理 - 计量单位")
@RestController
@RequestMapping("/api/system")
public class UomController {

    private final UomService uomService;

    public UomController(UomService uomService) {
        this.uomService = uomService;
    }

    @GetMapping("/uoms")
    @PreAuthorize("@ss.has('system:uom:query')")
    public CommonResult<List<UomResp>> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String status) {
        return CommonResult.success(uomService.list(keyword, category, status));
    }

    /** 启用单位，登录即可（UomSelect） */
    @GetMapping("/uoms/simple")
    public CommonResult<List<UomSimple>> simple() {
        return CommonResult.success(uomService.simple());
    }

    @OperLog("新建计量单位")
    @PostMapping("/uoms")
    @PreAuthorize("@ss.has('system:uom:create')")
    public CommonResult<Long> create(@Valid @RequestBody UomSave req) {
        return CommonResult.success(uomService.create(req));
    }

    @OperLog("修改计量单位")
    @PutMapping("/uoms/{id}")
    @PreAuthorize("@ss.has('system:uom:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody UomSave req) {
        uomService.update(id, req);
        return CommonResult.success();
    }

    @OperLog("启用计量单位")
    @PostMapping("/uoms/{id}/enable")
    @PreAuthorize("@ss.has('system:uom:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        uomService.changeStatus(id, EnableStatus.ENABLED);
        return CommonResult.success();
    }

    @OperLog("停用计量单位")
    @PostMapping("/uoms/{id}/disable")
    @PreAuthorize("@ss.has('system:uom:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        uomService.changeStatus(id, EnableStatus.DISABLED);
        return CommonResult.success();
    }

    @OperLog("删除计量单位")
    @DeleteMapping("/uoms/{id}")
    @PreAuthorize("@ss.has('system:uom:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        uomService.delete(id);
        return CommonResult.success();
    }

    @GetMapping("/uom-conversions")
    @PreAuthorize("@ss.has('system:uom:query')")
    public CommonResult<List<ConversionResp>> conversions() {
        return CommonResult.success(uomService.conversions());
    }

    @OperLog("新建单位换算")
    @PostMapping("/uom-conversions")
    @PreAuthorize("@ss.has('system:uom:update')")
    public CommonResult<Long> createConversion(@Valid @RequestBody ConversionSave req) {
        return CommonResult.success(uomService.createConversion(req));
    }

    @OperLog("修改单位换算")
    @PutMapping("/uom-conversions/{id}")
    @PreAuthorize("@ss.has('system:uom:update')")
    public CommonResult<Void> updateConversion(@PathVariable Long id, @Valid @RequestBody ConversionSave req) {
        uomService.updateConversion(id, req);
        return CommonResult.success();
    }

    @OperLog("删除单位换算")
    @DeleteMapping("/uom-conversions/{id}")
    @PreAuthorize("@ss.has('system:uom:update')")
    public CommonResult<Void> deleteConversion(@PathVariable Long id) {
        uomService.deleteConversion(id);
        return CommonResult.success();
    }
}
