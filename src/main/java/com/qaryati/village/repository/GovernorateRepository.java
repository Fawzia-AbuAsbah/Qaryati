
package com.qaryati.village.repository;

import com.qaryati.village.entity.Governorate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GovernorateRepository
        extends JpaRepository<Governorate, Long> {

    boolean existsByNameAr(String nameAr);

    boolean existsByCode(String code);

    boolean existsByNameArAndIdNot(String nameAr, Long id);

    boolean existsByCodeAndIdNot(String code, Long id);
}
