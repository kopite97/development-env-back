package com.kopite.devspace;

import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.user.domain.AuthIdentity;
import com.kopite.devspace.user.domain.AuthIdentityRepository;
import com.kopite.devspace.user.domain.User;
import com.kopite.devspace.user.domain.UserRepository;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class UserWorkspacePersistenceRulesTests {

    private final UserWorkspaceCreationService creationService;

    private final UserRepository userRepository;

    private final AuthIdentityRepository authIdentityRepository;

    private final PersonalWorkspaceRepository workspaceRepository;

    private final JdbcTemplate jdbcTemplate;

    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    UserWorkspacePersistenceRulesTests(
            UserWorkspaceCreationService creationService,
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PersonalWorkspaceRepository workspaceRepository,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.creationService = creationService;
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.workspaceRepository = workspaceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @Test
    void differentIssuerOrSubjectCombinationsRemainDistinct() {
        String issuer = unique("issuer");
        String subject = unique("subject");

        UserWorkspaceCreationResult first = creationService.createOrReuse(issuer, subject, "First User");
        UserWorkspaceCreationResult differentSubject = creationService.createOrReuse(
                issuer,
                subject + "-other",
                "Second User"
        );
        UserWorkspaceCreationResult differentIssuer = creationService.createOrReuse(
                issuer + "-other",
                subject,
                "Third User"
        );

        assertNotEquals(first.user().getId(), differentSubject.user().getId());
        assertNotEquals(first.user().getId(), differentIssuer.user().getId());
        assertNotEquals(differentSubject.user().getId(), differentIssuer.user().getId());
        assertEquals(1L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                issuer, subject));
        assertEquals(1L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                issuer, subject + "-other"));
        assertEquals(1L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                issuer + "-other", subject));
        assertEquals(3L, count("SELECT count(*) FROM workspaces WHERE owner_user_id IN (?, ?, ?)",
                first.user().getId(), differentSubject.user().getId(), differentIssuer.user().getId()));
    }

    @Test
    void databaseRejectsDuplicateIdentitySecondWorkspaceAndUnknownOwner() {
        String issuer = unique("issuer");
        String subject = unique("subject");
        UserWorkspaceCreationResult existing = creationService.createOrReuse(issuer, subject, "Existing User");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO auth_identities (issuer, subject, user_id)
                VALUES (?, ?, ?)
                """, issuer, subject, existing.user().getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO auth_identities (issuer, subject, user_id)
                VALUES (?, ?, ?)
                """, unique("unknown-issuer"), unique("unknown-subject"), UUID.randomUUID()));

        assertThrows(DataIntegrityViolationException.class, () -> insertWorkspace(
                UUID.randomUUID(),
                existing.user().getId(),
                "Second Workspace"
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insertWorkspace(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Unknown Owner"
        ));

        assertEquals(1L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                issuer, subject));
        assertEquals(1L, count("SELECT count(*) FROM workspaces WHERE owner_user_id = ?",
                existing.user().getId()));
    }

    @Test
    void repeatedCreationReusesTheExistingIdentityAndWorkspace() {
        String issuer = unique("issuer");
        String subject = unique("subject");

        UserWorkspaceCreationResult first = creationService.createOrReuse(issuer, subject, "First User");
        UserWorkspaceCreationResult second = creationService.createOrReuse(issuer, subject, "Ignored Name");

        assertEquals(first.user().getId(), second.user().getId());
        assertEquals(first.workspace().getId(), second.workspace().getId());
        assertEquals("First User", second.user().getDisplayName());
        assertEquals(1L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                issuer, subject));
        assertEquals(1L, count("SELECT count(*) FROM users WHERE id = ?", first.user().getId()));
        assertEquals(1L, count("SELECT count(*) FROM workspaces WHERE owner_user_id = ?",
                first.user().getId()));
    }

    @Test
    void workspaceNameLengthAndBlankRulesRemainInTheDomain() {
        User owner = User.create("Workspace Name Owner");

        assertThrows(IllegalArgumentException.class, () -> PersonalWorkspace.create(owner, "   "));
        assertThrows(IllegalArgumentException.class, () -> PersonalWorkspace.create(owner, "a".repeat(101)));
        assertEquals(100, PersonalWorkspace.create(owner, "😀".repeat(50)).getName().length());
    }

    @org.junit.jupiter.api.RepeatedTest(20)
    void concurrentCreationAttemptsConvergeOnOnePersistedAggregate() throws Exception {
        String issuer = unique("concurrent-issuer");
        String subject = unique("concurrent-subject");
        String displayName = unique("Concurrent User");
        int attemptCount = 8;
        CountDownLatch ready = new CountDownLatch(attemptCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        List<Future<UserWorkspaceCreationResult>> futures = new ArrayList<>();

        try {
            for (int attempt = 0; attempt < attemptCount; attempt++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent creation start");
                    }
                    return creationService.createOrReuse(issuer, subject, displayName);
                }));
            }

            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();

            List<UserWorkspaceCreationResult> results = new ArrayList<>();
            for (Future<UserWorkspaceCreationResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }

            Set<UUID> userIds = results.stream()
                    .map(result -> result.user().getId())
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> workspaceIds = results.stream()
                    .map(result -> result.workspace().getId())
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of(only(userIds)), userIds);
            assertEquals(Set.of(only(workspaceIds)), workspaceIds);

            assertEquals(1L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                    issuer, subject));
            assertEquals(1L, count("""
                    SELECT count(*)
                    FROM users u
                    JOIN auth_identities ai ON ai.user_id = u.id
                    WHERE ai.issuer = ? AND ai.subject = ?
                    """, issuer, subject));
            assertEquals(1L, count("""
                    SELECT count(*)
                    FROM workspaces w
                    JOIN users u ON u.id = w.owner_user_id
                    JOIN auth_identities ai ON ai.user_id = u.id
                    WHERE ai.issuer = ? AND ai.subject = ?
                    """, issuer, subject));
            assertEquals(1L, count("SELECT count(*) FROM workspaces WHERE owner_user_id = ?", only(userIds)));
            assertEquals(0L, count("""
                    SELECT count(*)
                    FROM users u
                    WHERE u.display_name = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM auth_identities ai
                          WHERE ai.user_id = u.id
                            AND ai.issuer = ?
                            AND ai.subject = ?
                      )
                    """, displayName, issuer, subject));
            assertEquals(0L, count("""
                    SELECT count(*)
                    FROM workspaces w
                    JOIN users u ON u.id = w.owner_user_id
                    WHERE u.display_name = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM auth_identities ai
                          WHERE ai.user_id = u.id
                            AND ai.issuer = ?
                            AND ai.subject = ?
                      )
                    """, displayName, issuer, subject));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedAtomicWriteRollsBackCandidateUserIdentityAndWorkspace() {
        String existingIssuer = unique("existing-issuer");
        String existingSubject = unique("existing-subject");
        UserWorkspaceCreationResult existing = creationService.createOrReuse(
                existingIssuer,
                existingSubject,
                "Existing User"
        );
        String candidateIssuer = unique("candidate-issuer");
        String candidateSubject = unique("candidate-subject");
        AtomicReference<UUID> candidateUserId = new AtomicReference<>();
        AtomicReference<UUID> candidateWorkspaceId = new AtomicReference<>();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            User candidate = User.create("Rollback Candidate " + UUID.randomUUID());
            candidateUserId.set(candidate.getId());
            User savedCandidate = userRepository.save(candidate);
            authIdentityRepository.save(AuthIdentity.create(savedCandidate, candidateIssuer, candidateSubject));

            PersonalWorkspace conflictingWorkspace = PersonalWorkspace.create(
                    userRepository.findById(existing.user().getId()).orElseThrow()
            );
            candidateWorkspaceId.set(conflictingWorkspace.getId());
            workspaceRepository.save(conflictingWorkspace);
            entityManager.flush();
        }));

        assertEquals(0L, count("SELECT count(*) FROM users WHERE id = ?", candidateUserId.get()));
        assertEquals(0L, count("SELECT count(*) FROM auth_identities WHERE issuer = ? AND subject = ?",
                candidateIssuer, candidateSubject));
        assertEquals(0L, count("SELECT count(*) FROM workspaces WHERE id = ?", candidateWorkspaceId.get()));
        assertEquals(1L, count("SELECT count(*) FROM users WHERE id = ?", existing.user().getId()));
        assertEquals(1L, count("SELECT count(*) FROM workspaces WHERE owner_user_id = ?", existing.user().getId()));
    }

    private long count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    private void insertWorkspace(UUID id, UUID ownerUserId, String name) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO workspaces (
                    id, owner_user_id, name, revision, data_revision, created_at, updated_at
                )
                VALUES (?, ?, ?, 1, 0, ?, ?)
                """, id, ownerUserId, name, now, now);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private UUID only(Set<UUID> values) {
        assertEquals(1, values.size());
        return values.iterator().next();
    }
}
