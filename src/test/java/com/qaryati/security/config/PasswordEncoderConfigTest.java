
package com.qaryati.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderConfigTest {

    private final PasswordEncoder passwordEncoder =
            new PasswordEncoderConfig().passwordEncoder();

    @Test
    void shouldEncodeAndVerifyCorrectPassword() {
        String rawPassword = "ExamplePass123!";

        String hash = passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, hash);
        assertTrue(passwordEncoder.matches(rawPassword, hash));
    }

    @Test
    void shouldRejectIncorrectPassword() {
        String hash = passwordEncoder.encode("ExamplePass123!");

        assertFalse(passwordEncoder.matches("WrongPassword", hash));
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        String password = "ExamplePass123!";

        String firstHash = passwordEncoder.encode(password);
        String secondHash = passwordEncoder.encode(password);

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordEncoder.matches(password, firstHash));
        assertTrue(passwordEncoder.matches(password, secondHash));
    }
}
