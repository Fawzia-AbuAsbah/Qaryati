
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.entity.VillageAgriculture;
import com.qaryati.agriculture.mapper.VillageAgricultureMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.agriculture.repository.VillageAgricultureRepository;
import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import java.util.List;

@Service
public class VillageAgricultureService {

    private final VillageAgricultureRepository repository;
    private final AgriculturalProductRepository productRepository;
    private final VillageAgricultureMapper mapper;

    public VillageAgricultureService(
            VillageAgricultureRepository repository,
            AgriculturalProductRepository productRepository,
            VillageAgricultureMapper mapper) {

        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public VillageAgricultureResponse getById(Long id) {

        VillageAgriculture relation = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Village agriculture record not found"
                ));

        return mapper.toResponse(relation);
    }

    @Transactional(readOnly = true)
    public List<VillageAgricultureResponse> getByVillageId(
            Long villageId) {

        return repository.findByVillageId(villageId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VillageAgricultureResponse> getByProductId(
            Long productId) {

        return repository.findByProductId(productId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public VillageAgricultureResponse update(
            Long id,
            VillageAgricultureRequest request) {

        VillageAgriculture relation = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Village agriculture record not found"
                ));

        if (!relation.getVillageId().equals(request.villageId())
                || !relation.getProductId().equals(request.productId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Village ID and Product ID cannot be changed"
            );
        }

        mapper.updateEntity(request, relation);

        VillageAgriculture saved = repository.save(relation);

        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {

        VillageAgriculture relation = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Village agriculture record not found"
                ));

        repository.delete(relation);
    }

    @Transactional
    public VillageAgricultureResponse create(
            VillageAgricultureRequest request) {

        // 1. Check that the product exists
        if (!productRepository.existsById(request.productId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agricultural product not found"
            );
        }

        // 2. Check that the relationship does not already exist
        if (repository.existsByVillageIdAndProductId(
                request.villageId(), request.productId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is already linked to this village"
            );
        }

        // 3. Village verification is not implemented yet.
        // Do not save until the Village module exposes its public API.
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Village verification is not available yet"
        );
    }

}
