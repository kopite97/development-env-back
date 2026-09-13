package com.kopite.devspace.task.presentation.dto;
import com.kopite.devspace.task.application.model.TaskSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record TaskResponse(
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) UUID id,
    @Schema(minimum="1",maximum="9007199254740991") long revision,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant createdAt,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant updatedAt,
    String title, UUID projectId,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) String projectName,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,allowableValues={"unity","server"}) String scope,
    String description,
    @Schema(allowableValues={"todo","doing","done"}) String status,
    @Schema(allowableValues={"normal","high"}) String priority,
    String tag,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,types={"string","null"},format="date-time") Instant deletedAt) {
    public static TaskResponse from(TaskSnapshot t) {
        return new TaskResponse(t.id(),t.revision(),t.createdAt(),t.updatedAt(),t.title(),t.projectId(),
            t.projectName(),t.scope(),t.description(),t.status(),t.priority(),t.tag(),t.deletedAt());
    }
}
