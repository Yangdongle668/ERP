package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.PermissionVOs.ModuleNode;
import com.erp.module.system.controller.vo.RoleVOs.MemberAdd;
import com.erp.module.system.controller.vo.RoleVOs.MemberResp;
import com.erp.module.system.controller.vo.RoleVOs.PermissionSave;
import com.erp.module.system.controller.vo.RoleVOs.RoleDetail;
import com.erp.module.system.controller.vo.RoleVOs.RoleQuery;
import com.erp.module.system.controller.vo.RoleVOs.RoleResp;
import com.erp.module.system.controller.vo.RoleVOs.RoleSave;
import com.erp.module.system.controller.vo.RoleVOs.RoleSimple;
import com.erp.module.system.service.PermissionService;
import com.erp.module.system.service.RoleService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色与权限（01-03） */
@Tag(name = "系统管理 - 角色与权限")
@RestController
@RequestMapping("/api/system")
public class RoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    public RoleController(RoleService roleService, PermissionService permissionService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    @GetMapping("/roles")
    @PreAuthorize("@ss.has('system:role:query')")
    public CommonResult<PageResult<RoleResp>> page(@Valid RoleQuery q) {
        return CommonResult.success(roleService.page(q));
    }

    /** 启用角色列表，登录即可（用户表单） */
    @GetMapping("/roles/simple")
    public CommonResult<List<RoleSimple>> simple() {
        return CommonResult.success(roleService.simple());
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("@ss.has('system:role:query')")
    public CommonResult<RoleDetail> get(@PathVariable Long id) {
        return CommonResult.success(roleService.get(id));
    }

    @OperLog("新建角色")
    @PostMapping("/roles")
    @PreAuthorize("@ss.has('system:role:create')")
    public CommonResult<Long> create(@Valid @RequestBody RoleSave req) {
        return CommonResult.success(roleService.create(req));
    }

    @OperLog("修改角色")
    @PutMapping("/roles/{id}")
    @PreAuthorize("@ss.has('system:role:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSave req) {
        roleService.update(id, req);
        return CommonResult.success();
    }

    @OperLog("启用角色")
    @PostMapping("/roles/{id}/enable")
    @PreAuthorize("@ss.has('system:role:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        roleService.enable(id);
        return CommonResult.success();
    }

    @OperLog("停用角色")
    @PostMapping("/roles/{id}/disable")
    @PreAuthorize("@ss.has('system:role:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        roleService.disable(id);
        return CommonResult.success();
    }

    @OperLog("删除角色")
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@ss.has('system:role:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return CommonResult.success();
    }

    @OperLog("复制角色")
    @PostMapping("/roles/{id}/copy")
    @PreAuthorize("@ss.has('system:role:create')")
    public CommonResult<Long> copy(@PathVariable Long id) {
        return CommonResult.success(roleService.copy(id));
    }

    @GetMapping("/permissions/tree")
    @PreAuthorize("@ss.has('system:role:grant')")
    public CommonResult<List<ModuleNode>> permissionTree() {
        return CommonResult.success(permissionService.tree());
    }

    @GetMapping("/roles/{id}/permissions")
    @PreAuthorize("@ss.has('system:role:grant')")
    public CommonResult<List<String>> permissions(@PathVariable Long id) {
        return CommonResult.success(roleService.permissions(id));
    }

    @OperLog("角色授权")
    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("@ss.has('system:role:grant')")
    public CommonResult<Void> savePermissions(@PathVariable Long id, @Valid @RequestBody PermissionSave req) {
        roleService.savePermissions(id, req.permissions());
        return CommonResult.success();
    }

    @GetMapping("/roles/{id}/users")
    @PreAuthorize("@ss.has('system:role:query')")
    public CommonResult<List<MemberResp>> members(@PathVariable Long id) {
        return CommonResult.success(roleService.members(id));
    }

    @OperLog("添加角色成员")
    @PostMapping("/roles/{id}/users")
    @PreAuthorize("@ss.has('system:role:update')")
    public CommonResult<Void> addMembers(@PathVariable Long id, @Valid @RequestBody MemberAdd req) {
        roleService.addMembers(id, req.userIds());
        return CommonResult.success();
    }

    @OperLog("移除角色成员")
    @DeleteMapping("/roles/{id}/users/{userId}")
    @PreAuthorize("@ss.has('system:role:update')")
    public CommonResult<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        roleService.removeMember(id, userId);
        return CommonResult.success();
    }
}
