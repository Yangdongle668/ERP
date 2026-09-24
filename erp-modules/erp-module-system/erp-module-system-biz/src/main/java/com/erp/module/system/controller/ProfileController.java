package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.controller.vo.AuthVOs.ChangePasswordReq;
import com.erp.module.system.controller.vo.AuthVOs.LoginResp;
import com.erp.module.system.controller.vo.AuthVOs.ProfileUpdate;
import com.erp.module.system.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人中心（01-13 3.3）：登录即可 */
@Tag(name = "系统管理 - 个人中心")
@RestController
@RequestMapping("/api/system/profile")
public class ProfileController {

    private final AuthService authService;

    public ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @OperLog("修改个人信息")
    @PutMapping
    public CommonResult<Void> update(@Valid @RequestBody ProfileUpdate req) {
        authService.updateProfile(SecurityUtils.getLoginUser().id(), req);
        return CommonResult.success();
    }

    @OperLog("修改密码")
    @PostMapping("/change-password")
    public CommonResult<LoginResp> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        return CommonResult.success(authService.changePassword(SecurityUtils.getLoginUser().id(), req));
    }
}
