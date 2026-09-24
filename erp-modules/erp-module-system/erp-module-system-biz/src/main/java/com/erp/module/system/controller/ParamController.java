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

    public ParamController(ParamService paramService, ParamApi paramApi) {
        this.paramService = paramService;
        this.paramApi = paramApi;
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
        return CommonResult.success(new PublicParams(paramApi.getString("sys.company.name"),
                paramApi.getInt("sys.login.captcha-after-fails"), paramApi.getInt("sys.session.idle-timeout-minutes")));
    }
}
