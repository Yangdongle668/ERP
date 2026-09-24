package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.controller.vo.CurrentUserRespVO;
import com.erp.module.system.controller.vo.LoginReqVO;
import com.erp.module.system.controller.vo.LoginRespVO;
import com.erp.module.system.controller.vo.RefreshReqVO;
import com.erp.module.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理 - 认证")
@RestController
@RequestMapping("/api/system/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public CommonResult<LoginRespVO> login(@Valid @RequestBody LoginReqVO req) {
        return CommonResult.success(authService.login(req.username(), req.password()));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public CommonResult<LoginRespVO> refresh(@Valid @RequestBody RefreshReqVO req) {
        return CommonResult.success(authService.refresh(req.refreshToken()));
    }

    @Operation(summary = "当前登录用户及权限")
    @GetMapping("/me")
    public CommonResult<CurrentUserRespVO> me() {
        LoginUser user = SecurityUtils.getLoginUser();
        return CommonResult.success(new CurrentUserRespVO(user.id(), user.username(), user.realName(), user.permissions()));
    }
}
