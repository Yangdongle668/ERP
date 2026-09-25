package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
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
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.BomPreview;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsDetail;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsQuery;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsRow;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsSave;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.QtyReq;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.SettleReq;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.StockDocResult;
import com.erp.module.purchase.service.outsourcing.OutsourcingService;

import java.math.BigDecimal;
import java.util.Map;

/** 委外加工（需求 07-07 第 5 节） */
@Tag(name = "资材 - 委外加工")
@RestController
@RequestMapping("/api/purchase/outsourcings")
public class OutsourcingController {

    private final OutsourcingService service;

    public OutsourcingController(OutsourcingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pur:outsourcing:query')")
    public CommonResult<PageResult<OsRow>> page(@Valid OsQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "选择加工物料后带出默认 BOM 与用料")
    @GetMapping("/bom-preview")
    @PreAuthorize("@ss.hasAny('pur:outsourcing:create', 'pur:outsourcing:update')")
    public CommonResult<BomPreview> preview(@RequestParam Long materialId, @RequestParam(required = false) Long bomId,
                                            @RequestParam(required = false) BigDecimal qty) {
        return CommonResult.success(service.preview(materialId, bomId, qty));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('pur:outsourcing:query')")
    public CommonResult<OsDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('pur:outsourcing:create')")
    public CommonResult<Long> create(@Valid @RequestBody OsSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pur:outsourcing:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody OsSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('pur:outsourcing:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('pur:outsourcing:delete')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('pur:outsourcing:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/{id}/unapprove")
    @PreAuthorize("@ss.has('pur:outsourcing:submit')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unapprove(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('pur:outsourcing:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.close(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @Operation(summary = "发料：生成委外发料出库单（R02）")
    @PostMapping("/{id}/issue")
    @PreAuthorize("@ss.has('pur:outsourcing:issue')")
    public CommonResult<StockDocResult> issue(@PathVariable Long id, @Valid @RequestBody QtyReq req) {
        return CommonResult.success(new StockDocResult(service.issue(id, req.lines())));
    }

    @Operation(summary = "余料退回：生成委外退料入库单")
    @PostMapping("/{id}/return-material")
    @PreAuthorize("@ss.has('pur:outsourcing:issue')")
    public CommonResult<StockDocResult> returnMaterial(@PathVariable Long id, @Valid @RequestBody QtyReq req) {
        return CommonResult.success(new StockDocResult(service.returnMaterial(id, req.lines())));
    }

    @Operation(summary = "核销（R05）：超耗行必须填写原因")
    @PostMapping("/{id}/settle")
    @PreAuthorize("@ss.has('pur:outsourcing:receive')")
    public CommonResult<Void> settle(@PathVariable Long id, @Valid @RequestBody(required = false) SettleReq req) {
        service.settle(id, req == null ? null : req.lines());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('pur:outsourcing:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
