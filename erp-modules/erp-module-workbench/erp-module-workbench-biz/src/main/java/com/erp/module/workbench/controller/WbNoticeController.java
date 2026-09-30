package com.erp.module.workbench.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.workbench.controller.vo.WbVOs.ActiveNotice;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeDetail;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeQuery;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeReader;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeRow;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeSave;
import com.erp.module.workbench.service.notice.NoticeService;
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

/** 公告：管理需要 wb:notice:manage；首页展示、阅读登录即可 */
@Tag(name = "工作台 - 公告")
@RestController
@RequestMapping("/api/workbench/notices")
public class WbNoticeController {

    private final NoticeService service;

    public WbNoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<PageResult<NoticeRow>> page(@Valid NoticeQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<ActiveNotice>> active(@RequestParam(defaultValue = "false") boolean importantUnread) {
        return CommonResult.success(service.active(importantUnread));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<NoticeDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> read(@PathVariable Long id) {
        service.read(id);
        return CommonResult.success();
    }

    @PostMapping
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<Long> create(@Valid @RequestBody NoticeSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody NoticeSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return CommonResult.success();
    }

    @GetMapping("/{id}/readers")
    @PreAuthorize("@ss.has('wb:notice:manage')")
    public CommonResult<List<NoticeReader>> readers(@PathVariable Long id) {
        return CommonResult.success(service.readers(id));
    }
}
