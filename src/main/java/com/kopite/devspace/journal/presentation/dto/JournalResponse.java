package com.kopite.devspace.journal.presentation.dto;
import com.kopite.devspace.journal.application.model.JournalSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record JournalResponse(
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) UUID id,
    @Schema(minimum="1",maximum="9007199254740991") long revision,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant createdAt,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant updatedAt,
    @Schema(maxLength=120) String title, UUID projectId,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) String projectName,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,allowableValues={"unity","server"}) String scope,
    @Schema(maxLength=20000) String body,
    @Schema(type="string",format="date") LocalDate entryDate) {
    public static JournalResponse from(JournalSnapshot j) {
        return new JournalResponse(j.id(),j.revision(),j.createdAt(),j.updatedAt(),j.title(),j.projectId(),j.projectName(),j.scope(),j.body(),j.entryDate());
    }
}
