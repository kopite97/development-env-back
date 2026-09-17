package com.kopite.devspace;
import com.kopite.devspace.widget.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@Import(WidgetExtensionTests.ProbeConfiguration.class)
class WidgetExtensionTests extends WidgetTestSupport {
    record ProbeConfig() implements WidgetConfig {}
    record ProbePayload(String kind,int value) implements WidgetPayload {}
    @TestConfiguration static class ProbeConfiguration {
        @Bean WidgetTypeDefinition probeDefinition(){return new WidgetTypeDefinition(){public String type(){return "test.probe";}public int configVersion(){return 1;}public Set<String> sizes(){return Set.of("small");}public String configSchema(){return "{\"type\":\"object\",\"additionalProperties\":false}";}public WidgetConfig decode(String json){WidgetJson.fields(WidgetJson.parse(json));return new ProbeConfig();}};}
        @Bean WidgetDataHandler probeData(){return new WidgetDataHandler(){public String type(){return "test.probe";}public boolean paginated(){return false;}public Result read(UUID user,WidgetConfig config,String cursor){return new Result(new ProbePayload("test.probe",42),null,false,null);}};}
    }
    @Test void registeredTestOnlyTypeWorksWithoutDashboardModelOrStorageChanges()throws Exception {
        var o=owner();var w=tree(request(o,post(W).header("Idempotency-Key","probe"),"{\"type\":\"test.probe\",\"title\":\"Probe\",\"configVersion\":1,\"config\":{}}",201));
        request(o,put(D),layout(0,id(w)).replace("wide","small"),200);var data=tree(request(o,get(W+"/"+id(w)+"/data"),null,200));assertEquals(42,data.path("data").path("value").asInt());assertEquals("test.probe",data.path("data").path("kind").asString());
        assertEquals("test.probe",tree(request(o,get(D),null,200)).path("widgets").get(0).path("type").asString());
    }
}
