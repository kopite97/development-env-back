package com.kopite.devspace;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.*;
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
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class DashboardOverviewApiTests {
    static final String HOME="/api/v1/dashboards/home",OVERVIEW="/api/v1/overview";
    @Autowired MockMvc mvc;
    @Autowired UserWorkspaceCreationService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf) {}
    Owner owner() throws Exception {
        var u=users.createOrReuse("dashboard-api",UUID.randomUUID().toString(),"Owner");
        var session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(u.user().getId(),"Owner"),null,List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        var token=json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("csrfToken").asString();
        return new Owner(u.user().getId(),u.workspace().getId(),session,token);
    }
    MockHttpServletRequestBuilder putHome(Owner o,String body){return put(HOME).session(o.session()).contentType("application/json").header("X-CSRF-Token",o.csrf()).content(body);}
    JsonNode response(MockHttpServletRequestBuilder req) throws Exception {return json.readTree(mvc.perform(req).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());}
    JsonNode read(Owner o,String path) throws Exception{return response(get(path).session(o.session()));}
    String body(long revision,Object widgets){return json.writeValueAsString(Map.of("schemaVersion",1,"revision",revision,"widgets",widgets));}
    Map<String,Object> widget(String type){return new LinkedHashMap<>(Map.of("id","widget","type",type,"title"," Title ","scope","unity","size","wide"));}
    Set<String> keys(JsonNode n){return n.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());}
    UUID project(Owner o,String scope,boolean archived) {
        UUID p=UUID.randomUUID();jdbc.update("insert into projects(id,workspace_id,name,scope,stack,status,created_at,updated_at) values(?,?,'Project',?,'Java',?,now(),now())",p,o.workspace(),scope,archived?"archived":"active");return p;
    }
    @Test void virtualDefaultSavedEmptyNormalizedResponseAndStrictFields() throws Exception {
        var o=owner();var d=read(o,HOME);assertEquals(Set.of("id","schemaVersion","revision","widgets"),keys(d));assertEquals("home",d.get("id").asString());assertEquals(1,d.get("schemaVersion").asInt());assertEquals(0,d.get("revision").asInt());assertEquals(6,d.get("widgets").size());
        assertEquals("home-overview",d.get("widgets").get(0).get("id").asString());assertFalse(d.get("widgets").get(0).has("limit"));assertFalse(d.get("widgets").get(0).has("projectId"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from dashboards where workspace_id=?",Long.class,o.workspace()));
        assertEquals(1,response(putHome(o,body(0,List.of()))).get("revision").asLong());assertEquals(0,read(o,HOME).get("widgets").size());
        UUID archived=project(o,"server",true);var w=widget("overview");w.put("id"," widget ");w.put("projectId",archived.toString());w.put("limit",20);
        var saved=response(putHome(o,body(1,List.of(w))));var item=saved.get("widgets").get(0);
        assertEquals(Set.of("id","type","title","scope","size","projectId","limit"),keys(item));assertEquals("widget",item.get("id").asString());assertEquals("Title",item.get("title").asString());assertEquals("all",item.get("scope").asString());assertEquals(20,item.get("limit").asInt());
        assertEquals(saved,read(o,HOME));
        mvc.perform(putHome(o,body(1,List.of()))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        assertEquals(2L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace()));
    }
    @Test void allWidgetCombinationsAndOmittedDefaults() throws Exception {
        var o=owner();long revision=0;UUID p=project(o,"unity",false);
        for(String type:List.of("overview","board","deploy","links","journal","milestone"))
            for(String size:List.of("small","medium","wide"))
                for(String scope:List.of("all","unity","server")) {
                    var w=widget(type);w.put("size",size);w.put("scope",scope);
                    var result=response(putHome(o,body(revision++,List.of(w))));var item=result.get("widgets").get(0);
                    assertEquals(scope,item.get("scope").asString());assertEquals(size,item.get("size").asString());assertFalse(item.has("limit"));assertFalse(item.has("projectId"));
                }
        for(String type:List.of("overview","board","journal","milestone")) {
            var w=widget(type);w.put("projectId",p.toString());w.put("limit",1);
            assertEquals("all",response(putHome(o,body(revision++,List.of(w)))).get("widgets").get(0).get("scope").asString());
        }
        var a=widget("board");var b=widget("board");b.put("id","second");
        var result=response(putHome(o,body(revision,List.of(b,a))));assertEquals("second",result.get("widgets").get(0).get("id").asString());
    }
    @Test void malformedAndConditionalWritesNeverCreateState() throws Exception {
        var o=owner();var good=widget("board");var bad=new ArrayList<String>();
        bad.addAll(List.of("[]","null","{}","{","{\"schemaVersion\":1,\"revision\":0}","{\"schemaVersion\":1,\"revision\":0,\"widgets\":null}","{\"schemaVersion\":1,\"revision\":0,\"widgets\":[null]}","{\"schemaVersion\":1,\"revision\":0,\"revision\":0,\"widgets\":[]}"));
        for(String r:List.of("-1","9007199254740992","999999999999999999999999","0.0","\"0\"","null"))
            bad.add("{\"schemaVersion\":1,\"revision\":"+r+",\"widgets\":[]}");
        for(String version:List.of("null","\"1\"","1.0","true"))
            bad.add("{\"schemaVersion\":"+version+",\"revision\":0,\"widgets\":[]}");
        for(String extra:List.of("id","workspaceId","ownerUserId","userId","createdAt","position"))
            bad.add("{\"schemaVersion\":1,\"revision\":0,\"widgets\":[],\""+extra+"\":\"x\"}");
        for(String field:List.of("id","type","title","scope","size","projectId","limit")) {
            var w=widget("board");w.put(field,null);bad.add(body(0,List.of(w)));
            if(!Set.of("projectId","limit").contains(field)){w=widget("board");w.remove(field);bad.add(body(0,List.of(w)));}
        }
        for(Object n:List.of(0,21,-1,1.5,"1",true)) {var w=widget("board");w.put("limit",n);bad.add(body(0,List.of(w)));}
        for(String type:List.of("deploy","links"))for(String field:List.of("projectId","limit")) {
            var w=widget(type);w.put(field,field.equals("limit")?3:UUID.randomUUID().toString());bad.add(body(0,List.of(w)));
        }
        for(String field:List.of("scope","size","type","projectId")){var w=widget("board");w.put(field,"INVALID");bad.add(body(0,List.of(w)));}
        for(String value:List.of(""," ","😀".repeat(25))) {var w=widget("board");w.put("title",value);bad.add(body(0,List.of(w)));}
        var duplicate=widget("board");duplicate.put("id"," widget ");bad.add(body(0,List.of(good,duplicate)));
        var unknown=widget("board");unknown.put("position",0);bad.add(body(0,List.of(unknown)));
        bad.add("{\"schemaVersion\":1,\"revision\":0,\"widgets\":[{\"id\":\"a\",\"id\":\"b\",\"type\":\"board\",\"title\":\"T\",\"scope\":\"all\",\"size\":\"wide\"}]}");
        for(String b:bad) mvc.perform(putHome(o,b)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for(String version:List.of("0","2","-1","999999999999999999999999"))mvc.perform(putHome(o,"{\"schemaVersion\":"+version+",\"revision\":0,\"widgets\":[]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SCHEMA_VERSION"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from dashboards where workspace_id=?",Long.class,o.workspace()));assertEquals(0L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace()));
    }
    @Test void overviewExactZeroShapeTotalsArchivedAndIntersection() throws Exception {
        var o=owner();var empty=read(o,OVERVIEW);
        assertEquals(Set.of("scope","projectId","projects","tasks","asOf"),keys(empty));assertTrue(empty.get("projectId").isNull());assertEquals("all",empty.get("scope").asString());
        assertEquals(Set.of("total","archived","byScope"),keys(empty.get("projects")));assertEquals(Set.of("unity","server"),keys(empty.get("projects").get("byScope")));assertEquals(Set.of("todo","doing","done","total"),keys(empty.get("tasks")));
        UUID active=project(o,"unity",false),archived=project(o,"server",true);
        for(String status:List.of("todo","doing","done"))jdbc.update("insert into tasks(id,workspace_id,project_id,title,status,created_at,updated_at) values(?,?,?,'Task',?,now(),now())",UUID.randomUUID(),o.workspace(),archived,status);
        jdbc.update("insert into tasks(id,workspace_id,project_id,title,deleted_at,created_at,updated_at) values(?,?,?,'Trash',now(),now(),now())",UUID.randomUUID(),o.workspace(),active);
        var all=read(o,OVERVIEW);assertEquals(1,all.get("projects").get("total").asLong());assertEquals(1,all.get("projects").get("archived").asLong());assertEquals(3,all.get("tasks").get("total").asLong());
        var selected=response(get(OVERVIEW).session(o.session()).param("projectId",archived.toString()));assertEquals(0,selected.get("projects").get("total").asLong());assertEquals(1,selected.get("projects").get("archived").asLong());
        var mismatch=response(get(OVERVIEW).session(o.session()).param("projectId",archived.toString()).param("scope","unity"));assertEquals(0,mismatch.get("tasks").get("total").asLong());assertEquals(0,mismatch.get("projects").get("archived").asLong());
        var stats=read(o,"/api/v1/tasks/stats");assertEquals(stats.get("total"),all.get("tasks").get("total"));for(String status:List.of("todo","doing","done"))assertEquals(stats.get("counts").get(status),all.get("tasks").get(status));
        assertEquals(0,read(o,HOME).get("revision").asLong());
    }
    @Test void strictQueriesAndAuthorization() throws Exception {
        Owner o=owner(),other=owner();UUID foreign=project(other,"server",true);
        for(String path:List.of(HOME,OVERVIEW)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(String key:List.of("limit","cursor","query","status","projectStatus","deleted","workspaceId"))
                mvc.perform(get(path).session(o.session()).param(key,"x")).andExpect(status().isBadRequest());
        }
        for(String scope:List.of("","ALL","bad"))mvc.perform(get(OVERVIEW).session(o.session()).param("scope",scope)).andExpect(status().isBadRequest());
        mvc.perform(get(OVERVIEW).session(o.session()).param("scope","all","unity")).andExpect(status().isBadRequest());
        mvc.perform(get(OVERVIEW).session(o.session()).param("projectId",foreign.toString(),foreign.toString())).andExpect(status().isBadRequest());
        for(String id:List.of("","bad","1-1-1-1-1"))mvc.perform(get(OVERVIEW).session(o.session()).param("projectId",id)).andExpect(status().isBadRequest());
        for(UUID id:List.of(foreign,UUID.randomUUID())) {
            mvc.perform(get(OVERVIEW).session(o.session()).param("projectId",id.toString()).param("scope","unity")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            var w=widget("journal");w.put("projectId",id.toString());mvc.perform(putHome(o,body(99,List.of(w)))).andExpect(status().isNotFound());
        }
        mvc.perform(put(HOME).contentType("application/json").content(body(0,List.of()))).andExpect(status().isUnauthorized());
        mvc.perform(put(HOME).session(o.session()).contentType("application/json").content(body(0,List.of()))).andExpect(status().isForbidden());
        mvc.perform(put(HOME).session(o.session()).header("X-CSRF-Token","bad").contentType("application/json").content(body(0,List.of()))).andExpect(status().isForbidden());
        mvc.perform(putHome(o,body(0,List.of())).header("Origin","https://evil.invalid")).andExpect(status().isForbidden());
        mvc.perform(putHome(o,body(0,List.of())).param("scope","all")).andExpect(status().isBadRequest());
        response(putHome(o,body(0,List.of(widget("links")))));assertEquals(0,read(other,HOME).get("revision").asLong());assertEquals(0,read(o,OVERVIEW).get("projects").get("total").asLong());
        for(String path:List.of(HOME,OVERVIEW)) {
            var disabled=owner();jdbc.update("update users set disabled_at=now() where id=?",disabled.user());
            mvc.perform(get(path).session(disabled.session())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        }
        var disabled=owner();jdbc.update("update users set disabled_at=now() where id=?",disabled.user());
        mvc.perform(putHome(disabled,body(0,List.of()))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }
    @RepeatedTest(3) void concurrentFirstHttpPutReturns200And409() throws Exception {
        var o=owner();CyclicBarrier start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> save=()->{start.await(10,TimeUnit.SECONDS);return mvc.perform(putHome(o,body(0,List.of(widget("board"))))).andReturn().getResponse().getStatus();};
            var a=pool.submit(save);var b=pool.submit(save);assertEquals(Set.of(200,409),Set.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS)));
        }
        assertEquals(1,read(o,HOME).get("revision").asLong());assertEquals(1L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace()));
    }
}
