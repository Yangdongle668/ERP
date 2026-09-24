package com.erp.module.system.service;

import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.security.JwtTokenService;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.TokenClaims;
import com.erp.framework.web.ClientIp;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.controller.vo.AuthVOs.ChangePasswordReq;
import com.erp.module.system.controller.vo.AuthVOs.CurrentUserResp;
import com.erp.module.system.controller.vo.AuthVOs.LoginFailData;
import com.erp.module.system.controller.vo.AuthVOs.LoginReq;
import com.erp.module.system.controller.vo.AuthVOs.LoginResp;
import com.erp.module.system.controller.vo.AuthVOs.ProfileUpdate;
import com.erp.module.system.dal.dataobject.RoleDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.RoleMapper;
import com.erp.module.system.dal.mapper.UserMapper;
import com.erp.module.system.dal.mapper.UserRoleMapper;
import com.erp.module.system.service.support.SystemCaches;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 登录、刷新、退出、当前用户、个人中心、修改密码（01-13）。 */
@Slf4j
@Service
public class AuthService {

    /** 用户不存在时用于比较的哈希，使响应时间与密码错误时一致，避免通过耗时探测账号是否存在。 */
    private static final String TIMING_DUMMY_HASH = "$2a$10$VysrErJhzMNsXPSa7vV3T.leKLUbc41X3gqoy/G/dICIFkOJp6y4e";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final ParamApi paramApi;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    private final PasswordPolicyService passwordPolicy;
    private final OrgService orgService;
    private final SystemCaches caches;

    public AuthService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper, PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService, ParamApi paramApi, CaptchaService captchaService, LoginLogService loginLogService,
                       PasswordPolicyService passwordPolicy, OrgService orgService, SystemCaches caches) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.paramApi = paramApi;
        this.captchaService = captchaService;
        this.loginLogService = loginLogService;
        this.passwordPolicy = passwordPolicy;
        this.orgService = orgService;
        this.caches = caches;
    }

    // ==================== 登录 ====================

    /** 该用户名是否需要验证码（连续失败次数 ≥ sys.login.captcha-after-fails；参数为 0 表示始终需要） */
    public boolean captchaRequired(String username) {
        int threshold = paramApi.getInt("sys.login.captcha-after-fails");
        if (threshold <= 0) return true;
        UserDO user = StringUtils.hasText(username) ? userMapper.selectByUsername(username.trim()) : null;
        int fails = user != null ? user.getFailCount() : captchaService.unknownUserFails(username);
        return fails >= threshold;
    }

    /**
     * 登录（01-13 第 4 节流程）。不使用 @Transactional：失败计数必须在抛出异常后依然保留。
     * 用户不存在与密码错误返回相同提示，避免暴露账号是否存在。
     */
    public LoginResp login(LoginReq req, HttpServletRequest request) {
        String username = req.username().trim().toLowerCase();
        if (captchaRequired(username)) {
            if (!StringUtils.hasText(req.captchaId())) {
                loginLogService.record(username, null, null, LoginLogService.LOGIN, "CAPTCHA_ERROR", request);
                throw new BizException(SystemErrorCodes.AUTH_CAPTCHA_ERROR).withData(new LoginFailData(true));
            }
            try {
                captchaService.verify(req.captchaId(), req.captchaCode());
            } catch (BizException e) {
                loginLogService.record(username, null, null, LoginLogService.LOGIN, "CAPTCHA_ERROR", request);
                throw e.withData(new LoginFailData(true));
            }
        }
        UserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            passwordEncoder.matches(req.password(), TIMING_DUMMY_HASH);
            captchaService.increaseUnknownUserFails(username);
            loginLogService.record(username, null, null, LoginLogService.LOGIN, "BAD_CREDENTIALS", request);
            throw new BizException(SystemErrorCodes.AUTH_BAD_CREDENTIALS).withData(new LoginFailData(captchaRequired(username)));
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockUntil()).toMinutes() + 1);
            loginLogService.record(username, user.getId(), user.getRealName(), LoginLogService.LOGIN, "LOCKED", request);
            throw BizException.of(SystemErrorCodes.AUTH_USER_LOCKED, minutes).withData(new LoginFailData(captchaRequired(username)));
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int maxFail = paramApi.getInt("sys.login.max-fail-count");
            LocalDateTime lockUntil = now.plusMinutes(paramApi.getInt("sys.login.lock-minutes"));
            userMapper.increaseFailCount(user.getId(), maxFail, lockUntil);
            boolean locked = user.getFailCount() + 1 >= maxFail;
            loginLogService.record(username, user.getId(), user.getRealName(), LoginLogService.LOGIN, locked ? "LOCKED" : "BAD_CREDENTIALS", request);
            throw new BizException(SystemErrorCodes.AUTH_BAD_CREDENTIALS).withData(new LoginFailData(captchaRequired(username)));
        }
        if (user.getStatus() != EnableStatus.ENABLED) {
            loginLogService.record(username, user.getId(), user.getRealName(), LoginLogService.LOGIN, "DISABLED", request);
            throw new BizException(SystemErrorCodes.AUTH_USER_DISABLED);
        }
        userMapper.markLoginSuccess(user.getId(), now, ClientIp.of(request));
        boolean expired = passwordPolicy.isExpired(user);
        loginLogService.record(username, user.getId(), user.getRealName(), LoginLogService.LOGIN, expired ? "EXPIRED" : "SUCCESS", request);
        return issueTokens(user.getId(), user.getTokenVersion(), Boolean.TRUE.equals(user.getMustChangePassword()), expired);
    }

    public LoginResp refresh(String refreshToken, HttpServletRequest request) {
        TokenClaims claims = tokenService.parseRefreshToken(refreshToken).orElse(null);
        UserDO user = claims == null ? null : userMapper.selectById(claims.userId());
        if (user == null || user.getStatus() != EnableStatus.ENABLED || user.getTokenVersion() != claims.tokenVersion()) {
            loginLogService.record(user == null ? "" : user.getUsername(), user == null ? null : user.getId(),
                    user == null ? null : user.getRealName(), LoginLogService.REFRESH, "EXPIRED", request);
            throw new BizException(SystemErrorCodes.AUTH_REFRESH_TOKEN_INVALID);
        }
        return issueTokens(user.getId(), user.getTokenVersion(), Boolean.TRUE.equals(user.getMustChangePassword()), passwordPolicy.isExpired(user));
    }

    public void logout(LoginUser user, HttpServletRequest request) {
        if (user != null) loginLogService.record(user.username(), user.id(), user.realName(), LoginLogService.LOGOUT, "SUCCESS", request);
    }

    private LoginResp issueTokens(Long userId, int tokenVersion, boolean mustChange, boolean expired) {
        Duration ttl = Duration.ofMinutes(paramApi.getInt("sys.session.access-token-minutes"));
        return new LoginResp(tokenService.createAccessToken(userId, tokenVersion, ttl), tokenService.createRefreshToken(userId, tokenVersion),
                ttl.toSeconds(), mustChange, expired);
    }

    // ==================== 当前用户 ====================

    public CurrentUserResp me(LoginUser login) {
        UserDO u = userMapper.selectById(login.id());
        List<Long> roleIds = userRoleMapper.selectRoleIds(u.getId());
        List<String> roleNames = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream().map(RoleDO::getName).toList();
        return new CurrentUserResp(u.getId(), u.getUsername(), u.getRealName(), u.getEmployeeNo(), u.getOrgId(), orgService.nameOf(u.getOrgId()),
                u.getDeptId(), orgService.nameOf(u.getDeptId()), u.getPosition(), u.getMobile(), u.getEmail(), u.getGender(),
                u.getLanguage(), u.getAvatarFileId(), roleNames, login.permissions().stream().sorted().toList(),
                Boolean.TRUE.equals(u.getAdmin()), Boolean.TRUE.equals(u.getMustChangePassword()), passwordPolicy.isExpired(u));
    }

    // ==================== 个人中心 ====================

    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, ProfileUpdate req) {
        UserDO u = userMapper.selectById(userId);
        String mobile = StringUtils.hasText(req.mobile()) ? req.mobile().trim() : null;
        if (mobile != null) {
            UserDO other = userMapper.selectByMobile(mobile);
            if (other != null && !other.getId().equals(userId)) throw BizException.of(SystemErrorCodes.USER_MOBILE_DUPLICATE, other.getRealName());
        }
        u.setMobile(mobile);
        u.setEmail(StringUtils.hasText(req.email()) ? req.email().trim() : null);
        if (req.language() != null) u.setLanguage(req.language());
        if (req.avatarFileId() != null) u.setAvatarFileId(req.avatarFileId());
        userMapper.updateByIdOrFail(u);
        caches.evictLoginUser(userId);
    }

    /** 修改密码：其他会话全部失效（令牌版本 +1），为当前会话重新签发令牌 */
    @Transactional(rollbackFor = Exception.class)
    public LoginResp changePassword(Long userId, ChangePasswordReq req) {
        UserDO u = userMapper.selectById(userId);
        if (!passwordEncoder.matches(req.oldPassword(), u.getPasswordHash())) throw new BizException(SystemErrorCodes.AUTH_OLD_PASSWORD_WRONG);
        passwordPolicy.check(req.newPassword(), u.getUsername());
        passwordPolicy.checkHistory(u, req.newPassword());
        passwordPolicy.remember(u.getId(), u.getPasswordHash());
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setMustChangePassword(false);
        u.setPasswordChangedAt(LocalDateTime.now());
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        userMapper.updateByIdOrFail(u);
        caches.evictLoginUser(userId);
        return issueTokens(u.getId(), u.getTokenVersion(), false, false);
    }

    /** 当前用户是否拥有权限集合中的任一（供需要细粒度判断的服务使用） */
    static boolean hasAny(Set<String> permissions, String... codes) {
        if (permissions.contains(LoginUser.ALL_PERMISSION)) return true;
        for (String c : codes) if (permissions.contains(c)) return true;
        return false;
    }
}
