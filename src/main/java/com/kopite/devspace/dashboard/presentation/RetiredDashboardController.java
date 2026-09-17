package com.kopite.devspace.dashboard.presentation;
import com.kopite.devspace.auth.application.*;
import com.kopite.devspace.compatibility.application.ApiVersionRetiredException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @SecurityRequirement(name="sessionCookie")
public class RetiredDashboardController {
    private final CurrentUserService users;
    @RequestMapping(value="/api/v2/dashboards/home",method={RequestMethod.GET,RequestMethod.PUT},produces="application/json")
    @Operation(summary="Retired full-configuration Dashboard contract",description="Use Widget v1 and Dashboard v3. Authentication/account checks and mutation CSRF still apply; old PUT cannot overwrite independent Widgets.")
    @ApiResponse(responseCode="410",description="API_VERSION_RETIRED")
    public void retired(Authentication a){users.resolve(((InternalUserPrincipal)a.getPrincipal()).userId());throw new ApiVersionRetiredException();}
}
