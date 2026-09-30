package com.erp.it.system;

import com.erp.framework.security.JwtProperties;
import com.erp.framework.security.JwtTokenService;
import com.erp.framework.security.SecretCipher;
import com.erp.it.AbstractIntegrationTest;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.service.DefaultPasswordGuard;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 安全默认值：敏感参数加密存储、初始管理员默认密码强制修改、生产环境禁止默认 JWT 密钥 */
class SecurityDefaultsIntegrationTest extends AbstractIntegrationTest {

    static final String KEY = "ai.api-key";

    @Autowired
    ParamApi paramApi;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DefaultPasswordGuard passwordGuard;

    private JsonNode param(String admin) throws Exception {
        for (JsonNode p : ok(doGet("/api/system/params?module=bi", admin))) {
            if (KEY.equals(p.at("/key").asText())) return p;
        }
        throw new AssertionError("未找到参数 " + KEY);
    }

    /** 敏感参数：数据库存密文，列表只返回后 4 位，业务读取明文；回传掩码视为未修改 */
    @Test
    void secretParamEncrypted() throws Exception {
        String admin = loginAsAdmin();
        try {
            assertThat(param(admin).at("/valueType").asText()).isEqualTo("SECRET");
            ok(doPut("/api/system/params", admin, List.of(Map.of("key", KEY, "value", "sk-test-abcd1234"))));
            JsonNode p = param(admin);
            assertThat(p.at("/value").asText()).isEqualTo("****1234");
            assertThat(p.at("/defaultValue").asText()).isEmpty();
            String stored = jdbc.queryForObject("SELECT param_value FROM sys_param WHERE param_key = ?", String.class, KEY);
            assertThat(stored).startsWith(SecretCipher.PREFIX).doesNotContain("abcd1234");
            assertThat(paramApi.getString(KEY)).isEqualTo("sk-test-abcd1234");
            // 页面原样回传掩码：不修改
            ok(doPut("/api/system/params", admin, List.of(Map.of("key", KEY, "value", "****1234"))));
            assertThat(paramApi.getString(KEY)).isEqualTo("sk-test-abcd1234");
            // 修改日志不包含明文
            JsonNode diffs = ok(doPut("/api/system/params", admin, List.of(Map.of("key", KEY, "value", "sk-new-5678"))));
            assertThat(diffs.toString()).contains("已修改").doesNotContain("sk-new");
            assertThat(paramApi.getString(KEY)).isEqualTo("sk-new-5678");
        } finally {
            ok(doPost("/api/system/params/" + KEY + "/reset", admin, null));
        }
        assertThat(paramApi.getString(KEY)).isEmpty();
    }

    /** 初始管理员仍使用 admin123 时要求修改密码；修改标记后登录响应 mustChangePassword=true */
    @Test
    void defaultAdminPasswordMustChange() throws Exception {
        try {
            assertThat(passwordGuard.check()).isTrue();
            JsonNode login = loginRaw("admin", "admin123");
            assertThat(login.at("/data/mustChangePassword").asBoolean()).isTrue();
            // 已标记时不重复处理
            assertThat(passwordGuard.check()).isFalse();
        } finally {
            jdbc.update("UPDATE sys_user SET must_change_password = 0 WHERE id = 1");
        }
    }

    /** mysql（生产）环境使用默认 JWT 密钥时拒绝启动；其他环境或自定义密钥正常 */
    @Test
    void defaultJwtSecretRejectedInProduction() {
        JwtProperties dev = new JwtProperties();
        dev.setSecret(JwtProperties.DEV_DEFAULT_SECRET);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("mysql");
        assertThatThrownBy(() -> new JwtTokenService(dev, prod)).hasMessageContaining("ERP_JWT_SECRET");
        new JwtTokenService(dev, new MockEnvironment());
        JwtProperties custom = new JwtProperties();
        custom.setSecret("a-random-secret-with-more-than-32-characters");
        new JwtTokenService(custom, prod);
    }
}
