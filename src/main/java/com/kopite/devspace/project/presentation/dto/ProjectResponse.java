package com.kopite.devspace.project.presentation.dto;

import com.kopite.devspace.project.application.model.ProjectSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "revision", "createdAt", "updatedAt", "name", "subtitle", "scope", "stack", "progress", "currentMilestone", "repositoryUrl", "status", "colorToken"})
public record ProjectResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID id,
        @Schema(minimum = "1", maximum = "9007199254740991", accessMode = Schema.AccessMode.READ_ONLY) long revision,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant createdAt,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant updatedAt,
        @Schema(maxLength = 100) String name, @Schema(maxLength = 4000) String subtitle,
        @Schema(allowableValues = {"unity", "server"}) String scope, @Schema(maxLength = 200) String stack,
        @Schema(minimum = "0", maximum = "100") BigDecimal progress,
        @Schema(maxLength = 200) String currentMilestone, @Schema(maxLength = 2000) String repositoryUrl,
        @Schema(allowableValues = {"active", "archived"}) String status,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY, allowableValues = {"unity", "server"}) String colorToken) {
    public static ProjectResponse from(ProjectSnapshot p) {
        return new ProjectResponse(p.id(), p.revision(), p.createdAt(), p.updatedAt(), p.name(), p.subtitle(), p.scope(),
                p.stack(), p.progress(), p.currentMilestone(), p.repositoryUrl(), p.status(), p.colorToken());
    }
}
