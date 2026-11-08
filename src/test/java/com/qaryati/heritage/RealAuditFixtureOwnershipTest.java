package com.qaryati.heritage;

import com.qaryati.village.api.VillageLookupApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No Spring context or DataSource: exercise the actual cleanup path using JDBC mocks. */
class RealAuditFixtureOwnershipTest {
    VillageHistoryRealAuditPostgresIntegrationTest contract() {
        var test = new VillageHistoryRealAuditPostgresIntegrationTest();
        test.jdbc = mock(JdbcTemplate.class);
        test.transactions = mock(PlatformTransactionManager.class);
        when(test.transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        test.villages = mock(VillageLookupApi.class);
        test.username = "audit-it-owned-token";
        return test;
    }

    @Test void onlyPositiveAllocatedIdsOfTheCorrectKindAreOwned() {
        var ownership = new RealAuditFixtureOwnership();
        assertFalse(ownership.owns("users", 0));
        assertFalse(ownership.owns("users", 8));
        assertThrows(IllegalArgumentException.class, () -> ownership.register("users", 0));
        assertThrows(IllegalArgumentException.class, () -> ownership.register("users", -1));
        ownership.register("sources", 8);
        assertTrue(ownership.owns("sources", 8));
        assertFalse(ownership.owns("users", 8));
    }

    @Test void failureBeforeAnyInsertNeverIssuesADelete() {
        var test = contract();
        test.cleanup();
        verify(test.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void partialSetupDeletesOnlyRegisteredGovernorateWithItsMarker() {
        var test = contract();
        test.governorate = test.ownership.register("governorates", 91);
        // Fields alone never authorize deletion, including pre-existing user IDs.
        test.actor = 8;
        test.village = 7;
        test.first = 6;
        test.cleanup();
        verify(test.jdbc).update("DELETE FROM public.governorates WHERE id=? AND name_ar=?", 91L, test.username);
        verify(test.jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test void completeCleanupRequiresMarkersAndPreservesForeignKeyOrder() {
        var test = contract();
        test.governorate = test.ownership.register("governorates", 91);
        test.village = test.ownership.register("villages", 92);
        test.first = test.ownership.register("sources", 93);
        test.second = test.ownership.register("sources", 94);
        test.actor = test.ownership.register("users", 95);
        test.cleanup();
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(test.jdbc, times(7)).update(sql.capture(), args.capture());
        String[] tables = {"audit_logs", "village_histories", "sources", "sources", "villages", "governorates", "users"};
        for (int i = 0; i < tables.length; i++) {
            assertTrue(sql.getAllValues().get(i).startsWith("DELETE FROM public." + tables[i] + " WHERE"));
            assertTrue(java.util.Arrays.asList(args.getAllValues().get(i)).contains(test.username));
            assertFalse(java.util.Arrays.asList(args.getAllValues().get(i)).contains(0L));
        }
        assertTrue(sql.getAllValues().getFirst().contains("actor_label=? AND EXISTS"));
        assertTrue(sql.getAllValues().get(1).contains("EXISTS"));
        verify(test.jdbc).update("DELETE FROM public.users WHERE id=? AND username=? AND email=?",
                95L, test.username, test.username + "@example.invalid");
    }
}
