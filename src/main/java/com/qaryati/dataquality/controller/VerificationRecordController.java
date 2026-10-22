
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.dto.CreateVerificationRequest;
import com.qaryati.dataquality.dto.VerificationRecordResponse;
import com.qaryati.dataquality.service.VerificationRecordService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationRecordController {

    private final VerificationRecordService verificationService;

    public VerificationRecordController(
            VerificationRecordService verificationService
    ) {
        this.verificationService = verificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationRecordResponse createVerification(
            @Valid @RequestBody CreateVerificationRequest request,
            Authentication authentication
    ) {
        return verificationService.createVerification(
                request,
                authentication
        );
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<VerificationRecordResponse> getVerifications(
            @RequestParam String entityType,
            @RequestParam Long entityId
    ) {
        return verificationService.getVerifications(
                entityType,
                entityId
        );
    }
}
