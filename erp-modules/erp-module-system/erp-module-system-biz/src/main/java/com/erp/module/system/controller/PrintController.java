package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.PrintVOs.Available;
import com.erp.module.system.controller.vo.PrintVOs.BizResp;
import com.erp.module.system.controller.vo.PrintVOs.ForPrint;
import com.erp.module.system.controller.vo.PrintVOs.PrintCount;
import com.erp.module.system.controller.vo.PrintVOs.PrintLogReq;
import com.erp.module.system.controller.vo.PrintVOs.TemplateDetail;
import com.erp.module.system.controller.vo.PrintVOs.TemplateQuery;
import com.erp.module.system.controller.vo.PrintVOs.TemplateResp;
import com.erp.module.system.controller.vo.PrintVOs.TemplateSave;
import com.erp.module.system.controller.vo.PrintVOs.ValidateReq;
import com.erp.module.system.service.print.PrintService;
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

/** 打印模板（需求 01-系统管理/09 第 6 节） */
@Tag(name = "系统管理 - 打印模板")
@RestController
@RequestMapping("/api/system")
public class PrintController {

    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @GetMapping("/print-templates")
    @PreAuthorize("@ss.has('system:print:query')")
    public CommonResult<PageResult<TemplateResp>> page(@Valid TemplateQuery q) {
        return CommonResult.success(printService.page(q));
    }

    @GetMapping("/print-templates/{id}")
    @PreAuthorize("@ss.has('system:print:query')")
    public CommonResult<TemplateDetail> get(@PathVariable Long id) {
        return CommonResult.success(printService.get(id));
    }

    /** 打印按钮：该单据类型的启用模板（登录即可） */
    @GetMapping("/print-templates/available")
    public CommonResult<Available> available(@RequestParam String bizType) {
        return CommonResult.success(printService.available(bizType));
    }

    /** 打印时取模板内容（登录即可；打印数据由业务模块按 print 权限控制） */
    @GetMapping("/print-templates/{id}/for-print")
    public CommonResult<ForPrint> forPrint(@PathVariable Long id) {
        return CommonResult.success(printService.forPrint(id));
    }

    /** 编辑器保存前校验（R02） */
    @PostMapping("/print-templates/validate")
    @PreAuthorize("@ss.has('system:print:query')")
    public CommonResult<Void> validate(@Valid @RequestBody ValidateReq req) {
        printService.validate(req.content());
        return CommonResult.success(null);
    }

    @OperLog("新建打印模板")
    @PostMapping("/print-templates")
    @PreAuthorize("@ss.has('system:print:create')")
    public CommonResult<Long> create(@Valid @RequestBody TemplateSave req) {
        return CommonResult.success(printService.create(req));
    }

    @OperLog("修改打印模板")
    @PutMapping("/print-templates/{id}")
    @PreAuthorize("@ss.has('system:print:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody TemplateSave req) {
        printService.update(id, req);
        return CommonResult.success(null);
    }

    @OperLog("删除打印模板")
    @DeleteMapping("/print-templates/{id}")
    @PreAuthorize("@ss.has('system:print:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        printService.delete(id);
        return CommonResult.success(null);
    }

    @OperLog("复制打印模板")
    @PostMapping("/print-templates/{id}/copy")
    @PreAuthorize("@ss.has('system:print:create')")
    public CommonResult<Long> copy(@PathVariable Long id) {
        return CommonResult.success(printService.copy(id));
    }

    @OperLog("设为默认打印模板")
    @PostMapping("/print-templates/{id}/set-default")
    @PreAuthorize("@ss.has('system:print:update')")
    public CommonResult<Void> setDefault(@PathVariable Long id) {
        printService.setDefault(id);
        return CommonResult.success(null);
    }

    @OperLog("启用打印模板")
    @PostMapping("/print-templates/{id}/enable")
    @PreAuthorize("@ss.has('system:print:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        printService.setStatus(id, true);
        return CommonResult.success(null);
    }

    @OperLog("停用打印模板")
    @PostMapping("/print-templates/{id}/disable")
    @PreAuthorize("@ss.has('system:print:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        printService.setStatus(id, false);
        return CommonResult.success(null);
    }

    @GetMapping("/print-biz")
    @PreAuthorize("@ss.has('system:print:query')")
    public CommonResult<List<BizResp>> bizList() {
        return CommonResult.success(printService.bizList());
    }

    /** 变量说明与示例数据（模板编辑器） */
    @GetMapping("/print-biz/{bizType}")
    @PreAuthorize("@ss.has('system:print:query')")
    public CommonResult<BizResp> biz(@PathVariable String bizType) {
        return CommonResult.success(printService.biz(bizType));
    }

    /** 记录打印（登录即可） */
    @PostMapping("/print-logs")
    public CommonResult<Void> log(@Valid @RequestBody PrintLogReq req) {
        printService.log(req.bizType(), req.bizIds(), req.templateId());
        return CommonResult.success(null);
    }

    /** 已打印次数 */
    @GetMapping("/print-logs/counts")
    public CommonResult<List<PrintCount>> counts(@RequestParam String bizType, @RequestParam List<Long> bizIds) {
        return CommonResult.success(printService.counts(bizType, bizIds));
    }
}
