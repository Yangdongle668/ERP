package com.erp.module.system.service;

import com.erp.framework.maintenance.DataRestoredEvent;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始管理员默认密码保护：启动时如果初始管理员（admin）的密码仍是 {@code admin123}，把它标记为“下次登录必须修改密码”，
 * 登录后只能访问修改密码等少数接口。修改过密码后不再触发。可用 {@code erp.security.force-default-password-change=false} 关闭（仅测试环境）。
 */
@Slf4j
@Component
public class DefaultPasswordGuard {

    static final Long INITIAL_ADMIN_ID = 1L;
    static final String DEFAULT_PASSWORD = "admin123";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public DefaultPasswordGuard(UserMapper userMapper, PasswordEncoder passwordEncoder,
                                @Value("${erp.security.force-default-password-change:true}") boolean enabled) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @EventListener({ApplicationReadyEvent.class, DataRestoredEvent.class})
    public void onReady() {
        if (enabled) check();
    }

    /** @return 是否标记了必须修改密码 */
    public boolean check() {
        UserDO admin = userMapper.selectById(INITIAL_ADMIN_ID);
        if (admin == null || admin.getPasswordHash() == null || Boolean.TRUE.equals(admin.getMustChangePassword())) return false;
        if (!passwordEncoder.matches(DEFAULT_PASSWORD, admin.getPasswordHash())) return false;
        admin.setMustChangePassword(true);
        userMapper.updateByIdOrFail(admin);
        log.warn("初始管理员 {} 仍在使用默认密码，已要求下次登录时修改密码", admin.getUsername());
        return true;
    }
}
