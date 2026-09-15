package com.kopite.devspace;
import com.kopite.devspace.compatibility.application.*;
import com.kopite.devspace.project.application.command.*;
import com.kopite.devspace.project.application.model.ProjectSnapshot;
import com.kopite.devspace.project.application.exception.ProjectIdempotencyConflictException;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class LinkRelationshipConcurrencyTests {
    @Autowired ProjectCommandService projects;
    @Autowired com.kopite.devspace.link.application.command.LinkCommandService links;
    @Autowired UserWorkspaceCreationService users; @Autowired PersonalWorkspaceRepository workspaces;
    @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;

    @RepeatedTest(3) void orderedWorkspaceLockSerializesReorderCreationAndReassignment()throws Exception {
        var evidence=new StringBuilder();
        for(String scenario:List.of("assignment-order","creation-order","creation-assignment"))for(boolean firstA:new boolean[]{true,false}) {
            var o=users.createOrReuse("link-relation-race",UUID.randomUUID().toString(),"Owner");var u=o.user().getId();var w=o.workspace().getId();
            var p=projects.create(u,"p",new CreateProjectCommand("P",null,"Java",null,null,null));var target=projects.create(u,"target",new CreateProjectCommand("Target",null,"Java",null,null,null));
            var input=new com.kopite.devspace.link.application.command.CreateLinkCommand("Link",null,"https://example.com",new com.kopite.devspace.link.application.command.LinkProjectSelection(true,p.id().toString()));
            var a=links.create(u,"a",input);var b=links.create(u,"b",input);
            Callable<Object> assignment=()->links.update(u,a.item().id(),new com.kopite.devspace.link.application.command.UpdateLinkCommand(1,null,null,null,new com.kopite.devspace.link.application.command.LinkProjectSelection(true,target.id().toString())));
            Callable<Object> creation=()->links.create(u,"new",input),order=()->links.reorder(u,2,List.of(b.item().id(),a.item().id()));
            Callable<Object> operationA=scenario.equals("assignment-order")?assignment:creation,operationB=scenario.equals("creation-assignment")?assignment:order;
            var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pid=new AtomicInteger();
            try(var pool=Executors.newFixedThreadPool(2)) {
                var first=pool.submit(()->tx(()->{workspaces.lockByOwnerId(u).orElseThrow();locked.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");return(firstA?operationA:operationB).call();}));
                assertTrue(locked.await(10,TimeUnit.SECONDS));
                var second=pool.submit(()->tx(()->{pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));started.countDown();return(firstA?operationB:operationA).call();}));
                try {
                    assertTrue(started.await(10,TimeUnit.SECONDS));boolean waiting=false;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    while(System.nanoTime()<until){waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where pid=? and wait_event_type='Lock')",Boolean.class,pid.get()));if(waiting)break;Thread.sleep(10);}
                    assertTrue(waiting,"Competing Link operation must wait for the workspace lock");
                } finally {release.countDown();}
                var results=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
                boolean oneSuccess=scenario.equals("assignment-order")||(scenario.equals("creation-order")&&firstA);
                long successes=oneSuccess?1:2;
                assertEquals(2-successes,results.stream().filter(com.kopite.devspace.link.domain.LinkConflictException.class::isInstance).count());
                assertEquals(4+successes,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,w));
                assertEquals(2+successes,jdbc.queryForObject("select revision from link_collections where workspace_id=?",Long.class,w));
                assertEquals(scenario.equals("assignment-order")?2L:3L,jdbc.queryForObject("select count(*) from links where workspace_id=?",Long.class,w));
                boolean assigned=scenario.equals("creation-assignment")||(scenario.equals("assignment-order")&&firstA);
                assertEquals(assigned?target.id():p.id(),jdbc.queryForObject("select project_id from links where id=?",UUID.class,a.item().id()));
                boolean reordered=!scenario.equals("creation-assignment")&&!firstA;
                var ids=jdbc.queryForList("select id from links where workspace_id=? order by position,id",UUID.class,w);
                assertEquals(reordered?b.item().id():a.item().id(),ids.getFirst());
                assertEquals(2,jdbc.queryForObject("select count(distinct position) from links where id in (?,?)",Integer.class,a.item().id(),b.item().id()));
                assertEquals(scenario.equals("creation-order")&&firstA?1L:2L,jdbc.queryForObject("select revision from links where id=?",Long.class,a.item().id()));
                evidence.append(scenario).append(" firstA=").append(firstA).append(" PostgreSQL Lock observed; exact revisions, relation, order and counters verified\n");
            } finally {release.countDown();}
        }
        Path out=Path.of(".gradle/project-category-only-validation/concurrency");Files.createDirectories(out);Files.writeString(out.resolve("link-relations-"+UUID.randomUUID()+".txt"),evidence);
    }
    Object tx(Callable<Object> action) {
        try{return new TransactionTemplate(manager).execute(s->{try{return action.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});}
        catch(com.kopite.devspace.link.domain.LinkConflictException expected){return expected;}
    }
}
