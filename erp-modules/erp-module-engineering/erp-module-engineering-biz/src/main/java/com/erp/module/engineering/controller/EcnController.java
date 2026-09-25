package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.engineering.controller.vo.EcnVOs.BatchReplaceReq;
import com.erp.module.engineering.controller.vo.EcnVOs.DoneReq;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnDetail;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnQuery;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnRow;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnSave;
import com.erp.module.engineering.controller.vo.EcnVOs.LineResp;
import com.erp.module.engineering.controller.vo.EcnVOs.SubmitResult;
import com.erp.module.engineering.service.EcnService;
import com.erp.module.engineering.service.ExportSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** ECN 工程变更（需求 05-05 第 6 节） */
@Tag(name = "研发工程 - ECN")
@RestController
@RequestMapping("/api/engineering/ecns")
public class EcnController {

    static final Map<String, String> STATUS = Map.of("DRAFT", "草稿", "PENDING_APPROVAL", "待审批", "APPROVED", "已审核", "IN_PROGRESS", "已生效",
            "COMPLETED", "已关闭", "VOIDED", "已作废");
    static final Map<String, String> MODES = Map.of("IMMEDIATE", "立即生效", "DATE", "指定日期", "USE_UP", "旧料用完后切换");

    static final List<ExcelColumn<EcnRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", EcnRow::docNo),
            ExcelColumn.text("title", "标题", EcnRow::title),
            ExcelColumn.text("ecnType", "类型", EcnRow::ecnType),
            ExcelColumn.text("reasonType", "原因", EcnRow::reasonType),
            ExcelColumn.text("urgency", "紧急程度", r -> "URGENT".equals(r.urgency()) ? "紧急" : "普通"),
            ExcelColumn.text("effectiveMode", "生效方式", r -> MODES.get(r.effectiveMode())),
            ExcelColumn.date("effectiveDate", "生效日期", EcnRow::effectiveDate),
            ExcelColumn.text("status", "状态", r -> STATUS.get(r.status())),
            ExcelColumn.text("createdByName", "发起人", EcnRow::createdByName),
            ExcelColumn.date("docDate", "单据日期", EcnRow::docDate));

    private final EcnService service;
    private final ExportSupport exportSupport;

    public EcnController(EcnService service, ExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('eng:ecn:query')")
    public CommonResult<PageResult<EcnRow>> page(@Valid EcnQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:ecn:query')")
    public CommonResult<EcnDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:ecn:create')")
    public CommonResult<Long> create(@Valid @RequestBody EcnSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:ecn:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody EcnSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:ecn:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "批量替换预览：原子件 → 新子件，返回将生成的 REPLACE 明细")
    @PostMapping("/batch-replace-preview")
    @PreAuthorize("@ss.has('eng:ecn:create')")
    public CommonResult<List<LineResp>> batchReplacePreview(@Valid @RequestBody BatchReplaceReq req) {
        return CommonResult.success(service.batchReplacePreview(req));
    }

    @PostMapping("/{id}/analyze")
    @PreAuthorize("@ss.has('eng:ecn:update')")
    public CommonResult<Void> analyze(@PathVariable Long id) {
        service.analyze(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('eng:ecn:submit')")
    public CommonResult<SubmitResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('eng:ecn:void')")
    public CommonResult<Void> voidEcn(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        service.voidEcn(id, body == null ? null : body.get("reason"));
        return CommonResult.success();
    }

    @PostMapping("/{id}/effect")
    @PreAuthorize("@ss.has('eng:ecn:effect')")
    public CommonResult<Void> effect(@PathVariable Long id) {
        service.effect(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('eng:ecn:close')")
    public CommonResult<Void> close(@PathVariable Long id) {
        service.close(id);
        return CommonResult.success();
    }

    @Operation(summary = "执行确认完成（任务负责人，登录即可）")
    @PostMapping("/{id}/tasks/{taskId}/done")
    public CommonResult<Void> taskDone(@PathVariable Long id, @PathVariable Long taskId, @Valid @RequestBody(required = false) DoneReq req) {
        service.taskDone(id, taskId, req == null ? null : req.remark());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('eng:ecn:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('eng:ecn:query')")
    public void export(@Valid EcnQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "ECN", EXPORT_COLUMNS, columns, limit -> service.listForExport(q, limit));
    }
}
