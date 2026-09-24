package com.erp.module.system.service;

import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.security.JwtTokenService;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.controller.vo.LoginRespVO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthService {

    /** 用户不存在时用于比较的哈希，使响应时间与密码错误时一致，避免通过耗时探测账号是否存在。 */
    private static final String TIMING_DUMMY_HASH = "$2a$10$VysrErJhzMNsXPSa7vV3T.leKLUbc41X3gqoy/G/dICIFkOJp6y4e";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final int maxFailCount;
    private final Duration lockDuration;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       @Value("${erp.security.login.max-fail-count:5}") int maxFailCount,
                       @Value("${erp.security.login.lock-duration:15m}") Duration lockDuration) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.maxFailCount = maxFailCount;
        this.lockDuration = lockDuration;
    }

    /**
     * 登录。不使用 @Transactional：失败计数必须在抛出异常后依然保留。
     * 用户不存在与密码错误返回相同提示，避免暴露账号是否存在。
     */
    public LoginRespVO login(String username, String password) {
        UserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            passwordEncoder.matches(password, TIMING_DUMMY_HASH);
            throw new BizException(SystemErrorCodes.AUTH_BAD_CREDENTIALS);
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockUntil()).toMinutes());
            throw BizException.of(SystemErrorCodes.AUTH_USER_LOCKED, minutes);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            userMapper.increaseFailCount(user.getId(), maxFailCount, now.plus(lockDuration));
            log.info("[登录失败] username={}", username);
            throw new BizException(SystemErrorCodes.AUTH_BAD_CREDENTIALS);
        }
        if (user.getStatus() != EnableStatus.ENABLED) {
            throw new BizException(SystemErrorCodes.AUTH_USER_DISABLED);
        }
        userMapper.markLoginSuccess(user.getId(), now);
        return issueTokens(user.getId());
    }

    public LoginRespVO refresh(String refreshToken) {
        Long userId = tokenService.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new BizException(SystemErrorCodes.AUTH_REFRESH_TOKEN_INVALID));
        UserDO user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != EnableStatus.ENABLED) {
            throw new BizException(SystemErrorCodes.AUTH_REFRESH_TOKEN_INVALID);
        }
        return issueTokens(userId);
    }

    private LoginRespVO issueTokens(Long userId) {
        return new LoginRespVO(tokenService.createAccessToken(userId), tokenService.createRefreshToken(userId),
                tokenService.getAccessTokenTtl().toSeconds());
    }
}
