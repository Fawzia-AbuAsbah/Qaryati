package com.qaryati.community.controller;

import com.qaryati.community.CommunityTestDatabaseGuard;
import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.entity.EconomicActivityType;
import com.qaryati.community.repository.EconomicActivityRepository;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = CommunityTestDatabaseGuard.class)
@Transactional
class EconomicActivityAuthorizationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private EconomicActivityRepository activities;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String tokenForRole(String roleCode) {
        Role role = roles.findByCode(roleCode).orElseThrow();
        String username = "community_auth_" + UUID.randomUUID().toString().replace("-", "");
        User user = new User(username, username + "@example.com", "test-password-hash", "Test User");
        user.addRole(role);
        users.saveAndFlush(user);
        return jwtService.generateToken(new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private Long villageId() {
        String suffix = UUID.randomUUID().toString();
        Long governorateId = jdbcTemplate.queryForObject("""
                INSERT INTO public.governorates (name_ar) VALUES (?) RETURNING id
                """, Long.class, "Auth governorate " + suffix);
        return jdbcTemplate.queryForObject("""
                INSERT INTO public.villages (governorate_id, name_ar, location_description)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, governorateId, "Auth village " + suffix, "Test location");
    }

    private Long activityId(Long villageId) {
        return activities.saveAndFlush(new EconomicActivity(villageId, "Activity " + UUID.randomUUID(),
                EconomicActivityType.CRAFT, null)).getId();
    }

    private String body(Long villageId) {
        return """
                {"villageId":%d,"name":"Activity %s","activityType":"CRAFT","description":"Test"}
                """.formatted(villageId, UUID.randomUUID());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc().perform(get("/api/v1/economic-activities")).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadButCannotCreate() throws Exception {
        String token = tokenForRole("VIEWER");
        Long villageId = villageId();

        mockMvc().perform(get("/api/v1/economic-activities/" + activityId(villageId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc().perform(post("/api/v1/economic-activities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(villageId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorCanCreateAndUpdateButCannotDelete() throws Exception {
        String token = tokenForRole("EDITOR");
        Long villageId = villageId();
        Long activityId = activityId(villageId);

        mockMvc().perform(post("/api/v1/economic-activities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(villageId)))
                .andExpect(status().isCreated());
        mockMvc().perform(put("/api/v1/economic-activities/" + activityId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(villageId)))
                .andExpect(status().isOk());
        mockMvc().perform(delete("/api/v1/economic-activities/" + activityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDelete() throws Exception {
        String token = tokenForRole("ADMIN");
        Long villageId = villageId();
        mockMvc().perform(delete("/api/v1/economic-activities/" + activityId(villageId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
