package com.kopite.devspace.auth.presentation;

import com.kopite.devspace.auth.infrastructure.SafeReturnToPolicy;
import io.swagger.v3.oas.annotations.Operation;
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
            @ApiResponse(responseCode = "302", description = "Redirect to the Google authorization endpoint")
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
            @ApiResponse(responseCode = "200", description = "Session-bound CSRF token"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new CsrfTokenResponse(token.getToken()));
    }
}
