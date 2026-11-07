package com.qaryati.heritage;

import com.qaryati.heritage.dto.HeritageSiteRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.service.HeritageSiteService;
import com.qaryati.heritage.repository.HeritageSiteRepository;
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
class HeritageSiteTransactionTest {
    @Test void auditFailureRollsBackEveryMutationThroughSpringProxy() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var repository = mock(HeritageSiteRepository.class);
            var villages = mock(VillageLookupApi.class);
            var audits = mock(AuditWriter.class);
            var links = mock(HeritageSiteSourceLinks.class);
            var manager = mock(PlatformTransactionManager.class);
            var transaction = mock(TransactionStatus.class);
            when(manager.getTransaction(any())).thenReturn(transaction);
            var target = new HeritageSiteService(repository, villages, links, audits, factory.getValidator());
            var proxyFactory = new ProxyFactory(target);
            var interceptor = new TransactionInterceptor();
            interceptor.setTransactionManager(manager);
            interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            proxyFactory.addAdvice(interceptor);
            var service = (HeritageSiteService) proxyFactory.getProxy();
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var record = HeritageSite.draft(HeritageSiteServiceTest.request("Old", List.of(2L)));
            ReflectionTestUtils.setField(record, "id", 3L);
            when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(record));
            when(repository.saveAndFlush(any())).thenAnswer(call -> {
                HeritageSite saved = call.getArgument(0); ReflectionTestUtils.setField(saved, "id", 3L); return saved;
            });
            when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, VillageLookupStatus.VERIFIED, false)));
            doThrow(new IllegalStateException("audit failed")).when(audits).recordHeritageSite(anyLong(), any(), nullable(HeritageSiteAuditSnapshot.class), nullable(HeritageSiteAuditSnapshot.class));
            var request = HeritageSiteServiceTest.request("New", List.of(2L));
            assertThrows(IllegalStateException.class, () -> service.create(request));
            assertThrows(IllegalStateException.class, () -> service.update(3L, request));
            assertThrows(IllegalStateException.class, () -> service.delete(3L));
            reset(audits);
            doThrow(new IllegalStateException("link failed")).when(links).linkNewSite(any(), any());
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
            assertTrue(attributes.getTransactionAttribute(HeritageSiteService.class.getMethod("getById", Long.class), HeritageSiteService.class).isReadOnly());
            assertTrue(attributes.getTransactionAttribute(HeritageSiteService.class.getMethod("getAll", int.class, int.class, Long.class), HeritageSiteService.class).isReadOnly());
        } catch (NoSuchMethodException exception) { throw new AssertionError(exception); }
        finally { SecurityContextHolder.clearContext(); }
    }
}

