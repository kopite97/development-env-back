package com.kopite.devspace;
import com.kopite.devspace.link.application.LinkLimits;
import com.kopite.devspace.link.application.command.CreateLinkCommand;
import com.kopite.devspace.link.application.command.LinkCommandService;
import com.kopite.devspace.link.application.command.UpdateLinkCommand;
import com.kopite.devspace.link.application.exception.LinkQuotaException;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import com.kopite.devspace.link.application.query.LinkListFilter;
import com.kopite.devspace.link.application.query.LinkQueryService;
import com.kopite.devspace.link.domain.LinkCollectionRepository;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LinkQueryTests {
    private final LinkQueryService queries;
    private final LinkCommandService commands;
    private final UserWorkspaceCreationService users;
    private final EntityManagerFactory factory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.kopite.devspace.project.application.command.ProjectCommandService projects;
    @Autowired com.kopite.devspace.projectcategory.application.CategoryCommandService categories;
    @MockitoSpyBean LinkCollectionRepository collections;
    @MockitoSpyBean LinkLimits limits;
    @Autowired LinkQueryTests(LinkQueryService queries,LinkCommandService commands,UserWorkspaceCreationService users,EntityManagerFactory factory) {
        this.queries=queries;this.commands=commands;this.users=users;this.factory=factory;
    }
    private final LinkListFilter filter=new LinkListFilter("all","");
    private final CreateLinkCommand input=new CreateLinkCommand("Link",null,"https://example.com", com.kopite.devspace.link.application.command.LinkProjectSelection.omitted());
    @Test void collectionRevisionAndRowsShareSnapshotIncludingFirstCreation() throws Exception {
        for(boolean initiallyEmpty:new boolean[]{true,false}) {
            var owner=users.createOrReuse("link-query",UUID.randomUUID().toString(),"Owner");UUID user=owner.user().getId();
            if(!initiallyEmpty)commands.create(user,"first",input);
            var injected=new AtomicBoolean();
            try(var executor=Executors.newSingleThreadExecutor()) {
                doAnswer(call->{var result=call.callRealMethod();if(injected.compareAndSet(false,true))executor.submit(()->commands.create(user,"concurrent",input)).get(20,TimeUnit.SECONDS);return result;}).when(collections).find(owner.workspace().getId());
                var result=queries.list(user,filter);assertTrue(injected.get());
                assertEquals(initiallyEmpty?0L:1L,result.dataRevision());assertEquals(initiallyEmpty?0:1,result.collectionRevision());assertEquals(initiallyEmpty?0:1,result.items().size());
            }
            var fresh=queries.list(user,filter);assertEquals(initiallyEmpty?1:2,fresh.collectionRevision());assertEquals(initiallyEmpty?1:2,fresh.items().size());
        }
    }
    @Test void listQueryCountIsConstantAndLowerAdmissionDoesNotHideOrBlockExistingRows() {
        var owner=users.createOrReuse("link-query",UUID.randomUUID().toString(),"Owner");UUID user=owner.user().getId();
        var a=commands.create(user,"first",input);
        var stats=factory.unwrap(SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try {
            stats.clear();assertEquals(1,queries.list(user,filter).items().size());long single=stats.getPrepareStatementCount();
            for(int i=0;i<5;i++) {
                UUID p=UUID.randomUUID();jdbc.update("insert into projects(id,workspace_id,name,stack,created_at,updated_at) values(?,?,'P','Java',now(),now())",p,owner.workspace().getId());
                commands.create(user,"more"+i,new CreateLinkCommand("Link",null,"https://example.com",new com.kopite.devspace.link.application.command.LinkProjectSelection(true,p.toString())));
            }
            stats.clear();var result=queries.list(user,filter);assertEquals(6,result.items().size());assertEquals(single,stats.getPrepareStatementCount());
            doReturn(1).when(limits).getMaxLinks();assertEquals(6,queries.list(user,filter).items().size());
            assertThrows(LinkQuotaException.class,()->commands.create(user,"over",input));
            commands.update(user,a.item().id(),new UpdateLinkCommand(1,null,null,null, com.kopite.devspace.link.application.command.LinkProjectSelection.omitted()));
            commands.reorder(user,7,result.items().stream().map(LinkSnapshot::id).toList());commands.delete(user,a.item().id(),2);
            assertEquals(5,queries.list(user,filter).items().size());
        } finally {stats.setStatisticsEnabled(enabled);}
    }

    @Test void projectCategoryAndNameChangeCannotSplitLinkRowsAndFreshnessHeader()throws Exception {
        var owner=users.createOrReuse("link-derived-read",UUID.randomUUID().toString(),"Owner");var u=owner.user().getId();var w=owner.workspace().getId();
        var a=categories.create(u,"a","A");var b=categories.create(u,"b","B");
        var p=projects.create(u,"p",new com.kopite.devspace.project.application.command.CreateProjectCommand("Before",null,"Java",null,null,null,new com.kopite.devspace.project.application.command.ProjectCategorySelection(true,a.id().toString())));
        commands.create(u,"link",new CreateLinkCommand("Link",null,"https://example.com",new com.kopite.devspace.link.application.command.LinkProjectSelection(true,p.id().toString())));
        var once=new AtomicBoolean();
        try(var executor=Executors.newSingleThreadExecutor()) {
            doAnswer(call->{var result=call.callRealMethod();if(once.compareAndSet(false,true))executor.submit(()->projects.update(u,p.id(),new com.kopite.devspace.project.application.command.UpdateProjectCommand(1,"After",null,null,null,null,null,null,new com.kopite.devspace.project.application.command.ProjectCategorySelection(true,b.id().toString())))).get(20,TimeUnit.SECONDS);return result;}).when(collections).find(w);
            var result=queries.list(u,new LinkListFilter(a.id().toString(),null,"all",""));
            assertTrue(once.get());assertEquals(4L,result.dataRevision());assertEquals(1,result.collectionRevision());assertEquals(1,result.items().size());
            assertEquals("Before",result.items().getFirst().projectName());assertEquals(a.id(),result.items().getFirst().categoryId());
        }
        var current=queries.list(u,new LinkListFilter(b.id().toString(),null,"all",""));assertEquals(5L,current.dataRevision());assertEquals(1,current.collectionRevision());
        assertEquals("After",current.items().getFirst().projectName());assertEquals(1,current.items().getFirst().revision());
    }
}
