
package com.qaryati.village.service;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.dto.GovernorateResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.mapper.GovernorateMapper;
import com.qaryati.village.repository.GovernorateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GovernorateService {

    private final GovernorateRepository repository;
    private final GovernorateMapper mapper;

    public GovernorateService(
            GovernorateRepository repository,
            GovernorateMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public GovernorateResponse create(GovernorateRequest request) {
        validateUniqueFields(request, null);

        Governorate governorate = mapper.toEntity(request);

        try {
            Governorate saved = repository.saveAndFlush(governorate);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Governorate violates a database constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<GovernorateResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GovernorateResponse getById(Long id) {
        return mapper.toResponse(findGovernorate(id));
    }

    @Transactional
    public GovernorateResponse update(
            Long id, GovernorateRequest request) {

        Governorate governorate = findGovernorate(id);
        validateUniqueFields(request, id);

        governorate.setNameAr(request.nameAr());
        governorate.setNameEn(request.nameEn());
        governorate.setCode(request.code());

        try {
            Governorate saved = repository.saveAndFlush(governorate);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Governorate violates a database constraint"
            );
        }
    }

    @Transactional
    public void delete(Long id) {
        Governorate governorate = findGovernorate(id);

        try {
            repository.delete(governorate);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Cannot delete a governorate referenced by other records"
            );
        }
    }

    private Governorate findGovernorate(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Governorate", id)
                );
    }

    private void validateUniqueFields(
            GovernorateRequest request, Long currentId) {

        boolean duplicateName = currentId == null
                ? repository.existsByNameAr(request.nameAr())
                : repository.existsByNameArAndIdNot(
                request.nameAr(), currentId);

        if (duplicateName) {
            throw new DuplicateResourceException(
                    "Governorate Arabic name already exists"
            );
        }

        if (request.code() != null) {
            boolean duplicateCode = currentId == null
                    ? repository.existsByCode(request.code())
                    : repository.existsByCodeAndIdNot(
                    request.code(), currentId);

            if (duplicateCode) {
                throw new DuplicateResourceException(
                        "Governorate code already exists"
                );
            }
        }
    }
}
