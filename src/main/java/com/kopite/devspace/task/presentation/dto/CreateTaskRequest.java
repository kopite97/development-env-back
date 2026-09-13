package com.kopite.devspace.task.presentation.dto;
import com.kopite.devspace.task.application.command.CreateTaskCommand;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=CreateTaskRequest.Deserializer.class)
@Schema(description="Only declared fields are writable. Explicit nulls and duplicates are invalid. Lengths use UTF-16 units. Optional text clears with an empty string.",
    additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record CreateTaskRequest(
    @TaskText(max=160,required=true,trim=true) @Schema(maxLength=160,requiredMode=Schema.RequiredMode.REQUIRED) String title,
    @NotNull @Schema(type="string",format="uuid",requiredMode=Schema.RequiredMode.REQUIRED) String projectId,
    @Size(max=10000) @Schema(defaultValue="") String description,
    @Pattern(regexp="todo|doing|done") @Schema(allowableValues={"todo","doing","done"},defaultValue="todo") String status,
    @Pattern(regexp="normal|high") @Schema(allowableValues={"normal","high"},defaultValue="normal") String priority,
    @TaskText(max=40,trim=true) @Schema(maxLength=40,defaultValue="") String tag) {
    public CreateTaskCommand command() {
        return new CreateTaskCommand(title,TaskRequestFields.uuid(projectId,"projectId"),description,status,priority,tag);
    }
    public static class Deserializer extends ValueDeserializer<CreateTaskRequest> {
        @Override public CreateTaskRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=TaskRequestFields.read(parser,false,false);
            return new CreateTaskRequest(TaskRequestFields.text(f,"title"),TaskRequestFields.text(f,"projectId"),
                TaskRequestFields.text(f,"description"),TaskRequestFields.text(f,"status"),TaskRequestFields.text(f,"priority"),TaskRequestFields.text(f,"tag"));
        }
    }
}
