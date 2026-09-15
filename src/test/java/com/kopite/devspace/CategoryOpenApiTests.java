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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class) @AutoConfigureMockMvc
class CategoryOpenApiTests {
    @Autowired MockMvc mvc; @Autowired JsonMapper json;
    @Test void exactCategoryAndNormalProjectContractExcludesLegacySurface()throws Exception {
        String body=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var api=json.readTree(body);var paths=api.get("paths");String base="/api/v1/project-categories";
        assertEquals(Set.of("get","post"),keys(paths.get(base)));assertEquals(Set.of("get","patch","delete"),keys(paths.get(base+"/{id}")));
        for(String path:List.of(base,base+"/{id}")) for(var entry:paths.get(path).properties()) {
            var op=entry.getValue();assertTrue(op.get("security").valueStream().anyMatch(s->s.has("sessionCookie")));
            for(String code:List.of("400","401","403","404","500"))assertEquals("#/components/schemas/ApiError",op.get("responses").get(code).get("content").get("application/json").get("schema").get("$ref").asString());
            if(!entry.getKey().equals("get"))assertTrue(parameter(op,"X-CSRF-Token").get("required").asBoolean());
        }
        var post=paths.get(base).get("post");assertTrue(parameter(post,"Idempotency-Key").get("required").asBoolean());
        assertEquals(128,parameter(post,"Idempotency-Key").get("schema").get("maxLength").asInt());assertTrue(post.get("responses").has("429"));
        assertTrue(paths.get(base+"/{id}").get("delete").get("responses").get("409").get("description").asString().contains("CATEGORY_IN_USE"));
        assertEquals(9007199254740991L,parameter(paths.get(base+"/{id}").get("delete"),"revision").get("schema").get("maximum").asLong());
        var list=paths.get(base).get("get");assertFalse(list.has("parameters")&&!list.get("parameters").isEmpty());
        var s=api.get("components").get("schemas");
        assertEquals(Set.of("id","name","revision","createdAt","updatedAt"),keys(s.get("CategoryResponse").get("properties")));
        assertEquals(Set.of("items","total"),keys(s.get("CategoryListResponse").get("properties")));
        assertEquals(Set.of("deletedId"),keys(s.get("DeleteCategoryResponse").get("properties")));
        assertEquals(Set.of("name"),strings(s.get("CreateCategoryRequest").get("required")));
        assertEquals(Set.of("name","revision"),strings(s.get("RenameCategoryRequest").get("required")));
        for(String name:List.of("CreateCategoryRequest","RenameCategoryRequest")) {
            assertFalse(s.get(name).get("additionalProperties").asBoolean());assertEquals(100,s.get(name).get("properties").get("name").get("maxLength").asInt());
        }
        assertEquals(9007199254740991L,s.get("CategoryResponse").get("properties").get("revision").get("maximum").asLong());
        for(String name:List.of("CreateProjectRequest","UpdateProjectRequest","ProjectResponse")) {
            var fields=s.get(name).get("properties");assertTrue(fields.has("categoryId"));assertFalse(fields.has("categoryIdPresent"));assertFalse(fields.has("categoryName"));assertFalse(fields.has("legacyResponse"));
            assertEquals("uuid",fields.get("categoryId").get("format").asString());
        }
        assertEquals(Set.of("name","stack"),strings(s.get("CreateProjectRequest").get("required")));
        assertEquals("#/components/schemas/ProjectResponse", paths.get("/api/v2/projects").get("post").get("responses").get("201").get("content").get("application/json").get("schema").get("$ref").asString());
        assertFalse(s.has("LegacyProjectResponse"));
        assertEquals(Set.of("category","status","query","limit","cursor"),paths.get("/api/v2/projects").get("get").get("parameters").valueStream().map(p->p.get("name").asString()).collect(java.util.stream.Collectors.toSet()));
        for(String name:List.of("TaskResponse","JournalResponse","MilestoneResponse","LinkResponse"))assertTrue(s.get(name).get("properties").has("categoryId"),name);
        Path out=Path.of(".gradle/project-category-only-validation/openapi");Files.createDirectories(out);Files.writeString(out.resolve("openapi.json"),body);
    }
    JsonNode parameter(JsonNode op,String name){return op.get("parameters").valueStream().filter(p->p.get("name").asString().equals(name)).findFirst().orElseThrow();}
    Set<String> keys(JsonNode n){return n.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());}
    Set<String> strings(JsonNode n){return n.valueStream().map(JsonNode::asString).collect(java.util.stream.Collectors.toSet());}
}
