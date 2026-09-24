package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 角色 VO */
public final class RoleVOs {

    private RoleVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RoleQuery extends PageParam {
        private String keyword;
        private String status;
    }

    public record RoleResp(Long id, String code, String name, String dataScope, boolean builtin, int userCount, int sort,
                           String status, String remark, Integer version) {
    }

    public record RoleDetail(Long id, String code, String name, String dataScope, List<Long> customDeptIds, boolean builtin,
                             int sort, String status, String remark, Integer version) {
    }

    public record RoleSave(
            @NotBlank(message = "请输入角色编码") @Pattern(regexp = "[A-Za-z0-9_]{2,32}", message = "角色编码为 2～32 位字母、数字、下划线") String code,
            @NotBlank(message = "请输入角色名称") @Size(max = 32, message = "角色名称不能超过 32 个字") String name,
            @NotBlank(message = "请选择数据范围") String dataScope,
            List<Long> customDeptIds,
            @NotNull(message = "请输入排序") Integer sort,
            @Size(max = 256) String remark,
            Integer version) {
    }

    public record RoleSimple(Long id, String code, String name) {
    }

    public record PermissionSave(@NotNull List<String> permissions) {
    }

    public record MemberResp(Long userId, String username, String realName, String deptName, String status) {
    }

    public record MemberAdd(@NotEmpty List<Long> userIds) {
    }
}
