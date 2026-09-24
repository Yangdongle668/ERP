package com.erp.framework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 从 Authorization: Bearer 头解析登录用户。解析失败不报错，交给后续鉴权返回 401。 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenService tokenService;
    private final LoginUserLoader loginUserLoader;

    public JwtAuthenticationFilter(JwtTokenService tokenService, LoginUserLoader loginUserLoader) {
        this.tokenService = tokenService;
        this.loginUserLoader = loginUserLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            tokenService.parseAccessToken(header.substring(BEARER.length()))
                    .flatMap(loginUserLoader::load)
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user, null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
