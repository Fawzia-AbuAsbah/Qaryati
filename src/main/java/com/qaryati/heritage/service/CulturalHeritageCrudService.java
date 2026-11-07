package com.qaryati.heritage.service;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.CulturalHeritageRepository;
import com.qaryati.dataquality.api.*;
import com.qaryati.village.api.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CulturalHeritageCrudService {
    private final CulturalHeritageRepository repository;
    private final VillageLookupApi villages;
    private final SourceLookup sources;
    private final CulturalHeritageSourceLinks links;
    private final AuditWriter audits;
    private final Validator validator;

    public CulturalHeritageCrudService(CulturalHeritageRepository repository, VillageLookupApi villages,
            SourceLookup sources, CulturalHeritageSourceLinks links, AuditWriter audits, Validator validator) {
        this.repository = repository; this.villages = villages; this.sources = sources;
        this.links = links; this.audits = audits; this.validator = validator;
    }

    @Transactional
    public CulturalHeritageCrudResponse create(CulturalHeritageCrudRequest request) {
        authorize(true); validate(request); validateReferences(request);
        if (repository.existsByVillageIdAndNameAndHeritageType(request.villageId(), request.name(), request.heritageType())) throw error(HttpStatus.CONFLICT, "Duplicate cultural heritage tuple");
        var heritage = CulturalHeritage.draft(foundation(request));
        save(heritage);
        sourceWrite(() -> links.linkNewCulturalHeritage(heritage.getId(), request.sourceIds()));
        var ids = sourceIds(heritage.getId());
        audits.recordCulturalHeritage(heritage.getId(), AuditAction.CREATE, null, snapshot(heritage, ids));
        return response(heritage, ids);
    }

    public CulturalHeritageCrudPage getAll(int page, int size, Long villageId, CulturalHeritageType heritageType, VerificationStatus verificationStatus) {
        boolean privileged = authorize(false);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        if (villageId != null) validId(villageId);
        var result = repository.findVisiblePage(villageId, heritageType, verificationStatus, !privileged,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        Map<Long, List<Long>> sourceMap;
        try { sourceMap = links.sourceIdsByCulturalHeritageIds(result.stream().map(CulturalHeritage::getId).toList()); }
        catch (NoSuchElementException exception) { throw missing("Cultural heritage"); }
        return new CulturalHeritageCrudPage(result.stream().map(e -> response(e, sourceMap.get(e.getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public CulturalHeritageCrudResponse getById(Long id) {
        boolean privileged = authorize(false); validId(id);
        var heritage = (privileged ? repository.findById(id)
                : repository.findByIdAndVerificationStatus(id, VerificationStatus.VERIFIED))
                .orElseThrow(() -> missing("Cultural heritage"));
        return response(heritage, sourceIds(id));
    }

    @Transactional
    public CulturalHeritageCrudResponse update(Long id, CulturalHeritageCrudRequest request) {
        authorize(true); validId(id); validate(request);
        var heritage = lockedDraft(id);
        if (!heritage.getVillageId().equals(request.villageId()))
            throw error(HttpStatus.CONFLICT, "Village ID cannot be changed");
        var before = snapshot(heritage, sourceIds(id));
        validateReferences(request);
        if (repository.existsByVillageIdAndNameAndHeritageTypeAndIdNot(request.villageId(), request.name(), request.heritageType(), id))
            throw error(HttpStatus.CONFLICT, "Duplicate cultural heritage tuple");
        heritage.reviseDraft(foundation(request));
        save(heritage);
        sourceWrite(() -> links.replaceSources(id, request.sourceIds()));
        var ids = sourceIds(id);
        audits.recordCulturalHeritage(id, AuditAction.UPDATE, before, snapshot(heritage, ids));
        return response(heritage, ids);
    }

    @Transactional
    public void delete(Long id) {
        authorize(true); validId(id);
        var heritage = lockedDraft(id);
        var before = snapshot(heritage, sourceIds(id));
        repository.delete(heritage); repository.flush();
        audits.recordCulturalHeritage(id, AuditAction.DELETE, before, null);
    }

    private CulturalHeritage lockedDraft(Long id) {
        var heritage = repository.findByIdForUpdate(id).orElseThrow(() -> missing("Cultural heritage"));
        if (heritage.getVerificationStatus() != VerificationStatus.DRAFT)
            throw error(HttpStatus.CONFLICT, "Only DRAFT cultural heritage records may be mutated");
        heritage.requireDraftMutation();
        return heritage;
    }
    private boolean authorize(boolean mutation) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        boolean privileged = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EDITOR"));
        boolean viewer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER"));
        if (!privileged && (mutation || !viewer)) throw new AccessDeniedException("Insufficient permissions");
        return privileged;
    }
    private void validate(CulturalHeritageCrudRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Invalid cultural heritage request");
    }
    private void validId(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Invalid ID");
    }
    private void validateReferences(CulturalHeritageCrudRequest request) {
        var village = villages.findById(request.villageId()).orElseThrow(() -> missing("Village"));
        if (village.archived() || village.status() != VillageLookupStatus.VERIFIED)
            throw error(HttpStatus.CONFLICT, "Village must be unarchived and VERIFIED");
        sourceWrite(() -> request.sourceIds().stream().distinct().forEach(sources::requireExists));
    }
    private List<Long> sourceIds(Long id) {
        try { return links.sourceIds(id); }
        catch (NoSuchElementException exception) { throw missing("Cultural heritage or source"); }
    }
    private void sourceWrite(Runnable operation) {
        try { operation.run(); }
        catch (NoSuchElementException exception) { throw missing("Cultural heritage or source"); }
    }
    private CulturalHeritageAuditSnapshot snapshot(CulturalHeritage e, List<Long> ids) {
        return new CulturalHeritageAuditSnapshot(e.getVillageId(), e.getName(), e.getHeritageType().name(), e.getDescription(), ids, e.getVerificationStatus().name());
    }
    private CulturalHeritageCrudResponse response(CulturalHeritage e, List<Long> ids) {
        return new CulturalHeritageCrudResponse(e.getId(), e.getVillageId(), e.getName(), e.getHeritageType(), e.getDescription(), e.getVerificationStatus(), ids);
    }
    private CulturalHeritageRequest foundation(CulturalHeritageCrudRequest request) {
        return new CulturalHeritageRequest(request.villageId(), request.name(), request.heritageType(), request.description());
    }
    private void save(CulturalHeritage heritage) {
        try { repository.saveAndFlush(heritage); }
        catch (org.springframework.dao.DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())
                        && "cultural_heritage_village_id_name_heritage_type_key".equals(constraint.getConstraintName()))
                    throw error(HttpStatus.CONFLICT, "Duplicate cultural heritage tuple");
            }
            throw failure;
        }
    }
    private ResponseStatusException missing(String type) { return error(HttpStatus.NOT_FOUND, type + " not found"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
