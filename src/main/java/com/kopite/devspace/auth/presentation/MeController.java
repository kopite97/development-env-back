package com.kopite.devspace.auth.presentation;

import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Current user")
public class MeController {

    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    @SecurityRequirement(name = "sessionCookie")
    @Operation(summary = "Get the authenticated user and personal workspace")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated user and workspace"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "The user account is disabled")
    })
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        InternalUserPrincipal principal = (InternalUserPrincipal) authentication.getPrincipal();
        UserWorkspaceCreationResult result = currentUserService.resolve(principal.userId());
        MeResponse response = new MeResponse(
                result.user().getId(),
                result.user().getDisplayName(),
                new WorkspaceResponse(
                        result.workspace().getId(),
                        result.workspace().getName(),
                        result.workspace().getRevision()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }
}
