package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.controller.vo.FileVOs.FileResp;
import com.erp.module.system.dal.dataobject.FileDO;
import com.erp.module.system.service.file.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 附件（需求 01-系统管理/12 第 1 节）。登录即可访问；查看、编辑权限由 FileAccessChecker 按单据判断（R04）。
 */
@Tag(name = "系统管理 - 附件")
@RestController
@RequestMapping("/api/system/files")
public class FileController {

    /** 允许浏览器内联预览的类型，其他类型一律作为下载（防止 HTML/SVG 等在站点域名下执行） */
    private static final Set<String> INLINE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "application/pdf", "text/plain");

    private final FileService fileService;
    private final UserApi userApi;

    public FileController(FileService fileService, UserApi userApi) {
        this.fileService = fileService;
        this.userApi = userApi;
    }

    /** 上传限制，供前端预校验 */
    @GetMapping("/policy")
    public CommonResult<FileService.Policy> policy() {
        return CommonResult.success(fileService.policy());
    }

    @OperLog("上传附件")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<FileResp> upload(@RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String bizType,
                                         @RequestParam(required = false) Long bizId,
                                         @RequestParam(required = false) String category) {
        FileDO f = fileService.upload(file, bizType, bizId, category);
        return CommonResult.success(toResp(List.of(f)).get(0));
    }

    @GetMapping
    public CommonResult<List<FileResp>> list(@RequestParam String bizType, @RequestParam Long bizId) {
        return CommonResult.success(toResp(fileService.listForCurrentUser(bizType, bizId)));
    }

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        write(fileService.getForView(id), false, response);
    }

    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        write(fileService.getForView(id), true, response);
    }

    @OperLog("删除附件")
    @DeleteMapping("/{id}")
    public CommonResult<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return CommonResult.success(null);
    }

    private void write(FileDO f, boolean inline, HttpServletResponse response) throws IOException {
        boolean asInline = inline && INLINE_TYPES.contains(f.getContentType());
        String type = "text/plain".equals(f.getContentType()) ? "text/plain;charset=UTF-8" : f.getContentType();
        String encoded = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        try (InputStream in = fileService.open(f)) {
            response.setContentType(asInline ? type : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader("Content-Disposition", (asInline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, max-age=0");
            response.setContentLengthLong(f.getFileSize());
            OutputStream out = response.getOutputStream();
            in.transferTo(out);
            out.flush();
        }
    }

    private List<FileResp> toResp(List<FileDO> files) {
        Map<Long, UserDTO> users = userApi.list(files.stream().map(FileDO::getCreatedBy).filter(Objects::nonNull).distinct().toList());
        return files.stream().map(f -> new FileResp(f.getId(), f.getBizType(), f.getBizId(), f.getCategory(), f.getFileName(), f.getFileSize(),
                f.getContentType(), f.getCreatedBy(), users.containsKey(f.getCreatedBy()) ? users.get(f.getCreatedBy()).realName() : null,
                f.getCreatedAt())).toList();
    }
}
