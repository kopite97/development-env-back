package com.kopite.devspace.milestone.presentation.dto;
import com.kopite.devspace.milestone.application.command.CreateMilestoneCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=CreateMilestoneRequest.Deserializer.class)
@Schema(description="Omitted dueDate defaults to null; omitted completed defaults to false. Explicit null is allowed only for dueDate. Unknown/duplicate fields and type coercion are rejected. Title length uses UTF-16 units.",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record CreateMilestoneRequest(
    @MilestoneText(max=200,required=true,trim=true) @Schema(minLength=1,maxLength=200,requiredMode=Schema.RequiredMode.REQUIRED) String title,
    @NotNull @Schema(type="string",format="uuid") String projectId,
    @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}") @Schema(types={"string","null"},format="date",description="Calendar date in years 0001 through 9999; null clears the date") String dueDate,
    @Schema(defaultValue="false") Boolean completed,
    @JsonIgnore @Schema(hidden=true) boolean dueDatePresent) {
    public CreateMilestoneCommand command() {
        return new CreateMilestoneCommand(title,MilestoneRequestFields.uuid(projectId,"projectId"),dueDate,completed,dueDatePresent);
    }
    public static class Deserializer extends ValueDeserializer<CreateMilestoneRequest> {
        @Override public CreateMilestoneRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=MilestoneRequestFields.read(parser,false);
            return new CreateMilestoneRequest(MilestoneRequestFields.text(f,"title"),MilestoneRequestFields.text(f,"projectId"),MilestoneRequestFields.text(f,"dueDate"),(Boolean)f.get("completed"),f.containsKey("dueDate"));
        }
    }
}
