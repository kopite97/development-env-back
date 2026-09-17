package com.kopite.devspace;
import org.junit.jupiter.api.Test;
import java.util.*;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class WidgetInputTests extends WidgetTestSupport {
    @Test void malformedConfigurationAndLayoutNeverWrite()throws Exception {
        var o=owner();var bad=new ArrayList<String>(List.of("[]","null","{}","{"));
        request(o,get(W+"/1-1-1-1-1"),null,400);request(o,get(W+"/1-1-1-1-1/data"),null,400);
        for(String field:List.of("type","title","configVersion","config")) {
            var n=(ObjectNode)json.readTree(createBody("board"));n.remove(field);bad.add(n.toString());
            n=(ObjectNode)json.readTree(createBody("board"));n.putNull(field);bad.add(n.toString());
        }
        for(Object limit:List.of(0,21,-1,1.5,"1",true)) {
            var n=(ObjectNode)json.readTree(createBody("board"));((ObjectNode)n.get("config")).set("limit",json.valueToTree(limit));bad.add(n.toString());
        }
        for(String type:List.of("links","deploy")) {
            var n=(ObjectNode)json.readTree(createBody(type));((ObjectNode)n.get("config")).put("limit",3);bad.add(n.toString());
        }
        for(String title:List.of(""," ","x".repeat(49))) {
            var n=(ObjectNode)json.readTree(createBody("board"));n.put("title",title);bad.add(n.toString());
        }
        for(String field:List.of("id","workspaceId","ownerUserId","createdAt","referenceState","size","position")) {
            var n=(ObjectNode)json.readTree(createBody("board"));n.put(field,"x");bad.add(n.toString());
        }
        for(String body:bad)request(o,post(W).header("Idempotency-Key",UUID.randomUUID().toString()),body,400);
        for(String value:List.of("-1","9007199254740992","99999999999999999999999","0.0","\"0\"","null"))request(o,put(D),"{\"schemaVersion\":3,\"layoutRevision\":"+value+",\"placements\":[]}",400);
        for(String value:List.of("null","\"3\"","3.0","2","4","true"))request(o,put(D),"{\"schemaVersion\":"+value+",\"layoutRevision\":0,\"placements\":[]}",400);
        for(String value:List.of("null","{}","[null]","[{\"widgetId\":null,\"size\":\"wide\"}]"))request(o,put(D),"{\"schemaVersion\":3,\"layoutRevision\":0,\"placements\":"+value+"}",400);
        assertEquals(0,counter(o));assertEquals(0,jdbc.queryForObject("select count(*) from widgets where workspace_id=?",Integer.class,o.workspace()));assertFalse(tree(request(o,get(D),null,200)).path("initialized").asBoolean());
    }
}
