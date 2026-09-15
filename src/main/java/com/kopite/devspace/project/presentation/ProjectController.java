package com.kopite.devspace.project.presentation;
import com.kopite.devspace.project.presentation.dto.CreateProjectRequest;
import com.kopite.devspace.project.presentation.dto.ProjectListRequest;
import com.kopite.devspace.project.presentation.dto.ProjectListResponse;
import com.kopite.devspace.project.presentation.dto.ProjectResponse;
import com.kopite.devspace.project.presentation.dto.UpdateProjectRequest;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.project.application.command.ProjectCommandService;
import com.kopite.devspace.project.application.query.ProjectQueryService;
import com.kopite.devspace.project.domain.ProjectValidationException;
import com.kopite.devspace.global.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v2/projects", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Projects")
@SecurityRequirement(name = "sessionCookie")
@ApiResponses({

        @ApiResponse(responseCode = "401", description = "AUTH_REQUIRED", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED, or CSRF_INVALID for an authenticated mutation", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class ProjectController {
    private final ProjectCommandService commands;
    private final ProjectQueryService queries;
    private final com.kopite.devspace.overview.application.OverviewQueryService aggregates;

    @GetMapping("/category-counts")
    @Operation(summary="Count all owned Projects by Category",description="Full ordered Category collection including zero counts, then the uncategorized bucket. Active and archived counts are separate. No query parameters.")
    @ApiResponse(responseCode="200",description="Category counts in one read snapshot",content=@Content(schema=@Schema(implementation=com.kopite.devspace.project.presentation.dto.ProjectCategoryCountsResponse.class)))
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<com.kopite.devspace.project.presentation.dto.ProjectCategoryCountsResponse> categoryCounts(Authentication authentication,
            @Parameter(hidden=true) @RequestParam java.util.Map<String,String> parameters) {
        if(!parameters.isEmpty()) throw new ProjectValidationException(parameters.keySet().iterator().next(),"not supported");
        var result=aggregates.categoryCounts(userId(authentication));
        return com.kopite.devspace.global.response.WorkspaceResponses.ok(com.kopite.devspace.project.presentation.dto.ProjectCategoryCountsResponse.from(result),result.dataRevision());
    }

    @PostMapping(consumes = "application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary = "Create an owned Project", description = "Requires session CSRF/Origin checks. Retry the same Idempotency-Key and body within 24 hours to replay the original 201 response. Category presence/null/raw UUID uses fingerprint v3. JSON property order is ignored; supplied values and field presence are preserved. Responses include nullable categoryId. Legacy intents must retry their original v1 endpoint. Replay never resolves current Category state.",
            parameters = @Parameter(name = "X-CSRF-Token", in = ParameterIn.HEADER, required = true, description = "Session token from GET /api/v1/auth/csrf", schema = @Schema(type = "string")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created, or original creation response replayed", content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: missing or inaccessible Category", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED or REVISION_CONFLICT", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ProjectResponse> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") @Parameter(schema = @Schema(minLength = 1, maxLength = 128, pattern = "[!-~]+")) String key,
            @Valid @RequestBody CreateProjectRequest request) {
        var result=commands.create(userId(authentication), key, request.command());
        return com.kopite.devspace.global.response.WorkspaceResponses.created(ProjectResponse.from(result),result.dataRevision());
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary = "Get an owned Project, including archived Projects")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owned Project", content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND (missing or inaccessible)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ProjectResponse> get(Authentication authentication, @PathVariable @Parameter(schema = @Schema(type = "string", format = "uuid")) String id) {
        var result=queries.get(userId(authentication), projectId(id));
        return com.kopite.devspace.global.response.WorkspaceResponses.ok(ProjectResponse.from(result),result.dataRevision());
    }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary = "Update, archive or unarchive an owned Project", description = "Only supplied fields change. categoryId omission preserves, null clears, UUID assigns an owned Category without changing other fields; other explicit nulls are invalid. Revision is required and advances once per successful PATCH, including same-value updates. Empty strings clear optional text. Archived Projects remain editable; archive/unarchive preserves Category and connected data.",
            parameters = @Parameter(name = "X-CSRF-Token", in = ParameterIn.HEADER, required = true, description = "Session token from GET /api/v1/auth/csrf", schema = @Schema(type = "string")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Full updated Project", content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND (missing or inaccessible)", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "REVISION_CONFLICT; reload and reconcile the draft", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ProjectResponse> update(Authentication authentication, @PathVariable @Parameter(schema = @Schema(type = "string", format = "uuid")) String id,
                                                   @Valid @RequestBody UpdateProjectRequest request) {
        var result=commands.update(userId(authentication), projectId(id), request.command());
        return com.kopite.devspace.global.response.WorkspaceResponses.ok(ProjectResponse.from(result),result.dataRevision());
    }

    @GetMapping
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or INVALID_CURSOR", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @Operation(summary = "List owned Projects", description = "createdAt DESC, id DESC. Query is a case-insensitive literal substring of name or stack. Cursor binds all filters and limit; no cross-page snapshot guarantee.")
    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: missing or inaccessible Category", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "200", description = "Filtered cursor page with total matching count", content = @Content(schema = @Schema(implementation = ProjectListResponse.class)))
    public ResponseEntity<ProjectListResponse> list(Authentication authentication,
            @RequestParam(defaultValue = "all") @Parameter(schema = @Schema(pattern="all|uncategorized|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", defaultValue = "all")) String category,
            @RequestParam(defaultValue = "active") @Parameter(schema = @Schema(allowableValues = {"active", "archived", "all"}, defaultValue = "active")) String status,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "20") @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")) int limit,
            @RequestParam(required = false) String cursor, jakarta.servlet.http.HttpServletRequest http) {
        validateQuery(http);
        var request = new ProjectListRequest(category, status, query, limit, cursor);
        var result=queries.list(userId(authentication), request.filter(), request.cursor());
        return com.kopite.devspace.global.response.WorkspaceResponses.ok(ProjectListResponse.from(result),result.dataRevision());
    }

    private void validateQuery(jakarta.servlet.http.HttpServletRequest request) {
        request.getParameterMap().forEach((name,values)->{
            if(!java.util.Set.of("category","status","query","limit","cursor").contains(name)||values.length!=1 || (name.equals("category") && values[0].isEmpty()))
                throw new ProjectValidationException(name,"invalid query parameter");
        });
    }
    private UUID userId(Authentication authentication) { return ((InternalUserPrincipal) authentication.getPrincipal()).userId(); }

    private UUID projectId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException invalid) { throw new ProjectValidationException("id", "must be a UUID"); }
    }
}
