package com.kopite.devspace.compatibility.presentation;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.compatibility.application.*;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

/** Bridge-only historical response surface, excluded from the normal v2 OpenAPI. */
@Hidden @RestController @RequestMapping(value="/api/v1",produces="application/json")
public class LegacyReplayController {
    private final LegacyReplayService service;
    private final LegacyCreateParser parser;
    private final CurrentUserService users;
    private final boolean enabled;
    public LegacyReplayController(LegacyReplayService service,LegacyCreateParser parser,CurrentUserService users,
        @Value("${app.category-transition.legacy-replay-enabled:true}") boolean enabled) {
        this.service=service;this.parser=parser;this.users=users;this.enabled=enabled;
    }
    @PostMapping(value={"/projects","/tasks","/journals","/milestones","/links"},consumes="application/json")
    public ResponseEntity<String> replay(Authentication auth,HttpServletRequest request,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {
        var user=((InternalUserPrincipal)auth.getPrincipal()).userId();
        if(!enabled){users.resolve(user);throw new ApiVersionRetiredException();}
        String path=request.getRequestURI().substring(request.getContextPath().length()+"/api/v1/".length());
        var resource=Arrays.stream(CreationResource.values()).filter(r->r.path().equals(path)).findFirst().orElseThrow(ApiVersionRetiredException::new);
        LegacyCreateRequest parsed;
        try { parsed=parser.parse(resource,body); }
        catch(tools.jackson.core.JacksonException invalid) {
            throw new com.kopite.devspace.project.domain.ProjectValidationException("body","invalid historical creation request");
        }
        var result=service.replay(user,key,parsed);
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(result.body());
    }
    @RequestMapping(value={"/projects","/tasks","/journals","/milestones","/links"},
        method={RequestMethod.GET,RequestMethod.HEAD,RequestMethod.PATCH,RequestMethod.PUT,RequestMethod.DELETE,RequestMethod.OPTIONS})
    public void retired(Authentication auth){users.resolve(((InternalUserPrincipal)auth.getPrincipal()).userId());throw new ApiVersionRetiredException();}
    @RequestMapping({"/projects/{id}","/projects/{id}/**","/tasks/{id}","/tasks/{id}/**","/journals/{id}","/milestones/{id}","/links/{id}","/overview","/dashboards/home"})
    public void retiredResource(Authentication auth){retired(auth);}
}
