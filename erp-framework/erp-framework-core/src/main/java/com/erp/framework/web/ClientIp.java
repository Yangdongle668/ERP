package com.erp.framework.web;

import jakarta.servlet.http.HttpServletRequest;

/** 客户端 IP：优先取 X-Forwarded-For 的第一个地址（经过反向代理时），其次 X-Real-IP，最后是连接地址。 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) return limit(first);
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return limit(real.trim());
        return limit(request.getRemoteAddr());
    }

    private static String limit(String ip) {
        return ip != null && ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
