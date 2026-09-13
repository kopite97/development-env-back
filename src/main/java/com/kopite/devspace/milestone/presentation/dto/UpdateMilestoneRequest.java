package com.kopite.devspace.milestone.presentation.dto;
import com.kopite.devspace.milestone.application.command.UpdateMilestoneCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=UpdateMilestoneRequest.Deserializer.class)
@Schema(description="Omitted fields stay unchanged; revision is required. Explicit null is allowed only for dueDate. Unknown/duplicate fields and type coercion are rejected. Title length uses UTF-16 units.",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record UpdateMilestoneRequest(@NotNull @Min(1) @Max(9007199254740991L) Long revision,
    @MilestoneText(max=200,required=false,trim=true) @Schema(minLength=1,maxLength=200 ) String title,
     @Schema(type="string",format="uuid") String projectId,
    @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}") @Schema(types={"string","null"},format="date",description="Calendar date in years 0001 through 9999; null clears the date") String dueDate,
     Boolean completed,
    @JsonIgnore @Schema(hidden=true) boolean dueDatePresent) {
    public UpdateMilestoneCommand command() {
        return new UpdateMilestoneCommand(revision,title,MilestoneRequestFields.uuid(projectId,"projectId"),dueDate,completed,dueDatePresent);
    }
    public static class Deserializer extends ValueDeserializer<UpdateMilestoneRequest> {
        @Override public UpdateMilestoneRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=MilestoneRequestFields.read(parser,true);
            return new UpdateMilestoneRequest((Long)f.get("revision"),MilestoneRequestFields.text(f,"title"),MilestoneRequestFields.text(f,"projectId"),MilestoneRequestFields.text(f,"dueDate"),(Boolean)f.get("completed"),f.containsKey("dueDate"));
        }
    }
}
