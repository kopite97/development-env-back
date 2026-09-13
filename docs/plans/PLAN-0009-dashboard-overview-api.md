# PLAN-0009: Dashboard and Overview API

Status: `completed`

## Goal

Implement GET `/api/v1/overview`, GET `/api/v1/dashboards/home` and PUT `/api/v1/dashboards/home` according to [backend-implementation-spec.md](../../backend-implementation-spec.md), especially sections 1, 2, 7, 8 and 9, supported by [backend-development-guide.md](../../backend-development-guide.md). Deliver owned, snapshot-consistent Project/Task totals and versioned, atomically saved Home Dashboard configuration with safe concurrent first saves.

Approved by the user on 2026-09-13, including the proposed contract, default configuration and all explicit clarifications. Execute sessions in order under the self-recovery policy below.

## Scope and Initial Review

- Reviewed the plan guide/runtime template, backend architecture, Spring/API/database/Flyway/security and implementation guides, the two source documents, PLAN-0007/0008 execution policy, existing Task statistics/query implementation, Project ownership repository and Workspace mutation model.
- Existing Java 21/Spring Boot, JPA/PostgreSQL, Flyway, Jackson, validation, Spring Security, springdoc and Testcontainers are sufficient. Use feature-local `overview` and `dashboard` packages with presentation/application/domain/infrastructure responsibilities where needed; do not create empty layers, a generic widget engine, new dependencies or infrastructure.
- `TaskQueryService` already runs read-only REPEATABLE_READ transactions; `stats` validates owned Project access and calls `TaskSearchRepository.counts`. `TaskSearchAdapter` groups statuses in SQL, joins Task to Project by both workspace and Project ID, and shares predicates with Task listing. Its aggregate query does not apply the filter's page limit. Reuse these semantics and the actual aggregation implementation.
- `ProjectRepository.findOwned/lockOwned` and Workspace locking/mutation support exist. `PersonalWorkspace.recordBusinessMutation` increments internal dataRevision; public Workspace revision/audits are separate. V1-V11 exist; provisionally reserve V12, rechecking availability at execution start.
- No Overview or Home Dashboard implementation/default-widget definition was found. The source documents specify a default layout but not its exact IDs/order/titles/sizes. No frontend source or referenced `frontend-contract-gap-analysis.md` was found; frontend differences below are document-derived, not frontend code inspection.
- PLAN-0008 records 150 passing tests and a successful clean build. These are prior evidence, not tests performed while creating this Plan. Initial `git status --short` was clean.
- Include only these three endpoints, Dashboard persistence, minimal Project aggregation and Task aggregation reuse, required DTOs/errors, tests and documentation. Exclude frontend edits, widget business-data hydration, deployments/provider calls, imports, workspace editing, additional dashboards, dashboard PATCH/DELETE/reset endpoints, Project/Task contract changes and deployment-wide quotas/body-limit infrastructure. Preserve unrelated changes, applied migrations, shared guides and historical Plans.

## Proposed Overview API Contract

GET `/api/v1/overview` returns 200 with exactly this shape (illustrative consistent counts):

```json
{
  "scope": "all",
  "projectId": null,
  "projects": {
    "total": 2,
    "archived": 1,
    "byScope": {
      "unity": {"total": 1, "archived": 1},
      "server": {"total": 1, "archived": 0}
    }
  },
  "tasks": {"todo": 2, "doing": 1, "done": 3, "total": 6},
  "asOf": "2026-09-13T00:00:00Z"
}
```

- Query accepts only `scope` (default `all`, allowed `all|unity|server`) and optional canonical UUID `projectId`. Proposed clarification: reject unsupported or repeated query parameters with 400 VALIDATION_ERROR, including query/status/projectStatus/deleted/limit/cursor and ownership selectors. Reject empty/invalid supplied values rather than silently applying defaults.
- Resolve authenticated User to its Workspace, then validate a supplied Project with an owned lookup before aggregation. Active and archived owned Projects are valid. Foreign/missing IDs return identical 404 RESOURCE_NOT_FOUND, even when scope would exclude the Project.
- Apply scope and projectId as an intersection, per the common filtering contract. A valid owned Project outside scope returns zero counts; echo the effective requested scope and projectId. Do not apply Dashboard's stored-widget scope normalization to this endpoint. A client selecting one Project across scopes calls scope=all.
- `projects.total` counts active Projects, not active plus archived. `projects.archived` counts archived Projects. Always return both byScope keys; excluded scopes have zeroes. Top-level Project counts equal the sums of their respective byScope values. A selected archived Project with matching scope gives total=0, archived=1.
- Task counts include only undeleted Tasks of the same owned/filter-matching Projects, across active and archived Projects. Always return todo/doing/done, with zeroes where absent, and total equal to their sum. Match `/tasks/stats` with the same scope/projectId, projectStatus=all and empty query; no status restriction, deleted rows, search restriction or page limit.
- Aggregation covers all matching database rows, independent of currently loaded Project/Task pages, widget limit, Project list default active status, UI search and UI archive tab. Do not calculate Project progress, task completion percentages, Journal/Milestone/Link counts or dashboard payloads.
- DTOs: `OverviewRequest`, `OverviewResponse`, `OverviewProjectCounts`, `OverviewProjectScopeCounts`, `OverviewProjectsByScope`, `OverviewTaskCounts`. Use explicit fixed fields for the externally fixed scope/status keys, nonnegative integer counts backed by Java long/PostgreSQL counts, nullable projectId and UTC Instant asOf. Never expose entities or internal ownership/dataRevision.

### Aggregation and consistent snapshot

- The Overview application query owns one read-only REPEATABLE_READ transaction covering CurrentUser/Workspace resolution, supplied Project ownership validation, Project grouped counts and Task grouped counts. All participating queries must join this transaction and database connection; no REQUIRES_NEW, asynchronous/parallel database calls, HTTP calls to the stats endpoint or separately committed subqueries.
- Group Project counts by current scope/status in PostgreSQL using workspace and optional Project predicates. Fill missing groups with zeroes; do not load Project entities or list pages to count them. Reuse the existing Task stats service within the outer transaction (scope/projectId, projectStatus=all, query empty, status absent, deleted=false). Its internal page-limit placeholder must not restrict counts. If a small extraction becomes necessary, keep the shared aggregation owned by Task and make both endpoints use it; do not duplicate SQL/predicates in Overview or create a generic cross-feature framework.
- Capture one server-clock asOf for the response within the query transaction. It is a response observation time, not a database commit watermark, revision or guarantee that later HTTP requests share the snapshot. The source sample and the example above both satisfy the Project byScope sum invariants. (Factual wording corrected during the standalone OpenAPI review; no contract change.)
- Existing ownership indexes are the starting point. Inspect generated SQL and representative PostgreSQL query plans; add an index only with concrete query evidence. Query count must remain independent of matching row count. GET never writes dashboard rows, counters or business data.

## Proposed Home Dashboard API Contract

| Method/path | Input | Success |
| --- | --- | --- |
| GET `/api/v1/dashboards/home` | No query parameters | 200 `HomeDashboardResponse` |
| PUT `/api/v1/dashboards/home` | Required `{schemaVersion,revision,widgets}` and X-CSRF-Token | 200 full `HomeDashboardResponse`, including on first save |

Response contains exactly `{id:"home",schemaVersion:1,revision,widgets}`. This resource-specific contract has no public UUID, createdAt/updatedAt or embedded business-data fields. No pagination, wrapper, Idempotency-Key requirement or separate reorder/reset action is introduced. Reject unsupported/repeated Dashboard query parameters.

- DTOs: `SaveHomeDashboardRequest`, `HomeDashboardResponse`, `DashboardWidgetRequest`, `DashboardWidgetResponse`, mapped to immutable application/domain configuration values. Separate request/response types and strict presence-aware parsing preserve omission of optional fields.
- schemaVersion is required, a JSON integer and currently exactly 1. A well-formed integer other than 1 yields 400 UNSUPPORTED_SCHEMA_VERSION; missing/null/noninteger/wrong-type values yield 400 VALIDATION_ERROR. Do not coerce, downgrade or infer a schema version from a bare array. It describes the configuration format, not a save counter.
- Request revision is required, a JSON safe integer 0..9007199254740991. Response revision is 0 only for an unsaved layout; persisted revisions are 1..9007199254740991. Reject strings, fractions, nulls and unsafe integers. Dashboard revision is independent of widget IDs, Workspace metadata revision and schemaVersion.
- PUT replaces the complete ordered widget array atomically. An empty array is a valid saved empty layout; distinguish it from no row/default layout. Omitted widgets, null arrays/elements and non-array widgets are invalid. A same-value PUT is a successful save and advances revision once, consistent with existing per-success mutation semantics.
- Reject unknown/duplicate JSON keys, wrong types and non-object bodies at all levels. Reject client id at the top level, audit/ownership fields, business resource payloads and unsupported widget settings. Errors identify indexed fields where useful, without revealing foreign Project details.

### Unsaved default layout

GET when no row exists returns a deterministic in-memory default with id=home, schemaVersion=1, revision=0. Do not insert a Dashboard, backfill at login, advance dataRevision, seed sample business resources or mutate persistence through any GET. A Workspace can already contain business data while its layout remains unsaved: do not hide or clear those real resources. Dashboard GET returns configuration only; empty business data for a new workspace comes from its independently empty business APIs.

Proposed default configuration, because source documents do not enumerate it:

| Array order | id | type | title | scope | size |
| --- | --- | --- | --- | --- | --- |
| 0 | home-overview | overview | 프로젝트 개요 | all | wide |
| 1 | home-board | board | 작업 보드 | all | wide |
| 2 | home-deploy | deploy | 운영 | all | medium |
| 3 | home-links | links | 바로가기 | all | small |
| 4 | home-journal | journal | 개발 일지 | all | medium |
| 5 | home-milestone | milestone | 마일스톤 | all | medium |

All default widgets omit projectId and limit. IDs are deterministic client-compatible instance strings, not generated business UUIDs. Repeated GETs return the same values with no mutable shared collection leakage. Deploy is only a valid layout type and does not imply connected/healthy operational services. Approval accepts this default proposal; later frontend integration must compare its existing reset draft explicitly.

### Widget validation and ordering

Every widget requires exactly the core fields `{id,type,title,scope,size}`, with only projectId and limit optionally permitted as below.

| type | allowed sizes | allowed scopes without projectId | projectId | explicit limit | omitted limit meaning for later data loading |
| --- | --- | --- | --- | --- | --- |
| overview | small, medium, wide | all, unity, server | Optional owned active/archived Project | Integer 1..20 | All matching Projects through paging; aggregate totals always separate |
| board | small, medium, wide | all, unity, server | Optional owned active/archived Project | Integer 1..20 | All matching Tasks through paging |
| journal | small, medium, wide | all, unity, server | Optional owned active/archived Project | Integer 1..20 | 3 |
| milestone | small, medium, wide | all, unity, server | Optional owned active/archived Project | Integer 1..20 | 2 |
| deploy | small, medium, wide | all, unity, server | Forbidden | Forbidden | No count setting |
| links | small, medium, wide | all, unity, server | Forbidden | Forbidden | Entire filtered Link collection |

- All listed size/type combinations are valid; the specification defines no per-type size restriction. Enums are case-sensitive. The same type may occur more than once with distinct IDs. Never deduplicate by type or reorder by type, ID, title or size.
- Widget id is a client-supplied string, nonblank after trim. Proposed clarification: apply common required-string trimming, preserve case and reject duplicate normalized IDs within the same dashboard. The same widget ID in a different user's dashboard is valid; no UUID requirement or server replacement. Title is trimmed and must contain 1..48 UTF-16 code units. Text is data, not executable HTML.
- For allowed types, projectId is an optional canonical UUID. If present, it must reference an owned Project; archived Projects are accepted. Validate supplied scope as an allowed enum first, then normalize scope to all when projectId exists and persist/return that normalized value. Validate every distinct reference in the save transaction; do not silently remove, substitute or reassign a bad reference.
- Proposed clarification: explicit null for projectId or limit is invalid; omission means absent, and removing a setting means omitting it in the next full replacement. For deploy/links, presence of either key is invalid even if null. Limit rejects 0, 21, negative, fractional, string and unsafe values.
- Preserve optional-field absence in stored configuration and GET/PUT responses. Do not materialize default numeric limits, especially not 20 for overview/board. Defaults above govern later business-data loading, not extra data fetched by Dashboard endpoints. Board limit applies across all columns combined, never per status; no limit affects Overview/Task statistics.
- Persist JSON array order as the authoritative layout order. No position field, separate widget table, per-widget revision or sorting endpoint. A save that only reorders/removes widgets advances the dashboard revision once and never edits/deletes associated business resources.
- On saved GET, validate referenced Projects in the same read snapshot, accepting archived Projects. Proposed clarification: an inaccessible/broken stored reference fails with 404 RESOURCE_NOT_FOUND; do not replace it with a different Project, return fabricated data, repair the stored layout or fall back to defaults. Owners can submit a corrected full layout with its previously known revision. Project physical deletion is currently absent; future deletion/import must review JSON reference integrity.
- No new widget-count or widget-ID length ceiling is silently inferred from the source contract. The development guide's proposed 100-widget/general-body limits remain deployment policy outside this Plan; do not claim an enforced payload ceiling. Validate complete arrays and batch reference checks where practical, rather than introduce an arbitrary limit during implementation.

## Proposed Database Schema

Add `V12__create_home_dashboards.sql`, subject to checking the next unused version before execution. Never edit/repair applied V1-V11. Use the existing Hibernate/PostgreSQL JSON support with typed widget values and explicit JSONB mapping; no new JSON library or separate persistence-model framework.

| dashboards column | Definition |
| --- | --- |
| workspace_id | UUID NOT NULL; FK workspaces(id), ON DELETE RESTRICT |
| dashboard_key | TEXT NOT NULL; CHECK dashboard_key='home' |
| schema_version | INTEGER NOT NULL; CHECK schema_version=1 |
| revision | BIGINT NOT NULL; CHECK 1..9007199254740991 |
| widgets | JSONB NOT NULL; CHECK jsonb_typeof(widgets)='array' |
| created_at | TIMESTAMP WITH TIME ZONE NOT NULL; immutable server UTC instant |
| updated_at | TIMESTAMP WITH TIME ZONE NOT NULL; server UTC save instant |

- Composite primary key `(workspace_id,dashboard_key)` provides the required per-workspace Home uniqueness. Workspace owner uniqueness makes this one Home per User as well. No duplicated user_id, client-selected owner, surrogate public dashboard UUID, cascade deletion, revision=0 row or default-row migration.
- JSONB preserves widget array order. Full nested type/field/ID uniqueness and Project ownership validation belongs to domain/application logic in the transaction; JSON references are not protected by a relational FK. DB PK/FK/check/NOT NULL guards complement that validation. No JSON index is justified for whole-layout lookup by the primary key.
- Revision and schema version are separate explicit fields; a future schema version needs a separately reviewed migration/compatibility contract. Reject corrupt/unsupported stored data through the common server error boundary rather than coercing it to defaults or rewriting it on GET. Do not expose raw JSON/SQL in errors.
- Initialize audit timestamps from one existing clock instant at existing microsecond precision. Save both layout state and workspace.dataRevision in the same transaction. Public responses omit internal timestamps and ownership.
- Verify fresh migration/JPA validate and populated V11-to-V12 upgrade, preserving every earlier table's representative rows, replay records and applied checksums. No backfill is needed: existing users receive virtual defaults until their first successful save.

## Revision, Authorization and First-save Concurrency

1. Strictly parse/validate request structure, schema, revision, widgets and enums. Resolve the authenticated internal User to its owned Workspace through the existing CurrentUser boundary, preserving disabled-account handling.
2. PUT acquires the existing Workspace write lock before any write, then validates/locks distinct referenced owned Projects in deterministic UUID order, including archived Projects, then reads/locks the Home row if present. Follow workspace -> Project -> Dashboard order; do not attempt to lock a nonexistent Dashboard as the first-save guard. Never use a process-local lock or blind upsert.
3. Validate referenced Project ownership before returning a business revision conflict. Under the Workspace lock the effective revision is 0 for absence, otherwise the persisted revision. Compare the supplied revision; mismatch yields 409 REVISION_CONFLICT with no state change.
4. No row + revision 0 creates revision 1. No row + positive revision conflicts without creating state. Existing row + revision 0 conflicts. Existing matching positive revision saves revision+1. Preflight the dashboard safe-integer ceiling and workspace counter overflow; return the established 409 conflict behavior and roll back, never wrap.
5. Persist the entire normalized layout, dashboard audit/revision and exactly one workspace.dataRevision increment, then flush/commit before HTTP success. Public Workspace metadata revision/audits and all Project/Task/Journal/Milestone/Link revisions and data remain unchanged. All failed saves roll back completely, including the first row and counter.

- Client revision comparison supplies optimistic stale-update semantics; database locking makes comparison and mutation atomic across processes. With two independent first PUTs at revision 0, the Workspace lock serializes absence checks: one returns 200 revision 1, the other sees the row and returns 409. The database PK independently prevents duplicate Homes. No successful retry/overwrite of the losing request.
- Two same-base existing-row PUTs likewise produce one success and one conflict. Different Workspaces can save independently. If unexpected uniqueness-conflict translation is needed, fully roll back the losing transaction before any fresh owned lookup, then report conflict; never query an aborted transaction or retry as a new update. Do not catch unrelated integrity errors as stale revisions.
- PUT has no create-replay store. A lost successful response followed by the same old revision returns 409; the client reads the current layout and compares its preserved draft. Never auto-advance the submitted revision or claim exactly-once response replay.
- GET Dashboard uses one read-only REPEATABLE_READ snapshot for ownership, layout and Project references, with no write locks or persistence creation. A concurrent first save may yield the complete virtual revision-0 layout or the complete persisted revision-1 layout depending on the snapshot, never a mixed layout or side effects.
- All three endpoints use authenticated session ownership, JSON UTF-8, Cache-Control: no-store and the common `{code,message,fieldErrors,requestId}` errors. Missing/foreign referenced Projects use identical 404. Anonymous application requests remain 401; disabled accounts and invalid Origin retain existing handling; PUT without valid CSRF is 403. Never accept client userId/workspaceId to select data.

## OpenAPI and Swagger Validation

- Document the three operations, session-cookie security, PUT's required X-CSRF-Token, exact response shapes and 200 on first save. Describe 400 validation/schema-version errors, 401/403, applicable 404 references, PUT 409 and common 500 responses. No Idempotency-Key or per-widget revision is required.
- Describe fixed schemaVersion=1, request/response revision range including virtual 0, persisted positive revisions, required core widget fields, optional non-null projectId/limit, all type/size/scope enums, limit 1..20, title length, normalized scope, order, duplicate-ID rejection and full replacement/empty-array semantics.
- Use conditional widget schema alternatives where supported to express settings forbidden for deploy/links; explicitly describe cross-item ID uniqueness, ownership and normalization, which ordinary field annotations cannot enforce. Generated-contract tests must verify the alternatives/constraints and runtime parity; a global uniqueItems flag is not a substitute for ID uniqueness.
- Overview docs must state active-only projects.total, archived count, fixed zero-filled byScope/status keys, intersection filtering, no query/paging, archived Project inclusion for Task counts and snapshot/asOf semantics. Include unsaved, saved-empty, project-specific widget and stale-first-save examples.
- Preserve anonymous docs only in dev/development/local/test, protected default/production, and production precedence over development profiles. Use a documentation-only real Chrome harness following PLAN-0007/0008: explicit HTTP readiness, visible HTTP method/path/operation labels and exact schema names, accessible controls; no Swagger internal CSS/JS state or security relaxation. MockMvc/PostgreSQL tests validate actual API/security behavior separately.

## Frontend/Backend Contract Differences for Later Integration

| Documented frontend behavior | Required later integration |
| --- | --- |
| Local widget array without schemaVersion | Wrap full ordered array in `{schemaVersion:1,revision,widgets}`; retain server revision including initial 0 |
| Synchronous boolean save / local persistence | Await Promise result, preserve draft on failure/conflict, reflect normalized response only after success; pending/error handling |
| Local reset/removal/cancel | Reset is an editable draft; only PUT persists it. Saved [] stays empty. Removing a widget/cancelling layout edits does not undo/delete business changes |
| Client widget IDs and repeated types | Preserve valid instance IDs and order; uniqueness is by normalized ID within a dashboard, not by type; business server-UUID rules do not replace widget IDs |
| Missing projectId/limit | Keep omission; journal default 3, milestone 2, overview/board all via pagination, links full collection |
| Project-selected widget | Send/retain owned Project ID, accept normalized scope=all and archived Projects; surface 404 instead of selecting a fallback |
| Loaded arrays used as totals | Read Overview/Task stats for totals independently of pages, widget limit, search and archive tab; active projects.total and archived are separate |
| Home board limit | Limit across all columns combined; Task page's per-column pagination is a different flow |
| Widget business-data loading | General overview uses active Project lists; selected Project detail permits archived. Journal includes archived by default; general milestone uses active Projects, selected milestone uses projectStatus=all |
| Home search/scope tabs | Select visible widgets without silently overriding each widget's configured business API filters |
| Default/demo fallbacks | Match the approved server default explicitly; deploy stays unconnected/demo until a separate integration exists. API failures must not become successful fixture results |
| Account switch and late responses | Scope cache/drafts by authenticated user/workspace, retain revision and discard old-session results; never save one user's draft as another |

These are later frontend tasks, not changes executed by this Plan. No frontend source was inspected and exact parity with its default layout is unverified.

## Clarifications and Unresolved Decisions

- Approval accepts the explicit six-widget default above, trimmed/case-sensitive widget ID uniqueness, omission-only optional settings, strict query parameters, saved broken-reference GET returning 404, unchanged-save revision increments and preservation of omitted defaults. These fill source gaps and are not new repository-wide guide rules.
- The source Overview example agrees with the normative text (total means active) and sum invariants. Overview scope/projectId intersection and stored-widget projectId scope normalization are intentionally different contracts.
- No unresolved architecture/dependency decision blocks this proposal. Exact frontend default parity and deployment-wide widget/body quotas remain later integration/deployment decisions; neither is silently represented as implemented. An alternative default can be accepted by revising this proposed Plan before execution.

## Execution Policy: Recover Within the Approved Contract

After explicit approval, change status to `active` and execute sessions in order. Complete and revalidate each session before advancing.

- Diagnose, correct and revalidate recoverable implementation, compilation/type inference, transaction, fixture/isolation/synchronization, OpenAPI and browser readiness/selector failures within the approved contract; then continue without requesting approval again.
- Preserve root-cause/failure evidence; check items only after success. Do not weaken assertions, skip required tests, use raw types/unchecked casts, relax security or change contract semantics to obtain a pass.
- Use controlled synchronization, independent transactions and final-state assertions for races. Repeat affected concurrency cases and include them in the full suite; a lucky rerun is not a fix. Unique-conflict recovery must fully roll back a losing transaction before looking up a winner in a separate transaction.
- Stop only for genuine decision-level blockers: contradictory approved requirements, required unapproved architecture/schema changes, security/data-integrity compromises, unavailable external prerequisites after reasonable authorized recovery, or a root cause that cannot be resolved reliably within the approved design.
- If blocked, keep `active`, leave incomplete validations unchecked and record reproduction, diagnosis, attempted fixes, affected checks and the decision/external prerequisite needed. Do not modify shared guides or completed Plans to adopt this policy.

## Execution Sessions

All five approved sessions executed in order. Checked items have passed implementation and validation.

### Session 1: Dashboard model, defaults and migration

Objective: Represent the complete ordered configuration and virtual/persisted distinction with PostgreSQL integrity.

Implementation:

- [x] Recheck source baseline and migration availability; implement the approved Dashboard/domain widget model, deterministic immutable defaults, repository boundary and V12 migration.
- [x] Map typed widgets to JSONB using the existing stack, protecting nested validation, optional-field absence, array order and audit/revision invariants.

Validation:

- [x] PostgreSQL Testcontainers verifies fresh/upgrade migrations, existing data/checksum preservation, JPA ddl-auto=validate, duplicate home/FK/schema/revision/JSON-array guards and flush/clear round trips.
- [x] Validate every type/size/scope combination, optional settings, UTF-16 title edges, normalized duplicate IDs, same-type distinct IDs, empty layout and preserved order/omission. No synthetic default row or seeded business data exists.

Session 1 evidence: DashboardPersistenceTests passed 4 tests, zero failures/errors/skips, including populated V11 upgrade and JSONB round trips.

### Session 2: Atomic Home reads, saves and concurrency

Objective: Safely save the first and subsequent complete layouts while keeping GET side-effect free.

Implementation:

- [x] Implement owned read and command services, reference validation/normalization, workspace-first locks, revision comparison and atomic dataRevision changes.
- [x] Implement rollback-safe errors, virtual default GET and saved-reference behavior; keep physical business resources and public Workspace metadata unchanged.

Validation:

- [x] Verify repeated unsaved GET, unsaved layout with existing business data, first PUT 0->1, saved-empty GET, replacements/reorder, same-value saves, absent-row positive revision, stale 0/positive revisions and overflow.
- [x] Use separate PostgreSQL connections/transactions and controlled synchronization for repeated concurrent first PUTs, same-base later PUTs, GET versus first save, different-owner saves and save versus Project archive. Assert one winner/one 409 where applicable and exact persisted widgets/revisions/counters after completion.
- [x] Prove two-user reference isolation, archived references, scope normalization and broken stored-reference GET behavior. Inject failure after real flush for first and subsequent saves; fresh transactions verify complete rollback, no partial row and unchanged counters/audits/business rows.

Session 2 evidence: DashboardCommandTests (11) and DashboardPersistenceTests (4) passed, zero failures/errors/skips; repeated first-save and stale-save races, flushed rollback, overflow and archived references verified.

### Session 3: Overview aggregation and snapshot consistency

Objective: Return all-row owned Project/Task totals with the existing Task statistics semantics in one snapshot.

Implementation:

- [x] Add Project grouped aggregation through an application-owned query boundary and reuse Task statistics aggregation under the Overview REPEATABLE_READ transaction.
- [x] Implement exact scope/projectId intersection, owned validation, zero-filled DTOs, count invariants and one asOf; inspect generated SQL/query plans.

Validation:

- [x] Test empty workspaces, both scopes, active/archived Projects, all Task statuses, deleted/restored Tasks, selected archived Projects, scope mismatch and foreign/missing Project parity using PostgreSQL.
- [x] Use more than 100 matching Projects/Tasks and multiple limited pages; totals must remain independent of items/page sizes and match equivalent Task stats and full filtered undeleted Task totals in a stable fixture.
- [x] Coordinate a commit between Project and Task aggregation queries (Project archive/scope change and Task mutation fixtures). Assert a coherent before snapshot within the response and the after state on a subsequent request. Include reference validation in the snapshot and prove no writes/counter changes.
- [x] Check bounded query count as row volume grows, real SQL grouping/ownership predicates and no N+1/full-entity counting. Run existing Task stats/query/API regressions after any shared extraction.

Session 3 evidence: 12 OverviewQueryTests/TaskApiTests/TaskCommandTests passed, zero failures/errors/skips. Both controlled concurrent snapshot schedules passed; 121-row totals and constant query count verified. PostgreSQL plans use existing owned-workspace indexes; no additional index needed (build/reports/dashboard-overview-api/query-plan.txt).

### Session 4: REST DTOs and authorization

Objective: Expose exactly the three approved operations with strict contracts and existing security.

Implementation:

- [x] Add controllers, purpose-specific DTOs/parsers and minimal common error mappings for schema version, revisions and references, retaining no-store and security boundaries.
- [x] Map full normalized responses without internal fields; document and implement precise omission/null handling and query allowlists.

Validation:

- [x] MockMvc plus PostgreSQL verifies exact methods/status/fields, all invalid JSON/query/schema/revision cases, nested unknown/duplicate fields, non-null optional-field rules, all widget combinations and indexed validation failures.
- [x] Verify anonymous 401, missing/invalid CSRF 403 for PUT, Origin rejection, disabled accounts, two-user layout/aggregation/reference isolation and unchanged production authentication. GETs cause no dashboard insertion or business mutation.
- [x] Run selected Overview/Dashboard and Project/Task/authentication regressions; verify first-save races through the HTTP boundary also produce 200/409 without leaked database errors.

Session 4 evidence: 47 selected Dashboard/Overview/Project/Task/security tests passed, zero failures/errors/skips. Strict REST matrices, ownership/security, normalized responses and three HTTP first-save races passed.

### Session 5: OpenAPI, real browser and final regression

Objective: Verify published contracts and finish only with successful required regression/build evidence.

Implementation:

- [x] Add focused OpenAPI constraints/examples and generated-schema assertions for the three operations, virtual revision, conditional widgets and aggregation semantics.
- [x] Add a documentation-only Chrome harness using readiness checks and rendered semantic content; retain artifacts and inspect the screenshot.

Validation:

- [x] OpenAPI tests verify exact request/response fields, required CSRF, version/revision bounds, optional settings, fixed count keys, error statuses and no false uniqueness/default/pagination claims. Default/production Swagger protection regressions pass.
- [x] Real Chrome renders all three operations and relevant DTO schemas anonymously only in the permitted test/development profile; capture result JSON, HTML and screenshot without testing authenticated CRUD via Swagger internals.
- [x] Java 21 `./gradlew.bat test --no-daemon --rerun-tasks` and `./gradlew.bat clean build --no-daemon` pass with actual PostgreSQL Testcontainers and repeated concurrency tests, no required skips. Preserve full-suite/clean-build XML counts, generated OpenAPI, query plans and browser evidence outside cleanable build output.

## Final Validation

- [x] Overview ownership, active/archived totals, fixed zero keys, all-row Task stats equivalence and one read snapshot are verified.
- [x] Unsaved GET has no persistence side effects; saved [] remains empty; only successful first PUT creates one positive-revision Home row.
- [x] Stale/first-save races, overflow and rollback preserve exact dashboard/workspace counters and business data.
- [x] Widget ordering, normalized ID uniqueness, all conditional fields and active/archived references match the approved contract.
- [x] Fresh/upgrade Flyway and JPA validation, required security regressions, full tests and clean build succeed without editing applied migrations.
- [x] Final diff contains only approved backend/test/documentation work; no frontend, dependency, shared-guide or unrelated changes introduced.
- [x] No failures caused by this Plan remain.
- [x] All Execution Sessions are complete.

### API Validation

- [x] All three implemented endpoints are present in `/v3/api-docs`.
- [x] Request/response schemas, conditional widget constraints and examples match the approved contract.
- [x] Bean Validation constraints are reflected where applicable; cross-field/ownership/revision rules are explicit.
- [x] HTTP methods, response/error statuses and required security headers match runtime behavior.
- [x] Swagger UI renders the implemented endpoints and schemas correctly in real Chrome with existing production protection intact.

## Completion

Completed on 2026-09-13 after all five sessions and Final/API Validation passed. No unresolved implementation, validation or decision-level blocker remains. The completed-plan index includes this Plan.

### Final validation evidence

- Java 21 `./gradlew.bat test --no-daemon --rerun-tasks`: BUILD SUCCESSFUL in 2m 33s, exit 0, all 5 tasks executed; 34 suites / 177 tests, zero failures/errors/skips. Includes 27 Dashboard/Overview tests and all prior User/AuthIdentity/Workspace creation, resource, security and production documentation regressions.
- Java 21 `./gradlew.bat clean build --no-daemon`: BUILD SUCCESSFUL in 2m 33s, exit 0, all 9 tasks executed; 34 suites / 177 tests, zero failures/errors/skips. Fresh executable and plain JARs produced in `build/libs/`.
- Full-suite XML reports, counts, OpenAPI JSON and SQL plans are preserved in `.gradle/dashboard-overview-api-validation/full-suite/`; corresponding clean-build evidence is in `.gradle/dashboard-overview-api-validation/clean-build/`, each with `summary.json`. Standard browsable report: `build/reports/tests/test/index.html`.
- Targeted DashboardOverviewOpenApiTests and ProductionSwaggerSecurityTests passed and passed again in both full runs. The generated contract verifies all three operations, exact fields, required CSRF, version/revision ranges, conditional widget alternatives, zero-filled count DTOs, null projectId and valid default/empty/Project-widget/stale-save examples. Existing authentication and documentation protection were unchanged.
- Real Chrome: `node src/test/browser/dashboard-overview-api-validation.mjs .gradle/dashboard-overview-api-validation/browser`, exit 0, `DASHBOARD_OVERVIEW_SWAGGER_DOCUMENTATION_PASS`. All three method/path/operation labels and 11 relevant schema names rendered; anonymous test-profile documentation, required CSRF and virtual revision/type alternatives verified. Evidence: `result.json`, `swagger.html`, `swagger.png`; screenshot visually inspected. Validation-only server and separate-profile Chrome processes stopped afterward.
- PostgreSQL tests verified fresh V12 and populated V11 upgrade/checksum preservation, typed JSONB ordering/omission, dashboard uniqueness and DB guards, unchanged GET state, saved-empty behavior, owned/archived/broken references, revision/counter overflow, flushed first/subsequent-save rollback and exact counters. Five repeated service first-save/stale-save races and three HTTP first-save races each verified one winner and one conflict; independent Workspaces, both archive/save orderings and GET versus first-save snapshots also passed.
- Overview tests verified active/archived counts, both scopes, ownership-before-filtering, archived-Project Tasks, trash/restore, equivalent Task statistics, 121-row totals independent of pages, constant query count and two controlled concurrent Project/Task mutation schedules with coherent before/after snapshots. SQL plans use existing workspace indexes; no new index or duplicated Task aggregation was introduced.
- Final scope review: changes comprise Dashboard and Overview feature code, V12, minimal shared error mappings, five test classes, one documentation browser harness, this Plan and its index entry. Existing V1-V11 migrations, frontend, auth/security configuration, application environment configuration, dependencies, shared guides and completed historical Plans remain unchanged. No new environment variable or dependency was added. Diff/whitespace checks passed.

### Execution recovery notes

- Session 1: corrected JUnit overload/type inference by asserting inside the transaction callback. Gradle's sandbox network denial was resolved with approved elevated execution. Docker Desktop was stopped; started the existing engine and reran actual PostgreSQL tests (initial evidence: `.gradle/dashboard-overview-api-validation/recovery/docker-unavailable.xml`).
- Session 1: PostgreSQL JSONB existence operator `?` in a JDBC fixture was interpreted as a parameter marker. Use equivalent `jsonb_exists` in the assertion query; production JSONB mapping and schema remained unchanged.
- Session 4: corrected a test fixture's compound `var` declaration to its concrete Owner type, then reran the complete selected REST/security regression set.

All required work and validation are complete; this Plan is recorded in [README.md](README.md).
