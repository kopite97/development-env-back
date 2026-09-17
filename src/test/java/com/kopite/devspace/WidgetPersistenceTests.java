package com.kopite.devspace;
import com.kopite.devspace.widget.domain.*;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class WidgetPersistenceTests {
    @Autowired UserWorkspaceCreationService users;
    @Autowired WidgetRepository widgets;
    @Autowired HomeLayoutRepository layouts;
    @Autowired PlatformTransactionManager manager;
    UUID workspace(){return users.createOrReuse("widgets",UUID.randomUUID().toString(),"Owner").workspace().getId();}
    @Test void jpaJsonLayoutReorderAndRollback() {
        UUID w=workspace(),other=workspace();var tx=new TransactionTemplate(manager);
        var a=Widget.create(w,"board","A",1,"{\"selection\":{\"kind\":\"all\"}}",Instant.now());
        var b=Widget.create(w,"links","B",1,"{\"selection\":{\"kind\":\"all\"}}",Instant.now());
        UUID pa=UUID.randomUUID(),pb=UUID.randomUUID();
        tx.executeWithoutResult(s->{widgets.insert(a);widgets.insert(b);layouts.insert(HomeLayout.create(w,Instant.now()));
            layouts.replacePlacements(w,List.of(WidgetPlacement.create(pa,w,a.getId(),0,"wide"),WidgetPlacement.create(pb,w,b.getId(),1,"small")));layouts.flush();});
        tx.executeWithoutResult(s->{assertTrue(widgets.owned(other,a.getId(),false).isEmpty());assertEquals(2,widgets.owned(w,Set.of(a.getId(),b.getId())).size());
            assertTrue(widgets.page(w,true,20,null,null).isEmpty());assertTrue(widgets.placed(w,a.getId()));
            assertTrue(widgets.owned(w,a.getId(),false).orElseThrow().getConfig().contains("selection"));
            layouts.replacePlacements(w,List.of(WidgetPlacement.create(pb,w,b.getId(),0,"wide"),WidgetPlacement.create(pa,w,a.getId(),1,"medium")));layouts.flush();});
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{layouts.replacePlacements(w,List.of());widgets.delete(widgets.owned(w,a.getId(),true).orElseThrow());widgets.flush();throw new IllegalStateException("rollback");}));
        tx.executeWithoutResult(s->{assertEquals(List.of(pb,pa),layouts.placements(w).stream().map(WidgetPlacement::getId).toList());assertTrue(widgets.owned(w,a.getId(),false).isPresent());});
    }
}
