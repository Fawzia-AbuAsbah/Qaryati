package com.qaryati.security.service;

import com.qaryati.security.api.UserSummary;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserLookupServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserLookupService service = new UserLookupService(repository);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void returnsIdAndEnabledWithoutNormalizingUsername(boolean enabled) {
        String username = " Reviewer ";
        User user = new User(username, "reviewer@example.com", "hash", "Reviewer");
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setEnabled(enabled);
        when(repository.findByUsername(username)).thenReturn(Optional.of(user));

        assertEquals(Optional.of(new UserSummary(42L, enabled)), service.findByUsername(username));
        verify(repository).findByUsername(username);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void returnsEmptyForMissingUser() {
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());
        assertTrue(service.findByUsername("missing").isEmpty());
    }
}
