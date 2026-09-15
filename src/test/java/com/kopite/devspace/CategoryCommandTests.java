package com.kopite.devspace;

import com.kopite.devspace.projectcategory.application.*;
import com.kopite.devspace.projectcategory.domain.*;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class CategoryCommandTests {
    @Autowired CategoryCommandService commands;
    @Autowired CategoryQueryService queries;
    @Autowired UserWorkspaceCreationService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.kopite.devspace.auth.application.CurrentUserService current;
    @Autowired com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository workspaces;
    @MockitoSpyBean ProjectCategoryRepository categories;
    @MockitoSpyBean CategoryCreateReplayStore replays;
    void assertReplay(CategorySnapshot expected,CategorySnapshot actual) {
        assertEquals(com.kopite.devspace.projectcategory.presentation.dto.CategoryResponse.from(expected),com.kopite.devspace.projectcategory.presentation.dto.CategoryResponse.from(actual));
        assertNull(actual.dataRevision(),"Replay is not a current workspace observation");
    }
    UUID owner(){return users.createOrReuse("category-command",UUID.randomUUID().toString(),"Owner").user().getId();}
    UUID workspace(UUID user){return jdbc.queryForObject("select id from workspaces where owner_user_id=?",UUID.class,user);}
    long counter(UUID user){return jdbc.queryForObject("select data_revision from workspaces where owner_user_id=?",Long.class,user);}
    void conflict(String code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(CategoryConflictException.class,action).getCode());}

    @Test void lifecycleOwnershipCountersReplayAndOverflow() {
        UUID u=owner(),other=owner(); var metadata=jdbc.queryForMap("select revision,updated_at from workspaces where id=?",workspace(u));
        assertTrue(queries.list(u).isEmpty());assertEquals(0,counter(u));
        var a=commands.create(u,"a"," Tools ");assertEquals("Tools",a.name());
        assertReplay(a,commands.create(u,"a"," Tools "));assertEquals(1,counter(u));
        conflict("IDEMPOTENCY_KEY_REUSED",()->commands.create(u,"a","Tools"));
        conflict("CATEGORY_NAME_CONFLICT",()->commands.create(u,"different","Tools"));
        assertThrows(CategoryNotFoundException.class,()->commands.rename(other,a.id(),99,"x"));
        conflict("REVISION_CONFLICT",()->commands.rename(u,a.id(),2,"x"));
        var b=commands.rename(u,a.id(),1,"Tools");assertEquals(2,b.revision());assertEquals(2,counter(u));
        assertReplay(a,commands.create(u,"a"," Tools "));
        jdbc.update("update project_categories set revision=9007199254740991 where id=?",a.id());
        conflict("REVISION_CONFLICT",()->commands.rename(u,a.id(),ProjectCategory.MAX_REVISION,"other"));
        commands.delete(u,a.id(),ProjectCategory.MAX_REVISION);assertEquals(3,counter(u));
        assertReplay(a,commands.create(u,"a"," Tools "));assertTrue(queries.list(u).isEmpty());
        assertThrows(CategoryNotFoundException.class,()->commands.delete(u,a.id(),1));
        var replacement=commands.create(u,"replacement","Tools");assertNotEquals(a.id(),replacement.id());
        assertEquals(metadata,jdbc.queryForMap("select revision,updated_at from workspaces where id=?",workspace(u)));
        jdbc.update("update workspaces set data_revision=? where id=?",Long.MAX_VALUE,workspace(u));
        conflict("REVISION_CONFLICT",()->commands.rename(u,replacement.id(),1,"new"));
        conflict("REVISION_CONFLICT",()->commands.delete(u,replacement.id(),1));
        assertEquals("Tools",queries.get(u,replacement.id()).name());
    }

    @Test void activeAndArchivedUsageBothRestrictAndRollbackAfterFlush() {
        UUID u=owner();var c=commands.create(u,"c","Category");
        for(String status:List.of("active","archived")) {
            UUID p=UUID.randomUUID();jdbc.update("insert into projects(id,workspace_id,name,scope,stack,status,category_id,created_at,updated_at) values(?,?,'Project','server','Java',?,?,now(),now())",p,workspace(u),status,c.id());
            conflict("CATEGORY_IN_USE",()->commands.delete(u,c.id(),1));assertEquals(1,counter(u));
            jdbc.update("delete from projects where id=?",p);
        }
        var tx=new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{commands.rename(u,c.id(),1,"Renamed");throw new IllegalStateException("after flush");}));
        assertEquals(c,queries.get(u,c.id()));assertEquals(1,counter(u));
        doThrow(new IllegalStateException("replay persistence failure")).when(replays).save(eq(workspace(u)),eq("fail"),anyString(),any(),any());
        var failure=assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->commands.create(u,"fail","Failure"));
        assertInstanceOf(IllegalStateException.class,failure.getCause());
        assertEquals("replay persistence failure",failure.getCause().getMessage());
        SnapshotAssertions.assertDataEquals(List.of(c),queries.list(u));assertEquals(1,counter(u));
    }

    @Test void fullCollectionQuotaOrderingAndExpiryBoundary() {
        UUID u=owner();var first=commands.create(u,"first","First");
        for(int i=1;i<100;i++) jdbc.update("insert into project_categories values(?,?,?,1,?,?)",UUID.randomUUID(),workspace(u),"C"+i,java.sql.Timestamp.from(first.createdAt()),java.sql.Timestamp.from(first.createdAt()));
        assertEquals(100,queries.list(u).size());assertThrows(CategoryQuotaException.class,()->commands.create(u,"overflow","Overflow"));
        assertReplay(first,commands.create(u,"first","First"));
        var expected=jdbc.queryForList("select id from project_categories where workspace_id=? order by created_at,id",UUID.class,workspace(u));
        assertEquals(expected,queries.list(u).stream().map(CategorySnapshot::id).toList());
        commands.rename(u,first.id(),1,"Renamed");assertEquals(expected,queries.list(u).stream().map(CategorySnapshot::id).toList());
        jdbc.update("insert into project_categories values(?,?,'Existing over cap',1,now(),now())",UUID.randomUUID(),workspace(u));assertEquals(101,queries.list(u).size());
        UUID v=owner();Instant start=Instant.parse("2026-09-14T00:00:00Z");var tx=new TransactionTemplate(manager);
        var a=tx.execute(s->fresh(start).create(v,"expiry","Name"));
        assertReplay(a,tx.execute(s->fresh(start.plusSeconds(86399)).create(v,"expiry","Name")));
        assertNotEquals(a.id(),tx.execute(s->fresh(start.plusSeconds(86400)).create(v,"expiry","Other")).id());assertEquals(2,counter(v));
    }
    CategoryCommandService fresh(Instant time){return new CategoryCommandService(current,workspaces,categories,replays,Clock.fixed(time,ZoneOffset.UTC));}

    @Test void listRetainsItsSnapshotAcrossConcurrentRename() throws Exception {
        UUID u=owner();var original=commands.create(u,"original","Before");
        try(var executor=Executors.newSingleThreadExecutor()) {
            doAnswer(call->{var rows=call.callRealMethod();executor.submit(()->commands.rename(u,original.id(),1,"After")).get(20,TimeUnit.SECONDS);return rows;}).when(categories).list(workspace(u));
            assertEquals(List.of(com.kopite.devspace.projectcategory.presentation.dto.CategoryResponse.from(original)),queries.list(u).stream().map(com.kopite.devspace.projectcategory.presentation.dto.CategoryResponse::from).toList());
        }
        reset(categories);assertEquals("After",queries.list(u).getFirst().name());assertEquals(2,counter(u));
    }

    @RepeatedTest(3) void concurrentCreatesSerializeKeysNamesAndQuota() throws Exception {
        UUID u=owner();var same=race(()->commands.create(u,"same","Name"),()->commands.create(u,"same","Name"));
        SnapshotAssertions.assertDataEquals(same.getFirst(),same.getLast());assertEquals(1,counter(u));
        UUID different=owner();var reused=race(()->commands.create(different,"key","A"),()->commands.create(different,"key","B"));
        assertEquals(1,reused.stream().filter(CategorySnapshot.class::isInstance).count());
        assertEquals(1,reused.stream().filter(x->x instanceof CategoryConflictException c&&c.getCode().equals("IDEMPOTENCY_KEY_REUSED")).count());assertEquals(1,counter(different));
        UUID v=owner();var duplicate=race(()->commands.create(v,"one","Name"),()->commands.create(v,"two","Name"));
        assertEquals(1,duplicate.stream().filter(CategorySnapshot.class::isInstance).count());
        assertEquals(1,duplicate.stream().filter(x->x instanceof CategoryConflictException c&&c.getCode().equals("CATEGORY_NAME_CONFLICT")).count());assertEquals(1,counter(v));
        UUID w=owner();for(int i=0;i<99;i++)jdbc.update("insert into project_categories values(?,?,?,1,now(),now())",UUID.randomUUID(),workspace(w),"N"+i);
        var quota=race(()->commands.create(w,"a","A"),()->commands.create(w,"b","B"));
        assertEquals(1,quota.stream().filter(CategorySnapshot.class::isInstance).count());assertEquals(1,quota.stream().filter(CategoryQuotaException.class::isInstance).count());assertEquals(100,queries.list(w).size());assertEquals(1,counter(w));
    }
    List<Object> race(Callable<?> a,Callable<?> b)throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=List.of(a,b).stream().map(action->pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("barrier");try{return action.call();}catch(CategoryConflictException|CategoryQuotaException ex){return ex;}})).toList();
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();return List.of(futures.getFirst().get(20,TimeUnit.SECONDS),futures.getLast().get(20,TimeUnit.SECONDS));
        } finally {start.countDown();}
    }
}
