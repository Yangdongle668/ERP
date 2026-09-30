package com.erp.module.bi.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.controller.vo.BiVOs.DimensionVO;
import com.erp.module.bi.controller.vo.BiVOs.MetricUpdate;
import com.erp.module.bi.controller.vo.BiVOs.MetricVO;
import com.erp.module.bi.controller.vo.BiVOs.PageConfig;
import com.erp.module.bi.dal.dataobject.BiEtlJobDO;
import com.erp.module.bi.dal.dataobject.BiEtlLogDO;
import com.erp.module.bi.service.etl.BiEtlService;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.BiMetricService.MetricInfo;
import com.erp.module.bi.service.metric.MetricDefinition;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.common.exception.BizException;
import com.erp.module.bi.api.BiErrorCodes;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 指标库、数据任务、专题页配置（需求 13-01 第 6 节、13-03 第 9 节） */
@Tag(name = "BI - 指标库与数据任务")
@RestController
@RequestMapping("/api/bi")
public class BiMetricController {

    static final Map<String, String> TOPICS = Map.of("sales", "销售分析", "purchase", "采购分析", "inventory", "库存分析", "production", "生产分析",
            "quality", "品质分析", "finance", "财务分析");

    private final BiMetricService metricService;
    private final BiEtlService etlService;

    public BiMetricController(BiMetricService metricService, BiEtlService etlService) {
        this.metricService = metricService;
        this.etlService = etlService;
    }

    @GetMapping("/metrics")
    @PreAuthorize("@ss.has('bi:metric:manage')")
    public CommonResult<List<MetricVO>> metrics() {
        LocalDateTime at = etlService.lastSuccessAt();
        return CommonResult.success(metricService.list().stream().map(m -> vo(m, at)).toList());
    }

    /** 当前用户有权限的指标（专题页、AI 问数使用） */
    @GetMapping("/metrics/visible")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<MetricVO>> visibleMetrics() {
        LocalDateTime at = etlService.lastSuccessAt();
        return CommonResult.success(metricService.listVisible(SecurityUtils.getLoginUser()).stream().map(m -> vo(m, at)).toList());
    }

    @PutMapping("/metrics/{code}")
    @PreAuthorize("@ss.has('bi:metric:manage')")
    public CommonResult<MetricVO> update(@PathVariable String code, @Valid @RequestBody MetricUpdate req) {
        return CommonResult.success(vo(metricService.update(code, req.displayName(), req.description(), req.ownerName()), etlService.lastSuccessAt()));
    }

    @GetMapping("/pages/{code}/config")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageConfig> pageConfig(@PathVariable String code) {
        String name = TOPICS.get(code);
        if (name == null) throw BizException.of(BiErrorCodes.NOT_EXISTS, "专题页");
        LocalDateTime at = etlService.lastSuccessAt();
        List<MetricVO> metrics = metricService.listVisible(SecurityUtils.getLoginUser()).stream().filter(m -> code.equals(m.def().topic()))
                .map(m -> vo(m, at)).toList();
        Set<String> dims = new LinkedHashSet<>();
        metrics.forEach(m -> m.dimensions().forEach(d -> dims.add(d.code())));
        List<DimensionVO> dimVos = new ArrayList<>();
        MetricRegistry.DIMENSIONS.forEach((k, v) -> {
            if (dims.contains(k)) dimVos.add(new DimensionVO(k, v));
        });
        return CommonResult.success(new PageConfig(code, name, metrics, dimVos, at));
    }

    @GetMapping("/etl/jobs")
    @PreAuthorize("@ss.has('bi:metric:manage')")
    public CommonResult<List<BiEtlJobDO>> jobs() {
        return CommonResult.success(etlService.listJobs());
    }

    @GetMapping("/etl/logs")
    @PreAuthorize("@ss.has('bi:metric:manage')")
    public CommonResult<List<BiEtlLogDO>> logs(@RequestParam(required = false) String jobCode, @RequestParam(defaultValue = "50") int limit) {
        return CommonResult.success(etlService.listLogs(jobCode, limit));
    }

    @PostMapping("/etl/jobs/{code}/run")
    @PreAuthorize("@ss.has('bi:metric:manage')")
    public CommonResult<BiEtlJobDO> run(@PathVariable String code) {
        return CommonResult.success(etlService.runNow(code));
    }

    static MetricVO vo(MetricInfo m, LocalDateTime at) {
        MetricDefinition d = m.def();
        List<DimensionVO> dims = d.dimColumns().keySet().stream().map(k -> new DimensionVO(k, MetricRegistry.DIMENSIONS.getOrDefault(k, k))).toList();
        return new MetricVO(d.code(), m.name(), d.name(), d.topic(), d.unit().name(), d.description(), d.source().label, dims, d.permission(),
                d.sensitive(), d.isDerived(), m.remark(), m.ownerName(), at);
    }
}
