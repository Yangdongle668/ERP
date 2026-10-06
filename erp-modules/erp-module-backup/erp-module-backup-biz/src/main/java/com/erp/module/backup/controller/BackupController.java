package com.erp.module.backup.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageParam;
import com.erp.common.result.PageResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.backup.controller.vo.BackupVOs.BackupInfo;
import com.erp.module.backup.controller.vo.BackupVOs.BackupReq;
import com.erp.module.backup.controller.vo.BackupVOs.RecordRow;
import com.erp.module.backup.controller.vo.BackupVOs.RestoreCheck;
import com.erp.module.backup.controller.vo.BackupVOs.RestoreLogRow;
import com.erp.module.backup.controller.vo.BackupVOs.RestoreReq;
import com.erp.module.backup.service.BackupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** 系统备份（需求 14）：全部接口只有超级管理员可以访问 */
@Tag(name = "系统备份")
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService service;

    public BackupController(BackupService service) {
        this.service = service;
    }

    @GetMapping("/info")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<BackupInfo> info() {
        return CommonResult.success(service.info());
    }

    @GetMapping("/records")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<PageResult<RecordRow>> page(@Valid PageParam q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<RecordRow> get(@PathVariable Long id) {
        return CommonResult.success(service.get(id));
    }

    @OperLog("系统备份")
    @PostMapping("/records")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<Long> backup(@Valid @RequestBody(required = false) BackupReq req) {
        return CommonResult.success(service.startBackup(req));
    }

    @OperLog("删除系统备份")
    @DeleteMapping("/records/{id}")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @OperLog("下载系统备份")
    @GetMapping("/records/{id}/download")
    @PreAuthorize("@ss.has('*')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Path p = service.file(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(p));
    }

    @OperLog("上传系统备份")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('*')")
    public CommonResult<Long> upload(@RequestPart("file") MultipartFile file) {
        return CommonResult.success(service.upload(file));
    }

    @GetMapping("/records/{id}/check")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<RestoreCheck> check(@PathVariable Long id) {
        return CommonResult.success(service.check(id));
    }

    @OperLog("一键恢复系统数据")
    @PostMapping("/records/{id}/restore")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<Long> restore(@PathVariable Long id, @RequestBody RestoreReq req) {
        return CommonResult.success(service.startRestore(id, req.confirm()));
    }

    /** 恢复进度（维护模式中也可访问） */
    @GetMapping("/restore/status")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<RestoreLogRow> restoreStatus() {
        return CommonResult.success(service.restoreStatus());
    }

    @GetMapping("/restore/logs")
    @PreAuthorize("@ss.has('*')")
    public CommonResult<List<RestoreLogRow>> restoreLogs(@RequestParam(defaultValue = "20") int limit) {
        return CommonResult.success(service.restoreLogs(limit));
    }
}
