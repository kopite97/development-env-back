package com.kopite.devspace.dashboard.presentation;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.dashboard.application.*;
import com.kopite.devspace.dashboard.presentation.dto.LayoutRequests;
import com.kopite.devspace.widget.presentation.WidgetHttp;
import com.kopite.devspace.global.response.WorkspaceResponses;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping(value="/api/v3/dashboards/home",produces="application/json") @RequiredArgsConstructor
@Tag(name="Home Layout") @SecurityRequirement(name="sessionCookie")
@ApiResponses({@ApiResponse(responseCode="400",description="VALIDATION_ERROR"),@ApiResponse(responseCode="401",description="AUTH_REQUIRED"),@ApiResponse(responseCode="403",description="ACCOUNT_DISABLED or CSRF_INVALID"),@ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND"),@ApiResponse(responseCode="409",description="REVISION_CONFLICT or IDEMPOTENCY_KEY_REUSED"),@ApiResponse(responseCode="500",description="INTERNAL_ERROR")})
public class HomeLayoutController {
    private final HomeLayoutQueryService queries;private final HomeLayoutCommandService commands;
    private UUID user(Authentication a){return ((InternalUserPrincipal)a.getPrincipal()).userId();}
    @GetMapping @Operation(summary="Read Home layout and batched Widget configurations",description="Unsaved: initialized=false, layoutRevision=0, empty arrays; GET never creates defaults. Layout, configuration and dataRevision share one RR snapshot.")
    @ApiResponse(responseCode="200",description="Saved or uninitialized layout")
    public ResponseEntity<HomeLayoutSnapshot> get(Authentication a,HttpServletRequest r){WidgetHttp.query(r);var s=queries.get(user(a));return WorkspaceResponses.ok(s,s.dataRevision());}
    @PutMapping(consumes="application/json") @Operation(summary="Replace ordered Home placements",description="Body layoutRevision required; initial 0 becomes 1, including an empty layout. Placement id is response-only, preserved by widgetId across reorder/resize. Removing placements does not delete Widgets. No Widget revision changes; same-value PUT increments layoutRevision.")
    @ApiResponse(responseCode="200",description="Saved layout")
    public ResponseEntity<HomeLayoutSnapshot> save(Authentication a,@Valid @RequestBody LayoutRequests.Save b,HttpServletRequest r){WidgetHttp.query(r);var s=commands.replace(user(a),b.layoutRevision(),b.placements());return WorkspaceResponses.ok(s,s.dataRevision());}
    @PostMapping(value="/initializations",consumes="application/json") @Operation(summary="Initialize Home with six default Widgets",description="Explicit schemaVersion=3/layoutRevision=0 and Idempotency-Key. Atomically creates six Widgets/placements and layout revision 1; workspace advances once. Existing layout (even empty) conflicts. Historical replay takes precedence and has original body/Location but no dataRevision.")
    @ApiResponse(responseCode="201",description="Initialized or historical replay",content=@Content(schema=@Schema(implementation=HomeLayoutSnapshot.class)))
    public ResponseEntity<String> initialize(Authentication a,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody LayoutRequests.Initialize b,HttpServletRequest r){WidgetHttp.query(r);return WidgetHttp.created(commands.initialize(user(a),key,b.hash()));}
}
