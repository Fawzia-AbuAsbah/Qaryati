
package com.qaryati.security.config;

import com.qaryati.security.controller.RoleTestController;
import com.qaryati.security.entity.User;
import com.qaryati.security.entity.Role;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.service.JwtService;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(RoleTestController.class)
class AuthorizationIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void viewerCanAccessViewerEndpoint() throws Exception {

        Role viewer = roleRepository.findByCode("VIEWER")
                .orElseGet(() -> roleRepository.save(
                        new Role("VIEWER", "View village information")
                ));

        User user = new User(
                "testviewer",
                "testviewer@example.com",
                "test-password-hash",
                "Test Viewer"
        );

        user.addRole(viewer);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "testviewer", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        mockMvc().perform(get("/api/v1/test/roles/viewer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotAccessAdminEndpoint() throws Exception {

        Role viewer = roleRepository.findByCode("VIEWER")
                .orElseGet(() -> roleRepository.save(
                        new Role("VIEWER", "View village information")
                ));

        User user = new User(
                "testviewer2",
                "testviewer2@example.com",
                "test-password-hash",
                "Test Viewer"
        );

        user.addRole(viewer);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "testviewer2", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        mockMvc().perform(get("/api/v1/test/roles/admin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessViewerEndpoint() throws Exception {

        mockMvc().perform(get("/api/v1/test/roles/viewer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editorCanAccessEditorEndpoint() throws Exception {

        Role editor = roleRepository.findByCode("EDITOR")
                .orElseGet(() -> roleRepository.save(
                        new Role("EDITOR", "Edit village information")
                ));

        User user = new User(
                "testeditor",
                "testeditor@example.com",
                "test-password-hash",
                "Test Editor"
        );

        user.addRole(editor);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "testeditor", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        mockMvc().perform(get("/api/v1/test/roles/editor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void editorCannotAccessAdminEndpoint() throws Exception {

        Role editor = roleRepository.findByCode("EDITOR")
                .orElseGet(() -> roleRepository.save(
                        new Role("EDITOR", "Edit village information")
                ));

        User user = new User(
                "testeditor2",
                "testeditor2@example.com",
                "test-password-hash",
                "Test Editor"
        );

        user.addRole(editor);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "testeditor2", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        mockMvc().perform(get("/api/v1/test/roles/admin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAllEndpoints() throws Exception {

        Role admin = roleRepository.findByCode("ADMIN")
                .orElseGet(() -> roleRepository.save(
                        new Role("ADMIN", "Manage users and roles")
                ));

        User user = new User(
                "testadmin",
                "testadmin@example.com",
                "test-password-hash",
                "Test Admin"
        );

        user.addRole(admin);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "testadmin", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        mockMvc().perform(get("/api/v1/test/roles/viewer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/test/roles/editor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/test/roles/admin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }


    @Test
    void disabledUserCannotAccessWithExistingToken() throws Exception {

        Role viewer = roleRepository.findByCode("VIEWER")
                .orElseGet(() -> roleRepository.save(
                        new Role("VIEWER", "View village information")
                ));

        User user = new User(
                "disabledviewer",
                "disabledviewer@example.com",
                "test-password-hash",
                "Disabled Viewer"
        );

        user.addRole(viewer);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "disabledviewer", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        // Before disabling: access is allowed
        mockMvc().perform(get("/api/v1/test/roles/viewer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Disable the account
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        // The same token must now be rejected
        mockMvc().perform(get("/api/v1/test/roles/viewer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void roleChangeShouldApplyToExistingToken() throws Exception {

        Role viewer = roleRepository.findByCode("VIEWER")
                .orElseGet(() -> roleRepository.save(
                        new Role("VIEWER", "View village information")
                ));

        Role editor = roleRepository.findByCode("EDITOR")
                .orElseGet(() -> roleRepository.save(
                        new Role("EDITOR", "Edit village information")
                ));

        User user = new User(
                "rolechangeuser",
                "rolechangeuser@example.com",
                "test-password-hash",
                "Role Change User"
        );

        user.addRole(viewer);
        userRepository.saveAndFlush(user);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "rolechangeuser", null, List.of()
                );

        String token = jwtService.generateToken(auth);

        // VIEWER cannot access EDITOR endpoint
        mockMvc().perform(get("/api/v1/test/roles/editor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Change role from VIEWER to EDITOR
        user.removeRole(viewer);
        user.addRole(editor);
        userRepository.saveAndFlush(user);

        // Same token should now allow EDITOR access
        mockMvc().perform(get("/api/v1/test/roles/editor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

}
