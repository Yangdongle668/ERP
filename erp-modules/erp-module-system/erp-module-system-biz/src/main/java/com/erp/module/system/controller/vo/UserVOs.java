package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 用户管理 VO */
public final class UserVOs {

    private UserVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserQuery extends PageParam {
        /** 部门（含下级） */
        private Long deptId;
        /** 用户名/姓名/工号/手机号 */
        private String keyword;
        private String status;
        private Long roleId;
        private String position;
        private LocalDate lastLoginFrom;
        private LocalDate lastLoginTo;
        /** 排序字段：lastLoginAt / createdAt */
        private String sortField;
        /** asc / desc */
        private String sortOrder;
    }

    public record UserResp(Long id, String username, String realName, String employeeNo, Long deptId, String deptName,
                           List<String> roleNames, String position, String mobile, String status, boolean locked,
                           LocalDateTime lastLoginAt, String lastLoginIp, LocalDateTime createdAt, boolean admin) {
    }

    public record UserDetail(Long id, String username, String realName, String employeeNo, String gender, String mobile,
                             String email, String position, String language, String remark, Long deptId, List<Long> partDeptIds,
                             Long superiorUserId, String superiorName, List<Long> roleIds, String status, boolean admin,
                             boolean locked, LocalDateTime lastLoginAt, Integer version) {
    }

    public record UserSave(
            @NotBlank(message = "请输入用户名") String username,
            @NotBlank(message = "请输入姓名") @Size(max = 32, message = "姓名不能超过 32 个字") String realName,
            @Size(max = 32) String employeeNo,
            String gender,
            @Pattern(regexp = "^$|^(1\\d{10}|\\+\\d{6,20})$", message = "手机号格式不正确，应为 11 位手机号或 + 开头的国际号码") String mobile,
            @Email(message = "邮箱格式不正确") @Size(max = 128) String email,
            String position,
            String language,
            @Size(max = 256) String remark,
            @NotNull(message = "请选择主部门") Long deptId,
            List<Long> partDeptIds,
            Long superiorUserId,
            @NotEmpty(message = "请至少分配一个角色") List<Long> roleIds,
            /** 新建时的初始密码；为空时取参数 sys.user.init-password，参数也为空时随机生成 */
            @Size(max = 64) String password,
            Boolean mustChangePassword,
            Integer version) {
    }

    /** 新建结果：初始密码只返回这一次 */
    public record CreateResult(Long id, String initPassword) {
    }

    public record UserSimple(Long id, String username, String realName, String employeeNo, String deptName) {
    }

    public record ResetPasswordReq(@NotBlank String mode, @Size(max = 64) String password, Boolean mustChangePassword) {
    }

    public record ResetPasswordResult(String password) {
    }

    public record BatchResult(Long id, String name, boolean success, String message) {
    }
}
