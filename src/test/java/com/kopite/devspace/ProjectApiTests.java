package com.kopite.devspace;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ProjectApiTests {
    private static final String BASE = "/api/v1/projects";
    private static final String CREATE = "{\"name\":\" Project \",\"scope\":\"unity\",\"stack\":\" Java \"}";
    private final MockMvc mvc;
    private final UserWorkspaceCreationService users;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    @Autowired
    ProjectApiTests(MockMvc mvc, UserWorkspaceCreationService users, JdbcTemplate jdbc, JsonMapper json) {
        this.mvc = mvc;
        this.users = users;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Test
    void createReadUpdateArchiveUnarchiveAndSameBodyReplayFollowContract() throws Exception {
        var owner = owner();
        String body = """
                {"name":" Project ","scope":"unity","stack":" Java ","progress":12.1234567890123456789,
                 "subtitle":" kept ","currentMilestone":"memo","repositoryUrl":" https://example.com/repo "}
                """;
        String original = mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "flow").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.name").value("Project")).andExpect(jsonPath("$.stack").value("Java"))
                .andExpect(jsonPath("$.subtitle").value(" kept ")).andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.colorToken").value("unity")).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.repositoryUrl").value("https://example.com/repo"))
                .andExpect(jsonPath("$.workspaceId").doesNotExist()).andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var project = json.readTree(original);
        String id = project.get("id").asString();
        assertEquals(new BigDecimal("12.1234567890123456789"), jdbc.queryForObject("select progress from projects where id=?", BigDecimal.class, UUID.fromString(id)));
        assertEquals(project.get("createdAt"), project.get("updatedAt"));
        mvc.perform(get(BASE + "/" + id).session(owner.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":1,\"status\":\"archived\",\"scope\":\"server\",\"subtitle\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.status").value("archived")).andExpect(jsonPath("$.colorToken").value("server"))
                .andExpect(jsonPath("$.name").value("Project")).andExpect(jsonPath("$.subtitle").value(""))
                .andExpect(jsonPath("$.createdAt").value(project.get("createdAt").asString()));
        mvc.perform(get(BASE).session(owner.session())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(BASE).session(owner.session()).param("status", "archived"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(BASE + "/" + id).session(owner.session())).andExpect(status().isOk());
        mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":1,\"name\":\"stale\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":2,\"status\":\"active\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3));
        mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4));
        mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":4,\"name\":\"Project\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(5));
        mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "flow").content(body))
                .andExpect(status().isCreated()).andExpect(content().string(original));
        assertEquals(5L, dataRevision(owner));
        mvc.perform(mutation(delete(BASE + "/" + id), owner)).andExpect(status().isMethodNotAllowed());
        assertEquals(1L, count(owner));
    }

    @Test
    void foreignAndMissingResourcesAreIndistinguishableAndInjectedOwnershipCannotSelectWorkspace() throws Exception {
        var a = owner();
        var b = owner();
        String id = create(a, CREATE).get("id").asString();
        for (String target : List.of(id, UUID.randomUUID().toString())) {
            mvc.perform(get(BASE + "/" + target).session(b.session())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND")).andExpect(jsonPath("$.revision").doesNotExist());
            mvc.perform(mutation(patch(BASE + "/" + target), b).content("{\"revision\":999,\"status\":\"archived\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(get(BASE).session(b.session()).param("query", "Project")
                        .param("workspaceId", a.user().workspace().getId().toString()).header("userId", a.user().user().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(mutation(post(BASE), b).header("Idempotency-Key", "injection")
                        .param("workspaceId", a.user().workspace().getId().toString()).header("ownerUserId", a.user().user().getId()).content(CREATE))
                .andExpect(status().isCreated());
        assertEquals(1L, count(a));
        assertEquals(1L, count(b));
        for (String field : List.of("userId", "ownerUserId", "workspaceId")) {
            mvc.perform(mutation(post(BASE), b).header("Idempotency-Key", "body-injection")
                            .content(CREATE.substring(0, CREATE.length() - 1) + ",\"" + field + "\":\"" + a.user().workspace().getId() + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertEquals(1L, dataRevision(a));
        assertEquals(1L, dataRevision(b));
    }

    @Test
    void sessionCsrfOriginAndDisabledUserRejectionsDoNotWrite() throws Exception {
        var owner = owner();
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).contentType("application/json").content(CREATE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).session(owner.session()).header("Idempotency-Key", "no-csrf").contentType("application/json").content(CREATE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post(BASE).session(owner.session()).header("Idempotency-Key", "bad-csrf").header("X-CSRF-Token", "bad")
                        .contentType("application/json").content(CREATE)).andExpect(status().isForbidden());
        mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "bad-origin").header("Origin", "https://evil.example").content(CREATE))
                .andExpect(status().isForbidden());
        String id = create(owner, CREATE).get("id").asString();
        mvc.perform(patch(BASE + "/" + id).session(owner.session()).contentType("application/json").content("{\"revision\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(mutation(post("/api/v1/auth/logout"), owner)).andExpect(status().isNoContent());
        mvc.perform(get(BASE).session(owner.session())).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "invalid-session"))
                        .contentType("application/json").content(CREATE)).andExpect(status().isUnauthorized());
        assertEquals(1L, count(owner));
        assertEquals(1L, dataRevision(owner));
        var disabled = owner();
        jdbc.update("update users set disabled_at=now() where id=?", disabled.user().user().getId());
        mvc.perform(mutation(post(BASE), disabled).header("Idempotency-Key", "disabled").content(CREATE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        assertEquals(0L, count(disabled));
    }

    @Test
    void keysetPaginationHandlesOverOneHundredTiedRowsAndRejectsCursorReuse() throws Exception {
        var owner = owner();
        seed(owner, 105);
        JsonNode defaults = list(owner, Map.of());
        assertEquals(20, defaults.get("items").size());
        assertEquals(105, defaults.get("total").asInt());
        var ids = new HashSet<String>();
        String cursor = null;
        do {
            JsonNode page = list(owner, cursor == null ? Map.of("limit", "100") : Map.of("limit", "100", "cursor", cursor));
            assertEquals(105, page.get("total").asInt());
            for (var item : page.get("items")) assertTrue(ids.add(item.get("id").asString()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        assertEquals(105, ids.size());
        String firstCursor = defaults.get("nextCursor").asString();
        for (Map<String, String> params : List.of(Map.of("cursor", ""), Map.of("cursor", "tampered"),
                Map.of("cursor", firstCursor + "x"), Map.of("cursor", firstCursor, "scope", "server"),
                Map.of("cursor", firstCursor, "status", "all"), Map.of("cursor", firstCursor, "query", "changed"),
                Map.of("cursor", firstCursor, "limit", "10"))) {
            var request = get(BASE).session(owner.session());
            params.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
        mvc.perform(get(BASE).session(owner().session()).param("cursor", firstCursor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        for (String limit : List.of("0", "101", "-1", "1.5", "abc", "999999999999999999")) {
            mvc.perform(get(BASE).session(owner.session()).param("limit", limit))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertEquals(0L, dataRevision(owner)); // Fixture inserts aside, reads do not mutate business state.
    }

    @Test
    void filtersUseLiteralCaseInsensitiveNameOrStackSearchAndEmptyResults() throws Exception {
        var owner = owner();
        var p = create(owner, "{\"name\":\"100%_!\\\\literal\",\"scope\":\"server\",\"stack\":\"MiXeD\"}");
        create(owner, CREATE);
        for (String query : List.of("%", "_", "!", "\\", "mixed", "LITERAL")) {
            assertEquals(1, list(owner, Map.of("query", query)).get("total").asInt());
        }
        assertEquals(1, list(owner, Map.of("scope", "unity")).get("total").asInt());
        assertEquals(1, list(owner, Map.of("scope", "server")).get("total").asInt());
        mvc.perform(mutation(patch(BASE + "/" + p.get("id").asString()), owner).content("{\"revision\":1,\"status\":\"archived\"}"))
                .andExpect(status().isOk());
        assertEquals(1, list(owner, Map.of("status", "active")).get("total").asInt());
        assertEquals(1, list(owner, Map.of("status", "archived")).get("total").asInt());
        assertEquals(2, list(owner, Map.of("status", "all")).get("total").asInt());
        var empty = list(owner, Map.of("query", "missing"));
        assertEquals(0, empty.get("total").asInt());
        assertEquals(0, empty.get("items").size());
        assertTrue(empty.get("nextCursor").isNull());
        for (String field : List.of("scope", "status")) {
            mvc.perform(get(BASE).session(owner.session()).param(field, "invalid")).andExpect(status().isBadRequest());
        }
    }

    @Test
    void strictInputValidationRejectsNullUnknownTypesAndOutOfRangeValuesWithoutWrites() throws Exception {
        var owner = owner();
        String id = create(owner, CREATE).get("id").asString();
        for (String field : List.of("name", "subtitle", "scope", "stack", "progress", "currentMilestone", "repositoryUrl", "status", "revision")) {
            mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":1,\"" + field + "\":null}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String body : List.of("{}", "{\"revision\":0}", "{\"revision\":-1}", "{\"revision\":1.5}", "{\"revision\":\"1\"}",
                "{\"revision\":9007199254740992}", "{\"revision\":1,\"progress\":101}", "{\"revision\":1,\"progress\":-0.1}",
                "{\"revision\":1,\"progress\":\"1\"}", "{\"revision\":1,\"name\":123}", "{\"revision\":1,\"name\":\" \"}",
                "{\"revision\":1,\"stack\":\" \"}", "{\"revision\":1,\"scope\":\"all\"}", "{\"revision\":1,\"status\":\"deleted\"}",
                "{\"revision\":1,\"repositoryUrl\":\"file:///tmp/file\"}", "{\"revision\":1,\"repositoryUrl\":\"https:///missing-host\"}",
                "null", "[]", "{malformed")) {
            mvc.perform(mutation(patch(BASE + "/" + id), owner).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.requestId").isString());
        }
        for (String field : List.of("id", "createdAt", "updatedAt", "colorToken", "archived", "milestone", "color", "unknown")) {
            mvc.perform(mutation(patch(BASE + "/" + id), owner).content("{\"revision\":1,\"" + field + "\":\"bad\"}"))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "null", "[]", "{\"name\":\"n\",\"scope\":\"unity\"}",
                "{\"name\":null,\"stack\":\"s\",\"scope\":\"unity\"}",
                "{\"name\":\"n\",\"stack\":\"s\",\"scope\":\"unity\",\"subtitle\":null}",
                "{\"name\":\"n\",\"stack\":\"s\",\"scope\":\"unity\",\"status\":\"active\"}")) {
            mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "invalid").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(mutation(post(BASE), owner).content(CREATE)).andExpect(status().isBadRequest());
        mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", " ").content(CREATE)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/1-1-1-1-1").session(owner.session())).andExpect(status().isBadRequest());
        assertEquals(1L, count(owner));
        assertEquals(1L, dataRevision(owner));
    }

    @Test
    void utf16BoundariesTrimmingAndIdempotencyPropertyOrderAreStable() throws Exception {
        var owner = owner();
        String body = json.writeValueAsString(Map.of("name", " " + "😀".repeat(50) + " ", "scope", "unity", "stack", "s"));
        create(owner, body);
        String tooLong = json.writeValueAsString(Map.of("name", "😀".repeat(51), "scope", "unity", "stack", "s"));
        mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "long").content(tooLong)).andExpect(status().isBadRequest());
        for (var field : Map.of("subtitle", 4000, "currentMilestone", 200, "stack", 200, "repositoryUrl", 2000).entrySet()) {
            var input = new java.util.HashMap<String, Object>(Map.of("name", "n", "scope", "unity", "stack", "s"));
            String value = field.getKey().equals("repositoryUrl") ? "https://example.com/" + "a".repeat(field.getValue() - 20) : "a".repeat(field.getValue());
            input.put(field.getKey(), value);
            create(owner, json.writeValueAsString(input));
            input.put(field.getKey(), "a".repeat(field.getValue() + 1));
            mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "long-" + field.getKey()).content(json.writeValueAsString(input)))
                    .andExpect(status().isBadRequest());
        }
        String first = mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "order").content(CREATE))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "order").content("{\"stack\":\" Java \", \"scope\":\"unity\", \"name\":\" Project \"}"))
                .andExpect(status().isCreated()).andExpect(content().string(first));
        mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", "order").content("{\"stack\":\" Java \",\"scope\":\"unity\",\"name\":\" Project \",\"subtitle\":\"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private record Owner(UserWorkspaceCreationResult user, MockHttpSession session, String csrf) {}

    private Owner owner() throws Exception {
        var user = users.createOrReuse("api-tests", UUID.randomUUID().toString(), "API Owner");
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new InternalUserPrincipal(user.user().getId(), "API Owner"), null, List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        String token = json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("csrfToken").asString();
        return new Owner(user, session, token);
    }

    private MockHttpServletRequestBuilder mutation(MockHttpServletRequestBuilder request, Owner owner) {
        return request.session(owner.session()).contentType("application/json").header("X-CSRF-Token", owner.csrf());
    }

    private JsonNode create(Owner owner, String body) throws Exception {
        return json.readTree(mvc.perform(mutation(post(BASE), owner).header("Idempotency-Key", UUID.randomUUID().toString()).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(Owner owner, Map<String, String> parameters) throws Exception {
        var request = get(BASE).session(owner.session());
        parameters.forEach(request::param);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long count(Owner owner) {
        return jdbc.queryForObject("select count(*) from projects where workspace_id=?", Long.class, owner.user().workspace().getId());
    }

    private long dataRevision(Owner owner) {
        return jdbc.queryForObject("select data_revision from workspaces where id=?", Long.class, owner.user().workspace().getId());
    }

    private void seed(Owner owner, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    insert into projects(id,workspace_id,name,scope,stack,created_at,updated_at)
                    values(?,?,'Tied','unity','Java','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')
                    """, UUID.randomUUID(), owner.user().workspace().getId());
        }
    }
}
