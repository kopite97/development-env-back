package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigInteger;
import java.util.*;

@JsonDeserialize(using=SaveHomeDashboardRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="Atomic full replacement; all fields required. First save compares 0 and returns 1; stale first or later saves return 409. Same-value saves increment revision. No Idempotency-Key. Empty widgets allowed; IDs unique after trim.")
public record SaveHomeDashboardRequest(
    @NotNull @Schema(allowableValues={"1"}) Integer schemaVersion,
    @NotNull @Min(0) @Max(9007199254740991L) Long revision,
    @NotNull @Valid List<@NotNull DashboardWidgetRequest> widgets) {
    public List<DashboardWidget> values() {return widgets.stream().map(DashboardWidgetRequest::value).toList();}
    public static class Deserializer extends ValueDeserializer<SaveHomeDashboardRequest> {
        public SaveHomeDashboardRequest deserialize(JsonParser p,DeserializationContext context) {
            object(p,"body");Set<String> seen=new HashSet<>();Long revision=null;Integer schema=null;List<DashboardWidgetRequest> widgets=null;
            while(p.nextToken()!=JsonToken.END_OBJECT) {
                String field=field(p,seen,"body");var token=p.nextToken();
                switch(field) {
                    case "schemaVersion" -> {if(token!=JsonToken.VALUE_NUMBER_INT)throw invalid(field);
                        if(!p.getBigIntegerValue().equals(BigInteger.ONE))throw new UnsupportedDashboardSchemaException();schema=1;}
                    case "revision" -> {revision=integer(p,field);HomeDashboard.validateRevision(revision);}
                    case "widgets" -> {
                        if(token!=JsonToken.START_ARRAY)throw invalid(field);widgets=new ArrayList<>();
                        while(p.nextToken()!=JsonToken.END_ARRAY) widgets.add(widget(p,"widgets["+widgets.size()+"]"));
                    }
                    default -> throw invalid(field);
                }
            }
            if(schema==null)throw invalid("schemaVersion");if(revision==null)throw invalid("revision");if(widgets==null)throw invalid("widgets");
            HomeDashboard.validate(widgets.stream().map(DashboardWidgetRequest::value).toList());
            return new SaveHomeDashboardRequest(schema,revision,List.copyOf(widgets));
        }
        private DashboardWidgetRequest widget(JsonParser p,String path) {
            object(p,path);Set<String> seen=new HashSet<>();Map<String,String> strings=new HashMap<>();Integer limit=null;
            while(p.nextToken()!=JsonToken.END_OBJECT) {
                String field=field(p,seen,path);var token=p.nextToken();
                if(Set.of("id","type","title","scope","size","projectId").contains(field)) {
                    if(token!=JsonToken.VALUE_STRING)throw invalid(path+"."+field);strings.put(field,p.getString());
                } else if(field.equals("limit")) {
                    long n=integer(p,path+".limit");if(n<1||n>20)throw invalid(path+".limit");limit=(int)n;
                } else throw invalid(path+"."+field);
            }
            try {
                UUID project=strings.containsKey("projectId")?uuid(strings.get("projectId"),"projectId"):null;
                return DashboardWidgetRequest.from(new DashboardWidget(strings.get("id"),strings.get("type"),strings.get("title"),strings.get("scope"),strings.get("size"),project,limit));
            } catch(DashboardValidationException ex) {throw new DashboardValidationException(path+"."+ex.getField(),ex.getMessage());}
        }
        private void object(JsonParser p,String field) {if(p.currentToken()!=JsonToken.START_OBJECT)throw invalid(field);}
        private String field(JsonParser p,Set<String> seen,String path) {
            if(p.currentToken()!=JsonToken.PROPERTY_NAME)throw invalid(path);
            String name=p.currentName();if(!seen.add(name))throw invalid(path+"."+name);return name;
        }
        private long integer(JsonParser p,String field) {
            if(p.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw invalid(field);
            BigInteger value=p.getBigIntegerValue();
            if(value.compareTo(BigInteger.valueOf(Long.MIN_VALUE))<0 || value.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0)throw invalid(field);
            return value.longValueExact();
        }
        private UUID uuid(String value,String field) {
            try {var id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw invalid(field);return id;}
            catch(IllegalArgumentException ex){throw invalid(field);}
        }
    }
    private static DashboardValidationException invalid(String field) {return new DashboardValidationException(field,"invalid, missing, null, duplicate or unsupported field");}
}
