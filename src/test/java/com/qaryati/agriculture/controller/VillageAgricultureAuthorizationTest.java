
package com.qaryati.agriculture.controller;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
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

}
