package com.kopite.devspace;
import com.kopite.devspace.task.domain.Task;
import com.kopite.devspace.task.domain.TaskConflictException;
import com.kopite.devspace.task.domain.TaskRepository;
import com.kopite.devspace.task.domain.TaskValidationException;
import com.kopite.devspace.task.domain.TaskValues;

import com.kopite.devspace.user.application.UserWorkspaceCreationService;
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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskPersistenceTests {
    @Autowired UserWorkspaceCreationService users;
    @Autowired TaskRepository tasks;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;

    private UUID workspace() {
        return users.createOrReuse("task-test", UUID.randomUUID().toString(), "Owner").workspace().getId();
    }

    private UUID project(UUID workspace) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into projects(id,workspace_id,name,scope,stack,created_at,updated_at) values(?,?,'Project','server','Java',now(),now())", id, workspace);
        return id;
    }

    private TaskValues values(UUID project) {
        return new TaskValues(" Task ", project, "  text\n ", "todo", "normal", " tag ");
    }

    @Test
    void roundTripLifecycleAndOwnedAccess() {
        UUID workspace = workspace();
        UUID project = project(workspace);
        var tx = new TransactionTemplate(manager);
        Task created = tx.execute(s -> tasks.save(Task.create(workspace, values(project), Instant.now())));
        assertNotNull(created);
        tx.executeWithoutResult(s -> {
            Task task = tasks.lockOwned(workspace, created.getId()).orElseThrow();
            assertEquals("Task", task.getTitle());
            assertEquals("tag", task.getTag());
            assertEquals("  text\n ", task.getDescription());
            assertNull(task.getDeletedAt());
            assertEquals(created.getCreatedAt(), task.getCreatedAt());
            assertEquals(task.getCreatedAt(), task.getUpdatedAt());
            task.update(1, task.values(), Instant.now());
        });
        tx.executeWithoutResult(s -> tasks.lockOwned(workspace, created.getId()).orElseThrow().delete(2, Instant.now()));
        tx.executeWithoutResult(s -> {
            Task task = tasks.lockOwned(workspace, created.getId()).orElseThrow();
            assertNotNull(task.getDeletedAt());
            assertEquals(3, task.getRevision());
            task.restore(3, Instant.now());
        });
        tx.executeWithoutResult(s -> {
            Task task = tasks.findOwned(workspace, created.getId()).orElseThrow();
            assertNull(task.getDeletedAt());
            assertEquals(4, task.getRevision());
            assertEquals(created.getCreatedAt(), task.getCreatedAt());
            assertEquals(workspace, task.getWorkspaceId());
            assertEquals(project, task.getProjectId());
            assertTrue(tasks.findOwned(UUID.randomUUID(), task.getId()).isEmpty());
        });
    }

    @Test
    void domainValidationAndRevisionPrecedesState() {
        UUID project = UUID.randomUUID();
        var now = Instant.now();
        Task task = Task.create(UUID.randomUUID(), values(project), now);
        assertEquals(160, new TaskValues("😀".repeat(80), project, "", "done", "high", "😀".repeat(20)).title().length());
        assertThrows(TaskValidationException.class, () -> new TaskValues("😀".repeat(81), project, "", "todo", "normal", ""));
        assertThrows(TaskValidationException.class, () -> new TaskValues("t", project, "", "todo", "normal", "😀".repeat(21)));
        assertThrows(TaskValidationException.class, () -> new TaskValues("t", project, "😀".repeat(5001), "todo", "normal", ""));
        assertThrows(TaskValidationException.class, () -> new TaskValues(" \t", project, "", "todo", "normal", ""));
        assertThrows(TaskValidationException.class, () -> new TaskValues("t", null, "", "todo", "normal", ""));
        assertThrows(TaskValidationException.class, () -> new TaskValues("t", project, null, "todo", "normal", ""));
        assertThrows(TaskValidationException.class, () -> new TaskValues("t", project, "", "TODO", "normal", ""));
        assertThrows(TaskValidationException.class, () -> new TaskValues("t", project, "", "todo", "높음", ""));
        assertThrows(TaskValidationException.class, () -> task.delete(0, now));
        assertEquals("INVALID_RESOURCE_STATE", assertThrows(TaskConflictException.class, () -> task.restore(1, now)).getCode());
        task.delete(1, now);
        assertEquals("REVISION_CONFLICT", assertThrows(TaskConflictException.class, () -> task.delete(1, now)).getCode());
        assertEquals("REVISION_CONFLICT", assertThrows(TaskConflictException.class, () -> task.update(1, task.values(), now)).getCode());
        assertEquals("RESOURCE_DELETED", assertThrows(TaskConflictException.class, () -> task.update(2, task.values(), now)).getCode());
        assertEquals("INVALID_RESOURCE_STATE", assertThrows(TaskConflictException.class, () -> task.delete(2, now)).getCode());
        assertEquals("REVISION_CONFLICT", assertThrows(TaskConflictException.class, () -> task.restore(1, now)).getCode());
        assertEquals(2, task.getRevision());
    }

    @Test
    void databaseDefaultsConstraintsCompositeOwnershipAndOverflow() {
        UUID workspace = workspace();
        UUID project = project(workspace);
        UUID foreignProject = project(workspace());
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tasks(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Task',now(),now())", id, workspace, project);
        var row = jdbc.queryForMap("select * from tasks where id=?", id);
        assertEquals("todo", row.get("status"));
        assertEquals("normal", row.get("priority"));
        assertEquals("", row.get("description"));
        assertEquals("", row.get("tag"));
        assertEquals(1L, row.get("revision"));
        assertNull(row.get("deleted_at"));
        for (String assignment : new String[]{"title=null", "title=' '", "title=repeat('x',161)",
                "description=null", "description=repeat('x',10001)", "tag=null", "tag=repeat('x',41)",
                "status='invalid'", "priority='높음'", "revision=0", "revision=9007199254740992", "project_id=null"}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update tasks set " + assignment + " where id=?", id), assignment);
        }
        for (UUID invalidProject : new UUID[]{foreignProject, UUID.randomUUID()})
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update tasks set project_id=? where id=?", invalidProject, id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update tasks set workspace_id=? where id=?", UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from projects where id=?", project));
        jdbc.update("update tasks set revision=? where id=?", Task.MAX_REVISION, id);
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            Task task = tasks.lockOwned(workspace, id).orElseThrow();
            assertEquals("REVISION_CONFLICT", assertThrows(TaskConflictException.class, () -> task.delete(Task.MAX_REVISION, Instant.now())).getCode());
            assertEquals("REVISION_CONFLICT", assertThrows(TaskConflictException.class, () -> task.update(Task.MAX_REVISION, task.values(), Instant.now())).getCode());
            assertNull(task.getDeletedAt());
            assertEquals(Task.MAX_REVISION, task.getRevision());
        });
    }

    @Test
    void upgradesPopulatedV3AndPreservesChecksumsAndData() {
        String schema = "task_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway baseline = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("3").load();
        baseline.migrate();
        UUID user = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".users values(?,'Existing',now(),now(),null)", user);
        jdbc.update("insert into " + schema + ".workspaces values(?,?,'Existing',7,42,now(),now())", workspace, user);
        jdbc.update("insert into " + schema + ".projects(id,workspace_id,name,scope,stack,created_at,updated_at) values(?,?,'Existing','server','Java',now(),now())", project, workspace);
        var before = jdbc.queryForMap("select * from " + schema + ".projects");
        var checksums = jdbc.queryForList("select version,checksum from " + schema + ".flyway_schema_history where version is not null order by version");
        Flyway upgraded = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load();
        upgraded.migrate();
        upgraded.validate();
        assertEquals(before, jdbc.queryForMap("select * from " + schema + ".projects"));
        assertEquals(42L, jdbc.queryForObject("select data_revision from " + schema + ".workspaces", Long.class));
        assertEquals(checksums, jdbc.queryForList("select version,checksum from " + schema + ".flyway_schema_history where version in ('1','2','3') order by version"));
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".tasks", Long.class));
    }
}
