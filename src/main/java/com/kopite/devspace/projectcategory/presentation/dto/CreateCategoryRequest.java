package com.kopite.devspace.projectcategory.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=CreateCategoryRequest.Deserializer.class)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="Name: Java trim then NFC; case-sensitive workspace uniqueness; at most 100 normalized UTF-16 units. Unknown/duplicate/null fields rejected.")
public record CreateCategoryRequest(@CategoryText @Schema(minLength=1,maxLength=100,requiredMode=Schema.RequiredMode.REQUIRED) String name) {
    public static class Deserializer extends ValueDeserializer<CreateCategoryRequest> {
        public CreateCategoryRequest deserialize(JsonParser p,DeserializationContext c){return new CreateCategoryRequest((String)CategoryRequestFields.read(p,false).get("name"));}
    }
}
