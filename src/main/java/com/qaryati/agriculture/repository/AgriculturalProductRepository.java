
package com.qaryati.agriculture.repository;

import com.qaryati.agriculture.entity.AgriculturalProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgriculturalProductRepository
        extends JpaRepository<AgriculturalProduct, Long> {

    Optional<AgriculturalProduct> findByNameAr(String nameAr);

    boolean existsByNameAr(String nameAr);
}
