package com.qaryati.village.repository;

import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface LocalAuthorityRepository
        extends JpaRepository<LocalAuthority, Long> {

    List<LocalAuthority> findByGovernorate(Governorate governorate);

    List<LocalAuthority> findByGovernorate_Id(Long governorateId, Sort sort);

    boolean existsByGovernorateAndNameAr(Governorate governorate, String nameAr);

    boolean existsByGovernorateAndNameArAndIdNot(
            Governorate governorate, String nameAr, Long id);
}
