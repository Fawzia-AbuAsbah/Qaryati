
package com.qaryati.dataquality.controller;

import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.SourceRepository;
import org.junit.jupiter.api.MediaType;
import com.qaryati.dataquality.entity.VillageSourceId;
import com.qaryati.dataquality.repository.VillageSourceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.ThreadLocalRandom;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.qaryati.dataquality.repository.AuditLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VillageSourceControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VillageSourceRepository villageSourceRepository;
    @Autowired
    private RoleRepository roleRepository;

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
    void viewerCannotLinkSource() throws Exception {

        String token = tokenForRole("VIEWER");

        mvc.perform(post("/api/v1/villages/1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceId": 5,
                                  "claimNote": "Population data"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotLinkSource() throws Exception {

        mvc.perform(post("/api/v1/villages/1/sources")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceId": 5
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadVillageSources() throws Exception {

        String token = tokenForRole("VIEWER");

        mvc.perform(get("/api/v1/villages/999999/sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void editorCannotLinkNegativeSourceId() throws Exception {

        String token = tokenForRole("EDITOR");

        mvc.perform(post("/api/v1/villages/1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceId": -5
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private Long[] createTestVillageAndSource() {

        // Unique IDs for test data
        long governorateId = ThreadLocalRandom.current()
                .nextLong(1_000_000_000L, 2_000_000_000L);

        long villageId = ThreadLocalRandom.current()
                .nextLong(2_000_000_000L, 3_000_000_000L);

        String uniqueName = "Test Governorate " + governorateId;

        // Create test governorate
        jdbcTemplate.update(
                """
                INSERT INTO public.governorates
                    (id, name_ar)
                VALUES (?, ?)
                """,
                governorateId,
                uniqueName
        );

        // Create test village
        jdbcTemplate.update(
                """
                INSERT INTO public.villages
                    (id, governorate_id, name_ar,
                     location_description, status,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                villageId,
                governorateId,
                "Test Village " + villageId,
                "Test location",
                "DRAFT"
        );

        // Create test source
        Source source = sourceRepository.saveAndFlush(
                new Source("Test Census", "CENSUS")
        );

        return new Long[]{villageId, source.getId()};
    }

    @Test
    void editorCanLinkSourceToVillageSuccessfully() throws Exception {

        // Create real test data
        Long[] ids = createTestVillageAndSource();

        Long villageId = ids[0];
        Long sourceId = ids[1];

        // Generate an EDITOR JWT
        String token = tokenForRole("EDITOR");

        // Prepare request body
        String requestBody = """
            {
                "sourceId": %d,
                "claimNote": "Population data from census"
            }
            """.formatted(sourceId);

        // Send POST request and verify response
        mockMvc.perform(post("/api/v1/villages/{villageId}/sources", villageId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villageId").value(villageId))
                .andExpect(jsonPath("$.sourceId").value(sourceId))
                .andExpect(jsonPath("$.claimNote")
                        .value("Population data from census"));
    }

    @Test
    void editorCannotLinkSameSourceTwice() throws Exception {

        // Create real test village and source
        Long[] ids = createTestVillageAndSource();

        Long villageId = ids[0];
        Long sourceId = ids[1];

        // Generate EDITOR token
        String token = tokenForRole("EDITOR");

        String requestBody = """
            {
                "sourceId": %d,
                "claimNote": "Population data from census"
            }
            """.formatted(sourceId);

        // First request: link created successfully
        mockMvc.perform(post("/api/v1/villages/{villageId}/sources", villageId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // Second request: same link must be rejected
        mockMvc.perform(post("/api/v1/villages/{villageId}/sources", villageId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());

        VillageSourceId linkId = new VillageSourceId(
                villageId,
                sourceId
        );

        assertTrue(
                villageSourceRepository.existsById(linkId),
                "Village-Source link should exist in the database"
        );

    }

    @Test
    void linkingSourceToVillageAutomaticallyCreatesAuditLog()
            throws Exception {

        Long[] ids = createTestVillageAndSource();

        Long villageId = ids[0];
        Long sourceId = ids[1];

        String token = tokenForRole("EDITOR");

        String requestBody = """
            {
                "sourceId": %d,
                "claimNote": "Population data from census"
            }
            """.formatted(sourceId);

        mockMvc.perform(
                        post(
                                "/api/v1/villages/{villageId}/sources",
                                villageId
                        )
                                .header("Authorization", "Bearer " + token)
                                .contentType(
                                        org.springframework.http.MediaType
                                                .APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villageId").value(villageId))
                .andExpect(jsonPath("$.sourceId").value(sourceId));

        VillageSourceId linkId = new VillageSourceId(
                villageId,
                sourceId
        );

        assertTrue(villageSourceRepository.existsById(linkId));

        var logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        "VILLAGE_SOURCE",
                        villageId
                );

        assertEquals(1, logs.size());

        var log = logs.get(0);

        assertNotNull(log.getId());
        assertNotNull(log.getActorUserId());

        assertEquals("VILLAGE_SOURCE", log.getEntityType());
        assertEquals(villageId, log.getEntityId());
        assertEquals("CREATE", log.getAction());

        assertEquals(
                villageId.longValue(),
                ((Number) log.getAfterData()
                        .get("villageId")).longValue()
        );

        assertEquals(
                sourceId.longValue(),
                ((Number) log.getAfterData()
                        .get("sourceId")).longValue()
        );

        var actor = userRepository
                .findById(log.getActorUserId())
                .orElseThrow();

        assertTrue(
                actor.getRoles().stream()
                        .anyMatch(role -> "EDITOR".equals(role.getCode()))
        );
    }

}
