# PLAN-0004: Project API

Status: `completed`

## Goal

Implement the workspace-owned Project resource according to [backend-implementation-spec.md](../../backend-implementation-spec.md), especially sections 1, 2, 4 and 9, and [backend-development-guide.md](../../backend-development-guide.md). Deliver persistence, authenticated REST operations, validation, concurrency protection, durable create retries, PostgreSQL integration tests and verified OpenAPI documentation.

Approved by the user with the proposed contract and clarifications accepted. Execution proceeds in session order; unfinished validation remains unchecked.

## Scope

- Include Project creation, detail, filtered cursor list, partial update, archive and unarchive; request/response DTOs; workspace authorization; migrations; revision and idempotency support needed by these endpoints.
- Follow the architecture, Spring Boot, API, database, Flyway, security, domain and application guides under `docs/agents/`. Use `project/presentation`, `project/application`, `project/domain`, and `project/infrastructure` under `com.kopite.devspace`, with domain/JPA entities, inward dependencies and application-owned transactions.
- Reuse Java 21, Spring Boot, JPA, PostgreSQL, Flyway, Bean Validation, Spring Security, springdoc and Testcontainers already present. No new dependency, framework or infrastructure service is proposed. Do not introduce event buses, separate persistence models or full CQRS infrastructure.
- Exclude frontend changes, Project deletion, child resource APIs, overview/statistics endpoints, workspace editing, imports, repository network access and deployment features. Preserve future child relationships through the schema and ownership rules; do not create placeholder child tables or endpoints.
- Preserve the approved PLAN-0003 authentication baseline, including its in-process Servlet session and idle-timeout limitations. This plan does not implement shared sessions or custom absolute expiration.

## Repository Review

- Reviewed the root `AGENTS.md`, plan guide/runtime template, relevant backend guides, both backend contract documents, PLAN-0003, current source/configuration, V1 and existing test structure. The working tree was clean before plan creation.
- V1 creates `users`, `auth_identities` and `workspaces`. Workspace already has public `revision` (initial 1) and internal `data_revision` (initial 0), but no business mutation or locking methods yet. No Project entity, migration, controller, cursor support or durable idempotency store exists.
- `CurrentUserService.resolve` resolves an internal user ID to its owner workspace. `InternalUserPrincipal`, the authentication filter, session CSRF, Origin checks and `/api/v1/me` are available. Reuse these boundaries rather than accepting client-selected ownership.
- Existing security errors have `{code,message,fieldErrors,requestId}`; a general MVC validation/business exception handler is still needed. Keep security responses compatible when adding the minimal shared error representation.
- Flyway and Hibernate `ddl-auto: validate` are configured. Existing tests use PostgreSQL 16.4 Testcontainers and Spring Security/MockMvc. PLAN-0003 records completed validation; this planning task does not rerun or claim new runtime validation.
- No frontend source or `frontend-contract-gap-analysis.md` was found in this repository. Frontend differences below come from the specification's section 9 and the development guide, not a verified inspection of frontend code. The development guide's statement that no backend exists predates the implementation; actual source and completed plans establish the current baseline.

## Proposed Project API Contract

All paths use `/api/v1`, UTF-8 JSON, authenticated session ownership and `Cache-Control: no-store`. Mutations use the existing `X-CSRF-Token` and Origin checks.

| Method and path | Input | Success |
| --- | --- | --- |
| POST `/projects` | `CreateProjectRequest` and required `Idempotency-Key` | 201, full `ProjectResponse` |
| GET `/projects/{id}` | Server-issued Project UUID | 200, full resource, including archived Projects |
| GET `/projects` | `scope`, `query`, `status`, `cursor`, `limit` | 200, `ProjectListResponse` |
| PATCH `/projects/{id}` | `UpdateProjectRequest`, including required `revision` | 200, full updated resource |

There is no DELETE, restore endpoint, PUT replacement or separate archive action. Archive with `{ "status": "archived", "revision": 1 }`; unarchive with `status=active`. Archiving preserves the Project and all connected data. Archived Projects remain editable; `PROJECT_ARCHIVED` is for future child operations that require an active target, not a blanket prohibition on Project edits.

### Request and response DTOs

| Field | Create | PATCH when present | Response |
| --- | --- | --- | --- |
| `name` | Required, nonblank, trim, max 100 | Same constraint | String |
| `subtitle` | Optional, default `""`, max 4000 | String, `""` clears | String |
| `scope` | Required, `unity` or `server` | Same enum | Same enum |
| `stack` | Required, nonblank, max 200 | Same constraint | String |
| `progress` | Optional, default 0, numeric 0–100 inclusive, decimals allowed | Same numeric constraint | Number |
| `currentMilestone` | Optional, default `""`, max 200 | String, `""` clears | Goal memo string, not a milestone ID |
| `repositoryUrl` | Optional, default `""`, trim, max 2000 | String, `""` clears | String |
| `status` | Not writable; server sets `active` | `active` or `archived` | Same enum |
| `revision` | Not writable; server sets 1 | Required positive safe integer | Positive safe integer |
| `id`, `createdAt`, `updatedAt` | Not writable | Not writable | UUID string and UTC ISO 8601 timestamps |
| `colorToken` | Not writable | Not writable | Server-derived token for current scope |

- Use separate `CreateProjectRequest`, presence-aware `UpdateProjectRequest`, `ProjectResponse`, list-query input and `ProjectListResponse` DTOs. Never serialize JPA entities. Response fields exclude user IDs, workspace IDs, internal counters and authentication information.
- PATCH changes only supplied fields. Distinguish omission from explicit null without introducing a dependency. Proposed clarification: all Project fields reject explicit null, including optional strings, because their empty representation is `""`; defaults apply to omission on create only.
- All length limits use UTF-16 code units, including supplementary characters. Validate normalized required text with Bean Validation at the boundary and protect invariants in the domain. Proposed normalization: trim `stack` as a required short label, preserve `subtitle` and `currentMilestone` contents, and trim `name`/URL as required by the common contract.
- Nonempty repository URLs must parse as absolute http/https URLs with a host. Do not fetch the URL. The specification defers a repository user-info restriction to later integration; do not silently import the stricter link-URL rule into this API.
- Reject unknown write fields and server/derived fields with 400, including `userId`, `ownerUserId`, `workspaceId`, `archived`, `milestone` and `color`. Reject incorrect JSON types, invalid enums, malformed IDs, noninteger revisions, invalid numeric ranges and malformed JSON through the common error format. Avoid permissive string-to-number coercion.
- Proposed clarification: a valid revision-only PATCH or assignment of existing values counts as a successful update and increments revision once, consistent with the specification's per-success rule.

### List behavior

- Defaults: `scope=all`, `status=active`, `query=""`, `limit=20`, no cursor. Allowed scope: `all|unity|server`; status: `active|archived|all`; limit: integer 1–100. Invalid parameters return 400 `VALIDATION_ERROR`.
- Apply workspace, scope and status filters together; query searches `name` OR `stack` with case-insensitive literal substring semantics. Escape `%`, `_` and the SQL escape character. Do not substitute full-text search or load all Projects into memory.
- Sort by `createdAt DESC, id DESC`, with keyset pagination. Return `{ "items": [...], "total": 123, "nextCursor": "..." }`; `total` counts all matching owned rows, not the page. Empty results use `items=[]`, `total=0`, `nextCursor=null`.
- Cursor binds the workspace, effective filters, fixed ordering and last timestamp/ID. Proposed implementation is a versioned integrity-protected opaque token using existing JDK cryptography and an environment-configured signing key, never plain editable Base64 JSON. Invalid, tampered, cross-workspace or changed-filter cursors return 400 `INVALID_CURSOR` without disclosing ownership. Bind limit too for an unambiguous pagination request.
- Count and page use the same filters and a consistent read snapshot within a request. There is no snapshot guarantee across pages. No business writes occur on GET.

### Errors

Use a central MVC handler with the existing `{code,message,fieldErrors,requestId}` shape. Document 400 `VALIDATION_ERROR`/`INVALID_CURSOR`, 401 `AUTH_REQUIRED` (or `SESSION_EXPIRED` only when distinguishable by existing authentication), 403 `CSRF_INVALID`/`ACCOUNT_DISABLED`, 404 `RESOURCE_NOT_FOUND`, and 409 `REVISION_CONFLICT`/`IDEMPOTENCY_KEY_REUSED` on applicable operations. Preserve the existing Origin rejection behavior. Errors must not expose SQL, stack traces, foreign resources or foreign revisions.

## Proposed Database Schema

Add focused versioned migrations, provisionally `V2__create_projects.sql` and `V3__create_project_create_idempotency.sql`. Confirm the next free versions before execution; never modify V1 or applied migration history.

| `projects` column | PostgreSQL type / constraints |
| --- | --- |
| `id` | UUID primary key, server-generated |
| `workspace_id` | UUID NOT NULL, FK to workspaces(id), ON DELETE RESTRICT |
| `name` | TEXT NOT NULL, nonblank, maximum 100 characters as a complementary DB guard |
| `subtitle` | TEXT NOT NULL DEFAULT '', maximum 4000 |
| `scope` | TEXT NOT NULL, CHECK in ('unity','server') |
| `stack` | TEXT NOT NULL, nonblank, maximum 200 |
| `progress` | NUMERIC NOT NULL DEFAULT 0, CHECK between 0 and 100; no arbitrary decimal scale restriction |
| `current_milestone` | TEXT NOT NULL DEFAULT '', maximum 200 |
| `repository_url` | TEXT NOT NULL DEFAULT '', maximum 2000 |
| `status` | TEXT NOT NULL DEFAULT 'active', CHECK in ('active','archived') |
| `revision` | BIGINT NOT NULL DEFAULT 1, CHECK between 1 and 9007199254740991 |
| `created_at`, `updated_at` | TIMESTAMP WITH TIME ZONE NOT NULL, server-managed |

- Add UNIQUE `(workspace_id,id)` for future child composite foreign keys. Names are not unique. Do not add `deleted_at`, a milestone FK, child collections or persisted `color_token`; derive the token from scope.
- Add `(workspace_id,created_at DESC,id DESC)` for all-status lists and `(workspace_id,status,created_at DESC,id DESC)` for the default active list. Add further indexes only after query evidence; B-tree indexes do not solve arbitrary substring search.
- PostgreSQL character counts differ from UTF-16 lengths: DB length checks are complementary upper bounds, while application validation enforces the precise external contract. Test non-BMP boundaries explicitly.
- Future tasks/journals/milestones must reference `(workspace_id,project_id)` to `projects(workspace_id,id)` with deletion restricted. Their migrations and runtime tests belong to their later plans.
- The supporting idempotency table stores workspace FK, HTTP method, canonical path, key, request hash, success HTTP status, exact response JSON/text, created timestamp and expiry timestamp; unique `(workspace_id,method,path,key)` and an expiry index. The unique workspace owner already scopes the user, so duplicating owner identity is unnecessary. Store successful outcomes atomically with the Project, not a separately committed pending outcome.
- Keep idempotency storage scoped to the current Project requirement through existing domain/port/adapter patterns; do not create a generic execution framework. Retain results for 24 hours. Expired-row replacement/cleanup must respect the same database locking and uniqueness rules without adding a scheduler dependency.

## Revision, Ownership and Transaction Strategy

1. Resolve the authenticated internal user to its current personal workspace using the existing authentication/application boundary. Client body/query/header ownership identifiers never select a workspace; reject ownership fields in write DTOs.
2. Every Project detail, mutation, count and list repository operation includes workspace ID. An inaccessible ID and a nonexistent ID both return 404 before reading or comparing a resource revision. No unscoped existence probe or workspace transfer is allowed.
3. Commands lock the workspace row first, then the owned Project row where applicable, following the development guide. Check expected revision and mutate in one transaction. Use explicit domain revision advancement under the lock; do not combine manual increments with an automatic JPA version increment.
4. Creation sets Project revision=1 and matching creation/update timestamps. Each successful PATCH, including archive/unarchive, advances revision exactly once and updates `updatedAt`, preserving `createdAt`. Guard overflow before mutation. Two independent updates using the same revision yield one success and one 409.
5. Each actual successful create/PATCH also increments workspace `dataRevision` once in that transaction. Proposed clarification: do not change the workspace's public revision or metadata timestamp for Project-only changes; those represent workspace metadata. Failed commands, reads and idempotent replays do not advance either business counter.
6. POST requires a nonblank `Idempotency-Key`. Proposed transport bound: 1–128 visible ASCII characters. Under the workspace lock, check the unique key before creation; serialize competing requests across application instances through PostgreSQL. Hash a deterministic representation of the validated request, preserving submitted field presence and values while ignoring JSON property order/formatting. Pin these equivalence rules with tests.
7. Within 24 hours, the same key/body replays the stored 201 and full original response without a second Project or counter increment; a different body returns 409. Revalidate authentication and workspace ownership before replay. Rollback leaves neither a Project, a counter change nor a successful idempotency result. A replay after subsequent PATCH still returns the original creation outcome; clients can GET current state. No replay recreates a missing resource. After expiry the key can represent a new request, so automatic frontend retries must stop for result reconciliation.
8. Project name/scope are authoritative. Future child response/search/statistic queries must derive them from the current Project rather than stale copied text. Archive and future child-link creation must share the prescribed locks; those child race tests cannot be executed until child APIs exist.

## Frontend Integration Differences (Later Work)

| Documented local behavior | Required integration change |
| --- | --- |
| `archived` boolean | Convert to/from `status=active|archived`; archive/unarchive via revision-bearing PATCH |
| `milestone` | Map to `currentMilestone`; keep the memo independent of actual milestone resources |
| `color` | Consume server `colorToken`, use a fallback for unknown tokens, never submit display colors |
| Client-generated resource UUID | Use returned server ID; maintain a separate retry Idempotency-Key |
| Boolean synchronous saves | Use Promise mutations, pending/error state, full successful response and stored revision; close forms only after success |
| Local overwrites | Send last read revision; preserve drafts on 409 and reconcile explicitly, without silent retry using a newer revision |
| Whole-array reads and client counts | Consume cursor pages and `total`; later overview statistics are a separate endpoint, not `items.length` |
| Default active selection list | Fetch selected archived Projects by detail; absence from the first page does not imply missing data |
| Local ownership/storage assumptions | Establish session with `/me`, send CSRF, scope caches to user/workspace and discard late responses after account changes |
| Local fixtures or stored records | Do not use them as successful API error fallbacks or upload automatically; explicit import remains separate |
| Project edits reflected locally | Invalidate Project detail/list/pickers and, once integrated, child/search/statistic caches after rename/scope/archive changes |

Preserve manual fractional progress, server timestamps, UTF-16 limits and optional-string clearing in frontend DTO adapters. No frontend files are modified by this plan or its backend execution sessions.

## Execution Sessions

Execute sessions in order only after approval. Do not start the next session until the current session's implementation and validation are complete. Check items only after actual completion and record evidence as execution progresses.

### Session 1: Project persistence and workspace mutation support

Objective: Establish the Project aggregate, database integrity and owned transactional access.

Implementation:

- [x] Add the Project migration, entity/invariants, scoped repository abstraction and JPA adapter.
- [x] Add only the workspace locking/counter behavior required for Project commands, preserving current user creation and workspace metadata behavior.
- [x] Implement explicit Project revision and audit-field handling, scope/status values and the approved color-token mapping.

Validation:

- [x] Run PostgreSQL Testcontainers persistence tests for fresh migrations, Hibernate validation, constraints/defaults, numeric fidelity and workspace ownership FK.
- [x] Verify upgrade from a populated V1 database preserves existing users/identities/workspaces; validate migration checksums without repair/baseline.
- [x] Verify Project lifecycle, fractional progress, UTF-16 limits and atomic counter behavior; rerun affected user/workspace tests.

### Session 2: Durable Project creation and revision-safe commands

Objective: Make create/update/archive use cases atomic and safe under retries and competing requests.

Implementation:

- [x] Add the idempotency migration/store and Project command services using workspace-first locking.
- [x] Implement owned lookup, expected revision checks, partial-change semantics, archive/unarchive and durable 24-hour create replay.
- [x] Add application/domain errors for validation, not found, stale revision and conflicting idempotency reuse.

Validation:

- [x] Use separate transactions/connections and controlled concurrency: same-revision PATCH yields one success; same-key create yields one row and one original outcome; different-body same-key yields a conflict.
- [x] Assert database state and both counters after success, replay, forced rollback, archive/unarchive and rejected foreign-ID updates; repeat replay through a fresh service context to prove DB-backed persistence.
- [x] Test expiry boundaries and original-response replay after later edits; no partial success or extra dataRevision increment is permitted.

### Session 3: Authenticated REST DTOs and cursor queries

Objective: Expose the four specified operations through the existing security boundary.

Implementation:

- [x] Add request/response DTOs, presence-aware PATCH binding, Bean Validation and centralized contract errors.
- [x] Add Project controllers, command/query separation, filtered counts/keyset lists and integrity-protected cursor configuration.
- [x] Apply session-resolved ownership, no-store responses and the existing CSRF/Origin protections.

Validation:

- [x] Run MockMvc integration tests against PostgreSQL for complete create/read/list/update/archive/unarchive flows, status codes and response fields.
- [x] With A/B persisted users and sessions, verify B cannot read/update/archive A's Project, foreign and missing IDs are indistinguishable, and B's search/total never include A's data. Test ownership injection in body/query/header and confirm no context change or disclosure.
- [x] Verify anonymous, disabled, logged-out/invalid-session, missing/invalid CSRF and rejected-Origin requests cause no Project mutation; use the existing session test pattern and obtain CSRF through its endpoint.
- [x] Exercise >100 owned Projects, tied timestamps, every filter/default, empty results, literal wildcard/escape searches, final cursor=null, malformed/tampered/foreign/filter-reused cursors and invalid limits.
- [x] Test omitted versus null fields, string/type/enum/URL boundaries, unknown/server fields, required idempotency keys and unsafe/fractional/missing revisions. Verify revision-only/same-value updates follow the approved clarification.

### Session 4: OpenAPI, Swagger and final regression validation

Objective: Verify the external contract and record reproducible backend validation.

Implementation:

- [x] Document operations, DTO examples, required headers, filters, security and applicable errors through existing springdoc support.
- [x] Describe PATCH presence/null behavior, decimal progress, read-only fields, limits, revision and idempotency semantics accurately, supplementing Bean Validation only where needed.
- [x] Record the final contract, frontend handoff notes and validation evidence in this plan without changing frontend code or agent guides.

Validation:

- [x] Assert authenticated `/v3/api-docs` contains exactly the supported Project operations and accurate request/response schemas, enums, required fields, defaults, constraints, nullability and HTTP codes.
- [x] Verify real Swagger UI rendering under authentication; inspect the Project operations and perform a CSRF-enabled create/read/update/list flow with the documented headers. Do not treat an HTML 200 alone as rendering evidence.
- [x] Run the full Java 21 `./gradlew.bat clean build --no-daemon` with Docker available and existing test profile; inspect test reports for failures and unintended skipped integration tests.

## Final Validation

- [x] All Execution Sessions are complete, with PostgreSQL Testcontainers evidence and no failures caused by this plan remaining.
- [x] Fresh and populated-V1 migration paths pass; Hibernate schema validation and existing authentication/user/workspace regressions pass.
- [x] Ownership, stale-update concurrency, durable idempotency, counters, filtering/pagination and input-boundary tests pass against the real PostgreSQL database.
- [x] No frontend modifications, delete API, child API placeholders, dependency additions or unrelated refactors are included.

### Execution evidence

- Session 1 implementation is written: V2, Project entity/validated values/scoped repository, workspace lock and business counter mutation, plus `ProjectPersistenceTests` covering persistence, lifecycle, constraints and populated-V1 upgrade.
- Docker Desktop was started and Docker reported version 28.3.2. The first sandboxed Gradle attempt was denied network access; the authorized outside-sandbox retry reached compilation.
- Command: Java 21 `./gradlew.bat test --tests com.kopite.devspace.ProjectPersistenceTests --tests com.kopite.devspace.UserWorkspacePersistenceTests --tests com.kopite.devspace.UserWorkspacePersistenceRulesTests --no-daemon`.
- Result: `compileJava` passed; `compileTestJava` failed at `ProjectPersistenceTests.java:70`. Passing generic `TransactionTemplate.execute(...)` directly to overloaded `assertTrue(...)` produces incompatible inference bounds (`BooleanSupplier` versus `Boolean`). No tests ran in this attempt; older test reports are not evidence for this change.
- On resume, the result was assigned to an explicit `Boolean` before asserting. The same complete Session 1 command passed on 2026-09-12, including all four Project persistence tests and the existing user/workspace tests. Fresh schema startup/Hibernate validation and populated-V1 upgrade/checksum validation passed. The previous compilation blocker is resolved.
- Session 2 passed: Java 21 `./gradlew.bat test --tests com.kopite.devspace.ProjectCommandTests --tests com.kopite.devspace.ProjectPersistenceTests --no-daemon`, 10 tests, zero failures/errors/skips. This includes controlled two-connection races, fresh command service/store instances over persisted replay data, expiry boundary, rollback, disabled-owner replay rejection and missing-row non-resurrection. V1-to-latest migration validation now also includes V3.
- Session 3 implementation is written, including strict streaming request deserialization (preserving decimal precision), scoped read/query services, HMAC-protected cursors, DTOs and central MVC errors. `PROJECT_CURSOR_SIGNING_KEY` is required outside tests (at least 32 UTF-8 bytes); `.env.example` documents coordinated rotation and first-page restart after rotation.
- Session 3 validation command: Java 21 `./gradlew.bat test --tests com.kopite.devspace.ProjectApiTests --tests com.kopite.devspace.ProjectCommandTests --tests com.kopite.devspace.SecurityIntegrationTests --no-daemon`. Compilation passed. Result on 2026-09-12: 26 tests, 25 passed, 1 failed, zero errors/skips (Project API 6/7, commands 6/6, existing security 13/13).
- Resolved Session 3 blocker: `ProjectApiTests.sessionCsrfOriginAndDisabledUserRejectionsDoNotWrite`, line 129, expected anonymous POST `/api/v1/projects` to return 401, but initially received 403. The existing `ApiAccessDeniedHandler` mapped CSRF exceptions to 403 before authorization could invoke the authentication entry point.
- Resolution: unauthenticated business mutations now use 401 `AUTH_REQUIRED`, while authenticated CSRF/Origin failures retain 403 `CSRF_INVALID`. The unchanged contract test passed on rerun.
- On resume, the shared `ApiAccessDeniedHandler` delegates unauthenticated failures to `ApiAuthenticationEntryPoint` before handling authenticated CSRF/access-denied failures. CSRF and Origin protection remain enabled. No Project-specific exception or bypass was added.
- Full Session 3 rerun passed on 2026-09-12: 27 tests, zero failures/errors/skips (Project API 7/7, commands 6/6, security 14/14). This includes the previously blocked security flow and shared anonymous POST/PATCH/PUT/DELETE checks across protected Project and workspace paths. The Session 3 blocker is resolved.
- Session 4 documentation changes are written: operation response codes/error schemas, CSRF and idempotency headers, UUID parameters, DTO examples, response required/read-only fields, unknown-field rejection and optional string defaults. `ProjectOpenApiTests` is written to verify the generated contract and save its JSON under `build/reports/project-api/` after successful application startup.
- Session 4 validation command: Java 21 `./gradlew.bat test --tests com.kopite.devspace.ProjectOpenApiTests --no-daemon`.
- Resolved Session 4 compilation blocker: `compileJava` initially failed at `ProjectOpenApiConfiguration.java:16` because a raw Swagger schema lookup made the property type `Object`, which has no `setDefault(String)` method.
- Resolution: the parent and child schemas use checked `Schema<?>` references with a string-schema guard. The approved empty-string defaults are preserved without raw types or unchecked casts.
- On resume, `ProjectOpenApiConfiguration` now uses explicit `Schema<?>` references for the parent and property, with a string-schema guard before assigning the empty default. No unchecked cast or suppression was added, and the API contract is unchanged. Production and test compilation passed on the Session 4 rerun; the typing blocker is resolved.
- Resolved Session 4 contract blocker: a later rerun found that generated list schemas omitted the runtime defaults `scope=all` and `status=active`.
- Resolution: both defaults are explicit in the parameter schemas. The complete OpenAPI test passed without weakening its assertions.
- On resume, the list parameter schemas were updated to document the runtime defaults `scope=all` and `status=active`. `ProjectOpenApiTests` then passed (1 test, zero failures/errors/skips), including endpoint/method, security, headers, status codes, DTO required/read-only fields, enums, constraints, nullability and defaults. The missing-default blocker is resolved.
- A test-runtime-only browser validation bootstrap and static flow page were added for real Chrome validation. They are intended to authenticate a browser session, render Swagger UI, and execute CSRF-enabled create/read/update/list requests with the documented headers; they are not part of production runtime resources.
- Resolved Session 4 browser-fixture compilation blocker: Java initially inferred `FilterRegistrationBean<<anonymous OncePerRequestFilter>>`, which could not be returned as `FilterRegistrationBean<OncePerRequestFilter>`.
- Resolution: the local registration variable uses the explicit correct generic type. The test application and real Chrome validation then passed.
- On final resume, `FilterRegistrationBean<OncePerRequestFilter>` was declared explicitly. Compilation passed without raw types, unchecked casts or warning suppression.
- A test-profile `bootTestRun` started the real application on port 18080 with PostgreSQL 16.4 Testcontainers, applied and validated V1–V3, and completed Hibernate schema validation. The initial launch without the `test` profile ended before opening the port because test profile configuration was not active; rerunning with `SPRING_PROFILES_ACTIVE=test` supplied the test-only cursor key and succeeded.
- Chrome headless rendered a 52,517-byte authenticated Swagger DOM containing the Projects tag and all four approved operation summaries: create, list, detail and update/archive. The authenticated `/v3/api-docs` contract test separately verified `X-CSRF-Token`, `Idempotency-Key`, status codes and schemas; unauthenticated `/v3/api-docs` returned 401.
- The real-browser flow returned `PROJECT_API_BROWSER_VALIDATION_PASS`: it acquired `/api/v1/auth/csrf`, created a Project with `X-CSRF-Token` and `Idempotency-Key`, read it, archived it with revision 1, and listed archived Projects with revision 2 and total 1. Chrome exited 0 with an empty stderr capture.
- Full suite command: Java 21 `./gradlew.bat test --no-daemon --rerun-tasks`; 47 tests across 8 suites passed with zero failures, errors or skips.
- Final command: Java 21 `./gradlew.bat clean build --no-daemon`; all 9 tasks executed successfully, including fresh compilation, assembly, all 47 PostgreSQL-backed tests and checks. Test reports again show zero failures, errors or skips.
- Final scope review found only backend source/configuration, Flyway migrations, backend tests, this Plan, and `.env.example`; no frontend file, dependency, Project delete endpoint, child API placeholder or agent guide changed. Sessions 1–4 and Final Validation are complete; no unresolved implementation blocker remains.

### API Validation

- [x] Implemented endpoints are present in `/v3/api-docs`.
- [x] Request and response schemas match the approved backend contract.
- [x] Bean Validation constraints are reflected where applicable, with PATCH-specific behavior explicitly documented.
- [x] HTTP methods and response status codes match the contract.
- [x] Swagger UI renders the implemented endpoints correctly and supports the documented authenticated flow.

## Clarifications and Unresolved Decisions

- The specification does not name actual `colorToken` values. Proposed Project-local mapping: `unity -> unity`, `server -> server`. Approval accepts these semantic tokens; the frontend maps them to presentation later.
- This plan proposes explicit-null rejection, stack trimming, same-value/revision-only PATCH increments, idempotency key format/hash equivalence and workspace metadata behavior where the source contract is not fully explicit. Review these as part of plan approval; they are not existing repository-wide rules.
- Cursor integrity requires an environment-provided signing key and deployment consistency across instances. Select its configuration name and rotation procedure during implementation; never hardcode a production key. Durable idempotency uses existing PostgreSQL, independent of the current in-memory session limitation.
- No unresolved choice between archive and delete exists: the specification explicitly requires archive/unarchive and provides no Project delete API.
- Frontend source verification and future child rename/scope/archive concurrency validation remain later integration work. They do not justify weakening the Project contract or claiming those later features are implemented.
- No new architectural decision record or recurring agent-guide change is proposed. Operational rate limits/quotas, shared sessions and deployment readiness remain outside this Project plan.

## Completion

Completed on 2026-09-12 after every implementation and validation item passed. The Plan is listed in `docs/plans/README.md` in execution order.
