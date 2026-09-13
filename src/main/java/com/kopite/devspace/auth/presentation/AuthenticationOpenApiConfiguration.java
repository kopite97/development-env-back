package com.kopite.devspace.auth.presentation;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthenticationOpenApiConfiguration {

    @Bean
    OpenAPI authenticationOpenAPI(@Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieName) {
        SecurityRequirement sessionSecurity = new SecurityRequirement().addList("sessionCookie");
        Operation callback = new Operation()
                .operationId("oidcCallback").addTagsItem("Authentication")
                .summary("OIDC callback handled by Spring Security")
                .description("Provider callback, consumed by Spring Security. Success redirects to the validated returnTo; failed or invalid callbacks redirect to /?authError=login_failed. Not a JSON login endpoint.")
                .addParametersItem(new Parameter().name("registrationId").in("path").required(true)
                        .description("Configured OAuth2 client registration; currently google").schema(new StringSchema()))
                .addParametersItem(new Parameter().name("code").in("query").required(false)
                        .description("Authorization code on a successful provider response").schema(new StringSchema()))
                .addParametersItem(new Parameter().name("state").in("query").required(false)
                        .description("Must match the saved authorization request").schema(new StringSchema()))
                .addParametersItem(new Parameter().name("error").in("query").required(false)
                        .description("Provider error instead of an authorization code").schema(new StringSchema()))
                .responses(new ApiResponses()
                        .addApiResponse("401", error("AUTH_REQUIRED: invalid or removed existing session user"))
                        .addApiResponse("403", error("ACCOUNT_DISABLED: existing session user is disabled"))
                        .addApiResponse("302", new ApiResponse().description("Redirect after authentication")));
        Operation logout = new Operation()
                .operationId("logout").addTagsItem("Authentication")
                .summary("Invalidate the authenticated browser session")
                .description("Authenticated logout requires the session CSRF token and an allowed Origin when supplied. Without a session, logout is an idempotent 204 and does not require a CSRF token.")
                .security(java.util.List.of(sessionSecurity, new SecurityRequirement()))
                .addParametersItem(new Parameter().name("X-CSRF-Token").in("header").required(false)
                        .description("Required when authenticated; obtain from GET /api/v1/auth/csrf. Not required without a session.")
                        .schema(new StringSchema()))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Session invalidated"))
                        .addApiResponse("401", error("AUTH_REQUIRED: invalid or removed session user"))
                        .addApiResponse("403", error("CSRF_INVALID, forbidden Origin or ACCOUNT_DISABLED")));

        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "sessionCookie",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(cookieName)
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

    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(
                "application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}
