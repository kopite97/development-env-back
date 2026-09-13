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
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class LinkOpenApiTests {
    private final MockMvc mvc;private final JsonMapper json;
    @Autowired LinkOpenApiTests(MockMvc mvc,JsonMapper json){this.mvc=mvc;this.json=json;}
    @Test void allSixOperationsAndCollectionContractAreDocumented() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());mvc.perform(get("/api/v1/links")).andExpect(status().isUnauthorized());
        String body=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var output=Path.of("build/reports/link-api/openapi.json");Files.createDirectories(output.getParent());Files.writeString(output,body);
        var api=json.readTree(body);var paths=api.get("paths");var schemas=api.get("components").get("schemas");
        var methods=Map.of("/api/v1/links",Set.of("get","post"),"/api/v1/links/{id}",Set.of("get","patch","delete"),"/api/v1/links/order",Set.of("put"));
        assertEquals(3,paths.properties().stream().filter(p->p.getKey().startsWith("/api/v1/links")).count());
        for(var entry:methods.entrySet()) {
            assertEquals(entry.getValue(),keys(paths.get(entry.getKey())));
            for(String method:entry.getValue()) {
                var op=paths.get(entry.getKey()).get(method);String success=method.equals("post")?"201":"200";
                assertTrue(op.get("responses").has(success));assertTrue(op.get("security").valueStream().anyMatch(s->s.has("sessionCookie")));
                for(String code:List.of("400","401","403","404","409","500"))assertEquals("#/components/schemas/ApiError",op.get("responses").get(code).get("content").get("application/json").get("schema").get("$ref").asString());
                if(!method.equals("get")){var csrf=parameter(op,"X-CSRF-Token");assertTrue(csrf.get("required").asBoolean());assertEquals("header",csrf.get("in").asString());}
            }
        }
        var create=paths.get("/api/v1/links").get("post");assertFalse(create.get("responses").has("200"));assertTrue(create.get("responses").has("429"));
        assertTrue(parameter(create,"Idempotency-Key").get("required").asBoolean());assertEquals(128,parameter(create,"Idempotency-Key").get("schema").get("maxLength").asInt());
        var list=paths.get("/api/v1/links").get("get");assertEquals(Set.of("scope","query"),list.get("parameters").valueStream().map(p->p.get("name").asString()).collect(Collectors.toSet()));
        assertEquals("all",parameter(list,"scope").get("schema").get("default").asString());assertEquals(Set.of("all","unity","server"),strings(parameter(list,"scope").get("schema").get("enum")));
        var delete=paths.get("/api/v1/links/{id}").get("delete");assertTrue(parameter(delete,"revision").get("required").asBoolean());assertEquals("query",parameter(delete,"revision").get("in").asString());bounds(parameter(delete,"revision").get("schema"),1);
        assertFalse(delete.has("requestBody"));assertFalse(delete.get("parameters").valueStream().anyMatch(p->p.get("name").asString().equals("Idempotency-Key")));
        assertEquals("#/components/schemas/LinkResponse",paths.get("/api/v1/links/{id}").get("get").get("responses").get("200").get("content").get("application/json").get("schema").get("$ref").asString());
        for(String name:List.of("CreateLinkRequest","UpdateLinkRequest")) {
            var schema=schemas.get(name);var properties=schema.get("properties");var expected=new HashSet<>(Set.of("label","description","url","scope"));if(name.startsWith("Update"))expected.add("revision");
            assertEquals(expected,keys(properties));assertFalse(schema.get("additionalProperties").asBoolean());assertEquals(100,properties.get("label").get("maxLength").asInt());assertEquals(300,properties.get("description").get("maxLength").asInt());assertEquals(2000,properties.get("url").get("maxLength").asInt());assertEquals("uri",properties.get("url").get("format").asString());
        }
        assertEquals(Set.of("label","url"),strings(schemas.get("CreateLinkRequest").get("required")));assertEquals(Set.of("revision"),strings(schemas.get("UpdateLinkRequest").get("required")));
        assertEquals("all",schemas.get("CreateLinkRequest").get("properties").get("scope").get("default").asString());assertFalse(schemas.get("UpdateLinkRequest").get("properties").get("scope").has("default"));bounds(schemas.get("UpdateLinkRequest").get("properties").get("revision"),1);
        assertEquals("",schemas.get("CreateLinkRequest").get("properties").get("description").get("default").asString());
        assertFalse(schemas.get("UpdateLinkRequest").get("properties").get("description").has("default"));
        var order=schemas.get("ReorderLinksRequest");assertEquals(Set.of("collectionRevision","ids"),keys(order.get("properties")));assertEquals(Set.of("collectionRevision","ids"),strings(order.get("required")));bounds(order.get("properties").get("collectionRevision"),0);assertEquals("uuid",order.get("properties").get("ids").get("items").get("format").asString());
        var fields=Map.of("LinkResponse",Set.of("id","revision","createdAt","updatedAt","label","description","url","scope","position"),"LinkMutationResponse",Set.of("item","collectionRevision"),"DeleteLinkResponse",Set.of("deletedId","collectionRevision"),"LinkListResponse",Set.of("items","total","nextCursor","collectionRevision"));
        for(var entry:fields.entrySet()){var schema=schemas.get(entry.getKey());assertEquals(entry.getValue(),keys(schema.get("properties")));assertEquals(entry.getValue(),strings(schema.get("required")));}
        for(String field:List.of("id","position","createdAt","updatedAt"))assertTrue(schemas.get("LinkResponse").get("properties").get(field).get("readOnly").asBoolean());
        for(String audit:List.of("createdAt","updatedAt"))assertEquals("date-time",schemas.get("LinkResponse").get("properties").get(audit).get("format").asString());
        bounds(schemas.get("LinkResponse").get("properties").get("position"),0);
        var page=schemas.get("LinkListResponse");assertEquals(500,page.get("properties").get("items").get("maxItems").asInt());
        var type=page.get("properties").get("nextCursor").get("type");assertEquals(Set.of("null"),type.isArray()?strings(type):Set.of(type.asString()));
        assertTrue(page.get("description").asString().contains("8 MiB"));
    }
    private JsonNode parameter(JsonNode op,String name){return op.get("parameters").valueStream().filter(p->p.get("name").asString().equals(name)).findFirst().orElseThrow();}
    private Set<String> strings(JsonNode a){return a.valueStream().map(JsonNode::asString).collect(Collectors.toSet());}
    private Set<String> keys(JsonNode o){return o.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());}
    private void bounds(JsonNode s,long min){assertEquals(min,s.get("minimum").asLong());assertEquals(9007199254740991L,s.get("maximum").asLong());}
}
