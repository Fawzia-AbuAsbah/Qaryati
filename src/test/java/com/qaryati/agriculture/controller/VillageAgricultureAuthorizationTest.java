
package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.AgricultureTestDatabaseGuard;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = AgricultureTestDatabaseGuard.class)
@Transactional
class VillageAgricultureAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String tokenForRole(String roleCode) {

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow();

        String username = "test_" +
                UUID.randomUUID().toString().replace("-", "");

        User user = new User(
                username,
                username + "@example.com",
                "test-password-hash",
                "Test User"
        );

        user.addRole(role);
        userRepository.saveAndFlush(user);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        username, null, List.of()
                );

        return jwtService.generateToken(authentication);
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {

        mockMvc().perform(
                get("/api/v1/village-agriculture/village/10")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerShouldBeAllowedToRead() throws Exception {

        String token = tokenForRole("VIEWER");

        mockMvc().perform(
                get("/api/v1/village-agriculture/village/10")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isOk());
    }

    @Test
    void editorShouldBeAllowedToRead() throws Exception {

        String token = tokenForRole("EDITOR");

        mockMvc().perform(
                get("/api/v1/village-agriculture/village/10")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isOk());
    }

    @Test
    void adminShouldBeAllowedToRead() throws Exception {

        String token = tokenForRole("ADMIN");

        mockMvc().perform(
                get("/api/v1/village-agriculture/village/10")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isOk());
    }

    @Test
    void userWithoutRoleShouldBeForbidden() throws Exception {

        String username = "no_role_" +
                UUID.randomUUID().toString().replace("-", "");

        User user = new User(
                username,
                username + "@example.com",
                "test-password-hash",
                "Test User"
        );

        // We deliberately do not assign any role.
        userRepository.saveAndFlush(user);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        username, null, List.of()
                );

        String token = jwtService.generateToken(authentication);

        mockMvc().perform(
                get("/api/v1/village-agriculture/village/10")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotCreateOrUpdateVillageAgriculture() throws Exception {

        String token = tokenForRole("VIEWER");
        String body = """
                {"villageId":10,"productId":5,"description":"Olives","importance":"HIGH"}
                """;

        mockMvc().perform(
                post("/api/v1/village-agriculture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isForbidden());

        mockMvc().perform(
                put("/api/v1/village-agriculture/999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isForbidden());
    }

    @Test
    void editorCanMutateButCannotDeleteVillageAgriculture() throws Exception {

        String token = tokenForRole("EDITOR");
        String body = """
                {"villageId":10,"productId":5,"description":"Olives","importance":"HIGH"}
                """;

        // The referenced village does not exist in this test; 404 proves authorization passed.
        mockMvc().perform(
                post("/api/v1/village-agriculture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isNotFound());

        // A missing relation yields 404 only after EDITOR authorization succeeds.
        mockMvc().perform(
                put("/api/v1/village-agriculture/999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isNotFound());

        mockMvc().perform(
                delete("/api/v1/village-agriculture/999")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteVillageAgriculture() throws Exception {

        String token = tokenForRole("ADMIN");

        // A missing relation yields 404 only after ADMIN authorization succeeds.
        mockMvc().perform(
                delete("/api/v1/village-agriculture/999")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isNotFound());
    }

}
