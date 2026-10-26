
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
    private com.qaryati.dataquality.service.SourceService sourceService;

    @Autowired
    private com.qaryati.dataquality.service.VillageSourceService villageSourceService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"EDITOR", "ADMIN"})
    void canUpdateSourceWithAccurateAuditAndReadItBack(String role) throws Exception {
        var source = sourceRepository.saveAndFlush(new com.qaryati.dataquality.entity.Source("Before", "BOOK"));
        String token = tokenForRole(role);
        mvc.perform(put("/api/v1/sources/{id}", source.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                            {"title":"After", "sourceType":"WEBSITE", "publisher":"Publisher",
                             "author":"Author", "url":"https://example.com/reference?token=private",
                             "publicationDate":"2020-01-01", "accessedAt":"2026-10-22", "citation":"Citation"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(source.getId()))
                .andExpect(jsonPath("$.publisher").value("Publisher"))
                .andExpect(jsonPath("$.publicationDate").value("2020-01-01"))
                .andExpect(jsonPath("$.citation").value("Citation"));
        mvc.perform(get("/api/v1/sources/{id}", source.getId())
                        .header("Authorization", "Bearer " + tokenForRole("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After"));
        var logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("SOURCE", source.getId());
        assertEquals(1, logs.size());
        var log = logs.getFirst();
        assertEquals("UPDATE", log.getAction());
        assertEquals("Before", log.getBeforeData().get("title"));
        assertEquals("After", log.getAfterData().get("title"));
        assertEquals("BOOK", log.getBeforeData().get("sourceType"));
        assertEquals("WEBSITE", log.getAfterData().get("sourceType"));
        assertEquals(true, log.getAfterData().get("hasUrl"));
        assertEquals(true, log.getAfterData().get("urlChanged"));
        assertEquals(true, log.getAfterData().get("citationChanged"));
        org.junit.jupiter.api.Assertions.assertFalse(log.getAfterData().containsKey("url"));
        org.junit.jupiter.api.Assertions.assertFalse(log.getAfterData().containsKey("citation"));
        assertEquals(jwtSubject(token), log.getActorLabel());
        assertNotNull(log.getOccurredAt());
    }

    private String jwtSubject(String token) {
        return context.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class).decode(token).getSubject();
    }

    @Test
    void putReplacesOptionalFieldsAndPreservesCreationTime() throws Exception {
        var source = new com.qaryati.dataquality.entity.Source("Before", "BOOK");
        source.setPublisher("Old publisher");
        source.setUrl("https://example.com");
        sourceRepository.saveAndFlush(source);
        var created = source.getCreatedAt();
        mvc.perform(put("/api/v1/sources/{id}", source.getId())
                        .header("Authorization", "Bearer " + tokenForRole("EDITOR"))
                        .contentType("application/json").content("""
                            {"title":"Replaced", "sourceType":"BOOK"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher").doesNotExist())
                .andExpect(jsonPath("$.url").doesNotExist());
        assertEquals(created, sourceRepository.findById(source.getId()).orElseThrow().getCreatedAt());
    }

    @Test
    void updateMissingSourceReturns404() throws Exception {
        mvc.perform(put("/api/v1/sources/999999999")
                        .header("Authorization", "Bearer " + tokenForRole("EDITOR"))
                        .contentType("application/json").content("""
                            {"title":"Title", "sourceType":"BOOK"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerAndAnonymousCannotUpdate() throws Exception {
        String body = "{\"title\":\"Title\",\"sourceType\":\"BOOK\"}";
        mvc.perform(put("/api/v1/sources/1").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/sources/1").contentType("application/json").content(body)
                        .header("Authorization", "Bearer " + tokenForRole("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"title\":\" \",\"sourceType\":\"BOOK\"}",
            "{\"title\":\"Title\"}",
            "{\"title\":\"Title\",\"sourceType\":\"OTHER\",\"url\":\"ftp://example.com\"}",
            "{\"title\":\"Title\",\"sourceType\":\"OTHER\",\"url\":\"https://\"}"
    })
    void invalidSourceIsRejectedForCreateAndUpdate(String body) throws Exception {
        String token = tokenForRole("EDITOR");
        for (var request : java.util.List.of(post("/api/v1/sources"), put("/api/v1/sources/1"))) {
            mvc.perform(request.header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void oversizedSourceFieldsAreRejected() throws Exception {
        String token = tokenForRole("EDITOR");
        for (String body : List.of(
                "{\"title\":\"" + "x".repeat(351) + "\",\"sourceType\":\"BOOK\"}",
                "{\"title\":\"Title\",\"sourceType\":\"BOOK\",\"publisher\":\"" + "x".repeat(201) + "\"}",
                "{\"title\":\"Title\",\"sourceType\":\"BOOK\",\"author\":\"" + "x".repeat(201) + "\"}")) {
            for (var request : List.of(post("/api/v1/sources"), put("/api/v1/sources/1"))) {
                mvc.perform(request.header("Authorization", "Bearer " + token)
                                .contentType("application/json").content(body))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "VIEWER")
    void serviceLayerRejectsViewerMutations() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> sourceService.createSource(new com.qaryati.dataquality.entity.Source("Title", "BOOK")));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> sourceService.updateSource(1L, new com.qaryati.dataquality.dto.CreateSourceRequest(
                        "Title", null, null, com.qaryati.dataquality.enums.SourceType.BOOK, null, null, null, null)));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> villageSourceService.linkSourceToVillage(1L, 1L, null));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @org.springframework.security.test.context.support.WithMockUser(username = "missing_audit_actor", roles = "EDITOR")
    void failedAuditRollsBackSourceCreation() {
        String title = "rollback_" + UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> sourceService.createSource(new com.qaryati.dataquality.entity.Source(title, "BOOK")));
        assertEquals(0, sourceRepository.findByTitleContainingIgnoreCase(title).size());
    }

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
