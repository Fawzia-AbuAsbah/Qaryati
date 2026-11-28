
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.entity.VillageAgriculture;
import com.qaryati.agriculture.mapper.VillageAgricultureMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.agriculture.repository.VillageAgricultureRepository;
import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;
import com.qaryati.dataquality.api.VillageAgricultureAuditSnapshot;
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
    private final AuditWriter audits;

    public VillageAgricultureService(
            VillageAgricultureRepository repository,
            AgriculturalProductRepository productRepository,
            VillageAgricultureMapper mapper,
            VillageLookupApi villages,
            AuditWriter audits) {

        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.villages = villages;
        this.audits = audits;
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

        VillageAgricultureAuditSnapshot before = snapshot(relation);
        mapper.updateEntity(request, relation);
        VillageAgriculture saved = save(relation);
        audits.recordVillageAgriculture(saved.getId(), AuditAction.UPDATE, before, snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {

        validateId(id);

        VillageAgriculture relation = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Village agriculture record not found"
                ));
        VillageAgricultureAuditSnapshot before = snapshot(relation);

        try {
            repository.delete(relation);
            repository.flush();
            audits.recordVillageAgriculture(id, AuditAction.DELETE, before, null);
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

        VillageAgriculture saved = save(mapper.toEntity(request));
        audits.recordVillageAgriculture(saved.getId(), AuditAction.CREATE, null, snapshot(saved));
        return mapper.toResponse(saved);
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

    private VillageAgriculture save(VillageAgriculture relation) {
        try {
            return repository.saveAndFlush(relation);
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

    private VillageAgricultureAuditSnapshot snapshot(VillageAgriculture relation) {
        return new VillageAgricultureAuditSnapshot(relation.getVillageId(), relation.getProductId(),
                relation.getImportance() == null ? null : relation.getImportance().name());
    }
}
