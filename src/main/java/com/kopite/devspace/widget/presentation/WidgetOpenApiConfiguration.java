package com.kopite.devspace.widget.presentation;
import com.kopite.devspace.widget.application.WidgetTypeRegistry;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@Configuration(proxyBeanMethods=false) @RequiredArgsConstructor
public class WidgetOpenApiConfiguration {
    private final WidgetTypeRegistry registry;
    @Bean OpenApiCustomizer widgetContracts(){return api->{
        var schemas=api.getComponents().getSchemas();
        for(String name:List.of("CreateWidgetRequest","UpdateWidgetRequest","SaveHomeLayoutRequest","InitializeHomeLayoutRequest","WidgetSnapshot","HomeLayoutSnapshot","WidgetConfigurationPage","PlacementInput","Placement","DeletedWidget","WidgetDataEnvelope","WidgetDataPage","WidgetDataProblem","OverviewWidgetData","BoardWidgetData","JournalWidgetData","MilestoneWidgetData","LinksWidgetData","WidgetTaskStatistics","WidgetTaskColumn","TaskSnapshot","JournalSnapshot","MilestoneSnapshot","LinkSnapshot","ProjectCategoryCount","Definition")) {
            var s=schemas.get(name);if(s!=null&&s.getProperties()!=null){s.setRequired(List.copyOf(s.getProperties().keySet()));s.setAdditionalProperties(false);}
        }
        // OpenAPI 3.1 $ref siblings are intersections. A nullable object must be a union,
        // not type:null combined with an object $ref (which would accept neither value).
        Schema<?> envelope=schemas.get("WidgetDataEnvelope");
        if(envelope!=null)for(var entry:Map.of("data","WidgetPayload","page","WidgetDataPage","problem","WidgetDataProblem").entrySet()) {
            var nullValue=new Schema<>();nullValue.setTypes(Set.of("null"));
            envelope.addProperty(entry.getKey(),new ComposedSchema().anyOf(List.of(new Schema<>().$ref("#/components/schemas/"+entry.getValue()),nullValue)));
        }
        Schema<?> payload=schemas.get("WidgetPayload");if(payload!=null){var mapping=new LinkedHashMap<String,String>();
            for(String type:List.of("overview","board","journal","milestone","links"))mapping.put(type,"#/components/schemas/"+Character.toUpperCase(type.charAt(0))+type.substring(1)+"WidgetData");
            payload.setDiscriminator(new Discriminator().propertyName("kind").mapping(mapping));
        }
        for(String name:List.of("TaskSnapshot","JournalSnapshot","MilestoneSnapshot","LinkSnapshot","ProjectCategoryCount")) {
            Schema<?> s=schemas.get(name);if(s==null)continue;
            for(String field:List.of("categoryId","deletedAt","dueDate"))if(s.getProperties().containsKey(field))s.getProperties().get(field).setTypes(Set.of("string","null"));
            if(name.equals("LinkSnapshot"))for(String field:List.of("projectId","projectName"))s.getProperties().get(field).setTypes(Set.of("string","null"));
            if(s.getProperties().containsKey("revision")){var revision=s.getProperties().get("revision");revision.setMinimum(BigDecimal.ONE);revision.setMaximum(BigDecimal.valueOf(9007199254740991L));revision.setReadOnly(true);}
        }
        Schema<?> definition=schemas.get("Definition");if(definition!=null)definition.addProperty("configSchema",new ObjectSchema().additionalProperties(true).description("JSON Schema for the selected configuration version"));
        var selection=schemas.get("WidgetSelection");if(selection!=null){var shapes=new ArrayList<Schema>();
            for(String kind:List.of("all","uncategorized","project","category")){var shape=new ObjectSchema();shape.setAdditionalProperties(false);var required=new ArrayList<>(List.of("kind"));shape.addProperty("kind",new StringSchema()._enum(List.of(kind)));
                if(kind.equals("project")||kind.equals("category")){required.add(kind+"Id");shape.addProperty(kind+"Id",new StringSchema().format("uuid"));}shape.setRequired(required);shapes.add(shape);}
            selection.setOneOf(shapes);
        }
        for(String name:List.of("WidgetSnapshot","HomeLayoutSnapshot")) {
            Schema<?> s=schemas.get(name);if(s==null)continue;
            String field=name.equals("WidgetSnapshot")?"revision":"layoutRevision";
            var revision=s.getProperties().get(field);revision.setMinimum(BigDecimal.valueOf(field.equals("revision")?1:0));revision.setMaximum(BigDecimal.valueOf(9007199254740991L));revision.setReadOnly(true);
        }
        Schema<?> config=schemas.get("LocalWidgetConfig");if(config!=null){config.setRequired(List.of("selection"));config.setAdditionalProperties(false);config.getProperties().get("limit").setMinimum(BigDecimal.ONE);config.getProperties().get("limit").setMaximum(BigDecimal.valueOf(20));}
        var create=schemas.get("CreateWidgetRequest");if(create!=null){var alternatives=new ArrayList<Schema>();
            for(var d:registry.all()) {try {
                Schema<?> c=io.swagger.v3.core.util.Json.mapper().readValue(d.configSchema(),Schema.class);schemas.put("WidgetConfig_"+d.type(),c);
                var shape=new ObjectSchema();shape.setAdditionalProperties(false);shape.setRequired(List.of("type","title","configVersion","config"));shape.addProperty("type",new StringSchema()._enum(List.of(d.type())));shape.addProperty("title",new StringSchema().description("Trimmed length 1-48 UTF-16 units"));shape.addProperty("configVersion",new IntegerSchema()._enum(List.of(d.configVersion())));shape.addProperty("config",new Schema<>().$ref("#/components/schemas/WidgetConfig_"+d.type()));alternatives.add(shape);
            }catch(java.io.IOException e){throw new IllegalStateException("Invalid Widget schema",e);}}
            create.setOneOf(alternatives);
        }
        api.getPaths().forEach((path,item)->{
            if(!path.startsWith("/api/v1/widgets")&&!path.equals("/api/v1/widget-types")&&!path.startsWith("/api/v3/dashboards/")&&!path.equals("/api/v2/dashboards/home"))return;
            item.readOperationsMap().forEach((method,op)->{
                boolean mutation=Set.of("POST","PUT","DELETE").contains(method.name());
                if(mutation)op.addParametersItem(new Parameter().name("X-CSRF-Token").in("header").required(true).schema(new StringSchema()).description("Session CSRF token; same-origin mutation required"));
                for(String status:List.of("400","401","403","404","409","500"))if(!op.getResponses().containsKey(status))op.getResponses().addApiResponse(status,new ApiResponse().description(status.equals("500")?"INTERNAL_ERROR":"See Widget contract error rules"));
                if(!mutation)op.getResponses().remove("409");
                op.getResponses().get("400").setDescription(!mutation&&(path.equals("/api/v1/widgets")||path.endsWith("/data"))?"VALIDATION_ERROR or INVALID_CURSOR":"VALIDATION_ERROR");
                if(method.name().equals("DELETE"))op.setRequestBody(null);
                if(op.getParameters()!=null)for(var p:op.getParameters()) {
                    switch(p.getName()) {
                        case "Idempotency-Key" -> p.setSchema(new StringSchema().minLength(1).maxLength(128).pattern("^[!-~]+$"));
                        case "revision" -> p.setSchema(new IntegerSchema().format("int64").minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(9007199254740991L)));
                        case "limit" -> p.setSchema(new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(100))._default(20));
                        case "unplaced" -> p.setSchema(new BooleanSchema()._default(false));
                    }
                }
                op.getResponses().forEach((status,response)->{
                    response.addHeaderObject("Cache-Control",new Header().schema(new StringSchema()._enum(List.of("no-store"))));
                    if(status.startsWith("4")||status.startsWith("5"))response.setContent(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
                    if(status.equals("201"))response.addHeaderObject("Location",new Header().description("Original created resource location, preserved on historical replay").schema(new StringSchema()));
                });
            });
        });
        var widgets=api.getPaths().get("/api/v1/widgets");if(widgets!=null)widgets.getPost().getRequestBody().getContent().get("application/json").addExamples("board",new Example().value(Map.of("type","board","title","Tasks","configVersion",1,"config",Map.of("selection",Map.of("kind","all"),"limit",10))));
        var home=api.getPaths().get("/api/v3/dashboards/home");if(home!=null){home.getPut().getRequestBody().getContent().get("application/json").addExamples("explicitEmpty",new Example().value(Map.of("schemaVersion",3,"layoutRevision",0,"placements",List.of())));
            home.getGet().getResponses().get("200").getContent().get("application/json").addExamples("unsaved",new Example().value(Map.of("id","home","schemaVersion",3,"initialized",false,"layoutRevision",0,"placements",List.of(),"widgets",List.of())));}
    };}
}
