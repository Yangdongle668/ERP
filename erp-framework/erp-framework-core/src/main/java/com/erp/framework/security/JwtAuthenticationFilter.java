package com.erp.framework.security;

import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 从 Authorization: Bearer 头解析登录用户。
 * <ul>
 *   <li>令牌无效：不设置登录用户，交给后续鉴权返回 401；</li>
 *   <li>令牌版本（tv）与用户当前版本不一致：视为未登录，401 提示“登录已失效，请重新登录”；</li>
 *   <li>用户需要修改密码：除修改密码、当前用户、退出等接口外一律 403“请先修改密码”。</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    /** 请求属性：认证失败原因，供 AuthenticationEntryPoint 选择提示文字 */
    public static final String AUTH_ERROR_ATTR = "erp.authError";

    /** 需要修改密码时仍可访问的接口 */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/api/system/auth/me",
            "/api/system/auth/logout",
            "/api/system/auth/password-policy",
            "/api/system/profile/change-password");

    private final JwtTokenService tokenService;
    private final LoginUserLoader loginUserLoader;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenService tokenService, LoginUserLoader loginUserLoader, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.loginUserLoader = loginUserLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            Optional<TokenClaims> claims = tokenService.parseAccessToken(header.substring(BEARER.length()));
            Optional<LoginUser> user = claims.flatMap(c -> loginUserLoader.load(c.userId()));
            if (claims.isPresent() && user.isPresent()) {
                LoginUser u = user.get();
                if (u.tokenVersion() != claims.get().tokenVersion()) {
                    request.setAttribute(AUTH_ERROR_ATTR, GlobalErrorCodes.TOKEN_REVOKED);
                } else if (u.mustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
                    writeForbidden(response);
                    return;
                } else {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), CommonResult.error(GlobalErrorCodes.PASSWORD_CHANGE_REQUIRED));
    }
}
