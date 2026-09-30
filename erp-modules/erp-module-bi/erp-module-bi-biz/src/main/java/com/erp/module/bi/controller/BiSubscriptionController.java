package com.erp.module.bi.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.subscription.BiSubscriptionService;
import com.erp.module.bi.service.subscription.BiSubscriptionService.Report;
import com.erp.module.bi.service.subscription.BiSubscriptionService.Save;
import com.erp.module.bi.service.subscription.BiSubscriptionService.View;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 报表订阅（需求 13-03）：只能维护自己的订阅，按订阅人的权限与数据范围生成 */
@Tag(name = "BI - 报表订阅")
@RestController
@RequestMapping("/api/bi/subscriptions")
public class BiSubscriptionController {

    public record Option(String code, String label) {
    }

    private final BiSubscriptionService service;

    public BiSubscriptionController(BiSubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('bi:subscription:manage')")
    public CommonResult<List<View>> list() {
        return CommonResult.success(service.list());
    }

    /** 可选的分组维度（不含日期） */
    @GetMapping("/dimensions")
    @PreAuthorize("@ss.has('bi:subscription:manage')")
    public CommonResult<List<Option>> dimensions() {
        return CommonResult.success(MetricRegistry.DIMENSIONS.entrySet().stream().filter(e -> !"date".equals(e.getKey()))
                .map(e -> new Option(e.getKey(), e.getValue())).toList());
    }

    @PostMapping
    @PreAuthorize("@ss.has('bi:subscription:manage')")
    public CommonResult<Map<String, Long>> create(@RequestBody Save req) {
        return CommonResult.success(Map.of("id", service.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('bi:subscription:manage')")
    public CommonResult<Void> update(@PathVariable Long id, @RequestBody Save req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('bi:subscription:manage')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    /** 立即发送一次（消息推送到工作台，勾选邮件则同时发邮件），返回报表内容供预览 */
    @PostMapping("/{id}/send")
    @PreAuthorize("@ss.has('bi:subscription:manage')")
    public CommonResult<Report> send(@PathVariable Long id) {
        return CommonResult.success(service.sendNow(id));
    }
}
