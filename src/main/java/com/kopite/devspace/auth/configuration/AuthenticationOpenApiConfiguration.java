package com.kopite.devspace.auth.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthenticationOpenApiConfiguration {

    @Bean
    OpenAPI authenticationOpenAPI() {
        SecurityRequirement sessionSecurity = new SecurityRequirement().addList("sessionCookie");
        Operation callback = new Operation()
                .summary("OIDC callback handled by Spring Security")
                .description("The authorization code and state are consumed by the Spring Security OAuth2 filter.")
                .responses(new ApiResponses()
                        .addApiResponse("302", new ApiResponse().description("Redirect after authentication")));
        Operation logout = new Operation()
                .summary("Invalidate the authenticated browser session")
                .security(java.util.List.of(sessionSecurity))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Session invalidated"))
                        .addApiResponse("403", new ApiResponse().description("CSRF or origin validation failed")));

        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "sessionCookie",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                ))
                .path(
                        "/api/v1/auth/callback/{registrationId}",
                        new PathItem().get(callback)
                )
                .path(
                        "/api/v1/auth/logout",
                        new PathItem().post(logout)
                );
    }
}
