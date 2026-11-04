package com.qaryati.village.service;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.village.dto.VillageRequest;
import com.qaryati.village.dto.VillageResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.entity.Village;
import com.qaryati.village.entity.VillageStatus;
import com.qaryati.village.mapper.VillageMapper;
import com.qaryati.village.repository.GovernorateRepository;
import com.qaryati.village.repository.LocalAuthorityRepository;
import com.qaryati.village.repository.VillageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Request Bean Validation belongs to the caller; timestamps do not provide actor-aware auditing. */
@Service
public class VillageService {

    private final VillageRepository repository;
    private final GovernorateRepository governorateRepository;
    private final LocalAuthorityRepository localAuthorityRepository;
    private final VillageMapper mapper;
    private final Clock clock;

    @Autowired
    public VillageService(VillageRepository repository,
                          GovernorateRepository governorateRepository,
                          LocalAuthorityRepository localAuthorityRepository,
                          VillageMapper mapper) {
        this(repository, governorateRepository, localAuthorityRepository, mapper, Clock.systemUTC());
    }

    VillageService(VillageRepository repository,
                   GovernorateRepository governorateRepository,
                   LocalAuthorityRepository localAuthorityRepository,
                   VillageMapper mapper, Clock clock) {
        this.repository = repository;
        this.governorateRepository = governorateRepository;
        this.localAuthorityRepository = localAuthorityRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public VillageResponse create(VillageRequest request) {
        Governorate governorate = findGovernorate(request.governorateId());
        LocalAuthority authority = resolveAuthority(request.localAuthorityId(), governorate);
        validateUniqueName(governorate, request.nameAr(), null);

        Village village = mapper.toEntity(request);
        village.setGovernorate(governorate);
        village.setLocalAuthority(authority);
        village.setStatus(VillageStatus.DRAFT);
        village.setArchivedAt(null);
        Instant now = currentInstant();
        village.setCreatedAt(now);
        village.setUpdatedAt(now);
        return mapper.toResponse(saveAndFlush(village));
    }

    @Transactional(readOnly = true)
    public VillageResponse getById(Long id) {
        validateVillageId(id);
        Village village = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Village", id));
        return mapper.toResponse(village);
    }

    @Transactional(readOnly = true)
    public Page<VillageResponse> getActive(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be nonnegative and size must be between 1 and 100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page offset exceeds the supported limit");
        }
        // Repository supplies static ID ascending ordering; no caller sort is accepted.
        return repository.findByArchivedAtIsNullOrderByIdAsc(PageRequest.of(page, size))
                .map(mapper::toResponse);
    }

    @Transactional
    public VillageResponse update(Long id, VillageRequest request) {
        validateVillageId(id);
        Village village = findForUpdate(id);
        if (village.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archived villages cannot be edited");
        }
        if (village.getStatus() != VillageStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT villages can be edited");
        }

        Governorate requestedGovernorate = findGovernorate(request.governorateId());
        Governorate existingGovernorate = village.getGovernorate();
        if (!existingGovernorate.getId().equals(requestedGovernorate.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Changing a village's governorate is not supported");
        }
        LocalAuthority authority = resolveAuthority(request.localAuthorityId(), existingGovernorate);
        validateUniqueName(existingGovernorate, request.nameAr(), id);

        mapper.updateEntity(request, village);
        village.setLocalAuthority(authority);
        village.setUpdatedAt(currentInstant());
        return mapper.toResponse(saveAndFlush(village));
    }

    @Transactional
    public void archive(Long id) {
        validateVillageId(id);
        Village village = findForUpdate(id);
        if (village.getArchivedAt() != null) {
            return;
        }
        Instant now = currentInstant();
        village.setArchivedAt(now);
        village.setUpdatedAt(now);
        saveAndFlush(village);
    }

    private void validateVillageId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Village ID must be positive");
        }
    }

    private Village findForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Village", id));
    }

    private Governorate findGovernorate(Long id) {
        return governorateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Governorate", id));
    }

    private LocalAuthority resolveAuthority(Long id, Governorate governorate) {
        if (id == null) {
            return null;
        }
        LocalAuthority authority = localAuthorityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LocalAuthority", id));
        if (!authority.getGovernorate().getId().equals(governorate.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Local authority must belong to the village's governorate");
        }
        return authority;
    }

    private void validateUniqueName(Governorate governorate, String nameAr, Long currentId) {
        boolean duplicate = currentId == null
                ? repository.existsByGovernorateAndNameAr(governorate, nameAr)
                : repository.existsByGovernorateAndNameArAndIdNot(governorate, nameAr, currentId);
        if (duplicate) {
            throw new DuplicateResourceException(
                    "Village Arabic name already exists in this governorate");
        }
    }

    private Instant currentInstant() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private Village saveAndFlush(Village village) {
        try {
            return repository.saveAndFlush(village);
        } catch (DataIntegrityViolationException ex) {
            // Exit the failed transaction; do not retry or expose driver/SQL details.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Village write conflicts with database constraints");
        }
    }
}
