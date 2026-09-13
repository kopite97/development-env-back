package com.kopite.devspace.task.presentation.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using=RestoreTaskRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record RestoreTaskRequest(@NotNull @Min(1) @Max(9007199254740991L) Long revision) {
    public static class Deserializer extends ValueDeserializer<RestoreTaskRequest> {
        @Override public RestoreTaskRequest deserialize(JsonParser parser,DeserializationContext context) {
            return new RestoreTaskRequest((Long)TaskRequestFields.read(parser,false,true).get("revision"));
        }
    }
}
