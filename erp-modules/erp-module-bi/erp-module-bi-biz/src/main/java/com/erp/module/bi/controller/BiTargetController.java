package com.erp.module.bi.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.bi.service.dashboard.BiTargetService;
import com.erp.module.bi.service.metric.BiMetricService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** KPI 月度目标（需求 13-02） */
@Tag(name = "BI - KPI 目标")
@RestController
@RequestMapping("/api/bi/kpi-targets")
public class BiTargetController {

    /** @param values 12 个月的目标（缺省为空） */
    public record Row(String metricCode, String metricName, List<BigDecimal> values) {
    }

    public record Year(int year, List<Row> rows) {
    }

    public record SaveReq(int year, List<BiTargetService.Item> items) {
    }

    private final BiTargetService service;
    private final BiMetricService metricService;

    public BiTargetController(BiTargetService service, BiMetricService metricService) {
        this.service = service;
        this.metricService = metricService;
    }

    @GetMapping
    @PreAuthorize("@ss.has('bi:target:manage')")
    public CommonResult<Year> year(@RequestParam int year) {
        Map<String, BigDecimal[]> data = service.year(year);
        Map<String, String> names = metricService.names();
        List<Row> rows = new ArrayList<>();
        for (String code : BiTargetService.TARGETABLE) {
            rows.add(new Row(code, names.getOrDefault(code, code), java.util.Arrays.asList(data.get(code))));
        }
        return CommonResult.success(new Year(year, rows));
    }

    @PutMapping
    @PreAuthorize("@ss.has('bi:target:manage')")
    public CommonResult<Void> save(@RequestBody SaveReq req) {
        service.save(req.year(), req.items());
        return CommonResult.success();
    }
}
