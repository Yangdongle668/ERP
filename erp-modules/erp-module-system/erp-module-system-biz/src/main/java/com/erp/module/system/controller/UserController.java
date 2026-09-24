package com.erp.module.system.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.dict.DictItemDTO;
import com.erp.module.system.service.support.ExportHelper;
import com.erp.module.system.controller.vo.UserVOs.BatchResult;
import com.erp.module.system.controller.vo.UserVOs.CreateResult;
import com.erp.module.system.controller.vo.UserVOs.ResetPasswordReq;
import com.erp.module.system.controller.vo.UserVOs.ResetPasswordResult;
import com.erp.module.system.controller.vo.UserVOs.UserDetail;
import com.erp.module.system.controller.vo.UserVOs.UserQuery;
import com.erp.module.system.controller.vo.UserVOs.UserResp;
import com.erp.module.system.controller.vo.UserVOs.UserSave;
import com.erp.module.system.controller.vo.UserVOs.UserSimple;
import com.erp.module.system.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户管理（01-02） */
@Tag(name = "系统管理 - 用户")
@RestController
@RequestMapping("/api/system/users")
public class UserController {

    private static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("username", "用户名", true, "4～32 位，字母开头，字母数字 . _ -"),
            ExcelColumn.input("realName", "姓名", true, ""),
            ExcelColumn.input("employeeNo", "工号", false, "非空时唯一"),
            ExcelColumn.input("deptCode", "主部门编码", true, "组织编码，必须存在且启用"),
            ExcelColumn.input("roleCodes", "角色编码", true, "多个用英文逗号分隔"),
            ExcelColumn.input("mobile", "手机号", false, "11 位手机号或 + 开头的国际号码"),
            ExcelColumn.input("email", "邮箱", false, ""),
            ExcelColumn.input("position", "岗位", false, "岗位名称（字典标签）"),
            ExcelColumn.input("superiorUsername", "直属上级用户名", false, "必须存在，可以是本文件中前面的行"));

    private static final List<ExcelColumn<UserResp>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("username", "用户名", UserResp::username),
            ExcelColumn.text("realName", "姓名", UserResp::realName),
            ExcelColumn.text("employeeNo", "工号", UserResp::employeeNo),
            ExcelColumn.text("deptName", "主部门", UserResp::deptName),
            ExcelColumn.<UserResp>text("roleNames", "角色", u -> String.join(",", u.roleNames())).width(30),
            ExcelColumn.text("position", "岗位", UserResp::position),
            ExcelColumn.text("mobile", "手机号", UserResp::mobile),
            ExcelColumn.<UserResp>text("status", "状态", u -> "ENABLED".equals(u.status()) ? "启用" : "停用"),
            ExcelColumn.dateTime("lastLoginAt", "最近登录", UserResp::lastLoginAt),
            ExcelColumn.dateTime("createdAt", "创建时间", UserResp::createdAt));

    private final UserService userService;
    private final DictApi dictApi;
    private final ExportHelper exportHelper;

    public UserController(UserService userService, DictApi dictApi, ExportHelper exportHelper) {
        this.userService = userService;
        this.dictApi = dictApi;
        this.exportHelper = exportHelper;
    }

    @GetMapping
    @PreAuthorize("@ss.has('system:user:query')")
    public CommonResult<PageResult<UserResp>> page(@Valid UserQuery q) {
        return CommonResult.success(userService.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('system:user:query')")
    public CommonResult<UserDetail> get(@PathVariable Long id) {
        return CommonResult.success(userService.getDetail(id));
    }

    /** UserSelect 远程搜索，登录即可 */
    @GetMapping("/simple")
    public CommonResult<List<UserSimple>> simple(@RequestParam(required = false) String keyword, @RequestParam(required = false) String ids) {
        List<Long> idList = ids == null || ids.isBlank() ? null
                : Arrays.stream(ids.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
        return CommonResult.success(userService.simple(keyword, idList));
    }

    @OperLog("新建用户")
    @PostMapping
    @PreAuthorize("@ss.has('system:user:create')")
    public CommonResult<CreateResult> create(@Valid @RequestBody UserSave req) {
        return CommonResult.success(userService.create(req));
    }

    @OperLog("修改用户")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('system:user:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody UserSave req) {
        userService.update(id, req);
        return CommonResult.success();
    }

    @OperLog("启用用户")
    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('system:user:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        userService.enable(id);
        return CommonResult.success();
    }

    @OperLog("停用用户")
    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('system:user:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return CommonResult.success();
    }

    @OperLog("批量停用用户")
    @PostMapping("/batch-disable")
    @PreAuthorize("@ss.has('system:user:update')")
    public CommonResult<List<BatchResult>> batchDisable(@RequestBody List<Long> ids) {
        return CommonResult.success(userService.batchDisable(ids));
    }

    @OperLog("解锁用户")
    @PostMapping("/{id}/unlock")
    @PreAuthorize("@ss.has('system:user:update')")
    public CommonResult<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return CommonResult.success();
    }

    @OperLog("强制下线")
    @PostMapping("/{id}/kick")
    @PreAuthorize("@ss.has('system:user:update')")
    public CommonResult<Void> kick(@PathVariable Long id) {
        userService.kick(id);
        return CommonResult.success();
    }

    @OperLog("重置密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@ss.has('system:user:reset-password')")
    public CommonResult<ResetPasswordResult> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordReq req) {
        return CommonResult.success(userService.resetPassword(id, req));
    }

    // ==================== 导入导出 ====================

    @GetMapping("/import-template")
    @PreAuthorize("@ss.has('system:user:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<ExcelColumn<Object>> cols = IMPORT_COLUMNS.stream()
                .map(c -> "position".equals(c.key()) ? c.options(positionOptions().keySet().stream().toList()) : c).toList();
        ExcelSupport.template(response, "用户", cols);
    }

    /** 校验；report=true 时返回错误报告文件 */
    @PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('system:user:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        userService.checkImport(rows, positionOptions());
        if (report) {
            ExcelSupport.writeBytes(response, "用户导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(IMPORT_COLUMNS, rows, r -> "新增"));
    }

    /** 不允许部分导入：有错误时拒绝 */
    @OperLog("导入用户")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('system:user:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file) {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        Map<String, String> positions = positionOptions();
        userService.checkImport(rows, positions);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        int n = userService.importUsers(rows, positions);
        return CommonResult.success(new ImportResult(n, 0, List.of()));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('system:user:export')")
    public void export(@Valid UserQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportHelper.export(response, "system", "用户", EXPORT_COLUMNS, columns, limit -> userService.listForExport(q, limit));
    }

    /** 岗位字典：标签 → 值 */
    private Map<String, String> positionOptions() {
        return dictApi.getItems("sys_position").stream().collect(Collectors.toMap(DictItemDTO::label, DictItemDTO::value, (a, b) -> a,
                java.util.LinkedHashMap::new));
    }
}
