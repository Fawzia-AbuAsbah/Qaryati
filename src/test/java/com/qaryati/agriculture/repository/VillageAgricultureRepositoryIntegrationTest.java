
package com.qaryati.agriculture.repository;

import com.qaryati.agriculture.entity.AgriculturalProduct;
import com.qaryati.agriculture.entity.AgricultureImportance;
import com.qaryati.agriculture.entity.VillageAgriculture;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VillageAgricultureRepositoryIntegrationTest {

    @Autowired
    private VillageAgricultureRepository repository;

    @Autowired
    private AgriculturalProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long createTestVillage() {

        String suffix = UUID.randomUUID().toString();

        Long governorateId = jdbcTemplate.queryForObject(
                """
                INSERT INTO public.governorates (name_ar)
                VALUES (?)
                RETURNING id
                """,
                Long.class,
                "محافظة اختبار_" + suffix
        );

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO public.villages
                    (governorate_id, name_ar, location_description)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                governorateId,
                "قرية اختبار_" + suffix,
                "Test village location"
        );
    }

    private Long createTestProduct() {

        AgriculturalProduct product = new AgriculturalProduct();

        product.setNameAr(
                "منتج اختبار_" + UUID.randomUUID()
        );
        product.setNameEn("Test Product");
        product.setProductType("CROP");

        return productRepository.saveAndFlush(product).getId();
    }

    @Test
    void shouldSaveAndFindVillageAgriculture() {

        Long villageId = createTestVillage();
        Long productId = createTestProduct();

        VillageAgriculture relation =
                new VillageAgriculture(
                        villageId,
                        productId,
                        "Olive cultivation",
                        AgricultureImportance.HIGH
                );

        VillageAgriculture saved =
                repository.saveAndFlush(relation);

        Long relationId = saved.getId();

        entityManager.clear();

        VillageAgriculture found =
                repository.findById(relationId).orElseThrow();

        assertEquals(villageId, found.getVillageId());
        assertEquals(productId, found.getProductId());
        assertEquals("Olive cultivation",
                found.getDescription());
        assertEquals(AgricultureImportance.HIGH,
                found.getImportance());
    }

    @Test
    void shouldFindProductsByVillage() {

        Long villageId = createTestVillage();
        Long productId = createTestProduct();

        repository.saveAndFlush(
                new VillageAgriculture(
                        villageId,
                        productId,
                        "Important crop",
                        AgricultureImportance.MEDIUM
                )
        );

        assertTrue(
                repository.existsByVillageIdAndProductId(
                        villageId,
                        productId
                )
        );

        assertEquals(
                1,
                repository.findByVillageId(villageId).size()
        );

        assertEquals(
                1,
                repository.findByProductId(productId).size()
        );
    }

    @Test
    void shouldRejectDuplicateVillageProductRelation() {

        Long villageId = createTestVillage();
        Long productId = createTestProduct();

        repository.saveAndFlush(
                new VillageAgriculture(
                        villageId,
                        productId,
                        "First relation",
                        AgricultureImportance.LOW
                )
        );

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(
                        new VillageAgriculture(
                                villageId,
                                productId,
                                "Duplicate relation",
                                AgricultureImportance.HIGH
                        )
                )
        );


    }

    @Test
    void shouldRejectNonExistingVillage() {

        Long productId = createTestProduct();

        VillageAgriculture relation =
                new VillageAgriculture(
                        -9999L,
                        productId,
                        "Invalid village relation",
                        AgricultureImportance.HIGH
                );

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(relation)
        );
    }

    @Test
    void shouldAllowNullDescriptionAndImportance() {

        Long villageId = createTestVillage();
        Long productId = createTestProduct();

        VillageAgriculture relation =
                new VillageAgriculture(
                        villageId,
                        productId,
                        null,
                        null
                );

        VillageAgriculture saved =
                repository.saveAndFlush(relation);

        Long relationId = saved.getId();

        entityManager.clear();

        VillageAgriculture found =
                repository.findById(relationId).orElseThrow();

        assertNull(found.getDescription());
        assertNull(found.getImportance());

        assertEquals(villageId, found.getVillageId());
        assertEquals(productId, found.getProductId());
    }

}

