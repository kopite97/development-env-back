package com.kopite.devspace;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Category compatibility through the replacement Widget/layout API. */
class CategoryOnlyDashboardApiTests extends WidgetTestSupport {
    String category(Owner o,String name)throws Exception {
        return id(tree(request(o,post("/api/v1/project-categories").header("Idempotency-Key",UUID.randomUUID().toString()),json.writeValueAsString(Map.of("name",name)),201)));
    }
    String withSelection(String body,Object selection){var n=(tools.jackson.databind.node.ObjectNode)json.readTree(body);((tools.jackson.databind.node.ObjectNode)n.path("config")).set("selection",json.valueToTree(selection));return n.toString();}
    @Test void deletionRetainsUuidAndOnlyIdenticalExistingMissingSelectionsCanBeSaved()throws Exception {
        var o=owner();String c=category(o,"Name");var selection=Map.of("kind","category","categoryId",c);
        String body=withSelection(createBody("board"),selection);String id=id(tree(request(o,post(W).header("Idempotency-Key","create"),body,201)));
        request(o,put(D),layout(0,id),200);
        String row=jdbc.queryForObject("select config::text from widgets where id=?",String.class,UUID.fromString(id));assertFalse(row.contains("referenceState"));
        request(o,delete("/api/v1/project-categories/"+c+"?revision=1"),null,200);
        var missing=tree(request(o,get(W+"/"+id),null,200));assertEquals("missingCategory",missing.path("referenceState").asString());assertEquals(c,missing.path("config").path("selection").path("categoryId").asString());
        assertEquals(row,jdbc.queryForObject("select config::text from widgets where id=?",String.class,UUID.fromString(id)));
        assertEquals("REFERENCE_MISSING",tree(request(o,get(W+"/"+id+"/data"),null,200)).path("problem").path("code").asString());
        String recreated=category(o,"Name");assertNotEquals(c,recreated);
        assertEquals("missingCategory",tree(request(o,get(D),null,200)).path("widgets").get(0).path("referenceState").asString());
        var retained=tree(request(o,put(W+"/"+id),withSelection(update(1),selection),200));assertEquals(2,retained.path("revision").asLong());
        request(o,post(W).header("Idempotency-Key","new"),body,404);
        request(o,put(W+"/"+id),withSelection(update(2),Map.of("kind","category","categoryId",UUID.randomUUID().toString())),404);
        request(o,put(W+"/"+id),retained.toString(),400);
        request(o,put(W+"/"+id),withSelection(update(1),selection),409);
        assertEquals("valid",tree(request(o,put(W+"/"+id),withSelection(update(2),Map.of("kind","category","categoryId",recreated)),200)).path("referenceState").asString());
    }
    @Test void strictSelectionShapesAndOwnedIdentities()throws Exception {
        var o=owner();var other=owner();String foreign=category(other,"Foreign");
        request(o,post(W).header("Idempotency-Key","foreign"),withSelection(createBody("board"),Map.of("kind","category","categoryId",foreign)),404);
        var bad=new ArrayList<Object>(List.of("all",List.of(),Map.of(),Map.of("kind","unity"),Map.of("kind","project"),Map.of("kind","category","categoryId","bad"),Map.of("kind","all","scope","all"),Map.of("kind","all","projectId",UUID.randomUUID().toString()),Map.of("kind","category","categoryId",foreign,"projectId",foreign)));
        var explicitNull=new LinkedHashMap<String,Object>();explicitNull.put("kind","all");explicitNull.put("categoryId",null);bad.add(explicitNull);
        for(Object selection:bad)request(o,post(W).header("Idempotency-Key",UUID.randomUUID().toString()),withSelection(createBody("board"),selection),400);
        assertEquals(0,counter(o));assertFalse(tree(request(o,get(D),null,200)).path("initialized").asBoolean());
    }
    @Test void allTypeSizeSelectionCombinationsPreserveOmittedDefaults()throws Exception {
        var o=owner();String c=category(o,"Reference");UUID p=UUID.randomUUID();jdbc.update("insert into projects(id,workspace_id,name,stack,status,created_at,updated_at) values(?,?,'Archived','Java','archived',now(),now())",p,o.workspace());long revision=0;
        for(String type:List.of("overview","board","deploy","links","journal","milestone"))for(String size:List.of("small","medium","wide"))for(String kind:List.of("all","uncategorized","project","category")){
            var selection=new LinkedHashMap<String,Object>();selection.put("kind",kind);if(kind.equals("project"))selection.put("projectId",p.toString());if(kind.equals("category"))selection.put("categoryId",c);
            String body=withSelection(createBody(type),selection);boolean invalid=type.equals("deploy")&&!kind.equals("all");
            var created=request(o,post(W).header("Idempotency-Key",UUID.randomUUID().toString()),body,invalid?400:201);if(invalid)continue;
            var widget=tree(created);assertEquals(kind,widget.path("config").path("selection").path("kind").asString());assertFalse(widget.path("config").has("limit"));assertEquals("valid",widget.path("referenceState").asString());
            var saved=tree(request(o,put(D),json.writeValueAsString(Map.of("schemaVersion",3,"layoutRevision",revision++,"placements",List.of(Map.of("widgetId",id(widget),"size",size)))),200));assertEquals(size,saved.path("placements").get(0).path("size").asString());
        }
    }
}
