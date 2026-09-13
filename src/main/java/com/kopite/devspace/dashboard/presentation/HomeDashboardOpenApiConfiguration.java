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
    private static final List<String> CORE=List.of("id","type","title","scope","size");
    @Bean OpenApiCustomizer homeDashboardContractSchemas() {
        return api->{
            var schemas=api.getComponents().getSchemas();
            Schema<?> request=schemas.get("DashboardWidgetRequest");
            if(request==null)return;
            // Conditional object shapes reject projectId/limit for utility widgets.
            var data=widget(List.of("overview","board","journal","milestone"),true);
            var utility=widget(List.of("deploy","links"),false);
            schemas.put("DashboardDataWidget",data);schemas.put("DashboardUtilityWidget",utility);
            for(String name:List.of("DashboardWidgetRequest","DashboardWidgetResponse")) {
                Schema<?> s=schemas.get(name);s.setRequired(CORE);
                s.setAdditionalProperties(false);
                s.getProperties().get("id").setMinLength(1);
                s.getProperties().get("title").setMinLength(1);
                s.setOneOf(List.of(new Schema<Object>().$ref("#/components/schemas/DashboardDataWidget"),new Schema<Object>().$ref("#/components/schemas/DashboardUtilityWidget")));
                s.addProperty("projectId",new StringSchema().format("uuid").description("Optional, never null. Owned active or archived Project; save normalizes scope to all."));
                s.addProperty("limit",new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(20)).description("Optional, never null. No default is inserted."));
            }
            for(String name:List.of("SaveHomeDashboardRequest","HomeDashboardResponse")) {
                Schema<?> s=schemas.get(name);s.setRequired(List.copyOf(s.getProperties().keySet()));s.setAdditionalProperties(false);
                s.addProperty("schemaVersion",new IntegerSchema()._enum(List.of(1)).description("Configuration format version; unsupported integer versions return UNSUPPORTED_SCHEMA_VERSION."));
            }
            var home=api.getPaths().get("/api/v1/dashboards/home");
            var get=home.getGet().getResponses().get("200").getContent().get("application/json");
            get.addExamples("unsaved",new Example().summary("Virtual defaults; GET does not write").value(HomeDashboardResponse.from(new HomeDashboardSnapshot(0,HomeDashboard.defaults()))));
            get.addExamples("savedEmpty",new Example().value(new HomeDashboardResponse("home",1,1,List.of())));
            var put=home.getPut();
            put.getRequestBody().getContent().get("application/json")
                .addExamples("firstSave",new Example().value(Map.of("schemaVersion",1,"revision",0,"widgets",List.of())))
                .addExamples("projectWidget",new Example().value(Map.of("schemaVersion",1,"revision",1,"widgets",List.of(Map.of("id","project-board","type","board","title","Selected project","size","wide","scope","unity","projectId","00000000-0000-0000-0000-000000000001","limit",5)))));
            put.getResponses().get("409").getContent().get("application/json").addExamples("staleFirstSave",new Example().summary("Another first PUT has already saved revision 1").value(Map.of("code","REVISION_CONFLICT","message","Reload the resource before updating","fieldErrors",Map.of(),"requestId","example")));
        };
    }
    private ObjectSchema widget(List<String> types,boolean settings) {
        var s=new ObjectSchema();s.setAdditionalProperties(false);s.setRequired(CORE);
        s.addProperty("id",new StringSchema().minLength(1).description("Trimmed, case-sensitive, unique ID within the array; same type may repeat."));
        s.addProperty("type",new StringSchema()._enum(types));
        s.addProperty("title",new StringSchema().minLength(1).maxLength(48).description("Trimmed 1..48 UTF-16 code units"));
        s.addProperty("scope",new StringSchema()._enum(List.of("all","unity","server")));
        s.addProperty("size",new StringSchema()._enum(List.of("small","medium","wide")));
        if(settings) {
            s.addProperty("projectId",new StringSchema().format("uuid"));
            s.addProperty("limit",new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(20)));
        }
        return s;
    }
}
