package com.qaryati.village.service;

import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import com.qaryati.village.api.VillageLookupStatus;
import com.qaryati.village.repository.VillageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class VillageLookupService implements VillageLookupApi {

    private final VillageRepository repository;

    public VillageLookupService(VillageRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VillageLookupResult> findById(Long villageId) {
        if (villageId == null || villageId <= 0) {
            throw new IllegalArgumentException("Village ID must be positive");
        }
        return repository.findById(villageId).map(village -> new VillageLookupResult(
                village.getId(),
                switch (village.getStatus()) {
                    case DRAFT -> VillageLookupStatus.DRAFT;
                    case UNDER_REVIEW -> VillageLookupStatus.UNDER_REVIEW;
                    case VERIFIED -> VillageLookupStatus.VERIFIED;
                    case REJECTED -> VillageLookupStatus.REJECTED;
                },
                village.getArchivedAt() != null));
    }
}
