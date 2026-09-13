# PLAN-0008: Link API

Status: `completed`

## Goal

Implement the workspace-owned Link resource using [backend-implementation-spec.md](../../backend-implementation-spec.md), especially sections 1, 2, 6 and 9, as the contract, supported by [backend-development-guide.md](../../backend-development-guide.md). Deliver CRUD, server-managed ordering, collection concurrency, durable creation retries, PostgreSQL persistence and verified runtime/OpenAPI behavior.

Approved by the user, including all explicit clarifications and the 500-Link / 8 MiB bounds. Sessions execute in order under the self-recovery policy.

## Scope and Initial Review

- Use `com.kopite.devspace.link` with presentation/application/domain/infrastructure layers, application-owned transactions, domain/JPA entities and purpose-specific DTOs. Reuse the existing Java 21, Spring Boot, PostgreSQL, Flyway, Jackson, validation, Spring Security and Testcontainers stack.
- Reviewed the plan guide/runtime template, relevant architecture, Spring, API, database, Flyway, security and domain/application guides, the current specification/development guide, and PLAN-0007's execution policy and completed implementation.
- Project, Task, Journal and Milestone provide owned repositories, workspace-first locking, strict write parsers, central errors, UTC audit timestamps and feature-local replay storage. Existing migrations are V1–V9; Link implementation and collection persistence are absent. Reserve V10/V11 subject to execution-start availability checks.
- Link has no Project relation. Do not copy child-resource Project validation, active-Project restrictions, scope derivation, cursor pagination or nullable-date behavior.
- The specification explicitly overrides generic pagination for this bounded resource: return the entire filtered owned collection. Scope unity/server includes common links with stored scope all. Scope all queries all three stored scopes.
- The current documents confirm `QuickLink.desc` versus `description`, local array order versus `position`, and the need to retain `collectionRevision`. No frontend source/package manifest, `backend-api-contract.md`, or referenced `frontend-contract-gap-analysis.md` was found here; this is document verification, not a claim of frontend source inspection.
- PLAN-0007 records 128 passing tests in the full suite and clean build. That is existing evidence, not validation performed while creating this Plan.
- Exclude frontend edits, import, dashboard integration, Project changes, link previews/network fetching, trash/restore, deployment execution and general infrastructure refactoring. Preserve the existing unrelated working tree, historical Plans and shared guides. No new dependency or framework is proposed.

## Approved API Contract

Paths use `/api/v1`, JSON UTF-8, the common `{code,message,fieldErrors,requestId}` errors and `Cache-Control: no-store` for personal responses.

| Method and path | Request | Success |
| --- | --- | --- |
| POST `/links` | label, url; optional description and scope; required Idempotency-Key and X-CSRF-Token | 201 `{item,collectionRevision}`; append to global owned order |
| GET `/links/{id}` | Canonical UUID | 200 full LinkResponse, without a collection wrapper |
| GET `/links` | scope=all/unity/server, query | 200 `{items,total,nextCursor:null,collectionRevision}`; full filtered collection |
| PATCH `/links/{id}` | Required resource revision plus supplied label/description/url/scope; required X-CSRF-Token | 200 `{item,collectionRevision}` |
| DELETE `/links/{id}?revision=...` | Required current resource revision and X-CSRF-Token | 200 `{deletedId,collectionRevision}`; permanent deletion |
| PUT `/links/order` | Required `{collectionRevision,ids}` and X-CSRF-Token | 200 full unfiltered `{items,total,nextCursor:null,collectionRevision}` |

No per-item PUT, position PATCH, partial reorder, Project filter, archive/restore or order-specific POST. The static `/order` route must resolve correctly alongside `/{id}`.

### Fields, DTOs and validation

Full LinkResponse contains exactly `id,revision,createdAt,updatedAt,label,description,url,scope,position`. All nine fields are always present and non-null. Never serialize entities or expose workspace/User identifiers.

Use CreateLinkRequest, UpdateLinkRequest, ReorderLinksRequest, LinkListRequest, LinkResponse, LinkMutationResponse (`item,collectionRevision`), DeleteLinkResponse (`deletedId,collectionRevision`) and LinkListResponse (`items,total,nextCursor,collectionRevision`). Reorder returns LinkListResponse. Detail deliberately returns only LinkResponse; clients requiring current collection state must read the list.

| Field | Rules |
| --- | --- |
| label | Required on create; trim, reject blank, maximum 100 UTF-16 code units |
| description | Optional on create, default empty string; maximum 300 UTF-16 code units; preserve content/whitespace; empty string clears |
| url | Required on create; trim, nonblank, maximum 2000 UTF-16 code units; parse as absolute http/https URL with host and no username/password |
| scope | Stored all/unity/server; create default all; independent of Projects |
| revision | Required PATCH body / DELETE query safe integer 1..9007199254740991 |
| position | Server-managed nonnegative safe integer, never accepted in writes |
| collectionRevision | Required only in reorder body, safe integer 0..9007199254740991; returned in list and mutation wrappers |
| ids | Required reorder array of canonical UUID strings; exact permutation of all live owned Link IDs; no null elements or duplicates |

- PATCH omission preserves a value; explicit null rejects for every writable Link field. Same-value and revision-only PATCH are successful mutations, consistent with existing resources.
- Strict write parsing rejects unknown/duplicate keys, wrong JSON types, string/fractional/unsafe revisions, malformed UUIDs, non-object request bodies and nulls. Reject `desc`, `position`, `projectId`, ownership selectors, audit fields and server IDs in create/update. Reorder accepts only collectionRevision and ids, not a resource revision or filters.
- URL validation must reject relative/protocol-relative URLs, non-http schemes, malformed authorities and user-info (including an empty user-info delimiter). Accept ordinary valid query strings/fragments and http/https without fetching, resolving DNS or probing reachability. Do not reuse Project's weaker credential validation unchanged. Preserve the trimmed URL rather than inventing a canonicalization policy.
- Required strings use Java trim/nonblank semantics and UTF-16 lengths; SQL checks remain complementary, not attempts to reproduce all Java whitespace/URL rules.
- Initialize resource revision 1 and both audit fields from one server UTC instant with existing microsecond precision. createdAt is immutable. Every successful resource PATCH increments revision and updates updatedAt.

### Full collection queries and bounds

- scope defaults all; unity includes unity + all, server includes server + all. Query defaults empty and performs case-insensitive literal substring matching across label, description and url, escaping SQL `%`, `_` and the escape character. Apply all filtering and workspace scoping in PostgreSQL.
- Order by position ASC, id ASC; `total == items.size()` and nextCursor is always null. There is no cursor, limit, search ranking or client sort parameter. Proposed clarification: reject unsupported query parameters rather than implying pagination or Project filtering occurred.
- Return filtered items and the **global workspace collection revision** from the same read-only REPEATABLE_READ snapshot. An empty filtered result can have a nonzero collectionRevision. Do not compute the revision from returned items or reset it when the last Link is deleted.
- Proposed capacity clarification, based on the guide's deployment proposal: at most 500 live Links per workspace, with creation admission controlled by `app.link.max-links` / `DEVSPACE_LINK_MAX_LINKS` (default 500, valid 1..500). Count globally under the collection lock; a new create beyond the configured threshold returns 429 QUOTA_EXCEEDED with a limit explanation. Valid creation replay is checked before quota enforcement and still succeeds at capacity.
- A lowered admission threshold does not hide existing Links or block editing/reordering/deleting them; the hard 500-record read bound remains. Raising the hard ceiling or importing more than it requires a separately reviewed contract change. All writers, including a later importer, must respect that invariant.
- Fix the compact UTF-8 successful response ceiling at 8 MiB for this contract, derived conservatively from 500 records, bounded strings (including worst-case JSON escaping), UUIDs, revisions and timestamp overhead. Contract tests must serialize maximum-size legal full collections and prove the ceiling; never truncate items to meet it. This is a bounded representation, not a new generic response-buffering layer. An inability to prove the bound is a contract-level issue to resolve before approval/execution continues.
- Document the count/response ceilings and environment setting during execution; add the safe default to both `.env` and `.env.example` without exposing existing secrets. General body limits/rate limiting and unrelated deployment controls remain outside this resource Plan.

## Approved Database Schema

### V10__create_link_collections_and_links.sql

| Table/column | Definition and purpose |
| --- | --- |
| link_collections.workspace_id | UUID primary key / FK workspaces(id), ON DELETE RESTRICT; one collection per workspace |
| link_collections.revision | BIGINT NOT NULL DEFAULT 0; CHECK 0..9007199254740991 |
| links.id | Server UUID primary key |
| links.workspace_id | UUID NOT NULL; FK link_collections(workspace_id), ON DELETE RESTRICT |
| links.label | TEXT NOT NULL; basic nonblank and character-length <=100 checks |
| links.description | TEXT NOT NULL DEFAULT ''; character-length <=300 |
| links.url | TEXT NOT NULL; basic nonblank and character-length <=2000 checks; full URL rules in Java |
| links.scope | TEXT NOT NULL DEFAULT 'all'; CHECK all/unity/server |
| links.position | BIGINT NOT NULL; CHECK 0..9007199254740991 |
| links.revision | BIGINT NOT NULL DEFAULT 1; CHECK 1..9007199254740991 |
| links.created_at, updated_at | TIMESTAMP WITH TIME ZONE NOT NULL |

- Use scalar workspace IDs; no Project FK, bidirectional collection graph, cascade deletion, soft-delete columns or tombstones.
- Enforce `UNIQUE(workspace_id,position) DEFERRABLE INITIALLY IMMEDIATE`. Its index supports the owned order scan; inspect generated SQL/plans before adding further indexes. Defer this named constraint only inside an atomic reorder transaction so swaps cannot fail on intermediate positions; flush and check it before returning success. Keep the persistence context consistent if using explicit SQL updates.
- Represent a never-written collection as empty/revision 0 on reads without inserting a row. Under the existing workspace write lock, lazily create the collection row for the first successful command. Failed commands roll this back; no GET business mutation or login-flow modification. The collection row persists after deleting all Links.
- No data backfill is necessary for existing empty collections. Prove a populated V9 upgrade preserves all previous business/replay rows and applied checksums. Recheck version availability before execution and use the next unused versions if necessary; never repair or edit applied migrations.

### V11__create_link_create_idempotency.sql

Follow the feature-local replay pattern: workspace FK, POST method check, canonical `/api/v1/links` path, key length 1..128, request hash, original response status 201, serialized original **mutation wrapper including collectionRevision**, created_at/expires_at and expiry index. Unique key `(workspace_id,method,path,key)`, with expires_at > created_at. Validate visible ASCII keys in the service.

Keep successful outcomes for 24 hours; use existing owned expiry cleanup patterns. No FK to the live Link and no deletion cascade from it. Replays survive editing, reordering and permanent deletion without resurrecting a resource.

## Ordering, Revisions and Transaction Strategy

- Resolve authenticated internal User to its workspace; check disabled accounts through the existing boundary. Lock workspace -> Link collection -> involved Links in deterministic UUID order. No Project locks are involved. Use database locks/transactions, not process-local synchronization.
- All successful create/PATCH/DELETE/reorder commands increment collectionRevision and workspace.dataRevision **exactly once each**, regardless of how many rows move. Public Workspace metadata revision/audits and other resource collections stay unchanged. Reads, conflicts, validation failures, rollback and creation replay increment neither counter.
- Proposed position clarification: initial Link position 0; creation appends at current maximum +1 (0 when empty). DELETE leaves gaps and does not change survivors' positions/revisions/audits. Reorder assigns dense positions 0..n-1 in submitted order. PATCH never changes position, including when scope changes. Position arithmetic overflow fails atomically with 409 REVISION_CONFLICT; never wrap or silently reorder others during create.
- Create initializes resource revision 1. PATCH checks the current resource revision and advances it once. DELETE checks the current revision, removes only that row and returns its ID plus the new collection revision; repeated DELETE/GET/PATCH after deletion returns 404 RESOURCE_NOT_FOUND. Deletion at maximum resource revision is valid provided the collection counter can advance.
- Create/PATCH/DELETE do **not** require a client collectionRevision. Disjoint item edits may both succeed sequentially despite changing the collection. Reorder requires collectionRevision rather than individual item revisions because every intervening item mutation invalidates its collection snapshot.
- Reorder compares the expected collectionRevision under locks, then validates the complete ID set, then applies all moves atomically. Proposed precedence: stale collectionRevision returns 409 REVISION_CONFLICT before set comparison; with a current revision, missing/duplicate/extra/foreign/nonexistent IDs return 400 VALIDATION_ERROR without disclosing foreign details. Structural/type validation still happens first. This ensures a create/delete that wins a race invalidates an old reorder as a collection conflict.
- Empty ids is valid only for an actually empty collection. Identity permutations and empty-on-empty reorder still increment collectionRevision/dataRevision once, because the specification says every successful reorder does so. They leave unchanged Links' revisions/audits untouched.
- Only Links whose numeric positions change during reorder increment their resource revision and updatedAt, once using the same operation timestamp. Unmoved rows retain both; createdAt never changes. Gaps being compacted can change positions even when relative order stays the same.
- Preflight all required counter/resource increments before mutation; collection/resource safe-integer overflow returns 409 REVISION_CONFLICT with no partial state. An unmoved Link at maximum revision does not prevent reorder; a moved one does. Workspace counter overflow must likewise leave everything rolled back under the existing workspace behavior.
- Two same-base reorders yield one success and one stale conflict. Create/delete/edit versus reorder are serialized: mutation-first invalidates reorder; reorder-first allows a subsequent create, and allows edit/delete only if that target's resource revision still matches (moved targets conflict). Verify both returned DTOs and final PostgreSQL positions/revisions/counters.

### Creation replay

- Authenticate and verify owned workspace before reading a stored outcome. Hash a fixed-order representation of raw supplied writable fields with explicit presence, distinguishing omitted description/scope from explicitly supplied defaults. JSON property order/formatting does not change the hash; raw text/presence changes do.
- Matching key/body returns the original 201 `{item,collectionRevision}`, including original position, audits and revision even after subsequent mutation/deletion. Different body returns 409 IDEMPOTENCY_KEY_REUSED. Do not substitute the latest collectionRevision in a historic replay or create the Link again.
- Store Link, collection revision, workspace counter and replay outcome in one transaction. Concurrent identical creates produce one row and one outcome; losing conflicting-body requests do not append a second row. Expired-key handling follows existing 24-hour semantics.
- Clients must not regress cached collectionRevision when a delayed response or historical replay arrives; refresh the list when needed. Replay is proof of the original create outcome, not a current collection snapshot.

## Authorization and OpenAPI/Swagger Policy

- Apply authenticated workspace scoping to all reads, searches, counts, mutations, replay and reorder membership. Individual missing/foreign IDs return the same 404 before business revision disclosure. Scope all means common links in the **same workspace**, never public/shared-across-user data.
- Preserve shared session security: anonymous application requests 401, authenticated missing/invalid CSRF 403, invalid Origin 403, disabled account handling unchanged and valid authenticated requests reach normal endpoint processing. POST/PATCH/DELETE/PUT order all require CSRF and Origin protection.
- OpenAPI documents all six operations, session-cookie security, required CSRF on mutations, required Idempotency-Key only on create, safe integer ranges, query defaults, URL constraints, exact wrappers, nextCursor always null and full-collection ceilings. Reorder body uses collectionRevision, not revision; DELETE uses resource query revision. Document common 400/401/403/404/409/500 errors as applicable and create quota 429.
- Keep existing anonymous documentation access only in dev/development/local/test; default/production remain protected and prod/production overrides development profiles. Do not change application security to simplify Swagger.
- Use a documentation-only Chrome harness with visible method/path/operation labels, schema names and accessible controls. Avoid internal Swagger CSS classes or JS state. Browser checks rendering, not authenticated CRUD/security failure workflows; MockMvc + PostgreSQL tests verify runtime behavior separately.

## Verified Frontend/Backend Differences

| Document evidence | Later frontend integration |
| --- | --- |
| Spec section 9 maps QuickLink.desc to description | Add an explicit adapter; do not send desc or accept it as a second backend field |
| Spec sections 6/9 replace local array order with position | Render server order, append using server responses, submit the full owned ID permutation for reorder; never PATCH position |
| Spec section 6 and guide section 4 require collectionRevision | Store it separately from each Link revision, update from mutation/list wrappers, reject stale ordering drafts and avoid replacing newer cache state with an older replay |
| Link lists return full filtered items with nextCursor null | Do not use generic cursor/limit loaders, truncate to widget display counts or interpret a filtered subset as the whole workspace collection |
| Scope-specific queries include common scope all links | Retain common links in unity/server views; clear scope/query and fetch a current complete list before ordering |
| Create/PATCH wrap item, DELETE wraps deletedId, detail is unwrapped | Decode operation-specific DTOs and reconcile caches after confirmed success |
| General revision/idempotency rules replace synchronous boolean saves | Use server UUID/revision/audits, pending/error handling and stable creation retry keys; keep drafts on 409; refresh after ambiguous edit/delete responses |
| Permanent deletion and server URL validation | No trash/restore; normalize and validate editor values, show URL/credential errors; do not rely only on frontend validation |

Actual frontend-source verification remains for integration. No frontend change is part of this Plan.

## Clarifications and Unresolved Decisions

- The user approved all explicit choices in this Plan: zero-based/gapped positions after deletion; dense reorder with deferred uniqueness; lazy empty collection revision 0; successful no-op reorder increments only collection/workspace counters; stale-before-membership conflict precedence; strict unsupported query rejection; presence-sensitive creation hashes; and the 500-Link / 8 MiB bound with configurable creation admission.
- The guide's 500-Link value is an operational proposal, not an already-finalized product limit. The user approved it as the concrete limit for this implementation. Any future ceiling change requires revising the bound and tests.
- No contradiction in the current Link endpoint/field contract was found. No new framework or long-term architectural guidance is required. Frontend/import/deployment-wide limits remain outside scope; this Plan does not claim deployment readiness.

## Execution Policy: Recover Within the Approved Contract

After explicit approval, change status to `active` and execute sessions in order. Complete and revalidate each session before advancing.

- Diagnose, correct and revalidate recoverable implementation, compilation/type inference, transaction, fixture/isolation/synchronization, OpenAPI and browser readiness/selector failures within the approved contract; then continue without requesting approval again.
- Preserve root-cause/failure evidence; check items only after success. Do not weaken assertions, skip required tests, use raw types/unchecked casts, relax security or change contract semantics to obtain a pass.
- Use controlled synchronization, independent transactions and final-state assertions for races. Repeat affected concurrency cases and include them in the full suite; a lucky rerun is not a fix. Unique-conflict recovery must fully roll back a losing transaction before looking up a winner in a separate transaction.
- Stop only for genuine decision-level blockers: contradictory approved requirements, required unapproved architecture/schema changes, security/data-integrity compromises, unavailable external prerequisites after reasonable authorized recovery, or a root cause that cannot be resolved reliably within the approved design.
- If blocked, keep `active`, leave incomplete validations unchecked and record reproduction, diagnosis, attempted fixes, affected checks and the decision/external prerequisite needed. Do not modify shared guides or completed Plans to adopt this policy.

## Execution Sessions

All four approved sessions executed in order. Checked items have passed implementation and validation.

### Session 1: Owned persistence and collection invariants

Objective: Establish Link/collection domain models and safe ordering persistence.

Implementation:

- [x] Add Link and LinkCollection domain/JPA models, owned repositories and V10 migration with deferred position uniqueness.
- [x] Implement field/URL validation, individual/collection revision bounds, append/gap/dense ordering rules and lazy initialization under workspace-first locking.

Validation:

- [x] Fresh PostgreSQL Testcontainers migration/Hibernate validation and populated V9 upgrade preserve previous rows and V1–V9 checksums; no repair or destructive reset.
- [x] Verify FK/required/scope/position/revision/basic text constraints, per-workspace uniqueness, swap flush/commit behavior and rollback of a duplicate final position.
- [x] Test Java UTF-16 boundaries, trim/nonblank, description preservation, URL schemes/authorities/credentials, audit immutability and arithmetic overflow.
- [x] Prove empty reads do not persist a collection or change counters, first writes create exactly one collection, and deleting the last Link retains its revision history. Compile and pass persistence/domain tests.

Session 1 evidence: LinkPersistenceTests passed 4 tests, zero failures/errors/skips; fresh PostgreSQL migration, populated V9 upgrade and deferred-constraint rollback verified.

### Session 2: Atomic CRUD, replay, quota and reorder

Objective: Implement application transactions and collection/resource concurrency.

Implementation:

- [x] Add V11 replay storage, owned create/update/delete/reorder commands, collection locks, safe position updates and exactly-once counter changes.
- [x] Add bounded admission configuration, safe `.env`/`.env.example` defaults and durable original-wrapper replay behavior.

Validation:

- [x] Two-user ownership and quota tests; deletion/repeated 404; no-op PATCH/reorder and empty order; missing/duplicate/extra/foreign order IDs; stale precedence; exact final positions and untouched siblings/other workspaces.
- [x] Test response revisions/audits for moved versus unmoved rows, gaps/compaction, scope edits without moves, overflow rollback, and independent resource versus collection revisions.
- [x] Deterministically race two initial creates, same/different-key creates, competing edits/deletes/reorders, reorder versus create/edit/delete and last-slot quota claims. Repeat concurrency cases; assert final IDs, unique positions, row counts and exact resource/collection/workspace counters in fresh transactions.
- [x] Verify replay after edit/reorder/delete, no resurrection, historic collectionRevision, default/presence hashing, JSON-order equivalence, expiry and workspace key isolation; quota does not reject valid replay.
- [x] Inject failure after flushed position changes and after replay persistence; preserve evidence of the injected cause through Spring translation. In fresh transactions assert complete row/position/revision/audit/counter rollback and no successful partial replay.
- [x] Run all Link tests plus existing User/AuthIdentity/Workspace creation regressions; verify same User/Workspace IDs and exactly one persisted aggregate per tested identity.

Session 2 evidence: 48 selected tests passed, zero failures/errors/skips; repeated races and fresh-transaction rollback verified.

### Session 3: REST, full snapshot queries and security

Objective: Expose exact DTOs and secure, bounded full-collection behavior.

Implementation:

- [x] Add strict DTO parsers/validation, six routes, common errors and response wrappers; no cursor machinery or Project relations.
- [x] Implement owned scope/common-link and literal label/description/url search, deterministic ordering and same-snapshot collectionRevision.

Validation:

- [x] MockMvc + PostgreSQL verify exact fields/wrappers/methods/statuses, defaults, unknown/null/duplicate/type rejection, unsupported query rejection, server-managed position and correct /order routing.
- [x] Test scope combinations, escaped literal searches, empty/new/filtered collections, full totals/nextCursor null, and complete unfiltered reorder output. Deterministically commit a concurrent mutation between query reads and prove snapshot consistency.
- [x] Verify anonymous 401, missing/invalid CSRF 403, invalid Origin 403 and valid authenticated handling for every mutation; disabled accounts, cross-user detail/search/order isolation and no-store responses.
- [x] Verify 500-record maximum legal collections and worst-case escaped UTF-8 serialized response stay within 8 MiB; configured admission/quota races, lowered threshold behavior and no silent truncation. Inspect owned SQL/query counts and representative index plans.
- [x] Run Link and existing Project/Task/Journal/Milestone/security regressions; recover and revalidate in-contract failures before advancing.

Session 3 evidence: 101 selected regression tests passed. Added snapshot/query-count and strengthened exact-size fixture checks then passed all 7 LinkApiTests/LinkQueryTests, zero failures/errors/skips. PostgreSQL plan: build/reports/link-api/query-plan.txt.

### Session 4: OpenAPI, real browser and final regression

Objective: Validate documentation and finish a reproducible build.

Implementation:

- [x] Add exact OpenAPI assertions for six operations, nine Link fields, wrappers, required resource/collection revisions, ids, URLs, position, filters, ceilings, quota error and security/idempotency headers.
- [x] Add documentation-only browser harness using stable visible paths/labels/schema names and semantic controls.

Validation:

- [x] Anonymous development/test `/v3/api-docs` matches the approved contract, including nextCursor always null, unwrapped detail, no cursor/limit parameters and required CSRF on PUT order.
- [x] Real Chrome displays all Link operations/schemas without login; save result/HTML/screenshot and visually inspect it. Default/production documentation security tests still pass.
- [x] Java 21 `./gradlew.bat test --no-daemon --rerun-tasks` passes the full suite, including PostgreSQL and repeated concurrency tests, with no failed/skipped required tests.
- [x] Java 21 `./gradlew.bat clean build --no-daemon` passes with tests executed; inspect reports/artifacts and preserve browser/OpenAPI/test/SQL evidence outside cleanable build output.

## Final Validation

- [x] All sessions and required checks pass; no unresolved implementation or decision-level blocker remains.
- [x] Full suite and clean build execute actual PostgreSQL Testcontainers tests; record counts, commands and zero failures/errors/skips.
- [x] Persistence/upgrade, ownership, URL validation, full filtered snapshots/bounds, atomic order permutations, revisions, quota, replay and fresh-transaction rollback/concurrency checks pass.
- [x] Final change review confirms no frontend/applied-migration/historical-Plan/shared-guide edits, new dependency, unrelated security change or unapproved architecture; any new environment variable is documented with safe local/example defaults.

### API Validation

- [x] All six operations and correct 201/200/error statuses appear in `/v3/api-docs`.
- [x] Exact DTOs, validation constraints, required resource versus collection revisions, server position, full-return defaults/bounds and required CSRF/idempotency headers match the approved contract.
- [x] Swagger renders paths/operation labels/schema names in a real browser under existing access policy.
- [x] MockMvc + PostgreSQL independently validate runtime/security; browser rendering is not a substitute.

## Completion

Completed on 2026-09-13 after all four sessions and Final Validation passed. No unresolved implementation or decision-level blocker remains. The completed-plan index now includes this Plan.

### Final validation evidence

- Java 21 `./gradlew.bat test --no-daemon --rerun-tasks`: BUILD SUCCESSFUL, exit 0, all 5 tasks executed; 29 suites / 150 tests, zero failures/errors/skips. Includes 22 Link tests and all existing User/AuthIdentity/Workspace and resource/security regressions. Final run includes the OpenAPI empty-string default correction and added independent-create/disjoint-edit/overflow coverage.
- Java 21 `./gradlew.bat clean build --no-daemon`: BUILD SUCCESSFUL in 2m 14s, exit 0, all 9 tasks executed; 29 suites / 150 tests, zero failures/errors/skips. Both executable and plain JARs produced in `build/libs/`.
- Full-suite XML reports, OpenAPI JSON, SQL plan and counts are preserved at `.gradle/link-api-validation/full-suite/`; corresponding clean-build evidence is at `.gradle/link-api-validation/clean-build/`, including `summary.json`. Standard browsable report: `build/reports/tests/test/index.html`.
- OpenAPI checks passed for all six operations, exact fields/wrappers, required revisions/CSRF/idempotency, URL/text constraints, default empty description/all scope, position metadata, 500-item bound, always-null nextCursor and quota errors. Production/default documentation security regressions passed. The latest generated contract is preserved with clean-build evidence.
- Real Chrome command: `node src/test/browser/link-api-validation.mjs .gradle/link-api-validation/browser`; exit 0, `LINK_API_SWAGGER_DOCUMENTATION_PASS`. Anonymous rendering displayed six method/path/operation labels and seven Link schema names; required CSRF and empty description default were checked. Result, HTML and screenshot are saved as `result.json`, `swagger.html` and `swagger.png` in that directory. The screenshot was visually inspected. Validation-only Chrome/test server processes were stopped afterward.
- PostgreSQL tests verified V9 upgrade preservation/checksums, lazy collection creation, deferred uniqueness/swaps, same-snapshot full lists including concurrent first creation, ownership, URL rules, quota admission, exact field-length boundaries, full 500-item responses below 8 MiB, original replay after deletion, revision/counter overflow and failure-injection rollback. Controlled races tested both serial outcomes and repeated same-key/reorder/edit/delete/quota races with final persisted-state checks.
- Compared existing files against the execution-start SHA-256 baseline. Changes are this Plan/index, Link feature files/tests/browser harness, V10/V11, Link error mappings and the admission setting in application.yml/.env/.env.example. Existing applied migrations, frontend, security configuration, dependencies, shared guides and historical Plans were preserved. `DEVSPACE_LINK_MAX_LINKS=500` is present once in both local/example env files; no secrets were printed or added to documentation.

### Execution recovery notes

- Session 3: the strengthened maximum URL fixture accidentally used 2001 characters because the URL prefix length was miscounted. Compute the suffix from `2000 - prefix.length()` and assert domain-valid exact field lengths before inserting. Keep production limits and DB checks unchanged; rerun LinkApiTests and LinkQueryTests.

- Session 4: final OpenAPI review found that springdoc omits an empty annotation default. Follow the existing typed Project customizer pattern to explicitly document CreateLinkRequest.description default empty string; add a direct assertion and rerun OpenAPI/full validation. Add final coverage for independent initial creates/disjoint edits and workspace-counter overflow rollback.

- Session 4 browser readiness: one invocation started before the test server was ready; it subsequently recovered and passed. Add an explicit HTTP readiness wait before Chrome navigation to avoid relying on that timing, then rerun successfully. No API/security changes.
