package com.kopite.devspace;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class) @AutoConfigureMockMvc
class CategoryOnlyDashboardApiTests {
    @Autowired MockMvc mvc; @Autowired UserWorkspaceCreationService users; @Autowired JdbcTemplate jdbc; @Autowired JsonMapper json;
    @Value("${app.security.origin}") String origin;
    record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf) {}
    Owner owner()throws Exception {
        var o=users.createOrReuse("v2-replay",UUID.randomUUID().toString(),"Owner");
        var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(o.user().getId(),"Owner"),null,List.of()));
        var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",ctx);
        String csrf=json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn().getResponse().getContentAsString()).path("csrfToken").asString();
        return new Owner(o.user().getId(),o.workspace().getId(),session,csrf);
    }
    String post(Owner o,String path,String key,String body,int status)throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).session(o.session()).header("X-CSRF-Token",o.csrf()).header("Origin",origin)
            .header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().is(status))
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
    }

    tools.jackson.databind.JsonNode read(Owner o,String path)throws Exception {
        var response=mvc.perform(get(path).session(o.session())).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Workspace-Data-Revision",Long.toString(counter(o)))).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }
    long counter(Owner o){return jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace());}
    String category(Owner o,String name)throws Exception{return json.readTree(post(o,"/api/v1/project-categories",name,json.writeValueAsString(Map.of("name",name)),201)).path("id").asString();}
    String project(Owner o,String name,String category)throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("name",name);body.put("stack","Java");body.put("categoryId",category);
        return json.readTree(post(o,"/api/v2/projects",name,json.writeValueAsString(body),201)).path("id").asString();
    }
    void patchProject(Owner o,String id,long revision,Object category)throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("revision",revision);body.put("categoryId",category);
        long before=counter(o);
        mvc.perform(patch("/api/v2/projects/"+id).session(o.session()).header("Origin",origin).header("X-CSRF-Token",o.csrf())
            .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk())
            .andExpect(header().string("X-Workspace-Data-Revision",Long.toString(before+1)));
    }
    void children(Owner o,String project)throws Exception {
        var id=UUID.fromString(project);
        jdbc.update("insert into tasks(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Needle',now(),now())",UUID.randomUUID(),o.workspace(),id);
        jdbc.update("insert into journals(id,workspace_id,project_id,title,body,entry_date,created_at,updated_at) values(?,?,?,'Needle','Body',current_date,now(),now())",UUID.randomUUID(),o.workspace(),id);
        jdbc.update("insert into milestones(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Needle',now(),now())",UUID.randomUUID(),o.workspace(),id);
    }

    static final String HOME="/api/v2/dashboards/home";
    Map<String,Object> widget(String id,String kind,String target) {
        var selection=new LinkedHashMap<String,Object>();selection.put("kind",kind);if(target!=null)selection.put(kind+"Id",target);
        return new LinkedHashMap<>(Map.of("id",id,"type","board","title","Board","size","wide","selection",selection));
    }
    tools.jackson.databind.JsonNode save(Owner o,long revision,List<?> widgets,int expected)throws Exception {
        long before=counter(o);var result=mvc.perform(put(HOME).session(o.session()).header("Origin",origin).header("X-CSRF-Token",o.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("schemaVersion",2,"revision",revision,"widgets",widgets)))).andExpect(status().is(expected));
        if(expected==200)result.andExpect(header().string("X-Workspace-Data-Revision",Long.toString(before+1)));
        else {result.andExpect(header().doesNotExist("X-Workspace-Data-Revision"));assertEquals(before,counter(o));}
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    @Test void deletionRetainsUuidAndOnlyIdenticalExistingMissingSelectionsCanBeSaved()throws Exception {
        var o=owner();String c=category(o,"Name");var original=widget("category","category",c);
        var saved=save(o,0,List.of(original),200);assertEquals("valid",saved.path("widgets").get(0).path("selectionState").asString());
        String row=jdbc.queryForObject("select widgets::text from dashboards where workspace_id=?",String.class,o.workspace());assertFalse(row.contains("selectionState"));assertFalse(row.contains("scope"));
        mvc.perform(delete("/api/v1/project-categories/"+c).session(o.session()).header("Origin",origin).header("X-CSRF-Token",o.csrf()).queryParam("revision","1")).andExpect(status().isOk());
        var missing=read(o,HOME);assertEquals(1,missing.path("revision").asInt());assertEquals(c,missing.path("widgets").get(0).path("selection").path("categoryId").asString());assertEquals("missingCategory",missing.path("widgets").get(0).path("selectionState").asString());
        assertEquals(row,jdbc.queryForObject("select widgets::text from dashboards where workspace_id=?",String.class,o.workspace()));
        String recreated=json.readTree(post(o,"/api/v1/project-categories","recreated",json.writeValueAsString(Map.of("name","Name")),201)).path("id").asString();assertNotEquals(c,recreated);
        assertEquals("missingCategory",read(o,HOME).path("widgets").get(0).path("selectionState").asString());
        var retained=save(o,1,List.of(original),200);assertEquals(2,retained.path("revision").asInt());assertEquals("missingCategory",retained.path("widgets").get(0).path("selectionState").asString());
        save(o,2,List.of(widget("new-id","category",c)),404);save(o,2,List.of(widget("category","category",UUID.randomUUID().toString())),404);
        var roundTrip=json.convertValue(retained.path("widgets").get(0),Map.class);save(o,2,List.of(roundTrip),400);
        save(o,1,List.of(original),409);
        var valid=save(o,2,List.of(widget("category","category",recreated)),200);assertEquals("valid",valid.path("widgets").get(0).path("selectionState").asString());
    }
    @Test void strictSelectionShapesAndOwnedIdentities()throws Exception {
        var o=owner();var other=owner();String foreignCategory=category(other,"Foreign"),foreignProject=project(other,"Foreign",null);
        save(o,0,List.of(widget("c","category",foreignCategory)),404);save(o,0,List.of(widget("p","project",foreignProject)),404);
        var bad=new ArrayList<Object>();bad.add("all");bad.add(List.of());bad.add(Map.of());bad.add(Map.of("kind","unity"));bad.add(Map.of("kind","project"));bad.add(Map.of("kind","category","categoryId","bad"));
        bad.add(Map.of("kind","all","projectId",UUID.randomUUID().toString()));bad.add(Map.of("kind","category","categoryId",UUID.randomUUID().toString(),"projectId",UUID.randomUUID().toString()));bad.add(Map.of("kind","all","scope","all"));
        var explicitNull=new LinkedHashMap<String,Object>();explicitNull.put("kind","all");explicitNull.put("categoryId",null);bad.add(explicitNull);
        for(Object selection:bad){var w=widget("x","all",null);w.put("selection",selection);save(o,0,List.of(w),400);}
        String duplicate="{\"schemaVersion\":2,\"revision\":0,\"widgets\":[{\"id\":\"x\",\"type\":\"board\",\"title\":\"T\",\"size\":\"wide\",\"selection\":{\"kind\":\"all\",\"kind\":\"uncategorized\"}}]}";
        mvc.perform(put(HOME).session(o.session()).header("Origin",origin).header("X-CSRF-Token",o.csrf()).contentType("application/json").content(duplicate)).andExpect(status().isBadRequest());
        assertEquals(0,counter(o));assertEquals(0,read(o,HOME).path("revision").asInt());
    }
}
