package com.kopite.devspace.milestone.presentation;
import com.kopite.devspace.milestone.presentation.dto.CreateMilestoneRequest;
import com.kopite.devspace.milestone.presentation.dto.DeleteMilestoneResponse;
import com.kopite.devspace.milestone.presentation.dto.MilestoneListRequest;
import com.kopite.devspace.milestone.presentation.dto.MilestoneListResponse;
import com.kopite.devspace.milestone.presentation.dto.MilestoneRequestFields;
import com.kopite.devspace.milestone.presentation.dto.MilestoneResponse;
import com.kopite.devspace.milestone.presentation.dto.UpdateMilestoneRequest;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.milestone.application.command.MilestoneCommandService;
import com.kopite.devspace.milestone.application.query.MilestoneQueryService;
import com.kopite.devspace.global.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
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
@RequestMapping(value="/api/v1/milestones",produces="application/json")
@RequiredArgsConstructor
@Tag(name="Milestones")
@SecurityRequirement(name="sessionCookie")
@ApiResponses({

    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="ACCOUNT_DISABLED; authenticated mutations may also return CSRF_INVALID for a missing/invalid token or forbidden Origin",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible Milestone/Project",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class MilestoneController {
    private final MilestoneCommandService commands;
    private final MilestoneQueryService queries;

    @PostMapping(consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary="Create an owned Milestone",description="Requires an owned active or archived Project. Retry the same key/body within 24 hours for the original 201 snapshot, including after deletion or Project archive.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="201",description="Full created Milestone or original replay",content=@Content(schema=@Schema(implementation=MilestoneResponse.class)))
    @ApiResponse(responseCode="409",description="IDEMPOTENCY_KEY_REUSED",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<MilestoneResponse> create(Authentication auth,
        @RequestHeader("Idempotency-Key") @Parameter(schema=@Schema(minLength=1,maxLength=128,pattern="[!-~]+")) String key,
        @Valid @RequestBody CreateMilestoneRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(MilestoneResponse.from(commands.create(user(auth),key,request.command())));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full owned Milestone",content=@Content(schema=@Schema(implementation=MilestoneResponse.class)))
    @Operation(summary="Read an owned Milestone")
    public ResponseEntity<MilestoneResponse> get(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id) {
        return ok(MilestoneResponse.from(queries.get(user(auth),id(id))));
    }

    @PatchMapping(value="/{id}",consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full updated Milestone",content=@Content(schema=@Schema(implementation=MilestoneResponse.class)))
    @Operation(summary="Update a Milestone",description="Omitted fields stay unchanged; revision is required. Same-value updates advance revision. completed=true completes and false reopens. dueDate=null clears, omission preserves. Owned archived Projects are allowed, including reassignment.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<MilestoneResponse> update(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,@Valid @RequestBody UpdateMilestoneRequest request) {
        return ok(MilestoneResponse.from(commands.update(user(auth),id(id),request.command())));
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Permanently deleted Milestone ID",content=@Content(schema=@Schema(implementation=DeleteMilestoneResponse.class)))
    @Operation(summary="Permanently delete an owned Milestone",description="Requires the current revision. Returns deletedId. Subsequent reads and mutations return 404.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<DeleteMilestoneResponse> delete(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,
        @RequestParam @Parameter(schema=@Schema(type="integer",minimum="1",maximum="9007199254740991")) long revision) {
        return ok(new DeleteMilestoneResponse(commands.delete(user(auth),id(id),revision)));
    }

    @GetMapping
    @ApiResponse(responseCode="200",description="Filtered cursor page and matching total",content=@Content(schema=@Schema(implementation=MilestoneListResponse.class)))
    @Operation(summary="List owned Milestones",description="completed ASC, dueDate ASC NULLS LAST, id ASC. status is only a filter: open, done or all. Overdue items are included. Cursor binds workspace, all filters and limit; no cross-page snapshot guarantee.")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR or INVALID_CURSOR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<MilestoneListResponse> list(Authentication auth,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","unity","server"},defaultValue="all")) String scope,
        @RequestParam(required=false) @Parameter(schema=@Schema(format="uuid")) String projectId,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","active","archived"},defaultValue="all")) String projectStatus,
        @RequestParam(defaultValue="open") @Parameter(schema=@Schema(allowableValues={"open","done","all"},defaultValue="open")) String status,
        @RequestParam(defaultValue="20") @Parameter(schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")) int limit,
        @RequestParam(required=false) String cursor) {
        var request=new MilestoneListRequest(scope,projectId,projectStatus,status,limit);
        return ok(MilestoneListResponse.from(queries.list(user(auth),request.filter(),cursor)));
    }

    private UUID user(Authentication auth) { return ((InternalUserPrincipal)auth.getPrincipal()).userId(); }
    private UUID id(String value) { return MilestoneRequestFields.uuid(value,"id"); }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
