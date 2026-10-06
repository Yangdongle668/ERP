package com.erp.module.fx.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.fx.controller.vo.FxVOs.DailyRow;
import com.erp.module.fx.controller.vo.FxVOs.FxStatus;
import com.erp.module.fx.controller.vo.FxVOs.MonthlyRow;
import com.erp.module.fx.service.FxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 实时汇率（需求 16-实时汇率） */
@Tag(name = "实时汇率")
@RestController
@RequestMapping("/api/fx")
public class FxController {

    private final FxService service;

    public FxController(FxService service) {
        this.service = service;
    }

    @Operation(summary = "最新汇率与轮询状态")
    @GetMapping("/status")
    @PreAuthorize("@ss.has('fx:rate:query')")
    public CommonResult<FxStatus> status() {
        return CommonResult.success(service.status());
    }

    @Operation(summary = "立即获取（忽略 15 分钟缓存）")
    @PostMapping("/refresh")
    @PreAuthorize("@ss.has('fx:rate:refresh')")
    public CommonResult<FxStatus> refresh() {
        service.refresh(true);
        return CommonResult.success(service.status());
    }

    @Operation(summary = "日平均汇率")
    @GetMapping("/daily")
    @PreAuthorize("@ss.has('fx:rate:query')")
    public CommonResult<List<DailyRow>> daily(@RequestParam(required = false) String pair,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isBefore(end.minusYears(1))) start = end.minusYears(1);
        return CommonResult.success(service.daily(pair == null || pair.isBlank() ? null : pair, start, end));
    }

    @Operation(summary = "月平均汇率")
    @GetMapping("/monthly")
    @PreAuthorize("@ss.has('fx:rate:query')")
    public CommonResult<List<MonthlyRow>> monthly(@RequestParam(defaultValue = "36") int limit) {
        return CommonResult.success(service.monthly(limit * 3));
    }
}
