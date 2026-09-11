package com.kopite.devspace.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

public record CsrfTokenResponse(
        @Schema(description = "Token to send in the X-CSRF-Token header")
        String csrfToken
) {
}
