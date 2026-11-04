package com.qaryati.village.service;

import com.qaryati.common.exception.*;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageNameMapper;
import com.qaryati.village.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class VillageNameService {
    private final VillageRepository villages;
    private final VillageNameRepository names;
    private final VillageNameMapper mapper;

    public VillageNameService(VillageRepository villages, VillageNameRepository names, VillageNameMapper mapper) {
        this.villages = villages;
        this.names = names;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<VillageNameResponse> getAll(Long villageId) {
        parent(villageId, false);
        return names.findByVillageIdOrderByIdAsc(villageId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VillageNameResponse getById(Long villageId, Long nameId) {
        validateId(nameId);
        parent(villageId, false);
        return mapper.toResponse(child(villageId, nameId));
    }

    @Transactional
    public VillageNameResponse create(Long villageId, VillageNameRequest request) {
        var village = parent(villageId, true);
        editable(village);
        unique(villageId, request, null);
        var entity = mapper.toEntity(request);
        entity.setVillage(village);
        return save(entity);
    }

    @Transactional
    public VillageNameResponse update(Long villageId, Long nameId, VillageNameRequest request) {
        validateId(nameId);
        var village = parent(villageId, true);
        var entity = child(villageId, nameId);
        editable(village);
        unique(villageId, request, nameId);
        mapper.updateEntity(request, entity);
        return save(entity);
    }

    @Transactional
    public void delete(Long villageId, Long nameId) {
        validateId(nameId);
        var village = parent(villageId, true);
        var entity = child(villageId, nameId);
        editable(village);
        try {
            names.delete(entity);
            names.flush();
        } catch (DataIntegrityViolationException ex) {
            throw integrityConflict();
        }
    }

    private Village parent(Long id, boolean lock) {
        validateId(id);
        return (lock ? villages.findByIdForUpdate(id) : villages.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Village", id));
    }

    private VillageName child(Long villageId, Long nameId) {
        return names.findByIdAndVillageId(nameId, villageId)
                .orElseThrow(() -> new ResourceNotFoundException("VillageName", nameId));
    }

    private void validateId(Long id) {
        if (id == null || id <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs must be positive");
    }

    private void editable(Village village) {
        if (village.getArchivedAt() != null || village.getStatus() != VillageStatus.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Names require an unarchived DRAFT village");
    }

    private void unique(Long villageId, VillageNameRequest request, Long currentId) {
        boolean duplicate = currentId == null
                ? names.existsByVillageIdAndNameAndLanguageCodeAndNameType(
                        villageId, request.name(), request.languageCode(), request.nameType())
                : names.existsByVillageIdAndNameAndLanguageCodeAndNameTypeAndIdNot(
                        villageId, request.name(), request.languageCode(), request.nameType(), currentId);
        if (duplicate) throw new DuplicateResourceException("Village name tuple already exists");
    }

    private VillageNameResponse save(VillageName entity) {
        try {
            return mapper.toResponse(names.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Only integrity failures are translated; locks/connection failures propagate.
            // Leave the failed transaction without retrying or revealing SQL details.
            throw integrityConflict();
        }
    }

    private ResponseStatusException integrityConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Village name write conflicts with database constraints");
    }
}