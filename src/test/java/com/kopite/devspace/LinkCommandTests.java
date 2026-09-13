package com.kopite.devspace;
import com.kopite.devspace.link.application.LinkLimits;
import com.kopite.devspace.link.application.command.CreateLinkCommand;
import com.kopite.devspace.link.application.command.LinkCommandService;
import com.kopite.devspace.link.application.command.UpdateLinkCommand;
import com.kopite.devspace.link.application.exception.LinkNotFoundException;
import com.kopite.devspace.link.application.exception.LinkQuotaException;
import com.kopite.devspace.link.application.model.LinkMutation;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import com.kopite.devspace.link.application.port.LinkCreateReplayStore;
import com.kopite.devspace.link.domain.Link;
import com.kopite.devspace.link.domain.LinkConflictException;
import com.kopite.devspace.link.domain.LinkRepository;
import com.kopite.devspace.link.domain.LinkValidationException;

import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LinkCommandTests {
    private final LinkCommandService commands;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EntityManager em;
    @MockitoSpyBean LinkCreateReplayStore replays;
    @MockitoSpyBean LinkRepository repository;
    @MockitoSpyBean LinkLimits limits;
    @Autowired
    LinkCommandTests(LinkCommandService commands,UserWorkspaceCreationService users,JdbcTemplate jdbc,
            PlatformTransactionManager manager,EntityManager em) {
        this.commands=commands;this.users=users;this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.em=em;
    }
    private UUID owner() { return users.createOrReuse("link-command",UUID.randomUUID().toString(),"Owner").user().getId(); }
    private UUID workspace(UUID user) { return jdbc.queryForObject("select id from workspaces where owner_user_id=?",UUID.class,user); }
    private CreateLinkCommand input() { return new CreateLinkCommand("Link",null,"https://example.com",null); }
    private LinkMutation create(UUID u) { return commands.create(u,UUID.randomUUID().toString(),input()); }
    private long counter(UUID u) { return jdbc.queryForObject("select data_revision from workspaces where owner_user_id=?",Long.class,u); }
    private long revision(UUID u) { return jdbc.queryForObject("select revision from link_collections where workspace_id=?",Long.class,workspace(u)); }
    private long count(UUID u) { return jdbc.queryForObject("select count(*) from links where workspace_id=?",Long.class,workspace(u)); }
    private List<String> state(UUID u) { return jdbc.queryForList("select row_to_json(l)::text from links l where workspace_id=? order by id",String.class,workspace(u)); }
    private UpdateLinkCommand edit(long r) { return new UpdateLinkCommand(r,"Edited",null,null,"unity"); }

    @Test
    void ownershipGapsNoopsRevisionsAndHistoricReplay() {
        UUID u=owner(),other=owner();
        var a=commands.create(u,"same",input());var b=create(u);var c=create(u);
        assertEquals(0,a.item().position());assertEquals(1,a.collectionRevision());
        assertThrows(LinkNotFoundException.class,()->commands.update(other,a.item().id(),edit(99)));
        assertThrows(LinkNotFoundException.class,()->commands.delete(other,a.item().id(),99));
        assertEquals(0L,jdbc.queryForObject("select count(*) from link_collections where workspace_id=?",Long.class,workspace(other)));
        assertThrows(LinkValidationException.class,()->commands.reorder(u,3,List.of(a.item().id(),b.item().id(),UUID.randomUUID())));
        assertThrows(LinkValidationException.class,()->commands.reorder(u,3,List.of(a.item().id(),a.item().id(),c.item().id())));
        assertThrows(LinkValidationException.class,()->commands.reorder(u,3,List.of()));
        assertThrows(LinkConflictException.class,()->commands.reorder(u,2,List.of()));
        var updated=commands.update(u,a.item().id(),edit(1));assertEquals(2,updated.item().revision());assertEquals(0,updated.item().position());
        assertEquals(a.item().createdAt(),updated.item().createdAt());
        var deleted=commands.delete(u,b.item().id(),1);assertEquals(5,deleted.collectionRevision());
        assertEquals(List.of(0L,2L),jdbc.queryForList("select position from links where workspace_id=? order by position",Long.class,workspace(u)));
        var reordered=commands.reorder(u,5,List.of(a.item().id(),c.item().id()));
        assertEquals(updated.item(),reordered.items().getFirst());assertEquals(2,reordered.items().getLast().revision());
        var noOp=commands.reorder(u,6,List.of(a.item().id(),c.item().id()));assertEquals(reordered.items(),noOp.items());
        assertEquals(7,noOp.collectionRevision());
        assertEquals(a,commands.create(u,"same",input()));
        assertThrows(LinkConflictException.class,()->commands.create(u,"same",new CreateLinkCommand("Link","","https://example.com",null)));
        assertThrows(LinkConflictException.class,()->commands.create(u,"same",new CreateLinkCommand("Link",null,"https://example.com","all")));
        assertThrows(LinkConflictException.class,()->commands.create(u,"same",new CreateLinkCommand(" Link ",null,"https://example.com",null)));
        commands.delete(u,a.item().id(),2);assertEquals(a,commands.create(u,"same",input()));assertEquals(8,counter(u));assertEquals(8,revision(u));
        assertThrows(LinkNotFoundException.class,()->commands.delete(u,a.item().id(),2));
        assertThrows(LinkNotFoundException.class,()->commands.update(u,a.item().id(),edit(2)));
        assertEquals(1,count(u));assertEquals(1L,jdbc.queryForObject("select revision from workspaces where owner_user_id=?",Long.class,u));
        assertNotEquals(a.item().id(),commands.create(other,"same",input()).item().id());
        UUID empty=owner();assertEquals(1,commands.reorder(empty,0,List.of()).collectionRevision());assertEquals(1,counter(empty));
        var expiredAt=a.item().createdAt().minusSeconds(3600);
        jdbc.update("update link_create_idempotency set expires_at=?,created_at=? where workspace_id=?",
            java.sql.Timestamp.from(expiredAt),java.sql.Timestamp.from(expiredAt.minusSeconds(86400)),workspace(u));
        assertNotEquals(a.item().id(),commands.create(u,"same",input()).item().id());
    }
    @Test
    void quotaAndOverflowLeaveNoPartialChanges() {
        UUID u=owner();var a=commands.create(u,"same",input());
        doReturn(1).when(limits).getMaxLinks();
        assertThrows(LinkQuotaException.class,()->create(u));assertEquals(a,commands.create(u,"same",input()));
        assertEquals(1,count(u));assertEquals(1,revision(u));
        commands.update(u,a.item().id(),edit(1));commands.reorder(u,2,List.of(a.item().id()));
        jdbc.update("update links set revision=? where id=?",Link.MAX_REVISION,a.item().id());
        assertThrows(LinkConflictException.class,()->commands.update(u,a.item().id(),edit(Link.MAX_REVISION)));
        assertEquals(4,commands.reorder(u,3,List.of(a.item().id())).collectionRevision());
        commands.delete(u,a.item().id(),Link.MAX_REVISION);assertEquals(5,counter(u));
        var b=create(u);jdbc.update("update link_collections set revision=? where workspace_id=?",Link.MAX_REVISION,workspace(u));
        assertThrows(LinkConflictException.class,()->commands.delete(u,b.item().id(),1));assertEquals(1,count(u));assertEquals(6,counter(u));
        jdbc.update("update link_collections set revision=6 where workspace_id=?",workspace(u));
        jdbc.update("update links set position=? where id=?",Link.MAX_REVISION,b.item().id());doReturn(500).when(limits).getMaxLinks();
        assertThrows(LinkConflictException.class,()->create(u));assertEquals(6,counter(u));
        jdbc.update("update links set revision=? where id=?",Link.MAX_REVISION,b.item().id());
        assertThrows(LinkConflictException.class,()->commands.reorder(u,6,List.of(b.item().id())));assertEquals(6,revision(u));
    }
    @Test
    void injectedFlushAndReplayFailuresRollBackInFreshTransactions() {
        UUID u=owner();var a=create(u);var b=create(u);var before=state(u);
        doAnswer(invocation->{invocation.callRealMethod();throw new IllegalStateException("injected reorder failure");}).when(repository).finishReorder();
        var failure=assertThrows(RuntimeException.class,()->commands.reorder(u,2,List.of(b.item().id(),a.item().id())));
        assertInjected(failure,"injected reorder failure");
        tx.executeWithoutResult(s->{assertEquals(before,state(u));assertEquals(2,revision(u));assertEquals(2,counter(u));});
        doAnswer(invocation->{invocation.callRealMethod();em.flush();throw new IllegalStateException("injected replay failure");}).when(replays).save(any(),anyString(),anyString(),any(),any());
        assertInjected(assertThrows(RuntimeException.class,()->commands.create(u,"failure",input())),"injected replay failure");
        tx.executeWithoutResult(s->{assertEquals(before,state(u));assertEquals(2,revision(u));assertEquals(2,counter(u));assertEquals(0L,jdbc.queryForObject("select count(*) from link_create_idempotency where workspace_id=? and key='failure'",Long.class,workspace(u)));});
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{commands.delete(u,a.item().id(),1);em.flush();assertEquals(1,count(u));throw new IllegalStateException("delete rollback");}));
        tx.executeWithoutResult(s->{assertEquals(before,state(u));assertEquals(2,revision(u));assertEquals(2,counter(u));});
    }
    private void assertInjected(Throwable failure,String message) {
        while(failure.getCause()!=null) failure=failure.getCause();assertInstanceOf(IllegalStateException.class,failure);assertEquals(message,failure.getMessage());
    }
    @RepeatedTest(5)
    void concurrentCommandsConvergeOnValidRowsAndExactCounters() throws Exception {
        UUID u=owner();var same=race(()->commands.create(u,"same",input()),()->commands.create(u,"same",input()));
        assertEquals(same.getFirst(),same.getLast());assertInstanceOf(LinkMutation.class,same.getFirst());assertEquals(1,count(u));assertEquals(1,revision(u));
        var a=((LinkMutation)same.getFirst()).item();
        var conflict=race(()->commands.create(u,"different",input()),()->commands.create(u,"different",new CreateLinkCommand("Other",null,"https://example.com",null)));
        assertEquals(1,conflict.stream().filter(LinkMutation.class::isInstance).count());assertEquals(1,conflict.stream().filter(LinkConflictException.class::isInstance).count());
        var b=((LinkMutation)conflict.stream().filter(LinkMutation.class::isInstance).findFirst().orElseThrow()).item();
        var orders=race(()->commands.reorder(u,2,List.of(b.id(),a.id())),()->commands.reorder(u,2,List.of(a.id(),b.id())));
        assertEquals(1,orders.stream().filter(LinkCommandService.Ordered.class::isInstance).count());assertEquals(1,orders.stream().filter(LinkConflictException.class::isInstance).count());
        var winner=(LinkCommandService.Ordered)orders.stream().filter(LinkCommandService.Ordered.class::isInstance).findFirst().orElseThrow();
        assertEquals(winner.items().stream().map(LinkSnapshot::id).toList(),jdbc.queryForList("select id from links where workspace_id=? order by position",UUID.class,workspace(u)));
        long r=jdbc.queryForObject("select revision from links where id=?",Long.class,a.id());
        var edits=race(()->commands.update(u,a.id(),edit(r)),()->commands.update(u,a.id(),edit(r)));
        assertEquals(1,edits.stream().filter(LinkMutation.class::isInstance).count());assertEquals(1,edits.stream().filter(LinkConflictException.class::isInstance).count());
        var deletes=race(()->commands.delete(u,a.id(),r+1),()->commands.delete(u,a.id(),r+1));
        assertEquals(1,deletes.stream().filter(LinkCommandService.Deleted.class::isInstance).count());assertEquals(1,deletes.stream().filter(LinkNotFoundException.class::isInstance).count());
        assertEquals(1,count(u));assertEquals(5,revision(u));assertEquals(5,counter(u));
        assertEquals(2L,jdbc.queryForObject("select count(*) from link_create_idempotency where workspace_id=?",Long.class,workspace(u)));
        UUID quota=owner();doReturn(1).when(limits).getMaxLinks();var last=race(()->create(quota),()->create(quota));
        assertEquals(1,last.stream().filter(LinkMutation.class::isInstance).count());assertEquals(1,last.stream().filter(LinkQuotaException.class::isInstance).count());assertEquals(1,count(quota));assertEquals(1,revision(quota));assertEquals(1,counter(quota));
    }
    @Test
    void independentCreationsAndEditsBothCommitAndWorkspaceOverflowRollsBack() throws Exception {
        UUID u=owner();var created=race(()->create(u),()->create(u));
        for(Object result:created)assertInstanceOf(LinkMutation.class,result);
        var a=((LinkMutation)created.getFirst()).item();var b=((LinkMutation)created.getLast()).item();
        assertNotEquals(a.id(),b.id());assertEquals(Set.of(0L,1L),Set.of(a.position(),b.position()));
        assertEquals(2,count(u));assertEquals(2,counter(u));assertEquals(2,revision(u));
        assertEquals(1L,jdbc.queryForObject("select count(*) from link_collections where workspace_id=?",Long.class,workspace(u)));
        var edited=race(()->commands.update(u,a.id(),edit(1)),()->commands.update(u,b.id(),edit(1)));
        for(Object result:edited)assertEquals(2,assertInstanceOf(LinkMutation.class,result).item().revision());
        assertEquals(4,counter(u));assertEquals(4,revision(u));
        var before=state(u);jdbc.update("update workspaces set data_revision=? where owner_user_id=?",Long.MAX_VALUE,u);
        assertThrows(ArithmeticException.class,()->commands.delete(u,a.id(),2));
        tx.executeWithoutResult(s->{assertEquals(before,state(u));assertEquals(4,revision(u));assertEquals(Long.MAX_VALUE,counter(u));});
    }
    @Test
    void reorderAgainstEveryMutationChecksBothSerialOutcomes() throws Exception {
        for(String action:List.of("create","edit","delete")) for(boolean orderFirst:List.of(false,true)) {
            UUID u=owner();var a=create(u).item();var b=create(u).item();
            var acquired=new CountDownLatch(1);var release=new CountDownLatch(1);
            Callable<Object> mutation=()->switch(action){case "create"->create(u);case "edit"->commands.update(u,a.id(),edit(1));default->commands.delete(u,a.id(),1);};
            Callable<Object> order=()->commands.reorder(u,2,List.of(b.id(),a.id()));
            try(var pool=Executors.newFixedThreadPool(2)) {
                Future<Object> first=pool.submit(()->tx.execute(s->{jdbc.queryForObject("select id from workspaces where owner_user_id=? for update",UUID.class,u);acquired.countDown();await(release);return invoke(orderFirst?order:mutation);}));
                assertTrue(acquired.await(10,TimeUnit.SECONDS));
                Future<Object> second=pool.submit(()->invoke(orderFirst?mutation:order));release.countDown();
                assertFalse(first.get(20,TimeUnit.SECONDS) instanceof Throwable);
                Object result=second.get(20,TimeUnit.SECONDS);
                if(orderFirst && action.equals("create")) {assertInstanceOf(LinkMutation.class,result);assertEquals(4,counter(u));assertEquals(3,count(u));}
                else {assertInstanceOf(LinkConflictException.class,result);assertEquals(3,counter(u));assertEquals(!orderFirst&&action.equals("delete")?1:!orderFirst&&action.equals("create")?3:2,count(u));}
                assertEquals(counter(u),revision(u));
                assertEquals(count(u),jdbc.queryForObject("select count(distinct position) from links where workspace_id=?",Long.class,workspace(u)));
            } finally {release.countDown();}
        }
    }
    private static void await(CountDownLatch latch) {try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private static Object invoke(Callable<?> call) {try{return call.call();}catch(Exception ex){return ex;}}
    private List<Object> race(Callable<?> first,Callable<?> second) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ready.countDown();await(start);return invoke(first);});var b=pool.submit(()->{ready.countDown();await(start);return invoke(second);});
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
}
