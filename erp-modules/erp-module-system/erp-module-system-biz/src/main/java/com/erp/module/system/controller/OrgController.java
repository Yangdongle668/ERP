package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.OrgVOs.OrgNode;
import com.erp.module.system.controller.vo.OrgVOs.OrgResp;
import com.erp.module.system.controller.vo.OrgVOs.OrgSave;
import com.erp.module.system.controller.vo.OrgVOs.SimpleNode;
import com.erp.module.system.service.OrgService;
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

/** 组织架构（01-01） */
@Tag(name = "系统管理 - 组织架构")
@RestController
@RequestMapping("/api/system/orgs")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/tree")
    @PreAuthorize("@ss.has('system:org:query')")
    public CommonResult<List<OrgNode>> tree(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return CommonResult.success(orgService.tree(keyword, status));
    }

    /** 仅启用节点的精简树，登录即可（OrgTreeSelect） */
    @GetMapping("/simple-tree")
    public CommonResult<List<SimpleNode>> simpleTree() {
        return CommonResult.success(orgService.simpleTree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('system:org:query')")
    public CommonResult<OrgResp> get(@PathVariable Long id) {
        return CommonResult.success(orgService.getDetail(id));
    }

    @OperLog("新建组织")
    @PostMapping
    @PreAuthorize("@ss.has('system:org:create')")
    public CommonResult<Long> create(@Valid @RequestBody OrgSave req) {
        return CommonResult.success(orgService.create(req));
    }

    @OperLog("修改组织")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('system:org:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody OrgSave req) {
        orgService.update(id, req);
        return CommonResult.success();
    }

    @OperLog("启用组织")
    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('system:org:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        orgService.enable(id);
        return CommonResult.success();
    }

    @OperLog("停用组织")
    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('system:org:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        orgService.disable(id);
        return CommonResult.success();
    }

    @OperLog("删除组织")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('system:org:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return CommonResult.success();
    }
}
