package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.controller.vo.AuthVOs.CurrentUserResp;
import com.erp.module.system.controller.vo.AuthVOs.LoginReq;
import com.erp.module.system.controller.vo.AuthVOs.LoginResp;
import com.erp.module.system.controller.vo.AuthVOs.PasswordPolicy;
import com.erp.module.system.controller.vo.AuthVOs.RefreshReq;
import com.erp.module.system.service.AuthService;
import com.erp.module.system.service.CaptchaService;
import com.erp.module.system.service.PasswordPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 登录与认证（01-13）。登录、刷新、验证码、密码策略为公开接口（见 SecurityConfig）。 */
@Tag(name = "系统管理 - 认证")
@RestController
@RequestMapping("/api/system/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final PasswordPolicyService passwordPolicy;

    public AuthController(AuthService authService, CaptchaService captchaService, PasswordPolicyService passwordPolicy) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.passwordPolicy = passwordPolicy;
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public CommonResult<LoginResp> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        return CommonResult.success(authService.login(req, request));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public CommonResult<LoginResp> refresh(@Valid @RequestBody RefreshReq req, HttpServletRequest request) {
        return CommonResult.success(authService.refresh(req.refreshToken(), request));
    }

    @Operation(summary = "退出登录（记录登出日志）")
    @PostMapping("/logout")
    public CommonResult<Void> logout(HttpServletRequest request) {
        authService.logout(SecurityUtils.getLoginUserOrNull(), request);
        return CommonResult.success();
    }

    @Operation(summary = "当前登录用户、权限、是否需要改密")
    @GetMapping("/me")
    public CommonResult<CurrentUserResp> me() {
        return CommonResult.success(authService.me(SecurityUtils.getLoginUser()));
    }

    @Operation(summary = "图片验证码")
    @GetMapping("/captcha")
    public CommonResult<CaptchaService.Captcha> captcha() {
        return CommonResult.success(captchaService.create());
    }

    @Operation(summary = "该用户名是否需要验证码")
    @GetMapping("/captcha-required")
    public CommonResult<Boolean> captchaRequired(@RequestParam(required = false) String username) {
        return CommonResult.success(authService.captchaRequired(username));
    }

    @Operation(summary = "密码策略（前端实时校验）")
    @GetMapping("/password-policy")
    public CommonResult<PasswordPolicy> passwordPolicy() {
        return CommonResult.success(passwordPolicy.policy());
    }
}
