package com.qaryati.security.controller;

import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginAuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService tokens;

    private User createUser(boolean enabled) {
        String name = "login_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(name, name + "@example.com", encoder.encode("TestPassword123!"), "Test User");
        user.setEnabled(enabled);
        return users.saveAndFlush(user);
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(request -> { request.setRemoteAddr(UUID.randomUUID().toString()); return request; })
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void loginReturnsUsableJwtWithoutCreatingSession() throws Exception {
        User user = createUser(true);
        var result = login(user.getUsername(), "TestPassword123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        String token = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(user.getUsername()));
    }

    @Test
    void invalidPasswordMissingAndDisabledUsersGetSameGenericError() throws Exception {
        User enabled = createUser(true);
        User disabled = createUser(false);
        login(enabled.getUsername(), "WrongPassword").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
        login("nonexistent_" + UUID.randomUUID(), "WrongPassword").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
        login(disabled.getUsername(), "TestPassword123!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void signedJwtForMissingUserGets401() throws Exception {
        String token = tokens.generateToken(new UsernamePasswordAuthenticationToken("missing_" + UUID.randomUUID(), null, List.of()));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "a.b.c"})
    void malformedJwtGets401(String token) throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
