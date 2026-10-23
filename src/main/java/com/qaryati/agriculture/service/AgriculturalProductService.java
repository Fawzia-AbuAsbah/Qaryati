
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.dto.AgriculturalProductRequest;
import com.qaryati.agriculture.dto.AgriculturalProductResponse;
import com.qaryati.agriculture.entity.AgriculturalProduct;
import com.qaryati.agriculture.mapper.AgriculturalProductMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AgriculturalProductService {

    private final AgriculturalProductRepository repository;
    private final AgriculturalProductMapper mapper;

    public AgriculturalProductService(
            AgriculturalProductRepository repository,
            AgriculturalProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public AgriculturalProductResponse create(
            AgriculturalProductRequest request) {

        if (repository.existsByNameAr(request.nameAr())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Agricultural product already exists"
            );
        }

        AgriculturalProduct product = mapper.toEntity(request);

        try {
            AgriculturalProduct saved = repository.saveAndFlush(product);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product violates a database constraint",
                    ex
            );
        }
    }

    @Transactional(readOnly = true)
    public List<AgriculturalProductResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgriculturalProductResponse getById(Long id) {
        AgriculturalProduct product = findProduct(id);
        return mapper.toResponse(product);
    }

    @Transactional
    public AgriculturalProductResponse update(
            Long id,
            AgriculturalProductRequest request) {

        AgriculturalProduct product = findProduct(id);

        repository.findByNameAr(request.nameAr())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Agricultural product already exists"
                    );
                });

        mapper.updateEntity(request, product);

        try {
            AgriculturalProduct saved = repository.saveAndFlush(product);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product violates a database constraint",
                    ex
            );
        }
    }

    @Transactional
    public void delete(Long id) {
        AgriculturalProduct product = findProduct(id);

        try {
            repository.delete(product);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete a product referenced by a village",
                    ex
            );
        }
    }

    private AgriculturalProduct findProduct(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Agricultural product not found"
                ));
    }
}
