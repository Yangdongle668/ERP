package com.erp.module.crm.controller;

import com.erp.common.enums.EnableStatus;
import com.erp.common.result.CommonResult;
import com.erp.module.crm.controller.vo.AppDomainVOs.AppDomainOption;
import com.erp.module.crm.controller.vo.AppDomainVOs.AppDomainRow;
import com.erp.module.crm.controller.vo.AppDomainVOs.AppDomainSave;
import com.erp.module.crm.service.AppDomainService;
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

import java.util.List;

/** 应用领域（客户编码 LD-领域字母-流水号） */
@Tag(name = "CRM - 应用领域")
@RestController
@RequestMapping("/api/crm")
public class AppDomainController {

    private final AppDomainService service;

    public AppDomainController(AppDomainService service) {
        this.service = service;
    }

    @Operation(summary = "应用领域列表（含客户数、下一个客户编码）")
    @GetMapping("/app-domains")
    @PreAuthorize("@ss.has('crm:app-domain:query')")
    public CommonResult<List<AppDomainRow>> list() {
        return CommonResult.success(service.list());
    }

    @Operation(summary = "下拉选项（客户新建 / 筛选用）")
    @GetMapping("/app-domains/options")
    @PreAuthorize("@ss.has('crm:customer:query') or @ss.has('crm:app-domain:query')")
    public CommonResult<List<AppDomainOption>> options() {
        return CommonResult.success(service.options());
    }

    @Operation(summary = "预览该领域下一个客户编码（不占用流水号）")
    @GetMapping("/customers/next-code")
    @PreAuthorize("@ss.has('crm:customer:create')")
    public CommonResult<String> nextCode(@RequestParam String appDomain) {
        return CommonResult.success(service.nextCode(service.requireUsable(appDomain.trim().toUpperCase()).getCode()));
    }

    @PostMapping("/app-domains")
    @PreAuthorize("@ss.has('crm:app-domain:manage')")
    public CommonResult<Long> create(@Valid @RequestBody AppDomainSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/app-domains/{id}")
    @PreAuthorize("@ss.has('crm:app-domain:manage')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody AppDomainSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/app-domains/{id}/enable")
    @PreAuthorize("@ss.has('crm:app-domain:manage')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        service.setStatus(id, EnableStatus.ENABLED);
        return CommonResult.success();
    }

    @PostMapping("/app-domains/{id}/disable")
    @PreAuthorize("@ss.has('crm:app-domain:manage')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        service.setStatus(id, EnableStatus.DISABLED);
        return CommonResult.success();
    }

    @DeleteMapping("/app-domains/{id}")
    @PreAuthorize("@ss.has('crm:app-domain:manage')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }
}
