package com.qaryati.security.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginRateLimitIntegrationTest {

    @Test
    void malformedRequestsCountAndForwardingHeadersCannotBypassLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> { request.setRemoteAddr("192.0.2.111"); return request; })
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .header("Forwarded", "for=198.51.100." + i)
                            .contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.111"); return request; })
                        .header("X-Forwarded-For", "198.51.100.99")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "60"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn429AfterTooManyLoginAttempts() throws Exception {

        String requestBody = """
                {
                    "username": "unknown_user",
                    "password": "WrongPassword123!"
                }
                """;

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(
                    post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
            ).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        ).andExpect(status().isTooManyRequests());
    }
}
