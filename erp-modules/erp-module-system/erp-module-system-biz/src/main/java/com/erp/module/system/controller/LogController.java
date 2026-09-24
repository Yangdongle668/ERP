package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.system.controller.vo.LogVOs.DocLogResp;
import com.erp.module.system.controller.vo.LogVOs.LoginLogQuery;
import com.erp.module.system.controller.vo.LogVOs.LoginLogResp;
import com.erp.module.system.controller.vo.LogVOs.OperLogQuery;
import com.erp.module.system.controller.vo.LogVOs.OperLogResp;
import com.erp.module.system.service.LogService;
import com.erp.module.system.service.support.ExportHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 日志审计（01-11）：只读，任何人不能修改或删除日志 */
@Tag(name = "系统管理 - 日志审计")
@RestController
@RequestMapping("/api/system")
public class LogController {

    private static final List<ExcelColumn<LoginLogResp>> LOGIN_COLUMNS = List.of(
            ExcelColumn.dateTime("createdAt", "时间", LoginLogResp::createdAt),
            ExcelColumn.text("username", "用户名", LoginLogResp::username),
            ExcelColumn.text("realName", "姓名", LoginLogResp::realName),
            ExcelColumn.text("type", "类型", LoginLogResp::type),
            ExcelColumn.text("result", "结果", LoginLogResp::result),
            ExcelColumn.text("ip", "IP", LoginLogResp::ip),
            ExcelColumn.text("browser", "浏览器", LoginLogResp::browser),
            ExcelColumn.text("os", "操作系统", LoginLogResp::os));

    private static final List<ExcelColumn<OperLogResp>> OPER_COLUMNS = List.of(
            ExcelColumn.dateTime("createdAt", "时间", OperLogResp::createdAt),
            ExcelColumn.text("realName", "操作人", OperLogResp::realName),
            ExcelColumn.text("moduleName", "模块", OperLogResp::moduleName),
            ExcelColumn.text("action", "操作", OperLogResp::action),
            ExcelColumn.text("result", "结果", OperLogResp::result),
            ExcelColumn.text("errorMsg", "错误信息", OperLogResp::errorMsg),
            ExcelColumn.number("durationMs", "耗时(ms)", OperLogResp::durationMs),
            ExcelColumn.text("ip", "IP", OperLogResp::ip),
            ExcelColumn.text("traceId", "追踪号", OperLogResp::traceId));

    private final LogService logService;
    private final ExportHelper exportHelper;

    public LogController(LogService logService, ExportHelper exportHelper) {
        this.logService = logService;
        this.exportHelper = exportHelper;
    }

    @GetMapping("/login-logs")
    @PreAuthorize("@ss.has('system:log:query')")
    public CommonResult<PageResult<LoginLogResp>> loginLogs(@Valid LoginLogQuery q) {
        return CommonResult.success(logService.loginLogs(q));
    }

    @GetMapping("/oper-logs")
    @PreAuthorize("@ss.has('system:log:query')")
    public CommonResult<PageResult<OperLogResp>> operLogs(@Valid OperLogQuery q) {
        return CommonResult.success(logService.operLogs(q));
    }

    @GetMapping("/oper-logs/{id}")
    @PreAuthorize("@ss.has('system:log:query')")
    public CommonResult<OperLogResp> operLog(@PathVariable Long id) {
        return CommonResult.success(logService.operLog(id));
    }

    @GetMapping("/login-logs/export")
    @PreAuthorize("@ss.has('system:log:export')")
    public void exportLogin(@Valid LoginLogQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportHelper.export(response, "system", "登录日志", LOGIN_COLUMNS, columns, limit -> collect(q, logService::loginLogs, limit));
    }

    @GetMapping("/oper-logs/export")
    @PreAuthorize("@ss.has('system:log:export')")
    public void exportOper(@Valid OperLogQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportHelper.export(response, "system", "操作日志", OPER_COLUMNS, columns, limit -> collect(q, logService::operLogs, limit));
    }

    /** 单据操作日志：登录即可（单据详情页“操作日志”页签） */
    @GetMapping("/doc-logs")
    public CommonResult<List<DocLogResp>> docLogs(@RequestParam String bizType, @RequestParam Long bizId) {
        return CommonResult.success(logService.docLogs(bizType, bizId));
    }

    private <Q extends com.erp.common.result.PageParam, T> List<T> collect(Q q, Function<Q, PageResult<T>> page, int max) {
        q.setPageNo(1);
        q.setPageSize(500);
        List<T> all = new ArrayList<>();
        while (all.size() < max) {
            PageResult<T> p = page.apply(q);
            all.addAll(p.list());
            if (p.list().isEmpty() || all.size() >= p.total()) break;
            q.setPageNo(q.getPageNo() + 1);
        }
        return all.size() > max ? all.subList(0, max) : all;
    }

}
