package com.erp.framework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Prometheus 指标端点 {@code /actuator/prometheus} 的访问令牌：请求头 {@code Authorization: Bearer <erp.monitoring.metrics-token>}。
 * 未配置令牌时端点关闭（返回 404），避免非 Docker 部署时指标对外暴露。
 */
public class MetricsTokenFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";

    private final byte[] token;

    public MetricsTokenFilter(String token) {
        this.token = StringUtils.hasText(token) ? token.trim().getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (token == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String header = request.getHeader("Authorization");
        String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        if (!MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
