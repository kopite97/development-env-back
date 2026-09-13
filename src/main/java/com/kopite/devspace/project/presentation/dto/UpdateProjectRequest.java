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
@Schema(description = "Only supplied fields change. Every explicit null and unknown field is rejected. Revision-only and same-value updates increment revision once.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        example = "{\"revision\":1,\"status\":\"archived\"}")
public record UpdateProjectRequest(
        @NotNull @Min(1) @Max(9007199254740991L) Long revision,
        @ProjectText(max = 100, trim = true) @Schema(maxLength = 100, description = "Nonblank after trim when supplied") String name,
        @Size(max = 4000) String subtitle,
        @Pattern(regexp = "unity|server") @Schema(allowableValues = {"unity", "server"}) String scope,
        @ProjectText(max = 200, trim = true) @Schema(maxLength = 200, description = "Nonblank after trim when supplied") String stack,
        @DecimalMin("0") @DecimalMax("100") BigDecimal progress,
        @Size(max = 200) String currentMilestone,
        @ProjectText(max = 2000, trim = true) @Schema(maxLength = 2000) String repositoryUrl,
        @Pattern(regexp = "active|archived") @Schema(allowableValues = {"active", "archived"}) String status) {
    public UpdateProjectCommand command() {
        return new UpdateProjectCommand(revision, name, subtitle, scope, stack, progress, currentMilestone, repositoryUrl, status);
    }

    public static class Deserializer extends ValueDeserializer<UpdateProjectRequest> {
        @Override public UpdateProjectRequest deserialize(JsonParser parser, DeserializationContext context) {
            var fields = ProjectRequestFields.read(parser, true);
            return new UpdateProjectRequest((Long) fields.get("revision"), ProjectRequestFields.text(fields, "name"),
                    ProjectRequestFields.text(fields, "subtitle"), ProjectRequestFields.text(fields, "scope"),
                    ProjectRequestFields.text(fields, "stack"), ProjectRequestFields.progress(fields),
                    ProjectRequestFields.text(fields, "currentMilestone"), ProjectRequestFields.text(fields, "repositoryUrl"),
                    ProjectRequestFields.text(fields, "status"));
        }
    }
}
