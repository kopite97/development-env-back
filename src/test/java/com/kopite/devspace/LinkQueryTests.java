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
    @MockitoSpyBean LinkCollectionRepository collections;
    @MockitoSpyBean LinkLimits limits;
    @Autowired LinkQueryTests(LinkQueryService queries,LinkCommandService commands,UserWorkspaceCreationService users,EntityManagerFactory factory) {
        this.queries=queries;this.commands=commands;this.users=users;this.factory=factory;
    }
    private final LinkListFilter filter=new LinkListFilter("all","");
    private final CreateLinkCommand input=new CreateLinkCommand("Link",null,"https://example.com",null);
    @Test void collectionRevisionAndRowsShareSnapshotIncludingFirstCreation() throws Exception {
        for(boolean initiallyEmpty:new boolean[]{true,false}) {
            var owner=users.createOrReuse("link-query",UUID.randomUUID().toString(),"Owner");UUID user=owner.user().getId();
            if(!initiallyEmpty)commands.create(user,"first",input);
            var injected=new AtomicBoolean();
            try(var executor=Executors.newSingleThreadExecutor()) {
                doAnswer(call->{var result=call.callRealMethod();if(injected.compareAndSet(false,true))executor.submit(()->commands.create(user,"concurrent",input)).get(20,TimeUnit.SECONDS);return result;}).when(collections).find(owner.workspace().getId());
                var result=queries.list(user,filter);assertTrue(injected.get());
                assertEquals(initiallyEmpty?0:1,result.collectionRevision());assertEquals(initiallyEmpty?0:1,result.items().size());
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
            for(int i=0;i<5;i++)commands.create(user,"more"+i,input);
            stats.clear();var result=queries.list(user,filter);assertEquals(6,result.items().size());assertEquals(single,stats.getPrepareStatementCount());
            doReturn(1).when(limits).getMaxLinks();assertEquals(6,queries.list(user,filter).items().size());
            assertThrows(LinkQuotaException.class,()->commands.create(user,"over",input));
            commands.update(user,a.item().id(),new UpdateLinkCommand(1,null,null,null,null));
            commands.reorder(user,7,result.items().stream().map(LinkSnapshot::id).toList());commands.delete(user,a.item().id(),2);
            assertEquals(5,queries.list(user,filter).items().size());
        } finally {stats.setStatisticsEnabled(enabled);}
    }
}
