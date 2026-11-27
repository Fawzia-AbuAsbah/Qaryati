package com.qaryati.village.service;

import com.qaryati.village.dto.PublicGovernorateResponse;
import com.qaryati.village.dto.PublicLocalAuthorityResponse;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.repository.GovernorateRepository;
import com.qaryati.village.repository.LocalAuthorityRepository;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicGeographicCatalogueService {
    private static final Sort CATALOGUE_ORDER = Sort.by(Sort.Direction.ASC, "id");

    private final GovernorateRepository governorates;
    private final LocalAuthorityRepository localAuthorities;

    public PublicGeographicCatalogueService(GovernorateRepository governorates,
                                            LocalAuthorityRepository localAuthorities) {
        this.governorates = governorates;
        this.localAuthorities = localAuthorities;
    }

    public List<PublicGovernorateResponse> getGovernorates() {
        return governorates.findAll(CATALOGUE_ORDER).stream()
                .map(governorate -> new PublicGovernorateResponse(
                        governorate.getId(), governorate.getNameAr(), governorate.getNameEn()))
                .toList();
    }

    public List<PublicLocalAuthorityResponse> getLocalAuthorities(Long governorateId) {
        if (governorateId != null && governorateId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Governorate ID must be positive");
        }
        var authorities = governorateId == null ? localAuthorities.findAll(CATALOGUE_ORDER)
                : localAuthorities.findByGovernorate_Id(governorateId, CATALOGUE_ORDER);
        return authorities.stream().map(this::toPublicResponse).toList();
    }

    private PublicLocalAuthorityResponse toPublicResponse(LocalAuthority authority) {
        var governorate = authority.getGovernorate();
        // Match VillageMapper: obtain the FK identity without initializing a parent proxy.
        Long governorateId = governorate instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getInternalIdentifier() : governorate.getId();
        return new PublicLocalAuthorityResponse(
                authority.getId(), authority.getNameAr(), authority.getNameEn(), governorateId);
    }
}
