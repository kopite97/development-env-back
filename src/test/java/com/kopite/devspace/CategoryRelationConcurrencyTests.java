package com.kopite.devspace;

import com.kopite.devspace.project.application.command.*;
import com.kopite.devspace.project.application.exception.ProjectNotFoundException;
import com.kopite.devspace.project.application.model.ProjectSnapshot;
import com.kopite.devspace.projectcategory.application.*;
import com.kopite.devspace.projectcategory.domain.CategoryConflictException;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class CategoryRelationConcurrencyTests {
    @Autowired ProjectCommandService projects;
    @Autowired CategoryCommandService categories;
    @Autowired UserWorkspaceCreationService users;
    @Autowired PersonalWorkspaceRepository workspaces;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @RepeatedTest(3) void assignmentAndDeletionBothOrdersWaitOnActualDatabaseLock()throws Exception {
        var evidence=new StringBuilder();
        for(String operation:List.of("create","active-patch","archived-patch")) for(boolean assignFirst:new boolean[]{true,false}) {
            var o=users.createOrReuse("category-race",UUID.randomUUID().toString(),"Owner");UUID u=o.user().getId(),w=o.workspace().getId();
            var c=categories.create(u,"category","Category");
            ProjectSnapshot existing=operation.equals("create")?null:projects.create(u,"original",input(ProjectCategorySelection.omitted()));
            if(operation.equals("archived-patch"))existing=projects.update(u,existing.id(),new UpdateProjectCommand(1,null,null,null,null,null,null,"archived"));
            final var p=existing;long before=counter(w);
            Callable<Object> assign=()->p==null?projects.create(u,"assign",input(new ProjectCategorySelection(true,c.id().toString())))
                :projects.update(u,p.id(),new UpdateProjectCommand(p.revision(),null,null,null,null,null,null,null,new ProjectCategorySelection(true,c.id().toString())));
            Callable<Object> remove=()->categories.delete(u,c.id(),1);
            var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var loserStarted=new CountDownLatch(1);var loserPid=new AtomicInteger();
            try(var pool=Executors.newFixedThreadPool(2)) {
                var winner=pool.submit(()->transaction(()->{
                    workspaces.lockByOwnerId(u).orElseThrow();locked.countDown();
                    if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");return (assignFirst?assign:remove).call();
                }));
                assertTrue(locked.await(10,TimeUnit.SECONDS));
                var loser=pool.submit(()->transaction(()->{loserPid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));loserStarted.countDown();return (assignFirst?remove:assign).call();}));
                try {
                    assertTrue(loserStarted.await(10,TimeUnit.SECONDS));
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                    while(System.nanoTime()<deadline) {
                        waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where pid=? and wait_event_type='Lock')",Boolean.class,loserPid.get()));
                        if(waiting)break;Thread.sleep(10);
                    }
                    assertTrue(waiting,"loser must actually wait on PostgreSQL lock; scheduling alone is not proof");
                    evidence.append(operation).append(" assignFirst=").append(assignFirst).append(" observed PostgreSQL Lock wait\n");
                } finally {release.countDown();}
                Object first=winner.get(20,TimeUnit.SECONDS),second=loser.get(20,TimeUnit.SECONDS);
                if(assignFirst){assertInstanceOf(ProjectSnapshot.class,first);assertEquals("CATEGORY_IN_USE",assertInstanceOf(CategoryConflictException.class,second).getCode());}
                else {assertEquals(c.id(),first);assertInstanceOf(ProjectNotFoundException.class,second);}
                assertEquals(before+1,counter(w));
                assertEquals(assignFirst?1L:0L,jdbc.queryForObject("select count(*) from projects where workspace_id=? and category_id=?",Long.class,w,c.id()));
                assertEquals(assignFirst?1L:0L,jdbc.queryForObject("select count(*) from project_categories where id=?",Long.class,c.id()));
                assertEquals(operation.equals("create")&&assignFirst?1L:0L,jdbc.queryForObject("select count(*) from project_create_idempotency where workspace_id=? and key='assign'",Long.class,w));
                if(p!=null)assertEquals(assignFirst?p.revision()+1:p.revision(),jdbc.queryForObject("select revision from projects where id=?",Long.class,p.id()));
            } finally {release.countDown();}
        }
        Path out=Path.of(".gradle/project-category-validation/concurrency");Files.createDirectories(out);Files.writeString(out.resolve("assignment-delete-"+UUID.randomUUID()+".txt"),evidence);
    }
    Object transaction(Callable<Object> action) {
        try{return new TransactionTemplate(manager).execute(s->{try{return action.call();}catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalStateException(ex);}});}
        catch(CategoryConflictException|ProjectNotFoundException ex){return ex;}
    }
    long counter(UUID w){return jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,w);}
    CreateProjectCommand input(ProjectCategorySelection c){return new CreateProjectCommand("Project",null,"Java",null,null,null,c);}
}
