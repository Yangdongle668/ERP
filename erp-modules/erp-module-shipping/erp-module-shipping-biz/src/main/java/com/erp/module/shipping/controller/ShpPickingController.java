package com.erp.module.shipping.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonSave;
import com.erp.module.shipping.controller.vo.PickingVOs.BatchOption;
import com.erp.module.shipping.controller.vo.PickingVOs.CompleteReq;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingDetail;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingQuery;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingRow;
import com.erp.module.shipping.controller.vo.PickingVOs.SaveLinesReq;
import com.erp.module.shipping.service.picking.PackingService;
import com.erp.module.shipping.service.picking.PickingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "出货 - 拣货 / 箱")
@RestController
public class ShpPickingController {

    private final PickingService service;
    private final PackingService packingService;

    public ShpPickingController(PickingService service, PackingService packingService) {
        this.service = service;
        this.packingService = packingService;
    }

    @GetMapping("/api/shipping/pickings")
    @PreAuthorize("@ss.has('shp:picking:query')")
    public CommonResult<PageResult<PickingRow>> page(@Valid PickingQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/api/shipping/pickings/{id}")
    @PreAuthorize("@ss.has('shp:picking:query')")
    public CommonResult<PickingDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/api/shipping/pickings/{id}/start")
    @PreAuthorize("@ss.has('shp:picking:pick')")
    public CommonResult<Void> start(@PathVariable Long id) {
        service.start(id);
        return CommonResult.success();
    }

    @PutMapping("/api/shipping/pickings/{id}/lines")
    @PreAuthorize("@ss.has('shp:picking:pick')")
    public CommonResult<Void> saveLines(@PathVariable Long id, @Valid @RequestBody SaveLinesReq req) {
        service.saveLines(id, req.lines());
        return CommonResult.success();
    }

    @PostMapping("/api/shipping/pickings/{id}/complete")
    @PreAuthorize("@ss.has('shp:picking:pick')")
    public CommonResult<Void> complete(@PathVariable Long id, @RequestBody(required = false) CompleteReq req) {
        service.complete(id, req != null && req.acceptShort());
        return CommonResult.success();
    }

    @GetMapping("/api/shipping/pickings/{id}/batches")
    @PreAuthorize("@ss.has('shp:picking:pick')")
    public CommonResult<List<BatchOption>> batches(@PathVariable Long id, @RequestParam Long noticeLineId) {
        return CommonResult.success(service.batchOptions(id, noticeLineId));
    }

    @GetMapping("/api/shipping/pickings/{id}/print-data")
    @PreAuthorize("@ss.has('shp:picking:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    // ==================== 箱 ====================

    @PutMapping("/api/shipping/cartons/{id}")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<Void> updateCarton(@PathVariable Long id, @Valid @RequestBody CartonSave req) {
        packingService.updateCarton(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/api/shipping/cartons/{id}")
    @PreAuthorize("@ss.has('shp:packing:pack')")
    public CommonResult<Void> deleteCarton(@PathVariable Long id) {
        packingService.deleteCarton(id);
        return CommonResult.success();
    }
}
