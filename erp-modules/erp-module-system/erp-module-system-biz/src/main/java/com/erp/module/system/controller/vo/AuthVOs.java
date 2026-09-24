package com.erp.module.system.controller.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 登录与个人中心 VO */
public final class AuthVOs {

    private AuthVOs() {
    }

    public record LoginReq(
            @NotBlank(message = "用户名不能为空") @Size(max = 64) String username,
            @NotBlank(message = "密码不能为空") @Size(max = 128) String password,
            String captchaId,
            @Size(max = 8) String captchaCode) {
    }

    /** @param expiresIn accessToken 有效秒数 */
    public record LoginResp(String accessToken, String refreshToken, long expiresIn, boolean mustChangePassword, boolean passwordExpired) {
    }

    public record RefreshReq(@NotBlank String refreshToken) {
    }

    /** 登录失败时随错误返回：是否需要验证码 */
    public record LoginFailData(boolean captchaRequired) {
    }

    public record CurrentUserResp(Long id, String username, String realName, String employeeNo, Long orgId, String orgName,
                                  Long deptId, String deptName, String position, String mobile, String email, String gender,
                                  String language, Long avatarFileId, List<String> roleNames, List<String> permissions,
                                  boolean admin, boolean mustChangePassword, boolean passwordExpired) {
    }

    public record PasswordPolicy(int minLength, String complexity, int historyCount) {
    }

    public record ProfileUpdate(
            @Pattern(regexp = "^$|^(1\\d{10}|\\+\\d{6,20})$", message = "手机号格式不正确，应为 11 位手机号或 + 开头的国际号码") String mobile,
            @Email(message = "邮箱格式不正确") @Size(max = 128) String email,
            @Pattern(regexp = "zh-CN|en", message = "语言只能是 zh-CN 或 en") String language,
            Long avatarFileId) {
    }

    public record ChangePasswordReq(@NotBlank(message = "请输入原密码") String oldPassword,
                                    @NotBlank(message = "请输入新密码") @Size(max = 64) String newPassword) {
    }
}
