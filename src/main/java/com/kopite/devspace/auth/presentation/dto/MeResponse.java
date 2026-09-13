package com.kopite.devspace.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(requiredProperties = {"id", "displayName", "workspace"})
public record MeResponse(
        @Schema(format = "uuid", accessMode = Schema.AccessMode.READ_ONLY) UUID id,
        String displayName,
        WorkspaceResponse workspace
) {
}
