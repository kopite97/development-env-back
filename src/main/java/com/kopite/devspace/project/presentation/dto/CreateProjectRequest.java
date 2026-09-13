package com.kopite.devspace.project.presentation.dto;

import com.kopite.devspace.project.application.command.CreateProjectCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;

@JsonDeserialize(using = CreateProjectRequest.Deserializer.class)
@Schema(description = "Unknown fields and explicit nulls are rejected. Omitted optional strings default to empty; progress defaults to zero. String limits use UTF-16 code units after applicable trimming.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        example = "{\"name\":\"Example Project\",\"scope\":\"server\",\"stack\":\"Java\",\"progress\":12.5}")
public record CreateProjectRequest(
        @ProjectText(max = 100, required = true, trim = true) @Schema(minLength=1,maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Size(max = 4000) @Schema(defaultValue = "") String subtitle,
        @NotNull @Pattern(regexp = "unity|server") @Schema(allowableValues = {"unity", "server"}) String scope,
        @ProjectText(max = 200, required = true, trim = true) @Schema(minLength=1,maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED) String stack,
        @DecimalMin("0") @DecimalMax("100") @Schema(defaultValue = "0", description = "Manual progress; decimals allowed") BigDecimal progress,
        @Size(max = 200) @Schema(defaultValue = "", description = "Goal memo, independent of milestone resources") String currentMilestone,
        @ProjectText(max = 2000, trim = true) @Schema(maxLength = 2000, defaultValue = "", description = "Empty or absolute http/https URL with a host") String repositoryUrl) {
    public CreateProjectCommand command() {
        return new CreateProjectCommand(name, subtitle, scope, stack, progress, currentMilestone, repositoryUrl);
    }

    public static class Deserializer extends ValueDeserializer<CreateProjectRequest> {
        @Override public CreateProjectRequest deserialize(JsonParser parser, DeserializationContext context) {
            var fields = ProjectRequestFields.read(parser, false);
            return new CreateProjectRequest(ProjectRequestFields.text(fields, "name"), ProjectRequestFields.text(fields, "subtitle"),
                    ProjectRequestFields.text(fields, "scope"), ProjectRequestFields.text(fields, "stack"), ProjectRequestFields.progress(fields),
                    ProjectRequestFields.text(fields, "currentMilestone"), ProjectRequestFields.text(fields, "repositoryUrl"));
        }
    }
}
