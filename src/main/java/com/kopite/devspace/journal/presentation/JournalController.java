package com.kopite.devspace.journal.presentation;
import com.kopite.devspace.journal.presentation.dto.CreateJournalRequest;
import com.kopite.devspace.journal.presentation.dto.DeleteJournalResponse;
import com.kopite.devspace.journal.presentation.dto.JournalListRequest;
import com.kopite.devspace.journal.presentation.dto.JournalListResponse;
import com.kopite.devspace.journal.presentation.dto.JournalRequestFields;
import com.kopite.devspace.journal.presentation.dto.JournalResponse;
import com.kopite.devspace.journal.presentation.dto.UpdateJournalRequest;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.journal.application.command.JournalCommandService;
import com.kopite.devspace.journal.application.query.JournalQueryService;
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
@RequestMapping(value="/api/v1/journals",produces="application/json")
@RequiredArgsConstructor
@Tag(name="Journals")
@SecurityRequirement(name="sessionCookie")
@ApiResponses({

    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="ACCOUNT_DISABLED; authenticated mutations may also return CSRF_INVALID for a missing/invalid token or forbidden Origin",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible Journal/Project",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class JournalController {
    private final JournalCommandService commands;
    private final JournalQueryService queries;

    @PostMapping(consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @Operation(summary="Create an owned Journal",description="Requires an active owned Project. Retry the same key/body within 24 hours for the original 201 snapshot, including after deletion or Project archive.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="201",description="Full created Journal or original replay",content=@Content(schema=@Schema(implementation=JournalResponse.class)))
    @ApiResponse(responseCode="409",description="PROJECT_ARCHIVED or IDEMPOTENCY_KEY_REUSED",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<JournalResponse> create(Authentication auth,
        @RequestHeader("Idempotency-Key") @Parameter(schema=@Schema(minLength=1,maxLength=128,pattern="[!-~]+")) String key,
        @Valid @RequestBody CreateJournalRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(JournalResponse.from(commands.create(user(auth),key,request.command())));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full owned Journal",content=@Content(schema=@Schema(implementation=JournalResponse.class)))
    @Operation(summary="Read an owned Journal")
    public ResponseEntity<JournalResponse> get(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id) {
        return ok(JournalResponse.from(queries.get(user(auth),id(id))));
    }

    @PatchMapping(value="/{id}",consumes="application/json")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Full updated Journal",content=@Content(schema=@Schema(implementation=JournalResponse.class)))
    @Operation(summary="Update a Journal",description="Omitted fields stay unchanged; revision is required. Same-value updates advance revision. Retaining an archived Project is allowed; reassignment requires an active owned target.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT or PROJECT_ARCHIVED",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<JournalResponse> update(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,@Valid @RequestBody UpdateJournalRequest request) {
        return ok(JournalResponse.from(commands.update(user(auth),id(id),request.command())));
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="200",description="Permanently deleted Journal ID",content=@Content(schema=@Schema(implementation=DeleteJournalResponse.class)))
    @Operation(summary="Permanently delete an owned Journal",description="Requires the current revision. Returns deletedId. Subsequent reads and mutations return 404.",
        parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<DeleteJournalResponse> delete(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,
        @RequestParam @Parameter(schema=@Schema(type="integer",minimum="1",maximum="9007199254740991")) long revision) {
        return ok(new DeleteJournalResponse(commands.delete(user(auth),id(id),revision)));
    }

    @GetMapping
    @ApiResponse(responseCode="200",description="Filtered cursor page and matching total",content=@Content(schema=@Schema(implementation=JournalListResponse.class)))
    @Operation(summary="List owned Journals",description="newest: entryDate DESC, createdAt DESC, id DESC; oldest reverses all three. Inclusive calendar date bounds. Literal title/Project-name/body search. Cursor binds workspace, all filters and limit; no cross-page snapshot guarantee.")
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR or INVALID_CURSOR",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<JournalListResponse> list(Authentication auth,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","unity","server"},defaultValue="all")) String scope,
        @RequestParam(required=false) @Parameter(schema=@Schema(format="uuid")) String projectId,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","active","archived"},defaultValue="all")) String projectStatus,
        @RequestParam(defaultValue="") String query,
        @RequestParam(required=false) @Parameter(schema=@Schema(type="string",format="date")) String from,
        @RequestParam(required=false) @Parameter(schema=@Schema(type="string",format="date")) String to,
        @RequestParam(defaultValue="newest") @Parameter(schema=@Schema(allowableValues={"newest","oldest"},defaultValue="newest")) String sort,
        @RequestParam(defaultValue="20") @Parameter(schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")) int limit,
        @RequestParam(required=false) String cursor) {
        var request=new JournalListRequest(scope,projectId,projectStatus,query,from,to,sort,limit);
        return ok(JournalListResponse.from(queries.list(user(auth),request.filter(),cursor)));
    }

    private UUID user(Authentication auth) { return ((InternalUserPrincipal)auth.getPrincipal()).userId(); }
    private UUID id(String value) { return JournalRequestFields.uuid(value,"id"); }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
