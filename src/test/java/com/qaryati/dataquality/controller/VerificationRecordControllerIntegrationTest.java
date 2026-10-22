
package com.qaryati.dataquality.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.ThreadLocalRandom;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.qaryati.dataquality.repository.VerificationRecordRepository;

import com.qaryati.dataquality.repository.AuditLogRepository;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VerificationRecordControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private VerificationRecordRepository verificationRecordRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Long lastCreatedUserId;

    @Test
    void unauthenticatedUserCannotCreateVerification() throws Exception {

        String requestBody = """
                {
                    "entityType": "VILLAGE",
                    "entityId": 1,
                    "decision": "VERIFIED",
                    "reason": "Information checked"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/verifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized());
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

        lastCreatedUserId = user.getId();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of()
                );

        return jwtService.generateToken(authentication);
    }

    @Test
    void viewerCannotCreateVerification() throws Exception {

        // Generate a valid JWT for a VIEWER
        String token = tokenForRole("VIEWER");

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": 1,
                "decision": "VERIFIED",
                "reason": "Information checked"
            }
            """;

        // VIEWER must not be allowed to create verifications
        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden());
    }

    private Long createTestVillage() {

        long governorateId = ThreadLocalRandom.current()
                .nextLong(1_000_000_000L, 2_000_000_000L);

        long villageId = ThreadLocalRandom.current()
                .nextLong(2_000_000_000L, 3_000_000_000L);

        // Create test governorate
        jdbcTemplate.update(
                """
                INSERT INTO public.governorates
                    (id, name_ar)
                VALUES (?, ?)
                """,
                governorateId,
                "Test Governorate " + governorateId
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

        return villageId;
    }

    @Test
    void adminCanCreateVerificationSuccessfully() throws Exception {

        // Create a real test village
        Long villageId = createTestVillage();

        // Generate an ADMIN JWT
        String token = tokenForRole("ADMIN");

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": %d,
                "decision": "VERIFIED",
                "reason": "Village information checked"
            }
            """.formatted(villageId);

        // Create verification through the API
        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.entityType").value("VILLAGE"))
                .andExpect(jsonPath("$.entityId").value(villageId))
                .andExpect(jsonPath("$.decision").value("VERIFIED"))
                .andExpect(jsonPath("$.reason")
                        .value("Village information checked"))
                .andExpect(jsonPath("$.reviewerUserId").isNumber());

        // Verify persistence in the test database
        var records = verificationRecordRepository
                .findByEntityTypeAndEntityIdOrderByReviewedAtDesc(
                        "VILLAGE",
                        villageId
                );

        assertEquals(1, records.size());

        var record = records.get(0);

        assertEquals(
                lastCreatedUserId,
                record.getReviewerUserId(),
                "Reviewer ID must match the authenticated ADMIN user"
        );

        // Reviewer ID must match the authenticated ADMIN
        String username = jwtService.generateToken(
                new UsernamePasswordAuthenticationToken(
                        "unused", null, List.of()
                )
        );

        // Confirm database fields
        assertEquals("VILLAGE", record.getEntityType());
        assertEquals(villageId, record.getEntityId());
        assertEquals("VERIFIED", record.getDecision().name());
    }

    @Test
    void viewerCanReadVerificationRecords() throws Exception {

        // Generate a valid JWT for a VIEWER
        String token = tokenForRole("VIEWER");

        // Use an entity ID with no verification records
        long entityId = 999999999L;

        mockMvc.perform(
                        get("/api/v1/verifications")
                                .header("Authorization", "Bearer " + token)
                                .param("entityType", "VILLAGE")
                                .param("entityId", String.valueOf(entityId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void viewerCanReadVerificationCreatedByAdmin() throws Exception {

        // Create a real test village
        Long villageId = createTestVillage();

        // Generate ADMIN token
        String adminToken = tokenForRole("ADMIN");

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": %d,
                "decision": "VERIFIED",
                "reason": "Village data reviewed"
            }
            """.formatted(villageId);

        // ADMIN creates a verification record
        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        // Generate VIEWER token
        String viewerToken = tokenForRole("VIEWER");

        // VIEWER reads the same verification record
        mockMvc.perform(
                        get("/api/v1/verifications")
                                .header("Authorization", "Bearer " + viewerToken)
                                .param("entityType", "VILLAGE")
                                .param("entityId", villageId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityType").value("VILLAGE"))
                .andExpect(jsonPath("$[0].entityId").value(villageId))
                .andExpect(jsonPath("$[0].decision").value("VERIFIED"))
                .andExpect(jsonPath("$[0].reason")
                        .value("Village data reviewed"))
                .andExpect(jsonPath("$[0].reviewerUserId").isNumber());
    }

    @Test
    void adminCannotVerifyNegativeEntityId() throws Exception {

        // Generate a valid ADMIN JWT
        String token = tokenForRole("ADMIN");

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": -5,
                "decision": "VERIFIED",
                "reason": "Invalid village ID"
            }
            """;

        // Negative entity IDs must be rejected
        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotUseInvalidVerificationDecision() throws Exception {

        // Generate a valid ADMIN JWT
        String token = tokenForRole("ADMIN");

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": 1,
                "decision": "APPROVED",
                "reason": "Invalid decision value"
            }
            """;

        // Invalid enum values must be rejected
        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotVerifyNonExistingVillage() throws Exception {

        // Generate a valid ADMIN JWT
        String token = tokenForRole("ADMIN");

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": 999999999,
                "decision": "VERIFIED",
                "reason": "Checking non-existing village"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void verifiedDecisionAutomaticallyCreatesAuditLog() throws Exception {

        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");
        Long actorId = lastCreatedUserId;

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": %d,
                "decision": "VERIFIED",
                "reason": "Information checked"
            }
            """.formatted(villageId);

        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        var records = verificationRecordRepository
                .findByEntityTypeAndEntityIdOrderByReviewedAtDesc(
                        "VILLAGE",
                        villageId
                );

        assertEquals(1, records.size());

        Long verificationId = records.get(0).getId();

        var logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        "VERIFICATION_RECORD",
                        verificationId
                );

        assertEquals(1, logs.size());

        var log = logs.get(0);

        assertEquals(actorId, log.getActorUserId());
        assertEquals("VERIFICATION_RECORD", log.getEntityType());
        assertEquals(verificationId, log.getEntityId());
        assertEquals("VERIFY", log.getAction());
        assertEquals("VERIFIED", log.getAfterData().get("decision"));

        assertEquals(
                villageId.longValue(),
                ((Number) log.getAfterData().get("entityId")).longValue()
        );
    }

    @Test
    void rejectedDecisionAutomaticallyCreatesAuditLog() throws Exception {

        Long villageId = createTestVillage();
        String adminToken = tokenForRole("ADMIN");
        Long actorId = lastCreatedUserId;

        String requestBody = """
            {
                "entityType": "VILLAGE",
                "entityId": %d,
                "decision": "REJECTED",
                "reason": "Information does not match the source"
            }
            """.formatted(villageId);

        mockMvc.perform(
                        post("/api/v1/verifications")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        var records = verificationRecordRepository
                .findByEntityTypeAndEntityIdOrderByReviewedAtDesc(
                        "VILLAGE",
                        villageId
                );

        assertEquals(1, records.size());

        Long verificationId = records.get(0).getId();

        var logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        "VERIFICATION_RECORD",
                        verificationId
                );

        assertEquals(1, logs.size());

        var log = logs.get(0);

        assertEquals(actorId, log.getActorUserId());
        assertEquals("VERIFICATION_RECORD", log.getEntityType());
        assertEquals(verificationId, log.getEntityId());
        assertEquals("REJECT", log.getAction());
        assertEquals("REJECTED", log.getAfterData().get("decision"));

        assertEquals(
                villageId.longValue(),
                ((Number) log.getAfterData().get("entityId")).longValue()
        );
    }

}
