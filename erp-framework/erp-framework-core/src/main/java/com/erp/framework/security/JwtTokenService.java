package com.erp.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与解析。令牌中只放用户 ID、类型和令牌版本（tv），权限每次从 {@link LoginUserLoader} 获取，
 * 保证权限变更即时生效；tv 与用户当前版本不一致时令牌失效（修改密码、强制下线）。
 */
@Component
public class JwtTokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 访问令牌，有效期由调用方传入（系统参数 sys.session.access-token-minutes）；为空时取配置 */
    public String createAccessToken(Long userId, int tokenVersion, Duration ttl) {
        return create(userId, tokenVersion, TYPE_ACCESS, ttl == null ? properties.getAccessTokenTtl() : ttl);
    }

    public String createRefreshToken(Long userId, int tokenVersion) {
        return create(userId, tokenVersion, TYPE_REFRESH, properties.getRefreshTokenTtl());
    }

    public Optional<TokenClaims> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public Optional<TokenClaims> parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public Duration getAccessTokenTtl() {
        return properties.getAccessTokenTtl();
    }

    private String create(Long userId, int tokenVersion, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Optional<TokenClaims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            Integer tv = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            return Optional.of(new TokenClaims(Long.valueOf(claims.getSubject()), tv == null ? 0 : tv));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
