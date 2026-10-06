package com.erp.module.asset.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.asset.controller.vo.AssetVOs.AssetQuery;
import com.erp.module.asset.controller.vo.AssetVOs.AssetRow;
import com.erp.module.asset.controller.vo.AssetVOs.AssetSave;
import com.erp.module.asset.controller.vo.AssetVOs.CodePreview;
import com.erp.module.asset.controller.vo.AssetVOs.ScrapReq;
import com.erp.module.asset.controller.vo.AssetVOs.StatusReq;
import com.erp.module.asset.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/** 固定资产台账（需求 15-固定资产） */
@Tag(name = "固定资产")
@RestController
@RequestMapping("/api/asset/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('ast:asset:query')")
    public CommonResult<PageResult<AssetRow>> page(@Valid AssetQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('ast:asset:query')")
    public CommonResult<AssetRow> get(@PathVariable Long id) {
        return CommonResult.success(service.get(id));
    }

    @Operation(summary = "编码预览（不占用流水号）")
    @GetMapping("/code-preview")
    @PreAuthorize("@ss.hasAny('ast:asset:create', 'ast:asset:update')")
    public CommonResult<CodePreview> preview(@RequestParam(required = false) String companyNo, @RequestParam(required = false) String assetClass,
                                             @RequestParam(required = false) String nameAbbr,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate) {
        return CommonResult.success(service.preview(companyNo, assetClass, nameAbbr, purchaseDate));
    }

    @PostMapping
    @PreAuthorize("@ss.has('ast:asset:create')")
    public CommonResult<Long> create(@Valid @RequestBody AssetSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('ast:asset:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody AssetSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @Operation(summary = "状态变更：闲置 / 启用 / 送修 / 修复")
    @PostMapping("/{id}/status")
    @PreAuthorize("@ss.has('ast:asset:update')")
    public CommonResult<Void> status(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        service.changeStatus(id, req.op(), req.reason());
        return CommonResult.success();
    }

    @PostMapping("/{id}/scrap")
    @PreAuthorize("@ss.has('ast:asset:scrap')")
    public CommonResult<Void> scrap(@PathVariable Long id, @Valid @RequestBody ScrapReq req) {
        service.scrap(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('ast:asset:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }
}
