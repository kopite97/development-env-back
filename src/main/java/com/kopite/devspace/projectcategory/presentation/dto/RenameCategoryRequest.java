package com.kopite.devspace.projectcategory.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=RenameCategoryRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="Both fields required. Same normalized name advances revision; revision-only PATCH is invalid. Name uses trim/NFC and case-sensitive workspace uniqueness.")
public record RenameCategoryRequest(@NotNull @Min(1) @Max(9007199254740991L) Long revision,
    @CategoryText @Schema(minLength=1,maxLength=100,requiredMode=Schema.RequiredMode.REQUIRED) String name) {
    public static class Deserializer extends ValueDeserializer<RenameCategoryRequest> {
        public RenameCategoryRequest deserialize(JsonParser p,DeserializationContext c){var f=CategoryRequestFields.read(p,true);return new RenameCategoryRequest((Long)f.get("revision"),(String)f.get("name"));}
    }
}
