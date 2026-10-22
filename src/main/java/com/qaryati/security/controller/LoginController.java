
package com.qaryati.security.controller;

import com.qaryati.security.dto.LoginRequest;
import com.qaryati.security.dto.LoginResponse;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.AuthenticationService;
import com.qaryati.security.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;
    private final long expirationMinutes;

    public LoginController(
            AuthenticationService authenticationService,
            JwtService jwtService,
            LoginRateLimitService loginRateLimitService,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {

        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
        this.expirationMinutes = expirationMinutes;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = httpRequest.getRemoteAddr();

        if (!loginRateLimitService.allowRequest(clientIp)) {
            return ResponseEntity.status(429).build();
        }

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

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            Authentication authentication) {

        return ResponseEntity.ok(
                Map.of("username", authentication.getName())
        );
    }
}
