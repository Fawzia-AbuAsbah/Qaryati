
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;
import com.qaryati.dataquality.repository.DataConflictRepository;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataConflictControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DataConflictRepository conflictRepository;

    private Long lastCreatedUserId;

    private String tokenForRole(String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseGet(() -> roleRepository.save(
                        new Role(roleCode, "Test role")
                ));

        String username = "conflict_" +
                UUID.randomUUID().toString().substring(0, 8);

        User user = new User(
                username,
                username + "@example.com",
                "test-password-hash",
                "Test User"
        );

        user.addRole(role);
        userRepository.saveAndFlush(user);
        lastCreatedUserId = user.getId();

        return jwtService.generateToken(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of()
                )
        );
    }

    private Long createTestVillage() {
        long governorateId = ThreadLocalRandom.current()
                .nextLong(1_000_000_000L, 2_000_000_000L);

        long villageId = ThreadLocalRandom.current()
                .nextLong(2_000_000_000L, 3_000_000_000L);

        jdbcTemplate.update(
                """
                INSERT INTO public.governorates (id, name_ar)
                VALUES (?, ?)
                """,
                governorateId,
                "Test Governorate " + governorateId
        );

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

        return villageId;
    }

    private String createRequestBody(Long villageId) {
        return """
                {
                  "entityType": "VILLAGE",
                  "entityId": %d,
                  "fieldName": "population",
                  "conflictingValues": [
                    {"source": "Source A", "value": 3500},
                    {"source": "Source B", "value": 4200}
                  ]
                }
                """.formatted(villageId);
    }

    private Long createConflict(String token, Long villageId)
            throws Exception {

        String response = mockMvc.perform(
                        post("/api/v1/data-conflicts")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestBody(villageId))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();


        return JsonMapper.builder()
                .build()
                .readTree(response)
                .get("id")
                .asLong();

    }

    @Test
    void anonymousCannotCreateConflict() throws Exception {
        mockMvc.perform(
                        post("/api/v1/data-conflicts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestBody(1L))
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotCreateConflict() throws Exception {
        String token = tokenForRole("VIEWER");

        mockMvc.perform(
                        post("/api/v1/data-conflicts")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestBody(1L))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void editorCanCreateConflict() throws Exception {
        Long villageId = createTestVillage();
        String token = tokenForRole("EDITOR");

        mockMvc.perform(
                        post("/api/v1/data-conflicts")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestBody(villageId))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.entityId").value(villageId))
                .andExpect(jsonPath("$.fieldName").value("population"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.conflictingValues.length()").value(2));
    }

    @Test
    void adminCanCreateConflictAndPersistIt() throws Exception {
        Long villageId = createTestVillage();
        String token = tokenForRole("ADMIN");

        Long conflictId = createConflict(token, villageId);

        DataConflict saved = conflictRepository.findById(conflictId)
                .orElseThrow();

        assertEquals("VILLAGE", saved.getEntityType());
        assertEquals(villageId, saved.getEntityId());
        assertEquals(DataConflictStatus.OPEN, saved.getStatus());
        assertEquals(2, saved.getConflictingValues().size());
    }

    @Test
    void viewerCanReadVillageConflicts() throws Exception {
        Long villageId = createTestVillage();

        createConflict(tokenForRole("ADMIN"), villageId);

        String viewerToken = tokenForRole("VIEWER");

        mockMvc.perform(
                        get("/api/v1/data-conflicts")
                                .header("Authorization", "Bearer " + viewerToken)
                                .param("entityType", "VILLAGE")
                                .param("entityId", villageId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void editorCanFilterConflictsByStatus() throws Exception {
        Long villageId = createTestVillage();
        createConflict(tokenForRole("ADMIN"), villageId);

        mockMvc.perform(
                        get("/api/v1/data-conflicts/status/OPEN")
                                .header("Authorization",
                                        "Bearer " + tokenForRole("EDITOR"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanMoveConflictToReview() throws Exception {
        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");
        Long conflictId = createConflict(adminToken, villageId);

        mockMvc.perform(
                        patch("/api/v1/data-conflicts/{id}/review", conflictId)
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        assertEquals(
                DataConflictStatus.IN_REVIEW,
                conflictRepository.findById(conflictId)
                        .orElseThrow().getStatus()
        );
    }

    @Test
    void adminCanResolveReviewedConflict() throws Exception {
        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");
        Long reviewerId = lastCreatedUserId;
        Long conflictId = createConflict(adminToken, villageId);

        mockMvc.perform(
                        patch("/api/v1/data-conflicts/{id}/review", conflictId)
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        patch("/api/v1/data-conflicts/{id}/resolve", conflictId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "resolutionNotes": "Verified using official source"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedBy").value(reviewerId))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.resolutionNotes")
                        .value("Verified using official source"));

        DataConflict saved = conflictRepository.findById(conflictId)
                .orElseThrow();

        assertEquals(DataConflictStatus.RESOLVED, saved.getStatus());
        assertEquals(reviewerId, saved.getResolvedBy());
        assertNotNull(saved.getResolvedAt());
    }

    @Test
    void cannotResolveOpenConflict() throws Exception {
        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");
        Long conflictId = createConflict(adminToken, villageId);

        mockMvc.perform(
                        patch("/api/v1/data-conflicts/{id}/resolve", conflictId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"resolutionNotes": "Checked"}
                                        """)
                )
                .andExpect(status().isConflict());
    }

    @Test
    void cannotCreateConflictForMissingVillage() throws Exception {
        String adminToken = tokenForRole("ADMIN");

        mockMvc.perform(
                        post("/api/v1/data-conflicts")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestBody(999999999L))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotCreateConflictWithOneValue() throws Exception {
        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");

        String body = """
                {
                  "entityType": "VILLAGE",
                  "entityId": %d,
                  "fieldName": "population",
                  "conflictingValues": [
                    {"source": "Source A", "value": 3500}
                  ]
                }
                """.formatted(villageId);

        mockMvc.perform(
                        post("/api/v1/data-conflicts")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCannotResolveConflict() throws Exception {
        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");
        Long conflictId = createConflict(adminToken, villageId);

        mockMvc.perform(
                        patch("/api/v1/data-conflicts/{id}/resolve", conflictId)
                                .header("Authorization",
                                        "Bearer " + tokenForRole("VIEWER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"resolutionNotes": "Not allowed"}
                                        """)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotReviewMissingConflict() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/data-conflicts/999999999/review")
                                .header("Authorization",
                                        "Bearer " + tokenForRole("ADMIN"))
                )
                .andExpect(status().isNotFound());
    }
}
