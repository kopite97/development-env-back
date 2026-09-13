package com.kopite.devspace.task.presentation.dto;
import com.kopite.devspace.task.application.command.UpdateTaskCommand;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=UpdateTaskRequest.Deserializer.class)
@Schema(description="Only declared fields are writable. Explicit nulls and duplicates are invalid. Lengths use UTF-16 units. Optional text clears with an empty string.",
    additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record UpdateTaskRequest(@NotNull @Min(1) @Max(9007199254740991L) Long revision,
    @TaskText(max=160,required=false,trim=true) @Schema(minLength=1,maxLength=160) String title,
    @Schema(type="string",format="uuid") String projectId,
    @Size(max=10000) @Schema(defaultValue="") String description,
    @Pattern(regexp="todo|doing|done") @Schema(allowableValues={"todo","doing","done"}) String status,
    @Pattern(regexp="normal|high") @Schema(allowableValues={"normal","high"}) String priority,
    @TaskText(max=40,trim=true) @Schema(maxLength=40,defaultValue="") String tag) {
    public UpdateTaskCommand command() {
        return new UpdateTaskCommand(revision,title,TaskRequestFields.uuid(projectId,"projectId"),description,status,priority,tag);
    }
    public static class Deserializer extends ValueDeserializer<UpdateTaskRequest> {
        @Override public UpdateTaskRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=TaskRequestFields.read(parser,true,false);
            return new UpdateTaskRequest((Long)f.get("revision"),TaskRequestFields.text(f,"title"),TaskRequestFields.text(f,"projectId"),
                TaskRequestFields.text(f,"description"),TaskRequestFields.text(f,"status"),TaskRequestFields.text(f,"priority"),TaskRequestFields.text(f,"tag"));
        }
    }
}
