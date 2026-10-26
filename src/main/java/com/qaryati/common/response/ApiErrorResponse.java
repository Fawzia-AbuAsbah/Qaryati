package com.qaryati.common.response;

import java.time.Instant;
import org.springframework.modulith.NamedInterface;

@NamedInterface("errors")
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
