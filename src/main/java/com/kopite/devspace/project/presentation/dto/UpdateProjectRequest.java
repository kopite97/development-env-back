package com.kopite.devspace.project.presentation.dto;

import com.kopite.devspace.project.application.command.UpdateProjectCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;

@JsonDeserialize(using = UpdateProjectRequest.Deserializer.class)
@Schema(description = "Only supplied fields change. categoryId omission preserves, null clears, UUID assigns an owned Category; archived Projects are editable. Other explicit nulls and unknown fields are rejected. Revision-only and same-value updates increment revision once.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        example = "{\"revision\":1,\"status\":\"archived\"}")
public record UpdateProjectRequest(
        @NotNull @Min(1) @Max(9007199254740991L) Long revision,
        @ProjectText(max = 100, trim = true) @Schema(minLength=1,maxLength = 100, description = "Nonblank after trim when supplied") String name,
        @Size(max = 4000) String subtitle,
        @ProjectText(max = 200, trim = true) @Schema(minLength=1,maxLength = 200, description = "Nonblank after trim when supplied") String stack,
        @DecimalMin("0") @DecimalMax("100") BigDecimal progress,
        @Size(max = 200) String currentMilestone,
        @ProjectText(max = 2000, trim = true) @Schema(maxLength = 2000) String repositoryUrl,
        @Pattern(regexp = "active|archived") @Schema(allowableValues = {"active", "archived"}) String status,
        @Schema(types={"string","null"},format="uuid") String categoryId,
        @com.fasterxml.jackson.annotation.JsonIgnore @Schema(hidden=true) boolean categoryIdPresent) {
    public UpdateProjectCommand command() {
        return new UpdateProjectCommand(revision, name, subtitle, stack, progress, currentMilestone, repositoryUrl, status,
            new com.kopite.devspace.project.application.command.ProjectCategorySelection(categoryIdPresent,categoryId));
    }

    public static class Deserializer extends ValueDeserializer<UpdateProjectRequest> {
        @Override public UpdateProjectRequest deserialize(JsonParser parser, DeserializationContext context) {
            var fields = ProjectRequestFields.read(parser, true);
            return new UpdateProjectRequest((Long) fields.get("revision"), ProjectRequestFields.text(fields, "name"),
                    ProjectRequestFields.text(fields, "subtitle"),
                    ProjectRequestFields.text(fields, "stack"), ProjectRequestFields.progress(fields),
                    ProjectRequestFields.text(fields, "currentMilestone"), ProjectRequestFields.text(fields, "repositoryUrl"),
                    ProjectRequestFields.text(fields, "status"),ProjectRequestFields.text(fields,"categoryId"),fields.containsKey("categoryId"));
        }
    }
}
