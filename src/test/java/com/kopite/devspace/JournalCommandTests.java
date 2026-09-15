package com.kopite.devspace;
import com.kopite.devspace.journal.application.command.CreateJournalCommand;
import com.kopite.devspace.journal.application.command.JournalCommandService;
import com.kopite.devspace.journal.application.command.UpdateJournalCommand;
import com.kopite.devspace.journal.application.exception.JournalNotFoundException;
import com.kopite.devspace.journal.application.model.JournalSnapshot;
import com.kopite.devspace.journal.application.port.JournalCreateReplayStore;
import com.kopite.devspace.journal.domain.Journal;
import com.kopite.devspace.journal.domain.JournalConflictException;
import com.kopite.devspace.journal.domain.JournalValidationException;
import com.kopite.devspace.project.application.command.CreateProjectCommand;
import com.kopite.devspace.project.application.command.ProjectCommandService;
import com.kopite.devspace.project.application.command.UpdateProjectCommand;

import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JournalCommandTests {
    private final JournalCommandService journals;
    private final ProjectCommandService projects;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    @MockitoSpyBean JournalCreateReplayStore replays;
    @Autowired
    JournalCommandTests(JournalCommandService journals,ProjectCommandService projects,UserWorkspaceCreationService users,
                        JdbcTemplate jdbc,PlatformTransactionManager manager,JsonMapper json) {
        this.journals=journals; this.projects=projects; this.users=users; this.jdbc=jdbc;
        this.transaction=new TransactionTemplate(manager); this.json=json;
    }
    private UUID owner() { return users.createOrReuse("journal-command",UUID.randomUUID().toString(),"Owner").user().getId(); }
    private UUID project(UUID owner) { return projects.create(owner,UUID.randomUUID().toString(),new CreateProjectCommand("Project",null,"Java",null,null,null)).id(); }
    private CreateJournalCommand input(UUID project) { return new CreateJournalCommand("Journal",project," Body\n ","2024-02-29"); }
    private UpdateJournalCommand change(long revision,UUID project) { return new UpdateJournalCommand(revision,null,project,null,"2026-09-01"); }
    private void archive(UUID user,UUID project) { projects.update(user,project,new UpdateProjectCommand(1,null,null,null,null,null,null,"archived")); }
    private long counter(UUID user) { return jdbc.queryForObject("select data_revision from workspaces where owner_user_id=?",Long.class,user); }

    @Test
    void archivedRelationOwnershipAndPermanentDeleteReplay() {
        UUID user=owner(); UUID project=project(user); UUID other=owner(); UUID foreign=project(other);
        var journal=journals.create(user,"create",input(project)); archive(user,project);
        assertEquals("PROJECT_ARCHIVED",assertThrows(JournalConflictException.class,()->journals.create(user,"new",input(project))).getCode());
        var edited=journals.update(user,journal.id(),change(1,project)); assertEquals(2,edited.revision());
        assertEquals(journal.createdAt(),edited.createdAt());
        assertThrows(JournalNotFoundException.class,()->journals.create(other,"create",input(project)));
        assertThrows(JournalNotFoundException.class,()->journals.update(user,journal.id(),change(99,foreign)));
        assertThrows(JournalNotFoundException.class,()->journals.update(other,journal.id(),change(99,null)));
        assertThrows(JournalNotFoundException.class,()->journals.delete(other,journal.id(),99));
        SnapshotAssertions.assertDataEquals(journal,journals.create(user,"create",input(project)));
        assertEquals(journal.id(),journals.delete(user,journal.id(),2));
        long before=counter(user);
        SnapshotAssertions.assertDataEquals(journal,journals.create(user,"create",input(project)));
        assertEquals(before,counter(user));
        assertEquals(0L,jdbc.queryForObject("select count(*) from journals where id=?",Long.class,journal.id()));
        assertThrows(JournalNotFoundException.class,()->journals.delete(user,journal.id(),2));
        assertThrows(JournalNotFoundException.class,()->journals.update(user,journal.id(),change(2,null)));
        assertEquals(1L,jdbc.queryForObject("select revision from workspaces where owner_user_id=?",Long.class,user));
        assertEquals(2L,jdbc.queryForObject("select revision from projects where id=?",Long.class,project));
    }

    @Test
    void creationAndMutationRacesHaveSingleWinnerAndAtomicCounters() throws Exception {
        UUID user=owner(); UUID project=project(user);
        var same=race(()->journals.create(user,"same",input(project)),()->journals.create(user,"same",input(project)));
        assertInstanceOf(JournalSnapshot.class,same.getFirst()); SnapshotAssertions.assertDataEquals(same.getFirst(),same.getLast());
        var journal=(JournalSnapshot)same.getFirst(); assertEquals(2,counter(user));
        var edits=race(()->journals.update(user,journal.id(),change(1,null)),()->journals.update(user,journal.id(),change(1,null)));
        assertEquals(1,edits.stream().filter(JournalSnapshot.class::isInstance).count());
        assertEquals(1,edits.stream().filter(JournalConflictException.class::isInstance).count());
        assertEquals(3,counter(user));
        var deletion=race(()->journals.delete(user,journal.id(),2),()->journals.delete(user,journal.id(),2));
        assertEquals(1,deletion.stream().filter(UUID.class::isInstance).count());
        assertEquals(1,deletion.stream().filter(JournalNotFoundException.class::isInstance).count()); assertEquals(4,counter(user));
        var different=race(()->journals.create(user,"different",input(project)),
            ()->journals.create(user,"different",new CreateJournalCommand("Other",project,"Body","2024-02-29")));
        assertEquals(1,different.stream().filter(JournalSnapshot.class::isInstance).count());
        assertEquals(1,different.stream().filter(JournalConflictException.class::isInstance).count());
        var survivor=(JournalSnapshot)different.stream().filter(JournalSnapshot.class::isInstance).findFirst().orElseThrow();
        var editDelete=race(()->journals.update(user,survivor.id(),change(1,null)),()->journals.delete(user,survivor.id(),1));
        assertEquals(1,editDelete.stream().filter(v->v instanceof JournalSnapshot || v instanceof UUID).count());
        assertEquals(1,editDelete.stream().filter(v->v instanceof JournalConflictException || v instanceof JournalNotFoundException).count());
        assertEquals(6,counter(user));
    }

    @Test
    void archiveCompetesSafelyWithCreateAndReassignment() throws Exception {
        UUID user=owner(); UUID target=project(user);
        var created=race(()->journals.create(user,"race",input(target)),()->{archive(user,target);return "archived";});
        assertEquals("archived",created.getLast());
        assertTrue(created.getFirst() instanceof JournalSnapshot || created.getFirst() instanceof JournalConflictException);
        if(created.getFirst() instanceof JournalConflictException conflict) assertEquals("PROJECT_ARCHIVED",conflict.getCode());
        UUID source=project(user); UUID destination=project(user); var journal=journals.create(user,"move",input(source));
        var moved=race(()->journals.update(user,journal.id(),change(1,destination)),()->{archive(user,destination);return "archived";});
        assertEquals("archived",moved.getLast());
        assertTrue(moved.getFirst() instanceof JournalSnapshot || moved.getFirst() instanceof JournalConflictException);
        if(moved.getFirst() instanceof JournalConflictException conflict) assertEquals("PROJECT_ARCHIVED",conflict.getCode());
        UUID stored=jdbc.queryForObject("select project_id from journals where id=?",UUID.class,journal.id());
        assertEquals(moved.getFirst() instanceof JournalSnapshot?destination:source,stored);
    }

    @Test
    void hashesExpiryAndRollbackPreserveAtomicOutcomes() {
        UUID user=owner(); UUID project=project(user);
        var journal=journals.create(user,"hash",input(project));
        assertThrows(JournalConflictException.class,()->journals.create(user,"hash",new CreateJournalCommand(" Journal ",project," Body\n ","2024-02-29")));
        String reordered="{\"entryDate\":\"2024-02-29\",\"body\":\" Body\\n \",\"projectId\":\""+project+"\",\"title\":\"Journal\"}";
        SnapshotAssertions.assertDataEquals(journal,journals.create(user,"hash",json.readValue(reordered,CreateJournalCommand.class)));
        for(String key:new String[]{null,""," ","한글","x".repeat(129)}) assertThrows(JournalValidationException.class,()->journals.create(user,key,input(project)));
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(s->{journals.delete(user,journal.id(),1);throw new IllegalStateException("rollback");}));
        assertEquals(1L,jdbc.queryForObject("select revision from journals where id=?",Long.class,journal.id())); assertEquals(2,counter(user));
        jdbc.update("update journal_create_idempotency set created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' where key='hash'");
        assertNotEquals(journal.id(),journals.create(user,"hash",input(project)).id());
        UUID other=owner(); assertNotEquals(journal.id(),journals.create(other,"hash",input(project(other))).id());
    }

    @Test
    void replayStorageFailureRollsBackJournalAndCounter() {
        UUID user=owner(); UUID project=project(user);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("storage failure");})
            .when(replays).save(any(UUID.class),eq("fail"),anyString(),any(JournalSnapshot.class),any(Instant.class));
        RuntimeException failure=assertThrows(RuntimeException.class,()->journals.create(user,"fail",input(project)));
        Throwable cause=failure;
        while(cause.getCause()!=null) cause=cause.getCause();
        assertInstanceOf(IllegalStateException.class,cause);
        assertEquals("storage failure",cause.getMessage());
        // The service transaction has exited; this template opens a fresh transaction.
        transaction.executeWithoutResult(s->{
            assertEquals(1,counter(user));
            assertEquals(0L,jdbc.queryForObject("select count(*) from journals where project_id=?",Long.class,project));
            assertEquals(0L,jdbc.queryForObject("select count(*) from journal_create_idempotency where key='fail'",Long.class));
            assertEquals(1L,jdbc.queryForObject("select revision from projects where id=?",Long.class,project));
            assertEquals(1L,jdbc.queryForObject("select revision from workspaces where owner_user_id=?",Long.class,user));
        });
    }
    private List<Object> race(Callable<?> a,Callable<?> b) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var futures=List.of(a,b).stream().map(call->executor.submit(()->{
                ready.countDown(); assertTrue(start.await(20,TimeUnit.SECONDS));
                try{return (Object)call.call();}catch(JournalConflictException|JournalNotFoundException expected){return expected;}
            })).toList();
            assertTrue(ready.await(20,TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.getFirst().get(30,TimeUnit.SECONDS),futures.getLast().get(30,TimeUnit.SECONDS));
        } finally {start.countDown();}
    }
}
