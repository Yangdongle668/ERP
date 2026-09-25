package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
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
import com.erp.module.purchase.controller.vo.CommonVOs.IdsReq;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.ScoreVOs.CalculateReq;
import com.erp.module.purchase.controller.vo.ScoreVOs.CalculateResult;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreDetail;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreQuery;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreRow;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreSave;
import com.erp.module.purchase.controller.vo.ScoreVOs.TrendPoint;
import com.erp.module.purchase.service.score.ScoreService;

import java.util.List;

/** 供应商评估（需求 07-10 第 6 节） */
@Tag(name = "资材 - 供应商评估")
@RestController
@RequestMapping("/api/purchase/scores")
public class ScoreController {

    private final ScoreService service;

    public ScoreController(ScoreService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pur:score:query')")
    public CommonResult<PageResult<ScoreRow>> page(@Valid ScoreQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "计算评估期的质量和交期得分（已发布的不重算）")
    @PostMapping("/calculate")
    @PreAuthorize("@ss.has('pur:score:calculate')")
    public CommonResult<CalculateResult> calculate(@Valid @RequestBody CalculateReq req) {
        return CommonResult.success(service.calculate(req.period()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pur:score:update')")
    public CommonResult<ScoreRow> update(@PathVariable Long id, @Valid @RequestBody ScoreSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.has('pur:score:calculate')")
    public CommonResult<Integer> publish(@RequestBody IdsReq req) {
        return CommonResult.success(service.publish(req.ids()));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@ss.has('pur:score:update')")
    public CommonResult<Void> unpublish(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unpublish(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("@ss.has('pur:score:query')")
    public CommonResult<ScoreDetail> details(@PathVariable Long id) {
        return CommonResult.success(service.details(id));
    }

    @GetMapping("/trend")
    @PreAuthorize("@ss.has('pur:score:query')")
    public CommonResult<List<TrendPoint>> trend(@RequestParam Long supplierId) {
        return CommonResult.success(service.trend(supplierId));
    }
}
