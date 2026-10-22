
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.entity.AuditLog;
import com.qaryati.dataquality.repository.AuditLogRepository;
import com.qaryati.dataquality.service.AuditLogService;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    private Long lastCreatedUserId;

    private String tokenForRole(String roleCode) {

        Role role = roleRepository.findByCode(roleCode)
                .orElseGet(() -> roleRepository.save(
                        new Role(roleCode, "Test role")
                ));

        String username = "audit_" +
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

    private AuditLog createTestAuditLog(Long actorUserId) {
        return auditLogRepository.saveAndFlush(
                new AuditLog(
                        actorUserId,
                        "Test Admin",
                        "VILLAGE",
                        100L,
                        "UPDATE",
                        Map.of("name", "Old Village"),
                        Map.of("name", "New Village"),
                        null,
                        "Corrected village name"
                )
        );
    }

    @Test
    void anonymousCannotReadAuditLogs() throws Exception {
        mockMvc.perform(
                        get("/api/v1/audit-logs/entity")
                                .param("entityType", "VILLAGE")
                                .param("entityId", "100")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotReadAuditLogs() throws Exception {
        String token = tokenForRole("VIEWER");

        mockMvc.perform(
                        get("/api/v1/audit-logs/entity")
                                .header("Authorization", "Bearer " + token)
                                .param("entityType", "VILLAGE")
                                .param("entityId", "100")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void editorCannotReadAuditLogs() throws Exception {
        String token = tokenForRole("EDITOR");

        mockMvc.perform(
                        get("/api/v1/audit-logs/entity")
                                .header("Authorization", "Bearer " + token)
                                .param("entityType", "VILLAGE")
                                .param("entityId", "100")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAuditLogsByEntity() throws Exception {
        String token = tokenForRole("ADMIN");

        AuditLog log = createTestAuditLog(lastCreatedUserId);

        mockMvc.perform(
                        get("/api/v1/audit-logs/entity")
                                .header("Authorization", "Bearer " + token)
                                .param("entityType", "VILLAGE")
                                .param("entityId", "100")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(log.getId()))
                .andExpect(jsonPath("$[0].entityType").value("VILLAGE"))
                .andExpect(jsonPath("$[0].entityId").value(100))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].actorLabel").value("Test Admin"))
                .andExpect(jsonPath("$[0].beforeData.name")
                        .value("Old Village"))
                .andExpect(jsonPath("$[0].afterData.name")
                        .value("New Village"));
    }

    @Test
    void adminCanReadAuditLogsByActor() throws Exception {
        String token = tokenForRole("ADMIN");
        Long actorId = lastCreatedUserId;

        AuditLog log = createTestAuditLog(actorId);

        mockMvc.perform(
                        get("/api/v1/audit-logs/actor/{actorUserId}", actorId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(log.getId()))
                .andExpect(jsonPath("$[0].actorUserId").value(actorId));
    }

    @Test
    void adminCanReadAuditLogsByAction() throws Exception {
        String token = tokenForRole("ADMIN");

        createTestAuditLog(lastCreatedUserId);

        mockMvc.perform(
                        get("/api/v1/audit-logs/action/UPDATE")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].action").value("UPDATE"));
    }

    @Test
    void invalidEntityIdReturnsBadRequest() throws Exception {
        String token = tokenForRole("ADMIN");

        mockMvc.perform(
                        get("/api/v1/audit-logs/entity")
                                .header("Authorization", "Bearer " + token)
                                .param("entityType", "VILLAGE")
                                .param("entityId", "-1")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidActionReturnsBadRequest() throws Exception {
        String token = tokenForRole("ADMIN");

        mockMvc.perform(
                        get("/api/v1/audit-logs/action/INVALID")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditLogPersistsCorrectly() {
        tokenForRole("ADMIN");

        AuditLog saved = createTestAuditLog(lastCreatedUserId);

        AuditLog found = auditLogRepository
                .findById(saved.getId())
                .orElseThrow();

        assertEquals("VILLAGE", found.getEntityType());
        assertEquals(100L, found.getEntityId());
        assertEquals("UPDATE", found.getAction());
        assertEquals("Test Admin", found.getActorLabel());
        assertEquals("Old Village", found.getBeforeData().get("name"));
        assertEquals("New Village", found.getAfterData().get("name"));
        assertNotNull(found.getId());
    }
}
