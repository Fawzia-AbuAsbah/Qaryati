package com.qaryati.community.service;

import com.qaryati.community.dto.EconomicActivityRequest;
import com.qaryati.community.dto.EconomicActivityResponse;
import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.mapper.EconomicActivityMapper;
import com.qaryati.community.repository.EconomicActivityRepository;
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;
import com.qaryati.dataquality.api.EconomicActivityAuditSnapshot;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EconomicActivityService {

    private final EconomicActivityRepository repository;
    private final EconomicActivityMapper mapper;
    private final VillageLookupApi villages;
    private final AuditWriter audits;

    public EconomicActivityService(EconomicActivityRepository repository, EconomicActivityMapper mapper,
                                   VillageLookupApi villages, AuditWriter audits) {
        this.repository = repository;
        this.mapper = mapper;
        this.villages = villages;
        this.audits = audits;
    }

    @Transactional
    public EconomicActivityResponse create(EconomicActivityRequest request) {
        requireVillage(request.villageId());
        rejectDuplicate(request, null);
        EconomicActivity saved = save(mapper.toEntity(request));
        audits.recordEconomicActivity(saved.getId(), AuditAction.CREATE, null, snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EconomicActivityResponse> getAll(Long villageId) {
        if (villageId != null) {
            validateId(villageId);
            return repository.findByVillageId(villageId).stream().map(mapper::toResponse).toList();
        }
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EconomicActivityResponse getById(Long id) {
        validateId(id);
        return mapper.toResponse(findActivity(id));
    }

    @Transactional
    public EconomicActivityResponse update(Long id, EconomicActivityRequest request) {
        validateId(id);
        EconomicActivity activity = findActivity(id);
        requireVillage(request.villageId());
        rejectDuplicate(request, id);
        EconomicActivityAuditSnapshot before = snapshot(activity);
        mapper.updateEntity(request, activity);
        EconomicActivity saved = save(activity);
        audits.recordEconomicActivity(saved.getId(), AuditAction.UPDATE, before, snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        validateId(id);
        EconomicActivity activity = findActivity(id);
        EconomicActivityAuditSnapshot before = snapshot(activity);
        try {
            repository.delete(activity);
            repository.flush();
            audits.recordEconomicActivity(id, AuditAction.DELETE, before, null);
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    private EconomicActivity findActivity(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Economic activity not found"));
    }

    private void requireVillage(Long villageId) {
        validateId(villageId);
        if (villages.findById(villageId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Village not found");
        }
    }

    private void rejectDuplicate(EconomicActivityRequest request, Long currentId) {
        boolean duplicate = currentId == null
                ? repository.existsByVillageIdAndNameAndActivityType(
                        request.villageId(), request.name(), request.activityType())
                : repository.existsByVillageIdAndNameAndActivityTypeAndIdNot(
                        request.villageId(), request.name(), request.activityType(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Economic activity already exists for this village");
        }
    }

    private EconomicActivity save(EconomicActivity activity) {
        try {
            return repository.saveAndFlush(activity);
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs must be positive");
        }
    }

    private ResponseStatusException integrityConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Economic activity write conflicts with database constraints");
    }

    private EconomicActivityAuditSnapshot snapshot(EconomicActivity activity) {
        return new EconomicActivityAuditSnapshot(activity.getVillageId(), activity.getName(),
                activity.getActivityType().name());
    }
}
