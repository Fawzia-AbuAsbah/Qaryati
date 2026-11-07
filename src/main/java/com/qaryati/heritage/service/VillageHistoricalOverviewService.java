package com.qaryati.heritage.service;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.VillageHistoryRepository;
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
public class VillageHistoricalOverviewService {
    private final VillageHistoryRepository repository;
    private final VillageLookupApi villages;
    private final SourceLookup sources;
    private final VillageHistorySourceLinks links;
    private final AuditWriter audits;
    private final Validator validator;

    public VillageHistoricalOverviewService(VillageHistoryRepository repository, VillageLookupApi villages,
            SourceLookup sources, VillageHistorySourceLinks links, AuditWriter audits, Validator validator) {
        this.repository = repository; this.villages = villages; this.sources = sources;
        this.links = links; this.audits = audits; this.validator = validator;
    }

    @Transactional
    public VillageHistoricalOverviewResponse create(VillageHistoricalOverviewRequest request) {
        authorize(true); validate(request); validateReferences(request);
        if (repository.existsByVillageId(request.villageId())) throw error(HttpStatus.CONFLICT, "Village already has a historical overview");
        var history = VillageHistory.draft(new VillageHistoryRequest(request.villageId(), request.overview()));
        try { repository.saveAndFlush(history); }
        catch (org.springframework.dao.DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())
                        && "village_histories_village_id_key".equals(constraint.getConstraintName()))
                    throw error(HttpStatus.CONFLICT, "Village already has a historical overview");
            }
            throw failure;
        }
        sourceWrite(() -> links.linkNewHistory(history.getId(), request.sourceIds()));
        var ids = sourceIds(history.getId());
        audits.recordVillageHistory(history.getId(), AuditAction.CREATE, null, snapshot(history, ids));
        return response(history, ids);
    }

    public VillageHistoricalOverviewPage getAll(int page, int size, Long villageId) {
        boolean privileged = authorize(false);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        if (villageId != null) validId(villageId);
        var result = repository.findVisiblePage(villageId, privileged ? null : VerificationStatus.VERIFIED,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        Map<Long, List<Long>> sourceMap;
        try { sourceMap = links.sourceIdsByHistoryIds(result.stream().map(VillageHistory::getId).toList()); }
        catch (NoSuchElementException exception) { throw missing("Village history"); }
        return new VillageHistoricalOverviewPage(result.stream().map(e -> response(e, sourceMap.get(e.getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public VillageHistoricalOverviewResponse getById(Long id) {
        boolean privileged = authorize(false); validId(id);
        var history = (privileged ? repository.findById(id)
                : repository.findByIdAndVerificationStatus(id, VerificationStatus.VERIFIED))
                .orElseThrow(() -> missing("Village history"));
        return response(history, sourceIds(id));
    }

    @Transactional
    public VillageHistoricalOverviewResponse update(Long id, VillageHistoricalOverviewRequest request) {
        authorize(true); validId(id); validate(request);
        var history = lockedDraft(id);
        if (!history.getVillageId().equals(request.villageId()))
            throw error(HttpStatus.CONFLICT, "Village ID cannot be changed");
        var before = snapshot(history, sourceIds(id));
        validateReferences(request);
        history.reviseDraft(new VillageHistoryRequest(request.villageId(), request.overview()));
        repository.saveAndFlush(history);
        sourceWrite(() -> links.replaceSources(id, request.sourceIds()));
        var ids = sourceIds(id);
        audits.recordVillageHistory(id, AuditAction.UPDATE, before, snapshot(history, ids));
        return response(history, ids);
    }

    @Transactional
    public void delete(Long id) {
        authorize(true); validId(id);
        var history = lockedDraft(id);
        var before = snapshot(history, sourceIds(id));
        repository.delete(history); repository.flush();
        audits.recordVillageHistory(id, AuditAction.DELETE, before, null);
    }

    private VillageHistory lockedDraft(Long id) {
        var history = repository.findByIdForUpdate(id).orElseThrow(() -> missing("Village history"));
        if (history.getVerificationStatus() != VerificationStatus.DRAFT)
            throw error(HttpStatus.CONFLICT, "Only DRAFT historical overviews may be mutated");
        history.requireDraftMutation();
        return history;
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
    private void validate(VillageHistoricalOverviewRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Invalid historical overview request");
    }
    private void validId(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Invalid ID");
    }
    private void validateReferences(VillageHistoricalOverviewRequest request) {
        var village = villages.findById(request.villageId()).orElseThrow(() -> missing("Village"));
        if (village.archived() || village.status() != VillageLookupStatus.VERIFIED)
            throw error(HttpStatus.CONFLICT, "Village must be unarchived and VERIFIED");
        sourceWrite(() -> request.sourceIds().stream().distinct().forEach(sources::requireExists));
    }
    private List<Long> sourceIds(Long id) {
        try { return links.sourceIds(id); }
        catch (NoSuchElementException exception) { throw missing("Village history or source"); }
    }
    private void sourceWrite(Runnable operation) {
        try { operation.run(); }
        catch (NoSuchElementException exception) { throw missing("Village history or source"); }
    }
    private VillageHistoryAuditSnapshot snapshot(VillageHistory e, List<Long> ids) {
        return new VillageHistoryAuditSnapshot(e.getVillageId(), e.getOverview(), ids, e.getVerificationStatus().name());
    }
    private VillageHistoricalOverviewResponse response(VillageHistory e, List<Long> ids) {
        return new VillageHistoricalOverviewResponse(e.getId(), e.getVillageId(), e.getOverview(),
                e.getUpdatedAt(), e.getVerificationStatus(), ids);
    }
    private ResponseStatusException missing(String type) { return error(HttpStatus.NOT_FOUND, type + " not found"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
