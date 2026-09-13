package com.kopite.devspace.auth.presentation.controller;
import com.kopite.devspace.auth.presentation.dto.CsrfTokenResponse;

import com.kopite.devspace.auth.infrastructure.oidc.SafeReturnToPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.kopite.devspace.global.response.ApiError;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final SafeReturnToPolicy safeReturnToPolicy;

    @GetMapping("/login")
    @Operation(summary = "Start the configured Google OIDC login")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to /oauth2/authorization/google, which then redirects to the provider"),
            @ApiResponse(responseCode = "401", description = "AUTH_REQUIRED: invalid or removed existing session user", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED: existing session user is disabled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> login(
            @Parameter(in = ParameterIn.QUERY, description = "Relative application path after login")
            @RequestParam(value = "returnTo", required = false) String returnTo
    ) {
        String safeReturnTo = safeReturnToPolicy.normalize(returnTo);
        String location = UriComponentsBuilder
                .fromPath("/oauth2/authorization/google")
                .queryParam("returnTo", safeReturnTo)
                .build()
                .encode()
                .toUriString();
        return ResponseEntity.status(HttpServletResponse.SC_FOUND)
                .location(URI.create(location))
                .build();
    }

    @GetMapping("/csrf")
    @SecurityRequirement(name = "sessionCookie")
    @Operation(summary = "Get the CSRF token for the authenticated browser session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session-bound CSRF token", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CsrfTokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CsrfTokenResponse> csrf(@Parameter(hidden = true) CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new CsrfTokenResponse(token.getToken()));
    }
}
