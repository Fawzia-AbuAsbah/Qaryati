package com.qaryati.heritage.service;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HistoricalEventRepository;
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
public class HistoricalEventService {
    private final HistoricalEventRepository repository;
    private final VillageLookupApi villages;
    private final SourceLookup sources;
    private final EventSourceLinks links;
    private final AuditWriter audits;
    private final Validator validator;

    public HistoricalEventService(HistoricalEventRepository repository, VillageLookupApi villages,
            SourceLookup sources, EventSourceLinks links, AuditWriter audits, Validator validator) {
        this.repository = repository; this.villages = villages; this.sources = sources;
        this.links = links; this.audits = audits; this.validator = validator;
    }

    @Transactional
    public HistoricalEventResponse create(HistoricalEventRequest request) {
        authorize(true); validate(request); validateReferences(request);
        var event = HistoricalEvent.draft(request);
        repository.saveAndFlush(event);
        sourceWrite(() -> links.linkNewEvent(event.getId(), request.sourceIds()));
        var ids = sourceIds(event.getId());
        audits.recordHistoricalEvent(event.getId(), AuditAction.CREATE, null, snapshot(event, ids));
        return response(event, ids);
    }

    public HistoricalEventPage getAll(int page, int size, Long villageId) {
        boolean privileged = authorize(false);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        if (villageId != null) validId(villageId);
        var result = repository.findVisiblePage(villageId, privileged ? null : VerificationStatus.VERIFIED,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        return new HistoricalEventPage(result.stream().map(e -> response(e, sourceIds(e.getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public HistoricalEventResponse getById(Long id) {
        boolean privileged = authorize(false); validId(id);
        var event = (privileged ? repository.findById(id)
                : repository.findByIdAndVerificationStatus(id, VerificationStatus.VERIFIED))
                .orElseThrow(() -> missing("Historical event"));
        return response(event, sourceIds(id));
    }

    @Transactional
    public HistoricalEventResponse update(Long id, HistoricalEventRequest request) {
        authorize(true); validId(id); validate(request);
        var event = lockedDraft(id);
        if (!event.getVillageId().equals(request.villageId()))
            throw error(HttpStatus.CONFLICT, "Village ID cannot be changed");
        var before = snapshot(event, sourceIds(id));
        validateReferences(request);
        event.reviseDraft(request);
        repository.saveAndFlush(event);
        sourceWrite(() -> links.replaceSources(id, request.sourceIds()));
        var ids = sourceIds(id);
        audits.recordHistoricalEvent(id, AuditAction.UPDATE, before, snapshot(event, ids));
        return response(event, ids);
    }

    @Transactional
    public void delete(Long id) {
        authorize(true); validId(id);
        var event = lockedDraft(id);
        var before = snapshot(event, sourceIds(id));
        repository.delete(event); repository.flush();
        audits.recordHistoricalEvent(id, AuditAction.DELETE, before, null);
    }

    private HistoricalEvent lockedDraft(Long id) {
        var event = repository.findByIdForUpdate(id).orElseThrow(() -> missing("Historical event"));
        if (event.getVerificationStatus() != VerificationStatus.DRAFT)
            throw error(HttpStatus.CONFLICT, "Only DRAFT historical events may be mutated");
        event.requireDraftMutation();
        return event;
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
    private void validate(HistoricalEventRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Invalid historical event request");
    }
    private void validId(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Invalid ID");
    }
    private void validateReferences(HistoricalEventRequest request) {
        var village = villages.findById(request.villageId()).orElseThrow(() -> missing("Village"));
        if (village.archived() || village.status() != VillageLookupStatus.VERIFIED)
            throw error(HttpStatus.CONFLICT, "Village must be unarchived and VERIFIED");
        sourceWrite(() -> request.sourceIds().stream().distinct().forEach(sources::requireExists));
    }
    private List<Long> sourceIds(Long id) {
        try { return links.sourceIds(id); }
        catch (NoSuchElementException exception) { throw missing("Historical event or source"); }
    }
    private void sourceWrite(Runnable operation) {
        try { operation.run(); }
        catch (NoSuchElementException exception) { throw missing("Historical event or source"); }
    }
    private HistoricalEventAuditSnapshot snapshot(HistoricalEvent e, List<Long> ids) {
        return new HistoricalEventAuditSnapshot(e.getVillageId(), e.getTitle(), e.getEventDate(),
                e.getYearFrom(), e.getYearTo(), e.getDatePrecision().name(), ids, e.getVerificationStatus().name());
    }
    private HistoricalEventResponse response(HistoricalEvent e, List<Long> ids) {
        return new HistoricalEventResponse(e.getId(), e.getVillageId(), e.getTitle(), e.getDescription(),
                e.getEventDate(), e.getYearFrom(), e.getYearTo(), e.getDatePrecision(), e.getVerificationStatus(), e.getCreatedAt(), ids);
    }
    private ResponseStatusException missing(String type) { return error(HttpStatus.NOT_FOUND, type + " not found"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
