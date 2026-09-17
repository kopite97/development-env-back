package com.kopite.devspace.widget.presentation;
import com.kopite.devspace.auth.application.*;
import com.kopite.devspace.widget.application.*;
import com.kopite.devspace.widget.domain.Widget;
import com.kopite.devspace.widget.domain.WidgetException;
import com.kopite.devspace.widget.presentation.dto.WidgetRequests;
import com.kopite.devspace.global.response.*;
import io.swagger.v3.oas.annotations.*;
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
import java.util.*;
@RestController @RequestMapping(value="/api/v1/widgets",produces="application/json") @RequiredArgsConstructor
@Tag(name="Widgets") @SecurityRequirement(name="sessionCookie")
@ApiResponses({@ApiResponse(responseCode="400",description="VALIDATION_ERROR or INVALID_CURSOR"),@ApiResponse(responseCode="401",description="AUTH_REQUIRED"),@ApiResponse(responseCode="403",description="ACCOUNT_DISABLED or CSRF_INVALID"),@ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND"),@ApiResponse(responseCode="409",description="REVISION_CONFLICT, WIDGET_IN_USE or IDEMPOTENCY_KEY_REUSED"),@ApiResponse(responseCode="500",description="INTERNAL_ERROR")})
public class WidgetController {
    public record DeletedWidget(UUID id){}
    private final WidgetCommandService commands;private final WidgetQueryService queries;
    private UUID user(Authentication a){return ((InternalUserPrincipal)a.getPrincipal()).userId();}
    @GetMapping @Operation(summary="List owned Widget configurations",description="Keyset createdAt/id descending; unplaced=true excludes placed Widgets. Cursor binds workspace and filters. No writes; no-store.")
    @ApiResponse(responseCode="200",description="Owned configuration page")
    public ResponseEntity<WidgetQueryService.Page> list(Authentication a,HttpServletRequest r,@RequestParam(defaultValue="20") String limit,@RequestParam(defaultValue="false") String unplaced,@RequestParam(required=false)String cursor) {
        WidgetHttp.query(r,"limit","unplaced","cursor");if(!Set.of("true","false").contains(unplaced))throw WidgetException.invalid("unplaced");
        var s=queries.list(user(a),(int)WidgetHttp.integer(limit,"limit",1,100),Boolean.parseBoolean(unplaced),cursor);return WorkspaceResponses.ok(s,s.dataRevision());
    }
    @GetMapping("/{id}") @Operation(summary="Read a Widget configuration",description="referenceState is response-only valid or missingCategory. Broken Project references are 404; retained unresolved Category IDs are not repaired.")
    @ApiResponse(responseCode="200",description="Current configuration")
    public ResponseEntity<WidgetSnapshot> get(Authentication a,@PathVariable UUID id,HttpServletRequest r){WidgetHttp.query(r);var s=queries.get(user(a),id);return WorkspaceResponses.ok(s,s.dataRevision());}
    @PostMapping(consumes="application/json") @Operation(summary="Create an unplaced Widget",description="Requires CSRF and Idempotency-Key. 24-hour historical replay preserves original 201/body/Location even after deletion; replay omits dataRevision. GET current state before adopting a replay. No placement is created.")
    @ApiResponse(responseCode="201",description="Created or historical replay",content=@Content(schema=@Schema(implementation=WidgetSnapshot.class)))
    public ResponseEntity<String> create(Authentication a,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody WidgetRequests.Create body,HttpServletRequest r){WidgetHttp.query(r);return WidgetHttp.created(commands.create(user(a),key,body.type(),body.title(),body.configVersion(),body.config().toString(),body.hash()));}
    @PutMapping(value="/{id}",consumes="application/json") @Operation(summary="Replace Widget title and configuration",description="Body revision required; immutable type. Same-value update increments Widget and workspace revisions, not layoutRevision. Existing identical missing Category may be retained.")
    @ApiResponse(responseCode="200",description="Saved configuration")
    public ResponseEntity<WidgetSnapshot> replace(Authentication a,@PathVariable UUID id,@Valid @RequestBody WidgetRequests.Update b,HttpServletRequest r){WidgetHttp.query(r);var s=commands.replace(user(a),id,b.revision(),b.title(),b.configVersion(),b.config().toString());return WorkspaceResponses.ok(s,s.dataRevision());}
    @DeleteMapping("/{id}") @Operation(summary="Delete an unplaced Widget",description="Single positive revision query required; no request body. Placed returns 409 WIDGET_IN_USE. Repeated deletion returns 404; creation replay is retained.")
    @ApiResponse(responseCode="200",description="Deleted resource identity; observed workspace revision is in the header")
    public ResponseEntity<DeletedWidget> delete(Authentication a,@PathVariable UUID id,@RequestParam String revision,HttpServletRequest r,@RequestBody(required=false)String body){WidgetHttp.query(r,"revision");if(body!=null&&!body.isEmpty())throw WidgetException.invalid("body");var s=commands.delete(user(a),id,WidgetHttp.integer(revision,"revision",1,Widget.MAX_REVISION));return WorkspaceResponses.ok(new DeletedWidget(s.id()),s.dataRevision());}
}
