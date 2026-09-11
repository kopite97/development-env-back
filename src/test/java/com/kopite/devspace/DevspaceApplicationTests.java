package com.kopite.devspace;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class DevspaceApplicationTests {

    private final JdbcTemplate jdbcTemplate;

    private final Environment environment;

    private final Flyway flyway;

    @Autowired
    DevspaceApplicationTests(JdbcTemplate jdbcTemplate, Environment environment, Flyway flyway) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.flyway = flyway;
    }

    @Test
    void contextLoads() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void connectsToPostgres() {
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
    }

    @Test
    void appliesVersionOneMigration() {
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Long.class));
        assertEquals(3L, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'auth_identities', 'workspaces')
                """, Long.class));
    }

    @Test
    void reapplyingMigrationDoesNotDuplicateHistoryOrRemoveData() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "Migration Rerun User");

        flyway.migrate();

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE id = ?", Long.class, userId));
    }

    @Test
    void identityConstraintsAreEnforced() {
        UUID userId = UUID.randomUUID();
        String issuer = "issuer-" + UUID.randomUUID();
        String subject = "subject-" + UUID.randomUUID();
        insertUser(userId, "Identity User");
        insertIdentity(userId, issuer, subject);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertIdentity(userId, issuer, subject));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertIdentity(userId, "", "non-empty-subject"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertIdentity(userId, "non-empty-issuer", ""));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertIdentity(UUID.randomUUID(), "unknown-user-issuer", "unknown-user-subject"));
    }

    @Test
    void workspaceOwnershipAndRevisionConstraintsAreEnforced() {
        UUID ownerUserId = UUID.randomUUID();
        insertUser(ownerUserId, "Workspace User");
        insertWorkspace(UUID.randomUUID(), ownerUserId, "Workspace", 1, 0);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertWorkspace(UUID.randomUUID(), ownerUserId, "Second Workspace", 1, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertWorkspace(UUID.randomUUID(), UUID.randomUUID(), "Unknown Owner", 1, 0));

        UUID revisionOwnerId = UUID.randomUUID();
        insertUser(revisionOwnerId, "Revision User");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertWorkspace(UUID.randomUUID(), revisionOwnerId, "Invalid Revision", 0, 0));

        UUID dataRevisionOwnerId = UUID.randomUUID();
        insertUser(dataRevisionOwnerId, "Data Revision User");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertWorkspace(UUID.randomUUID(), dataRevisionOwnerId, "Invalid Data Revision", 1, -1));
    }

    @Test
    void requiredColumnsRejectNullValues() {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO users (id, display_name, created_at, updated_at, disabled_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, null, now, now, null));

        insertUser(userId, "Required Value User");
        assertThrows(DataIntegrityViolationException.class, () -> insertWorkspace(
                UUID.randomUUID(), userId, null, 1, 0));
    }

    private void insertUser(UUID id, String displayName) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO users (id, display_name, created_at, updated_at, disabled_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, displayName, now, now, null);
    }

    private void insertIdentity(UUID userId, String issuer, String subject) {
        jdbcTemplate.update("""
                INSERT INTO auth_identities (issuer, subject, user_id)
                VALUES (?, ?, ?)
                """, issuer, subject, userId);
    }

    private void insertWorkspace(UUID id, UUID ownerUserId, String name, int revision, long dataRevision) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO workspaces (
                    id, owner_user_id, name, revision, data_revision, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, ownerUserId, name, revision, dataRevision, now, now);
    }

}
