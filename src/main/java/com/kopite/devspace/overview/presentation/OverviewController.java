package com.kopite.devspace.overview.presentation;
import com.kopite.devspace.overview.application.*;
import com.kopite.devspace.overview.presentation.dto.*;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.global.response.ApiError;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
@RestController
@RequestMapping(value="/api/v1/overview",produces="application/json")
@RequiredArgsConstructor
@Tag(name="Overview")
@SecurityRequirement(name="sessionCookie")
@ApiResponses({
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="ACCOUNT_DISABLED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible Project",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class OverviewController {
    private final OverviewQueryService queries;
    @GetMapping
    @Operation(summary="Read workspace Overview",description="Single read snapshot; scope/projectId intersection after ownership validation, including archived Projects. projects.total counts active, archived separately. Both byScope keys and every Task status always present, excluded scopes zero. Tasks match unsearched stats with projectStatus=all and deleted=false. Totals cover every matching row independent of pages or widget limits; query/status/projectStatus/deleted/limit/cursor and repeated parameters are not accepted.")
    @ApiResponse(responseCode="200",description="Complete owned Project and Task counts",content=@Content(schema=@Schema(implementation=OverviewResponse.class)))
    public ResponseEntity<OverviewResponse> get(Authentication auth,
        @RequestParam(required=false) @Parameter(schema=@Schema(allowableValues={"all","unity","server"},defaultValue="all")) String scope,
        @RequestParam(required=false) @Parameter(schema=@Schema(type="string",format="uuid")) String projectId,HttpServletRequest http) {
        http.getParameterMap().forEach((name,values)->{if(!Set.of("scope","projectId").contains(name)||values.length!=1)throw new OverviewValidationException(name);});
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(OverviewResponse.from(queries.get(((InternalUserPrincipal)auth.getPrincipal()).userId(),new OverviewRequest(scope,projectId).filter())));
    }
}
