package com.kopite.devspace;
import com.kopite.devspace.widget.application.*;
import com.kopite.devspace.widget.domain.*;
import com.kopite.devspace.dashboard.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WidgetModelTests {
    @Test void strictConfigAndIndependentDomainRules() {
        var d=new LocalWidgetDefinition("board");String json="{\"selection\":{\"kind\":\"all\"}}";
        assertNull(((LocalWidgetConfig)d.decode(json)).limit());
        assertThrows(WidgetException.class,()->d.decode(json+" {}"));
        for(String bad:List.of("{\"selection\":null}","{\"selection\":{\"kind\":\"all\",\"projectId\":\""+UUID.randomUUID()+"\"}}","{\"selection\":{\"kind\":\"all\"},\"limit\":1.0}","{\"selection\":{\"kind\":\"all\"},\"limit\":2,\"limit\":3}"))
            assertThrows(WidgetException.class,()->d.decode(bad));
        assertThrows(WidgetException.class,()->new LocalWidgetDefinition("links").decode("{\"selection\":{\"kind\":\"all\"},\"limit\":1}"));
        var w=Widget.create(UUID.randomUUID(),"board","  Board  ",1,json,Instant.now());assertEquals("Board",w.getTitle());
        assertThrows(WidgetException.class,()->w.replace(2,"x",1,json,Instant.now()));w.replace(1,"Board",1,json,Instant.now());assertEquals(2,w.getRevision());
        var layout=HomeLayout.create(w.getWorkspaceId(),Instant.now());assertEquals(1,layout.getLayoutRevision());
        assertThrows(WidgetException.class,()->WidgetPlacement.create(UUID.randomUUID(),w.getWorkspaceId(),w.getId(),-1,"wide"));
    }
    @Test void registryRejectsDuplicatesAndAcceptsTestOnlyTypeWithoutDashboardChanges() {
        var d=new LocalWidgetDefinition("board");assertThrows(IllegalStateException.class,()->new WidgetTypeRegistry(List.of(d,d)));
        record ProbeConfig() implements WidgetConfig {}
        WidgetTypeDefinition probe=new WidgetTypeDefinition(){
            public String type(){return "test.probe";}public int configVersion(){return 1;}public Set<String> sizes(){return Set.of("small");}
            public String configSchema(){return "{\"type\":\"object\",\"additionalProperties\":false}";}
            public WidgetConfig decode(String s){WidgetJson.fields(WidgetJson.parse(s));return new ProbeConfig();}
        };
        var registry=new WidgetTypeRegistry(List.of(d,probe));assertInstanceOf(ProbeConfig.class,registry.get("test.probe",1).decode("{}"));
        assertThrows(WidgetException.class,()->registry.get("board",2));assertEquals(2,registry.all().size());
    }
}
