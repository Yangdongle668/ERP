package com.erp.module.system.service;

import com.erp.framework.web.ClientIp;
import com.erp.module.system.dal.dataobject.LoginLogDO;
import com.erp.module.system.dal.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 登录日志（01-11）：登录、登出、刷新失败、锁定。独立事务写入，登录失败抛异常时日志仍保留。 */
@Slf4j
@Service
public class LoginLogService {

    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String REFRESH = "REFRESH";

    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String username, Long userId, String realName, String type, String result, HttpServletRequest request) {
        try {
            LoginLogDO l = new LoginLogDO();
            l.setUsername(truncate(username == null ? "" : username, 64));
            l.setUserId(userId);
            l.setRealName(realName);
            l.setLogType(type);
            l.setResult(result);
            if (request != null) {
                String ua = request.getHeader("User-Agent");
                l.setIp(ClientIp.of(request));
                l.setUserAgent(truncate(ua, 512));
                l.setBrowser(browser(ua));
                l.setOs(os(ua));
            }
            l.setCreatedAt(LocalDateTime.now());
            loginLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("[登录日志] 保存失败 username={}", username, e);
        }
    }

    static String browser(String ua) {
        if (ua == null) return null;
        if (ua.contains("Edg/")) return "Edge " + version(ua, "Edg/");
        if (ua.contains("Chrome/") && !ua.contains("Chromium")) return "Chrome " + version(ua, "Chrome/");
        if (ua.contains("Firefox/")) return "Firefox " + version(ua, "Firefox/");
        if (ua.contains("Safari/") && ua.contains("Version/")) return "Safari " + version(ua, "Version/");
        return truncate(ua.split(" ")[0], 64);
    }

    static String os(String ua) {
        if (ua == null) return null;
        if (ua.contains("Windows NT 10.0")) return "Windows 10/11";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }

    private static String version(String ua, String token) {
        int i = ua.indexOf(token) + token.length();
        int j = i;
        while (j < ua.length() && (Character.isDigit(ua.charAt(j)) || ua.charAt(j) == '.')) j++;
        String v = ua.substring(i, j);
        int dot = v.indexOf('.');
        return dot > 0 ? v.substring(0, dot) : v;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
