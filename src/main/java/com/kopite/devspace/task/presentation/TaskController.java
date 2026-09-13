package com.kopite.devspace.task.presentation;
import com.kopite.devspace.task.presentation.dto.CreateTaskRequest;
import com.kopite.devspace.task.presentation.dto.RestoreTaskRequest;
import com.kopite.devspace.task.presentation.dto.TaskListRequest;
import com.kopite.devspace.task.presentation.dto.TaskListResponse;
import com.kopite.devspace.task.presentation.dto.TaskRequestFields;
import com.kopite.devspace.task.presentation.dto.TaskResponse;
import com.kopite.devspace.task.presentation.dto.TaskStatsRequest;
import com.kopite.devspace.task.presentation.dto.TaskStatsResponse;
import com.kopite.devspace.task.presentation.dto.UpdateTaskRequest;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.task.application.command.TaskCommandService;
import com.kopite.devspace.task.application.query.TaskQueryService;
import com.kopite.devspace.task.domain.TaskValidationException;
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
@RequestMapping(value="/api/v1/tasks",produces="application/json")
@RequiredArgsConstructor
@Tag(name="Tasks")
@SecurityRequirement(name="sessionCookie")
@ApiResponses({

    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="ACCOUNT_DISABLED; authenticated mutations may also return CSRF_INVALID for a missing/invalid token or forbidden Origin",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible Task/Project",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class TaskController {
    private final TaskCommandService commands;
    private final TaskQueryService queries;

    @PostMapping(consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary="Create an owned Task",description="Requires an active owned Project. Retry the same key/body within 24 hours for the original 201 snapshot, including after deletion or Project archive.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="201",description="Full created Task or original replay",content=@Content(schema=@Schema(implementation=TaskResponse.class)))
    @ApiResponse(responseCode="409",description="PROJECT_ARCHIVED or IDEMPOTENCY_KEY_REUSED",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<TaskResponse> create(Authentication auth,
        @RequestHeader("Idempotency-Key") @Parameter(schema=@Schema(minLength=1,maxLength=128,pattern="[!-~]+")) String key,
        @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(TaskResponse.from(commands.create(user(auth),key,request.command())));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full owned Task including trash",content=@Content(schema=@Schema(implementation=TaskResponse.class)))
    @Operation(summary="Read an owned Task including trash")
    public ResponseEntity<TaskResponse> get(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id) {
        return ok(TaskResponse.from(queries.get(user(auth),id(id))));
    }

    @PatchMapping(value="/{id}",consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full updated Task",content=@Content(schema=@Schema(implementation=TaskResponse.class)))
    @Operation(summary="Update or complete/reopen a Task",description="Omitted fields stay unchanged; revision is required. Completion uses status=done. Same-value updates advance revision. Trash cannot be edited. Retaining an archived Project is allowed; reassignment requires an active owned target.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT, PROJECT_ARCHIVED or RESOURCE_DELETED",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<TaskResponse> update(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,@Valid @RequestBody UpdateTaskRequest request) {
        return ok(TaskResponse.from(commands.update(user(auth),id(id),request.command())));
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full soft-deleted Task",content=@Content(schema=@Schema(implementation=TaskResponse.class)))
    @Operation(summary="Soft-delete an owned Task",description="Revision conflict precedes already-deleted state conflict. Returns the full Task.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT or INVALID_RESOURCE_STATE",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<TaskResponse> delete(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,
        @RequestParam @Parameter(schema=@Schema(type="integer",minimum="1",maximum="9007199254740991")) long revision) {
        return ok(TaskResponse.from(commands.delete(user(auth),id(id),revision)));
    }

    @PostMapping(value="/{id}/restore",consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full restored Task",content=@Content(schema=@Schema(implementation=TaskResponse.class)))
    @Operation(summary="Restore an owned Task",description="Body contains only revision. No creation Idempotency-Key is required; archived Project relations are retained.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT or INVALID_RESOURCE_STATE",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<TaskResponse> restore(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,@Valid @RequestBody RestoreTaskRequest request) {
        return ok(TaskResponse.from(commands.restore(user(auth),id(id),request.revision())));
    }

    @GetMapping
    @ApiResponse(responseCode="200",description="Filtered cursor page and matching total",content=@Content(schema=@Schema(implementation=TaskListResponse.class)))
    @Operation(summary="List owned Tasks",description="createdAt DESC, id DESC. Literal title/Project-name search. deleted=true selects only trash. Cursor binds workspace, all filters and limit; no cross-page snapshot guarantee.")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR or INVALID_CURSOR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<TaskListResponse> list(Authentication auth,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","unity","server"},defaultValue="all")) String scope,
        @RequestParam(required=false) @Parameter(schema=@Schema(format="uuid")) String projectId,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","active","archived"},defaultValue="all")) String projectStatus,
        @RequestParam(defaultValue="") String query,
        @RequestParam(required=false) @Parameter(schema=@Schema(allowableValues={"todo","doing","done"})) String status,
        @RequestParam(defaultValue="false") @Parameter(schema=@Schema(type="boolean",defaultValue="false")) String deleted,
        @RequestParam(defaultValue="20") @Parameter(schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")) int limit,
        @RequestParam(required=false) String cursor) {
        var request=new TaskListRequest(scope,projectId,projectStatus,query,status,deleted,limit,cursor);
        return ok(TaskListResponse.from(queries.list(user(auth),request.filter(),cursor)));
    }

    @GetMapping("/stats")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Undeleted counts in one read snapshot",content=@Content(schema=@Schema(implementation=TaskStatsResponse.class)))
    @Operation(summary="Count undeleted Tasks by status",description="All three status keys are always returned. Archived Projects are included by default. status/deleted parameters are rejected.")
    public ResponseEntity<TaskStatsResponse> stats(Authentication auth,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","unity","server"},defaultValue="all")) String scope,
        @RequestParam(required=false) @Parameter(schema=@Schema(format="uuid")) String projectId,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","active","archived"},defaultValue="all")) String projectStatus,
        @RequestParam(defaultValue="") String query,
        @Parameter(hidden=true) @RequestParam java.util.Map<String,String> parameters) {
        for(String field:parameters.keySet())
            if(!java.util.Set.of("scope","projectId","projectStatus","query").contains(field))
                throw new TaskValidationException(field,"not supported by Task stats");
        return ok(TaskStatsResponse.from(queries.stats(user(auth),new TaskStatsRequest(scope,projectId,projectStatus,query).filter())));
    }
    private UUID user(Authentication auth) { return ((InternalUserPrincipal)auth.getPrincipal()).userId(); }
    private UUID id(String value) { return TaskRequestFields.uuid(value,"id"); }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
