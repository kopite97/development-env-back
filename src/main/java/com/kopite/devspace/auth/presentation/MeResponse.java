package com.kopite.devspace.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record MeResponse(
        @Schema(format = "uuid") UUID id,
        String displayName,
        WorkspaceResponse workspace
) {
}
