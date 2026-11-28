
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.dto.AgriculturalProductRequest;
import com.qaryati.agriculture.dto.AgriculturalProductResponse;
import com.qaryati.agriculture.entity.AgriculturalProduct;
import com.qaryati.agriculture.mapper.AgriculturalProductMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.dataquality.api.AgriculturalProductAuditSnapshot;
import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgriculturalProductServiceTest {

    @Mock
    private AgriculturalProductRepository repository;
    @Mock
    private AuditWriter audits;

    private AgriculturalProductService service;

    @BeforeEach
    void setUp() {
        AgriculturalProductMapper mapper =
                new AgriculturalProductMapper();

        service = new AgriculturalProductService(
                repository, mapper, audits
        );
    }

    // TEST 1: Create a new agricultural product
    @Test
    void shouldCreateAgriculturalProduct() {

        AgriculturalProductRequest request =
                new AgriculturalProductRequest(
                        "زيتون", "Olives", "CROP"
                );

        when(repository.existsByNameAr("زيتون"))
                .thenReturn(false);

        when(repository.saveAndFlush(
                any(AgriculturalProduct.class)))
                .thenAnswer(invocation -> {
                    AgriculturalProduct product =
                            invocation.getArgument(0);
                    product.setId(1L);
                    return product;
                });

        AgriculturalProductResponse response =
                service.create(request);

        assertEquals(1L, response.id());
        assertEquals("زيتون", response.nameAr());
        assertEquals("Olives", response.nameEn());
        assertEquals("CROP", response.productType());

        verify(repository).saveAndFlush(
                any(AgriculturalProduct.class));
        verify(audits).recordAgriculturalProduct(eq(1L), eq(AuditAction.CREATE), isNull(),
                argThat(snapshot -> "Olives".equals(snapshot.nameEn()) && "CROP".equals(snapshot.productType())));
    }

    // TEST 2: Reject duplicate agricultural product
    @Test
    void shouldRejectDuplicateProduct() {

        AgriculturalProductRequest request =
                new AgriculturalProductRequest(
                        "زيتون", "Olives", "CROP"
                );

        when(repository.existsByNameAr("زيتون"))
                .thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.create(request)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(repository, never()).saveAndFlush(
                any(AgriculturalProduct.class));
        verifyNoInteractions(audits);
    }

    // TEST 3: Return 404 when product does not exist
    @Test
    void shouldReturnNotFoundForMissingProduct() {

        when(repository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getById(99L)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    // TEST 4: Update an existing agricultural product
    @Test
    void shouldUpdateAgriculturalProduct() {

        AgriculturalProduct product =
                new AgriculturalProduct();

        product.setId(1L);
        product.setNameAr("زيتون");
        product.setNameEn("Olives");
        product.setProductType("CROP");

        AgriculturalProductRequest request =
                new AgriculturalProductRequest(
                        "زيتون بلدي", "Local Olives", "CROP"
                );

        when(repository.findById(1L))
                .thenReturn(Optional.of(product));

        when(repository.findByNameAr("زيتون بلدي"))
                .thenReturn(Optional.empty());

        when(repository.saveAndFlush(product))
                .thenReturn(product);

        AgriculturalProductResponse response =
                service.update(1L, request);

        assertEquals(1L, response.id());
        assertEquals("زيتون بلدي", response.nameAr());
        assertEquals("Local Olives", response.nameEn());
        assertEquals("CROP", response.productType());

        verify(repository).saveAndFlush(product);
        verify(audits).recordAgriculturalProduct(eq(1L), eq(AuditAction.UPDATE),
                argThat(snapshot -> "Olives".equals(snapshot.nameEn()) && "CROP".equals(snapshot.productType())),
                argThat(snapshot -> "Local Olives".equals(snapshot.nameEn()) && "CROP".equals(snapshot.productType())));
    }

    // TEST 5: Delete an existing agricultural product
    @Test
    void shouldDeleteAgriculturalProduct() {

        AgriculturalProduct product =
                new AgriculturalProduct();

        product.setId(1L);
        product.setProductType("CROP");
        product.setNameAr("زيتون");

        when(repository.findById(1L))
                .thenReturn(Optional.of(product));

        service.delete(1L);

        verify(repository).delete(product);
        verify(repository).flush();
        verify(audits).recordAgriculturalProduct(eq(1L), eq(AuditAction.DELETE),
                argThat(snapshot -> snapshot.nameEn() == null && "CROP".equals(snapshot.productType())), isNull());
    }

    // TEST 6: Reject update when another product has the same name
    @Test
    void shouldRejectUpdateWithDuplicateName() {

        AgriculturalProduct product =
                new AgriculturalProduct();

        product.setId(1L);
        product.setNameAr("زيتون");

        AgriculturalProduct anotherProduct =
                new AgriculturalProduct();

        anotherProduct.setId(2L);
        anotherProduct.setNameAr("قمح");

        AgriculturalProductRequest request =
                new AgriculturalProductRequest(
                        "قمح", "Wheat", "CROP"
                );

        when(repository.findById(1L))
                .thenReturn(Optional.of(product));

        when(repository.findByNameAr("قمح"))
                .thenReturn(Optional.of(anotherProduct));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.update(1L, request)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(repository, never()).saveAndFlush(
                any(AgriculturalProduct.class)
        );
        verifyNoInteractions(audits);
    }

    @Test
    void propagatesAuditFailureAfterSuccessfulCreate() {
        var request = new AgriculturalProductRequest("Ø²ÙŠØªÙˆÙ†", "Olives", "CROP");
        when(repository.existsByNameAr("Ø²ÙŠØªÙˆÙ†")).thenReturn(false);
        when(repository.saveAndFlush(any(AgriculturalProduct.class))).thenAnswer(invocation -> {
            var product = invocation.getArgument(0, AgriculturalProduct.class);
            product.setId(1L);
            return product;
        });
        doThrow(new IllegalStateException("audit failure")).when(audits).recordAgriculturalProduct(
                eq(1L), eq(AuditAction.CREATE), isNull(), any());

        assertEquals("audit failure", assertThrows(IllegalStateException.class, () -> service.create(request)).getMessage());
        InOrder order = inOrder(repository, audits);
        order.verify(repository).saveAndFlush(any(AgriculturalProduct.class));
        order.verify(audits).recordAgriculturalProduct(eq(1L), eq(AuditAction.CREATE), isNull(), any());
    }
}
