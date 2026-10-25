package com.qaryati.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevCsrfController.class)
@ActiveProfiles("csrf-inactive")
class DevCsrfControllerInactiveProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    @WithMockUser
    void endpointAndControllerAreAbsentWithoutLocalCsrfProfile() throws Exception {
        assertTrue(context.getBeansOfType(DevCsrfController.class).isEmpty());

        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticationIsStillRequiredWhenProfileIsInactive() throws Exception {
        mockMvc.perform(get("/api/v1/csrf").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}