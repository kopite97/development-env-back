package com.kopite.devspace.widget.presentation.dto;
import com.kopite.devspace.widget.application.*;
import com.kopite.devspace.widget.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class WidgetRequests {
    private WidgetRequests(){}
    @JsonDeserialize(using=CreateParser.class)
    @Schema(name="CreateWidgetRequest",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record Create(@NotBlank String type,@NotBlank String title,@Min(1) int configVersion,
        @NotNull @Schema(implementation=LocalWidgetConfig.class) JsonNode config,@com.fasterxml.jackson.annotation.JsonIgnore String hash){}
    @JsonDeserialize(using=UpdateParser.class)
    @Schema(name="UpdateWidgetRequest",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record Update(@Min(1) @Max(9007199254740991L) long revision,@NotBlank String title,@Min(1) int configVersion,
        @NotNull @Schema(implementation=LocalWidgetConfig.class) JsonNode config){}
    // Track duplicate fields before constructing a tree, including nested configuration objects.
    public static JsonNode strictTree(JsonParser p){var n=node(p);if(p.nextToken()!=null)throw WidgetException.invalid("body");return n;}
    private static JsonNode node(JsonParser p) {
        if(p.currentToken()==tools.jackson.core.JsonToken.START_OBJECT){var n=WidgetJson.MAPPER.createObjectNode();var seen=new java.util.HashSet<String>();
            while(p.nextToken()!=tools.jackson.core.JsonToken.END_OBJECT){if(p.currentToken()!=tools.jackson.core.JsonToken.PROPERTY_NAME)throw WidgetException.invalid("body");String key=p.currentName();if(!seen.add(key))throw WidgetException.invalid(key);p.nextToken();n.set(key,node(p));}return n;}
        if(p.currentToken()==tools.jackson.core.JsonToken.START_ARRAY){var n=WidgetJson.MAPPER.createArrayNode();while(p.nextToken()!=tools.jackson.core.JsonToken.END_ARRAY){if(p.currentToken()==null)throw WidgetException.invalid("body");n.add(node(p));}return n;}
        var f=WidgetJson.MAPPER.getNodeFactory();
        if(p.currentToken()==null)throw WidgetException.invalid("body");
        return switch(p.currentToken()) {
            case VALUE_STRING -> f.stringNode(p.getString());
            case VALUE_NUMBER_INT -> f.numberNode(p.getBigIntegerValue());
            case VALUE_NUMBER_FLOAT -> f.numberNode(p.getDecimalValue());
            case VALUE_TRUE -> f.booleanNode(true);
            case VALUE_FALSE -> f.booleanNode(false);
            case VALUE_NULL -> f.nullNode();
            default -> throw WidgetException.invalid("body");
        };
    }
    public static class CreateParser extends ValueDeserializer<Create> {
        public Create deserialize(JsonParser p,DeserializationContext c){var n=strictTree(p);WidgetJson.fields(n,"type","title","configVersion","config");
            var config=n.get("config");if(config==null||!config.isObject())throw WidgetException.invalid("config");
            return new Create(WidgetJson.text(n,"type"),WidgetJson.text(n,"title"),(int)WidgetJson.integer(n,"configVersion",1,Integer.MAX_VALUE),config,WidgetRequestHash.of(WidgetReplay.CREATE,n.toString()));}
    }
    public static class UpdateParser extends ValueDeserializer<Update> {
        public Update deserialize(JsonParser p,DeserializationContext c){var n=strictTree(p);WidgetJson.fields(n,"revision","title","configVersion","config");
            var config=n.get("config");if(config==null||!config.isObject())throw WidgetException.invalid("config");
            return new Update(WidgetJson.integer(n,"revision",1,Widget.MAX_REVISION),WidgetJson.text(n,"title"),(int)WidgetJson.integer(n,"configVersion",1,Integer.MAX_VALUE),config);}
    }
}
