package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.finance.controller.vo.CloseVOs.CheckResult;
import com.erp.module.finance.controller.vo.CloseVOs.FxResult;
import com.erp.module.finance.controller.vo.CloseVOs.PeriodRow;
import com.erp.module.finance.controller.vo.CloseVOs.ReopenReq;
import com.erp.module.finance.service.close.CloseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "财务 - 月结")
@RestController
@RequestMapping("/api/finance/close")
public class FinCloseController {

    private final CloseService service;

    public FinCloseController(CloseService service) {
        this.service = service;
    }

    @GetMapping("/periods")
    @PreAuthorize("@ss.has('fin:close:query')")
    public CommonResult<List<PeriodRow>> periods(@RequestParam(required = false) Integer year) {
        return CommonResult.success(service.periods(year));
    }

    @GetMapping("/{period}/check")
    @PreAuthorize("@ss.has('fin:close:query')")
    public CommonResult<CheckResult> check(@PathVariable String period) {
        return CommonResult.success(service.check(period));
    }

    @GetMapping("/{period}/fx-revaluation")
    @PreAuthorize("@ss.has('fin:close:query')")
    public CommonResult<FxResult> fxPreview(@PathVariable String period) {
        return CommonResult.success(service.fxPreview(period));
    }

    @PostMapping("/{period}/fx-revaluation")
    @PreAuthorize("@ss.has('fin:close:execute')")
    public CommonResult<FxResult> revalue(@PathVariable String period) {
        return CommonResult.success(service.revalue(period));
    }

    @PostMapping("/{period}/close")
    @PreAuthorize("@ss.has('fin:close:execute')")
    public CommonResult<Void> close(@PathVariable String period) {
        service.close(period);
        return CommonResult.success();
    }

    @PostMapping("/{period}/reopen")
    @PreAuthorize("@ss.has('fin:close:reopen')")
    public CommonResult<Void> reopen(@PathVariable String period, @RequestBody ReopenReq req) {
        service.reopen(period, req.reason());
        return CommonResult.success();
    }
}
