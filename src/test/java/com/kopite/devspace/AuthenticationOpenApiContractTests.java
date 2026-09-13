package com.kopite.devspace;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="server.servlet.session.cookie.name=contract-review-session")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthenticationOpenApiContractTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserWorkspaceCreationService users;

    @Test void filterDocumentationUsesConfiguredCookieAndConditionalLogoutCsrf() throws Exception {
        var api=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("contract-review-session", api.at("/components/securitySchemes/sessionCookie/name").asString());
        var callback=api.path("paths").path("/api/v1/auth/callback/{registrationId}").path("get");
        var registration=callback.path("parameters").valueStream().filter(p->p.path("name").asString().equals("registrationId")).findFirst().orElseThrow();
        assertTrue(registration.path("required").asBoolean());
        assertEquals("path", registration.path("in").asString());
        var logout=api.path("paths").path("/api/v1/auth/logout").path("post");
        assertTrue(logout.path("security").valueStream().anyMatch(s->s.isObject()&&s.isEmpty()));
        assertFalse(logout.path("parameters").get(0).path("required").asBoolean());
        assertTrue(logout.path("parameters").get(0).path("description").asString().contains("Required when authenticated"));
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent()).andExpect(cookie().maxAge("contract-review-session",0));
        mvc.perform(get("/api/v1/auth/callback/google").param("error","access_denied").param("state","unmatched"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/?authError=login_failed"));
        mvc.perform(get("/api/v1/auth/login").param("returnTo","/tasks"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/oauth2/authorization/google?returnTo=/tasks"));
    }

    @Test void CsrfNeedsNoQueryTokenAndErrorsDoNotUseSuccessSchemas() throws Exception {
        var api=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var csrf=api.path("paths").path("/api/v1/auth/csrf").path("get");
        assertTrue(csrf.path("parameters").isMissingNode()||csrf.path("parameters").isEmpty());
        for(String path:List.of("/api/v1/auth/csrf","/api/v1/me")) {
            assertEquals("#/components/schemas/ApiError",api.path("paths").path(path).path("get").at("/responses/401/content/application~1json/schema/$ref").asString());
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isMap()).andExpect(jsonPath("$.requestId").isString());
        }
        var user=users.createOrReuse("openapi-review",UUID.randomUUID().toString(),"Review");
        var session=new MockHttpSession(); var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(user.user().getId(),"Review"),null,List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        String body=mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.csrfToken").isString()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-Token",json.readTree(body).path("csrfToken").asString()))
            .andExpect(status().isNoContent());
    }
}
