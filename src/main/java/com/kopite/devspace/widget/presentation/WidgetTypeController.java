package com.kopite.devspace.widget.presentation;
import com.kopite.devspace.auth.application.*;
import com.kopite.devspace.widget.application.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import java.util.*;
@RestController @RequiredArgsConstructor @SecurityRequirement(name="sessionCookie")
public class WidgetTypeController {
    private final WidgetTypeRegistry registry;private final CurrentUserService users;
    public record Definition(String type,int configVersion,JsonNode configSchema,List<String> supportedSizes,String dataKind,String availability){}
    @GetMapping(value="/api/v1/widget-types",produces="application/json") @Operation(summary="Read Widget type definitions",description="Authenticated catalog with JSON Schema for each configuration; no workspace observation header. Never includes credentials or implementation names.")
    public ResponseEntity<List<Definition>> list(Authentication a,HttpServletRequest r){WidgetHttp.query(r);users.resolve(((InternalUserPrincipal)a.getPrincipal()).userId());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registry.all().stream().map(d->new Definition(d.type(),d.configVersion(),WidgetJson.parse(d.configSchema()),d.sizes().stream().sorted().toList(),d.type(),d.availability())).toList());}
}
