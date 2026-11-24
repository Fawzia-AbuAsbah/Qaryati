
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.entity.VillageAgriculture;
import com.qaryati.agriculture.mapper.VillageAgricultureMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.agriculture.repository.VillageAgricultureRepository;
import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import com.qaryati.village.api.VillageLookupApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class VillageAgricultureService {

    private final VillageAgricultureRepository repository;
    private final AgriculturalProductRepository productRepository;
    private final VillageAgricultureMapper mapper;
    private final VillageLookupApi villages;

    public VillageAgricultureService(
            VillageAgricultureRepository repository,
            AgriculturalProductRepository productRepository,
            VillageAgricultureMapper mapper,
            VillageLookupApi villages) {

        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.villages = villages;
    }

    @Transactional(readOnly = true)
    public VillageAgricultureResponse getById(Long id) {

        validateId(id);

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

        validateId(villageId);

        return repository.findByVillageId(villageId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VillageAgricultureResponse> getByProductId(
            Long productId) {

        validateId(productId);

        return repository.findByProductId(productId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public VillageAgricultureResponse update(
            Long id,
            VillageAgricultureRequest request) {

        validateId(id);

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

        return save(relation);
    }

    @Transactional
    public void delete(Long id) {

        validateId(id);

        VillageAgriculture relation = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Village agriculture record not found"
                ));

        try {
            repository.delete(relation);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    @Transactional
    public VillageAgricultureResponse create(
            VillageAgricultureRequest request) {

        requireVillage(request.villageId());

        if (!productRepository.existsById(request.productId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agricultural product not found"
            );
        }

        if (repository.existsByVillageIdAndProductId(
                request.villageId(), request.productId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is already linked to this village"
            );
        }

        return save(mapper.toEntity(request));
    }

    private void requireVillage(Long villageId) {
        validateId(villageId);
        if (villages.findById(villageId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Village not found"
            );
        }
    }

    private VillageAgricultureResponse save(VillageAgriculture relation) {
        try {
            return mapper.toResponse(repository.saveAndFlush(relation));
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "IDs must be positive"
            );
        }
    }

    private ResponseStatusException integrityConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Village agriculture write conflicts with database constraints"
        );
    }
}
