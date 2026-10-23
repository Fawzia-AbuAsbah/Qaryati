
package com.qaryati.agriculture.repository;

import com.qaryati.agriculture.entity.AgriculturalProduct;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgriculturalProductRepositoryIntegrationTest {

    @Autowired
    private AgriculturalProductRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndFindProductByNameAr() {

        String uniqueName =
                "زيتون_" + UUID.randomUUID()
                        .toString().substring(0, 8);

        AgriculturalProduct product =
                new AgriculturalProduct();

        product.setNameAr(uniqueName);
        product.setNameEn("Olives");
        product.setProductType("CROP");

        repository.saveAndFlush(product);

        entityManager.clear();

        AgriculturalProduct found =
                repository.findByNameAr(uniqueName)
                        .orElseThrow();

        assertNotNull(found.getId());
        assertEquals(uniqueName, found.getNameAr());
        assertEquals("Olives", found.getNameEn());
        assertEquals("CROP", found.getProductType());
    }

    @Test
    void shouldDetectExistingProductByNameAr() {

        String uniqueName =
                "قمح_" + UUID.randomUUID()
                        .toString().substring(0, 8);

        AgriculturalProduct product =
                new AgriculturalProduct();

        product.setNameAr(uniqueName);
        product.setNameEn("Wheat");
        product.setProductType("CROP");

        repository.saveAndFlush(product);

        assertTrue(repository.existsByNameAr(uniqueName));
        assertFalse(repository.existsByNameAr(
                "unknown_" + UUID.randomUUID()));
    }

    @Test
    void shouldDeleteProduct() {

        AgriculturalProduct product =
                new AgriculturalProduct();

        product.setNameAr(
                "لوز_" + UUID.randomUUID()
                        .toString().substring(0, 8));

        product.setProductType("CROP");

        AgriculturalProduct saved =
                repository.saveAndFlush(product);

        Long id = saved.getId();

        repository.delete(saved);
        repository.flush();
        entityManager.clear();

        assertFalse(repository.findById(id).isPresent());
    }
}
