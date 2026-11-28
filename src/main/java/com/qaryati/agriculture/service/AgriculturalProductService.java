
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.dto.AgriculturalProductRequest;
import com.qaryati.agriculture.dto.AgriculturalProductResponse;
import com.qaryati.agriculture.entity.AgriculturalProduct;
import com.qaryati.agriculture.mapper.AgriculturalProductMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.dataquality.api.AgriculturalProductAuditSnapshot;
import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;
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
    private final AuditWriter audits;

    public AgriculturalProductService(
            AgriculturalProductRepository repository,
            AgriculturalProductMapper mapper,
            AuditWriter audits) {
        this.repository = repository;
        this.mapper = mapper;
        this.audits = audits;
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
            audits.recordAgriculturalProduct(saved.getId(), AuditAction.CREATE, null, snapshot(saved));
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

        AgriculturalProductAuditSnapshot before = snapshot(product);
        mapper.updateEntity(request, product);

        try {
            AgriculturalProduct saved = repository.saveAndFlush(product);
            audits.recordAgriculturalProduct(saved.getId(), AuditAction.UPDATE, before, snapshot(saved));
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
        AgriculturalProductAuditSnapshot before = snapshot(product);

        try {
            repository.delete(product);
            repository.flush();
            audits.recordAgriculturalProduct(id, AuditAction.DELETE, before, null);
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

    private AgriculturalProductAuditSnapshot snapshot(AgriculturalProduct product) {
        return new AgriculturalProductAuditSnapshot(product.getNameAr(), product.getNameEn(), product.getProductType());
    }
}
