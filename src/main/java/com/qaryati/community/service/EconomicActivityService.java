package com.qaryati.community.service;

import com.qaryati.community.dto.EconomicActivityRequest;
import com.qaryati.community.dto.EconomicActivityResponse;
import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.mapper.EconomicActivityMapper;
import com.qaryati.community.repository.EconomicActivityRepository;
import com.qaryati.village.api.VillageLookupApi;
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

    public EconomicActivityService(EconomicActivityRepository repository, EconomicActivityMapper mapper,
                                   VillageLookupApi villages) {
        this.repository = repository;
        this.mapper = mapper;
        this.villages = villages;
    }

    @Transactional
    public EconomicActivityResponse create(EconomicActivityRequest request) {
        requireVillage(request.villageId());
        rejectDuplicate(request, null);
        return save(mapper.toEntity(request));
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
        mapper.updateEntity(request, activity);
        return save(activity);
    }

    @Transactional
    public void delete(Long id) {
        validateId(id);
        try {
            repository.delete(findActivity(id));
            repository.flush();
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

    private EconomicActivityResponse save(EconomicActivity activity) {
        try {
            return mapper.toResponse(repository.saveAndFlush(activity));
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
}
