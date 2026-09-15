package com.kopite.devspace;
import com.kopite.devspace.dashboard.application.*;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import com.kopite.devspace.project.domain.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DashboardCommandTests {
    @Autowired HomeDashboardCommandService commands;
    @Autowired HomeDashboardQueryService queries;
    @Autowired UserWorkspaceCreationService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired PersonalWorkspaceRepository workspaces;
    @Autowired ProjectRepository projects;
    @Autowired EntityManager em;
    @MockitoSpyBean HomeDashboardRepository dashboards;
    UUID owner() {return users.createOrReuse("dashboard-command",UUID.randomUUID().toString(),"Owner").user().getId();}
    UUID workspace(UUID u) {return jdbc.queryForObject("select id from workspaces where owner_user_id=?",UUID.class,u);}
    long counter(UUID u) {return jdbc.queryForObject("select data_revision from workspaces where owner_user_id=?",Long.class,u);}
    List<String> state(UUID u) {return jdbc.queryForList("select row_to_json(d)::text from dashboards d where workspace_id=?",String.class,workspace(u));}
    List<DashboardWidget> layout(String id) {return List.of(new DashboardWidget(id,"board","Board","wide",com.kopite.devspace.dashboard.domain.DashboardSelection.all(),null));}
    List<DashboardWidget> selected(UUID p) {return List.of(new DashboardWidget("p","overview","Project","small",new com.kopite.devspace.dashboard.domain.DashboardSelection("project",p,null),3));}
    UUID project(UUID u,boolean archived) {
        UUID p=UUID.randomUUID();
        jdbc.update("insert into projects(id,workspace_id,name,stack,status,created_at,updated_at) values(?,?,'Project','Java',?,now(),now())",p,workspace(u),archived?"archived":"active");return p;
    }
    @Test void defaultsDoNotWriteAndAllRevisionTransitions() {
        UUID u=owner();project(u,false);
        for(int i=0;i<3;i++){var d=queries.get(u);assertEquals(0,d.revision());assertEquals(HomeDashboard.defaults(),d.widgets());}
        assertTrue(state(u).isEmpty());assertEquals(0,counter(u));
        assertThrows(DashboardConflictException.class,()->commands.save(u,1,List.of()));assertTrue(state(u).isEmpty());
        var first=commands.save(u,0,List.of());assertEquals(1,first.revision());assertTrue(queries.get(u).widgets().isEmpty());
        assertThrows(DashboardConflictException.class,()->commands.save(u,0,layout("a")));
        var a=commands.save(u,1,HomeDashboard.defaults());assertEquals(2,a.revision());
        var b=commands.save(u,2,a.widgets().reversed());assertEquals(a.widgets().reversed(),queries.get(u).widgets());
        assertEquals(4,commands.save(u,3,b.widgets()).revision());assertEquals(4,counter(u));
        assertEquals(1L,jdbc.queryForObject("select revision from workspaces where owner_user_id=?",Long.class,u));
        assertEquals(1L,jdbc.queryForObject("select revision from projects where workspace_id=?",Long.class,workspace(u)));
        assertThrows(DashboardConflictException.class,()->commands.save(u,3,List.of()));
        assertEquals(4,counter(u));
    }
    @Test void ownedArchivedReferencesAndBrokenStoredReference() {
        UUID u=owner(),other=owner(),p=project(u,true),foreign=project(other,false);
        assertThrows(DashboardNotFoundException.class,()->commands.save(u,99,selected(foreign)));
        assertThrows(DashboardNotFoundException.class,()->commands.save(u,0,selected(UUID.randomUUID())));
        assertTrue(state(u).isEmpty());assertEquals(0,counter(u));
        var result=commands.save(u,0,selected(p));assertEquals("project",result.widgets().getFirst().selection().kind());
        assertEquals(result,queries.get(u));assertEquals(0,queries.get(other).revision());
        var before=state(u);
        jdbc.update("update dashboards set widgets=jsonb_set(widgets,'{0,selection,projectId}',to_jsonb(cast(? as text))) where workspace_id=?",foreign.toString(),workspace(u));
        assertThrows(DashboardNotFoundException.class,()->queries.get(u));assertEquals(1,counter(u));
        assertNotEquals(before,state(u));
        assertEquals(2,commands.save(u,1,List.of()).revision());assertTrue(queries.get(u).widgets().isEmpty());
    }
    @Test void firstAndExistingSaveRollbackAfterActualFlush() {
        UUID u=owner();
        doAnswer(call->{call.callRealMethod();assertEquals(1L,jdbc.queryForObject("select count(*) from dashboards where workspace_id=?",Long.class,workspace(u)));
            assertEquals(1,counter(u));throw new IllegalStateException("injected after first flush");}).when(dashboards).flush();
        assertInjected(assertThrows(RuntimeException.class,()->commands.save(u,0,layout("first"))),"injected after first flush");
        assertTrue(state(u).isEmpty());assertEquals(0,counter(u));
        reset(dashboards);commands.save(u,0,layout("saved"));var before=state(u);
        doAnswer(call->{call.callRealMethod();assertEquals(2,counter(u));assertEquals(2L,jdbc.queryForObject("select revision from dashboards where workspace_id=?",Long.class,workspace(u)));
            throw new IllegalStateException("injected after update flush");}).when(dashboards).flush();
        assertInjected(assertThrows(RuntimeException.class,()->commands.save(u,1,layout("replacement"))),"injected after update flush");
        new TransactionTemplate(manager).executeWithoutResult(s->{assertEquals(before,state(u));assertEquals(1,counter(u));});
    }
    @Test void counterAndDashboardOverflowRollBack() {
        UUID u=owner();commands.save(u,0,layout("a"));
        jdbc.update("update dashboards set revision=? where workspace_id=?",HomeDashboard.MAX_REVISION,workspace(u));
        var before=state(u);assertThrows(DashboardConflictException.class,()->commands.save(u,HomeDashboard.MAX_REVISION,List.of()));
        assertEquals(before,state(u));assertEquals(1,counter(u));
        for(boolean existing:new boolean[]{false,true}) {
            UUID v=owner();if(existing) commands.save(v,0,layout("a"));var state=state(v);
            jdbc.update("update workspaces set data_revision=? where owner_user_id=?",Long.MAX_VALUE,v);
            assertThrows(DashboardConflictException.class,()->commands.save(v,existing?1:0,List.of()));
            assertEquals(state,state(v));assertEquals(Long.MAX_VALUE,counter(v));
        }
    }
    @RepeatedTest(5) void concurrentFirstAndLaterSavesHaveOneWinner() throws Exception {
        UUID u=owner();
        var first=race(()->commands.save(u,0,layout("a")),()->commands.save(u,0,layout("b")));
        assertWinner(u,first,1);
        var later=race(()->commands.save(u,1,layout("c")),()->commands.save(u,1,layout("d")));
        assertWinner(u,later,2);
        assertEquals(1,state(u).size());
    }
    @Test void differentOwnersAndProjectArchiveBothSucceed() throws Exception {
        UUID a=owner(),b=owner();var separate=race(()->commands.save(a,0,layout("same")),()->commands.save(b,0,layout("same")));
        separate.forEach(r->assertInstanceOf(HomeDashboardSnapshot.class,r));assertEquals(1,counter(a));assertEquals(1,counter(b));
        for(boolean archiveFirst:new boolean[]{true,false}) {
            UUID u=owner(),p=project(u,false);var tx=new TransactionTemplate(manager);
            try(var pool=Executors.newSingleThreadExecutor()) {
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                var blocker=pool.submit(()->tx.execute(s->{
                    var w=workspaces.lockByOwnerId(u).orElseThrow();
                    Object result;
                    if(archiveFirst) {var project=projects.lockOwned(w.getId(),p).orElseThrow();project.update(1,project.values(),"archived",java.time.Instant.now());w.recordBusinessMutation();result="archived";}
                    else result=commands.save(u,0,selected(p));
                    em.flush();entered.countDown();await(release);return result;
                }));
                assertTrue(entered.await(10,TimeUnit.SECONDS));
                try(var otherPool=Executors.newSingleThreadExecutor()) {
                    var other=otherPool.submit(()->{
                        if(archiveFirst)return commands.save(u,0,selected(p));
                        return tx.execute(s->{var w=workspaces.lockByOwnerId(u).orElseThrow();var project=projects.lockOwned(w.getId(),p).orElseThrow();project.update(1,project.values(),"archived",java.time.Instant.now());w.recordBusinessMutation();return "archived";});
                    });
                    release.countDown();assertNotNull(blocker.get(20,TimeUnit.SECONDS));assertNotNull(other.get(20,TimeUnit.SECONDS));
                } finally {release.countDown();}
            }
            assertEquals("archived",jdbc.queryForObject("select status from projects where id=?",String.class,p));
            assertEquals(selected(p),queries.get(u).widgets());assertEquals(2,counter(u));
        }
    }
    @Test void getKeepsUnsavedSnapshotWhileFirstSaveCommits() throws Exception {
        UUID u=owner();var once=new AtomicBoolean();
        try(var pool=Executors.newSingleThreadExecutor()) {
            doAnswer(call->{var result=call.callRealMethod();if(once.compareAndSet(false,true))pool.submit(()->commands.save(u,0,layout("saved"))).get(20,TimeUnit.SECONDS);return result;}).when(dashboards).find(workspace(u));
            var before=queries.get(u);assertTrue(once.get());assertEquals(0,before.revision());assertEquals(HomeDashboard.defaults(),before.widgets());
        }
        assertEquals(1,queries.get(u).revision());assertEquals(layout("saved"),queries.get(u).widgets());assertEquals(1,counter(u));
    }
    void assertWinner(UUID u,List<Object> results,long revision) {
        assertEquals(1,results.stream().filter(HomeDashboardSnapshot.class::isInstance).count(),results.toString());
        assertEquals(1,results.stream().filter(DashboardConflictException.class::isInstance).count(),results.toString());
        var winner=(HomeDashboardSnapshot)results.stream().filter(HomeDashboardSnapshot.class::isInstance).findFirst().orElseThrow();
        assertEquals(winner,queries.get(u));assertEquals(revision,winner.revision());assertEquals(revision,counter(u));
    }
    List<Object> race(Callable<?> a,Callable<?> b) throws Exception {
        CyclicBarrier barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(()->outcome(barrier,a));var y=pool.submit(()->outcome(barrier,b));
            return List.of(x.get(25,TimeUnit.SECONDS),y.get(25,TimeUnit.SECONDS));
        }
    }
    Object outcome(CyclicBarrier barrier,Callable<?> action) throws Exception {
        barrier.await(10,TimeUnit.SECONDS);try{return action.call();}catch(RuntimeException ex){return ex;}
    }
    void await(CountDownLatch latch) {try{assertTrue(latch.await(20,TimeUnit.SECONDS));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}}
    void assertInjected(Throwable ex,String message) {while(ex.getCause()!=null)ex=ex.getCause();assertEquals(message,ex.getMessage());}
}
