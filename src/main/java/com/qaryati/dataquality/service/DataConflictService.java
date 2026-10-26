
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.dto.CreateDataConflictRequest;
import com.qaryati.dataquality.dto.DataConflictResponse;
import com.qaryati.dataquality.dto.ResolveDataConflictRequest;
import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;
import com.qaryati.dataquality.repository.DataConflictRepository;
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
public class DataConflictService {

    private final DataConflictRepository conflictRepository;
    private final VillageExistsRepository villageExistsRepository;
    private final UserLookup userLookup;
    private final AuditLogService auditLogService;

    public DataConflictService(
            DataConflictRepository conflictRepository,
            VillageExistsRepository villageExistsRepository,
            UserLookup userLookup,
            AuditLogService auditLogService
    ) {
        this.conflictRepository = conflictRepository;
        this.villageExistsRepository = villageExistsRepository;
        this.userLookup = userLookup;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public DataConflictResponse createConflict(
            @jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateDataConflictRequest request
    ) {
        String entityType = request.entityType().trim();

        if (!"VILLAGE".equals(entityType)) {
            throw new IllegalArgumentException(
                    "Currently only VILLAGE is supported"
            );
        }

        if (!villageExistsRepository.existsById(request.entityId())) {
            throw new NoSuchElementException(
                    "Village not found: " + request.entityId()
            );
        }

        if (request.conflictingValues().size() < 2) {
            throw new IllegalArgumentException(
                    "At least two conflicting values are required"
            );
        }

        DataConflict conflict = new DataConflict(
                entityType,
                request.entityId(),
                request.fieldName().trim(),
                request.conflictingValues()
        );

        DataConflict saved = conflictRepository.saveAndFlush(conflict);

        auditLogService.record(
                "DATA_CONFLICT",
                saved.getId(),
                "CREATE",
                null,
                auditData(saved),
                "Data conflict created"
        );

        return DataConflictResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<DataConflictResponse> getConflicts(
            String entityType,
            Long entityId
    ) {
        if (!"VILLAGE".equals(entityType)) {
            throw new IllegalArgumentException(
                    "Currently only VILLAGE is supported"
            );
        }

        if (entityId == null || entityId <= 0) {
            throw new IllegalArgumentException(
                    "Entity ID must be positive"
            );
        }

        return conflictRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        entityType,
                        entityId
                )
                .stream()
                .map(DataConflictResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public List<DataConflictResponse> getConflictsByStatus(
            DataConflictStatus status
    ) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Conflict status is required"
            );
        }

        return conflictRepository
                .findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(DataConflictResponse::fromEntity)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DataConflictResponse markInReview(Long conflictId) {

        DataConflict conflict = findConflict(conflictId);

        if (conflict.getStatus() != DataConflictStatus.OPEN) {
            throw new IllegalStateException(
                    "Only OPEN conflicts can be moved to IN_REVIEW"
            );
        }

        Map<String, Object> beforeData = auditData(conflict);

        conflict.markInReview();

        DataConflict saved = conflictRepository.saveAndFlush(conflict);

        auditLogService.record(
                "DATA_CONFLICT",
                saved.getId(),
                "UPDATE",
                beforeData,
                auditData(saved),
                "Conflict moved to review"
        );

        return DataConflictResponse.fromEntity(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DataConflictResponse resolveConflict(
            Long conflictId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.Valid ResolveDataConflictRequest request
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        DataConflict conflict = findConflict(conflictId);

        if (conflict.getStatus() != DataConflictStatus.IN_REVIEW) {
            throw new IllegalStateException(
                    "Only IN_REVIEW conflicts can be resolved"
            );
        }

        if (request == null ||
                request.resolutionNotes() == null ||
                request.resolutionNotes().isBlank()) {
            throw new IllegalArgumentException(
                    "Resolution notes are required"
            );
        }

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        UserSummary reviewer = userLookup
                .findByUsername(authentication.getName())
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

        Map<String, Object> beforeData = auditData(conflict);

        conflict.resolve(
                reviewer.id(),
                request.resolutionNotes().trim()
        );

        DataConflict saved = conflictRepository.saveAndFlush(conflict);

        auditLogService.record(
                "DATA_CONFLICT",
                saved.getId(),
                "UPDATE",
                beforeData,
                auditData(saved),
                "Conflict resolved"
        );

        return DataConflictResponse.fromEntity(saved);
    }

    private Map<String, Object> auditData(DataConflict conflict) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("entityType", conflict.getEntityType());
        data.put("entityId", conflict.getEntityId());
        data.put("fieldName", conflict.getFieldName());
        data.put("status", conflict.getStatus().name());
        data.put("resolvedBy", conflict.getResolvedBy());
        data.put("resolvedAt", conflict.getResolvedAt() == null
                ? null
                : conflict.getResolvedAt().toString());

        // Only store non-sensitive metadata in the audit snapshot.
        // Conflicting values and resolution notes are not copied here.
        return data;
    }

    private DataConflict findConflict(Long conflictId) {

        if (conflictId == null || conflictId <= 0) {
            throw new IllegalArgumentException(
                    "Conflict ID must be positive"
            );
        }

        return conflictRepository.findByIdForUpdate(conflictId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Data conflict not found: " + conflictId
                        )
                );
    }
}
