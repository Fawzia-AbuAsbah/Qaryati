package com.qaryati.population.service;

import com.qaryati.population.dto.*;
import com.qaryati.population.entity.*;
import com.qaryati.population.mapper.PopulationRecordMapper;
import com.qaryati.population.repository.PopulationRecordRepository;
import com.qaryati.village.api.*;
import com.qaryati.dataquality.api.*;
import com.qaryati.common.exception.DuplicateResourceException;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly = true)
public class PopulationRecordService {
    private final PopulationRecordRepository repository;
    private final PopulationRecordMapper mapper;
    private final VillageLookupApi villages;
    private final SourceLookup sources;
    private final AuditWriter audits;
    private final Validator validator;

    public PopulationRecordService(PopulationRecordRepository repository, PopulationRecordMapper mapper,
            VillageLookupApi villages, SourceLookup sources, AuditWriter audits, Validator validator) {
        this.repository = repository; this.mapper = mapper; this.villages = villages;
        this.sources = sources; this.audits = audits; this.validator = validator;
    }

    @Transactional
    public PopulationRecordResponse create(PopulationRecordRequest request) {
        authorize(true); validate(request); validateReferences(request); duplicate(request, null);
        PopulationRecord record = mapper.toEntity(request);
        flushSave(record);
        audits.record("POPULATION_RECORD", record.getId(), AuditAction.CREATE, null, snapshot(record));
        return mapper.toResponse(record);
    }

    public PopulationRecordPage getAll(int page, int size) {
        boolean privileged = authorize(false);
        if (page < 0 || size < 1 || size > 100) throw badRequest();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
        var result = privileged ? repository.findAll(pageable)
                : repository.findByVerificationStatus(VerificationStatus.VERIFIED, pageable);
        return new PopulationRecordPage(result.getContent().stream().map(mapper::toResponse).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public PopulationRecordResponse getById(Long id) {
        boolean privileged = authorize(false); validId(id);
        var record = (privileged ? repository.findById(id)
                : repository.findByIdAndVerificationStatus(id, VerificationStatus.VERIFIED))
                .orElseThrow(() -> missing("Population record"));
        return mapper.toResponse(record);
    }

    @Transactional
    public PopulationRecordResponse update(Long id, PopulationRecordRequest request) {
        authorize(true); validId(id); validate(request);
        var record = repository.findByIdForUpdate(id).orElseThrow(() -> missing("Population record"));
        record.requireDraft();
        if (!record.getVillageId().equals(request.villageId()))
            throw new DuplicateResourceException("Village ID cannot be changed");
        validateReferences(request); duplicate(request, id);
        var before = snapshot(record);
        mapper.updateEntity(request, record); flushSave(record);
        audits.record("POPULATION_RECORD", id, AuditAction.UPDATE, before, snapshot(record));
        return mapper.toResponse(record);
    }

    @Transactional
    public void delete(Long id) {
        authorize(true); validId(id);
        var record = repository.findByIdForUpdate(id).orElseThrow(() -> missing("Population record"));
        record.requireDraft();
        var before = snapshot(record);
        repository.delete(record); repository.flush();
        audits.record("POPULATION_RECORD", id, AuditAction.DELETE, before, null);
    }

    private boolean authorize(boolean mutation) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EDITOR"));
        boolean viewer = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER"));
        if (!privileged && (mutation || !viewer)) throw new AccessDeniedException("Insufficient permissions");
        return privileged;
    }

    private void validate(PopulationRecordRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) throw badRequest();
    }
    private void validId(Long id) { if (id == null || id <= 0) throw badRequest(); }

    private void validateReferences(PopulationRecordRequest request) {
        var village = villages.findById(request.villageId()).orElseThrow(() -> missing("Village"));
        if (village.archived() || village.status() != VillageLookupStatus.VERIFIED)
            throw new DuplicateResourceException("Village must be unarchived and VERIFIED");
        if (!sources.existsById(request.sourceId())) throw missing("Source");
    }

    private void duplicate(PopulationRecordRequest r, Long id) {
        boolean exists = id == null
                ? repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(r.villageId(), r.referenceYear(), r.methodology(), r.sourceId())
                : repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(r.villageId(), r.referenceYear(), r.methodology(), r.sourceId(), id);
        if (exists) throw new DuplicateResourceException("Population observation already exists");
    }

    private void flushSave(PopulationRecord record) {
        try { repository.saveAndFlush(record); }
        catch (DataIntegrityViolationException exception) {
            // Match SQL state and exact constraint, never arbitrary database message text.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())
                        && "uq_population_observation".equals(constraint.getConstraintName()))
                    throw new DuplicateResourceException("Population observation already exists");
            }
            throw exception;
        }
    }

    private AuditSnapshot snapshot(PopulationRecord record) {
        return new AuditSnapshot(record.getVillageId(), record.getSourceId(), record.getReferenceYear(), record.getPopulationCount());
    }
    private ResponseStatusException missing(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found"); }
    private ResponseStatusException badRequest() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid population request"); }
}
