package com.qaryati.heritage.service;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HeritageSiteRepository;
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
public class HeritageSiteService {
    private final HeritageSiteRepository repository;
    private final VillageLookupApi villages;
    private final HeritageSiteSourceLinks links;
    private final AuditWriter audits;
    private final Validator validator;

    public HeritageSiteService(HeritageSiteRepository repository, VillageLookupApi villages,
            HeritageSiteSourceLinks links, AuditWriter audits, Validator validator) {
        this.repository = repository; this.villages = villages;
        this.links = links; this.audits = audits; this.validator = validator;
    }

    @Transactional
    public HeritageSiteResponse create(HeritageSiteRequest request) {
        authorize(true); validate(request); validateReferences(request);
        var site = HeritageSite.draft(request);
        repository.saveAndFlush(site);
        sourceWrite(() -> links.linkNewSite(site.getId(), request.sourceIds().stream().distinct().sorted().toList()));
        var ids = sourceIds(site.getId());
        audits.recordHeritageSite(site.getId(), AuditAction.CREATE, null, snapshot(site, ids));
        return response(site, ids);
    }

    public HeritageSitePage getAll(int page, int size, Long villageId) {
        boolean privileged = authorize(false);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        if (villageId != null) validId(villageId);
        var result = repository.findVisiblePage(villageId, privileged ? null : VerificationStatus.VERIFIED,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        var associations = result.isEmpty() ? Map.<Long, List<Long>>of()
                : sourceRead(() -> links.sourceIdsBySiteIds(result.stream().map(HeritageSite::getId).toList()));
        return new HeritageSitePage(result.stream().map(e -> response(e, associations.get(e.getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public HeritageSiteResponse getById(Long id) {
        boolean privileged = authorize(false); validId(id);
        var site = (privileged ? repository.findById(id)
                : repository.findByIdAndVerificationStatus(id, VerificationStatus.VERIFIED))
                .orElseThrow(() -> missing("Heritage site"));
        return response(site, sourceIds(id));
    }

    @Transactional
    public HeritageSiteResponse update(Long id, HeritageSiteRequest request) {
        authorize(true); validId(id); validate(request);
        var site = lockedDraft(id);
        var before = snapshot(site, sourceIds(id));
        if (!site.getVillageId().equals(request.villageId()))
            throw error(HttpStatus.CONFLICT, "Village ID cannot be changed");
        validateReferences(request);
        site.reviseDraft(request);
        repository.saveAndFlush(site);
        sourceWrite(() -> links.replaceSources(id, request.sourceIds().stream().distinct().sorted().toList()));
        var ids = sourceIds(id);
        audits.recordHeritageSite(id, AuditAction.UPDATE, before, snapshot(site, ids));
        return response(site, ids);
    }

    @Transactional
    public void delete(Long id) {
        authorize(true); validId(id);
        var site = lockedDraft(id);
        var before = snapshot(site, sourceIds(id));
        repository.delete(site); repository.flush();
        audits.recordHeritageSite(id, AuditAction.DELETE, before, null);
    }

    private HeritageSite lockedDraft(Long id) {
        var site = repository.findByIdForUpdate(id).orElseThrow(() -> missing("Heritage site"));
        if (site.getVerificationStatus() != VerificationStatus.DRAFT)
            throw error(HttpStatus.CONFLICT, "Only DRAFT heritage sites may be mutated");
        site.requireDraftMutation();
        return site;
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
    private void validate(HeritageSiteRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Invalid heritage site request");
    }
    private void validId(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Invalid ID");
    }
    private void validateReferences(HeritageSiteRequest request) {
        var village = villages.findById(request.villageId()).orElseThrow(() -> missing("Village"));
        if (village.archived() || village.status() != VillageLookupStatus.VERIFIED)
            throw error(HttpStatus.CONFLICT, "Village must be unarchived and VERIFIED");
        // The public association API validates source existence in this transaction.
    }
    private List<Long> sourceIds(Long id) {
        try { return links.sourceIds(id); }
        catch (NoSuchElementException exception) { throw missing("Heritage site or source"); }
    }
    private void sourceWrite(Runnable operation) {
        try { operation.run(); }
        catch (NoSuchElementException exception) { throw missing("Heritage site or source"); }
    }
    private <T> T sourceRead(java.util.function.Supplier<T> operation) {
        try { return operation.get(); }
        catch (NoSuchElementException exception) { throw missing("Heritage site or source"); }
    }
    private HeritageSiteAuditSnapshot snapshot(HeritageSite e, List<Long> ids) {
        return new HeritageSiteAuditSnapshot(e.getVillageId(), e.getName(), e.getSiteType().name(),
                e.getDescription(), e.getLatitude(), e.getLongitude(), e.getConditionNotes(), ids,
                e.getVerificationStatus().name());
    }
    private HeritageSiteResponse response(HeritageSite e, List<Long> ids) {
        return new HeritageSiteResponse(e.getId(), e.getVillageId(), e.getName(), e.getSiteType(),
                e.getDescription(), e.getLatitude(), e.getLongitude(), e.getConditionNotes(), e.getVerificationStatus(), ids);
    }
    private ResponseStatusException missing(String type) { return error(HttpStatus.NOT_FOUND, type + " not found"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
