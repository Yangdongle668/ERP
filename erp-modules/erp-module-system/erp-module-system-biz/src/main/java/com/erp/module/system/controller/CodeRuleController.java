package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.CodeRuleVOs.ManualInfo;
import com.erp.module.system.controller.vo.CodeRuleVOs.PreviewReq;
import com.erp.module.system.controller.vo.CodeRuleVOs.RuleResp;
import com.erp.module.system.controller.vo.CodeRuleVOs.RuleSave;
import com.erp.module.system.controller.vo.CodeRuleVOs.SeqAdjust;
import com.erp.module.system.controller.vo.CodeRuleVOs.SeqResp;
import com.erp.module.system.service.CodeRuleService;
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

/** 编码规则（01-05） */
@Tag(name = "系统管理 - 编码规则")
@RestController
@RequestMapping("/api/system/code-rules")
public class CodeRuleController {

    private final CodeRuleService codeRuleService;

    public CodeRuleController(CodeRuleService codeRuleService) {
        this.codeRuleService = codeRuleService;
    }

    @GetMapping
    @PreAuthorize("@ss.has('system:code-rule:query')")
    public CommonResult<List<RuleResp>> list(@RequestParam(required = false) String moduleCode, @RequestParam(required = false) String keyword) {
        return CommonResult.success(codeRuleService.list(moduleCode, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('system:code-rule:query')")
    public CommonResult<RuleResp> get(@PathVariable Long id) {
        return CommonResult.success(codeRuleService.get(id));
    }

    @OperLog("修改编码规则")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('system:code-rule:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody RuleSave req) {
        codeRuleService.update(id, req);
        return CommonResult.success();
    }

    @OperLog(value = "预览编码规则", enabled = false)
    @PostMapping("/preview")
    @PreAuthorize("@ss.has('system:code-rule:query')")
    public CommonResult<String> preview(@Valid @RequestBody PreviewReq req) {
        return CommonResult.success(codeRuleService.preview(req));
    }

    @GetMapping("/{id}/seqs")
    @PreAuthorize("@ss.has('system:code-rule:query')")
    public CommonResult<List<SeqResp>> seqs(@PathVariable Long id) {
        return CommonResult.success(codeRuleService.seqs(id));
    }

    @OperLog("调整流水号")
    @PutMapping("/{id}/seqs")
    @PreAuthorize("@ss.has('system:code-rule:update')")
    public CommonResult<Void> adjust(@PathVariable Long id, @Valid @RequestBody SeqAdjust req) {
        codeRuleService.adjustSeq(id, req);
        return CommonResult.success();
    }

    /** 前端表单判断编码框是否可编辑，登录即可 */
    @GetMapping("/by-biz/{bizCode}/allow-manual")
    public CommonResult<ManualInfo> allowManual(@PathVariable String bizCode) {
        return CommonResult.success(new ManualInfo(codeRuleService.isManualAllowed(bizCode)));
    }
}
