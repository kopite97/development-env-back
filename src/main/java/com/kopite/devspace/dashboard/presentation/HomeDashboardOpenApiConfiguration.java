package com.kopite.devspace.dashboard.presentation;
import com.kopite.devspace.dashboard.application.HomeDashboardSnapshot;
import com.kopite.devspace.dashboard.domain.HomeDashboard;
import com.kopite.devspace.dashboard.presentation.dto.HomeDashboardResponse;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@Configuration(proxyBeanMethods=false)
public class HomeDashboardOpenApiConfiguration {
    private static final List<String> CORE=List.of("id","type","title","size","selection");
    @Bean OpenApiCustomizer homeDashboardContractSchemas() {
        return api->{
            var schemas=api.getComponents().getSchemas();
            if(!schemas.containsKey("DashboardWidgetRequest"))return;
            Schema<?> selection=schemas.get("DashboardSelectionDto");selection.setRequired(List.of("kind"));selection.setAdditionalProperties(false);
            var shapes=new ArrayList<Schema>();
            for(String kind:List.of("all","uncategorized","project","category")) {
                var shape=selection(kind);String name="DashboardSelection"+Character.toUpperCase(kind.charAt(0))+kind.substring(1);
                schemas.put(name,shape);shapes.add(new Schema<>().$ref("#/components/schemas/"+name));
            }
            selection.setOneOf(shapes);
            for(boolean response:List.of(false,true)) {
                String suffix=response?"Response":"Request";var alternatives=new ArrayList<Schema>();
                for(String group:List.of("Data","Links","Deploy")) {
                    var types=group.equals("Data")?List.of("overview","board","journal","milestone"):List.of(group.toLowerCase(Locale.ROOT));
                    String name="Dashboard"+group+"Widget"+suffix;
                    schemas.put(name,widget(types,group.equals("Data"),group.equals("Deploy"),response));alternatives.add(new Schema<>().$ref("#/components/schemas/"+name));
                }
                Schema<?> schema=schemas.get("DashboardWidget"+suffix);var required=new ArrayList<>(CORE);if(response)required.add("selectionState");
                schema.setRequired(required);schema.setAdditionalProperties(false);schema.setOneOf(alternatives);
                schema.getProperties().get("id").setMinLength(1);schema.getProperties().get("title").setMinLength(1);
            }
            for(String name:List.of("SaveHomeDashboardRequest","HomeDashboardResponse")) {
                Schema<?> schema=schemas.get(name);schema.setRequired(List.copyOf(schema.getProperties().keySet()));schema.setAdditionalProperties(false);
                schema.addProperty("schemaVersion",new IntegerSchema()._enum(List.of(2)).description("Configuration format version. Other integer versions return UNSUPPORTED_SCHEMA_VERSION."));
            }
            var home=api.getPaths().get("/api/v2/dashboards/home");
            var get=home.getGet().getResponses().get("200").getContent().get("application/json");
            get.addExamples("unsaved",new Example().summary("Virtual defaults; GET does not write").value(HomeDashboardResponse.from(new HomeDashboardSnapshot(0,HomeDashboard.defaults()))));
            get.addExamples("savedEmpty",new Example().value(new HomeDashboardResponse("home",2,1,List.of())));
            var put=home.getPut();put.getRequestBody().getContent().get("application/json")
                .addExamples("firstSave",new Example().value(Map.of("schemaVersion",2,"revision",0,"widgets",List.of())))
                .addExamples("projectWidget",new Example().value(Map.of("schemaVersion",2,"revision",1,"widgets",List.of(Map.of("id","project-board","type","board","title","Selected project","size","wide","selection",Map.of("kind","project","projectId","00000000-0000-0000-0000-000000000001"),"limit",5)))));
            put.getResponses().get("409").getContent().get("application/json").addExamples("staleFirstSave",new Example().value(Map.of("code","REVISION_CONFLICT","message","Reload the resource before updating","fieldErrors",Map.of(),"requestId","example")));
        };
    }
    private ObjectSchema selection(String kind) {
        var schema=new ObjectSchema();schema.setAdditionalProperties(false);var required=new ArrayList<>(List.of("kind"));schema.addProperty("kind",new StringSchema()._enum(List.of(kind)));
        if(kind.equals("project")||kind.equals("category")){String field=kind+"Id";required.add(field);schema.addProperty(field,new StringSchema().format("uuid"));}
        schema.setRequired(required);return schema;
    }
    private ObjectSchema widget(List<String> types,boolean limit,boolean allOnly,boolean response) {
        var schema=new ObjectSchema();schema.setAdditionalProperties(false);var required=new ArrayList<>(CORE);if(response)required.add("selectionState");schema.setRequired(required);
        schema.addProperty("id",new StringSchema().minLength(1).description("Trimmed case-sensitive ID unique within the array"));schema.addProperty("type",new StringSchema()._enum(types));
        schema.addProperty("title",new StringSchema().minLength(1).maxLength(48));schema.addProperty("size",new StringSchema()._enum(List.of("small","medium","wide")));
        schema.addProperty("selection",new Schema<>().$ref("#/components/schemas/"+(allOnly?"DashboardSelectionAll":"DashboardSelectionDto")));
        if(limit)schema.addProperty("limit",new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(20)));
        if(response)schema.addProperty("selectionState",new StringSchema()._enum(allOnly?List.of("valid"):List.of("valid","missingCategory")).readOnly(true));
        return schema;
    }
}
