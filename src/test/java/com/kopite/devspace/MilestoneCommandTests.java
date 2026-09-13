package com.kopite.devspace;
import com.kopite.devspace.milestone.application.command.CreateMilestoneCommand;
import com.kopite.devspace.milestone.application.command.MilestoneCommandService;
import com.kopite.devspace.milestone.application.command.UpdateMilestoneCommand;
import com.kopite.devspace.milestone.application.exception.MilestoneNotFoundException;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import com.kopite.devspace.milestone.application.port.MilestoneCreateReplayStore;
import com.kopite.devspace.milestone.domain.Milestone;
import com.kopite.devspace.milestone.domain.MilestoneConflictException;
import com.kopite.devspace.milestone.domain.MilestoneValidationException;
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
class MilestoneCommandTests {
    private final MilestoneCommandService milestones;
    private final ProjectCommandService projects;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean MilestoneCreateReplayStore replays;
    @Autowired
    MilestoneCommandTests(MilestoneCommandService milestones,ProjectCommandService projects,UserWorkspaceCreationService users,
                        JdbcTemplate jdbc,PlatformTransactionManager manager,JsonMapper json,jakarta.persistence.EntityManager entityManager) {
        this.milestones=milestones; this.projects=projects; this.users=users; this.jdbc=jdbc;
        this.transaction=new TransactionTemplate(manager); this.json=json;this.entityManager=entityManager;
    }
    private UUID owner() { return users.createOrReuse("milestone-command",UUID.randomUUID().toString(),"Owner").user().getId(); }
    private UUID project(UUID owner) { return projects.create(owner,UUID.randomUUID().toString(),new CreateProjectCommand("Project",null,"server","Java",null,null,null)).id(); }
    private CreateMilestoneCommand input(UUID project) { return new CreateMilestoneCommand("Milestone",project,"2024-02-29",false,true); }
    private UpdateMilestoneCommand change(long revision,UUID project) { return new UpdateMilestoneCommand(revision,null,project,"2026-09-01",true,true); }
    private void archive(UUID user,UUID project) { projects.update(user,project,new UpdateProjectCommand(1,null,null,null,null,null,null,null,"archived")); }
    private long counter(UUID user) { return jdbc.queryForObject("select data_revision from workspaces where owner_user_id=?",Long.class,user); }

    @Test
    void archivedRelationOwnershipAndPermanentDeleteReplay() {
        UUID user=owner(); UUID project=project(user); UUID other=owner(); UUID foreign=project(other);
        var milestone=milestones.create(user,"create",input(project)); archive(user,project);
        var sibling=milestones.create(user,"new",input(project));
        var projectBefore=jdbc.queryForMap("select * from projects where id=?",project);
        var siblingBefore=jdbc.queryForMap("select * from milestones where id=?",sibling.id());
        var edited=milestones.update(user,milestone.id(),change(1,project)); assertEquals(2,edited.revision());
        assertEquals(milestone.createdAt(),edited.createdAt());
        assertThrows(MilestoneNotFoundException.class,()->milestones.create(other,"create",input(project)));
        assertThrows(MilestoneNotFoundException.class,()->milestones.update(user,milestone.id(),change(99,foreign)));
        assertThrows(MilestoneNotFoundException.class,()->milestones.update(other,milestone.id(),change(99,null)));
        assertThrows(MilestoneNotFoundException.class,()->milestones.delete(other,milestone.id(),99));
        assertEquals(milestone,milestones.create(user,"create",input(project)));
        assertEquals(milestone.id(),milestones.delete(user,milestone.id(),2));
        long before=counter(user);
        assertEquals(milestone,milestones.create(user,"create",input(project)));
        assertEquals(before,counter(user));
        assertEquals(0L,jdbc.queryForObject("select count(*) from milestones where id=?",Long.class,milestone.id()));
        assertThrows(MilestoneNotFoundException.class,()->milestones.delete(user,milestone.id(),2));
        assertThrows(MilestoneNotFoundException.class,()->milestones.update(user,milestone.id(),change(2,null)));
        assertEquals(1L,jdbc.queryForObject("select revision from workspaces where owner_user_id=?",Long.class,user));
        assertEquals(2L,jdbc.queryForObject("select revision from projects where id=?",Long.class,project));
        assertEquals(projectBefore,jdbc.queryForMap("select * from projects where id=?",project));
        assertEquals(siblingBefore,jdbc.queryForMap("select * from milestones where id=?",sibling.id()));
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    void creationAndMutationRacesHaveSingleWinnerAndAtomicCounters() throws Exception {
        UUID user=owner(); UUID project=project(user);
        var same=race(()->milestones.create(user,"same",input(project)),()->milestones.create(user,"same",input(project)));
        assertInstanceOf(MilestoneSnapshot.class,same.getFirst()); assertEquals(same.getFirst(),same.getLast());
        var milestone=(MilestoneSnapshot)same.getFirst(); assertEquals(2,counter(user));
        assertEquals(1L,jdbc.queryForObject("select count(*) from milestones where project_id=?",Long.class,project));
        var edits=race(()->milestones.update(user,milestone.id(),change(1,null)),()->milestones.update(user,milestone.id(),new UpdateMilestoneCommand(1,null,null,null,false,false)));
        assertEquals(1,edits.stream().filter(MilestoneSnapshot.class::isInstance).count());
        assertEquals(1,edits.stream().filter(MilestoneConflictException.class::isInstance).count());
        assertEquals(3,counter(user));
        var editWinner=(MilestoneSnapshot)edits.stream().filter(MilestoneSnapshot.class::isInstance).findFirst().orElseThrow();
        assertEquals(editWinner.completed(),jdbc.queryForObject("select completed from milestones where id=?",Boolean.class,milestone.id()));
        assertEquals(2L,jdbc.queryForObject("select revision from milestones where id=?",Long.class,milestone.id()));
        var deletion=race(()->milestones.delete(user,milestone.id(),2),()->milestones.delete(user,milestone.id(),2));
        assertEquals(1,deletion.stream().filter(UUID.class::isInstance).count());
        assertEquals(1,deletion.stream().filter(MilestoneNotFoundException.class::isInstance).count()); assertEquals(4,counter(user));
        assertEquals(0L,jdbc.queryForObject("select count(*) from milestones where id=?",Long.class,milestone.id()));
        var different=race(()->milestones.create(user,"different",input(project)),
            ()->milestones.create(user,"different",new CreateMilestoneCommand("Other",project,"2024-02-29",false,true)));
        assertEquals(1,different.stream().filter(MilestoneSnapshot.class::isInstance).count());
        assertEquals(1,different.stream().filter(MilestoneConflictException.class::isInstance).count());
        var survivor=(MilestoneSnapshot)different.stream().filter(MilestoneSnapshot.class::isInstance).findFirst().orElseThrow();
        var editDelete=race(()->milestones.update(user,survivor.id(),change(1,null)),()->milestones.delete(user,survivor.id(),1));
        assertEquals(1,editDelete.stream().filter(v->v instanceof MilestoneSnapshot || v instanceof UUID).count());
        assertEquals(1,editDelete.stream().filter(v->v instanceof MilestoneConflictException || v instanceof MilestoneNotFoundException).count());
        assertEquals(6,counter(user));
        assertEquals(editDelete.stream().anyMatch(MilestoneSnapshot.class::isInstance)?1L:0L,
            jdbc.queryForObject("select count(*) from milestones where project_id=?",Long.class,project));
        assertEquals(2L,jdbc.queryForObject("select count(*) from milestone_create_idempotency where workspace_id=(select id from workspaces where owner_user_id=?)",Long.class,user));
    }

    @Test
    void archiveCompetesSafelyWithCreateAndReassignment() throws Exception {
        UUID user=owner(); UUID target=project(user);
        var created=race(()->milestones.create(user,"race",input(target)),()->{archive(user,target);return "archived";});
        assertEquals("archived",created.getLast());
        assertInstanceOf(MilestoneSnapshot.class,created.getFirst());
        UUID source=project(user); UUID destination=project(user); var milestone=milestones.create(user,"move",input(source));
        var moved=race(()->milestones.update(user,milestone.id(),change(1,destination)),()->{archive(user,destination);return "archived";});
        assertEquals("archived",moved.getLast());
        assertInstanceOf(MilestoneSnapshot.class,moved.getFirst());
        UUID stored=jdbc.queryForObject("select project_id from milestones where id=?",UUID.class,milestone.id());
        assertEquals(destination,stored);
    }

    @Test
    void hashesExpiryAndRollbackPreserveAtomicOutcomes() {
        UUID user=owner(); UUID project=project(user);
        var milestone=milestones.create(user,"hash",input(project));
        assertThrows(MilestoneConflictException.class,()->milestones.create(user,"hash",new CreateMilestoneCommand(" Milestone ",project,"2024-02-29",false,true)));
        String reordered="{\"dueDatePresent\":true,\"completed\":false,\"dueDate\":\"2024-02-29\",\"projectId\":\""+project+"\",\"title\":\"Milestone\"}";
        assertEquals(milestone,milestones.create(user,"hash",json.readValue(reordered,CreateMilestoneCommand.class)));
        for(String key:new String[]{null,""," ","한글","x".repeat(129)}) assertThrows(MilestoneValidationException.class,()->milestones.create(user,key,input(project)));
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(s->{milestones.delete(user,milestone.id(),1); entityManager.flush();
            assertEquals(0L,jdbc.queryForObject("select count(*) from milestones where id=?",Long.class,milestone.id()));
            assertEquals(3,counter(user)); throw new IllegalStateException("rollback");}));
        transaction.executeWithoutResult(state->{
            assertEquals(1L,jdbc.queryForObject("select revision from milestones where id=?",Long.class,milestone.id())); assertEquals(2,counter(user));
        });
        jdbc.update("update milestone_create_idempotency set created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' where key='hash'");
        assertNotEquals(milestone.id(),milestones.create(user,"hash",input(project)).id());
        UUID other=owner(); assertNotEquals(milestone.id(),milestones.create(other,"hash",input(project(other))).id());
    }

    @Test
    void replayStorageFailureRollsBackMilestoneAndCounter() {
        UUID user=owner(); UUID project=project(user);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("storage failure");})
            .when(replays).save(any(UUID.class),eq("fail"),anyString(),any(MilestoneSnapshot.class),any(Instant.class));
        RuntimeException failure=assertThrows(RuntimeException.class,()->milestones.create(user,"fail",input(project)));
        Throwable cause=failure;
        while(cause.getCause()!=null) cause=cause.getCause();
        assertInstanceOf(IllegalStateException.class,cause);
        assertEquals("storage failure",cause.getMessage());
        // The service transaction has exited; this template opens a fresh transaction.
        transaction.executeWithoutResult(s->{
            assertEquals(1,counter(user));
            assertEquals(0L,jdbc.queryForObject("select count(*) from milestones where project_id=?",Long.class,project));
            assertEquals(0L,jdbc.queryForObject("select count(*) from milestone_create_idempotency where key='fail'",Long.class));
            assertEquals(1L,jdbc.queryForObject("select revision from projects where id=?",Long.class,project));
            assertEquals(1L,jdbc.queryForObject("select revision from workspaces where owner_user_id=?",Long.class,user));
        });
    }
    @Test
    void presenceNullClearingCompletionAndArchivedReassignment() {
        UUID user=owner(); UUID source=project(user); UUID target=project(user); archive(user,target);
        var initial=milestones.create(user,"omitted",new CreateMilestoneCommand("Milestone",source,null,null,false));
        assertNull(initial.dueDate()); assertFalse(initial.completed());
        assertThrows(MilestoneConflictException.class,()->milestones.create(user,"omitted",new CreateMilestoneCommand("Milestone",source,null,null,true)));
        assertThrows(MilestoneConflictException.class,()->milestones.create(user,"omitted",new CreateMilestoneCommand("Milestone",source,null,false,false)));
        var dated=milestones.update(user,initial.id(),new UpdateMilestoneCommand(1,null,target,"2024-02-29",true,true));
        assertTrue(dated.completed()); assertEquals(target,dated.projectId());
        var reopened=milestones.update(user,initial.id(),new UpdateMilestoneCommand(2,null,null,null,false,false));
        assertFalse(reopened.completed()); assertEquals(dated.dueDate(),reopened.dueDate());
        var cleared=milestones.update(user,initial.id(),new UpdateMilestoneCommand(3,null,null,null,null,true));
        assertNull(cleared.dueDate());
        var same=milestones.update(user,initial.id(),new UpdateMilestoneCommand(4,null,null,null,null,false));
        assertEquals(5,same.revision()); assertEquals(initial.createdAt(),same.createdAt());
        assertEquals(initial,milestones.create(user,"omitted",new CreateMilestoneCommand("Milestone",source,null,null,false)));
        long before=counter(user);
        assertThrows(MilestoneConflictException.class,()->milestones.delete(user,initial.id(),4)); assertEquals(before,counter(user));
        jdbc.update("update milestones set revision=? where id=?",Milestone.MAX_REVISION,initial.id());
        assertEquals(initial.id(),milestones.delete(user,initial.id(),Milestone.MAX_REVISION)); assertEquals(before+1,counter(user));
        assertThrows(MilestoneNotFoundException.class,()->milestones.delete(user,initial.id(),Milestone.MAX_REVISION)); assertEquals(before+1,counter(user));
    }

    private List<Object> race(Callable<?> a,Callable<?> b) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var futures=List.of(a,b).stream().map(call->executor.submit(()->{
                ready.countDown(); assertTrue(start.await(20,TimeUnit.SECONDS));
                try{return (Object)call.call();}catch(MilestoneConflictException|MilestoneNotFoundException expected){return expected;}
            })).toList();
            assertTrue(ready.await(20,TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.getFirst().get(30,TimeUnit.SECONDS),futures.getLast().get(30,TimeUnit.SECONDS));
        } finally {start.countDown();}
    }
}
