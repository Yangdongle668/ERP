package com.erp.module.bi.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.bi.service.forecast.BiForecastService;
import com.erp.module.bi.service.forecast.BiForecastService.Generated;
import com.erp.module.bi.service.forecast.BiForecastService.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 销售预测建议（需求 13-04 2.4） */
@Tag(name = "BI - 销售预测建议")
@RestController
@RequestMapping("/api/bi/forecast")
public class BiForecastController {

    public record GenerateReq(List<Long> materialIds, Integer months) {
    }

    private final BiForecastService service;

    public BiForecastController(BiForecastService service) {
        this.service = service;
    }

    @GetMapping("/suggestions")
    @PreAuthorize("@ss.has('bi:forecast:use')")
    public CommonResult<Result> suggestions(@RequestParam(defaultValue = "3") int months) {
        return CommonResult.success(service.suggestions(months));
    }

    @PostMapping("/generate")
    @PreAuthorize("@ss.has('bi:forecast:use')")
    public CommonResult<Generated> generate(@RequestBody GenerateReq req) {
        return CommonResult.success(service.generate(req.materialIds(), req.months() == null ? 3 : req.months()));
    }
}
