
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.dto.CreateDataConflictRequest;
import com.qaryati.dataquality.dto.DataConflictResponse;
import com.qaryati.dataquality.dto.ResolveDataConflictRequest;
import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;
import com.qaryati.dataquality.repository.DataConflictRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DataConflictService {

    private final DataConflictRepository conflictRepository;
    private final VillageExistsRepository villageExistsRepository;
    private final UserRepository userRepository;

    public DataConflictService(
            DataConflictRepository conflictRepository,
            VillageExistsRepository villageExistsRepository,
            UserRepository userRepository
    ) {
        this.conflictRepository = conflictRepository;
        this.villageExistsRepository = villageExistsRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public DataConflictResponse createConflict(
            CreateDataConflictRequest request
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

        conflict.markInReview();

        return DataConflictResponse.fromEntity(
                conflictRepository.saveAndFlush(conflict)
        );
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DataConflictResponse resolveConflict(
            Long conflictId,
            ResolveDataConflictRequest request,
            Authentication authentication
    ) {
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

        User reviewer = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reviewer user not found"
                        )
                );

        if (!reviewer.isEnabled()) {
            throw new IllegalStateException(
                    "Reviewer account is disabled"
            );
        }

        conflict.resolve(
                reviewer.getId(),
                request.resolutionNotes().trim()
        );

        return DataConflictResponse.fromEntity(
                conflictRepository.saveAndFlush(conflict)
        );
    }

    private DataConflict findConflict(Long conflictId) {

        if (conflictId == null || conflictId <= 0) {
            throw new IllegalArgumentException(
                    "Conflict ID must be positive"
            );
        }

        return conflictRepository.findById(conflictId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Data conflict not found: " + conflictId
                        )
                );
    }
}
