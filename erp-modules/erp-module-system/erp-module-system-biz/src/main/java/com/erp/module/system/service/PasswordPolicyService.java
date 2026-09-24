package com.erp.module.system.service;

import com.erp.common.exception.BizException;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.controller.vo.AuthVOs.PasswordPolicy;
import com.erp.module.system.dal.dataobject.PasswordHistoryDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.PasswordHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/** 密码策略（01-13 SYS-AUT-R06~R08，参数见 01-10）与历史密码。 */
@Service
public class PasswordPolicyService {

    private static final int MAX_LENGTH = 64;
    private static final int KEEP_HISTORY = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%&*";

    private final ParamApi paramApi;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryMapper historyMapper;

    public PasswordPolicyService(ParamApi paramApi, PasswordEncoder passwordEncoder, PasswordHistoryMapper historyMapper) {
        this.paramApi = paramApi;
        this.passwordEncoder = passwordEncoder;
        this.historyMapper = historyMapper;
    }

    public PasswordPolicy policy() {
        return new PasswordPolicy(paramApi.getInt("sys.password.min-length"), paramApi.getString("sys.password.complexity"),
                paramApi.getInt("sys.password.history-count"));
    }

    /** 校验长度、复杂度、不含用户名 */
    public void check(String password, String username) {
        PasswordPolicy p = policy();
        if (password == null || password.length() < p.minLength()) throw BizException.of(SystemErrorCodes.AUTH_PASSWORD_TOO_SHORT, p.minLength());
        if (password.length() > MAX_LENGTH) throw new BizException(SystemErrorCodes.AUTH_PASSWORD_TOO_LONG);
        if ("LETTER_DIGIT".equals(p.complexity()) && !(password.matches(".*[A-Za-z].*") && password.matches(".*\\d.*"))) {
            throw new BizException(SystemErrorCodes.AUTH_PASSWORD_LETTER_DIGIT);
        }
        if ("STRONG".equals(p.complexity())) {
            int kinds = (password.matches(".*[A-Z].*") ? 1 : 0) + (password.matches(".*[a-z].*") ? 1 : 0)
                    + (password.matches(".*\\d.*") ? 1 : 0) + (password.matches(".*[^A-Za-z0-9].*") ? 1 : 0);
            if (kinds < 3) throw new BizException(SystemErrorCodes.AUTH_PASSWORD_STRONG);
        }
        if (username != null && !username.isEmpty() && password.toLowerCase().contains(username.toLowerCase())) {
            throw new BizException(SystemErrorCodes.AUTH_PASSWORD_CONTAINS_USERNAME);
        }
    }

    /** SYS-AUT-R07：不能与最近 N 次密码相同（含当前密码） */
    public void checkHistory(UserDO user, String newPassword) {
        int n = paramApi.getInt("sys.password.history-count");
        if (n <= 0) return;
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) throw BizException.of(SystemErrorCodes.AUTH_PASSWORD_REUSED, n);
        List<PasswordHistoryDO> recent = historyMapper.selectList(new LambdaQueryWrapper<PasswordHistoryDO>()
                .eq(PasswordHistoryDO::getUserId, user.getId()).orderByDesc(PasswordHistoryDO::getCreatedAt).last("LIMIT " + n));
        for (PasswordHistoryDO h : recent) {
            if (passwordEncoder.matches(newPassword, h.getPasswordHash())) throw BizException.of(SystemErrorCodes.AUTH_PASSWORD_REUSED, n);
        }
    }

    /** 记录历史密码，每个用户只保留最近 10 条 */
    public void remember(Long userId, String passwordHash) {
        PasswordHistoryDO h = new PasswordHistoryDO();
        h.setUserId(userId);
        h.setPasswordHash(passwordHash);
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);
        List<PasswordHistoryDO> all = historyMapper.selectList(new LambdaQueryWrapper<PasswordHistoryDO>()
                .eq(PasswordHistoryDO::getUserId, userId).orderByDesc(PasswordHistoryDO::getCreatedAt).orderByDesc(PasswordHistoryDO::getId));
        for (int i = KEEP_HISTORY; i < all.size(); i++) historyMapper.deleteById(all.get(i).getId());
    }

    /** 密码是否过期（sys.password.expire-days，0 表示永不过期） */
    public boolean isExpired(UserDO user) {
        int days = paramApi.getInt("sys.password.expire-days");
        if (days <= 0 || Boolean.TRUE.equals(user.getAdmin()) && user.getPasswordChangedAt() == null) return false;
        LocalDateTime changed = user.getPasswordChangedAt() == null ? user.getCreatedAt() : user.getPasswordChangedAt();
        return changed != null && changed.plusDays(days).isBefore(LocalDateTime.now());
    }

    /** 生成满足当前策略的随机密码（12 位，含大小写、数字、特殊字符，不含易混淆字符） */
    public String random(String username) {
        int len = Math.max(12, policy().minLength());
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder();
            sb.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(SPECIAL));
            String all = UPPER + LOWER + DIGITS;
            while (sb.length() < len) sb.append(pick(all));
            String pwd = shuffle(sb.toString());
            try {
                check(pwd, username);
                return pwd;
            } catch (BizException ignored) {
                // 极少数情况下包含用户名，重试
            }
        }
        throw new IllegalStateException("无法生成随机密码");
    }

    private static char pick(String s) {
        return s.charAt(RANDOM.nextInt(s.length()));
    }

    private static String shuffle(String s) {
        char[] c = s.toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }
}
