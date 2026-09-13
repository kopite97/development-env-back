# PLAN-0005: Task API

Status: `completed`

## Goal

Implement the workspace-owned Task resource according to [backend-implementation-spec.md](../../backend-implementation-spec.md), sections 1, 2, 4, 8 and 9, and [backend-development-guide.md](../../backend-development-guide.md). Deliver persistence, Project ownership enforcement, authenticated REST operations, revision and create-retry protection, PostgreSQL integration tests and verified OpenAPI/Swagger behavior.

Approved by the user with the proposed contract and clarifications accepted. Execution proceeds in session order; incomplete validation remains unchecked.

## Scope

- Include Task creation, detail, filtered cursor listing, partial update, completion/reopening through status, soft deletion, restore and `/tasks/stats`.
- Use `com.kopite.devspace.task` with presentation/application/domain/infrastructure layers, domain/JPA entities, application-owned transactions and inward dependencies. Follow the relevant architecture, Spring Boot, API, database, Flyway, security and implementation guides under `docs/agents/`.
- Reuse the existing Java 21, Spring Boot, PostgreSQL, Flyway, Bean Validation, Spring Security, springdoc and Testcontainers stack. No new dependencies, infrastructure services or architectural framework are proposed.
- Exclude frontend modifications, data import, physical deletion, arbitrary card reordering, multiple tags, extra Task fields, overview endpoint implementation and other resource APIs. Task status changes do not update Project manual progress.
- Preserve the existing authentication/session baseline and shared CSRF/Origin boundary. Do not expand this work into session infrastructure or unrelated refactoring.

## Repository and Document Review

- Reviewed the root instructions, plan guide/runtime template, relevant backend guides, both current backend documents, completed PLAN-0004 and existing Project services, repository interfaces and migrations.
- Current migrations are V1 (User/Workspace), V2 (Projects) and V3 (Project create idempotency). Projects already have `UNIQUE(workspace_id,id)`, ownership-scoped repository methods and row locking. Workspace provides `lockByOwnerId` and `recordBusinessMutation`.
- Project implementation supplies useful precedents for strict DTO parsing, explicit revision comparison, signed cursors, repeatable-read pagination, durable replay and central API errors. Its replay table explicitly restricts the path to `/api/v1/projects`; Task must not write into it unchanged.
- Existing PostgreSQL Testcontainers, MockMvc, OpenAPI tests and test-only browser login/validation support can be extended. Production authentication must not acquire a test bypass.
- The working tree already contains PLAN-0004 implementation changes. Preserve them. This planning task adds only this document and does not rerun or claim runtime validation.
- No frontend source, root `README.md` or `frontend-contract-gap-analysis.md` is present in the current repository, although the specification links the latter documents. Frontend observations below are limited to the available specification and development guide. The guide's introductory statement that no backend exists is historical; existing source and completed plans establish the implementation baseline.

## Proposed Task API Contract

All paths below have prefix `/api/v1`. Use UTF-8 JSON, authenticated session ownership, the common error DTO `{code,message,fieldErrors,requestId}` and `Cache-Control: no-store`. Mutations retain `X-CSRF-Token` and Origin validation.

| Method and path | Input | Success |
| --- | --- | --- |
| POST `/tasks` | `CreateTaskRequest`, required `Idempotency-Key` | 201, full `TaskResponse` |
| GET `/tasks/{id}` | Task UUID | 200, full Task, including deleted Tasks |
| GET `/tasks` | `TaskListRequest` filters below | 200, `{items,total,nextCursor}` |
| PATCH `/tasks/{id}` | `UpdateTaskRequest`, required body revision | 200, full updated Task |
| DELETE `/tasks/{id}` | Required query revision | 200, full soft-deleted Task |
| POST `/tasks/{id}/restore` | `RestoreTaskRequest` containing only revision | 200, full restored Task; no create idempotency key required |
| GET `/tasks/stats` | `TaskStatsRequest` filters below | 200, `{counts:{todo,doing,done},total,asOf}` |

Completion is PATCH `{status:"done",revision:...}`; reopening uses `todo` or `doing`. There is no PUT, separate completion endpoint, hard delete or position/reorder API.

### DTOs and validation

| Field | Create | PATCH when present | Response |
| --- | --- | --- | --- |
| `title` | Required; trim, nonblank, max 160 UTF-16 units | Same constraint | String |
| `projectId` | Required UUID; owned active Project | Owned active target when changing relation | UUID |
| `description` | Optional, default `""`; preserve text, max 10000 | Same limit; `""` clears | String |
| `status` | Optional, default `todo`; `todo/doing/done` | Same enum | Enum string |
| `priority` | Optional, default `normal`; `normal/high` | Same enum | Enum string |
| `tag` | Optional, default `""`; trim, max 40 | Same constraint; `""` clears | Single string |
| `revision` | Server initializes 1; client field rejected | Required integer, 1..9007199254740991 | Safe positive integer |
| `id`, `createdAt`, `updatedAt` | Server-generated; client fields rejected | Rejected | UUID and UTC ISO timestamps |
| `projectName`, `scope` | Derived from current Project; client fields rejected | Rejected | Current Project name and `unity/server` |
| `deletedAt` | Server initializes null; client field rejected | Rejected | Null or UTC ISO timestamp |

Use purpose-specific request/response DTOs and application snapshots, never entity serialization. List items use the full Task DTO, including description and revision. Internal ownership fields are absent from responses.

PATCH preserves omitted fields. Proposed clarification consistent with Project: explicit null for any writable Task field is invalid; optional strings clear with `""`. Restore accepts only revision. A revision-only or same-value PATCH succeeds and advances revision. Reject unknown/duplicate write fields, wrong JSON types, fractional/string revisions, malformed UUIDs, enum casing variants, `tags`, localized priority strings, derived fields and client ownership fields with 400 `VALIDATION_ERROR`. Validate UTF-16 lengths at DTO and domain boundaries; preserve ordinary text without executing HTML.

### List and statistics

- List filters: `scope=all|unity|server` (default all), optional `projectId`, `projectStatus=all|active|archived` (default all), `query` (default `""`), optional `status=todo|doing|done` (omission means every status), `deleted` boolean (default false), optional cursor and limit (default 20, range 1..100).
- Proposed clarification: `deleted=true` selects only trash, false selects only undeleted Tasks; there is no deleted=all value. No priority/tag filter or client-selected sort is defined by the specification.
- Validate supplied Project ownership before applying intersecting filters. An owned Project with a different scope/status yields an empty result; a foreign or missing Project yields the same 404, even if filters would produce no rows.
- Search is a case-insensitive literal substring of Task title OR current Project name. Escape SQL LIKE `%`, `_` and the escape character. Do not search description/tag or silently substitute full-text search.
- Stable order is `createdAt DESC,id DESC`, including within board columns. Status movement changes status, not creation time or an invented ordering field.
- Use database filtering/counting and keyset pagination, fetching limit+1. `total` counts all matching rows, not the page. Bind signed opaque cursors to the Task resource, workspace, all effective filters, limit, fixed sort and final timestamp/UUID; invalid, tampered, cross-resource or changed-filter cursors return 400 `INVALID_CURSOR`.
- Use the existing signed-cursor approach with Task-specific payload/context and externally configured signing material. Do not expose workspace identity or accept unsigned cursors. No cross-page snapshot guarantee; count and items within one response use a consistent read snapshot.
- Stats accept only scope, projectId, projectStatus and query; reject status/deleted filters. Always return all three count keys (including zero), total and UTC `asOf`. Aggregate undeleted Tasks of all statuses; archived Projects are included by default. Counts sum to total and match the equivalent undeleted list's total in the same database state.
- Share Task filter/aggregation semantics within the feature so future overview aggregation can reuse them; do not implement overview now. Use one authorized read snapshot for stats and resolve `/tasks/stats` as a static route, not a Task UUID.

## Proposed Database Schema

Add new migrations; do not change V1–V3. Proposed next versions are V4 and V5, subject to checking for newly allocated versions when execution starts.

### V4: tasks

| Column | PostgreSQL definition / rule |
| --- | --- |
| `id` | UUID primary key, server-generated |
| `workspace_id` | UUID NOT NULL, immutable, FK to workspaces with DELETE RESTRICT |
| `project_id` | UUID NOT NULL |
| `title` | TEXT NOT NULL, nonblank trimmed content, length guard 160 |
| `description` | TEXT NOT NULL DEFAULT '', length guard 10000 |
| `status` | TEXT NOT NULL DEFAULT 'todo', CHECK todo/doing/done |
| `priority` | TEXT NOT NULL DEFAULT 'normal', CHECK normal/high |
| `tag` | TEXT NOT NULL DEFAULT '', length guard 40 |
| `revision` | BIGINT NOT NULL DEFAULT 1, CHECK 1..9007199254740991 |
| `created_at`, `updated_at` | TIMESTAMP WITH TIME ZONE NOT NULL |
| `deleted_at` | TIMESTAMP WITH TIME ZONE NULL |

Enforce `(workspace_id,project_id) REFERENCES projects(workspace_id,id) ON DELETE RESTRICT`. A valid Project UUID from another workspace must fail even through direct SQL. Do not store copied `project_name` or `scope`, tag arrays, completion timestamps or card positions. PostgreSQL character-length checks complement, but do not replace, Java's stricter UTF-16 validation for supplementary characters.

Keep Task an independent aggregate with workspace/project identifiers, following Project's identifier-based ownership model; resolve current Project data through owned queries/projections. No bidirectional Project.tasks collection or deletion cascade is needed.

Propose indexes `(workspace_id,created_at DESC,id DESC)` and `(workspace_id,project_id,created_at DESC,id DESC)` for general/trash and Project-scoped access, plus a partial `(workspace_id,status,created_at DESC,id DESC) WHERE deleted_at IS NULL` for board/status listing. Review generated SQL/query plans against representative test data before adding further indexes; do not claim B-tree indexes accelerate arbitrary substring search.

### V5: task_create_idempotency

Follow the existing feature-local replay table: workspace FK, method restricted to POST, canonical path restricted to `/api/v1/tasks`, key, request hash, successful response status (201), serialized original full response, created_at and expires_at. Use primary key `(workspace_id,method,path,key)` and expiry index. Workspace's unique owner represents user scoping; no client-selected ownership is accepted.

Retain successful outcomes for 24 hours. Validate keys as 1..128 visible ASCII characters, matching Project. Hash a deterministic representation of submitted fields preserving omission and raw values while ignoring JSON property order/formatting, matching Project's retry semantics. Store Task creation, workspace counter and replay outcome in one transaction. Expired entries can be removed within the owned create flow; no scheduler or generic idempotency framework is proposed.

## Ownership, Project Relation and Revision Strategy

- Resolve workspace from the authenticated internal User through the existing current-user boundary. Repository queries always require workspace scope; body/query/header ownership selectors never select another workspace.
- Every Task operation validates its Project relation: detail and writes resolve both Task and current Project within the workspace; list/search/stats join Project on workspace AND id; create/reassignment validate the target with an owned Project lookup. Replay also revalidates authenticated ownership of its referenced Project/Task before returning saved data.
- Missing and foreign Task/Project IDs both return 404 `RESOURCE_NOT_FOUND` with no foreign revision/data. Establish ownership before revision/state checks or replay output. On reassignment establish target ownership before reporting conflicts involving that target.
- New Tasks and changes to a different Project require an active target, otherwise 409 `PROJECT_ARCHIVED`. Retaining the same Project, even explicitly resubmitting its ID, permits edit/status/delete/restore when it is archived. Project rename/scope changes immediately affect Task responses, filtering and stats through live joins; they do not individually increment Task revisions.
- Commands lock workspace first, then related Projects, then Task. For updates, discover the current Project through an owned lookup after acquiring workspace lock; lock current/target Projects in deterministic UUID order before locking the Task. Existing Project archive commands use the same workspace/Project locks, serializing archive against Task creation/reassignment.
- Compare required expected revision and mutate in the same transaction with explicit conflict handling, following Project rather than relying on an incidental JPA flush version. Increment Task revision and updatedAt on every successful PATCH/delete/restore; keep createdAt fixed. Revision overflow is a conflict and cannot wrap.
- Check revision before deleted-state conflicts: stale revision yields 409 `REVISION_CONFLICT`; current-revision PATCH of trash yields 409 `RESOURCE_DELETED`; repeat deletion or restoration of an undeleted Task yields 409 `INVALID_RESOURCE_STATE`.
- DELETE sets deletedAt to server UTC time, restore sets it to null. Preserve Task fields/status and Project relation. Each successful new creation or mutation increments workspace.dataRevision exactly once; failures, reads and idempotent replay do not. Do not increment public workspace metadata revision or Project revision for Task changes.
- Same-key concurrent creates produce one Task and one counter increment. Matching replay returns the original 201 snapshot, even after later edit/deletion, without restoring or recreating data. Proposed clarification: after ownership validation, replay precedes active-Project state checks; an original successful creation can be replayed after its Project is archived. New creation with an expired/new key still requires an active Project. Different body with an unexpired key returns 409 `IDEMPOTENCY_KEY_REUSED`.
- Preserve shared security outcomes: anonymous protected requests 401; authenticated missing/invalid CSRF 403; authenticated invalid Origin 403; valid authenticated requests reach normal endpoint handling. Disabled-account handling remains shared. Do not add Task-specific security bypasses.

## Verified Frontend/Backend Contract Differences

These are document findings, not claims that frontend source was inspected or changed.

| Topic | Evidence in current documents | Later frontend integration work |
| --- | --- | --- |
| Single tag versus tags[] | Specification §4 explicitly defines single `tag`; §9 maps local `tag` to single `tag`. No available document establishes a current `tags[]` frontend field. | Keep a string; reject `tags` in backend DTOs. Verify actual frontend types before any conversion; do not invent multi-tag support or a lossy array-to-string rule. |
| Priority labels | Specification §9 records local `보통/높음`; backend §4 uses `normal/high`. | Map labels at the feature boundary; send enum values, retain localized display. |
| Project name/reference | Specification §9 maps local `Task.project` to `projectName`; §4 requires projectId. Development guide §5 describes ambiguous legacy Task name references requiring explicit Project mapping. | Require a real owned Project UUID for writes; use server-derived name/scope for display. Do not resolve names or create fallback Projects in Task API. Exact current UI fallback algorithm is unverified without frontend source. |
| Completion and ordering | Specification §4 defines status PATCH and createdAt/id ordering, with no position field. | Drag between statuses with revision; do not assume local array order or in-column reordering is persisted. |
| Trash and derived fields | Specification §4 defines deletedAt, full trash detail and restore; Project name/scope propagate. Guide §4 requires related-cache invalidation. | Retain delete/restore response and revision; refresh lists, trash, detail and stats after Task/Project changes. |
| Mutations, IDs and conflicts | Specification §9 and guide §4 record boolean-to-Promise mutations, server IDs and preserving drafts on 409. | Add pending/error handling, stable create retry keys, revision-aware writes and account-scoped caches; no silent stale overwrite. |
| Pagination/statistics | Specification §2/§8 and guide §4 distinguish pages, total and aggregates. | Use nextCursor/total and Task stats; do not derive whole-board counts from the loaded page. |

## Clarifications and Unresolved Decisions

- Approval of this proposal includes the explicitly identified clarifications: null rejection/empty-string clearing, trash-only deleted=true, revision-only PATCH, unchanged archived Project ID acceptance and ownership-checked replay before active-state checks. These fill implementation details without adding resource fields or endpoints.
- The development guide's general duplicate-DELETE guidance is less specific than the Task contract. Apply specification §4's explicit revision-first and state-conflict behavior, not idempotent repeated soft-delete success.
- No unresolved architectural decision is required for the proposed backend work. Frontend source availability, actual fallback behavior and any historical tags[] contract remain verification items for later integration, not reasons to change this backend contract.
- Recheck migration availability and existing changes when execution starts. Record and stop on a genuine contract contradiction or implementation/validation blocker; do not silently alter the approved contract.

## Execution Sessions

Execute sessions in order only after approval and transition to `active`. Do not start the next session until the current session's implementation and validation are complete. Check items only after actual completion and record validation evidence in this Plan.

### Session 1: Persistence and domain rules

Objective: Establish Task integrity and state transitions on real PostgreSQL.

Implementation:

- [x] Add V4 tasks migration, domain/JPA model, value validation and workspace-scoped repository/adapter operations.
- [x] Implement revision, update, soft-delete and restore domain behavior without exposing entity setters or ownership mutation.

Validation:

- [x] PostgreSQL Testcontainers: migrate a fresh database and upgrade a populated V1–V3 database; preserve existing data; Flyway checksum and Hibernate schema validation pass.
- [x] Persistence tests cover round trips, defaults, timestamps, enum/length/revision constraints and foreign/missing/cross-workspace Project FK rejection.
- [x] Domain tests cover UTF-16 boundaries, trim/text preservation, same-value updates, immutable ownership/createdAt, stale-before-state conflicts and revision overflow.
- [x] Compile production/test sources and run Session 1 tests successfully.

Evidence (2026-09-12): Java 21 `./gradlew.bat test --tests com.kopite.devspace.TaskPersistenceTests --no-daemon` passed, 4 tests with no failures/errors/skips. Fresh PostgreSQL Testcontainers startup validates Hibernate mapping; the upgrade test preserves populated V3 Project/Workspace data and V1–V3 checksums. Initial sandbox network/Docker access errors were resolved by the approved elevated execution.

### Session 2: Commands, ownership and durable retries

Objective: Make Task writes atomic, owner-scoped and safe against Project archive and stale clients.

Implementation:

- [x] Add V5 Task replay storage and create/update/delete/restore application services using the approved lock order and current-user resolution.
- [x] Enforce current/target Project ownership, active-target rules, dataRevision accounting and central business-error mapping.

Validation:

- [x] Testcontainers command tests cover active/archived targets, same-ID retention, reassignment, two-user isolation for every mutation, and rollback without counter/revision changes.
- [x] Concurrent separate-transaction tests prove stale competing writes have one winner, and Project archive versus create/reassign has only valid serialized outcomes with no deadlocks.
- [x] Test matching/different-body and concurrent replay, omitted versus explicit values, JSON field-order independence, 24-hour expiry, replay after Task delete/Project archive, key scope isolation and atomic rollback of replay storage failures.
- [x] Verify every successful mutation advances only Task revision and internal workspace.dataRevision as specified; replay advances neither. Run all Session 2 tests and prior Task persistence tests.

Evidence (2026-09-12): TaskCommandTests (5) and TaskPersistenceTests (4) passed with no failures/errors/skips using Java 21 and PostgreSQL Testcontainers. Separate transactions exercised create/write/archive races; replay storage failure rolled back all writes.

### Session 3: REST, queries, statistics and security

Objective: Expose the complete Task contract with consistent current Project data and shared security handling.

Implementation:

- [x] Add strict create/update/restore/list/stats DTOs, full responses, controller endpoints and Bean Validation/common errors.
- [x] Implement workspace-and-Project-scoped detail, database-filtered list/count, signed keyset cursors and aggregate statistics with consistent read snapshots.

Validation:

- [x] MockMvc plus PostgreSQL tests verify all methods/status codes, full response shapes, defaults, clearing/omission, invalid/unknown/null/duplicate fields, UUIDs, enums, lengths and revision inputs.
- [x] Verify anonymous 401 and authenticated CSRF/Origin 403 behavior across Task mutations, valid-session endpoint handling, disabled accounts and no-store responses without weakening the shared security boundary.
- [x] Two-user tests cover detail including trash, list/search/stats, filter Project IDs, writes and replay; foreign/missing IDs have indistinguishable 404 responses without data leakage.
- [x] Verify every filter/intersection, deleted selection, literal search escaping, stable equal-timestamp pagination, total/nextCursor, cursor tamper/cross-workspace/cross-resource/filter rejection and final-page behavior.
- [x] Verify Project rename/scope/archive propagation, empty results, all zero count keys, stats/list agreement, rejected stats status/deleted filters, deletion/restoration counts and no N+1 Project lookups.
- [x] Run all Task tests and existing Project/security regression tests successfully.

Evidence (2026-09-12): Task, Project and SecurityIntegrationTests passed (45 tests, no failures/errors/skips). TaskApiTests cover lifecycle, strict DTO/query inputs, two-user and shared security behavior, derived Project changes, trash/stats and tied cursor pagination. Query review confirms each page selects Task and Project together through the composite ownership join; no per-item Project lookup occurs.

### Session 4: OpenAPI, browser workflow and final regression

Objective: Validate public development/test documentation and the authenticated API through separate browser/documentation and integration-test checks.

Implementation:

- [x] Document all seven endpoints, defaults, enums, read-only/nullable response fields, revision requirements, idempotency header and common errors using existing springdoc conventions.
- [x] Add generated OpenAPI assertions and extend test-only browser validation fixtures where needed; keep test authentication out of production and preserve frontend files.

Validation:

- [x] `/v3/api-docs` tests verify all methods, 201/200 responses, error responses, schema constraints, single tag, required projectId, enum values, filters, DELETE query revision and restore body revision.
- [x] In a real browser without authentication, confirm development/test Swagger UI renders all Task endpoints and schemas and can load /v3/api-docs. Record documentation-only browser evidence.
- [x] MockMvc + PostgreSQL Testcontainers validate anonymous 401, missing/invalid CSRF 403, invalid Origin 403, valid mutations, ownership, lifecycle/stats, revisions, idempotency and concurrency. Verify production Swagger remains protected.
- [x] Run the full test suite and clean build with Java 21 and available Docker/PostgreSQL Testcontainers; require actual execution, not skipped container tests or cached-only results.

## Final Validation

### Previous Session 4 blocker (2026-09-12; parameter issue resolved)

- Java 21 `./gradlew.bat test --tests com.kopite.devspace.TaskOpenApiTests --no-daemon` passed (1 test, no failures/errors/skips). Sessions 1–3 remain validated as recorded above.
- Started `bootTestRun` with test profile, PostgreSQL Testcontainers, port 18080 and matching APP_ORIGIN. Fresh V1–V5 migration and application startup succeeded. A separate headless Chrome profile authenticated through the existing test-only login and rendered all seven Task operations in Swagger UI.
- `node src/test/browser/task-api-validation.mjs` failed before its first HTTP mutation: Swagger's `system.fn.execute` reported `Required parameter X-CSRF-Token is not provided`. The harness adds the token in requestInterceptor, but Swagger validates required operation parameters before invoking that interceptor. This is a browser validation harness blocker; no endpoint failure is established by this result.
- Resume by supplying the CSRF header in Swagger execute parameters for authenticated mutations. To test missing CSRF, satisfy the request builder then remove the header in the interceptor so the request actually reaches the security boundary. Do not weaken the documented security requirement or server CSRF handling.
- Per the user's stop-on-blocker instruction, no fix was applied after this failure. Browser workflow, full suite, clean build and Final Validation remain unchecked; Session 4 is incomplete and status remains active. The completed-plan index is not updated for PLAN-0005.

### Previous Session 4 blocker (2026-09-13; superseded by approved policy)

- Updated only the browser harness: `X-CSRF-Token` is supplied through Swagger execute parameters before required-parameter validation. The deliberate missing-CSRF case removes the built header in requestInterceptor. OpenAPI still requires the header, and production CSRF/security handling is unchanged.
- Started the test-profile server with fresh PostgreSQL Testcontainers and V1–V5 migrations. Real headless Chrome authenticated, rendered Swagger operations, and successfully created the prerequisite Project through Swagger with HTTP 201. The previous required-parameter blocker is resolved.
- The next call, the deliberate missing-CSRF Task POST, received HTTP 403. Swagger's HTTP client rejected its promise with `Error: response status is 403` before the harness could inspect the response. The stack identifies line 30 of the evaluated browser function, the missing-CSRF call. The planned `CSRF_INVALID` assertion was not reached, so full missing-CSRF validation remains unchecked.
- Resume by handling Swagger's rejected HTTP response in the browser harness so expected 403 and 409 responses can be asserted, while preserving failures for unexpected responses/network errors. No production API or security change is required.
- Per the stop-on-another-blocker instruction, no additional fix was applied. The remaining Task browser workflow, full suite, clean build and Final Validation were not executed in this resumed run. Status remains active and PLAN-0005 has not been added to the completed-plan index.

- [x] All Execution Sessions and required validation items are complete with recorded evidence.
- [x] Full test suite (`./gradlew.bat test --no-daemon --rerun-tasks`) and clean build (`./gradlew.bat clean build --no-daemon`) pass; inspect reports for failures, errors and skipped required tests.
- [x] Flyway fresh/upgrade validation, persistence, ownership, concurrency, durable retries, pagination, stats and existing Project/authentication regressions pass.
- [x] Review final diff for contract fidelity, no frontend changes, no applied-migration edits, no new dependencies or unapproved architecture and no production test-authentication bypass.
- [x] No implementation or validation blocker remains; record test and browser evidence without credentials/session tokens.

### Approved validation policy (2026-09-13)

- Swagger UI and GET /v3/api-docs (including configuration/assets) are anonymous only under dev/development/local/test profiles; prod/production overrides these profiles. Default and production environments retain authenticated documentation access.
- Application API session authentication, CSRF, Origin and ownership remain unchanged. Required X-CSRF-Token documentation remains intact.
- The browser harness validates rendering and visible Task operations/schemas only, without login or business/security requests. MockMvc + PostgreSQL Testcontainers own all runtime/security/concurrency validation.
- Earlier browser CRUD/expected-error harness blockers are superseded by this user-approved policy; they are retained above as historical evidence, not outstanding completion requirements.

### API Validation

- [x] All implemented Task endpoints are present in `/v3/api-docs`.
- [x] Request/response schemas and examples match the approved backend contract.
- [x] Bean Validation constraints, required fields, defaults, enums and nullability are reflected where applicable.
- [x] HTTP methods, parameters and success/error status codes match the contract.
- [x] Swagger UI renders Task endpoints and schemas without authentication in development/test; actual CRUD/security behavior passes integration tests.

### Previous clean-build blocker and validation evidence (2026-09-13)

- Development/test anonymous Swagger GET access is implemented; prod/production profiles override development/test, and the default remains protected. Actual application API authentication, CSRF, Origin and ownership configuration is unchanged.
- Real Chrome browser documentation validation passed with TASK_API_SWAGGER_DOCUMENTATION_PASS. All seven Task operations and six Task schemas were visible without login; required mutation CSRF documentation was verified. Evidence survives clean under `.gradle/task-api-validation/browser/` (`result.json`, `swagger.html`, `swagger.png`). An initial browser attempt hit a transient navigation execution-context error; rerunning after server startup passed without code changes.
- Java 21 `./gradlew.bat test --no-daemon --rerun-tasks` passed: 62 tests across 13 suites, zero failures/errors/skips. Includes anonymous Task/docs access checks, required OpenAPI headers, Task runtime/security/persistence/concurrency tests, and production Swagger protection with both test and prod active.
- The subsequent required `./gradlew.bat clean build --no-daemon` failed: 62 tests, 1 failure, no errors/skips. Fresh compilation and assembly succeeded. `UserWorkspacePersistenceRulesTests.concurrentCreationAttemptsConvergeOnOnePersistedAggregate` failed through `only` at line 310 (caller line 203): expected a set size of 1, observed 2. Do not treat the prior passing run as a substitute for this failed clean build.
- Failure evidence: `build/test-results/test/TEST-com.kopite.devspace.UserWorkspacePersistenceRulesTests.xml` and `build/reports/tests/test/index.html`. The cause of the different concurrent IDs has not been established; no unrelated persistence fix or retry was performed after this blocker.
- Per user instruction, stop here with status active. Browser/OpenAPI and completed runtime checks are checked; the clean-build requirement and overall Final Validation remain incomplete. PLAN-0005 is not added to the completed-plan index. Next work is to investigate this concurrency failure, then rerun the required suite/clean build before completing the Plan.

### Concurrency root cause and fix (2026-09-13)

- Reproduced deterministically before changing production code: pause a losing request immediately after its identity lookup returns empty; let another request create and commit the same external identity; resume the losing request. The regression test failed with different returned User/Workspace IDs, a reassigned identity user_id, and two persisted Users/Workspaces. Pre-fix evidence is preserved in `.gradle/task-api-validation/concurrency/before-fix.xml`.
- Root cause is a real persistence race: AuthIdentity has a preassigned composite ID, so Spring Data save selected EntityManager.merge. If the winner commits between initial lookup and merge's lookup, merge updates the winner's identity to the candidate User rather than raising an INSERT unique conflict. This bypassed conflict recovery and left the first aggregate orphaned. The original start latch made this timing nondeterministic; unique per-test identity/name data and isolated deterministic reproduction rule out shared Spring context or test-data collisions as the cause.
- AuthIdentityRepositoryAdapter now explicitly persists and flushes a new identity. Duplicate identities must raise a unique violation instead of updating an existing owner. No schema or API contract change is needed.
- Creation and recovery lookup now each use REQUIRES_NEW on the existing separate transactional bean. The service catches only after creation has exited and rolled back, then reads the winner in a new transaction. This also avoids joining an ambient caller transaction that could otherwise be rollback-only or retain a stale snapshot.
- The deterministic regression repeats 10 times, alternating standalone requests and an ambient caller transaction. It asserts rollback completion before recovery, different PostgreSQL txid_current values for creation/recovery, an unaffected/resumed caller transaction, exactly one recovery, identical returned IDs and exactly one identity/User/Workspace with no orphan candidates.
- The original eight-request race repeats 20 times with unique test data and final SQL counts. Targeted validation passed: 35 tests total (10 deterministic schedules, 20 eight-request races, 5 other persistence-rule tests), zero failures/errors/skips. Full-suite and clean-build validation subsequently passed as recorded below.

### Final validation evidence (2026-09-13)

- Java 21 full suite: `./gradlew.bat test --no-daemon --rerun-tasks` passed, 91 tests across 14 suites, zero failures/errors/skips; all five Gradle tasks executed.
- Java 21 clean build: `./gradlew.bat clean build --no-daemon` passed, all nine Gradle tasks executed, fresh compilation/assembly and all 91 tests passed again with zero failures/errors/skips.
- Each targeted/full/clean run included 10 forced lookup/commit/insert races and 20 eight-request races. Across these three successful runs, 90 race scenarios were checked. Every returned User and Workspace ID converged and PostgreSQL retained exactly one User, AuthIdentity and PersonalWorkspace per tested external identity, without orphan candidates.
- Recovery tests explicitly observed rollback completion before lookup and separate PostgreSQL transaction IDs, including ambient caller transactions. This is a verified production race fix, not a lucky rerun or relaxed synchronization assertion.
- Browser documentation evidence remains valid: `.gradle/task-api-validation/browser/result.json`, `swagger.html`, `swagger.png`. Final OpenAPI contracts were revalidated in both full and clean test runs. Production Swagger protection and application session/CSRF/Origin/ownership regression checks passed.
- Before/after deterministic evidence is preserved under `.gradle/task-api-validation/concurrency/`: `before-fix.xml`, `full-suite-after-fix.xml`, `clean-build-after-fix.xml`. Final complete reports are under `build/test-results/test/` and `build/reports/tests/test/`.
- Final review confirmed no frontend changes, no edits to existing Flyway migrations, no dependency additions or agent-guide changes, no production test-login bypass, and no weakened Task/OpenAPI security requirements. The approved development/test documentation access policy and the authorized User creation race fix are the only additional scope changes.

## Completion

Completed on 2026-09-13. All Execution Sessions and required Final Validation passed under the approved Swagger validation policy. Historical blockers above are resolved or superseded; none remains outstanding. PLAN-0005 is appended to the completed-plan index in `docs/plans/README.md`.
