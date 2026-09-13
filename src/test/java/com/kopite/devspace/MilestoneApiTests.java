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
class MilestoneApiTests {
    private static final String BASE="/api/v1/milestones";
    private final MockMvc mvc;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    @Autowired
    MilestoneApiTests(MockMvc mvc,UserWorkspaceCreationService users,JdbcTemplate jdbc,JsonMapper json) {
        this.mvc=mvc; this.users=users; this.jdbc=jdbc; this.json=json;
    }
    private record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf) {}
    private Owner owner() throws Exception {
        var user=users.createOrReuse("milestone-api",UUID.randomUUID().toString(),"Owner");
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
    private String body(String project) { return "{\"title\":\" Milestone \",\"projectId\":\""+project+"\"}"; }
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
    void lifecycleNullableDatesCompletionArchivedProjectsAndPermanentDeletion() throws Exception {
        var owner=owner(); String project=project(owner);
        mvc.perform(mutation(patch("/api/v1/projects/"+project),owner).content("{\"revision\":1,\"status\":\"archived\"}")).andExpect(status().isOk());
        var item=create(owner,body(project)); String path=BASE+"/"+item.get("id").asString();
        assertEquals(10,item.size()); assertEquals("Milestone",item.get("title").asString()); assertTrue(item.get("dueDate").isNull()); assertFalse(item.get("completed").asBoolean());
        assertEquals(item,read(owner,path,Map.of())); assertEquals(item.get("createdAt"),item.get("updatedAt"));
        assertEquals(1,read(owner,BASE,Map.of()).get("total").asInt());
        assertEquals(0,read(owner,BASE,Map.of("projectStatus","active")).get("total").asInt());
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":1,\"completed\":true,\"dueDate\":\"2024-02-29\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.completed").value(true));
        assertEquals(0,read(owner,BASE,Map.of()).get("total").asInt()); assertEquals(1,read(owner,BASE,Map.of("status","done")).get("total").asInt());
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":2,\"completed\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dueDate").value("2024-02-29"));
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":3,\"dueDate\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4));
        assertTrue(read(owner,path,Map.of()).get("dueDate").isNull());
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":4}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(5));
        mvc.perform(mutation(patch("/api/v1/projects/"+project),owner).content("{\"revision\":2,\"name\":\"Renamed\",\"scope\":\"unity\",\"currentMilestone\":\"Independent memo\",\"progress\":33}"))
            .andExpect(status().isOk());
        var detail=read(owner,path,Map.of()); assertEquals("Renamed",detail.get("projectName").asString()); assertEquals("unity",detail.get("scope").asString());
        assertEquals(5,detail.get("revision").asInt()); assertEquals(item.get("createdAt"),detail.get("createdAt"));
        assertEquals(1,read(owner,BASE,Map.of("scope","unity","projectStatus","archived")).get("total").asInt());
        assertEquals(0,read(owner,BASE,Map.of("scope","server")).get("total").asInt());
        var sibling=create(owner,body(project));
        var projectBefore=read(owner,"/api/v1/projects/"+project,Map.of());
        long counter=jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,owner.workspace());
        mvc.perform(mutation(delete(path),owner).param("revision","4")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        var deleted=json.readTree(mvc.perform(mutation(delete(path),owner).param("revision","5")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
        assertEquals(1,deleted.size()); assertEquals(item.get("id"),deleted.get("deletedId"));
        assertEquals(counter+1,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,owner.workspace()));
        mvc.perform(get(path).session(owner.session())).andExpect(status().isNotFound());
        mvc.perform(mutation(patch(path),owner).content("{\"revision\":5}")).andExpect(status().isNotFound());
        mvc.perform(mutation(delete(path),owner).param("revision","5")).andExpect(status().isNotFound());
        assertEquals(counter+1,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,owner.workspace()));
        assertEquals(projectBefore,read(owner,"/api/v1/projects/"+project,Map.of()));
        assertEquals(sibling,read(owner,BASE+"/"+sibling.get("id").asString(),Map.of()));
        assertEquals(1,read(owner,BASE,Map.of("status","all")).get("total").asInt());
        mvc.perform(mutation(put(BASE+"/"+sibling.get("id").asString()),owner).content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(mutation(post(path+"/restore"),owner).content("{\"revision\":5}")).andExpect(status().isNotFound());
    }

    @Test
    void strictFieldsDatesAndPresenceAwareCreationReplay() throws Exception {
        var owner=owner(); String project=project(owner);
        for(String input:List.of("[]","{}","{\"title\":\"x\"}",body(project).replace("Milestone"," "),body(project).replace("Milestone","x".repeat(201)),body(project).replace(project,"1-1-1-1-1")))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(input)).andExpect(status().isBadRequest());
        for(String extra:List.of("\"status\":\"done\"","\"progress\":100","\"body\":\"no\"","\"position\":1","\"workspaceId\":\"no\"","\"userId\":\"no\"","\"ownerUserId\":\"no\"","\"id\":\"client\"","\"createdAt\":null","\"updatedAt\":null","\"scope\":\"unity\"","\"projectName\":\"fallback\"","\"revision\":1","\"dueDatePresent\":true","\"title\":\"duplicate\"","\"dueDate\":null,\"dueDate\":null"))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(body(project).replace("}",","+extra+"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for(String field:List.of("title","projectId","completed")) {
            var values=new LinkedHashMap<String,Object>();values.put("title","Title");values.put("projectId",project);values.put(field,null);
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(json.writeValueAsString(values))).andExpect(status().isBadRequest());
        }
        for(String wrong:List.of("\"true\"","1","[]","{}"))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(body(project).replace("}",",\"completed\":"+wrong+"}"))).andExpect(status().isBadRequest());
        for(String date:List.of("","2023-02-29","0000-01-01","10000-01-01","2024-2-29","2024-02-29T00:00:00Z"))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key","bad").content(body(project).replace("}",",\"dueDate\":\""+date+"\"}"))).andExpect(status().isBadRequest());
        var item=create(owner,body(project));String path=BASE+"/"+item.get("id").asString();
        for(String input:List.of("{}","{\"revision\":null}","{\"revision\":0}","{\"revision\":1.0}","{\"revision\":\"1\"}","{\"revision\":9007199254740992}","{\"revision\":1,\"completed\":null}","{\"revision\":1,\"dueDate\":null,\"dueDate\":\"2024-01-01\"}","{\"revision\":1,\"dueDate\":\"\"}","{\"revision\":1,\"title\":\" \"}"))
            mvc.perform(mutation(patch(path),owner).content(input)).andExpect(status().isBadRequest());
        mvc.perform(mutation(delete(path),owner)).andExpect(status().isBadRequest());
        for(String rev:List.of("0","-1","9007199254740992","1.0")) mvc.perform(mutation(delete(path),owner).param("revision",rev)).andExpect(status().isBadRequest());
        mvc.perform(mutation(post(BASE),owner).content(body(project))).andExpect(status().isBadRequest());
        for(var entry:Map.of("status","true","scope","bad","projectStatus","bad","limit","101","projectId","bad").entrySet())
            mvc.perform(get(BASE).session(owner.session()).param(entry.getKey(),entry.getValue())).andExpect(status().isBadRequest());
        String key=UUID.randomUUID().toString();
        String first=mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key",key).content(body(project))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key",key).content("{\"projectId\":\""+project+"\",\"title\":\" Milestone \"}"))
            .andExpect(status().isCreated()).andExpect(content().json(first));
        for(String extra:List.of("\"dueDate\":null","\"completed\":false"))
            mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key",key).content(body(project).replace("}",","+extra+"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        var explicit=create(owner,body(project).replace("}",",\"dueDate\":null,\"completed\":true}"));assertTrue(explicit.get("dueDate").isNull());assertTrue(explicit.get("completed").asBoolean());
    }

    @Test
    void nullableKeysetPaginationMatchesPostgresOrderAndBindsEveryFilter() throws Exception {
        var owner=owner(); String project=project(owner);
        for(boolean done:new boolean[]{false,true}) for(String date:new String[]{"2024-02-29","2024-02-29","2024-03-01",null,null,null})
            jdbc.update("insert into milestones(id,workspace_id,project_id,title,due_date,completed,created_at,updated_at) values(?,?,?,'Tied',?::date,?,now(),now())",UUID.randomUUID(),owner.workspace(),UUID.fromString(project),date,done);
        for(String state:List.of("open","done","all")) {
            var page=read(owner,BASE,Map.of("limit","2","status",state));List<String> seen=new ArrayList<>();int total=state.equals("all")?12:6;
            while(true) {
                assertEquals(total,page.get("total").asInt());
                for(var item:page.get("items")) {assertEquals(10,item.size());seen.add(item.get("id").asString());}
                if(page.get("nextCursor").isNull()) break;
                page=read(owner,BASE,Map.of("limit","2","status",state,"cursor",page.get("nextCursor").asString()));
            }
            assertEquals(total,new HashSet<>(seen).size());
            var expected=jdbc.queryForList("select id::text from milestones where workspace_id=?"+(state.equals("all")?"":" and completed="+state.equals("done"))+" order by completed asc,due_date asc nulls last,id asc",String.class,owner.workspace());
            assertEquals(expected,seen);
        }
        String cursor=read(owner,BASE,Map.of("limit","2","status","all")).get("nextCursor").asString();
        for(var entry:Map.of("scope","unity","projectId",project,"projectStatus","active","status","open","limit","3").entrySet()) {
            var params=new HashMap<>(Map.of("limit","2","status","all","cursor",cursor));params.put(entry.getKey(),entry.getValue());
            var request=get(BASE).session(owner.session());params.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
        for(String invalid:List.of(cursor+"x","","junk")) mvc.perform(get(BASE).session(owner.session()).param("status","all").param("limit","2").param("cursor",invalid)).andExpect(status().isBadRequest());
        var other=owner();mvc.perform(get(BASE).session(other.session()).param("status","all").param("limit","2").param("cursor",cursor)).andExpect(status().isBadRequest());
        for(String resource:List.of("projects","tasks","journals")) mvc.perform(get("/api/v1/"+resource).session(owner.session()).param("limit","2").param("cursor",cursor)).andExpect(status().isBadRequest());
        assertEquals(0,read(owner,BASE,Map.of("projectId",project,"scope","unity")).get("total").asInt());
        var output=java.nio.file.Path.of("build/reports/milestone-api/query-plan.txt");java.nio.file.Files.createDirectories(output.getParent());
        java.nio.file.Files.writeString(output,String.join("\n",jdbc.queryForList("explain select id from milestones where workspace_id=? order by completed asc,due_date asc nulls last,id asc limit 3",String.class,owner.workspace())));
    }
    @Test
    void securityIsolationAndReplayAfterPhysicalDeletion() throws Exception {
        var owner=owner(); var other=owner(); String project=project(owner);
        var milestone=create(owner,body(project)); String path=BASE+"/"+milestone.get("id").asString();
        for(var request:List.of(get(BASE),get(path),post(BASE),patch(path),delete(path)))
            mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        for(var request:List.of(post(BASE),patch(path),delete(path)))
            mvc.perform(request.session(owner.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        for(var request:List.of(post(BASE),patch(path),delete(path)))
            mvc.perform(mutation(request,owner).header("Origin","https://evil.example").content(body(project))).andExpect(status().isForbidden());
        mvc.perform(post(BASE).session(owner.session()).header("X-CSRF-Token","invalid").contentType("application/json").content(body(project))).andExpect(status().isForbidden());
        mvc.perform(delete(path).session(owner.session()).header("X-CSRF-Token","invalid").param("revision","1")).andExpect(status().isForbidden());
        for(String id:List.of(milestone.get("id").asString(),UUID.randomUUID().toString())) {
            mvc.perform(get(BASE+"/"+id).session(other.session())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            mvc.perform(mutation(patch(BASE+"/"+id),other).content("{\"revision\":99}")).andExpect(status().isNotFound());
            mvc.perform(mutation(delete(BASE+"/"+id),other).param("revision","99")).andExpect(status().isNotFound());
        }
        assertEquals(0,read(other,BASE,Map.of()).get("total").asInt());
        mvc.perform(get(BASE).session(other.session()).param("projectId",project).param("scope","unity")).andExpect(status().isNotFound());
        mvc.perform(mutation(post(BASE),other).header("Idempotency-Key","foreign").content(body(project))).andExpect(status().isNotFound());
        String key=UUID.randomUUID().toString();
        var original=mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key",key).content(body(project))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replayId=json.readTree(original).get("id").asString();
        mvc.perform(mutation(delete(BASE+"/"+replayId),owner).param("revision","1")).andExpect(status().isOk());
        Long before=jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,owner.workspace());
        mvc.perform(mutation(post(BASE),owner).header("Idempotency-Key",key).content(body(project))).andExpect(status().isCreated()).andExpect(content().json(original));
        assertEquals(before,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,owner.workspace()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from milestones where id=?",Long.class,UUID.fromString(replayId)));
        jdbc.update("update users set disabled_at=now() where id=?",other.user());
        mvc.perform(get(BASE).session(other.session())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

}
