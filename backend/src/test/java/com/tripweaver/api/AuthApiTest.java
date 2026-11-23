package com.tripweaver.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    static String unique(String p) {
        return p + UUID.randomUUID().toString().substring(0, 8);
    }

    ResultActions postJson(String path, String body, MockHttpSession s) throws Exception {
        return mvc.perform(
                post(path).session(s).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registerLoginMeLogout() throws Exception {
        String u = unique("asha");
        MockHttpSession s = new MockHttpSession();
        postJson(
                        "/api/v1/auth/register",
                        "{\"username\":\"" + u + "\",\"password\":\"goa-trip-2026\",\"displayName\":\"Asha\"}",
                        s)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(u))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Asha"));
        mvc.perform(post("/api/v1/auth/logout").session(s)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        MockHttpSession s2 = new MockHttpSession();
        postJson("/api/v1/auth/login", "{\"username\":\"" + u + "\",\"password\":\"goa-trip-2026\"}", s2)
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(s2))
                .andExpect(jsonPath("$.username").value(u));
    }

    @Test
    void passwordsAreHashedAndBadLoginsRejected() throws Exception {
        String u = unique("ravi");
        postJson(
                        "/api/v1/auth/register",
                        "{\"username\":\"" + u + "\",\"password\":\"goa-trip-2026\"}",
                        new MockHttpSession())
                .andExpect(status().isCreated());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, u);
        org.junit.jupiter.api.Assertions.assertTrue(hash.startsWith("$2"), "BCrypt hash");
        postJson(
                        "/api/v1/auth/login",
                        "{\"username\":\"" + u + "\",\"password\":\"wrong-password\"}",
                        new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BAD_CREDENTIALS"));
        postJson(
                        "/api/v1/auth/login",
                        "{\"username\":\"nobody-" + u + "\",\"password\":\"wrong-password\"}",
                        new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BAD_CREDENTIALS"));
    }

    @Test
    void registrationIsValidated() throws Exception {
        String u = unique("meera");
        postJson(
                        "/api/v1/auth/register",
                        "{\"username\":\"" + u + "\",\"password\":\"goa-trip-2026\"}",
                        new MockHttpSession())
                .andExpect(status().isCreated());
        postJson(
                        "/api/v1/auth/register",
                        "{\"username\":\"" + u + "\",\"password\":\"goa-trip-2026\"}",
                        new MockHttpSession())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
        postJson(
                        "/api/v1/auth/register",
                        "{\"username\":\"" + unique("x") + "\",\"password\":\"short\"}",
                        new MockHttpSession())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_PASSWORD"));
        postJson(
                        "/api/v1/auth/register",
                        "{\"username\":\"a b\",\"password\":\"goa-trip-2026\"}",
                        new MockHttpSession())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_USERNAME"));
    }

    @Test
    void tripsNeedALogin() throws Exception {
        mvc.perform(get("/api/v1/trips"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}
