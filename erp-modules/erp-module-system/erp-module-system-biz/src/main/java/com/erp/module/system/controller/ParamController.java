package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.controller.vo.ParamVOs.ParamChange;
import com.erp.module.system.controller.vo.ParamVOs.ParamModule;
import com.erp.module.system.controller.vo.ParamVOs.ParamResp;
import com.erp.module.system.controller.vo.ParamVOs.PublicParams;
import com.erp.module.system.service.ParamService;
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

import java.util.List;

/** 系统参数（01-10） */
@Tag(name = "系统管理 - 系统参数")
@RestController
@RequestMapping("/api/system/params")
public class ParamController {

    private final ParamService paramService;
    private final ParamApi paramApi;
    private final com.erp.module.system.service.OrgService orgService;
    private final com.erp.module.system.service.file.FileService fileService;

    public ParamController(ParamService paramService, ParamApi paramApi, com.erp.module.system.service.OrgService orgService,
                           com.erp.module.system.service.file.FileService fileService) {
        this.paramService = paramService;
        this.paramApi = paramApi;
        this.orgService = orgService;
        this.fileService = fileService;
    }

    @GetMapping("/modules")
    @PreAuthorize("@ss.has('system:param:query')")
    public CommonResult<List<ParamModule>> modules() {
        return CommonResult.success(paramService.modules());
    }

    @GetMapping
    @PreAuthorize("@ss.has('system:param:query')")
    public CommonResult<List<ParamResp>> list(@RequestParam(required = false) String module, @RequestParam(required = false) String keyword) {
        return CommonResult.success(paramService.list(module, keyword));
    }

    /** 批量保存，返回变更明细（操作日志中也记录了旧值新值） */
    @OperLog("修改系统参数")
    @PutMapping
    @PreAuthorize("@ss.has('system:param:update')")
    public CommonResult<List<String>> save(@Valid @RequestBody List<@Valid ParamChange> changes) {
        return CommonResult.success(paramService.save(changes));
    }

    @OperLog("恢复参数默认值")
    @PostMapping("/{key}/reset")
    @PreAuthorize("@ss.has('system:param:update')")
    public CommonResult<Void> reset(@PathVariable String key) {
        paramService.reset(key);
        return CommonResult.success();
    }

    /** 登录页需要的公开参数，不需要登录 */
    @GetMapping("/public")
    public CommonResult<PublicParams> publicParams() {
        Long logo = orgService.systemLogoFileId();
        return CommonResult.success(new PublicParams(paramApi.getString("sys.company.name"),
                paramApi.getInt("sys.login.captcha-after-fails"), paramApi.getInt("sys.session.idle-timeout-minutes"),
                logo == null ? null : String.valueOf(logo)));
    }

    /**
     * 系统 Logo 图片（不需要登录：登录页、左上角）。取第一个启用的顶级公司在「组织架构」中上传的 Logo；
     * SVG 上传时已过滤脚本，这里再加 CSP 头，直接打开也不会执行脚本。
     */
    @GetMapping("/public/logo")
    public org.springframework.http.ResponseEntity<byte[]> publicLogo() throws java.io.IOException {
        Long id = orgService.systemLogoFileId();
        com.erp.module.system.dal.dataobject.FileDO f = fileService.findById(id);
        if (f == null) return org.springframework.http.ResponseEntity.notFound().build();
        byte[] bytes;
        try (java.io.InputStream in = fileService.open(f)) {
            bytes = in.readNBytes(com.erp.module.system.service.OrgService.LOGO_MAX_BYTES + 1);
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(f.getContentType()))
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)))
                .body(bytes);
    }
}
