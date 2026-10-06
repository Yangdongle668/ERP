package com.erp.framework.maintenance;

import com.erp.common.result.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 维护模式下拒绝接口请求（HTTP 503），只放行恢复进度查询与健康检查。在安全过滤器之前执行。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MaintenanceFilter extends OncePerRequestFilter {

    /** 维护期间仍可访问的路径前缀 */
    static final List<String> ALLOWED = List.of("/api/backup/restore/status", "/actuator/health");

    private final ObjectMapper objectMapper;

    public MaintenanceFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MaintenanceMode.State s = MaintenanceMode.state();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (s == null || !path.startsWith("/api/") || ALLOWED.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(CommonResult.error(503, "系统维护中：" + s.reason() + "，请稍后再试")));
    }
}
