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
    private final com.erp.module.system.service.file.FileService fileService;

    public OrgController(OrgService orgService, com.erp.module.system.service.file.FileService fileService) {
        this.orgService = orgService;
        this.fileService = fileService;
    }

    /** 上传公司 Logo（PNG / SVG / JPG，≤ 512KB）：系统左上角、登录页、打印单据抬头使用；返回文件 ID */
    @PostMapping(value = "/{id}/logo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('system:org:update')")
    public CommonResult<Long> uploadLogo(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestPart("file")
                                         org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return CommonResult.success(orgService.uploadLogo(id, file.getOriginalFilename(), file.getBytes(), fileService::saveGenerated));
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("@ss.has('system:org:update')")
    public CommonResult<Void> removeLogo(@PathVariable Long id) {
        orgService.removeLogo(id);
        return CommonResult.success();
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
