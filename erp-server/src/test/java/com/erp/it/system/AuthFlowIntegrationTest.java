package com.erp.it.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 登录与个人中心验收用例（01-13 SYS-AUT-T01～T07）及用户状态对登录的影响（01-02） */
class AuthFlowIntegrationTest extends SystemTestSupport {

    @Test
    void firstLoginMustChangePassword_T01() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of("system:org:query"));
        String username = "first" + uniq();
        createUser(username, dept, List.of(role), true);

        JsonNode login = ok(loginRaw(username, PASSWORD));
        assertThat(login.at("/mustChangePassword").asBoolean()).isTrue();
        String token = "Bearer " + login.at("/accessToken").asText();

        // 需要改密时只能访问改密等接口
        JsonNode blocked = doGet("/api/system/orgs/tree", token);
        assertThat(blocked.at("/code").asInt()).isEqualTo(1_000_000_004);
        assertThat(ok(doGet("/api/system/auth/me", token)).at("/mustChangePassword").asBoolean()).isTrue();

        JsonNode changed = ok(doPost("/api/system/profile/change-password", token, Map.of("oldPassword", PASSWORD, "newPassword", "NewPass2026")));
        String newToken = "Bearer " + changed.at("/accessToken").asText();
        ok(doGet("/api/system/orgs/tree", newToken));
        // 改密后旧令牌失效
        JsonNode old = doGet("/api/system/auth/me", token);
        assertThat(old.at("/code").asInt()).isEqualTo(1_000_000_005);
    }

    @Test
    void passwordPolicyAndHistory_T04_T05() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "pol" + uniq();
        createUser(username, dept, List.of(role), false);
        String token = login(username, PASSWORD);

        assertError(doPost("/api/system/profile/change-password", token, Map.of("oldPassword", PASSWORD, "newPassword", "12345678")),
                "密码必须同时包含字母和数字");
        assertError(doPost("/api/system/profile/change-password", token, Map.of("oldPassword", PASSWORD, "newPassword", "a1b2")),
                "密码长度不能少于 8 位");
        assertError(doPost("/api/system/profile/change-password", token, Map.of("oldPassword", "wrong", "newPassword", "Abc12345")),
                "原密码不正确");
        assertError(doPost("/api/system/profile/change-password", token, Map.of("oldPassword", PASSWORD, "newPassword", username + "1a")),
                "密码不能包含用户名");
        // 与当前密码相同 → 最近 3 次限制
        assertError(doPost("/api/system/profile/change-password", token, Map.of("oldPassword", PASSWORD, "newPassword", PASSWORD)),
                "新密码不能与最近 3 次使用的密码相同");
        String t2 = "Bearer " + ok(doPost("/api/system/profile/change-password", token,
                Map.of("oldPassword", PASSWORD, "newPassword", "Second2026"))).at("/accessToken").asText();
        // 改回上一次的密码 → 被拒绝
        assertError(doPost("/api/system/profile/change-password", t2, Map.of("oldPassword", "Second2026", "newPassword", PASSWORD)),
                "新密码不能与最近 3 次使用的密码相同");
    }

    @Test
    void captchaThenLock_T02_T03() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "lock" + uniq();
        createUser(username, dept, List.of(role), false);

        for (int i = 0; i < 3; i++) assertError(loginRaw(username, "wrong"), "用户名或密码错误");
        assertThat(ok(doGet("/api/system/auth/captcha-required?username=" + username, null)).asBoolean()).isTrue();
        // 需要验证码但未提供
        JsonNode noCaptcha = loginRaw(username, PASSWORD);
        assertThat(noCaptcha.at("/msg").asText()).isEqualTo("验证码错误");
        assertThat(noCaptcha.at("/data/captchaRequired").asBoolean()).isTrue();
        // 验证码可以获取
        JsonNode captcha = ok(doGet("/api/system/auth/captcha", null));
        assertThat(captcha.at("/image").asText()).startsWith("data:image/png;base64,");
    }

    @Test
    void lockAfterMaxFailures_T03() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "lk" + uniq();
        String id = createUser(username, dept, List.of(role), false);
        // 把验证码阈值调为 10，单独验证锁定
        ok(doPut("/api/system/params", admin, List.of(Map.of("key", "sys.login.captcha-after-fails", "value", "10"))));
        try {
            for (int i = 0; i < 5; i++) loginRaw(username, "wrong");
            JsonNode locked = loginRaw(username, PASSWORD);
            assertThat(locked.at("/msg").asText()).startsWith("密码错误次数过多，账号已锁定，请 ").endsWith(" 分钟后再试");
            ok(doPost("/api/system/users/" + id + "/unlock", admin, null));
            ok(loginRaw(username, PASSWORD));
        } finally {
            ok(doPost("/api/system/params/sys.login.captcha-after-fails/reset", admin, null));
        }
    }

    @Test
    void disableAndKickRevokeTokens_USR_T04() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "dis" + uniq();
        String id = createUser(username, dept, List.of(role), false);
        String token = login(username, PASSWORD);
        ok(doGet("/api/system/auth/me", token));

        ok(doPost("/api/system/users/" + id + "/kick", admin, null));
        assertThat(doGet("/api/system/auth/me", token).at("/code").asInt()).isEqualTo(1_000_000_005);

        String token2 = login(username, PASSWORD);
        ok(doPost("/api/system/users/" + id + "/disable", admin, null));
        assertThat(doGet("/api/system/auth/me", token2).at("/code").asInt()).isEqualTo(401);
        assertError(loginRaw(username, PASSWORD), "账号已停用，请联系管理员");
    }

    @Test
    void resetPasswordRequiresChange_USR_T06() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "rst" + uniq();
        String id = createUser(username, dept, List.of(role), false);
        String token = login(username, PASSWORD);
        JsonNode r = ok(doPost("/api/system/users/" + id + "/reset-password", admin, Map.of("mode", "RANDOM")));
        String newPwd = r.at("/password").asText();
        assertThat(newPwd).hasSizeGreaterThanOrEqualTo(12);
        assertThat(doGet("/api/system/auth/me", token).at("/code").asInt()).isEqualTo(1_000_000_005);
        assertThat(ok(loginRaw(username, newPwd)).at("/mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void profileUpdateAndPublicParams() throws Exception {
        String dept = createDept(HQ, "部门" + uniq());
        String role = createRole("SELF", List.of());
        String username = "pf" + uniq();
        createUser(username, dept, List.of(role), false);
        String token = login(username, PASSWORD);
        String mobile = "139" + String.format("%08d", Math.abs(username.hashCode()) % 100_000_000);
        ok(doPut("/api/system/profile", token, Map.of("mobile", mobile, "email", "a@b.com", "language", "en")));
        JsonNode me = ok(doGet("/api/system/auth/me", token));
        assertThat(me.at("/mobile").asText()).isEqualTo(mobile);
        assertThat(me.at("/language").asText()).isEqualTo("en");
        assertThat(me.at("/deptName").asText()).startsWith("部门");

        JsonNode pub = ok(doGet("/api/system/params/public", null));
        assertThat(pub.at("/systemName").asText()).isNotEmpty();
    }
}
