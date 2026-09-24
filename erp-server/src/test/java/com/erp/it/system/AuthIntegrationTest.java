package com.erp.it.system;

import com.erp.it.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void loginAndGetCurrentUser() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get("/api/system/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.permissions[0]").value("*"))
                .andExpect(header().string("X-Trace-Id", not(emptyString())));
    }

    @Test
    void wrongPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/system/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "nobody", "password", "x"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_001_001_000));
    }

    @Test
    void protectedApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/engineering/materials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void invalidTokenTreatedAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/engineering/materials").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
