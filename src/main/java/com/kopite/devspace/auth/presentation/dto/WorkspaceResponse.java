package com.kopite.devspace.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(requiredProperties = {"id", "name", "revision"})
public record WorkspaceResponse(
        @Schema(format = "uuid", accessMode = Schema.AccessMode.READ_ONLY) UUID id,
        @Schema(minLength = 1, maxLength = 100) String name,
        @Schema(minimum = "1", accessMode = Schema.AccessMode.READ_ONLY) int revision
) {
}
