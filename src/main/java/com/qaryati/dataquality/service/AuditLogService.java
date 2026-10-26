
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.AuditLog;
import com.qaryati.dataquality.repository.AuditLogRepository;
import com.qaryati.security.api.UserLookup;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qaryati.dataquality.dto.AuditLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import java.util.Map;
import java.util.Set;

@Service
public class AuditLogService {

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "CREATE",
            "UPDATE",
            "DELETE",
            "VERIFY",
            "REJECT",
            "RESTORE"
    );

    private final AuditLogRepository auditLogRepository;
    private final UserLookup userLookup;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserLookup userLookup
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userLookup = userLookup;
    }

    @Transactional
    public void record(
            String entityType,
            Long entityId,
            String action,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            String reason
    ) {
        if (entityType == null || entityType.isBlank()
                || entityType.length() > 80) {
            throw new IllegalArgumentException(
                    "Invalid audit entity type"
            );
        }

        if (entityId == null || entityId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid audit entity ID"
            );
        }

        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException(
                    "Invalid audit action"
            );
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException(
                    "Authenticated user required for audit"
            );
        }

        String username = authentication.getName();

        var user = userLookup.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Audit actor not found"
                ));

        if (!user.enabled()) {
            throw new IllegalStateException("Audit actor account is disabled");
        }

        AuditLog auditLog = new AuditLog(
                user.id(),
                username,
                entityType,
                entityId,
                action,
                beforeData,
                afterData,
                null,
                reason
        );

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogResponse> getLogsByEntity(
            String entityType,
            Long entityId
    ) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException(
                    "Entity type is required"
            );
        }

        if (entityId == null || entityId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid entity ID"
            );
        }

        return auditLogRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        entityType,
                        entityId
                )
                .stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogResponse> getLogsByActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid actor user ID"
            );
        }

        return auditLogRepository
                .findByActorUserIdOrderByOccurredAtDesc(actorUserId)
                .stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogResponse> getLogsByAction(String action) {
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException(
                    "Invalid audit action"
            );
        }

        return auditLogRepository
                .findByActionOrderByOccurredAtDesc(action)
                .stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

}
