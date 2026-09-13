package com.kopite.devspace.dashboard.presentation;
import com.kopite.devspace.dashboard.application.*;
import com.kopite.devspace.dashboard.domain.DashboardValidationException;
import com.kopite.devspace.dashboard.presentation.dto.*;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.global.response.ApiError;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/v1/dashboards/home",produces="application/json")
@RequiredArgsConstructor
@Tag(name="Home Dashboard")
@SecurityRequirement(name="sessionCookie")
@ApiResponses({

    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="ACCOUNT_DISABLED; authenticated PUT may also return CSRF_INVALID for a missing/invalid token or forbidden Origin",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible Project reference",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class HomeDashboardController {
    private final HomeDashboardQueryService queries;
    private final HomeDashboardCommandService commands;
    @GetMapping
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary="Read Home Dashboard",description="No query parameters. Unsaved returns revision 0 and default configuration without persistence writes. Saved empty array stays empty. Broken references return 404 without repair or fallback.")
    @ApiResponse(responseCode="200",description="Owned saved or virtual default configuration",content=@Content(schema=@Schema(implementation=HomeDashboardResponse.class)))
    public ResponseEntity<HomeDashboardResponse> get(Authentication auth,HttpServletRequest http) {
        noQuery(http);return ok(queries.get(((InternalUserPrincipal)auth.getPrincipal()).userId()));
    }
    @PutMapping(consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR or UNSUPPORTED_SCHEMA_VERSION",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary="Save Home Dashboard",description="Full atomic ordered replacement. First PUT revision 0 becomes 1; concurrent first saves have one success and one 409. Existing revision must match; unchanged saves increment. Owned archived Projects permitted, scope normalized to all. No business-resource mutation or default numeric limits.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="200",description="Full normalized configuration, including first save",content=@Content(schema=@Schema(implementation=HomeDashboardResponse.class)))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT: read current layout and compare preserved draft; no automatic overwrite/replay",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<HomeDashboardResponse> save(Authentication auth,@Valid @RequestBody SaveHomeDashboardRequest request,HttpServletRequest http) {
        noQuery(http);return ok(commands.save(((InternalUserPrincipal)auth.getPrincipal()).userId(),request.revision(),request.values()));
    }
    private void noQuery(HttpServletRequest http) {
        if(!http.getParameterMap().isEmpty())throw new DashboardValidationException("query","no query parameters allowed");
    }
    private ResponseEntity<HomeDashboardResponse> ok(HomeDashboardSnapshot s){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(HomeDashboardResponse.from(s));}
}
