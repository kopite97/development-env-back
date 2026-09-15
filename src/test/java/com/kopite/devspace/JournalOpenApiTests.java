package com.kopite.devspace;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
class JournalOpenApiTests {
    private final MockMvc mvc;
    private final UserWorkspaceCreationService users;
    private final JsonMapper json;
    @Autowired
    JournalOpenApiTests(MockMvc mvc,UserWorkspaceCreationService users,JsonMapper json) {
        this.mvc=mvc; this.users=users; this.json=json;
    }
    @Test
    void generatedContractContainsAllJournalOperationsAndStrictSchemas() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/api/v2/journals")).andExpect(status().isUnauthorized());
        String body=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var output=Path.of("build/reports/journal-api/openapi.json"); Files.createDirectories(output.getParent()); Files.writeString(output,body);
        var api=json.readTree(body); var paths=api.get("paths");
        Map<String,Set<String>> methods=Map.of("/api/v2/journals",Set.of("get","post"),"/api/v2/journals/{id}",Set.of("get","patch","delete"));
        for(var entry:methods.entrySet()) {
            assertEquals(entry.getValue(),paths.get(entry.getKey()).properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
            for(String method:entry.getValue()) {
                var operation=paths.get(entry.getKey()).get(method);
                String success=entry.getKey().equals("/api/v2/journals")&&method.equals("post")?"201":"200";
                assertTrue(operation.get("responses").has(success));
                assertTrue(operation.get("security").valueStream().anyMatch(s->s.has("sessionCookie")));
                for(String code:(method.equals("get") ? List.of("400","401","403","404","500") : List.of("400","401","403","404","409","500")))
                    assertEquals("#/components/schemas/ApiError",operation.get("responses").get(code).get("content").get("application/json").get("schema").get("$ref").asString());
                if(method.equals("get")) assertFalse(operation.get("responses").has("409"));
                if(!method.equals("get")) assertTrue(parameter(operation,"X-CSRF-Token").get("required").asBoolean());
            }
        }
        var create=paths.get("/api/v2/journals").get("post");
        assertFalse(create.get("responses").has("200"));
        assertTrue(parameter(create,"Idempotency-Key").get("required").asBoolean());
        assertEquals(128,parameter(create,"Idempotency-Key").get("schema").get("maxLength").asInt());
        var delete=paths.get("/api/v2/journals/{id}").get("delete");
        assertTrue(parameter(delete,"revision").get("required").asBoolean());
        assertEquals("query",parameter(delete,"revision").get("in").asString());
        var list=paths.get("/api/v2/journals").get("get");
        assertEquals("all",parameter(list,"projectStatus").get("schema").get("default").asString());
        assertEquals(20,parameter(list,"limit").get("schema").get("default").asInt());
        assertEquals(100,parameter(list,"limit").get("schema").get("maximum").asInt());
        assertEquals("newest",parameter(list,"sort").get("schema").get("default").asString());
        assertEquals(Set.of("newest","oldest"),strings(parameter(list,"sort").get("schema").get("enum")));
        assertEquals("",parameter(list,"query").get("schema").get("default").asString());
        for(String bound:List.of("from","to")) assertEquals("date",parameter(list,bound).get("schema").get("format").asString());
        assertEquals("#/components/schemas/DeleteJournalResponse",delete.get("responses").get("200").get("content").get("application/json").get("schema").get("$ref").asString());
        var schemas=api.get("components").get("schemas");
        var request=schemas.get("CreateJournalRequest");
        assertEquals(Set.of("title","projectId","body","entryDate"),strings(request.get("required")));
        assertEquals(Set.of("revision"),strings(schemas.get("UpdateJournalRequest").get("required")));
        for(String name:List.of("CreateJournalRequest","UpdateJournalRequest")) {
            var schema=schemas.get(name); assertFalse(schema.get("additionalProperties").asBoolean());
            var props=schema.get("properties");
            assertEquals(120,props.get("title").get("maxLength").asInt());
            assertEquals(20000,props.get("body").get("maxLength").asInt());
            assertEquals("uuid",props.get("projectId").get("format").asString());
            assertEquals("date",props.get("entryDate").get("format").asString());
            assertEquals(Set.of("title","projectId","body","entryDate"),schema.get("properties").properties().stream().map(Map.Entry::getKey).filter(k->!k.equals("revision")).collect(Collectors.toSet()));
            for(String forbidden:List.of("tags","workspaceId","projectName","categoryId","deletedAt","id")) assertFalse(props.has(forbidden));
        }
        assertEquals(Set.of("deletedId"),strings(schemas.get("DeleteJournalResponse").get("required")));
        var revision=schemas.get("UpdateJournalRequest").get("properties").get("revision");
        assertEquals(1,revision.get("minimum").asLong()); assertEquals(9007199254740991L,revision.get("maximum").asLong());
        var response=schemas.get("JournalResponse"); assertEquals(10,response.get("required").size());
        for(String field:List.of("id","createdAt","updatedAt","projectName","categoryId"))
            assertTrue(response.get("properties").get(field).get("readOnly").asBoolean());
        assertEquals("date",response.get("properties").get("entryDate").get("format").asString());
        for(String audit:List.of("createdAt","updatedAt")) assertEquals("date-time",response.get("properties").get(audit).get("format").asString());
        assertEquals("#/components/schemas/JournalResponse",schemas.get("JournalListResponse").get("properties").get("items").get("items").get("$ref").asString());
        assertEquals(Set.of("items","total","nextCursor"),strings(schemas.get("JournalListResponse").get("required")));
    }
    private JsonNode parameter(JsonNode operation,String name) {
        return operation.get("parameters").valueStream().filter(p->p.get("name").asString().equals(name)).findFirst().orElseThrow();
    }
    private Set<String> strings(JsonNode values) { return values.valueStream().map(JsonNode::asString).collect(Collectors.toSet()); }
}
