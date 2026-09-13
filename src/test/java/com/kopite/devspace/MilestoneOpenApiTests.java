package com.kopite.devspace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class MilestoneOpenApiTests {
    private final MockMvc mvc;
    private final JsonMapper json;
    @Autowired
    MilestoneOpenApiTests(MockMvc mvc,JsonMapper json) {
        this.mvc=mvc; this.json=json;
    }
    @Test
    void generatedContractContainsAllMilestoneOperationsAndStrictSchemas() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/milestones")).andExpect(status().isUnauthorized());
        String body=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var output=Path.of("build/reports/milestone-api/openapi.json"); Files.createDirectories(output.getParent()); Files.writeString(output,body);
        var api=json.readTree(body); var paths=api.get("paths");
        Map<String,Set<String>> methods=Map.of("/api/v1/milestones",Set.of("get","post"),"/api/v1/milestones/{id}",Set.of("get","patch","delete"));
        for(var entry:methods.entrySet()) {
            assertEquals(entry.getValue(),paths.get(entry.getKey()).properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
            for(String method:entry.getValue()) {
                var operation=paths.get(entry.getKey()).get(method);
                String success=entry.getKey().equals("/api/v1/milestones")&&method.equals("post")?"201":"200";
                assertTrue(operation.get("responses").has(success));
                assertTrue(operation.get("security").valueStream().anyMatch(s->s.has("sessionCookie")));
                for(String code:(method.equals("get") ? List.of("400","401","403","404","500") : List.of("400","401","403","404","409","500")))
                    assertEquals("#/components/schemas/ApiError",operation.get("responses").get(code).get("content").get("application/json").get("schema").get("$ref").asString());
                if(method.equals("get")) assertFalse(operation.get("responses").has("409"));
                if(!method.equals("get")) assertTrue(parameter(operation,"X-CSRF-Token").get("required").asBoolean());
            }
        }
        var create=paths.get("/api/v1/milestones").get("post");
        assertFalse(create.get("responses").has("200"));
        assertTrue(parameter(create,"Idempotency-Key").get("required").asBoolean());
        assertEquals(128,parameter(create,"Idempotency-Key").get("schema").get("maxLength").asInt());
        var delete=paths.get("/api/v1/milestones/{id}").get("delete");
        assertTrue(parameter(delete,"revision").get("required").asBoolean());
        assertEquals("query",parameter(delete,"revision").get("in").asString());
        var list=paths.get("/api/v1/milestones").get("get");
        assertEquals("all",parameter(list,"projectStatus").get("schema").get("default").asString());
        assertEquals(20,parameter(list,"limit").get("schema").get("default").asInt());
        assertEquals(100,parameter(list,"limit").get("schema").get("maximum").asInt());
        assertEquals("open",parameter(list,"status").get("schema").get("default").asString());
        assertEquals(Set.of("open","done","all"),strings(parameter(list,"status").get("schema").get("enum")));
        assertEquals(Set.of("scope","projectId","projectStatus","status","limit","cursor"),list.get("parameters").valueStream().map(p->p.get("name").asString()).collect(Collectors.toSet()));
        assertEquals(1,parameter(delete,"revision").get("schema").get("minimum").asLong());
        assertEquals(9007199254740991L,parameter(delete,"revision").get("schema").get("maximum").asLong());
        assertFalse(delete.has("requestBody"));
        assertFalse(delete.get("parameters").valueStream().anyMatch(p->p.get("name").asString().equals("Idempotency-Key")));
        assertEquals("#/components/schemas/DeleteMilestoneResponse",delete.get("responses").get("200").get("content").get("application/json").get("schema").get("$ref").asString());
        var schemas=api.get("components").get("schemas");
        var request=schemas.get("CreateMilestoneRequest");
        assertEquals(Set.of("title","projectId"),strings(request.get("required")));
        assertEquals(Set.of("revision"),strings(schemas.get("UpdateMilestoneRequest").get("required")));
        for(String name:List.of("CreateMilestoneRequest","UpdateMilestoneRequest")) {
            var schema=schemas.get(name); assertFalse(schema.get("additionalProperties").asBoolean());
            var props=schema.get("properties");
            assertEquals(200,props.get("title").get("maxLength").asInt());
            assertEquals("uuid",props.get("projectId").get("format").asString());
            assertEquals("date",props.get("dueDate").get("format").asString());
            assertEquals(Set.of("title","projectId","dueDate","completed"),schema.get("properties").properties().stream().map(Map.Entry::getKey).filter(k->!k.equals("revision")).collect(Collectors.toSet()));
            assertEquals(Set.of("string","null"),strings(props.get("dueDate").get("type")));
            assertEquals("boolean",props.get("completed").get("type").asString());
            for(String forbidden:List.of("status","progress","dueDatePresent","tags","workspaceId","projectName","scope","deletedAt","id")) assertFalse(props.has(forbidden));
        }
        assertEquals(Set.of("deletedId"),strings(schemas.get("DeleteMilestoneResponse").get("required")));
        var revision=schemas.get("UpdateMilestoneRequest").get("properties").get("revision");
        assertEquals(1,revision.get("minimum").asLong()); assertEquals(9007199254740991L,revision.get("maximum").asLong());
        assertFalse(request.get("properties").get("completed").get("default").asBoolean());
        assertFalse(schemas.get("UpdateMilestoneRequest").get("properties").get("completed").has("default"));
        var response=schemas.get("MilestoneResponse"); assertEquals(10,response.get("required").size());
        for(String field:List.of("id","createdAt","updatedAt","projectName","scope"))
            assertTrue(response.get("properties").get(field).get("readOnly").asBoolean());
        assertEquals("date",response.get("properties").get("dueDate").get("format").asString());
        assertEquals(Set.of("string","null"),strings(response.get("properties").get("dueDate").get("type")));
        assertEquals(Set.of("id","revision","createdAt","updatedAt","projectId","projectName","scope","title","dueDate","completed"),strings(response.get("required")));
        assertEquals(Set.of("deletedId"),schemas.get("DeleteMilestoneResponse").get("properties").properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
        assertEquals("uuid",schemas.get("DeleteMilestoneResponse").get("properties").get("deletedId").get("format").asString());
        assertEquals(2,paths.properties().stream().filter(p->p.getKey().startsWith("/api/v1/milestones")).count());
        for(String audit:List.of("createdAt","updatedAt")) assertEquals("date-time",response.get("properties").get(audit).get("format").asString());
        assertEquals("#/components/schemas/MilestoneResponse",schemas.get("MilestoneListResponse").get("properties").get("items").get("items").get("$ref").asString());
        assertEquals(Set.of("items","total","nextCursor"),strings(schemas.get("MilestoneListResponse").get("required")));
    }
    private JsonNode parameter(JsonNode operation,String name) {
        return operation.get("parameters").valueStream().filter(p->p.get("name").asString().equals(name)).findFirst().orElseThrow();
    }
    private Set<String> strings(JsonNode values) { return values.valueStream().map(JsonNode::asString).collect(Collectors.toSet()); }
}
