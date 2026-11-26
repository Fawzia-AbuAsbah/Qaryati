package com.qaryati.heritage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual lock helper using mocked JDBC and in-memory transactions; no context or database. */
class RealAuditPostgresRollbackTest {
    private record Fixture(VillageHistoryRealAuditPostgresIntegrationTest contract,
                           RecordingTransactions transactions, Connection blocker, Statement settings,
                           PreparedStatement lock, ResultSet rows) { }

    private Fixture fixture() throws SQLException {
        var contract = new VillageHistoryRealAuditPostgresIntegrationTest();
        var transactions = new RecordingTransactions();
        var blocker = mock(Connection.class);
        var settings = mock(Statement.class);
        var lock = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        contract.transactions = transactions;
        contract.jdbc = mock(JdbcTemplate.class);
        contract.dataSource = mock(DataSource.class);
        when(contract.dataSource.getConnection()).thenReturn(blocker);
        when(blocker.createStatement()).thenReturn(settings);
        when(blocker.prepareStatement("SELECT id FROM public.sources WHERE id=? FOR UPDATE")).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        return new Fixture(contract, transactions, blocker, settings, lock, rows);
    }

    private void assertRolledBackAndReleased(Fixture fixture) throws SQLException {
        assertEquals(0, fixture.transactions().commits);
        assertEquals(List.of(true), fixture.transactions().rollbackOnlyRequests);
        assertEquals(List.of(10), fixture.transactions().timeouts);
        verify(fixture.blocker()).setAutoCommit(false);
        verify(fixture.settings()).setQueryTimeout(5);
        verify(fixture.settings()).execute("SET LOCAL lock_timeout = '300ms'");
        verify(fixture.settings()).execute("SET LOCAL statement_timeout = '5s'");
        verify(fixture.lock()).setQueryTimeout(5);
        verify(fixture.lock()).setLong(1, 7L);
        verify(fixture.rows()).close();
        verify(fixture.lock()).close();
        verify(fixture.settings()).close();
        var order = inOrder(fixture.blocker());
        order.verify(fixture.blocker()).rollback();
        order.verify(fixture.blocker()).close();
    }

    @Test
    void unexpectedMutationSuccessRollsBackAndStillFailsTheOriginalAssertion() throws SQLException {
        var fixture = fixture();
        var mutation = mock(Runnable.class);
        var failure = assertThrows(AssertionFailedError.class,
                () -> fixture.contract().blocked("sources", 7L, mutation));
        assertNull(failure.getCause());
        verify(mutation).run();
        assertRolledBackAndReleased(fixture);
    }

    @Test
    void expectedPostgresLockTimeoutPreservesSqlStateAssertion() throws SQLException {
        var fixture = fixture();
        var mutation = mock(Runnable.class);
        var failure = new IllegalStateException("Controlled mutation failure",
                new SQLException("Controlled lock timeout", "55P03"));
        doThrow(failure).when(mutation).run();
        assertDoesNotThrow(() -> fixture.contract().blocked("sources", 7L, mutation));
        verify(mutation).run();
        assertRolledBackAndReleased(fixture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23503", "22012"})
    void otherSqlStatesStillFailTheLockAssertion(String sqlState) throws SQLException {
        var fixture = fixture();
        var mutation = mock(Runnable.class);
        doThrow(new IllegalStateException("Wrong SQLSTATE", new SQLException("Controlled failure", sqlState)))
                .when(mutation).run();
        assertThrows(AssertionFailedError.class,
                () -> fixture.contract().blocked("sources", 7L, mutation));
        verify(mutation).run();
        assertRolledBackAndReleased(fixture);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assertionFailureInTimeoutSetupOrMutationStillRequestsRollbackOnly(boolean duringTimeoutSetup)
            throws SQLException {
        var fixture = fixture();
        var mutation = mock(Runnable.class);
        var assertionFailure = new AssertionError("Controlled assertion failure");
        if (duringTimeoutSetup) {
            doThrow(assertionFailure).when(fixture.contract().jdbc).execute("SET LOCAL lock_timeout = '300ms'");
        } else {
            doThrow(assertionFailure).when(mutation).run();
        }
        var failure = assertThrows(AssertionFailedError.class,
                () -> fixture.contract().blocked("sources", 7L, mutation));
        assertSame(assertionFailure, failure.getCause());
        if (duringTimeoutSetup) verifyNoInteractions(mutation);
        else verify(mutation).run();
        assertRolledBackAndReleased(fixture);
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private int commits;
        private final List<Boolean> rollbackOnlyRequests = new ArrayList<>();
        private final List<Integer> timeouts = new ArrayList<>();

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            timeouts.add(definition.getTimeout());
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            rollbackOnlyRequests.add(status.isLocalRollbackOnly());
        }
    }
}
