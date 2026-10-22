
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.qaryati.dataquality.repository.AuditLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SourceControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private String tokenForRole(String roleCode) {

        Role role = roleRepository.findByCode(roleCode)
                .orElseGet(() -> roleRepository.save(
                        new Role(roleCode, "Test role")
                ));

        String username = "test_" +
                UUID.randomUUID().toString().substring(0, 8);

        User user = new User(
                username,
                username + "@example.com",
                "test-password-hash",
                "Test User"
        );

        user.addRole(role);
        userRepository.saveAndFlush(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        username, null, List.of()
                );

        return jwtService.generateToken(authentication);
    }

    @Test
    void viewerCannotCreateSource() throws Exception {

        String token = tokenForRole("VIEWER");

        mvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Village History",
                                  "sourceType": "BOOK"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorCanCreateSource() throws Exception {

        String token = tokenForRole("EDITOR");

        mvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Village History",
                                  "sourceType": "BOOK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title")
                        .value("Village History"))
                .andExpect(jsonPath("$.sourceType")
                        .value("BOOK"));
    }

    @Test
    void unauthenticatedUserCannotCreateSource() throws Exception {

        mvc.perform(post("/api/v1/sources")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Village History",
                                  "sourceType": "BOOK"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadSources() throws Exception {

        String token = tokenForRole("VIEWER");

        mvc.perform(get("/api/v1/sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenSourceDoesNotExist() throws Exception {

        String token = tokenForRole("VIEWER");

        mvc.perform(get("/api/v1/sources/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Source not found"));
    }

    @Test
    void shouldRejectSourceWithoutTitle() throws Exception {

        String token = tokenForRole("EDITOR");

        mvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                            {
                              "title": "",
                              "sourceType": "BOOK"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid request data"));
    }


    @Test
    void shouldRejectSourceWithoutType() throws Exception {

        String token = tokenForRole("EDITOR");

        mvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                            {
                              "title": "Village History",
                              "sourceType": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid request body or source type"));
    }


    @Test
    void shouldRejectInvalidSourceType() throws Exception {

        String token = tokenForRole("EDITOR");

        mvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                            {
                              "title": "Village History",
                              "sourceType": "PIZZA"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid request body or source type"));
    }

    @Test
    void creatingSourceAutomaticallyCreatesAuditLog() throws Exception {

        String token = tokenForRole("EDITOR");

        mvc.perform(
                        post("/api/v1/sources")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("""
                                    {
                                        "title": "Audit Test Source",
                                        "sourceType": "BOOK"
                                    }
                                    """)
                )
                .andExpect(status().isCreated());

        var sources = sourceRepository
                .findByTitleContainingIgnoreCase("Audit Test Source");

        assertEquals(1, sources.size());

        var source = sources.get(0);

        var logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        "SOURCE",
                        source.getId()
                );

        assertEquals(1, logs.size());

        var log = logs.get(0);

        assertNotNull(log.getId());
        assertNotNull(log.getActorUserId());

        assertEquals("SOURCE", log.getEntityType());
        assertEquals(source.getId(), log.getEntityId());
        assertEquals("CREATE", log.getAction());

        assertEquals(
                "Audit Test Source",
                log.getAfterData().get("title")
        );

        assertEquals(
                "BOOK",
                log.getAfterData().get("sourceType")
        );

        var actor = userRepository.findById(log.getActorUserId())
                .orElseThrow();

        assertEquals("EDITOR", actor.getRoles()
                .iterator()
                .next()
                .getCode());
    }

}
