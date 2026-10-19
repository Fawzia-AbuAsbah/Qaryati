
package com.qaryati.security.service;

import com.qaryati.security.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {

    private AuthenticationManager authenticationManager;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        service = new AuthenticationService(authenticationManager);
    }

    @Test
    void shouldAuthenticateWithValidCredentials() {
        LoginRequest request =
                new LoginRequest("ahmad", "CorrectPassword123!");

        Authentication authenticated =
                new UsernamePasswordAuthenticationToken(
                        "ahmad",
                        null,
                        java.util.List.of()
                );

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated);

        Authentication result = service.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertEquals("ahmad", result.getName());

        verify(authenticationManager).authenticate(
                argThat(auth ->
                        auth.getName().equals("ahmad")
                                && auth.getCredentials().equals("CorrectPassword123!")
                )
        );
    }

    @Test
    void shouldRejectInvalidCredentials() {
        LoginRequest request =
                new LoginRequest("ahmad", "WrongPassword");

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(
                BadCredentialsException.class,
                () -> service.authenticate(request)
        );
    }
}
