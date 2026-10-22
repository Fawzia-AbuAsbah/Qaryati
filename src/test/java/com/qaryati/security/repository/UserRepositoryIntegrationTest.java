
package com.qaryati.security.repository;

import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryIntegrationTest {
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldSaveAndFindUserByUsername() {

        String username = "test_" + UUID.randomUUID()
                .toString().substring(0, 8);

        User user = new User(
                username,
                username + "@example.com",
                "test-hash-not-a-real-password",
                "Test User"
        );

        userRepository.saveAndFlush(user);

        User found = userRepository.findByUsername(username)
                .orElseThrow();

        assertNotNull(found.getId());
        assertEquals(username, found.getUsername());
        assertEquals(user.getEmail(), found.getEmail());
    }

    @Test
    void shouldAssignRoleToUser() {

        String unique = UUID.randomUUID().toString().substring(0, 8);

        Role role = new Role(
                "EDITOR_" + unique,
                "Can edit village information"
        );

        Role savedRole = roleRepository.saveAndFlush(role);

        User user = new User(
                "user_" + unique,
                "user_" + unique + "@example.com",
                "test-hash-not-a-real-password",
                "Test Editor"
        );

        user.addRole(savedRole);
        userRepository.saveAndFlush(user);

        // Clear the persistence context so we read from the database again
        entityManager.clear();

        User found = userRepository.findByUsername("user_" + unique)
                .orElseThrow();

        assertTrue(found.getRoles().stream()
                .anyMatch(r -> r.getCode().equals(savedRole.getCode())));
    }

}
