package com.kopite.devspace;
import com.kopite.devspace.milestone.application.command.CreateMilestoneCommand;
import com.kopite.devspace.milestone.application.command.MilestoneCommandService;
import com.kopite.devspace.milestone.application.port.MilestoneSearchRepository;
import com.kopite.devspace.milestone.application.query.MilestoneListFilter;
import com.kopite.devspace.milestone.application.query.MilestoneQueryService;
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
class MilestoneQueryTests {
    private final MilestoneQueryService queries;
    private final MilestoneCommandService commands;
    private final ProjectCommandService projects;
    private final UserWorkspaceCreationService users;
    private final EntityManagerFactory factory;
    @Autowired MilestoneQueryTests(MilestoneQueryService queries,MilestoneCommandService commands,ProjectCommandService projects,UserWorkspaceCreationService users,EntityManagerFactory factory) {
        this.queries=queries;this.commands=commands;this.projects=projects;this.users=users;this.factory=factory;
    }
    @MockitoSpyBean MilestoneSearchRepository search;
    private final MilestoneListFilter filter=new MilestoneListFilter("all",null,"all","open",20);
    @Test void countAndRowsShareSnapshotDespiteConcurrentCommit() throws Exception {
        var owner=users.createOrReuse("milestone-query",UUID.randomUUID().toString(),"Owner");
        UUID user=owner.user().getId();
        var project=projects.create(user,UUID.randomUUID().toString(),new CreateProjectCommand("Project",null,"Java",null,null,null));
        var input=new CreateMilestoneCommand("Milestone",project.id(),"2024-02-29",false,true);
        commands.create(user,UUID.randomUUID().toString(),input);
        var injected=new AtomicBoolean();
        try(var executor=Executors.newSingleThreadExecutor()) {
            doAnswer(call->{
                long count=(Long)call.callRealMethod();
                if(injected.compareAndSet(false,true)) executor.submit(()->commands.create(user,UUID.randomUUID().toString(),input)).get(20,TimeUnit.SECONDS);
                return count;
            }).when(search).count(eq(owner.workspace().getId()),any(MilestoneListFilter.class));
            var page=queries.list(user,filter,null);
            assertTrue(injected.get()); assertEquals(1,page.total()); assertEquals(1,page.items().size());
        }
        var fresh=queries.list(user,filter,null); assertEquals(2,fresh.total()); assertEquals(2,fresh.items().size());
    }
    @Test void projectingMoreProjectsDoesNotAddPerRowQueries() {
        var owner=users.createOrReuse("milestone-query",UUID.randomUUID().toString(),"Owner"); UUID user=owner.user().getId();
        var project=projects.create(user,UUID.randomUUID().toString(),new CreateProjectCommand("Project",null,"Java",null,null,null));
        commands.create(user,UUID.randomUUID().toString(),new CreateMilestoneCommand("Milestone",project.id(),"2024-02-29",false,true));
        var stats=factory.unwrap(SessionFactory.class).getStatistics(); boolean enabled=stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear(); assertEquals(1,queries.list(user,filter,null).items().size()); long single=stats.getPrepareStatementCount();
            for(int i=0;i<5;i++) {
                var target=projects.create(user,UUID.randomUUID().toString(),new CreateProjectCommand("Project"+i,null,"Java",null,null,null));
                commands.create(user,UUID.randomUUID().toString(),new CreateMilestoneCommand("Milestone",target.id(),"2024-02-29",false,true));
            }
            stats.clear(); assertEquals(6,queries.list(user,filter,null).items().size()); assertEquals(single,stats.getPrepareStatementCount());
        } finally {stats.setStatisticsEnabled(enabled);}
    }
}
