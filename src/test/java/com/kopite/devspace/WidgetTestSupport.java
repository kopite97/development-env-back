package com.kopite.devspace;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
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
@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class) @AutoConfigureMockMvc
abstract class WidgetTestSupport {
    @Autowired MockMvc mvc;@Autowired UserWorkspaceCreationService users;@Autowired JdbcTemplate jdbc;@Autowired JsonMapper json;
    @Value("${app.security.origin}") String origin;
    static final String W="/api/v1/widgets",D="/api/v3/dashboards/home",I=D+"/initializations";
    record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf){}
    Owner owner()throws Exception {
        var o=users.createOrReuse("widget-test",UUID.randomUUID().toString(),"Owner");var ctx=SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(o.user().getId(),"Owner"),null,List.of()));var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",ctx);
        var response=mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn().getResponse();assertEquals(200,response.getStatus());
        return new Owner(o.user().getId(),o.workspace().getId(),session,json.readTree(response.getContentAsString()).path("csrfToken").asString());
    }
    MockHttpServletResponse request(Owner o,MockHttpServletRequestBuilder request,String body,int expected)throws Exception {
        request.session(o.session()).header("Origin",origin).header("X-CSRF-Token",o.csrf());if(body!=null)request.contentType("application/json").content(body);
        var r=mvc.perform(request).andReturn().getResponse();assertEquals(expected,r.getStatus(),r.getContentAsString());assertEquals("no-store",r.getHeader("Cache-Control"));return r;
    }
    JsonNode tree(MockHttpServletResponse r)throws Exception{return json.readTree(r.getContentAsString());}
    String createBody(String type){return "{\"type\":\""+type+"\",\"title\":\"Example\",\"configVersion\":1,\"config\":{\"selection\":{\"kind\":\"all\"}}}";}
    JsonNode create(Owner o,String type)throws Exception{return tree(request(o,post(W).header("Idempotency-Key",UUID.randomUUID().toString()),createBody(type),201));}
    String update(long revision){return "{\"revision\":"+revision+",\"title\":\"Changed\",\"configVersion\":1,\"config\":{\"selection\":{\"kind\":\"all\"}}}";}
    String layout(long revision,String... ids){var p=Arrays.stream(ids).map(id->Map.of("widgetId",id,"size","wide")).toList();return json.writeValueAsString(Map.of("schemaVersion",3,"layoutRevision",revision,"placements",p));}
    long counter(Owner o){return jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace());}
    String id(JsonNode n){return n.path("id").asString();}
}
