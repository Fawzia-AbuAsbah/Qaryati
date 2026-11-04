package com.qaryati.village.service;

import com.qaryati.common.exception.*;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageRelationMapper;
import com.qaryati.village.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class VillageRelationService {
    private final VillageRepository villages;
    private final VillageRelationRepository relations;
    private final VillageRelationMapper mapper;

    public VillageRelationService(VillageRepository villages, VillageRelationRepository relations, VillageRelationMapper mapper) {
        this.villages = villages;
        this.relations = relations;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<VillageRelationResponse> getAll(Long villageId) {
        parent(villageId, false);
        return relations.findByVillageIdOrderByIdAsc(villageId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VillageRelationResponse getById(Long villageId, Long relationId) {
        validateId(relationId);
        parent(villageId, false);
        return mapper.toResponse(child(villageId, relationId));
    }

    @Transactional
    public VillageRelationResponse create(Long villageId, VillageRelationRequest request) {
        var village = parent(villageId, true);
        editable(village);
        var target = target(villageId, request);
        unique(villageId, request, null);
        var entity = mapper.toEntity(request);
        entity.setVillage(village);
        entity.setRelatedVillage(target);
        return save(entity);
    }

    @Transactional
    public VillageRelationResponse update(Long villageId, Long relationId, VillageRelationRequest request) {
        validateId(relationId);
        var village = parent(villageId, true);
        var entity = child(villageId, relationId);
        editable(village);
        var target = target(villageId, request);
        unique(villageId, request, relationId);
        mapper.updateEntity(request, entity);
        entity.setRelatedVillage(target);
        return save(entity);
    }

    @Transactional
    public void delete(Long villageId, Long relationId) {
        validateId(relationId);
        var village = parent(villageId, true);
        var entity = child(villageId, relationId);
        editable(village);
        try {
            relations.delete(entity);
            relations.flush();
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    private Village parent(Long id, boolean lock) {
        validateId(id);
        return (lock ? villages.findByIdForUpdate(id) : villages.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Village", id));
    }

    private VillageRelation child(Long villageId, Long relationId) {
        return relations.findByIdAndVillageId(relationId, villageId)
                .orElseThrow(() -> new ResourceNotFoundException("VillageRelation", relationId));
    }

    private void validateId(Long id) {
        if (id == null || id <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs must be positive");
    }

    private void editable(Village village) {
        if (village.getArchivedAt() != null || village.getStatus() != VillageStatus.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Relations require an unarchived DRAFT village");
    }

    private void unique(Long villageId, VillageRelationRequest request, Long currentId) {
        boolean duplicate;
        if (request.relatedVillageId() != null) {
            duplicate = currentId == null
                    ? relations.existsByVillageIdAndRelatedVillageIdAndRelationType(villageId, request.relatedVillageId(), request.relationType())
                    : relations.existsByVillageIdAndRelatedVillageIdAndRelationTypeAndIdNot(villageId, request.relatedVillageId(), request.relationType(), currentId);
        } else {
            duplicate = currentId == null
                    ? relations.existsByVillageIdAndRelatedPlaceNameAndRelationType(villageId, request.relatedPlaceName(), request.relationType())
                    : relations.existsByVillageIdAndRelatedPlaceNameAndRelationTypeAndIdNot(villageId, request.relatedPlaceName(), request.relationType(), currentId);
        }
        if (duplicate) throw new DuplicateResourceException("Village relation tuple already exists");
    }

    private Village target(Long sourceId, VillageRelationRequest request) {
        if (request.relatedVillageId() == null) return null;
        validateId(request.relatedVillageId());
        if (sourceId.equals(request.relatedVillageId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Self-relations are not allowed");
        return villages.findById(request.relatedVillageId())
                .orElseThrow(() -> new ResourceNotFoundException("Village", request.relatedVillageId()));
    }
    private VillageRelationResponse save(VillageRelation entity) {
        try {
            return mapper.toResponse(relations.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Only integrity failures are translated; locks/connection failures propagate.
            // Leave the failed transaction without retrying or revealing SQL details.
            throw integrityConflict();
        }
    }

    private ResponseStatusException integrityConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Village relation write conflicts with database constraints");
    }
}
