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
class DashboardCategoryConcurrencyTests {
    @Autowired com.kopite.devspace.dashboard.application.HomeDashboardCommandService dashboards;
    @Autowired com.kopite.devspace.dashboard.application.HomeDashboardQueryService queries;
    @Autowired com.kopite.devspace.projectcategory.application.CategoryCommandService categories;
    @Autowired UserWorkspaceCreationService users; @Autowired PersonalWorkspaceRepository workspaces;
    @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;

    @RepeatedTest(3) void categoryDeletionAndDashboardSavesHaveDeterministicSerialOutcomes()throws Exception {
        var evidence=new StringBuilder();
        for(String scenario:List.of("delete-save","first-save","replace-save"))for(boolean saveFirst:new boolean[]{true,false}) {
            var o=users.createOrReuse("dashboard-category-race",UUID.randomUUID().toString(),"Owner");var u=o.user().getId();var w=o.workspace().getId();
            var category=categories.create(u,"category","Category");
            long expected=scenario.equals("replace-save")?1:0;
            if(expected==1)dashboards.save(u,0,List.of(widget("initial",null)));
            long before=jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,w);
            Callable<Object> a=()->dashboards.save(u,expected,List.of(widget("a",scenario.equals("delete-save")?category.id():null)));
            Callable<Object> b=scenario.equals("delete-save")?()->categories.delete(u,category.id(),1):()->dashboards.save(u,expected,List.of(widget("b",null)));
            var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pid=new AtomicInteger();
            try(var pool=Executors.newFixedThreadPool(2)) {
                var first=pool.submit(()->tx(()->{workspaces.lockByOwnerId(u).orElseThrow();locked.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");return(saveFirst?a:b).call();}));
                assertTrue(locked.await(10,TimeUnit.SECONDS));
                var second=pool.submit(()->tx(()->{pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));started.countDown();return(saveFirst?b:a).call();}));
                try {
                    assertTrue(started.await(10,TimeUnit.SECONDS));boolean waiting=false;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    while(System.nanoTime()<until){waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where pid=? and wait_event_type='Lock')",Boolean.class,pid.get()));if(waiting)break;Thread.sleep(10);}
                    assertTrue(waiting,"Competing operation must actually wait on PostgreSQL workspace lock");
                } finally {release.countDown();}
                var results=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
                var current=queries.get(u);
                if(scenario.equals("delete-save")) {
                    assertTrue(results.contains(category.id()));assertEquals(saveFirst?1:0,current.revision());
                    assertEquals(before+(saveFirst?2:1),current.dataRevision());
                    if(saveFirst){assertInstanceOf(com.kopite.devspace.dashboard.application.HomeDashboardSnapshot.class,results.getFirst());assertEquals(Set.of("a"),current.missingCategoryWidgetIds());}
                    else assertInstanceOf(com.kopite.devspace.dashboard.application.DashboardNotFoundException.class,results.getLast());
                    assertEquals(0,jdbc.queryForObject("select count(*) from project_categories where id=?",Integer.class,category.id()));
                } else {
                    assertInstanceOf(com.kopite.devspace.dashboard.application.HomeDashboardSnapshot.class,results.getFirst());assertInstanceOf(com.kopite.devspace.dashboard.domain.DashboardConflictException.class,results.getLast());
                    assertEquals(expected+1,current.revision());assertEquals(before+1,current.dataRevision());assertEquals(saveFirst?"a":"b",current.widgets().getFirst().id());
                }
                evidence.append(scenario).append(" saveFirst=").append(saveFirst).append(" PostgreSQL Lock observed; exact state/revisions/counters verified\n");
            } finally {release.countDown();}
        }
        Path out=Path.of(".gradle/project-category-only-validation/concurrency");Files.createDirectories(out);Files.writeString(out.resolve("dashboard-category-"+UUID.randomUUID()+".txt"),evidence);
    }
    com.kopite.devspace.dashboard.domain.DashboardWidget widget(String id,UUID category) {
        var selection=category==null?com.kopite.devspace.dashboard.domain.DashboardSelection.all():new com.kopite.devspace.dashboard.domain.DashboardSelection("category",null,category);
        return new com.kopite.devspace.dashboard.domain.DashboardWidget(id,"board","Board","wide",selection,null);
    }
    Object tx(Callable<Object> action) {
        try{return new TransactionTemplate(manager).execute(s->{try{return action.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});}
        catch(com.kopite.devspace.dashboard.domain.DashboardConflictException|com.kopite.devspace.dashboard.application.DashboardNotFoundException expected){return expected;}
    }
}
