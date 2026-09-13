package com.kopite.devspace;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.auth.infrastructure.oidc.OidcAuthenticationSuccessHandler;
import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    private final MockMvc mockMvc;
    private final UserWorkspaceCreationService creationService;
    private final OidcAuthenticationSuccessHandler successHandler;
    private final JdbcTemplate jdbcTemplate;
    private final HttpSessionSecurityContextRepository securityContextRepository;
    private final Environment environment;

    @Autowired
    SecurityIntegrationTests(
            MockMvc mockMvc,
            UserWorkspaceCreationService creationService,
            OidcAuthenticationSuccessHandler successHandler,
            JdbcTemplate jdbcTemplate,
            HttpSessionSecurityContextRepository securityContextRepository,
            Environment environment
    ) {
        this.mockMvc = mockMvc;
        this.creationService = creationService;
        this.successHandler = successHandler;
        this.jdbcTemplate = jdbcTemplate;
        this.securityContextRepository = securityContextRepository;
        this.environment = environment;
    }

    @Test
    void unauthenticatedProtectedEndpointsReturnJson401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void unauthenticatedProtectedMutationsUseTheSharedAuthenticationEntryPoint() throws Exception {
        for (String method : java.util.List.of("POST", "PATCH", "PUT", "DELETE")) {
            for (String path : java.util.List.of("/api/v1/projects", "/api/v1/me/workspace")) {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .request(org.springframework.http.HttpMethod.valueOf(method), path)
                                .contentType("application/json").content("{}"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                        .andExpect(header().string("Cache-Control", containsString("no-store")));
            }
        }
    }

    @Test
    void loginUsesGoogleAndNormalizesUnsafeReturnTo() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login").param("returnTo", "https://evil.example/path"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/google?returnTo=/"));

        MvcResult authorization = mockMvc.perform(
                        get("/oauth2/authorization/google")
                                .param("returnTo", "/projects")
                )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("127.0.0.1:18999/oauth2/authorize")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", containsString("nonce=")))
                .andExpect(header().string("Location", containsString("code_challenge=")))
                .andExpect(header().string("Location", containsString("redirect_uri=")))
                .andReturn();
        assertNotNull(authorization.getRequest().getSession(false));
    }

    @Test
    void oidcSuccessCreatesSessionAndMeUsesTheOwningWorkspace() throws Exception {
        String issuer = "https://accounts.google.com";
        String subject = unique("success-subject");
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setSession(session);
        MockHttpServletResponse callbackResponse = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                callbackRequest,
                callbackResponse,
                oidcAuthentication(issuer, subject, "OIDC User", unique("google-registration"))
        );

        UserWorkspaceCreationResult persisted = creationService.createOrReuse(
                issuer,
                subject,
                "Ignored after callback"
        );
        assertEquals("/", callbackResponse.getRedirectedUrl());
        assertNotNull(session.getAttribute(
                "SPRING_SECURITY_CONTEXT"
        ));

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(persisted.user().getId().toString()))
                .andExpect(jsonPath("$.displayName").value("OIDC User"))
                .andExpect(jsonPath("$.workspace.id").value(persisted.workspace().getId().toString()))
                .andExpect(jsonPath("$.workspace.name").value(persisted.workspace().getName()))
                .andExpect(jsonPath("$.workspace.revision").value(1))
                .andExpect(jsonPath("$.workspace.dataRevision").doesNotExist())
                .andExpect(jsonPath("$.issuer").doesNotExist());
    }

    @Test
    void differentVerifiedIssuerSubjectPairsDoNotMergeEvenWhenEmailMatches() throws Exception {
        String email = "same-address@example.test";
        String firstSubject = unique("google-subject");
        String secondSubject = unique("other-subject");
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.setSession(new MockHttpSession());
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                firstRequest,
                firstResponse,
                oidcAuthentication("https://accounts.google.com", firstSubject, "Google Name", "google", email)
        );
        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setSession(new MockHttpSession());
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                secondRequest,
                secondResponse,
                oidcAuthentication("https://login.example.test", secondSubject, "Other Provider Name", "google", email)
        );

        UserWorkspaceCreationResult first = creationService.createOrReuse(
                "https://accounts.google.com", firstSubject, "Ignored"
        );
        UserWorkspaceCreationResult second = creationService.createOrReuse(
                "https://login.example.test", secondSubject, "Ignored"
        );

        assertFalse(first.user().getId().equals(second.user().getId()));
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE id IN (?, ?)",
                Long.class,
                first.user().getId(),
                second.user().getId()
        ));
    }

    @Test
    void callbackErrorsDoNotCreateAnAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/callback/google")
                        .param("error", "access_denied")
                        .param("state", "invalid-state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/?authError=login_failed"));
    }

    @Test
    void callbackStateMismatchAndReuseCannotCreateAUserSession() throws Exception {
        MvcResult authorization = mockMvc.perform(
                        get("/oauth2/authorization/google").param("returnTo", "/projects")
                )
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/api/v1/auth/callback/google")
                        .session(session)
                        .param("code", "unused-code")
                        .param("state", "mismatched-state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/?authError=login_failed"));
        assertTrue(session.isInvalid());
    }

    @Test
    void standardServletSessionTimeoutAndJPAValidationRemainConfigured() {
        assertEquals("12h", environment.getProperty("server.servlet.session.timeout"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void oidcDisplayNameFallsBackToAStableNonEmptyValue() throws Exception {
        String subject = unique("fallback-subject");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                request,
                response,
                oidcAuthenticationWithoutProfile("https://accounts.google.com", subject)
        );

        UserWorkspaceCreationResult result = creationService.createOrReuse(
                "https://accounts.google.com", subject, "Ignored"
        );
        assertEquals("Google User", result.user().getDisplayName());
    }

    @Test
    void separateAuthenticatedSessionsCannotChangeTheOwnerContext() throws Exception {
        UserWorkspaceCreationResult first = creationService.createOrReuse(
                unique("issuer-a"), unique("subject-a"), "First User"
        );
        UserWorkspaceCreationResult second = creationService.createOrReuse(
                unique("issuer-b"), unique("subject-b"), "Second User"
        );
        Authentication firstAuthentication = internalAuthentication(first);
        Authentication secondAuthentication = internalAuthentication(second);

        mockMvc.perform(get("/api/v1/me").with(authentication(firstAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.user().getId().toString()))
                .andExpect(jsonPath("$.workspace.id").value(first.workspace().getId().toString()));
        mockMvc.perform(get("/api/v1/me?userId=" + second.user().getId())
                        .with(authentication(firstAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.user().getId().toString()))
                .andExpect(jsonPath("$.workspace.id").value(first.workspace().getId().toString()));
        mockMvc.perform(get("/api/v1/me").with(authentication(secondAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second.user().getId().toString()))
                .andExpect(jsonPath("$.workspace.id").value(second.workspace().getId().toString()));
    }

    @Test
    void csrfProtectsAuthenticatedLogoutAndNoSessionLogoutIsIdempotent() throws Exception {
        UserWorkspaceCreationResult user = creationService.createOrReuse(
                unique("issuer"), unique("subject"), "CSRF User"
        );
        Authentication authentication = internalAuthentication(user);
        MockHttpSession session = authenticatedSession(user, "CSRF User");

        mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.csrfToken").isString());
        String csrfToken = (String) mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"csrfToken\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/v1/auth/logout").session(session)
                        .header("X-CSRF-Token", csrfToken)
                        .header("Origin", "http://localhost:8080"))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void invalidOriginAndDisabledUsersCannotUseAnApplicationSession() throws Exception {
        UserWorkspaceCreationResult user = creationService.createOrReuse(
                unique("issuer"), unique("subject"), "Disabled User"
        );
        MockHttpSession session = authenticatedSession(user, "Disabled User");
        String csrfToken = (String) mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"csrfToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(post("/api/v1/auth/logout").session(session)
                        .header("X-CSRF-Token", csrfToken)
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        jdbcTemplate.update("UPDATE users SET disabled_at = CURRENT_TIMESTAMP WHERE id = ?", user.user().getId());
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        assertTrue(session.isInvalid());
    }

    @Test
    void openApiIsAvailableToAnAuthenticatedSessionAndDocumentsFilterRoutes() throws Exception {
        UserWorkspaceCreationResult user = creationService.createOrReuse(
                unique("issuer"), unique("subject"), "OpenAPI User"
        );
        MockHttpSession session = authenticatedSession(user, "OpenAPI User");
        mockMvc.perform(get("/v3/api-docs").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/auth/login")))
                .andExpect(content().string(containsString("/api/v1/auth/callback/{registrationId}")))
                .andExpect(content().string(containsString("/api/v1/auth/logout")))
                .andExpect(content().string(containsString("/api/v1/auth/csrf")))
                .andExpect(content().string(containsString("/api/v1/me")));
        mockMvc.perform(get("/swagger-ui/index.html").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not("Authentication is required.")));
    }

    @Test
    void disabledOidcIdentityDoesNotSaveAUserSecurityContext() throws Exception {
        String issuer = "https://accounts.google.com";
        String subject = unique("disabled-subject");
        UserWorkspaceCreationResult user = creationService.createOrReuse(issuer, subject, "Disabled OIDC User");
        jdbcTemplate.update("UPDATE users SET disabled_at = CURRENT_TIMESTAMP WHERE id = ?", user.user().getId());

        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                request,
                response,
                oidcAuthentication(issuer, subject, "Disabled OIDC User", "google")
        );

        assertEquals(403, response.getStatus());
        assertTrue(session.isInvalid());
        assertFalse(response.getContentAsString().isBlank());
    }

    private MockHttpSession authenticatedSession(UserWorkspaceCreationResult user, String displayName) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(internalAuthentication(user));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        SecurityContextHolder.clearContext();
        return session;
    }

    private Authentication internalAuthentication(UserWorkspaceCreationResult result) {
        return new UsernamePasswordAuthenticationToken(
                new InternalUserPrincipal(result.user().getId(), result.user().getDisplayName()),
                null,
                List.of()
        );
    }

    private Authentication oidcAuthentication(String issuer, String subject, String name, String registrationId) {
        return oidcAuthentication(issuer, subject, name, registrationId, subject + "@example.test");
    }

    private Authentication oidcAuthentication(
            String issuer,
            String subject,
            String name,
            String registrationId,
            String email
    ) {
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "sub", subject,
                "name", name,
                "email", email
        );
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(300),
                claims
        );
        DefaultOidcUser user = new DefaultOidcUser(
                Set.of(new OidcUserAuthority(idToken)),
                idToken
        );
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
    }

    private Authentication oidcAuthenticationWithoutProfile(String issuer, String subject) {
        Map<String, Object> claims = Map.of("iss", issuer, "sub", subject);
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(300),
                claims
        );
        DefaultOidcUser user = new DefaultOidcUser(
                Set.of(new OidcUserAuthority(idToken)),
                idToken
        );
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
