package com.kopite.devspace;

import com.kopite.devspace.project.domain.Project;
import com.kopite.devspace.project.domain.ProjectRepository;
import com.kopite.devspace.project.domain.ProjectRevisionConflictException;
import com.kopite.devspace.project.domain.ProjectValidationException;
import com.kopite.devspace.project.domain.ProjectValues;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProjectPersistenceTests {
    private final UserWorkspaceCreationService users;
    private final ProjectRepository projects;
    private final PersonalWorkspaceRepository workspaces;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DataSource dataSource;

    @Autowired
    ProjectPersistenceTests(UserWorkspaceCreationService users, ProjectRepository projects,
                            PersonalWorkspaceRepository workspaces, JdbcTemplate jdbc,
                            PlatformTransactionManager manager, DataSource dataSource) {
        this.users = users;
        this.projects = projects;
        this.workspaces = workspaces;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.dataSource = dataSource;
    }

    @Test
    void persistsFractionalProgressAndOwnedLifecycleWithoutChangingWorkspaceMetadata() {
        var owner = users.createOrReuse("project-test", UUID.randomUUID().toString(), "Owner");
        UUID workspaceId = owner.workspace().getId();
        var metadata = jdbc.queryForMap("select revision, updated_at from workspaces where id = ?", workspaceId);
        Project created = transaction.execute(tx -> {
            var workspace = workspaces.lockByOwnerId(owner.user().getId()).orElseThrow();
            Project project = projects.save(Project.create(workspaceId, values("  Test  "), Instant.now()));
            workspace.recordBusinessMutation();
            return project;
        });
        Project reloaded = transaction.execute(tx -> projects.findOwned(workspaceId, created.getId()).orElseThrow());
        assertEquals("Test", reloaded.getName());
        assertEquals("Java", reloaded.getStack());
        assertEquals(new BigDecimal("12.1234567890123456789"), reloaded.getProgress());
        assertEquals(created.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(reloaded.getCreatedAt(), reloaded.getUpdatedAt());
        assertEquals(1, reloaded.getRevision());
        assertNull(reloaded.getCategoryId());
        Boolean inaccessible = transaction.execute(
                tx -> projects.findOwned(UUID.randomUUID(), created.getId()).isEmpty());
        assertEquals(Boolean.TRUE, inaccessible);

        transaction.executeWithoutResult(tx -> {
            var workspace = workspaces.lockByOwnerId(owner.user().getId()).orElseThrow();
            var project = projects.lockOwned(workspaceId, created.getId()).orElseThrow();
            project.update(1, values("Renamed"), "archived", Instant.now());
            workspace.recordBusinessMutation();
        });
        transaction.executeWithoutResult(tx -> {
            var workspace = workspaces.lockByOwnerId(owner.user().getId()).orElseThrow();
            var project = projects.lockOwned(workspaceId, created.getId()).orElseThrow();
            assertEquals("archived", project.getStatus());
            assertEquals(2, project.getRevision());
            assertEquals(created.getCreatedAt(), project.getCreatedAt());
            assertThrows(ProjectRevisionConflictException.class,
                    () -> project.update(1, project.values(), "active", Instant.now()));
            project.update(2, project.values(), "active", Instant.now());
            workspace.recordBusinessMutation();
        });
        assertEquals(3L, jdbc.queryForObject("select data_revision from workspaces where id = ?", Long.class, workspaceId));
        assertEquals(metadata, jdbc.queryForMap("select revision, updated_at from workspaces where id = ?", workspaceId));

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(tx -> {
            var workspace = workspaces.lockByOwnerId(owner.user().getId()).orElseThrow();
            var project = projects.lockOwned(workspaceId, created.getId()).orElseThrow();
            project.update(3, values("Rollback"), "archived", Instant.now());
            workspace.recordBusinessMutation();
            throw new IllegalStateException("force rollback");
        }));
        assertEquals("Renamed", jdbc.queryForObject("select name from projects where id = ?", String.class, created.getId()));
        assertEquals(3L, jdbc.queryForObject("select revision from projects where id = ?", Long.class, created.getId()));
        assertEquals(3L, jdbc.queryForObject("select data_revision from workspaces where id = ?", Long.class, workspaceId));
    }

    @Test
    void domainChecksUtf16UrlStatesAndRevisionOverflowBeforeMutation() {
        assertEquals(100, values("😀".repeat(50)).name().length());
        assertThrows(ProjectValidationException.class, () -> values("😀".repeat(51)));
        assertThrows(ProjectValidationException.class, () -> values(" \t "));
        assertThrows(ProjectValidationException.class, () -> new com.kopite.devspace.project.application.command.ProjectCategorySelection(true, "not-a-uuid"));
        assertThrows(ProjectValidationException.class, () -> new ProjectValues("n", "", "s", BigDecimal.valueOf(101), "", ""));
        assertThrows(ProjectValidationException.class, () -> new ProjectValues("n", "", "s", BigDecimal.ZERO, "", "file:///tmp/a"));
        assertEquals("https://user:password@example.com/repo", new ProjectValues("n", "", "s",
                BigDecimal.ZERO, "", " https://user:password@example.com/repo ").repositoryUrl());
        var project = Project.create(UUID.randomUUID(), values("n"), Instant.now());
        assertThrows(ProjectValidationException.class, () -> project.update(0, values("new"), "active", Instant.now()));
        assertThrows(ProjectValidationException.class, () -> project.update(1, values("new"), "deleted", Instant.now()));
        assertEquals("n", project.getName());
        assertEquals(1, project.getRevision());
    }

    @Test
    void databaseEnforcesDefaultsConstraintsAndWorkspaceForeignKey() {
        var owner = users.createOrReuse("project-test", UUID.randomUUID().toString(), "Owner");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into projects(id,workspace_id,name,stack,created_at,updated_at) values(?,?,?,'Java',now(),now())",
                id, owner.workspace().getId(), "DB defaults");
        var row = jdbc.queryForMap("select subtitle,progress,current_milestone,repository_url,status,revision from projects where id=?", id);
        assertEquals("", row.get("subtitle"));
        assertEquals(BigDecimal.ZERO, row.get("progress"));
        assertEquals("", row.get("current_milestone"));
        assertEquals("", row.get("repository_url"));
        assertEquals("active", row.get("status"));
        assertEquals(1L, row.get("revision"));
        for (String assignment : new String[]{"name=null", "name=''", "name=repeat('x',101)", "stack=' '",
                "stack=repeat('x',201)", "subtitle=repeat('x',4001)", "current_milestone=repeat('x',201)",
                "repository_url=repeat('x',2001)", "status='deleted'", "progress=-1",
                "progress=101", "progress='NaN'", "revision=0", "revision=9007199254740992"}) {
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("update projects set " + assignment + " where id=?", id), assignment);
        }
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update projects set workspace_id=? where id=?", UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from workspaces where id=?", owner.workspace().getId()));
        jdbc.update("update projects set revision=9007199254740991 where id=?", id);
        transaction.executeWithoutResult(tx -> {
            var p = projects.lockOwned(owner.workspace().getId(), id).orElseThrow();
            assertThrows(ProjectRevisionConflictException.class,
                    () -> p.update(Project.MAX_REVISION, p.values(), "active", Instant.now()));
            assertEquals(Project.MAX_REVISION, p.getRevision());
        });
    }

    @Test
    void upgradesPopulatedV1WithoutChangingExistingRowsOrChecksums() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway v1 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("1").load();
        v1.migrate();
        UUID user = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".users values(?, 'Existing', now(), now(), null)", user);
        jdbc.update("insert into " + schema + ".auth_identities values('issuer','subject',?)", user);
        jdbc.update("insert into " + schema + ".workspaces values(?,?,'Existing workspace',7,42,now(),now())", workspace, user);
        var before = jdbc.queryForMap("select * from " + schema + ".workspaces");
        Integer checksum = jdbc.queryForObject("select checksum from " + schema + ".flyway_schema_history where version='1'", Integer.class);
        Flyway upgraded = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("14").load();
        upgraded.migrate();
        upgraded.validate();
        assertEquals(before, jdbc.queryForMap("select * from " + schema + ".workspaces"));
        assertEquals(user, jdbc.queryForObject("select id from " + schema + ".users", UUID.class));
        assertEquals(user, jdbc.queryForObject("select user_id from " + schema + ".auth_identities", UUID.class));
        assertEquals(checksum, jdbc.queryForObject("select checksum from " + schema + ".flyway_schema_history where version='1'", Integer.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".projects", Long.class));
    }

    private ProjectValues values(String name) {
        return new ProjectValues(name, "", " Java ", new BigDecimal("12.1234567890123456789"), "memo", "");
    }
}
