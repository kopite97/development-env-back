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
class LinkApiTests {
    private static final String BASE="/api/v1/links";
    private final MockMvc mvc;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    @Autowired
    LinkApiTests(MockMvc mvc,UserWorkspaceCreationService users,JdbcTemplate jdbc,JsonMapper json) {
        this.mvc=mvc; this.users=users; this.jdbc=jdbc; this.json=json;
    }
    private record Owner(UUID user,UUID workspace,MockHttpSession session,String csrf) {}
    private Owner owner() throws Exception {
        var user=users.createOrReuse("link-api",UUID.randomUUID().toString(),"Owner");
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
    private String body() {return "{\"label\":\" Link \",\"url\":\"https://example.com\"}";}
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
    void sixOperationsReturnExactWrappersAndServerOrder() throws Exception {
        var o=owner();var empty=read(o,BASE,Map.of());assertEquals(0,empty.get("collectionRevision").asLong());assertTrue(empty.get("nextCursor").isNull());
        var a=create(o,body());var b=create(o,body());assertEquals(2,a.size());assertEquals(9,a.get("item").size());
        String id=a.get("item").get("id").asString(),bid=b.get("item").get("id").asString();
        var detail=read(o,BASE+"/"+id,Map.of());assertEquals(a.get("item"),detail);assertEquals("",detail.get("description").asString());assertEquals("all",detail.get("scope").asString());
        var order=mvc.perform(mutation(put(BASE+"/order"),o).content(json.writeValueAsString(Map.of("collectionRevision",2,"ids",List.of(bid,id)))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.collectionRevision").value(3)).andReturn();
        var rows=json.readTree(order.getResponse().getContentAsString());assertEquals(4,rows.size());assertEquals(bid,rows.get("items").get(0).get("id").asString());assertEquals(2,rows.get("items").get(1).get("revision").asInt());
        mvc.perform(mutation(patch(BASE+"/"+id),o).content("{\"revision\":1}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(mutation(patch(BASE+"/"+id),o).content("{\"revision\":2,\"description\":\"changed\",\"scope\":\"unity\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.position").value(1)).andExpect(jsonPath("$.item.revision").value(3)).andExpect(jsonPath("$.collectionRevision").value(4));
        mvc.perform(mutation(delete(BASE+"/"+id),o).param("revision","2")).andExpect(status().isConflict());
        var deleted=mvc.perform(mutation(delete(BASE+"/"+id),o).param("revision","3")).andExpect(status().isOk()).andExpect(jsonPath("$.collectionRevision").value(5)).andReturn();
        assertEquals(2,json.readTree(deleted.getResponse().getContentAsString()).size());
        mvc.perform(get(BASE+"/"+id).session(o.session())).andExpect(status().isNotFound());
        mvc.perform(mutation(patch(BASE+"/"+id),o).content("{\"revision\":3}")).andExpect(status().isNotFound());
        mvc.perform(mutation(delete(BASE+"/"+id),o).param("revision","3")).andExpect(status().isNotFound());
        assertEquals(1,read(o,BASE,Map.of()).get("total").asInt());
        mvc.perform(mutation(put(BASE+"/"+bid),o).content("{}")).andExpect(status().isMethodNotAllowed());
        String key=UUID.randomUUID().toString();var first=mvc.perform(mutation(post(BASE),o).header("Idempotency-Key",key).content(body())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String createdId=json.readTree(first).get("item").get("id").asString();
        mvc.perform(mutation(delete(BASE+"/"+createdId),o).param("revision","1")).andExpect(status().isOk());
        mvc.perform(mutation(post(BASE),o).header("Idempotency-Key",key).content("{\"url\":\"https://example.com\",\"label\":\" Link \"}"))
            .andExpect(status().isCreated()).andExpect(content().json(first));assertEquals(1,read(o,BASE,Map.of()).get("total").asInt());
    }
    @Test
    void strictWritesAndUnsupportedQueriesReject() throws Exception {
        var o=owner();var a=create(o,body());String path=BASE+"/"+a.get("item").get("id").asString();
        for(String field:List.of("desc","position","projectId","workspaceId","ownerUserId","userId","id","createdAt","updatedAt","collectionRevision","revision"))
            mvc.perform(mutation(post(BASE),o).header("Idempotency-Key","bad").content(body().replace("}",",\""+field+"\":1}"))).andExpect(status().isBadRequest());
        for(String field:List.of("label","description","url","scope")) for(String value:List.of("null","true","1","[]","{}"))
            mvc.perform(mutation(patch(path),o).content("{\"revision\":1,\""+field+"\":"+value+"}")).andExpect(status().isBadRequest());
        for(String value:List.of("null","0","-1","1.1","\"1\"","9007199254740992"))
            mvc.perform(mutation(patch(path),o).content("{\"revision\":"+value+"}")).andExpect(status().isBadRequest());
        mvc.perform(mutation(patch(path),o).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(mutation(patch(path),o).content("{\"revision\":1,\"label\":\"a\",\"label\":\"b\"}")).andExpect(status().isBadRequest());
        mvc.perform(mutation(delete(path),o)).andExpect(status().isBadRequest());
        for(String r:List.of("0","-1","9007199254740992","1.0"))mvc.perform(mutation(delete(path),o).param("revision",r)).andExpect(status().isBadRequest());
        for(String input:List.of("{}","[]","{\"collectionRevision\":null,\"ids\":[]}","{\"collectionRevision\":1,\"ids\":[null]}","{\"collectionRevision\":1,\"ids\":[\"bad\"]}","{\"collectionRevision\":1,\"ids\":[],\"revision\":1}","{\"collectionRevision\":1,\"ids\":[],\"ids\":[]}","{\"collectionRevision\":\"1\",\"ids\":[]}"))
            mvc.perform(mutation(put(BASE+"/order"),o).content(input)).andExpect(status().isBadRequest());
        mvc.perform(mutation(put(BASE+"/order"),o).content("{\"collectionRevision\":0,\"ids\":[]}")).andExpect(status().isConflict());
        mvc.perform(mutation(put(BASE+"/order"),o).content("{\"collectionRevision\":1,\"ids\":[]}")).andExpect(status().isBadRequest());
        for(String p:List.of("cursor","limit","projectId","sort","workspaceId"))mvc.perform(get(BASE).session(o.session()).param(p,"x")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).session(o.session()).param("scope","bad")).andExpect(status().isBadRequest());
        mvc.perform(mutation(post(BASE),o).content(body())).andExpect(status().isBadRequest());
    }
    @Test
    void fullFilteredCollectionsIncludeCommonAndLiteralUrlSearch() throws Exception {
        var o=owner();create(o,"{\"label\":\"Common\",\"url\":\"https://example.com/a_%25!\",\"description\":\"Needle\"}");
        create(o,"{\"label\":\"Unity\",\"url\":\"https://example.com\",\"scope\":\"unity\"}");create(o,"{\"label\":\"Server\",\"url\":\"https://example.com\",\"scope\":\"server\"}");
        assertEquals(3,read(o,BASE,Map.of()).get("total").asInt());
        for(String scope:List.of("unity","server"))assertEquals(2,read(o,BASE,Map.of("scope",scope)).get("total").asInt());
        for(String query:List.of("NEEDLE","common","a_%25!","%","_","!")){var r=read(o,BASE,Map.of("query",query));assertEquals(1,r.get("total").asInt(),query);assertEquals(3,r.get("collectionRevision").asInt());assertTrue(r.get("nextCursor").isNull());}
        var empty=read(o,BASE,Map.of("query","absent"));assertEquals(0,empty.get("total").asInt());assertEquals(3,empty.get("collectionRevision").asInt());
    }
    @Test
    void everyMutationPreservesSecurityAndCrossUserIsolation() throws Exception {
        var o=owner();var other=owner();var a=create(o,body());String id=a.get("item").get("id").asString();String path=BASE+"/"+id;
        for(var req:List.of(get(BASE),get(path),post(BASE),patch(path),delete(path),put(BASE+"/order")))mvc.perform(req.contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        for(int security=0;security<3;security++)for(var req:List.of(post(BASE),patch(path),delete(path),put(BASE+"/order"))) {
            req.session(o.session()).contentType("application/json").content("{}");
            if(security==1)req.header("X-CSRF-Token","invalid");
            if(security==2)req.header("X-CSRF-Token",o.csrf()).header("Origin","https://evil.example");
            mvc.perform(req).andExpect(status().isForbidden());
        }
        for(String target:List.of(id,UUID.randomUUID().toString())) {
            mvc.perform(get(BASE+"/"+target).session(other.session())).andExpect(status().isNotFound());
            mvc.perform(mutation(patch(BASE+"/"+target),other).content("{\"revision\":99}")).andExpect(status().isNotFound());
            mvc.perform(mutation(delete(BASE+"/"+target),other).param("revision","99")).andExpect(status().isNotFound());
        }
        mvc.perform(mutation(put(BASE+"/order"),other).content(json.writeValueAsString(Map.of("collectionRevision",0,"ids",List.of(id)))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(0,read(other,BASE,Map.of("query","Link")).get("total").asInt());
        jdbc.update("update users set disabled_at=now() where id=?",other.user());mvc.perform(get(BASE).session(other.session())).andExpect(status().isForbidden());
    }
    @Test
    void maximumCollectionIsReturnedWithoutTruncationAndWithinResponseBound() throws Exception {
        var o=owner();jdbc.update("insert into link_collections(workspace_id,revision) values(?,500)",o.workspace());
        String label="X"+"\u0001".repeat(98)+"X",description="\u0001".repeat(300),url="https://example.com/"+"x".repeat(2000-"https://example.com/".length());
        var legal=new com.kopite.devspace.link.domain.LinkValues(label,description,url,"all");assertEquals(100,legal.label().length());assertEquals(300,legal.description().length());assertEquals(2000,legal.url().length());
        jdbc.batchUpdate("insert into links(id,workspace_id,label,description,url,scope,position,revision,created_at,updated_at) values(?,?,?,?,?,'all',?,9007199254740991,now(),now())",new org.springframework.jdbc.core.BatchPreparedStatementSetter(){
            public int getBatchSize(){return 500;}
            public void setValues(java.sql.PreparedStatement s,int i)throws java.sql.SQLException{s.setObject(1,UUID.randomUUID());s.setObject(2,o.workspace());s.setString(3,label);s.setString(4,description);s.setString(5,url);s.setLong(6,i);}
        });
        var response=mvc.perform(get(BASE).session(o.session())).andExpect(status().isOk()).andReturn().getResponse();
        var value=json.readTree(response.getContentAsString());assertEquals(500,value.get("items").size());assertEquals(500,value.get("total").asInt());assertTrue(value.get("nextCursor").isNull());
        assertTrue(response.getContentAsByteArray().length<8*1024*1024);
        // Conservative serialization proof: six bytes per UTF-16 code unit, plus 1024 bytes of fixed fields/JSON overhead per item.
        assertTrue(500L*((100+300+2000)*6+1024)+1024<8L*1024*1024);
        mvc.perform(mutation(post(BASE),o).header("Idempotency-Key","over-limit").content(body())).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
        var output=java.nio.file.Path.of("build/reports/link-api/query-plan.txt");java.nio.file.Files.createDirectories(output.getParent());
        java.nio.file.Files.writeString(output,String.join("\n",jdbc.queryForList("explain select id from links where workspace_id=? order by position,id",String.class,o.workspace())));
    }
}



