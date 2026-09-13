# PLAN-0007: Milestone API

Status: `completed`

## Goal

Implement the workspace-owned Milestone resource using [backend-implementation-spec.md](../../backend-implementation-spec.md), especially sections 1, 2, 6, 7 and 9, as the implementation contract, supported by [backend-development-guide.md](../../backend-development-guide.md). Deliver persistence, owned Project relations, completion/reopening, permanent deletion, revision-safe updates, durable creation retries, deterministic pagination, security and verified documentation.

Approved by the user, including the permanent-delete contract and all proposed clarifications. Execution proceeds in session order with in-contract diagnosis, correction and revalidation before continuing.

## Scope

- Use `com.kopite.devspace.milestone` with presentation/application/domain/infrastructure layers, application-owned transactions and a domain/JPA entity, following the existing feature architecture and backend guides.
- Include five HTTP operations: create, detail, list, partial update and permanent delete. Completion/reopening uses PATCH of `completed`; there is no trash, soft delete, tombstone, archive, restore, separate transition endpoint or bulk ordering API.
- Preserve the specification's boolean `completed`. Do not introduce a persisted/response status enum, Milestone progress, body, completion criteria, priority, tags, position or completedAt field. `status` is only a list filter.
- Reuse the existing Java 21, Spring Boot, JPA, PostgreSQL, Flyway, Bean Validation, Spring Security, springdoc, clock, HMAC cursor configuration and Testcontainers stack. No new dependency, framework, generic resource abstraction or infrastructure service is proposed.
- Exclude frontend implementation, local-data imports, dashboard/widget implementation, Project progress calculation, overview/statistics and deployment changes. Preserve existing unrelated working-tree changes.
- Keep application session authentication, CSRF, Origin validation and ownership enforcement intact. Follow the approved documentation-only Swagger validation policy from PLAN-0005/0006.

## Initial Repository and Document Review

- Reviewed root AGENTS.md, the plan guide/runtime template, relevant architecture/Spring/API/database/Flyway/security/implementation guides, the current backend specification and development guide, and the completed Journal plan.
- Project, Task and Journal implementations already provide owner-scoped lookup/locking, workspace.dataRevision accounting, full DTO projections, strict write parsing, central errors, feature-local creation replay and signed cursors. Milestone implementation is absent. V1–V7 exist; proposed new versions are V8/V9, subject to checking availability at execution start.
- The Project table provides `UNIQUE(workspace_id,id)` for composite ownership FKs. Project names/scopes are projected live by child resources. Workspace rows serialize business mutations before Project/resource locks.
- Journal's strict parser rejects all explicit nulls and its update command uses null as omission. Neither behavior can be copied unchanged for Milestone's nullable dueDate. Milestone needs explicit field-presence handling.
- Task/Journal require active Projects for creation/reassignment. Specification section 6 explicitly permits Milestone work on owned archived Projects; that active-only rule must not be copied.
- Specification section 6 explicitly states `projectId, projectName, scope, title, dueDate, completed` and excludes separate progress/status/body fields. Section 9 says `completed` stays unchanged and local empty dueDate becomes null. The suggested boolean-to-richer-status mismatch is therefore not present in the current contract.
- No frontend source/package manifest or referenced `frontend-contract-gap-analysis.md` was found in this repository. Frontend observations below are grounded in the supplied documents, not a claimed source-code inspection. The development guide's historical statement that no server exists does not override the actual implemented backend.
- PLAN-0006 records successful real-browser validation and 107 tests in both full test and clean build runs. Those are existing evidence, not tests run as part of creating this Plan.

## Proposed API Contract

All paths use `/api/v1`, JSON UTF-8, the common error DTO `{code,message,fieldErrors,requestId}` and `Cache-Control: no-store` for personal API responses.

| Method and path | Input | Successful response |
| --- | --- | --- |
| POST `/milestones` | Required projectId and title; optional dueDate and completed; required Idempotency-Key and X-CSRF-Token headers | 201 full MilestoneResponse, including matching creation replay |
| GET `/milestones/{id}` | Canonical UUID id | 200 full owned MilestoneResponse, including completed items and archived Project relations |
| GET `/milestones` | scope, projectId, projectStatus, status, cursor, limit | 200 `{items,total,nextCursor}` |
| PATCH `/milestones/{id}` | Required revision plus any supplied writable fields; required X-CSRF-Token header | 200 full updated MilestoneResponse |
| DELETE `/milestones/{id}?revision=...` | Required current revision query parameter and X-CSRF-Token; authenticated ownership and Origin validation | 200 `{ "deletedId": "..." }` |

No PUT, archive, restore, stats or reorder route is implemented or documented. Verify unsupported PUT remains 405 for authenticated requests satisfying security requirements; DELETE is supported as specified above.

### Fields, DTOs and validation

Full MilestoneResponse has exactly ten fields: `id, revision, createdAt, updatedAt, projectId, projectName, scope, title, dueDate, completed`. All are present, including `dueDate: null` when unset. Detail, create, update and list items share this full contract. Internal workspace/User information is never exposed.

Use purpose-specific CreateMilestoneRequest, UpdateMilestoneRequest, MilestoneListRequest, MilestoneResponse, MilestoneListResponse and DeleteMilestoneResponse (`deletedId` UUID only), with application commands/snapshots behind the presentation boundary. Do not serialize entities.

| Field | Write rules and semantics |
| --- | --- |
| projectId | Required on POST, optional on PATCH; non-null canonical UUID referencing an owned Project. No Project-name fallback. |
| title | Required on POST; trim, reject blank, maximum 200 UTF-16 code units after trimming. Omission on PATCH preserves; null/empty/blank rejects. |
| dueDate | Optional date or null; POST omission defaults to null. PATCH omission preserves; explicit null clears; a date replaces. |
| completed | Optional JSON boolean; POST omission defaults to false. PATCH omission preserves; true completes, false reopens. Explicit null/string/numeric values reject. |
| revision | Required positive safe integer in PATCH body or DELETE query, 1..9007199254740991; not writable on POST. |
| id, createdAt, updatedAt, projectName, scope | Server-issued/derived, read-only; reject in writes. |

- Use Bean Validation at the DTO boundary and enforce business invariants in the domain. Preserve strict JSON token typing, reject unknown/duplicate writable keys, arrays in place of objects, explicit nulls except dueDate, malformed UUIDs and fractional/string/unsafe revisions. Duplicate detection must work even if the first dueDate value is null; do not use a null value to infer absence.
- Reject `userId`, `ownerUserId`, `workspaceId`, `status`, `progress`, `position`, `body` and other undeclared write fields with 400 `VALIDATION_ERROR`. Never derive authorization from client ownership selectors in any transport location.
- Proposed date clarification: accept only valid canonical `YYYY-MM-DD` dates in Common Era years 0001–9999, matching Journal's date bounds. Reject timestamps, offsets, impossible dates, invalid leap days, year zero and expanded years. Persist Java LocalDate/PostgreSQL DATE; timezone changes cannot change dueDate.
- The API accepts dueDate as a date or null, not an empty string. Specification section 9's local `dueDate=""` conversion belongs in the future frontend adapter. Do not add backend empty-string coercion.
- Track PATCH field presence separately from the nullable dueDate value. Do not introduce a generic patch framework or new dependency for this feature-local requirement.

### Status, progress and audit semantics

- `completed=false` is open, `completed=true` is done. Both directions are permitted, including on owned archived Projects. Creating an already-completed Milestone is supported.
- `PATCH {"completed":true,"revision":n}` completes and `PATCH {"completed":false,"revision":n}` reopens. The list filter `status=open|done|all` maps to this boolean; it is not a resource field or writable enum.
- There is no Milestone progress percentage and no rule deriving completion from a percentage. Completing/reopening a Milestone does not change Project.progress, Project.currentMilestone, Project revision or Project audit timestamps.
- Project.currentMilestone is an independent goal memo, not a Milestone id. Editing that memo never creates or updates Milestones; Milestone edits never overwrite the memo.
- dueDate is independent of audit timestamps and completion. Past dates are accepted, completion does not clear dates, and reopening preserves dates unless explicitly changed. Overdue items remain in list results.
- The frontend computes overdue as `!completed && dueDate != null && dueDate < browser-local today`. Do not persist or return a timezone-dependent overdue field or introduce a server “today” filter.
- Initialize createdAt/updatedAt from the same server UTC instant, using the existing microsecond convention. createdAt is immutable. Every successful PATCH updates updatedAt and revision, including same-value and revision-only PATCH (proposed clarification consistent with existing resources).

### Listing and deterministic ordering

- Filters: scope=`all|unity|server` (default all), optional owned projectId, projectStatus=`all|active|archived` (default all), status=`open|done|all` (default open), optional cursor and limit (default 20, 1..100).
- The resource-specific list contract has no search query, date-range filter, client sort or custom position. Do not inherit Journal's search/date/sort endpoints or add fields for speculative ordering.
- Validate a supplied Project's ownership before intersecting filters. Owned-but-excluded scope/status yields an empty page; foreign/missing Project yields the same 404, even when the intersection would otherwise be empty.
- Order exactly `completed ASC, dueDate ASC NULLS LAST, id ASC`. Nulls are last within each completion group: undated open items still precede every done item when status=all. Audit timestamps are not ordering keys.
- Implement database keyset pagination with limit+1 and an exact matching total. For the same completion group, a dated cursor admits later dates, same-date greater UUIDs and null dates; an undated cursor admits only greater UUIDs with null dates. An open cursor also admits the done group when filters allow it. Never compare SQL null as a normal date or use a fake sentinel date.
- Sign a Milestone-specific cursor carrying completed, an explicit nullable dueDate representation and id. Bind resource namespace, workspace, every effective filter, fixed ordering and limit. Use the existing externally configured cursor key with a separate Milestone HMAC context; no new environment variable is planned.
- Malformed/tampered/cross-resource/cross-workspace/changed-filter/changed-limit cursors return 400 `INVALID_CURSOR`. Use unambiguous encoding for null dates and filter values, following current cursor signing patterns.
- Count and items, including Project-filter authorization, use one read-only REPEATABLE_READ snapshot. Join Project on workspace_id and project_id for live projectName/scope and projectStatus filtering, avoiding per-row lazy loading/N+1.
- No snapshot guarantee spans separate page requests. Completion/date edits may move items between pages; later frontend integration must reset cursors and deduplicate IDs.
- Consumers choose explicit display policy: normal home projectStatus=active; selected-Project widget/detail projectStatus=all + projectId. Widget default display count 2 is a frontend request limit, not a change to the generic API default 20. Detail must allow paging through all matches.

## Proposed Database Schema

### V8__create_milestones.sql

| Column | PostgreSQL definition / rule |
| --- | --- |
| id | UUID primary key, server-generated |
| workspace_id | UUID NOT NULL, immutable; FK workspaces(id), DELETE RESTRICT |
| project_id | UUID NOT NULL |
| title | TEXT NOT NULL; basic nonblank and max character-length 200 checks |
| due_date | Nullable DATE, default null; CHECK null or date in 0001-01-01..9999-12-31 |
| completed | BOOLEAN NOT NULL DEFAULT false |
| revision | BIGINT NOT NULL DEFAULT 1; CHECK 1..9007199254740991 |
| created_at, updated_at | TIMESTAMP WITH TIME ZONE NOT NULL; created_at immutable in entity/API |

Enforce `(workspace_id,project_id) REFERENCES projects(workspace_id,id) ON DELETE RESTRICT`. Model Milestone as an independent aggregate with scalar workspace/Project identifiers. Provide an owned repository delete operation for the Milestone row only. Do not add cascade deletion, orphan removal or a bidirectional Project collection; deleting a child must not change its Project or siblings.

SQL basic btrim/length guards complement Java's nonblank and UTF-16 validation; do not reproduce every Java whitespace rule in SQL. PostgreSQL character length does not replace Java code-unit checks.

Propose indexes `(workspace_id,completed,due_date ASC NULLS LAST,id)` and `(workspace_id,project_id,completed,due_date ASC NULLS LAST,id)` for workspace-wide and Project-specific ordered pagination/FK lookup. Review generated SQL and representative plans with dated/undated and open/done rows. No persisted status/progress/position/deleted_at/overdue columns are needed.

### V9__create_milestone_create_idempotency.sql

Follow the existing feature-local replay table: workspace FK, method restricted to POST, canonical path `/api/v1/milestones`, key length 1..128, request hash, successful response status 201, serialized original full response, created_at, expires_at, expiry index and CHECK expires_at>created_at. Primary key `(workspace_id,method,path,key)` scopes the unique owner's retries. Service validation requires visible ASCII `[!-~]` keys.

Retain outcomes for 24 hours and remove expired owned records in the create flow, following existing adapters. No new scheduler or generic replay infrastructure. Store success and business changes atomically in the application transaction. Retain creation outcomes through permanent deletion; do not cascade replay deletion or require an FK to the live Milestone row.

Recheck migration version availability before implementation; use next unused versions if concurrent repository work consumes V8/V9. Never modify an applied migration, repair checksums or discard existing data to make tests pass.

## Permanent Deletion

- DELETE `/api/v1/milestones/{id}?revision=...` requires the authenticated owner's workspace, current Project relation, current Milestone revision, X-CSRF-Token and valid Origin under shared security rules. It is allowed for open/done items on active/archived owned Projects.
- Missing/invalid revision is 400 VALIDATION_ERROR. Missing/inaccessible Milestone is 404 RESOURCE_NOT_FOUND before disclosing revision; a stale revision on an owned existing Milestone is 409 REVISION_CONFLICT.
- Under workspace -> Project -> Milestone locks, compare revision and physically delete only that Milestone in one transaction, incrementing workspace.dataRevision exactly once. Return 200 `{ "deletedId": "..." }` with no body revision. Do not increment/store a deleted resource revision; deletion at the maximum valid revision is allowed.
- No trash, deleted_at, restore or tombstone. Subsequent GET/PATCH and repeated DELETE return 404. Failed/repeated deletion does not increment counters. Project data/revision/audit fields, other Milestones and public Workspace metadata revision remain unchanged.
- Competing deletes yield one 200 and one 404. In edit/delete races, an edit winner makes the old-revision delete conflict; a delete winner makes the later edit return 404. Rollback restores both row and counter; no partial persistence is allowed.
- DELETE requires no creation Idempotency-Key and records no delete replay/tombstone. Existing POST replay records remain available for their retention period without resurrecting deleted data.

## Ownership, Revisions, Replay and Concurrency

- Resolve workspace exclusively from authenticated internal User through the existing shared current-user path. Every read, list/count, mutation and replay uses owned workspace scope. Missing/foreign Milestone or Project ids return indistinguishable 404 before revision or state disclosure.
- Validate the Project relation on every operation: detail/list/count use the composite join; create checks the requested Project; PATCH checks current and proposed target Projects; DELETE validates the current owned Project before revision checks; replay checks the stored original Project and current Milestone relation.
- Owned archived Projects are valid for create, edit, completion, reopening, reassignment and deletion. Proposed clarification: reassignment to another owned archived Project is allowed as part of the specification's edit permission. Do not raise PROJECT_ARCHIVED solely for Milestone operations. Project status affects list filtering, not mutation eligibility.
- Follow the established lock order: workspace row, current/target Projects in deterministic UUID order, then Milestone. Discover the owned relation after the workspace lock and verify it under the resource lock. Project archive versus Milestone work must remain consistent without an active-only rejection rule.
- Initial revision is 1. Compare expected revision under lock and mutate in the same transaction. Missing/invalid revision is 400; stale existing owned revision is 409 `REVISION_CONFLICT`; overflow conflicts before any mutation. Competing updates/completion/reopening with the same revision have one winner.
- Each actual create/PATCH/DELETE increments workspace.dataRevision exactly once atomically. Reads, replay, failures and repeated missing DELETE do not increment it. Public Workspace metadata revision and Project metadata/progress/memo remain unchanged by Milestone writes.
- Idempotency-Key is mandatory for POST only. Same unexpired key/body returns the original full 201 snapshot after subsequent Milestone edits/completion/reassignment/permanent deletion or Project rename/archive, rather than the latest resource DTO. A different body on the same key returns 409 `IDEMPOTENCY_KEY_REUSED` after ownership checks. A new/expired key is a new create attempt.
- Proposed fingerprint clarification: use deterministic field order and submitted field presence plus raw values, independent of JSON property order/formatting. Distinguish omitted dueDate from explicit null, and omitted completed from explicit false, even though their create defaults coincide; different submitted bodies conflict. Retain raw title before trimming, preserve boolean types and canonicalize UUID representation consistently with existing resources. This requires presence information to survive request-to-command mapping.
- Retain creation replay after deletion and return the original 201 snapshot for an unexpired matching key/body after workspace/original Project ownership checks. If the live Milestone exists, validate its current Project too; its absence after deletion is not a replay failure. Never recreate an aggregate or increment dataRevision on replay. This does not change repeated DELETE returning 404.
- Journal/Task replay adapters use JPA plus JDBC in one Spring transaction. Test failure injection after the replay insert, verify the injected cause through persistence exception translation, and inspect rollback in a fresh transaction: no Milestone, no successful replay, unchanged workspace counter and no partial state. Do not assert an inappropriate exact outer infrastructure exception type.
- Retain existing User/AuthIdentity/Workspace concurrency regression tests. If a uniqueness-conflict recovery is exercised, the losing transaction must fully roll back before a separate-transaction winner lookup; never accept a passing rerun as proof of a concurrency fix.

## Security and Swagger Validation

- Preserve the shared security boundary: anonymous protected requests 401; authenticated missing/invalid CSRF 403; invalid Origin 403; disabled account handling unchanged; valid security permits normal owned endpoint handling. Do not add resource-specific security bypasses.
- POST/PATCH/DELETE documentation requires X-CSRF-Token; POST also requires Idempotency-Key. OpenAPI documents session-cookie security, required/nullable fields, constraints, defaults and common 400/401/403/404/409/500 errors. Do not advertise Milestone PROJECT_ARCHIVED as a mutation or deletion restriction. Document required DELETE query revision, 200 DeleteMilestoneResponse, stale 409 and missing/repeated 404; no request body or Idempotency-Key is required for DELETE.
- Development profiles dev/development/local/test allow anonymous GET Swagger UI and `/v3/api-docs`; default/production remain protected, with prod/production overriding development/test. Keep this existing configuration unchanged unless a verified in-contract defect requires correction.
- Use Swagger/OpenAPI to validate documentation: all five operations, methods/statuses, fields, date/null format, read-only audit/derived fields, boolean completion, query-only status enum, revision and headers. dueDate is optional and nullable in requests but always present and nullable in responses. PATCH omission must not be documented as a default that silently clears data.
- Real-browser validation checks rendered method/path/operation-label and visible schema-name text using semantic controls/accessibility roles, following the corrected Journal harness. Avoid Swagger internal CSS classes and internal JS state. Handle actual navigation/readiness rather than assuming rendering is synchronous.
- Swagger is not required to execute authenticated CRUD or deliberately cause security errors. Validate runtime/security through Spring Security + MockMvc + PostgreSQL Testcontainers. Keep required CSRF documentation and runtime protection intact when correcting harness issues.

## Verified Frontend/Backend Differences

| Topic | Current document evidence | Later integration work |
| --- | --- | --- |
| Completion versus richer status/progress | Spec section 6 explicitly keeps completed boolean and excludes status/progress fields; section 9 says completed stays unchanged. | Keep the boolean. Use status only for open/done/all list queries. Do not invent a richer resource enum or percentage conversion. |
| Revision and server metadata | Spec section 2 requires server ids, initial revision 1, safe revisioned PATCH and audit timestamps; section 9 replaces synchronous boolean save outcomes. | Store returned id/revision; complete/reopen with revision; await success and retain drafts on 409 instead of silently overwriting. |
| Unset dueDate | Spec sections 6/9 map local empty string to API null. | Convert empty editor values to null. Preserve PATCH omission versus explicit clearing. Keep date-only strings, with no UTC/noon conversion. |
| Overdue display | Spec section 6 defines browser-today comparison for incomplete dated items. | Compute locally; do not expect a stored overdue flag or exclude overdue rows from server lists. |
| Archived Projects | Spec section 6 permits owned archived targets; sections 7 and guide 4 distinguish home/detail queries. | Permit archived targets in Milestone forms; send active for general home, all + projectId for selected Project/detail. Do not apply Task/Journal active-only selection assumptions. |
| Project memo/progress independence | Spec sections 4/6 and guide section 4 separate currentMilestone memo and manual Project progress from Milestones. | Do not sync completion with progress or generate Milestones on memo edits. Refresh affected views without altering unrelated Project fields. |
| Ordering and pagination | Spec sections 2/6/7 define completed/date-null-last/id ordering, API limit 20, widget display 2 and detail paging. | Use server cursors/total, request widget limits explicitly, reset cursors after filters/mutations and deduplicate IDs. Do not treat one page as all data. |
| Permanent deletion | Updated spec section 6 defines revision-checked permanent DELETE and repeated 404; guide section 4 defines cache reconciliation. | Send current revision with CSRF, remove the item after confirmed success, refresh related caches, retain conflict state, and reconcile a lost response with owned GET. Do not offer trash/restore or recreate through a creation retry. |
| Legacy import | Guide section 5 preserves existing milestones, respects an explicit empty array and derives memo-based targets only when the key is absent and the user opts in. | Defer to a separately approved import flow; no auto-seeding during Milestone creation, login or migration. |

These are verified document statements. Confirm actual frontend source and adapters during later integration; no frontend files are changed by this Plan.

## Clarifications and Unresolved Decisions

- The current contract resolves the user's status/progress and deletion questions: completed boolean, query-only status, no Milestone progress and an explicitly authorized permanent-delete API. There is no contradiction requiring a richer model.
- Approval of this Plan includes the explicit clarifications above: years 0001–9999; API empty dueDate rejection with frontend normalization; same-value/revision-only PATCH; archived-Project reassignment; and field-presence-aware creation fingerprints. These clarifications were approved and are implemented and validated.
- No new architectural decision or dependency is required. Exact frontend-source verification and legacy import choices remain outside this Plan. Migration version numbering is a repository availability check, not a new schema decision.

## Execution Policy: Recover Within the Approved Contract

After explicit approval, change status to `active` and execute all sessions in order. Complete and revalidate each session before advancing; do not stop between sessions for routine errors or request approval again for work already authorized.

- Diagnose, correct and revalidate implementation bugs, compilation/type-inference errors, transaction-boundary defects, fixture/isolation/synchronization problems, OpenAPI typing/annotation mismatches and browser harness/readiness/selector issues that can be resolved within the approved contract. Then continue execution.
- Keep failures and root-cause evidence while fixing them. Check validation items only after actual success. Run affected regressions after corrections and the required final full suite/clean build. Do not weaken assertions, skip required tests, use raw/unchecked typing workarounds, change API semantics or relax security to obtain a pass.
- Investigate nondeterministic failures through controlled synchronization, independent transactions and final persisted-state checks. Reproduce the affected concurrency case repeatedly and in the full suite; a lucky rerun is not a fix.
- Stop only for a genuine decision-level blocker: contradictory approved requirements; a necessary unapproved architecture/schema change; a proposed security/data-integrity compromise; unavailable external prerequisites after reasonable authorized recovery; or a root cause that cannot be resolved reliably within the approved design.
- On such a blocker, keep status `active`, leave incomplete checks unchecked, and record reproduction, diagnosis, attempted fixes, affected validations and the exact decision/external requirement needed before stopping. Never mark completed solely to close the task.
- This policy applies to this Plan; do not modify shared agent guides or earlier Plans as part of its adoption.

## Execution Sessions

All approved sessions were executed in order. Checked items have passed implementation and validation.

### Session 1: Persistence, nullable dates and domain rules

Objective: Establish a valid owned Milestone aggregate and safe migrations.

Implementation:

- [x] Add Milestone entity/domain validation, owned repository operations and V8 migration with composite FK and explicit null-last indexes.
- [x] Implement boolean completion/reopening, nullable date changes, immutable ownership/audit fields and revision/overflow rules without introducing progress/status storage.

Validation:

- [x] PostgreSQL Testcontainers fresh migration/Hibernate validation and populated V7 upgrade preserve User/Project/Task/Journal/replay data and V1–V7 checksums.
- [x] Verify DB required fields, basic title checks, boolean/revision/date bounds and rejection of missing/cross-workspace Project FKs; Java title trim/nonblank/UTF-16 limits including supplementary characters.
- [x] Verify null/date round trips across JVM timezones, leap dates, year bounds, impossible/timestamp inputs, same-value updates, immutable createdAt, initial/evolving revisions and overflow rollback.
- [x] Verify physical row deletion/rollback, deletion at maximum valid revision, preservation of Project and sibling rows, and absence of tombstone/soft-delete state.
- [x] Production/test compilation and Session 1 tests pass; no migrations or tests are skipped to hide failures.

Session 1 evidence: MilestonePersistenceTests passed all 4 tests after the JDBC date-comparison fixture correction; zero failures/errors/skips.

### Session 2: Owned commands, completion, deletion and atomic retries

Objective: Make creation, updates and permanent deletion atomic, revision-safe and correct on archived Projects.

Implementation:

- [x] Add V9 replay storage and create/update/delete command services with consistent lock order, field-presence-aware fingerprints, owned references and dataRevision accounting.
- [x] Implement nullable-date patch semantics, complete/reopen and reassignment on active or archived owned Projects; preserve Project memo/progress metadata.

Validation:

- [x] Two-user tests cover current/target Project authorization, missing/foreign ids before revision disclosure, replay authorization after reassignment and archived target creation/edit/completion/reopening/reassignment.
- [x] Separate-transaction races cover matching-key and conflicting-body creation, competing edits/completion/reopening, and Project archive versus creation/reassignment. Assert response identities/revisions and final row/counter state; archive does not cause Milestone PROJECT_ARCHIVED errors.
- [x] Verify original 201 replay after edits/Project changes, JSON key-order equivalence, raw/presence differences, omitted/null dates, omitted/false booleans, 24-hour expiry and per-workspace key scope.
- [x] Inject persistence/replay failure, assert intended cause through Spring translation, then in a fresh transaction verify no candidate aggregate/replay/partial state and unchanged counters/Project metadata.
- [x] Verify deletion ownership before revision disclosure, stale conflict, exactly-once counter increment, repeated 404, complete/open and archived-Project deletion, edit/delete and duplicate-delete races. In fresh transactions prove rollback restores the row/counter and preserves Project/sibling metadata. Verify create replay after deletion returns the original snapshot without resurrection or counter increment.
- [x] Run Session 2 plus prior Milestone tests and existing repeated User/AuthIdentity creation regressions. Diagnose/fix/revalidate in-contract failures before proceeding.

Session 2 evidence: 49 tests passed (14 Milestone and 35 User creation regressions), zero failures/errors/skips, including five repeated command-race runs with final PostgreSQL state checks.

### Session 3: REST, null-aware pagination and shared security

Objective: Expose the five operations with strict DTOs and consistent owned queries.

Implementation:

- [x] Add strict create/update/list/delete response DTOs, response projections, controller and central errors; distinguish omitted/null dueDate without permitting other nulls or type coercion.
- [x] Implement completion/date/id keyset ordering, nullable cursor encoding/signing, live Project filters/projections and same-snapshot count/page reads.

Validation:

- [x] MockMvc + PostgreSQL verify five methods/statuses/full DTOs, defaults, field omission versus null/false, required key/revision, duplicate-null keys, unknown/derived/ownership fields and rejected status/progress writes. Verify permanent DELETE returns only deletedId; there is no trash/restore/reorder capability.
- [x] Verify anonymous 401, missing/invalid CSRF 403, invalid Origin 403, disabled accounts, valid authenticated mutations and no-store responses through shared security.
- [x] Exercise DELETE with missing/invalid/stale/current revisions, missing/foreign/repeated ids, anonymous/missing-CSRF/invalid-CSRF/invalid-Origin requests, no-store 200 deletedId, GET/PATCH-after-delete 404 and disappearance from all list totals; assert Project and sibling Milestones remain unchanged.
- [x] Test all scope/Project/status intersections, foreign Project filters, live Project rename/scope/archive propagation and unchanged Milestone revision after Project-only edits.
- [x] Cover open/done/all, dated/undated items, ties, transition from open nulls to done dates, multiple null-date pages, empty/final pages, overdue inclusion, totals and every cursor context/tampering mismatch. Compare complete pagination against PostgreSQL's full approved sort order without missing/duplicate IDs.
- [x] Prove count/items remain consistent during a concurrent commit and query count does not grow per Project row; inspect null-last SQL/index use. Run all Milestone/Project/Task/Journal/security regressions.

Session 3 evidence: all 83 selected Milestone/Project/Task/Journal/security tests passed with zero failures/errors/skips. SQL plan evidence: build/reports/milestone-api/query-plan.txt; PostgreSQL used an owned-workspace index and explicit completed/date/id ordering.

### Session 4: Documentation, real browser and final regression

Objective: Verify the published contract and finish a reproducible build.

Implementation:

- [x] Add OpenAPI assertions for all five operations, exact DTO fields, date-or-null dueDate, boolean defaults, query-only status, revision constraints, errors and required CSRF/idempotency headers.
- [x] Verify OpenAPI DELETE query revision is required with safe integer bounds, response is only required deletedId UUID, errors include 400/401/403/404/409, and X-CSRF-Token/session security remain mandatory.
- [x] Add a documentation-only real-browser harness using visible method/path/operation labels and schema names/semantic roles; avoid internal Swagger selectors and authenticated CRUD dependencies.

Validation:

- [x] Anonymous development/test `/v3/api-docs` matches the approved contract, with required versus nullable fields correctly distinguished and no extra status/progress/trash/restore behavior; DELETE is documented with required query revision and DeleteMilestoneResponse.
- [x] Real Chrome renders all Milestone operations and schemas without login; record result/HTML/screenshot and inspect the screenshot. Existing default/production Swagger and application security protections still pass integration tests.
- [x] Java 21 `./gradlew.bat test --no-daemon --rerun-tasks` passes the full suite, including all required PostgreSQL and repeated concurrency tests, with no failed/skipped required tests.
- [x] Java 21 `./gradlew.bat clean build --no-daemon` passes with tests actually executed. Inspect XML reports, preserve browser/OpenAPI/test evidence outside cleanable build output where needed, and correct/revalidate recoverable failures before finishing.

## Final Validation

- [x] Every session and required validation passes; no unresolved implementation/decision-level blocker remains.
- [x] Full test suite and clean build pass with actual Testcontainers execution and no failed/skipped required tests; report counts and evidence.
- [x] Persistence/upgrade, dueDate presence/null/timezone behavior, completion/reopening, permanent deletion/repeated 404, archived Project ownership, replay after deletion without resurrection, revision/counter atomicity, null-last pagination and existing security/concurrency regressions pass.
- [x] Final diff contains no frontend changes, applied-migration edits, new dependency/framework, unapproved schema/architecture, implicit progress/status expansion or weakened security.

### API Validation

- [x] All five Milestone operations appear in `/v3/api-docs` with correct methods and 201/200 responses; DELETE returns 200 DeleteMilestoneResponse with required revision; no restore or separate transition API appears.
- [x] Request/response schemas, required/nullable fields, boolean defaults, title/date/revision constraints, filter defaults, ordering and CSRF/idempotency requirements match the approved contract.
- [x] Swagger visibly renders Milestone paths, operation labels and schema names in a real browser under the existing documentation access policy.
- [x] MockMvc + PostgreSQL separately validate actual runtime/ownership/security behavior; browser rendering is not used as a substitute.

## Completion

Completed on 2026-09-13 after all four sessions and Final Validation passed. No unresolved implementation or decision-level blocker remains. The completed-plan index now includes this Plan.

### Final validation evidence

- Java 21 full suite: `./gradlew.bat test --no-daemon --rerun-tasks`; 24 suites, 128 tests, zero failures/errors/skips, including 21 Milestone tests and existing User/AuthIdentity/Workspace concurrency regressions.
- Java 21 clean build: `./gradlew.bat clean build --no-daemon`; exit 0, BUILD SUCCESSFUL, all 9 tasks executed; 24 suites, 128 tests, zero failures/errors/skips. Both executable and plain JARs were produced under `build/libs/`.
- Clean-build XML reports, aggregate counts and PostgreSQL query-plan evidence are preserved outside cleanable build output at `.gradle/milestone-api-validation/clean-build/` (including `summary.json` and `query-plan.txt`). Standard browsable test report: `build/reports/tests/test/index.html`.
- Targeted MilestoneOpenApiTests and ProductionSwaggerSecurityTests passed; both also passed in the full suite and clean build. Assertions cover all five operations, exact DTO fields, nullable date format, completion defaults, revision bounds, required CSRF/idempotency headers, DELETE response/errors and protected production documentation.
- Real Chrome command: `node src/test/browser/milestone-api-validation.mjs .gradle/milestone-api-validation/browser`; exit 0, `MILESTONE_API_SWAGGER_DOCUMENTATION_PASS`. Anonymous rendering displayed all five method/path/operation labels and all five Milestone schema names. The saved screenshot was visually inspected. Evidence: `.gradle/milestone-api-validation/browser/result.json`, `swagger.html`, and `swagger.png`.
- Runtime MockMvc/PostgreSQL tests independently passed ownership/security, completion/reopening, nullable dates, archived Project relations, deletion/repeated 404, replay without resurrection, rollback/counter atomicity, repeated races, same-snapshot totals and null-last pagination. Swagger was used only for documentation validation.
- Compared existing files with the execution-start SHA-256 baseline. Existing changes made during this execution are the shared NoResourceFoundException-to-404 handler, this Plan and its completed index entry; additions are the Milestone feature, V8/V9 migrations, five test classes and browser harness. Existing V1-V7 migrations, frontend, security configuration, dependencies, environment configuration, shared guides and completed historical Plans were preserved. SQL whitespace guards complement Java validation.

### Execution recovery notes

- Session 1: the sibling-row comparison initially used JDBC java.sql.Date objects created before/after changing the JVM timezone. Their epoch representations differ despite the same stored DATE. Compare PostgreSQL row_to_json output for full-row immutability; domain LocalDate round-trip assertions remain unchanged. Revalidate all Session 1 tests after this fixture correction.

- Session 3: corrected an annotation enum spelling error in CreateMilestoneRequest found by compileJava; retained the required-field contract and reran the full Session 3 selection.

- Session 3: 82/83 regression tests passed; unsupported restore routing exposed the existing catch-all mapping Spring NoResourceFoundException to 500. Map this framework exception to the shared 404 response, keeping the route absent and security unchanged. Strengthen deletion rollback with an actual flush and in-transaction deleted-row/counter assertions before failure, followed by fresh-transaction rollback checks. Rerun the entire selection.
