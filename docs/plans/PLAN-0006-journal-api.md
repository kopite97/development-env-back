# PLAN-0006: Journal API

Status: `completed`

## Goal

Implement the workspace-owned Journal resource according to [backend-implementation-spec.md](../../backend-implementation-spec.md), especially sections 1, 2, 5 and 9, and [backend-development-guide.md](../../backend-development-guide.md). Separate the user-selected entry date from server audit timestamps and deliver Project ownership enforcement, revision-safe mutations, durable creation retries, PostgreSQL integration tests and verified OpenAPI documentation.

Approved by the user with the proposed contract and clarifications accepted. SQL nonblank constraints complement Java/domain validation; they do not reproduce all Java whitespace semantics. Execution proceeds in session order.

## Scope

- Include Journal creation, full detail/list, partial update, permanent deletion, Project relation validation, date filtering, deterministic pagination, DTO validation, security, migrations and tests.
- Use `com.kopite.devspace.journal` with presentation/application/domain/infrastructure layers, domain/JPA entities and application-owned transactions. Follow the existing architecture, Spring Boot, API, database, Flyway, security and implementation guides under `docs/agents/`.
- Reuse the current Java 21, Spring Boot, JPA, PostgreSQL, Flyway, Bean Validation, Spring Security, springdoc and Testcontainers stack. No new dependencies, frameworks, infrastructure services or generic resource framework are proposed.
- Exclude frontend changes, imports/data conversion, Journal trash/restore/archive, attachments, additional Journal fields, standalone Journal statistics, overview/widget implementation, backups and deployment changes. Project archive remains a separate Project operation.
- Preserve the approved PLAN-0005 Swagger policy: anonymous documentation in development/test profiles, protected documentation in default/production; application APIs retain session authentication, CSRF, Origin and ownership enforcement.

## Initial Repository and Document Review

- Reviewed the root instructions, plan guide/runtime template, relevant backend guides, current common/Journal contracts and development guidance, completed Project/Task plans, and existing ownership, query and replay implementations.
- V1–V5 already create User/AuthIdentity/Workspace, Projects, Tasks and their feature-specific creation replay tables. No Journal implementation or migration exists. Proposed new versions are V6/V7; recheck version availability when execution starts and never edit an applied migration.
- Projects provide `UNIQUE(workspace_id,id)` and owned lookup/locking. Workspace resolves from authenticated User and supports owner-row locking and `dataRevision` increments. Task supplies precedents for composite Project ownership, live Project projections, cursor binding and command lock order.
- Existing strict DTO parsing, central `{code,message,fieldErrors,requestId}` errors, database replay stores, clock and cursor signing configuration can be followed without broad refactoring. Task replay currently requires a live Task row; Journal permanent deletion requires the explicitly different replay handling below.
- Preserve PLAN-0005's User/AuthIdentity creation race fix: identity insertion must not merge/reassign a concurrent winner, and conflict recovery runs after rollback in a separate transaction. Its repeated PostgreSQL tests remain part of regression validation.
- The working tree already contains previous implementation changes. This task adds only this Plan; existing changes are preserved. No test/build result is newly claimed by this planning task.
- The available frontend evidence is specification section 9 and development-guide sections 4–6. No frontend source, root `README.md` or referenced `frontend-contract-gap-analysis.md` was found in this repository. Do not present the documented legacy behavior as a fresh frontend code inspection. The development guide's introductory claim that no backend exists predates the implemented baseline.

## Proposed Journal API Contract

Paths below use prefix `/api/v1`, UTF-8 JSON and `Cache-Control: no-store`. Every application endpoint requires the current session and workspace authorization. Mutations use the existing required `X-CSRF-Token` and Origin policy.

| Method and path | Input | Success |
| --- | --- | --- |
| POST `/journals` | `CreateJournalRequest`, required `Idempotency-Key` | 201, full `JournalResponse` |
| GET `/journals/{id}` | Journal UUID | 200, full `JournalResponse` |
| GET `/journals` | `JournalListRequest` filters below | 200, `{items,total,nextCursor}` |
| PATCH `/journals/{id}` | `UpdateJournalRequest`, required body revision | 200, full updated `JournalResponse` |
| DELETE `/journals/{id}` | Required query revision | 200, `DeleteJournalResponse` containing only `{deletedId}` |

Deletion is permanent. There is no PUT, restore, archive, trash or completion endpoint. After deletion, GET/PATCH/repeated DELETE return the same 404 `RESOURCE_NOT_FOUND` used for missing/inaccessible resources. Following a lost DELETE response, an authorized GET returning 404 lets the client reconcile deletion; repeated deletion is not another successful mutation.

### Request/response DTOs and validation

| Field | Create | PATCH when present | Response |
| --- | --- | --- | --- |
| `title` | Required; trim, nonblank, max 120 UTF-16 units | Same constraint | String |
| `projectId` | Required UUID; active owned Project | Active owned target when relation changes | UUID |
| `body` | Required; nonblank, max 20000 UTF-16 units; preserve content | Same constraint | Full text, including in list items |
| `entryDate` | Required valid `YYYY-MM-DD` date | Same constraint | Date string |
| `revision` | Server initializes 1; client field rejected | Required integer 1..9007199254740991 | Safe positive integer |
| `id`, `createdAt`, `updatedAt` | Server-generated; client fields rejected | Rejected | UUID and UTC ISO timestamps |
| `projectName`, `scope` | Derived; client fields rejected | Rejected | Current Project name and `unity/server` |

Create has no optional business fields or date fallback. PATCH changes only supplied fields. Explicit null is invalid for all writable fields; empty/whitespace-only title/body and empty entryDate/projectId are invalid. Body validity uses a whitespace check without stripping stored leading/trailing spaces or line breaks. Store plain text; do not execute HTML or add a Markdown-rendering contract.

Use purpose-specific DTOs, Bean Validation at the boundary and domain invariants internally, following existing strict parsing for unknown/duplicate fields, type coercion, malformed UUIDs and explicit nulls. Reject server/derived/ownership fields (`userId`, `ownerUserId`, `workspaceId`), fractional/string/unsafe revisions, and timestamp values in date fields with 400 `VALIDATION_ERROR`. Never expose JPA entities or internal workspace data. Full list/detail/create/update DTOs have the same fields; do not silently truncate body or substitute a summary DTO.

### entryDate and audit timestamps

- `entryDate` is a calendar date selected by the user, persisted as PostgreSQL DATE and Java LocalDate. It is independent of timezone, offset and time of day; changing device/server timezone must not change its value.
- Parse strictly: reject impossible dates, invalid leap days, timestamps, offsets and noncanonical date strings rather than rolling them into another day. Proposed clarification: use four-digit Common Era years 0001–9999 consistently for entryDate/from/to; no BC, year zero or signed/expanded years. There is no past-only or future-date restriction in the specification.
- `createdAt` is the actual server creation instant; `updatedAt` is the server instant of the latest successful update. Initialize both from the same server clock instant and use the existing UTC/microsecond persistence convention. Clients cannot set either field.
- PATCH may change entryDate, title, body or Project relation and increments revision/updatedAt while preserving createdAt. The specification's “only updatedAt changes” refers to audit timestamps, not a prohibition on editing business fields or revision.
- Never derive entryDate from createdAt, normalize it to local noon/midnight, or apply UTC conversion. For example, a Journal entered as `2026-09-01` and created on `2026-09-13T03:00:00Z` keeps those separate meanings; editing its entryDate to `2026-09-02` leaves createdAt unchanged.

### List, date filters and pagination

- Filters: `scope=all|unity|server` (default all), optional owned `projectId`, `projectStatus=all|active|archived` (default all), query (default `""`), optional `from`/`to` dates, `sort=newest|oldest` (default newest), optional cursor and limit (default 20, range 1..100).
- Check supplied Project ownership before applying the filter intersection. An owned Project excluded by scope/projectStatus/date/search returns an empty page; a foreign/missing Project returns indistinguishable 404 even if the intersection would be empty.
- `from` and `to` compare directly against entryDate and include both endpoints. Either bound may be omitted; same-day ranges are valid; from>to is 400. No timestamp/UTC boundary parameters are accepted.
- Query is case-insensitive literal substring matching on title OR current projectName OR body. Escape SQL LIKE `%`, `_` and its escape character. Preserve the current literal search semantics; no search extension or in-memory table filtering.
- Newest order is `(entryDate DESC,createdAt DESC,id DESC)`; oldest reverses all three keys to ASC. updatedAt is not a sorting key. Project scope/name are live values, never stale copies stored on Journal.
- Query/count in PostgreSQL, fetch limit+1, and return all matching rows' total plus the next cursor or null. Use the existing consistent read-snapshot approach so one response's total and items agree; Project ownership validation belongs in that snapshot as well.
- Journal-specific signed cursor payload includes entryDate, createdAt and UUID, bound to resource namespace, workspace, all effective filters (including both dates), sort and limit. Tampering, malformed cursors, other-workspace/resource use and changed-filter/sort use yield 400 `INVALID_CURSOR`.
- Follow the existing HMAC cursor implementation and externally configured `PROJECT_CURSOR_SIGNING_KEY` shared by Project/Task, with a distinct Journal signing context. No new environment variable is needed. One index can support forward/reverse scanning; predicates must reverse correctly for oldest.
- There is no snapshot guarantee across separate pages. Editing entryDate can move a Journal between pages; later frontend integration must reset cursors on filter changes and deduplicate by ID. No journal status/deleted/tag filters, custom order field or unversioned summary optimization are introduced.

## Proposed Database Schema

### V6__create_journals.sql

| Column | PostgreSQL definition / rule |
| --- | --- |
| `id` | UUID primary key, server-generated |
| `workspace_id` | UUID NOT NULL, immutable; FK to workspaces with DELETE RESTRICT |
| `project_id` | UUID NOT NULL |
| `title` | TEXT NOT NULL, nonblank guard and max character-length guard 120 |
| `body` | TEXT NOT NULL, whitespace-only rejection and max character-length guard 20000 |
| `entry_date` | DATE NOT NULL; range check matching the approved date-year clarification |
| `revision` | BIGINT NOT NULL DEFAULT 1; CHECK 1..9007199254740991 |
| `created_at`, `updated_at` | TIMESTAMP WITH TIME ZONE NOT NULL; created_at immutable in the entity/API |

Enforce `(workspace_id,project_id) REFERENCES projects(workspace_id,id) ON DELETE RESTRICT`. A foreign-workspace Project must fail even through direct SQL. PostgreSQL character-length checks complement Java UTF-16 checks; they do not replace surrogate-pair-aware API validation. No `deleted_at`, archive/status column, copied projectName/scope or date-as-timestamp column is proposed.

Keep Journal an independent aggregate with scalar ownership/Project identifiers, following Task. No bidirectional Project.journals collection, cascade delete or orphan removal is needed. Project archive preserves Journals; Journal permanent delete removes only that Journal.

Propose indexes `(workspace_id,entry_date DESC,created_at DESC,id DESC)` and `(workspace_id,project_id,entry_date DESC,created_at DESC,id DESC)` for required list/date/project predicates and relation lookup. Review generated SQL and representative query plans; no claim that B-tree indexes solve arbitrary body substring search.

### V7__create_journal_create_idempotency.sql

Use the existing feature-local pattern: workspace FK, method restricted to POST, path restricted to `/api/v1/journals`, key (1..128 visible ASCII characters), request hash, successful status 201, serialized original full Journal response, created_at and expires_at. Primary key is `(workspace_id,method,path,key)` with an expiry index and expires_at>created_at check. Workspace's unique owner supplies user scoping.

Retain successful outcomes for 24 hours, including after Journal deletion. Do not add a cascading FK to the live Journal row. Preserve raw submitted business values in a deterministic fingerprint, independent of JSON property order/formatting. Authenticate and validate owned references before replay; create/replay recording and workspace counter update are atomic. Remove expired records in the owned create flow, following existing behavior; no scheduler or generic replay framework is proposed.

## Ownership, Transactions, Revision and Deletion

- Resolve workspace solely from the authenticated internal User. Client body/query/header ownership selectors cannot choose another workspace. Every repository operation requires workspace scope, including permanent delete and replay lookup.
- Every Journal operation validates its Project relation: detail/list/count use a composite workspace+Project join; create/reassignment use owned Project lookup; update/delete resolve the current owned Project; supplied projectId list filters are authorized before intersection.
- Missing and foreign Journal/Project IDs return the same 404 without leaking existence, revision or content. Ownership, including any proposed target Project, precedes revision/business-state conflict reporting.
- Creation and reassignment to a different Project require active status, otherwise 409 `PROJECT_ARCHIVED`. Keeping the same archived Project, even explicitly resubmitting its ID, permits edit/delete. Project rename/scope/archive changes immediately affect Journal projection/search/filtering without changing Journal revision or audit timestamps.
- Commands lock workspace first, then current/target Projects in deterministic UUID order, then Journal. After the workspace lock, discover the owned Journal relation before acquiring Project/Journal locks, as in Task. This serializes Project archive versus Journal create/reassignment and avoids reversed lock order.
- PATCH requires body revision, DELETE requires query revision. Missing/invalid revision is 400; an owned existing row with stale revision is 409 `REVISION_CONFLICT`. Comparison and mutation/delete run in one transaction. Concurrent edits have one winner; concurrent edit/delete outcomes reflect the serialized winner (stale existing row 409, already removed row 404).
- Creation initializes revision 1. Every successful PATCH, including same-value/revision-only PATCH, increments revision and updates updatedAt; overflow yields a conflict before mutation. Proposed clarification for permanent DELETE: validate the current revision then remove the row, without retaining/incrementing a tombstone or adding revision to `{deletedId}`. Deletion at maximum valid revision is allowed because no new revision is published or retained.
- Each actual creation/PATCH/delete increments internal workspace.dataRevision exactly once in the same transaction. Reads, failures, repeated missing DELETE and create replay do not increment it. Do not change public workspace metadata revision or Project revision for Journal writes. Rollback must remove all candidate changes and success replay records.
- Proposed replay clarification: a matching unexpired key returns the original 201 snapshot after later edit, Project archive or permanent Journal delete, without recreating the Journal. Revalidate the replay's workspace and original Project ownership; if the live Journal still exists, validate its current owned Project too. Absence after deletion is not a replay ownership failure: workspace-bound replay storage and retained Project reference establish authorization. This deliberately differs from Task's live-row replay check.
- Replay precedes active-Project state validation once ownership and request equality are established. Different body on an unexpired key yields 409 `IDEMPOTENCY_KEY_REUSED`. After expiry, the key is a new creation attempt subject to active-target checks; clients must reconcile rather than automatically retry uncertain creates beyond retention.
- Use central API errors and existing shared security boundary: anonymous protected requests 401; authenticated missing/invalid CSRF 403; invalid Origin 403; disabled accounts retain shared handling; valid security permits normal Journal handling. Do not log bodies, replay content, CSRF/session tokens or SQL in API errors.

## Verified Frontend/Backend Differences

| Topic | Current document evidence | Later frontend integration |
| --- | --- | --- |
| Journal date | Specification §9 maps editable local Journal.createdAt to entryDate plus immutable createdAt. Guide §4 explicitly describes rewriting createdAt to local noon. | Date display, editor and range filters must use entryDate as a date-only string. Send no createdAt/updatedAt in writes and perform no timezone/noon conversion. Actual frontend source is not available to inspect here. |
| Audit timestamps | Specification §5 separates editable entryDate from server audit instants. | Display creation/update metadata separately when needed; changing the journal date cannot rewrite the record's creation history. |
| Legacy import | Guide §5 says old createdAt may be an edited date and requires a user-confirmed IANA timezone for conversion, preserving provenance. | Handle this only in a later explicit import flow; do not automatically reinterpret local data or add inference to Journal POST. |
| Project reference | Specification §9 maps Journal.project to projectName; §5 requires projectId. | Send a real owned active Project UUID for new/reassigned relations; consume derived name/scope for display. Refresh related caches after Project edits/archive and retain existing archived relations. |
| Ordering/search | Specification §5 uses entryDate/createdAt/id in both directions, inclusive date bounds and body search. | Replace createdAt-only sorting/filtering, send date-only from/to, and use server pagination/total. Preserve full body in list/detail DTOs. |
| Mutation/delete behavior | Specification §2/§5 and guide §4 require async results, revisions, create idempotency and permanent deletion. | Retain drafts on 409; store returned IDs/revisions; no silent overwrite, fake success or Journal trash/restore assumption. Reconcile lost DELETE responses through owned GET. |

The current repository cannot verify an exact frontend date implementation beyond these explicit document statements. This is a later integration verification item, not justification to merge entryDate and audit timestamps.

## Clarifications and Unresolved Decisions

- The specification resolves permanent delete versus archive and date versus audit timestamp semantics; neither remains open.
- Approval of this proposal includes four implementation clarifications: date years 0001–9999, revision-only/same-value PATCH, revision handling for permanent deletion, and ownership-checked replay of the original creation snapshot after permanent deletion. These are recorded explicitly rather than silently copied from Task behavior.
- No new architectural decision is required. Frontend source verification, legacy import timezone choices and operational backup/retention policy remain outside this Plan; backups do not imply an API restore feature.
- Stop and record any genuine contract contradiction or blocker discovered during execution instead of changing the approved contract. New migration version availability must be rechecked before implementation.

## Execution Sessions

The approved sessions below were executed in order. Each session passed its required validation before the next began; final evidence is recorded under Completion.

### Session 1: Persistence, dates and domain invariants

Objective: Establish the Journal model and calendar-date/audit integrity on PostgreSQL.

Implementation:

- [x] Add the Journal migration, domain/JPA entity, value validation and workspace-scoped repository/adapter operations including physical deletion.
- [x] Implement explicit date, revision, immutable ownership/createdAt and partial-update domain rules.

Validation:

- [x] PostgreSQL Testcontainers fresh migration and populated V1–V5 upgrade preserve existing data/checksums and pass Hibernate schema validation.
- [x] Verify required fields, UTF-16 boundaries, body whitespace/content preservation, valid/invalid leap dates, date-year bounds, revision limits and cross-workspace/missing Project FK rejection.
- [x] Round-trip entryDate unchanged across different JVM timezone settings; verify immutable createdAt, UTC audit instants, same-value updates, stale conflicts, overflow, physical deletion and rollback.
- [x] Production/test compilation and Session 1 tests pass.

Evidence (2026-09-13): JournalPersistenceTests passed, 4 tests with no failures/errors/skips. Java 21 compilation, fresh PostgreSQL migration/schema validation, populated V5 upgrade, timezone round trips, date/UTF-16 rules and physical-delete rollback passed.

### Session 2: Commands, ownership, archive races and replay

Objective: Make Journal writes and permanent deletion atomic and safe under concurrency.

Implementation:

- [x] Add feature-local creation replay migration/storage and create/update/delete application services, central errors, Project locks and dataRevision accounting.
- [x] Implement active-target rules and the approved replay behavior after edit/archive/permanent deletion.

Validation:

- [x] Two-user PostgreSQL tests cover all command/replay ownership paths, unchanged archived relations, active reassignment, foreign/missing IDs and no disclosure before revision checks.
- [x] Separate-transaction concurrency tests cover same-key/different-body create races, competing revisions, edit/delete, duplicate delete and Project archive versus create/reassignment, with final row/counter assertions.
- [x] Verify raw-value and JSON-order fingerprint behavior, key scope/expiry, replay after edit/archive/delete, no resurrection, unchanged replay counters and atomic rollback when persistence/replay storage fails.
- [x] Run Session 2 and prior Journal tests; retain existing User creation concurrency regression tests.

### Session 3: REST, date filtering, search and security

Session 2 resumed and passed all 44 tests (0 failures/errors/skips). The injected root cause is verified and rollback assertions execute in a fresh transaction. Session 3 passed 62 Journal/Project/Task/security tests with 0 failures/errors/skips. JournalQueryTests verifies count/page snapshot consistency during a concurrent commit and constant query count across multiple Project relations.

Objective: Expose all five endpoints with the full approved DTO contract.

Implementation:

- [x] Add strict create/update/list/delete DTOs, controller and central validation/error mapping.
- [x] Implement live Project projections, literal body/title/Project search, inclusive date filters and signed three-key cursors in both sort directions.

Validation:

- [x] MockMvc + PostgreSQL validate all methods/status codes/DTO fields, body inclusion, omission/null/type/unknown/duplicate writes, required revision/key and rejected client audit/ownership fields.
- [x] Test anonymous 401, authenticated missing/invalid CSRF 403, invalid Origin 403, disabled-account handling, valid authenticated writes and no-store responses without weakening shared security.
- [x] Verify owned detail/list/search/update/delete/replay, foreign Project filter rejection, scope/projectStatus intersection, live Project rename/scope/archive propagation and deleted-resource 404.
- [x] Cover open-ended/inclusive/same-day/reversed date ranges, leap days, invalid timestamp inputs, timezone independence, newest/oldest ties across every sort key, body literal wildcard search, total/final pages and cursor tampering/filter/sort/workspace/resource mismatch.
- [x] Confirm count/items use a consistent read snapshot and page queries avoid N+1 Project loading; run all Journal, Project, Task and security regression tests.

### Session 4: OpenAPI, real browser documentation and final regression

Objective: Verify the published contract and final build under the approved Swagger policy.

Implementation:

- [x] Document all five endpoints, full schemas, date versus date-time fields, mandatory inputs, body limits, filters/sort, revisions, idempotency and required CSRF headers.
- [x] Add generated OpenAPI assertions and extend the documentation-only browser harness for visible Journal operations/schemas; no authenticated Swagger CRUD/security workflow is required.

Validation:

- [x] Anonymous development/test `/v3/api-docs` matches methods, request/response DTOs, status codes, defaults, constraints, `entryDate` format date, audit fields date-time/read-only, DELETE query revision and `{deletedId}` response.
- [x] Real browser renders Journal operations and schemas without login in development/test. Keep default/production documentation protected and application APIs secured; verify via existing/incremental integration tests.
- [x] Run the full test suite with required Testcontainers tests actually executed, including repeated User/AuthIdentity creation races and all Journal concurrency checks.
- [x] Run a clean build, inspect test reports and record browser/OpenAPI/test evidence without sensitive data.

## Final Validation

- [x] All sessions and required validations pass; no unresolved blocker remains.
- [x] Java 21 `./gradlew.bat test --no-daemon --rerun-tasks` and `./gradlew.bat clean build --no-daemon` pass without failed/skipped required tests.
- [x] Persistence/upgrade, date semantics, ownership, active-target races, revisions, physical deletion, replay-after-delete, search/pagination and shared authentication regressions pass.
- [x] Review final diff: no frontend changes, applied-migration edits, new dependency/framework, weakened security or unapproved architectural change.

### API Validation

- [x] All five Journal endpoints appear in `/v3/api-docs` with the approved methods/status codes.
- [x] DTOs, required fields, validation constraints, full-body list contract, date formats, revisions, idempotency and CSRF headers are documented correctly.
- [x] Swagger UI visibly renders Journal operations/schemas in a real browser under development/test access policy.
- [x] Runtime CRUD, ownership and security behavior is verified by MockMvc + PostgreSQL Testcontainers, separately from Swagger rendering.

## Completion

Completed on 2026-09-13. All four sessions and Final Validation passed. No unresolved implementation or validation blocker remains.

### Final evidence

- Documentation-only browser harness checks visible semantic buttons by HTTP method, endpoint path, operation label and exact schema name. It no longer uses Swagger internal CSS classes or JavaScript state. No production Journal API, OpenAPI contract or security behavior was changed for browser validation.
- Real headless Chrome rendered all 5 Journal operations and all 5 Journal schemas anonymously. `/v3/api-docs` returned 200 without credentials and required CSRF headers remained documented. Result: `JOURNAL_API_SWAGGER_DOCUMENTATION_PASS`. Evidence: `.gradle/journal-api-validation/browser/result.json`, `swagger.html`, `swagger.png`; screenshot inspected.
- Java 21 `./gradlew.bat test --no-daemon --rerun-tasks`: BUILD SUCCESSFUL; 19 suites, 107 tests, 0 failures, 0 errors, 0 skipped. Evidence preserved in `.gradle/journal-api-validation/full-test/` (XML reports, summary.json and openapi.json).
- Java 21 `./gradlew.bat clean build --no-daemon`: BUILD SUCCESSFUL; all 9 tasks executed, including tests. 19 suites, 107 tests, 0 failures, 0 errors, 0 skipped. Boot and plain JARs were produced in `build/libs`. Evidence: `build/reports/tests/test/index.html`, `build/test-results/test/`, `build/reports/journal-api/openapi.json`; preserved copies in `.gradle/journal-api-validation/clean-build/`.
- Both full runs include 16 Journal tests, the 10 forced User/AuthIdentity creation-conflict repetitions and 25 UserWorkspacePersistenceRulesTests (including 20 concurrent creation repetitions), plus Project/Task/authentication/production Swagger regressions. PostgreSQL Testcontainers tests actually executed.
- Final review confirms no frontend changes, applied-migration modifications, new dependency/framework or unapproved architecture/security changes. Resume baseline hashes confirm this final resumption changed only the browser harness and Plan/index documentation. Existing unrelated working-tree changes were preserved. No new application environment variable was added.
- Completed-plan index updated in [README.md](README.md).

### Resolved blockers and session evidence

- Session 1 passed 4 JournalPersistenceTests: fresh/upgrade migrations, date and UTF-16 rules, timezone round trips, physical deletion and rollback. SQL whitespace checks complement Java/domain validation.
- Session 2 originally failed because the replay-storage spy's injected IllegalStateException was translated by Spring to InvalidDataAccessApiUsageException. The resumed test verifies the intended root cause/message instead of requiring the outer infrastructure type. Fresh-transaction assertions verify no Journal/replay record, unchanged workspace.dataRevision, and unchanged Project/Workspace metadata revisions. All 44 Session 2 regression tests subsequently passed.
- Session 3 passed all 62 selected Journal/Project/Task/security tests. JournalQueryTests confirms count/page snapshot consistency during a concurrent commit and constant query count across increasing Project relations.
- Session 4 OpenAPI and production Swagger protection tests passed. The original browser script then failed at `Journal schema models not rendered` because it searched for `.model-container`. Diagnostic screenshot/DOM proved all operations and schemas were rendered; the mismatch was in the harness. Original evidence remains in `.gradle/journal-api-validation/browser/blocker.json`, `blocker.html` and `blocker.png`.
- The approved resumption replaced internal selectors with stable rendered-content assertions, passed the complete browser workflow, and passed both final test/build runs above. Application APIs retain session authentication, CSRF, Origin and ownership enforcement; default/production Swagger remains protected.
