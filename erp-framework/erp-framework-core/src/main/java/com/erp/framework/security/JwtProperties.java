package com.erp.framework.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "erp.security.jwt")
public class JwtProperties {

    /** application.yml 中的开发用默认密钥；生产（mysql）环境使用它时拒绝启动 */
    public static final String DEV_DEFAULT_SECRET = "change-me-please-this-is-a-dev-only-secret-key";

    /** HMAC 密钥，至少 32 个字符。生产环境必须通过环境变量注入，不能使用默认值。 */
    @NotBlank
    @Size(min = 32, message = "JWT 密钥至少 32 个字符")
    private String secret;

    private Duration accessTokenTtl = Duration.ofHours(2);

    private Duration refreshTokenTtl = Duration.ofDays(7);
}
