package com.kopite.devspace.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record WorkspaceResponse(
        @Schema(format = "uuid") UUID id,
        String name,
        int revision
) {
}
