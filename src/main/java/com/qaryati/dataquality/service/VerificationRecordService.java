
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.dto.CreateVerificationRequest;
import com.qaryati.dataquality.dto.VerificationRecordResponse;
import com.qaryati.dataquality.entity.VerificationRecord;
import com.qaryati.dataquality.repository.VerificationRecordRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.security.api.UserSummary;
import com.qaryati.security.api.UserLookup;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@org.springframework.validation.annotation.Validated
public class VerificationRecordService {

    private final VerificationRecordRepository verificationRepository;
    private final UserLookup userLookup;
    private final VillageExistsRepository villageExistsRepository;
    private final AuditLogService auditLogService;

    public VerificationRecordService(
            VerificationRecordRepository verificationRepository,
            UserLookup userLookup,
            VillageExistsRepository villageExistsRepository,
            AuditLogService auditLogService
    ) {
        this.verificationRepository = verificationRepository;
        this.userLookup = userLookup;
        this.villageExistsRepository = villageExistsRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationRecordResponse createVerification(
            @jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateVerificationRequest request
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null ||
                !authentication.isAuthenticated()) {
            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        String username = authentication.getName();

        UserSummary reviewer = userLookup.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reviewer user not found"
                        )
                );

        if (!reviewer.enabled()) {
            throw new IllegalStateException(
                    "Reviewer account is disabled"
            );
        }

        String entityType = request.entityType().trim();

        if (entityType.isEmpty() || entityType.length() > 80) {
            throw new IllegalArgumentException(
                    "Invalid entity type"
            );
        }

        if (request.entityId() == null || request.entityId() <= 0) {
            throw new IllegalArgumentException(
                    "Entity ID must be positive"
            );
        }

        if (request.decision() == null) {
            throw new IllegalArgumentException(
                    "Verification decision is required"
            );
        }

        if (!"VILLAGE".equals(entityType)) {
            throw new IllegalArgumentException(
                    "Unsupported entity type. Currently only VILLAGE is supported"
            );
        }

        if (!villageExistsRepository.existsById(request.entityId())) {
            throw new NoSuchElementException(
                    "Village not found: " + request.entityId()
            );
        }

        VerificationRecord record = new VerificationRecord(
                entityType,
                request.entityId(),
                reviewer.id(),
                request.decision(),
                request.reason()
        );

        VerificationRecord saved =
                verificationRepository.saveAndFlush(record);

        String decision = saved.getDecision().name();

        String auditAction = switch (decision) {
            case "VERIFIED" -> "VERIFY";
            case "REJECTED" -> "REJECT";
            case "NEEDS_REVISION" -> "UPDATE";
            default -> throw new IllegalArgumentException(
                    "Unsupported verification decision: " + decision
            );
        };

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("entityType", entityType);
        afterData.put("entityId", request.entityId());
        afterData.put("reviewerId", reviewer.id());
        afterData.put("decision", decision);

        auditLogService.record(
                "VERIFICATION_RECORD",
                saved.getId(),
                auditAction,
                null,
                afterData,
                "Verification decision recorded"
        );

        return VerificationRecordResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<VerificationRecordResponse> getVerifications(
            String entityType,
            Long entityId
    ) {
        return verificationRepository
                .findByEntityTypeAndEntityIdOrderByReviewedAtDesc(
                        entityType,
                        entityId
                )
                .stream()
                .map(VerificationRecordResponse::fromEntity)
                .toList();
    }
}
