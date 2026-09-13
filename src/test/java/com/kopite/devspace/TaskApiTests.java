package com.kopite.devspace;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class TaskApiTests {
    private static final String BASE="/api/v1/tasks";
    private final MockMvc mvc;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    @Autowired
    TaskApiTests(MockMvc mvc,UserWorkspaceCreationService users,JdbcTemplate jdbc,JsonMapper json) {
        this.mvc=mvc; this.users=users; this.jdbc=jdbc; this.json=json;
    }
    private record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf) {}
    private Owner owner() throws Exception {
        var user=users.createOrReuse("task-api",UUID.randomUUID().toString(),"Owner");
        var session=new MockHttpSession();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(user.user().getId(),"Owner"),null,List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        var token=json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("csrfToken").asString();
        return new Owner(user.user().getId(),user.workspace().getId(),session,token);
    }
    private MockHttpServletRequestBuilder mutation(MockHttpServletRequestBuilder request,Owner owner) {
        return request.session(owner.session()).contentType("application/json").header("X-CSRF-Token",owner.csrf());
    }
    private String project(Owner owner) throws Exception {
        return json.readTree(mvc.perform(mutation(post("/api/v1/projects"),owner).header("Idempotency-Key",UUID.randomUUID().toString())
            .content("{\"name\":\"Project\",\"scope\":\"server\",\"stack\":\"Java\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }
    private String body(String project) { return "{\"title\":\" Task \",\"projectId\":\""+project+"\"}"; }
    private JsonNode create(Owner owner,String body) throws Exception {
        return json.readTree(mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key",UUID.randomUUID().toString()).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode read(Owner owner,String path,Map<String,String> params) throws Exception {
        var request=get(path).session(owner.session()); params.forEach(request::param);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString());
    }

    @Test
    void fullLifecycleAndDerivedProjectData() throws Exception {
        var owner=owner(); String project=project(owner);
        var task=create(owner,body(project)); String path=BASE+"/"+task.get("id").asString();
        assertEquals("Task",task.get("title").asString()); assertEquals("",task.get("tag").asString());
        assertEquals("normal",task.get("priority").asString()); assertEquals("todo",task.get("status").asString());
        assertTrue(task.get("deletedAt").isNull()); assertFalse(task.has("workspaceId"));
        assertEquals(task,read(owner,path,Map.of()));
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":1,\"status\":\"done\",\"tag\":\" label \",\"description\":\" kept \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.tag").value("label"));
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(mutation(patch("/api/v1/projects/"+project),owner).content("{\"revision\":1,\"name\":\"Renamed%_!\",\"scope\":\"unity\",\"status\":\"archived\"}"))
            .andExpect(status().isOk());
        var detail=read(owner,path,Map.of());
        assertEquals("Renamed%_!",detail.get("projectName").asString()); assertEquals("unity",detail.get("scope").asString());
        assertEquals(2,detail.get("revision").asInt());
        assertEquals(1,read(owner,BASE,Map.of("query","%_!","scope","unity")).get("total").asInt());
        assertEquals(0,read(owner,BASE,Map.of("projectStatus","active")).get("total").asInt());
        assertEquals(1,read(owner,BASE+"/stats",Map.of()).get("counts").get("done").asInt());
        mvc.perform(mutation(delete(path),owner).param("revision","2")).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3));
        assertNotNull(read(owner,path,Map.of()).get("description"));
        assertEquals(0,read(owner,BASE,Map.of()).get("total").asInt());
        assertEquals(1,read(owner,BASE,Map.of("deleted","true")).get("total").asInt());
        assertEquals(0,read(owner,BASE+"/stats",Map.of()).get("total").asInt());
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":3}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_DELETED"));
        mvc.perform(mutation(delete(path),owner).param("revision","2"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(mutation(delete(path),owner).param("revision","3"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
        mvc.perform(mutation(post(path+"/restore"),owner).content("{\"revision\":3}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4)).andExpect(jsonPath("$.status").value("done"));
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":4,\"status\":\"todo\",\"tag\":\"\",\"description\":\"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tag").value(""));
        mvc.perform(mutation(post(path+"/restore"),owner).content("{\"revision\":5}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
    }

    @Test
    void strictWriteValidationAndQueryParameters() throws Exception {
        var owner=owner(); String project=project(owner);
        for(String body:List.of("{}","{\"title\":\"x\"}","{\"title\":null,\"projectId\":\""+project+"\"}",
            body(project).replace("Task"," "),body(project).replace(project,"1-1-1-1-1"),
            body(project).replace("Task","x".repeat(161))))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(body)).andExpect(status().isBadRequest());
        for(String extra:List.of("\"tags\":[]","\"priority\":\"높음\"","\"status\":\"DONE\"","\"scope\":\"server\"","\"projectName\":\"fallback\"",
            "\"workspaceId\":\"ignored\"","\"userId\":\"ignored\"","\"ownerUserId\":\"ignored\"","\"revision\":1","\"deletedAt\":null",
            "\"description\":null","\"tag\":42","\"title\":\"duplicate\"","\"id\":\"client\""))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(body(project).replace("}",","+extra+"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        var task=create(owner,body(project)); String path=BASE+"/"+task.get("id").asString();
        for(String value:List.of("{}","{\"revision\":null}","{\"revision\":0}","{\"revision\":1.0}","{\"revision\":\"1\"}","{\"revision\":9007199254740992}","{\"revision\":1,\"tag\":null}"))
            mvc.perform(mutation(patch(path),owner).content(value)).andExpect(status().isBadRequest());
        mvc.perform(mutation(delete(path),owner)).andExpect(status().isBadRequest());
        mvc.perform(mutation(post(path+"/restore"),owner).content("{\"revision\":1,\"status\":\"todo\"}")).andExpect(status().isBadRequest());
        for(var entry:Map.of("status","all","deleted","yes","scope","bad","projectStatus","bad","limit","101","projectId","bad").entrySet())
            mvc.perform(get(BASE).session(owner.session()).param(entry.getKey(),entry.getValue())).andExpect(status().isBadRequest());
        for(String forbidden:List.of("status","deleted"))
            mvc.perform(get(BASE+"/stats").session(owner.session()).param(forbidden,"false")).andExpect(status().isBadRequest());
    }

    @Test
    void securityBoundaryAndTwoUserIsolation() throws Exception {
        var owner=owner(); var other=owner(); String project=project(owner);
        var task=create(owner,body(project)); String path=BASE+"/"+task.get("id").asString();
        for(var request:List.of(post(BASE),patch(path),delete(path),post(path+"/restore")))
            mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        for(var request:List.of(post(BASE),patch(path),delete(path),post(path+"/restore")))
            mvc.perform(request.session(owner.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(mutation(post(BASE),owner).header("Origin","https://evil.example").content(body(project))).andExpect(status().isForbidden());
        mvc.perform(post(BASE).session(owner.session()).header("X-CSRF-Token","invalid").contentType("application/json").content(body(project))).andExpect(status().isForbidden());
        for(String id:List.of(task.get("id").asString(),UUID.randomUUID().toString())) {
            mvc.perform(get(BASE+"/"+id).session(other.session())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            mvc.perform(mutation(patch(BASE+"/"+id),other).content("{\"revision\":99}")).andExpect(status().isNotFound());
            mvc.perform(mutation(delete(BASE+"/"+id),other).param("revision","99")).andExpect(status().isNotFound());
            mvc.perform(mutation(post(BASE+"/"+id+"/restore"),other).content("{\"revision\":99}")).andExpect(status().isNotFound());
        }
        assertEquals(0,read(other,BASE,Map.of("query","Task")).get("total").asInt());
        for(String endpoint:List.of(BASE,BASE+"/stats"))
            mvc.perform(get(endpoint).session(other.session()).param("projectId",project).param("scope","unity")).andExpect(status().isNotFound());
        mvc.perform(mutation(post(BASE),other).header("Idempotency-Key","foreign").content(body(project))).andExpect(status().isNotFound());
        jdbc.update("update users set disabled_at=now() where id=?",other.user());
        mvc.perform(get(BASE).session(other.session())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void tiedPaginationCursorBindingAndStats() throws Exception {
        var owner=owner(); String project=project(owner);
        for(int i=0;i<5;i++) jdbc.update("insert into tasks(id,workspace_id,project_id,title,status,created_at,updated_at) values(?,?,?,'Tied',?,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')",
            UUID.randomUUID(),owner.workspace(),UUID.fromString(project),i<3?"todo":"doing");
        var first=read(owner,BASE,Map.of("limit","2")); assertEquals(5,first.get("total").asInt());
        String cursor=first.get("nextCursor").asString(); Set<String> seen=new HashSet<>();
        var page=first;
        while(true) {
            for(var item:page.get("items")) assertTrue(seen.add(item.get("id").asString()));
            if(page.get("nextCursor").isNull()) break;
            page=read(owner,BASE,Map.of("limit","2","cursor",page.get("nextCursor").asString()));
        }
        assertEquals(5,seen.size());
        for(var entry:Map.of("scope","unity","projectId",project,"projectStatus","active","status","todo","deleted","true","query","Tied","limit","3").entrySet()) {
            var request=get(BASE).session(owner.session()).param("limit","2").param("cursor",cursor);
            if(entry.getKey().equals("limit")) request=get(BASE).session(owner.session()).param("cursor",cursor);
            mvc.perform(request.param(entry.getKey(),entry.getValue())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
        mvc.perform(get(BASE).session(owner.session()).param("limit","2").param("cursor",cursor+"x")).andExpect(status().isBadRequest());
        var other=owner();
        mvc.perform(get(BASE).session(other.session()).param("limit","2").param("cursor",cursor)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects").session(owner.session()).param("limit","2").param("cursor",cursor)).andExpect(status().isBadRequest());
        assertEquals(0,read(owner,BASE,Map.of("projectId",project,"scope","unity")).get("total").asInt());
        var stats=read(owner,BASE+"/stats",Map.of());
        assertEquals(5,stats.get("total").asInt()); assertEquals(3,stats.get("counts").get("todo").asInt());
        assertEquals(2,stats.get("counts").get("doing").asInt()); assertEquals(0,stats.get("counts").get("done").asInt());
        assertNotNull(stats.get("asOf"));
    }
}
