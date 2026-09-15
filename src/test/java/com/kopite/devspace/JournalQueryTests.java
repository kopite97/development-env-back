package com.kopite.devspace;
import com.kopite.devspace.journal.application.command.CreateJournalCommand;
import com.kopite.devspace.journal.application.command.JournalCommandService;
import com.kopite.devspace.journal.application.port.JournalSearchRepository;
import com.kopite.devspace.journal.application.query.JournalListFilter;
import com.kopite.devspace.journal.application.query.JournalQueryService;
import com.kopite.devspace.project.application.command.CreateProjectCommand;
import com.kopite.devspace.project.application.command.ProjectCommandService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JournalQueryTests {
    @Autowired JournalQueryService queries;
    @Autowired JournalCommandService commands;
    @Autowired ProjectCommandService projects;
    @Autowired UserWorkspaceCreationService users;
    @Autowired EntityManagerFactory factory;
    @MockitoSpyBean JournalSearchRepository search;
    private final JournalListFilter filter=new JournalListFilter("all",null,"all","",null,null,"newest",20);
    @Test void countAndRowsShareSnapshotDespiteConcurrentCommit() throws Exception {
        var owner=users.createOrReuse("journal-query",UUID.randomUUID().toString(),"Owner");
        UUID user=owner.user().getId();
        var project=projects.create(user,UUID.randomUUID().toString(),new CreateProjectCommand("Project",null,"Java",null,null,null));
        var input=new CreateJournalCommand("Journal",project.id(),"body","2024-02-29");
        commands.create(user,UUID.randomUUID().toString(),input);
        var injected=new AtomicBoolean();
        try(var executor=Executors.newSingleThreadExecutor()) {
            doAnswer(call->{
                long count=(Long)call.callRealMethod();
                if(injected.compareAndSet(false,true)) executor.submit(()->commands.create(user,UUID.randomUUID().toString(),input)).get(20,TimeUnit.SECONDS);
                return count;
            }).when(search).count(eq(owner.workspace().getId()),any(JournalListFilter.class));
            var page=queries.list(user,filter,null);
            assertTrue(injected.get()); assertEquals(1,page.total()); assertEquals(1,page.items().size());
        }
        var fresh=queries.list(user,filter,null); assertEquals(2,fresh.total()); assertEquals(2,fresh.items().size());
    }
    @Test void projectingMoreProjectsDoesNotAddPerRowQueries() {
        var owner=users.createOrReuse("journal-query",UUID.randomUUID().toString(),"Owner"); UUID user=owner.user().getId();
        var project=projects.create(user,UUID.randomUUID().toString(),new CreateProjectCommand("Project",null,"Java",null,null,null));
        commands.create(user,UUID.randomUUID().toString(),new CreateJournalCommand("Journal",project.id(),"body","2024-02-29"));
        var stats=factory.unwrap(SessionFactory.class).getStatistics(); boolean enabled=stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear(); assertEquals(1,queries.list(user,filter,null).items().size()); long single=stats.getPrepareStatementCount();
            for(int i=0;i<5;i++) {
                var target=projects.create(user,UUID.randomUUID().toString(),new CreateProjectCommand("Project"+i,null,"Java",null,null,null));
                commands.create(user,UUID.randomUUID().toString(),new CreateJournalCommand("Journal",target.id(),"body","2024-02-29"));
            }
            stats.clear(); assertEquals(6,queries.list(user,filter,null).items().size()); assertEquals(single,stats.getPrepareStatementCount());
        } finally {stats.setStatisticsEnabled(enabled);}
    }
}
