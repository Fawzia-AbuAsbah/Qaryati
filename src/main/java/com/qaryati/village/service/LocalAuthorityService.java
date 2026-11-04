package com.qaryati.village.service;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.village.dto.LocalAuthorityRequest;
import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.mapper.LocalAuthorityMapper;
import com.qaryati.village.repository.GovernorateRepository;
import com.qaryati.village.repository.LocalAuthorityRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocalAuthorityService {

    private final LocalAuthorityRepository repository;
    private final GovernorateRepository governorateRepository;
    private final LocalAuthorityMapper mapper;

    public LocalAuthorityService(
            LocalAuthorityRepository repository,
            GovernorateRepository governorateRepository,
            LocalAuthorityMapper mapper) {
        this.repository = repository;
        this.governorateRepository = governorateRepository;
        this.mapper = mapper;
    }

    @Transactional
    public LocalAuthorityResponse create(LocalAuthorityRequest request) {
        Governorate governorate = findGovernorate(request.governorateId());
        validateUniqueName(governorate, request.nameAr(), null);

        LocalAuthority localAuthority = mapper.toEntity(request);
        localAuthority.setGovernorate(governorate);

        try {
            LocalAuthority saved = repository.saveAndFlush(localAuthority);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Local authority violates a database constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public LocalAuthorityResponse getById(Long id) {
        return mapper.toResponse(findLocalAuthority(id));
    }

    @Transactional(readOnly = true)
    public List<LocalAuthorityResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocalAuthorityResponse> getByGovernorate(Long governorateId) {
        Governorate governorate = findGovernorate(governorateId);
        return repository.findByGovernorate(governorate)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public LocalAuthorityResponse update(Long id, LocalAuthorityRequest request) {
        LocalAuthority localAuthority = findLocalAuthority(id);
        Governorate governorate = findGovernorate(request.governorateId());

        // Temporarily disallow reassignment to protect future Village relationships.
        if (!localAuthority.getGovernorate().getId().equals(governorate.getId())) {
            throw new DuplicateResourceException(
                    "Changing a local authority's governorate is not supported"
            );
        }

        validateUniqueName(localAuthority.getGovernorate(), request.nameAr(), id);
        mapper.updateEntity(request, localAuthority);

        try {
            LocalAuthority saved = repository.saveAndFlush(localAuthority);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Local authority violates a database constraint"
            );
        }
    }

    @Transactional
    public void delete(Long id) {
        LocalAuthority localAuthority = findLocalAuthority(id);

        try {
            repository.delete(localAuthority);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Cannot delete local authority because of existing references or a database constraint"
            );
        }
    }

    private LocalAuthority findLocalAuthority(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LocalAuthority", id));
    }

    private Governorate findGovernorate(Long id) {
        return governorateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Governorate", id));
    }

    private void validateUniqueName(Governorate governorate, String nameAr, Long currentId) {
        boolean duplicateName = currentId == null
                ? repository.existsByGovernorateAndNameAr(governorate, nameAr)
                : repository.existsByGovernorateAndNameArAndIdNot(governorate, nameAr, currentId);

        if (duplicateName) {
            throw new DuplicateResourceException(
                    "Local authority Arabic name already exists in this governorate"
            );
        }
    }
}
