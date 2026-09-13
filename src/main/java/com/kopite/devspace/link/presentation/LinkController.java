package com.kopite.devspace.link.presentation;
import com.kopite.devspace.link.presentation.dto.CreateLinkRequest;
import com.kopite.devspace.link.presentation.dto.DeleteLinkResponse;
import com.kopite.devspace.link.presentation.dto.LinkListRequest;
import com.kopite.devspace.link.presentation.dto.LinkListResponse;
import com.kopite.devspace.link.presentation.dto.LinkMutationResponse;
import com.kopite.devspace.link.presentation.dto.LinkRequestFields;
import com.kopite.devspace.link.presentation.dto.LinkResponse;
import com.kopite.devspace.link.presentation.dto.ReorderLinksRequest;
import com.kopite.devspace.link.presentation.dto.UpdateLinkRequest;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.link.application.command.LinkCommandService;
import com.kopite.devspace.link.application.query.LinkQueryService;
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
@RequestMapping(value="/api/v1/links",produces="application/json")
@RequiredArgsConstructor
@Tag(name="Links")
@SecurityRequirement(name="sessionCookie")
@ApiResponses({
    @ApiResponse(responseCode="400",description="VALIDATION_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="401",description="AUTH_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="CSRF_INVALID, forbidden Origin or ACCOUNT_DISABLED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing or inaccessible Link",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="409",description="REVISION_CONFLICT or IDEMPOTENCY_KEY_REUSED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="500",description="INTERNAL_ERROR",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class LinkController {
    private final LinkCommandService commands;
    private final LinkQueryService queries;
    @PostMapping(consumes="application/json")
    @Operation(summary="Create an owned Link",description="Append to global order. Required same key/body replays original wrapper for 24 hours even after deletion. Maximum configured 500 live Links.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="201",description="Created item and collectionRevision",content=@Content(schema=@Schema(implementation=LinkMutationResponse.class)))
    @ApiResponse(responseCode="429",description="QUOTA_EXCEEDED: delete a Link before creating another",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<LinkMutationResponse> create(Authentication auth,
        @RequestHeader("Idempotency-Key") @Parameter(schema=@Schema(minLength=1,maxLength=128,pattern="[!-~]+")) String key,
        @Valid @RequestBody CreateLinkRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(LinkMutationResponse.from(commands.create(user(auth),key,request.command())));
    }
    @GetMapping("/{id}")
    @Operation(summary="Read an owned Link")
    @ApiResponse(responseCode="200",description="Full Link without collection wrapper",content=@Content(schema=@Schema(implementation=LinkResponse.class)))
    public ResponseEntity<LinkResponse> get(Authentication auth,@PathVariable String id) {return ok(LinkResponse.from(queries.get(user(auth),id(id))));}
    @PatchMapping(value="/{id}",consumes="application/json")
    @Operation(summary="Update an owned Link",description="Requires item revision; position cannot be written. Omission preserves, null rejects. Every successful PATCH advances item and collection revisions.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="200",description="Updated item and collectionRevision",content=@Content(schema=@Schema(implementation=LinkMutationResponse.class)))
    public ResponseEntity<LinkMutationResponse> update(Authentication auth,@PathVariable String id,@Valid @RequestBody UpdateLinkRequest request) {return ok(LinkMutationResponse.from(commands.update(user(auth),id(id),request.command())));}
    @DeleteMapping("/{id}")
    @Operation(summary="Permanently delete an owned Link",description="Requires current item revision; leaves position gaps. Repeated delete returns 404; no restore or tombstone.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="200",description="Deleted ID and collectionRevision",content=@Content(schema=@Schema(implementation=DeleteLinkResponse.class)))
    public ResponseEntity<DeleteLinkResponse> delete(Authentication auth,@PathVariable String id,@RequestParam @Parameter(schema=@Schema(type="integer",minimum="1",maximum="9007199254740991")) long revision) {
        var result=commands.delete(user(auth),id(id),revision);return ok(new DeleteLinkResponse(result.deletedId(),result.collectionRevision()));
    }
    @PutMapping(value="/order",consumes="application/json")
    @Operation(summary="Reorder all owned Links",description="Clear filters and submit the exact whole-workspace ID permutation with collectionRevision. Stale revision 409 precedes ID-set validation 400. Assign dense positions; moved items advance revision. Even unchanged/empty valid order advances collectionRevision once.",parameters=@Parameter(name="X-CSRF-Token",in=ParameterIn.HEADER,required=true,schema=@Schema(type="string")))
    @ApiResponse(responseCode="200",description="Full unfiltered ordered collection",content=@Content(schema=@Schema(implementation=LinkListResponse.class)))
    public ResponseEntity<LinkListResponse> reorder(Authentication auth,@Valid @RequestBody ReorderLinksRequest request,jakarta.servlet.http.HttpServletRequest http) {
        parameters(http,java.util.Set.of());var result=commands.reorder(user(auth),request.collectionRevision(),request.ids());return ok(LinkListResponse.from(result.items(),result.collectionRevision()));
    }
    @GetMapping
    @Operation(summary="List owned Links",description="Full filtered collection, position ASC then id ASC; nextCursor always null. Scope unity/server includes common all links. Query is literal case-insensitive label/description/url search. Maximum 500 items / 8 MiB compact UTF-8; no limit/cursor/project parameters.")
    @ApiResponse(responseCode="200",description="Full filtered collection and same-snapshot global collectionRevision",content=@Content(schema=@Schema(implementation=LinkListResponse.class)))
    public ResponseEntity<LinkListResponse> list(Authentication auth,
        @RequestParam(defaultValue="all") @Parameter(schema=@Schema(allowableValues={"all","unity","server"},defaultValue="all")) String scope,
        @RequestParam(defaultValue="") String query,jakarta.servlet.http.HttpServletRequest http) {
        parameters(http,java.util.Set.of("scope","query"));var result=queries.list(user(auth),new LinkListRequest(scope,query).filter());return ok(LinkListResponse.from(result.items(),result.collectionRevision()));
    }
    private void parameters(jakarta.servlet.http.HttpServletRequest request,java.util.Set<String> allowed) {
        request.getParameterMap().forEach((name,values)->{if(!allowed.contains(name)||values.length!=1)throw LinkRequestFields.invalid(name);});
    }
    private UUID user(Authentication auth){return ((InternalUserPrincipal)auth.getPrincipal()).userId();}
    private UUID id(String value){return LinkRequestFields.uuid(value,"id");}
    private <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
