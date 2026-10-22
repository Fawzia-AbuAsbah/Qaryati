
package com.qaryati.security.service;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import com.qaryati.security.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void shouldRejectMissingOrBlankSubjectAndMissingExpiration() {
        Instant now = Instant.now();
        for (String subject : new String[] {null, " ", "ahmad"}) {
            var builder = JwtClaimsSet.builder().issuer("https://qaryati.example").issuedAt(now);
            if (subject != null) builder.subject(subject);
            if (!"ahmad".equals(subject)) builder.expiresAt(now.plusSeconds(900));
            String token = jwtEncoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
            assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
        }
    }

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;
    private JwtEncoder jwtEncoder;
    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();

        // Test-only key: NOT used in production
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);

        String secret = Base64.getEncoder()
                .encodeToString(keyBytes);

        SecretKey secretKey = config.jwtSecretKey(secret);

        jwtEncoder = config.jwtEncoder(secretKey);        jwtDecoder = config.jwtDecoder(secretKey);

        jwtService = new JwtService(jwtEncoder, 15);
    }

    @Test
    void shouldGenerateValidToken() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "ahmad",
                        null,
                        java.util.List.of()
                );

        String token = jwtService.generateToken(authentication);

        assertNotNull(token);
        assertFalse(token.isBlank());

        Jwt decoded = jwtDecoder.decode(token);

        assertEquals("ahmad", decoded.getSubject());
        assertEquals("https://qaryati.example", decoded.getIssuer().toString());    }

    @Test
    void shouldSetExpirationTo15Minutes() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "ahmad",
                        null,
                        java.util.List.of()
                );

        String token = jwtService.generateToken(authentication);

        Jwt decoded = jwtDecoder.decode(token);

        long expirationSeconds = Duration.between(
                decoded.getIssuedAt(),
                decoded.getExpiresAt()
        ).getSeconds();

        assertEquals(900, expirationSeconds);
    }

    @Test
    void shouldRejectTokenWithWrongSignature() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "ahmad",
                        null,
                        java.util.List.of()
                );

        String token = jwtService.generateToken(authentication);

        // Create a decoder using a DIFFERENT secret key
        JwtConfig config = new JwtConfig();

        byte[] wrongKeyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(wrongKeyBytes);

        String wrongSecret = Base64.getEncoder()
                .encodeToString(wrongKeyBytes);

        JwtDecoder wrongDecoder = config.jwtDecoder(
                config.jwtSecretKey(wrongSecret)
        );

        assertThrows(
                org.springframework.security.oauth2.jwt.JwtException.class,
                () -> wrongDecoder.decode(token)
        );
    }

    @Test
    void shouldRejectExpiredToken() {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://qaryati.example")
                .subject("ahmad")
                .issuedAt(now.minusSeconds(1200))
                .expiresAt(now.minusSeconds(600))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .build();

        String expiredToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        assertThrows(
                JwtValidationException.class,
                () -> jwtDecoder.decode(expiredToken)
        );
    }

    @Test
    void shouldRejectTokenWithWrongIssuer() {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://another-app.example")
                .subject("ahmad")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        assertThrows(
                JwtValidationException.class,
                () -> jwtDecoder.decode(token)
        );
    }

}
