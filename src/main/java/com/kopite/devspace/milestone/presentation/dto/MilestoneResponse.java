package com.kopite.devspace.milestone.presentation.dto;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record MilestoneResponse(
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) UUID id,
    @Schema(minimum="1",maximum="9007199254740991") long revision,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant createdAt,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant updatedAt,
    @Schema(maxLength=200) String title, UUID projectId,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) String projectName,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,allowableValues={"unity","server"}) String scope,
    @Schema(types={"string","null"},format="date") LocalDate dueDate,
    boolean completed) {
    public static MilestoneResponse from(MilestoneSnapshot j) {
        return new MilestoneResponse(j.id(),j.revision(),j.createdAt(),j.updatedAt(),j.title(),j.projectId(),j.projectName(),j.scope(),j.dueDate(),j.completed());
    }
}
