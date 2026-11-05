package com.qaryati.population.service;

import com.qaryati.population.dto.PopulationRecordRequest;
import com.qaryati.population.entity.*;
import com.qaryati.population.mapper.PopulationRecordMapper;
import com.qaryati.population.repository.PopulationRecordRepository;
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
class PopulationTransactionTest {
    @Test void auditFailureRollsBackEveryMutationThroughSpringProxy() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var repository = mock(PopulationRecordRepository.class);
            var villages = mock(VillageLookupApi.class);
            var sources = mock(SourceLookup.class);
            var audits = mock(AuditWriter.class);
            var manager = mock(PlatformTransactionManager.class);
            var transaction = mock(TransactionStatus.class);
            when(manager.getTransaction(any())).thenReturn(transaction);
            var target = new PopulationRecordService(repository, new PopulationRecordMapper(), villages, sources, audits, factory.getValidator());
            var proxyFactory = new ProxyFactory(target);
            var interceptor = new TransactionInterceptor();
            interceptor.setTransactionManager(manager);
            interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            proxyFactory.addAdvice(interceptor);
            var service = (PopulationRecordService) proxyFactory.getProxy();
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var record = new PopulationRecord(1L, 2020, null, 100, Methodology.CENSUS, 2L, null);
            ReflectionTestUtils.setField(record, "id", 3L);
            when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(record));
            when(repository.saveAndFlush(any())).thenAnswer(call -> {
                PopulationRecord saved = call.getArgument(0); ReflectionTestUtils.setField(saved, "id", 3L); return saved;
            });
            when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, VillageLookupStatus.VERIFIED, false)));
            when(sources.existsById(2L)).thenReturn(true);
            doThrow(new IllegalStateException("audit failed")).when(audits).record(anyString(), anyLong(), any(), nullable(AuditSnapshot.class), nullable(AuditSnapshot.class));
            var request = new PopulationRecordRequest(1L, 2020, null, 200, Methodology.CENSUS, 2L, null);
            assertThrows(IllegalStateException.class, () -> service.create(request));
            assertThrows(IllegalStateException.class, () -> service.update(3L, request));
            assertThrows(IllegalStateException.class, () -> service.delete(3L));
            verify(manager, times(3)).rollback(transaction);
            verify(manager, never()).commit(any());
            var definition = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(manager, times(3)).getTransaction(definition.capture());
            assertTrue(definition.getAllValues().stream().noneMatch(TransactionDefinition::isReadOnly));
            var attributes = new AnnotationTransactionAttributeSource();
            assertTrue(attributes.getTransactionAttribute(PopulationRecordService.class.getMethod("getById", Long.class), PopulationRecordService.class).isReadOnly());
            assertTrue(attributes.getTransactionAttribute(PopulationRecordService.class.getMethod("getAll", int.class, int.class), PopulationRecordService.class).isReadOnly());
        } catch (NoSuchMethodException exception) { throw new AssertionError(exception); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
