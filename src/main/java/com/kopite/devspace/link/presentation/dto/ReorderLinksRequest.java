package com.kopite.devspace.link.presentation.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.*;

@JsonDeserialize(using=ReorderLinksRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record ReorderLinksRequest(
    @NotNull @Min(0) @Max(9007199254740991L) Long collectionRevision,
    @NotNull List<@NotNull UUID> ids) {
    public static class Deserializer extends ValueDeserializer<ReorderLinksRequest> {
        public ReorderLinksRequest deserialize(JsonParser p,DeserializationContext context) {
            if(p.currentToken()!=JsonToken.START_OBJECT)throw LinkRequestFields.invalid("body");
            Set<String> seen=new HashSet<>();Long revision=null;List<UUID> ids=null;
            while(p.nextToken()!=JsonToken.END_OBJECT) {
                if(p.currentToken()!=JsonToken.PROPERTY_NAME)throw LinkRequestFields.invalid("body");
                String name=p.currentName();var token=p.nextToken();if(!seen.add(name))throw LinkRequestFields.invalid(name);
                if(name.equals("collectionRevision")) {
                    if(token!=JsonToken.VALUE_NUMBER_INT)throw LinkRequestFields.invalid(name);revision=p.getLongValue();
                } else if(name.equals("ids")) {
                    if(token!=JsonToken.START_ARRAY)throw LinkRequestFields.invalid(name);ids=new ArrayList<>();
                    while(p.nextToken()!=JsonToken.END_ARRAY) {
                        if(p.currentToken()!=JsonToken.VALUE_STRING)throw LinkRequestFields.invalid(name);
                        ids.add(LinkRequestFields.uuid(p.getString(),name));
                    }
                } else throw LinkRequestFields.invalid(name);
            }
            return new ReorderLinksRequest(revision,ids==null?null:List.copyOf(ids));
        }
    }
}
