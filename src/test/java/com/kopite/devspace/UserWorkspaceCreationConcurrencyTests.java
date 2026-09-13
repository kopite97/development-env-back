package com.kopite.devspace;

import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.user.domain.AuthIdentityId;
import com.kopite.devspace.user.domain.AuthIdentityRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserWorkspaceCreationConcurrencyTests {
    private final UserWorkspaceCreationService creation;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    AuthIdentityRepository identities;

    @Autowired
    UserWorkspaceCreationConcurrencyTests(UserWorkspaceCreationService creation, JdbcTemplate jdbc,
                                          PlatformTransactionManager transactionManager) {
        this.creation = creation;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    @RepeatedTest(10)
    void winnerCommitsBetweenLosingLookupAndInsertWithoutIdentityReassignment(RepetitionInfo repetition) throws Exception {
        String issuer = "forced-race-" + UUID.randomUUID();
        String subject = UUID.randomUUID().toString();
        String name = "race-candidates-" + UUID.randomUUID();
        var id = new AuthIdentityId(issuer, subject);
        var readMissing = new CountDownLatch(1);
        var winnerCommitted = new CountDownLatch(1);
        var first = new AtomicBoolean(true);
        var losingThread = new AtomicReference<Thread>();
        var losingTransaction = new AtomicLong();
        var rollbackCompleted = new AtomicBoolean();
        var recoveries = new AtomicInteger();
        doAnswer(call -> {
            if (first.compareAndSet(true, false)) {
                Object result = call.callRealMethod();
                assertEquals(java.util.Optional.empty(), result);
                losingThread.set(Thread.currentThread());
                losingTransaction.set(jdbc.queryForObject("select txid_current()", Long.class));
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        rollbackCompleted.set(status == STATUS_ROLLED_BACK);
                    }
                });
                readMissing.countDown();
                assertTrue(winnerCommitted.await(20, TimeUnit.SECONDS));
                return result;
            }
            if (Thread.currentThread() == losingThread.get()) {
                assertTrue(rollbackCompleted.get(), "losing transaction must finish rollback before recovery lookup");
                assertNotEquals(losingTransaction.get(), jdbc.queryForObject("select txid_current()", Long.class));
                assertEquals(1L, jdbc.queryForObject("select count(*) from users where display_name=?", Long.class, name));
                recoveries.incrementAndGet();
            }
            return call.callRealMethod();
        }).when(identities).findById(eq(id));

        try (var executor = Executors.newSingleThreadExecutor()) {
            var losing = executor.submit(() -> {
                if (repetition.getCurrentRepetition() % 2 != 0) return creation.createOrReuse(issuer, subject, name);
                // An ambient caller transaction must neither swallow the unique violation
                // nor be reused for recovery. It remains usable and can roll back independently.
                return new TransactionTemplate(transactionManager).execute(status -> {
                    long callerTransaction = jdbc.queryForObject("select txid_current()", Long.class);
                    var result = creation.createOrReuse(issuer, subject, name);
                    assertEquals(callerTransaction, jdbc.queryForObject("select txid_current()", Long.class));
                    assertNotEquals(callerTransaction, losingTransaction.get());
                    assertFalse(status.isRollbackOnly());
                    status.setRollbackOnly();
                    return result;
                });
            });
            UserWorkspaceCreationResult winner;
            try {
                assertTrue(readMissing.await(20, TimeUnit.SECONDS));
                winner = creation.createOrReuse(issuer, subject, name);
            } finally {
                winnerCommitted.countDown();
            }
            var recovered = losing.get(30, TimeUnit.SECONDS);
            assertAll(
                () -> assertEquals(1, recoveries.get(), "must exercise the unique-conflict recovery path"),
                () -> assertEquals(winner.user().getId(), recovered.user().getId(), "every caller must resolve the winning User"),
                () -> assertEquals(winner.workspace().getId(), recovered.workspace().getId(), "every caller must resolve the winning Workspace"),
                () -> assertEquals(winner.user().getId(), jdbc.queryForObject("select user_id from auth_identities where issuer=? and subject=?", UUID.class, issuer, subject)),
                () -> assertEquals(1L, jdbc.queryForObject("select count(*) from auth_identities where issuer=? and subject=?", Long.class, issuer, subject)),
                () -> assertEquals(1L, jdbc.queryForObject("select count(*) from users where display_name=?", Long.class, name), "no orphan candidate User"),
                () -> assertEquals(1L, jdbc.queryForObject("select count(*) from workspaces w join users u on u.id=w.owner_user_id where u.display_name=?", Long.class, name), "no orphan candidate Workspace")
            );
        } finally {
            winnerCommitted.countDown();
        }
    }
}
