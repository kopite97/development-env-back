package com.kopite.devspace;
import com.kopite.devspace.widget.application.*;
import com.kopite.devspace.widget.domain.WidgetException;
import com.kopite.devspace.dashboard.application.*;
import com.kopite.devspace.projectcategory.application.CategoryCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class WidgetConcurrencyTests extends WidgetTestSupport {
    @Autowired WidgetCommandService commands;@Autowired WidgetQueryService queries;
    @Autowired HomeLayoutCommandService layouts;@Autowired HomeLayoutQueryService layoutQueries;
    @Autowired CategoryCommandService categories;@Autowired PlatformTransactionManager manager;
    static final String CONFIG="{\"selection\":{\"kind\":\"all\"}}";
    List<Object> race(Supplier<?> a,Supplier<?> b)throws Exception {
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Object>> tasks=new ArrayList<>();for(var action:List.of(a,b))tasks.add(pool.submit(()->{assertTrue(start.await(10,TimeUnit.SECONDS));try{return action.get();}catch(WidgetException e){return e.code();}}));
            start.countDown();return List.of(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS));
        }
    }
    @Test void sameWidgetConflictsButIndependentWidgetsBothSucceed()throws Exception {
        var o=owner();UUID a=UUID.fromString(id(create(o,"board"))),b=UUID.fromString(id(create(o,"links")));
        var same=race(()->commands.replace(o.user(),a,1,"A",1,CONFIG),()->commands.replace(o.user(),a,1,"B",1,CONFIG));
        assertEquals(1,same.stream().filter(x->x.equals("REVISION_CONFLICT")).count());assertEquals(3,counter(o));
        var separate=race(()->commands.replace(o.user(),a,2,"A",1,CONFIG),()->commands.replace(o.user(),b,1,"B",1,CONFIG));
        assertTrue(separate.stream().allMatch(WidgetSnapshot.class::isInstance));assertEquals(5,counter(o));assertEquals(0,layoutQueries.get(o.user()).layoutRevision());
    }
    @Test void initializationRacesAndLayoutAttachDeleteCannotDangle()throws Exception {
        var o=owner();String hash=WidgetRequestHash.of(I,"{\"schemaVersion\":3,\"layoutRevision\":0}");
        var initial=race(()->layouts.initialize(o.user(),"same",hash),()->layouts.initialize(o.user(),"same",hash));
        assertTrue(initial.stream().allMatch(WidgetCreationResult.class::isInstance));assertEquals(1,counter(o));
        assertEquals(((WidgetCreationResult)initial.get(0)).body(),((WidgetCreationResult)initial.get(1)).body());
        assertEquals(1,initial.stream().map(WidgetCreationResult.class::cast).filter(r->r.dataRevision()==null).count());
        var empty=owner();var competing=race(()->layouts.initialize(empty.user(),"init",hash),()->layouts.replace(empty.user(),0,List.of()));
        assertEquals(1,competing.stream().filter(x->x.equals("REVISION_CONFLICT")).count());assertEquals(1,counter(empty));
        var attach=owner();UUID id=UUID.fromString(id(create(attach,"board")));
        var outcome=race(()->layouts.replace(attach.user(),0,List.of(new HomeLayoutCommandService.PlacementInput(id,"wide"))),()->commands.delete(attach.user(),id,1));
        assertEquals(1,outcome.stream().filter(String.class::isInstance).count());
        assertEquals(0,jdbc.queryForObject("select count(*) from dashboard_widget_placements p left join widgets w on w.id=p.widget_id and w.workspace_id=p.workspace_id where w.id is null",Integer.class));
    }
    @Test void replayRowsWidgetsPlacementsAndCountersRollbackTogether()throws Exception {
        var o=owner();var tx=new TransactionTemplate(manager);String hash=WidgetRequestHash.of(W,createBody("board"));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{commands.create(o.user(),"rollback","board","Example",1,CONFIG,hash);throw new IllegalStateException("after replay insert");}));
        assertEquals(0,counter(o));assertEquals(0,jdbc.queryForObject("select count(*) from widgets where workspace_id=?",Integer.class,o.workspace()));
        assertEquals(0,jdbc.queryForObject("select count(*) from widget_operation_replays where workspace_id=?",Integer.class,o.workspace()));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{layouts.initialize(o.user(),"init",WidgetRequestHash.of(I,"{\"schemaVersion\":3,\"layoutRevision\":0}"));throw new IllegalStateException("after initialization replay insert");}));
        assertFalse(layoutQueries.get(o.user()).initialized());assertEquals(0,counter(o));assertEquals(0,jdbc.queryForObject("select count(*) from widgets where workspace_id=?",Integer.class,o.workspace()));
    }
    @Test void categoryDeletionAndWidgetCreationFollowWorkspaceOrder()throws Exception {
        var o=owner();var c=categories.create(o.user(),"category","Category");String config="{\"selection\":{\"kind\":\"category\",\"categoryId\":\""+c.id()+"\"}}";
        var outcome=race(()->commands.create(o.user(),"widget","board","Board",1,config,"test-hash"),()->categories.delete(o.user(),c.id(),1));
        var created=outcome.stream().filter(WidgetCreationResult.class::isInstance).map(WidgetCreationResult.class::cast).findFirst();
        if(created.isPresent()){UUID id=UUID.fromString(WidgetJson.parse(created.get().body()).path("id").asString());assertEquals("missingCategory",queries.get(o.user(),id).referenceState());assertEquals(3,counter(o));}
        else {assertTrue(outcome.contains("RESOURCE_NOT_FOUND"));assertEquals(2,counter(o));}
    }
}
