package com.qaryati.heritage;

import com.qaryati.heritage.dto.CulturalHeritageCrudRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.service.CulturalHeritageCrudService;
import com.qaryati.heritage.repository.CulturalHeritageRepository;
import com.qaryati.dataquality.api.*;
import com.qaryati.village.api.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifies Spring's actual transaction advice; does not claim database rollback coverage. */
class CulturalHeritageCrudTransactionTest {
    @Test void auditFailureRollsBackEveryMutationThroughSpringProxy() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var repository = mock(CulturalHeritageRepository.class);
            var villages = mock(VillageLookupApi.class);
            var sources = mock(SourceLookup.class);
            var audits = mock(AuditWriter.class);
            var links = mock(CulturalHeritageSourceLinks.class);
            var manager = mock(PlatformTransactionManager.class);
            var transaction = mock(TransactionStatus.class);
            when(manager.getTransaction(any())).thenReturn(transaction);
            var target = new CulturalHeritageCrudService(repository, villages, sources, links, audits, factory.getValidator());
            var proxyFactory = new ProxyFactory(target);
            var interceptor = new TransactionInterceptor();
            interceptor.setTransactionManager(manager);
            interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            proxyFactory.addAdvice(interceptor);
            var service = (CulturalHeritageCrudService) proxyFactory.getProxy();
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var record = CulturalHeritage.draft(new com.qaryati.heritage.dto.CulturalHeritageRequest(1L, "Old", CulturalHeritageType.CRAFT, "Description"));
            ReflectionTestUtils.setField(record, "id", 3L);
            when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(record));
            when(repository.saveAndFlush(any())).thenAnswer(call -> {
                CulturalHeritage saved = call.getArgument(0); ReflectionTestUtils.setField(saved, "id", 3L); return saved;
            });
            when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, VillageLookupStatus.VERIFIED, false)));
            when(sources.existsById(2L)).thenReturn(true);
            doThrow(new IllegalStateException("audit failed")).when(audits).recordCulturalHeritage(anyLong(), any(), nullable(CulturalHeritageAuditSnapshot.class), nullable(CulturalHeritageAuditSnapshot.class));
            var request = CulturalHeritageCrudServiceTest.request("New", List.of(2L));
            assertThrows(IllegalStateException.class, () -> service.create(request));
            assertThrows(IllegalStateException.class, () -> service.update(3L, request));
            assertThrows(IllegalStateException.class, () -> service.delete(3L));
            reset(audits);
            doThrow(new IllegalStateException("link failed")).when(links).linkNewCulturalHeritage(any(), any());
            doThrow(new IllegalStateException("replace failed")).when(links).replaceSources(any(), any());
            assertThrows(IllegalStateException.class, () -> service.create(request));
            assertThrows(IllegalStateException.class, () -> service.update(3L, request));
            verifyNoInteractions(audits);
            verify(manager, times(5)).rollback(transaction);
            verify(manager, never()).commit(any());
            var definition = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(manager, times(5)).getTransaction(definition.capture());
            assertTrue(definition.getAllValues().stream().noneMatch(TransactionDefinition::isReadOnly));
            var attributes = new AnnotationTransactionAttributeSource();
            assertTrue(attributes.getTransactionAttribute(CulturalHeritageCrudService.class.getMethod("getById", Long.class), CulturalHeritageCrudService.class).isReadOnly());
            assertTrue(attributes.getTransactionAttribute(CulturalHeritageCrudService.class.getMethod("getAll", int.class, int.class, Long.class, CulturalHeritageType.class, VerificationStatus.class), CulturalHeritageCrudService.class).isReadOnly());
        } catch (NoSuchMethodException exception) { throw new AssertionError(exception); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
