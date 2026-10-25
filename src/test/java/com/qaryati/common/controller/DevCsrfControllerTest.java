package com.qaryati.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DevCsrfController.class)
@ActiveProfiles("local-csrf")
@Import(DevCsrfControllerTest.WriteProbeController.class)
class DevCsrfControllerTest {

    private static final String TOKEN_PATH = "/api/v1/csrf";
    private static final String WRITE_PATH = "/csrf-test/write";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void authenticatedRequestReturnsTokenAndPreventsCaching() throws Exception {
        MvcResult result = mockMvc.perform(get(TOKEN_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        assertNotNull(result.getRequest().getSession(false));
    }

    @Test
    void unauthenticatedRequestCannotObtainToken() throws Exception {
        mockMvc.perform(get(TOKEN_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @WithMockUser
    void authenticatedPostWithoutTokenIsForbidden() throws Exception {
        mockMvc.perform(post(WRITE_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void authenticatedPostWithInvalidTokenIsForbidden() throws Exception {
        mockMvc.perform(post(WRITE_PATH)
                        .header("X-CSRF-TOKEN", "invalid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void returnedTokenAllowsPostWithMatchingSession() throws Exception {
        MvcResult result = mockMvc.perform(get(TOKEN_PATH))
                .andExpect(status().isOk())
                .andReturn();
        CsrfToken token = (CsrfToken) result.getRequest()
                .getAttribute(CsrfToken.class.getName());
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(token);
        assertNotNull(session);
        // Verify the value sent below is exactly the value exposed in the JSON.
        jsonPath("$.token").value(token.getToken()).match(result);

        mockMvc.perform(post(WRITE_PATH).session(session)
                        .header(token.getHeaderName(), token.getToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("accepted"));
    }

    @Test
    @WithMockUser
    void returnedTokenCannotBeUsedWithDifferentSession() throws Exception {
        MvcResult result = mockMvc.perform(get(TOKEN_PATH))
                .andExpect(status().isOk())
                .andReturn();
        CsrfToken token = (CsrfToken) result.getRequest()
                .getAttribute(CsrfToken.class.getName());
        assertNotNull(token);

        mockMvc.perform(post(WRITE_PATH).session(new MockHttpSession())
                        .header(token.getHeaderName(), token.getToken()))
                .andExpect(status().isForbidden());
    }

    // A test-only write endpoint exercises the real security filters without a database.
    @RestController
    static class WriteProbeController {

        @PostMapping(WRITE_PATH)
        String write() {
            return "accepted";
        }
    }
}