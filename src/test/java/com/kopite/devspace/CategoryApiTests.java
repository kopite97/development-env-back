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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class) @AutoConfigureMockMvc
class CategoryApiTests {
    static final String C="/api/v1/project-categories",P="/api/v2/projects";
    @Autowired MockMvc mvc; @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired UserWorkspaceCreationService users;
    record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf) {}
    Owner owner()throws Exception {
        var u=users.createOrReuse("category-api",UUID.randomUUID().toString(),"Owner");
        var session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(u.user().getId(),"Owner"),null,List.of()));session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        String csrf=body(mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk())).get("csrfToken").asString();
        return new Owner(u.user().getId(),u.workspace().getId(),session,csrf);
    }
    MockHttpServletRequestBuilder mutation(Owner o,MockHttpServletRequestBuilder r){return r.session(o.session()).header("X-CSRF-Token",o.csrf()).contentType("application/json");}
    JsonNode body(ResultActions r)throws Exception{return json.readTree(r.andReturn().getResponse().getContentAsString());}
    JsonNode create(Owner o,String name)throws Exception{return body(mvc.perform(mutation(o,post(C)).header("Idempotency-Key",UUID.randomUUID().toString()).content(json.writeValueAsString(Map.of("name",name)))).andExpect(status().isCreated()));}
    JsonNode project(Owner o,String extra,String key)throws Exception{return body(mvc.perform(mutation(o,post(P)).header("Idempotency-Key",key).content("{\"name\":\"Project\",\"stack\":\"Java\""+extra+"}")).andExpect(status().isCreated()));}
    long counter(Owner o){return jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,o.workspace());}
    Set<String> keys(JsonNode n){return n.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());}

    @Test void categoryCrudAndProjectPresenceArchiveReplayFreshness()throws Exception {
        var o=owner();var empty=body(mvc.perform(get(C).session(o.session())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertEquals(Set.of("items","total"),keys(empty));assertEquals(0,empty.get("total").asInt());
        var c=create(o," Cafe\u0301 ");String id=c.get("id").asString();assertEquals("Caf\u00e9",c.get("name").asString());
        assertEquals(Set.of("id","name","revision","createdAt","updatedAt"),keys(c));
        var p=project(o,",\"categoryId\":\""+id+"\"","assigned");String pid=p.get("id").asString();
        assertEquals(id,p.get("categoryId").asString());assertFalse(p.has("categoryName"));
        mvc.perform(mutation(o,delete(C+"/"+id)).param("revision","1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":1,\"status\":\"archived\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(id));
        mvc.perform(mutation(o,delete(C+"/"+id)).param("revision","1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        mvc.perform(mutation(o,patch(C+"/"+id)).content("{\"revision\":1,\"name\":\"Renamed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        mvc.perform(get(P+"/"+pid).session(o.session())).andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.categoryId").value(id)).andExpect(jsonPath("$.scope").doesNotExist());
        mvc.perform(get(C+"/"+id).session(o.session())).andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":2,\"categoryId\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3));
        mvc.perform(mutation(o,delete(C+"/"+id)).param("revision","2")).andExpect(status().isOk()).andExpect(jsonPath("$.deletedId").value(id));
        assertEquals(p,project(o,",\"categoryId\":\""+id+"\"","assigned"));
        assertEquals(6,counter(o));
        mvc.perform(mutation(o,post(P)).header("Idempotency-Key","fresh").content("{\"name\":\"Project\",\"stack\":\"Java\",\"categoryId\":\""+id+"\"}"))
            .andExpect(status().isNotFound());
        var omitted=project(o,"","omitted");assertTrue(omitted.get("categoryId").isNull());
        var explicit=project(o,",\"categoryId\":null","null");assertTrue(explicit.get("categoryId").isNull());
        mvc.perform(mutation(o,post(P)).header("Idempotency-Key","omitted").content("{\"name\":\"Project\",\"stack\":\"Java\",\"categoryId\":null}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":3,\"categoryId\":null,\"status\":\"active\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4));
    }

    @Test void strictRequestsAndTypedNameConflicts()throws Exception {
        var o=owner();var c=create(o,"Tools");String id=c.get("id").asString();long before=counter(o);
        for(String invalid:List.of("{}","[]","null","{\"name\":null}","{\"name\":3}","{\"name\":\"a\",\"name\":\"b\"}","{\"name\":\"a\",\"scope\":\"unity\"}","{\"name\":\" \"}"))
            mvc.perform(mutation(o,post(C)).header("Idempotency-Key","invalid").content(invalid)).andExpect(status().isBadRequest());
        for(String invalid:List.of("{}","{\"revision\":1}","{\"revision\":null,\"name\":\"x\"}","{\"revision\":1.5,\"name\":\"x\"}","{\"revision\":1,\"name\":null}","{\"revision\":9007199254740992,\"name\":\"x\"}"))
            mvc.perform(mutation(o,patch(C+"/"+id)).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(get(C).session(o.session()).param("limit","20")).andExpect(status().isBadRequest());
        mvc.perform(mutation(o,delete(C+"/"+id)).param("revision","1","1")).andExpect(status().isBadRequest());
        mvc.perform(mutation(o,delete(C+"/"+id)).param("revision","1").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(mutation(o,post(C)).header("Idempotency-Key","duplicate").content("{\"name\":\" Tools \"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_CONFLICT")).andExpect(jsonPath("$.fieldErrors.name").exists());
        assertEquals(before,counter(o));
        var p=project(o,"","p");String pid=p.get("id").asString();
        for(String extra:List.of("null,\"categoryId\":null","3","{}","\"invalid\"","\"1-1-1-1-1\""))
            mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":1,\"categoryId\":"+extra+"}")).andExpect(status().isBadRequest());
        mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":1,\"scope\":null}")).andExpect(status().isBadRequest());
        mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":1,\"categoryId\":\""+id.toUpperCase(Locale.ROOT)+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(id));
        mvc.perform(mutation(o,patch(P+"/"+pid)).content("{\"revision\":1,\"categoryId\":null}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
    }

    @Test void securityAndOwnershipDoNotWriteOrLeak()throws Exception {
        var a=owner();var b=owner();String id=create(a,"Owned").get("id").asString();
        for(String target:List.of(id,UUID.randomUUID().toString())) {
            mvc.perform(get(C+"/"+target).session(b.session())).andExpect(status().isNotFound());
            mvc.perform(mutation(b,patch(C+"/"+target)).content("{\"revision\":999,\"name\":\"x\"}")).andExpect(status().isNotFound());
            mvc.perform(mutation(b,delete(C+"/"+target)).param("revision","999")).andExpect(status().isNotFound());
            mvc.perform(mutation(b,post(P)).header("Idempotency-Key","foreign").content("{\"name\":\"P\",\"stack\":\"J\",\"categoryId\":\""+target+"\"}"))
                .andExpect(status().isNotFound());
        }
        mvc.perform(get(C)).andExpect(status().isUnauthorized());
        mvc.perform(post(C).contentType("application/json").content("{\"name\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(C).session(b.session()).header("Idempotency-Key","csrf").contentType("application/json").content("{\"name\":\"x\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(mutation(b,post(C)).header("Origin","https://invalid.example").header("Idempotency-Key","origin").content("{\"name\":\"x\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(mutation(b,post(C)).header("Idempotency-Key","owner").content("{\"name\":\"x\",\"workspaceId\":\""+a.workspace()+"\"}"))
            .andExpect(status().isBadRequest());
        jdbc.update("update users set disabled_at=now() where id=?",b.user());
        mvc.perform(get(C).session(b.session())).andExpect(status().isForbidden());
        assertEquals(0,counter(b));assertEquals(1,counter(a));
    }
}
