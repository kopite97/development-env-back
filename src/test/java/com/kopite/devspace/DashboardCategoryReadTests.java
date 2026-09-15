package com.kopite.devspace;
import com.kopite.devspace.dashboard.application.*;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.projectcategory.application.CategoryCommandService;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class DashboardCategoryReadTests {
    @Autowired HomeDashboardCommandService commands; @Autowired HomeDashboardQueryService queries; @Autowired CategoryCommandService categories;
    @Autowired UserWorkspaceCreationService users; @Autowired JdbcTemplate jdbc; @Autowired EntityManagerFactory factory;
    @MockitoSpyBean HomeDashboardRepository dashboards;
    @Test void categoryDeletionDuringReadKeepsSelectionStateAndCounterInSameSnapshot()throws Exception {
        var o=users.createOrReuse("dashboard-category-read",UUID.randomUUID().toString(),"Owner");var u=o.user().getId();var w=o.workspace().getId();
        var category=categories.create(u,"c","C");var widget=new DashboardWidget("c","board","Category","wide",new DashboardSelection("category",null,category.id()),null);
        commands.save(u,0,List.of(widget));var once=new AtomicBoolean();
        try(var pool=Executors.newSingleThreadExecutor()) {
            doAnswer(call->{var result=call.callRealMethod();if(once.compareAndSet(false,true))pool.submit(()->categories.delete(u,category.id(),1)).get(20,TimeUnit.SECONDS);return result;}).when(dashboards).find(w);
            var old=queries.get(u);assertTrue(once.get());assertTrue(old.missingCategoryWidgetIds().isEmpty());assertEquals(2L,old.dataRevision());assertEquals(1,old.revision());
        }
        var current=queries.get(u);assertEquals(Set.of("c"),current.missingCategoryWidgetIds());assertEquals(3L,current.dataRevision());assertEquals(1,current.revision());assertEquals(widget,current.widgets().getFirst());
    }
    @Test void manyProjectAndCategoryReferencesUseConstantQueryCount() {
        var o=users.createOrReuse("dashboard-batch",UUID.randomUUID().toString(),"Owner");var u=o.user().getId();var w=o.workspace().getId();var widgets=new ArrayList<DashboardWidget>();
        append(w,widgets,0);commands.save(u,0,widgets);
        var statistics=factory.unwrap(SessionFactory.class).getStatistics();boolean enabled=statistics.isStatisticsEnabled();statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();assertEquals(2,queries.get(u).widgets().size());long initial=statistics.getPrepareStatementCount();
            for(int i=1;i<40;i++)append(w,widgets,i);commands.save(u,1,widgets);
            statistics.clear();var result=queries.get(u);assertEquals(80,result.widgets().size());assertTrue(result.missingCategoryWidgetIds().isEmpty());assertEquals(initial,statistics.getPrepareStatementCount());
        } finally {statistics.setStatisticsEnabled(enabled);}
    }
    void append(UUID workspace,List<DashboardWidget> widgets,int n) {
        UUID project=UUID.randomUUID(),category=UUID.randomUUID();jdbc.update("insert into projects(id,workspace_id,name,stack,created_at,updated_at) values(?,?,'P','Java',now(),now())",project,workspace);
        jdbc.update("insert into project_categories(id,workspace_id,name,created_at,updated_at) values(?,?,?,now(),now())",category,workspace,"C"+n);
        widgets.add(new DashboardWidget("p"+n,"board","P","wide",new DashboardSelection("project",project,null),null));
        widgets.add(new DashboardWidget("c"+n,"links","C","small",new DashboardSelection("category",null,category),null));
    }
}
