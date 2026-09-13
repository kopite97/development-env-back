package com.kopite.devspace.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"csrfToken"})
public record CsrfTokenResponse(
        @Schema(description = "Token to send in the X-CSRF-Token header")
        String csrfToken
) {
}
