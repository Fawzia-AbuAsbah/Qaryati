package com.qaryati.community.service;

import com.qaryati.community.dto.CommunityFacilityRequest;
import com.qaryati.community.dto.CommunityFacilityResponse;
import com.qaryati.community.entity.CommunityFacility;
import com.qaryati.community.mapper.CommunityFacilityMapper;
import com.qaryati.community.repository.CommunityFacilityRepository;
import com.qaryati.village.api.VillageLookupApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CommunityFacilityService {

    private final CommunityFacilityRepository repository;
    private final CommunityFacilityMapper mapper;
    private final VillageLookupApi villages;

    public CommunityFacilityService(CommunityFacilityRepository repository, CommunityFacilityMapper mapper,
                                    VillageLookupApi villages) {
        this.repository = repository;
        this.mapper = mapper;
        this.villages = villages;
    }

    @Transactional
    public CommunityFacilityResponse create(CommunityFacilityRequest request) {
        requireVillage(request.villageId());
        return save(mapper.toEntity(request));
    }

    @Transactional(readOnly = true)
    public List<CommunityFacilityResponse> getAll(Long villageId) {
        if (villageId != null) {
            validateId(villageId);
            return repository.findByVillageId(villageId).stream().map(mapper::toResponse).toList();
        }
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CommunityFacilityResponse getById(Long id) {
        validateId(id);
        return mapper.toResponse(findFacility(id));
    }

    @Transactional
    public CommunityFacilityResponse update(Long id, CommunityFacilityRequest request) {
        validateId(id);
        CommunityFacility facility = findFacility(id);
        requireVillage(request.villageId());
        mapper.updateEntity(request, facility);
        return save(facility);
    }

    @Transactional
    public void delete(Long id) {
        validateId(id);
        try {
            repository.delete(findFacility(id));
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    private CommunityFacility findFacility(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Community facility not found"));
    }

    private void requireVillage(Long villageId) {
        validateId(villageId);
        if (villages.findById(villageId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Village not found");
        }
    }

    private CommunityFacilityResponse save(CommunityFacility facility) {
        try {
            return mapper.toResponse(repository.saveAndFlush(facility));
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
                "Community facility write conflicts with database constraints");
    }
}
