package com.kopite.devspace;
import com.kopite.devspace.overview.application.*;
import com.kopite.devspace.project.application.exception.ProjectNotFoundException;
import com.kopite.devspace.project.application.query.*;
import com.kopite.devspace.task.application.query.*;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OverviewQueryTests {
    @Autowired OverviewQueryService queries;
    @Autowired TaskQueryService tasks;
    @Autowired ProjectQueryService projects;
    @Autowired UserWorkspaceCreationService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean OverviewProjectRepository aggregate;
    record Owner(UUID user,UUID workspace) {}
    Owner owner() {var u=users.createOrReuse("overview-query",UUID.randomUUID().toString(),"Owner");return new Owner(u.user().getId(),u.workspace().getId());}
    UUID project(Owner o,String scope,String status) {
        UUID p=UUID.randomUUID();jdbc.update("insert into projects(id,workspace_id,name,scope,stack,status,created_at,updated_at) values(?,?,'Project',?,'Java',?,now(),now())",p,o.workspace(),scope,status);return p;
    }
    UUID task(Owner o,UUID p,String status,boolean deleted) {
        UUID id=UUID.randomUUID();jdbc.update("insert into tasks(id,workspace_id,project_id,title,status,deleted_at,created_at,updated_at) values(?,?,?,'Task',?,case when ? then now() else null end,now(),now())",id,o.workspace(),p,status,deleted);return id;
    }
    long count(OverviewSnapshot s,String scope,String status) {return s.projects().stream().filter(c->c.scope().equals(scope)&&c.status().equals(status)).mapToLong(OverviewProjectRepository.Count::count).sum();}
    OverviewSnapshot read(Owner o,String scope,UUID p) {return queries.get(o.user(),new OverviewFilter(scope,p));}
    @Test void filtersArchiveTrashRestoreOwnershipAndTaskStatsParity() {
        Owner o=owner(),other=owner();
        var empty=read(o,"all",null);assertTrue(empty.projects().isEmpty());assertEquals(Map.of("todo",0L,"doing",0L,"done",0L),empty.tasks().counts());
        UUID ua=project(o,"unity","active"),ur=project(o,"unity","archived"),sa=project(o,"server","active"),foreign=project(other,"server","active");
        task(o,ua,"todo",false);task(o,ur,"doing",false);task(o,sa,"done",false);UUID trash=task(o,sa,"todo",true);task(other,foreign,"todo",false);
        var all=read(o,"all",null);assertEquals(1,count(all,"unity","active"));assertEquals(1,count(all,"unity","archived"));assertEquals(1,count(all,"server","active"));assertEquals(3,all.tasks().total());
        var archived=read(o,"all",ur);assertEquals(1,count(archived,"unity","archived"));assertEquals(0,count(archived,"unity","active"));assertEquals(1,archived.tasks().counts().get("doing"));
        var excluded=read(o,"server",ur);assertTrue(excluded.projects().isEmpty());assertEquals(0,excluded.tasks().total());
        for(UUID bad:List.of(foreign,UUID.randomUUID()))assertThrows(ProjectNotFoundException.class,()->read(o,"unity",bad));
        for(String scope:List.of("all","unity","server")) {
            var result=read(o,scope,null);var stats=tasks.stats(o.user(),new TaskListFilter(scope,null,"all","",null,false,1));
            assertEquals(stats.counts(),result.tasks().counts());assertEquals(stats.total(),result.tasks().total());
        }
        jdbc.update("update tasks set deleted_at=null where id=?",trash);assertEquals(4,read(o,"all",null).tasks().total());
        jdbc.update("update tasks set deleted_at=now() where id=?",trash);assertEquals(3,read(o,"all",null).tasks().total());
        assertEquals(0L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from dashboards where workspace_id=?",Long.class,o.workspace()));
    }
    @Test void allRowsNotPagesAndConstantQueryCount() throws Exception {
        Owner o=owner();UUID p=project(o,"unity","active");task(o,p,"todo",false);
        var statistics=factory.unwrap(SessionFactory.class).getStatistics();boolean enabled=statistics.isStatisticsEnabled();statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();read(o,"all",null);long small=statistics.getPrepareStatementCount();
            for(int i=0;i<120;i++){UUID more=project(o,i%2==0?"unity":"server",i%3==0?"archived":"active");task(o,more,i%2==0?"doing":"done",false);}
            statistics.clear();var result=read(o,"all",null);assertEquals(small,statistics.getPrepareStatementCount());
            assertEquals(121,result.projects().stream().mapToLong(OverviewProjectRepository.Count::count).sum());assertEquals(121,result.tasks().total());
            var taskFilter=new TaskListFilter("all",null,"all","",null,false,7);
            var page=tasks.list(o.user(),taskFilter,null);assertEquals(7,page.items().size());assertEquals(121,page.total());assertNotNull(page.nextCursor());
            var second=tasks.list(o.user(),taskFilter,page.nextCursor());assertEquals(7,second.items().size());assertEquals(121,second.total());
            var projectPage=projects.list(o.user(),new ProjectListFilter("all","all","",3),null);
            assertEquals(3,projectPage.items().size());assertEquals(121,projectPage.total());assertNotNull(projectPage.nextCursor());
            assertEquals(result.projects(),read(o,"all",null).projects());assertEquals(121,read(o,"all",null).tasks().total());
            Path output=Path.of("build/reports/dashboard-overview-api/query-plan.txt");Files.createDirectories(output.getParent());
            var plans=new ArrayList<String>();
            plans.addAll(jdbc.queryForList("explain (analyze,buffers) select scope,status,count(*) from projects where workspace_id=? group by scope,status",String.class,o.workspace()));
            plans.addAll(jdbc.queryForList("explain (analyze,buffers) select t.status,count(*) from tasks t join projects p on p.workspace_id=t.workspace_id and p.id=t.project_id where t.workspace_id=? and t.deleted_at is null group by t.status",String.class,o.workspace()));
            Files.write(output,plans);
        } finally {statistics.setStatisticsEnabled(enabled);}
    }
    @Test void projectAndTaskCountsRemainInOneSnapshotAcrossConcurrentCommit() throws Exception {
        for(boolean changeScope:new boolean[]{false,true}) {
            Owner o=owner();UUID p=project(o,"unity","active"),t=task(o,p,"todo",false);var once=new AtomicBoolean();
            try(var executor=Executors.newSingleThreadExecutor()) {
                doAnswer(call->{var result=call.callRealMethod();if(once.compareAndSet(false,true))
                    executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(s->{
                        jdbc.update("update projects set scope=?,status='archived' where id=?",changeScope?"server":"unity",p);
                        jdbc.update("update tasks set status='done' where id=?",t);
                    })).get(20,TimeUnit.SECONDS);return result;
                }).when(aggregate).counts(o.workspace(),new OverviewFilter("unity",p));
                var before=read(o,"unity",p);assertTrue(once.get());assertEquals(1,count(before,"unity","active"));assertEquals(1,before.tasks().counts().get("todo"));assertEquals(0,before.tasks().counts().get("done"));
            }
            var after=read(o,"unity",p);assertEquals(0,count(after,"unity","active"));
            assertEquals(changeScope?0:1,count(after,"unity","archived"));assertEquals(changeScope?0:1,after.tasks().counts().get("done"));
            assertEquals(0L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace()));
        }
    }
}
