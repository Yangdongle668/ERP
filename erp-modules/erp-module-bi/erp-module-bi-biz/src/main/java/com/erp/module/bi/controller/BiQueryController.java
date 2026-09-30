package com.erp.module.bi.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.bi.service.dashboard.BiDashboardService;
import com.erp.module.bi.service.dashboard.BiDashboardService.Compare;
import com.erp.module.bi.service.dashboard.BiDashboardService.Dashboard;
import com.erp.module.bi.service.dashboard.BiDashboardService.Period;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 通用查询（指标权限在服务内按指标校验，数据范围按当前用户）与经营驾驶舱 */
@Tag(name = "BI - 查询与驾驶舱")
@RestController
@RequestMapping("/api/bi")
public class BiQueryController {

    private final BiQueryService queryService;
    private final BiDashboardService dashboardService;

    public BiQueryController(BiQueryService queryService, BiDashboardService dashboardService) {
        this.queryService = queryService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/query")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<BiQueryResult> query(@RequestBody BiQuery q) {
        return CommonResult.success(queryService.query(q));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@ss.has('bi:dashboard:view')")
    public CommonResult<Dashboard> dashboard(@RequestParam(required = false) Period period,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Compare compare) {
        return CommonResult.success(dashboardService.dashboard(period, from, to, compare));
    }
}
