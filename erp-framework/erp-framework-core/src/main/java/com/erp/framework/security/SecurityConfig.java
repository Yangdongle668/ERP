package com.erp.framework.security;

import com.erp.common.exception.ErrorCode;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 无状态 JWT 安全配置。
 *
 * <p>除白名单外所有 /api/** 都要求登录；具体权限用 {@code @PreAuthorize("@ss.has('xxx')")} 声明。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/system/auth/login",
            "/api/system/auth/refresh",
            "/api/system/auth/captcha",
            "/api/system/auth/captcha-required",
            "/api/system/auth/password-policy",
            "/api/system/params/public",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenService tokenService,
                                                   LoginUserLoader loginUserLoader, ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, resp, ex) ->
                                write(resp, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                        CommonResult.error(req.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR) instanceof ErrorCode code
                                                ? code : GlobalErrorCodes.UNAUTHORIZED)))
                        .accessDeniedHandler((req, resp, ex) ->
                                write(resp, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        CommonResult.error(GlobalErrorCodes.FORBIDDEN))))
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, loginUserLoader, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void write(HttpServletResponse resp, ObjectMapper mapper, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(resp.getOutputStream(), body);
    }
}
