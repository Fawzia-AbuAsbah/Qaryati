
package com.qaryati.security.service;

import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseUserDetailsServiceTest {

    private UserRepository userRepository;
    private DatabaseUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        service = new DatabaseUserDetailsService(userRepository);
    }

    @Test
    void shouldLoadUserWithRoles() {
        User user = new User(
                "ahmad",
                "ahmad@example.com",
                "encoded-password",
                "Ahmad"
        );

        Role editor = new Role("EDITOR", "Can edit village data");
        user.addRole(editor);

        when(userRepository.findWithRolesByUsername("ahmad"))
                .thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("ahmad");

        assertEquals("ahmad", result.getUsername());
        assertEquals("encoded-password", result.getPassword());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EDITOR")));
        assertTrue(result.isEnabled());
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        when(userRepository.findWithRolesByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown")
        );
    }

    @Test
    void shouldReturnDisabledUser() {
        User user = new User(
                "disabled_user",
                "disabled@example.com",
                "encoded-password",
                "Disabled User"
        );
        user.setEnabled(false);

        when(userRepository.findWithRolesByUsername("disabled_user"))
                .thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("disabled_user");

        assertFalse(result.isEnabled());
    }
}
