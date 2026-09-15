package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.application.command.CreateLinkCommand;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=CreateLinkRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="Only supplied fields change. projectId omission preserves, null clears, UUID assigns an owned active or archived Project. Other null, unknown and duplicate fields reject. URLs must have an http/https host and no credentials; strings use UTF-16 lengths.")
public record CreateLinkRequest(
    @LinkText(max=100,trim=true,required=true) @Schema(minLength=1,maxLength=100,requiredMode=Schema.RequiredMode.REQUIRED) String label,
    @Size(max=300) @Schema(maxLength=300,defaultValue="") String description,
    @LinkText(max=2000,trim=true,required=true) @Schema(minLength=1,maxLength=2000,format="uri",requiredMode=Schema.RequiredMode.REQUIRED) String url,
    @Schema(types={"string","null"},format="uuid") String projectId,
    @com.fasterxml.jackson.annotation.JsonIgnore @Schema(hidden=true) boolean projectIdPresent) {
    public CreateLinkCommand command() { return new CreateLinkCommand(label,description,url,new com.kopite.devspace.link.application.command.LinkProjectSelection(projectIdPresent,projectId)); }
    public static class Deserializer extends ValueDeserializer<CreateLinkRequest> {
        public CreateLinkRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=LinkRequestFields.read(parser,false);
            return new CreateLinkRequest(f.get("label"),f.get("description"),f.get("url"),f.get("projectId"),f.containsKey("projectId"));
        }
    }
}
