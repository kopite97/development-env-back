package com.kopite.devspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class DashboardOverviewOpenApiTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Test void threeOperationsExactDtosConditionalWidgetsAndExamples() throws Exception {
        String body=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Path out=Path.of("build/reports/dashboard-overview-api/openapi.json");Files.createDirectories(out.getParent());Files.writeString(out,body);
        var api=json.readTree(body);var paths=api.get("paths");var schemas=api.get("components").get("schemas");
        assertEquals(Set.of("get"),keys(paths.get("/api/v2/overview")));
        assertEquals(Set.of("get","put"),keys(paths.get("/api/v2/dashboards/home")));
        for(var entry:Map.of("/api/v2/overview",List.of("get"),"/api/v2/dashboards/home",List.of("get","put")).entrySet())
            for(String method:entry.getValue()) {
                var op=paths.get(entry.getKey()).get(method);assertTrue(op.get("responses").has("200"));assertFalse(op.get("responses").has("201"));
                assertTrue(op.get("security").valueStream().anyMatch(s->s.has("sessionCookie")));
                for(String code:List.of("400","401","403","404","500"))assertEquals("#/components/schemas/ApiError",op.get("responses").get(code).get("content").get("application/json").get("schema").get("$ref").asString());
                if(op.has("parameters"))assertFalse(op.get("parameters").valueStream().anyMatch(p->p.get("name").asString().equals("Idempotency-Key")));
            }
        var save=paths.get("/api/v2/dashboards/home").get("put");assertTrue(save.get("responses").has("409"));
        assertEquals(Set.of("X-CSRF-Token"),save.get("parameters").valueStream().map(p->p.get("name").asString()).collect(Collectors.toSet()));
        assertTrue(save.get("parameters").get(0).get("required").asBoolean());
        assertEquals("header",save.get("parameters").get(0).get("in").asString());
        assertTrue(save.get("responses").get("400").get("description").asString().contains("UNSUPPORTED_SCHEMA_VERSION"));
        var overview=paths.get("/api/v2/overview").get("get");
        assertEquals(Set.of("category","projectId"),overview.get("parameters").valueStream().map(p->p.get("name").asString()).collect(Collectors.toSet()));
        var scope=overview.get("parameters").valueStream().filter(p->p.get("name").asString().equals("category")).findFirst().orElseThrow();
        assertEquals("all",scope.get("schema").get("default").asString());assertFalse(scope.get("schema").has("enum"));
        Map<String,Set<String>> fields=Map.of(
            "SaveHomeDashboardRequest",Set.of("schemaVersion","revision","widgets"),
            "HomeDashboardResponse",Set.of("id","schemaVersion","revision","widgets"),
            "OverviewResponse",Set.of("category","projectId","projects","tasks","asOf"),
            "OverviewProjectCounts",Set.of("total","archived","byCategory"),
            "OverviewProjectCategoryCounts",Set.of("categoryId","total","archived"),
            "OverviewTaskCounts",Set.of("todo","doing","done","total"));
        for(var entry:fields.entrySet()) {var s=schemas.get(entry.getKey());assertEquals(entry.getValue(),keys(s.get("properties")));assertEquals(entry.getValue(),strings(s.get("required")));assertFalse(s.get("additionalProperties").asBoolean());}
        for(String name:List.of("SaveHomeDashboardRequest","HomeDashboardResponse")) {
            var s=schemas.get(name);assertEquals(0,s.get("properties").get("revision").get("minimum").asLong());assertEquals(9007199254740991L,s.get("properties").get("revision").get("maximum").asLong());
            assertEquals(2,s.get("properties").get("schemaVersion").get("enum").get(0).asInt());assertFalse(s.get("properties").get("widgets").has("minItems"));assertFalse(s.get("properties").get("widgets").has("uniqueItems"));
        }
        var core=Set.of("id","type","title","selection","size");
        for(String suffix:List.of("Request","Response")) {
            var required=new HashSet<>(core);if(suffix.equals("Response"))required.add("selectionState");
            var widget=schemas.get("DashboardWidget"+suffix);
            assertEquals(required,strings(widget.get("required")));assertEquals(3,widget.get("oneOf").size());
            assertEquals(Set.of("#/components/schemas/DashboardDataWidget"+suffix,"#/components/schemas/DashboardLinksWidget"+suffix,"#/components/schemas/DashboardDeployWidget"+suffix),widget.get("oneOf").valueStream().map(v->v.get("$ref").asString()).collect(Collectors.toSet()));
            var data=schemas.get("DashboardDataWidget"+suffix);
            assertEquals(Set.of("overview","board","journal","milestone"),strings(data.get("properties").get("type").get("enum")));
            assertEquals(Set.of("small","medium","wide"),strings(data.get("properties").get("size").get("enum")));
            assertEquals(48,data.get("properties").get("title").get("maxLength").asInt());
            assertFalse(data.get("properties").get("limit").has("default"));assertEquals(1,data.get("properties").get("limit").get("minimum").asInt());assertEquals(20,data.get("properties").get("limit").get("maximum").asInt());
            for(String group:List.of("Data","Links","Deploy")) {
                var shape=schemas.get("Dashboard"+group+"Widget"+suffix);assertFalse(shape.get("additionalProperties").asBoolean());
                assertFalse(shape.get("properties").has("projectId"));assertFalse(shape.get("properties").has("scope"));
                if(!group.equals("Data"))assertEquals(required,keys(shape.get("properties")));
            }
            assertEquals("#/components/schemas/DashboardSelectionAll",schemas.get("DashboardDeployWidget"+suffix).get("properties").get("selection").get("$ref").asString());
        }
        assertEquals(4,schemas.get("DashboardSelectionDto").get("oneOf").size());
        for(String kind:List.of("All","Uncategorized","Project","Category")) {
            var selection=schemas.get("DashboardSelection"+kind);var required=new HashSet<>(Set.of("kind"));
            if(kind.equals("Project")||kind.equals("Category")){String key=kind.toLowerCase(Locale.ROOT)+"Id";required.add(key);assertEquals("uuid",selection.get("properties").get(key).get("format").asString());}
            assertEquals(required,keys(selection.get("properties")));assertEquals(required,strings(selection.get("required")));assertFalse(selection.get("additionalProperties").asBoolean());
        }
        var projectId=schemas.get("OverviewResponse").get("properties").get("projectId");
        assertTrue(strings(projectId.get("type")).contains("null"));assertEquals("date-time",schemas.get("OverviewResponse").get("properties").get("asOf").get("format").asString());
        var examples=paths.get("/api/v2/dashboards/home").get("get").get("responses").get("200").get("content").get("application/json").get("examples");
        assertEquals(0,examples.get("unsaved").get("value").get("revision").asLong());assertEquals(6,examples.get("unsaved").get("value").get("widgets").size());
        assertEquals(0,examples.get("savedEmpty").get("value").get("widgets").size());
        var workspaceExample=overview.get("responses").get("200").get("content").get("application/json").get("examples").get("workspace").get("value");
        assertTrue(workspaceExample.get("projectId").isNull());
    }
    Set<String> keys(JsonNode n){return n.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());}
    Set<String> strings(JsonNode n){if(n.isString())return Set.of(n.asString());return n.valueStream().map(JsonNode::asString).collect(Collectors.toSet());}
}
