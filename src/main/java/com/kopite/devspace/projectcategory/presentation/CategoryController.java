package com.kopite.devspace.projectcategory.presentation;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.projectcategory.application.*;
import com.kopite.devspace.projectcategory.presentation.dto.*;
import com.kopite.devspace.global.response.ApiError;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping(value="/api/v1/project-categories",produces="application/json")
@RequiredArgsConstructor @Tag(name="Project Categories") @SecurityRequirement(name="sessionCookie")
@ApiResponses({
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="ACCOUNT_DISABLED or CSRF_INVALID for authenticated mutations",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))})
public class CategoryController {
    private final CategoryCommandService commands;
    private final CategoryQueryService queries;
    @GetMapping
    @Operation(summary="List Project Categories",description="Full collection, createdAt ASC/id ASC. No query parameters, cursor, search or manual ordering. Creation cap 100 never truncates existing data.")
    @ApiResponse(responseCode="200",description="Full owned collection",content=@Content(schema=@Schema(implementation=CategoryListResponse.class)))
    public ResponseEntity<CategoryListResponse> list(Authentication auth,@RequestParam @Parameter(hidden=true) MultiValueMap<String,String> params) {
        query(params,Set.of());var result=queries.collection(user(auth));var items=result.items().stream().map(CategoryResponse::from).toList();return com.kopite.devspace.global.response.WorkspaceResponses.ok(new CategoryListResponse(items,items.size()),result.dataRevision());
    }
    @GetMapping("/{id}")
    @Operation(summary="Read a Project Category")
    @ApiResponse(responseCode="200",description="Current owned Category",content=@Content(schema=@Schema(implementation=CategoryResponse.class)))
    public ResponseEntity<CategoryResponse> get(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,@RequestParam @Parameter(hidden=true) MultiValueMap<String,String> params) {
        query(params,Set.of());var result=queries.get(user(auth),CategoryRequestFields.uuid(id));return com.kopite.devspace.global.response.WorkspaceResponses.ok(CategoryResponse.from(result),result.dataRevision());
    }
    @PostMapping(consumes="application/json")
    @Operation(summary="Create a Project Category",description="Required key/body replays original 201 snapshot for 24 hours, even after rename/deletion. Raw name and field values define intent; normalization-equivalent raw names differ. No automatic defaults.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="201",description="Created or original snapshot replayed",content=@Content(schema=@Schema(implementation=CategoryResponse.class)))
    @ApiResponse(responseCode="409",description="CATEGORY_NAME_CONFLICT, IDEMPOTENCY_KEY_REUSED or REVISION_CONFLICT",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="429",description="QUOTA_EXCEEDED: maximum 100 persisted Categories",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<CategoryResponse> create(Authentication auth,@RequestHeader("Idempotency-Key") @Parameter(schema=@Schema(minLength=1,maxLength=128,pattern="[!-~]+")) String key,
        @Valid @RequestBody CreateCategoryRequest request,@RequestParam @Parameter(hidden=true) MultiValueMap<String,String> params) {
        query(params,Set.of());var result=commands.create(user(auth),key,request.name());return com.kopite.devspace.global.response.WorkspaceResponses.created(CategoryResponse.from(result),result.dataRevision());
    }
    @PatchMapping(value="/{id}",consumes="application/json")
    @Operation(summary="Rename a Project Category",description="Requires revision and name; same-value rename advances Category revision once, never Project revisions.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="200",description="Renamed Category",content=@Content(schema=@Schema(implementation=CategoryResponse.class)))
    @ApiResponse(responseCode="409",description="CATEGORY_NAME_CONFLICT or REVISION_CONFLICT",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<CategoryResponse> rename(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,@Valid @RequestBody RenameCategoryRequest request,
        @RequestParam @Parameter(hidden=true) MultiValueMap<String,String> params) {
        query(params,Set.of());var result=commands.rename(user(auth),CategoryRequestFields.uuid(id),request.revision(),request.name());return com.kopite.devspace.global.response.WorkspaceResponses.ok(CategoryResponse.from(result),result.dataRevision());
    }
    @DeleteMapping("/{id}")
    @Operation(summary="Delete a Project Category",description="Any active or archived Project reference rejects deletion with CATEGORY_IN_USE. No cascading, clearing or reassignment. Repeated delete returns 404. No request body.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="200",description="Deleted ID",content=@Content(schema=@Schema(implementation=DeleteCategoryResponse.class)))
    @ApiResponse(responseCode="409",description="CATEGORY_IN_USE or REVISION_CONFLICT",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<DeleteCategoryResponse> delete(Authentication auth,@PathVariable @Parameter(schema=@Schema(type="string",format="uuid")) String id,
        @RequestParam @Parameter(schema=@Schema(type="integer",minimum="1",maximum="9007199254740991")) long revision,
        @RequestParam @Parameter(hidden=true) MultiValueMap<String,String> params,jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        query(params,Set.of("revision"));if(request.getInputStream().read()!=-1)throw CategoryRequestFields.invalid("body");
        com.kopite.devspace.projectcategory.domain.ProjectCategory.validateRevision(revision);
        var result=commands.deleteObserved(user(auth),CategoryRequestFields.uuid(id),revision);
        return com.kopite.devspace.global.response.WorkspaceResponses.ok(new DeleteCategoryResponse(result.id()),result.dataRevision());
    }
    private void query(MultiValueMap<String,String> params,Set<String> allowed) {
        params.forEach((key,values)->{if(!allowed.contains(key)||values.size()!=1)throw CategoryRequestFields.invalid(key);});
    }
    private UUID user(Authentication auth){return ((InternalUserPrincipal)auth.getPrincipal()).userId();}
    private <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
