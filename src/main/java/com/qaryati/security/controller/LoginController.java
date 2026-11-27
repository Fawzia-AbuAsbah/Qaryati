
package com.qaryati.security.controller;

import com.qaryati.security.dto.LoginRequest;
import com.qaryati.security.dto.LoginResponse;
import com.qaryati.security.service.AuthenticationService;
import com.qaryati.security.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final long expirationMinutes;

    public LoginController(
            AuthenticationService authenticationService,
            JwtService jwtService,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {

        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        Authentication authentication =
                authenticationService.authenticate(request);

        String token = jwtService.generateToken(authentication);

        LoginResponse response = new LoginResponse(
                token,
                "Bearer",
                expirationMinutes * 60
        );

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request data"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            Authentication authentication) {

        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> "ROLE_VIEWER".equals(authority)
                        || "ROLE_EDITOR".equals(authority)
                        || "ROLE_ADMIN".equals(authority))
                .map(authority -> authority.substring("ROLE_".length()))
                .distinct()
                .sorted()
                .toList();

        return ResponseEntity.ok(
                Map.of("username", authentication.getName(), "roles", roles)
        );
    }
}
