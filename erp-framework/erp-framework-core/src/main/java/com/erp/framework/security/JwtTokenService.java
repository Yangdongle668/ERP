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

/** JWT 签发与解析。token 中只放用户 ID 和类型，权限每次从 {@link LoginUserLoader} 获取，保证权限变更即时生效。 */
@Component
public class JwtTokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        return create(userId, TYPE_ACCESS, properties.getAccessTokenTtl());
    }

    public String createRefreshToken(Long userId) {
        return create(userId, TYPE_REFRESH, properties.getRefreshTokenTtl());
    }

    public Optional<Long> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public Optional<Long> parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public Duration getAccessTokenTtl() {
        return properties.getAccessTokenTtl();
    }

    private String create(Long userId, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Optional<Long> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
