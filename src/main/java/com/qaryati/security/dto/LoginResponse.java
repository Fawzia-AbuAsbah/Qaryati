
package com.qaryati.security.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
