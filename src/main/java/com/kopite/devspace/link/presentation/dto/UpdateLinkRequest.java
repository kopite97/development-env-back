package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.application.command.UpdateLinkCommand;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=UpdateLinkRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="Only supplied fields change. projectId omission preserves, null clears, UUID assigns an owned active or archived Project. Other null, unknown and duplicate fields reject. URLs must have an http/https host and no credentials; strings use UTF-16 lengths.")
public record UpdateLinkRequest(
    @NotNull @Min(1) @Max(9007199254740991L) Long revision,
    @LinkText(max=100,trim=true) @Schema(minLength=1,maxLength=100) String label,
    @Size(max=300) @Schema(maxLength=300) String description,
    @LinkText(max=2000,trim=true) @Schema(minLength=1,maxLength=2000,format="uri") String url,
    @Schema(types={"string","null"},format="uuid") String projectId,
    @com.fasterxml.jackson.annotation.JsonIgnore @Schema(hidden=true) boolean projectIdPresent) {
    public UpdateLinkCommand command() { return new UpdateLinkCommand(revision,label,description,url,new com.kopite.devspace.link.application.command.LinkProjectSelection(projectIdPresent,projectId)); }
    public static class Deserializer extends ValueDeserializer<UpdateLinkRequest> {
        public UpdateLinkRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=LinkRequestFields.read(parser,true);
            return new UpdateLinkRequest(f.get("revision")==null?null:Long.valueOf(f.get("revision")),f.get("label"),f.get("description"),f.get("url"),f.get("projectId"),f.containsKey("projectId"));
        }
    }
}
