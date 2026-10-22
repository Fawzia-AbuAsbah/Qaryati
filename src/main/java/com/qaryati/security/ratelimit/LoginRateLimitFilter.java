package com.qaryati.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Counts all login requests, including malformed bodies, before MVC validation. */
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginRateLimitService rateLimitService;

    public LoginRateLimitFilter(LoginRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/v1/auth/login".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimitService.allowRequest(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
