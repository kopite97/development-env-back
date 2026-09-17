package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.application.HomeLayoutCommandService.PlacementInput;
import com.kopite.devspace.widget.application.*;
import com.kopite.devspace.widget.domain.*;
import com.kopite.devspace.widget.presentation.dto.WidgetRequests;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.*;
public final class LayoutRequests {
    private LayoutRequests(){}
    @JsonDeserialize(using=SaveParser.class) @Schema(name="SaveHomeLayoutRequest",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record Save(@Min(3) @Max(3) int schemaVersion,@Min(0) @Max(9007199254740991L) long layoutRevision,@NotNull @Valid List<PlacementInput> placements){}
    @JsonDeserialize(using=InitializeParser.class) @Schema(name="InitializeHomeLayoutRequest",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record Initialize(@Min(3) @Max(3) int schemaVersion,@Min(0) @Max(0) long layoutRevision,@com.fasterxml.jackson.annotation.JsonIgnore String hash){}
    public static class SaveParser extends ValueDeserializer<Save> {
        public Save deserialize(JsonParser p,DeserializationContext c){var n=WidgetRequests.strictTree(p);WidgetJson.fields(n,"schemaVersion","layoutRevision","placements");
            WidgetJson.integer(n,"schemaVersion",3,3);long rev=WidgetJson.integer(n,"layoutRevision",0,Widget.MAX_REVISION);var a=n.get("placements");if(a==null||!a.isArray())throw WidgetException.invalid("placements");
            var items=new ArrayList<PlacementInput>();for(var v:a){WidgetJson.fields(v,"widgetId","size");items.add(new PlacementInput(WidgetJson.uuid(WidgetJson.text(v,"widgetId"),"widgetId"),WidgetJson.text(v,"size")));}
            return new Save(3,rev,List.copyOf(items));}
    }
    public static class InitializeParser extends ValueDeserializer<Initialize> {
        public Initialize deserialize(JsonParser p,DeserializationContext c){var n=WidgetRequests.strictTree(p);WidgetJson.fields(n,"schemaVersion","layoutRevision");WidgetJson.integer(n,"schemaVersion",3,3);WidgetJson.integer(n,"layoutRevision",0,0);return new Initialize(3,0,WidgetRequestHash.of(WidgetReplay.INITIALIZE,n.toString()));}
    }
}
