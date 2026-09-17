package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.*;
import java.util.*;

public record LocalWidgetDefinition(String type) implements WidgetTypeDefinition {
    public int configVersion(){return 1;}
    public Set<String> sizes(){return Set.of("small","medium","wide");}
    public String availability(){return type.equals("deploy")?"unavailable":"ready";}
    public WidgetConfig decode(String json) {
        var n=WidgetJson.parse(json);WidgetJson.fields(n,"selection","limit");
        var s=n.get("selection");if(s==null)throw WidgetException.invalid("selection");WidgetJson.fields(s,"kind","projectId","categoryId");
        var selection=new WidgetSelection(WidgetJson.text(s,"kind"),s.has("projectId")?WidgetJson.uuid(WidgetJson.text(s,"projectId"),"projectId"):null,s.has("categoryId")?WidgetJson.uuid(WidgetJson.text(s,"categoryId"),"categoryId"):null);
        Integer limit=n.has("limit")?(int)WidgetJson.integer(n,"limit",1,20):null;
        if(Set.of("deploy","links").contains(type)&&limit!=null)throw WidgetException.invalid("limit");
        if(type.equals("deploy")&&!selection.kind().equals("all"))throw WidgetException.invalid("selection");
        return new LocalWidgetConfig(selection,limit);
    }
    public WidgetSelection selection(WidgetConfig config){return ((LocalWidgetConfig)config).selection();}
    public String configSchema() {
        String selection=type.equals("deploy")?"{\"type\":\"object\",\"required\":[\"kind\"],\"additionalProperties\":false,\"properties\":{\"kind\":{\"const\":\"all\"}}}":
            "{\"oneOf\":["+simple("all")+","+simple("uncategorized")+","+reference("project","projectId")+","+reference("category","categoryId")+"]}";
        String limit=Set.of("deploy","links").contains(type)?"":",\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}";
        return "{\"type\":\"object\",\"required\":[\"selection\"],\"additionalProperties\":false,\"properties\":{\"selection\":"+selection+limit+"}}";
    }
    private String simple(String kind){return "{\"type\":\"object\",\"required\":[\"kind\"],\"additionalProperties\":false,\"properties\":{\"kind\":{\"const\":\""+kind+"\"}}}";}
    private String reference(String kind,String id){return "{\"type\":\"object\",\"required\":[\"kind\",\""+id+"\"],\"additionalProperties\":false,\"properties\":{\"kind\":{\"const\":\""+kind+"\"},\""+id+"\":{\"type\":\"string\",\"format\":\"uuid\"}}}";}
}
