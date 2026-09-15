# PLAN-0010: ProjectCategory

Status: `completed`

Approved for execution by the user on 2026-09-14. Sessions execute in order under the self-recovery policy below.

## Goal

Implement the accepted [DECISION-0001: ProjectCategory](../decisions/DECISION-0001-project-category.md): workspace-owned Category management and a nullable, UUID-only Project relation, preserving existing scope contracts and historical Project creation retries.

The user approved execution on 2026-09-14. All six sessions were implemented and validated in order under the policy below. The accepted Decision remains authoritative and unchanged; no production deployment or live business-data mutation was performed.

## Scope and Initial Review

- Reviewed the accepted Decision, plan guide/runtime template, recent PLAN-0008/0009 self-recovery policy, and the completed source/schema/API/test impact investigation. Applicable architecture, Spring, API, database, Flyway, security and implementation guides were inspected during the investigation; consult affected guides again if they change before execution.
- Current migrations end at V12. Provisionally reserve V13 for Category storage/Project relation and V14 for Category creation replay storage; recheck availability before writing migrations. Never edit V1–V12 or repair their checksums.
- Use the existing Java 21, Spring Boot, JPA/PostgreSQL, Flyway, Jackson, Spring Security, validation, springdoc and Testcontainers stack. Keep Category responsibilities feature-local with inward dependencies; no new dependency, database extension, global abstraction or infrastructure is needed.
- Affected Project code includes `Project`, `ProjectValues`, create/update commands and DTOs, strict field parsing, `ProjectCommandService`, `ProjectSnapshot`, response mapping, `ProjectRequestHash`, and the creation replay port/adapter. Preserve query/search predicates and cursor codec semantics. Add Category domain, owned repositories, commands/queries, DTOs/controllers and focused common-error mappings.
- Existing `PersonalWorkspace` ownership locks and `recordBusinessMutation` provide transaction/counter conventions. Keep workspace creation/login unchanged: no Category provisioning or fallback assignment.
- Historical investigation observed two unexpired Project replay rows in local PostgreSQL. Do not assume these rows are still unexpired or that this database represents production. Required compatibility tests must create independent, deterministic V12-era fixtures with controlled expiry.
- Existing working-tree changes are the accepted Decision and its index entry; preserve them. Implementation follows the approved scope below. The completed-only plan index is updated after successful final validation.
- Exclude frontend changes, Category filters/search in Project lists, Dashboard Category configuration, scope replacement/inference, starter Categories, mandatory assignment, manual ordering, Category archive/restore and automatic reassignment/clearing on deletion. Do not update shared guides or historical Plans.

## Accepted Contract to Deliver

### Schema, identity, names and collection

- `ProjectCategory`: server UUID `id`, immutable `workspaceId`, normalized `name`, explicit revision 1–9007199254740991, UTC/microsecond `createdAt` and `updatedAt`; no scope, archive state, position or default flag.
- `project_categories`: UUID primary key, restrictive workspace FK, unique `(workspace_id,id)`, workspace-local normalized-name uniqueness with deterministic PostgreSQL `C` collation, and complementary basic integrity checks. Java validation retains ownership of malformed-Unicode, normalization and UTF-16 semantics.
- `projects.category_id`: nullable UUID with `(workspace_id,category_id)` FK to `project_categories(workspace_id,id) ON DELETE RESTRICT`, plus the reference-check index. Existing Project keys, name-duplication semantics and all old fields remain intact.
- Name rules are exactly the Decision's Java `trim()` then NFC, nonblank `isBlank()`, valid Unicode and maximum 100 normalized UTF-16 code units. Preserve internal whitespace and case; no locale/accent/compatibility folding. Another Category with the same normalized name gives `409 CATEGORY_NAME_CONFLICT` and `fieldErrors.name`. Own-name rename succeeds; name reuse after deletion gets a new UUID.
- Full collection ordered `createdAt ASC,id ASC`, `total == items.length`, no pagination/search/manual order. Admission limit is 100 Categories per workspace; new creation at capacity gives `429 QUOTA_EXCEEDED`. Do not truncate reads or prevent replay/rename/delete at capacity. No collection revision is introduced.

### Category HTTP contract

All success bodies are JSON/no-store; retain session ownership, account checks, CSRF/Origin enforcement and the common error envelope. Category response is exactly `{id,name,revision,createdAt,updatedAt}`, without workspace identity/dataRevision. UUID input accepts standard hyphenation and hexadecimal case; response UUIDs are canonical lowercase.

| Method/path | Required input | Success |
| --- | --- | --- |
| GET `/api/v1/project-categories` | No query parameters | 200 `{items:[CategoryResponse...],total}` |
| GET `/api/v1/project-categories/{id}` | UUID path | 200 CategoryResponse |
| POST `/api/v1/project-categories` | `Idempotency-Key`, `{name}` | 201 CategoryResponse, including historical replay |
| PATCH `/api/v1/project-categories/{id}` | `{revision,name}` | 200 CategoryResponse |
| DELETE `/api/v1/project-categories/{id}` | Query `revision`, no body | 200 `{deletedId}` |

- Require existing `X-CSRF-Token` protection for mutations. Reject unknown/repeated JSON fields and Category query parameters, invalid types/nulls, ownership/audit injection and invalid UUID/revision/name inputs. Category revision-only PATCH is invalid; same-name PATCH succeeds.
- Preserve `400 VALIDATION_ERROR`, `401 AUTH_REQUIRED`, existing `403 ACCOUNT_DISABLED/CSRF_INVALID`, `404 RESOURCE_NOT_FOUND`, `409 REVISION_CONFLICT/IDEMPOTENCY_KEY_REUSED` and `500 INTERNAL_ERROR` envelope semantics. Add name/in-use/quota errors from the Decision without SQL/constraint disclosure.
- Resolve missing/foreign resources identically. For rename/delete, owned existence precedes revision, then business checks. Any active or archived Project reference yields `409 CATEGORY_IN_USE` with empty field errors. Back application checks with the specific FK; never cascade, clear or reassign. Repeat delete gives 404.

### Project relation, revisions and compatibility

| `categoryId` input | Project create | Project PATCH |
| --- | --- | --- |
| Omitted | Uncategorized | Preserve |
| Explicit null | Uncategorized | Clear |
| UUID | Assign owned Category | Assign/reassign owned Category |

- Retain presence independently from parsed UUID. Reject explicit null for other currently non-nullable fields, as today. Missing/foreign supplied Category gives 404 for a new mutation. Archived Projects support Category editing; archive/unarchive preserves the relation. Category never changes scope.
- Normal detail/list/PATCH and all newly stored creation responses include required nullable `categoryId` only. No Category name/revision is embedded or copied. Pre-deployment Project creation replay retains its original response shape without this field.
- Category create/delete increments workspace dataRevision once. Category rename, including same normalized name, also increments Category revision once and assigns operation time to updatedAt. Project Category changes/clear/no-op follow one normal Project PATCH revision/timestamp/dataRevision increment, including multi-field PATCH. No JPA `@Version`.
- Preserve workspace metadata revision/updatedAt and unrelated resource revisions. Reads, replays and failures do not advance counters. Capacity overflow of a required counter is `REVISION_CONFLICT`; deleting a Category at its maximum revision is allowed if workspace dataRevision can advance.
- Commands lock workspace first before loading mutable counter state; when both resource types are needed, Project precedes Category, with consistent ID ordering. Assignment/delete share this serialization boundary. If assignment wins, deletion conflicts in-use; if deletion wins, a new assignment gets 404. Valid historical replay is independent of current Category existence.

### Fingerprints and historical response preservation

- Category-omitted Project creates keep version 1 byte-for-byte: same version integer, seven-field order, raw command value conversion and length-prefixed UTF-8/null framing. Do not append an omission marker or normalize existing hash inputs.
- Category-present creates use version 2 and the same seven fields followed by explicit-null marker or raw supplied valid UUID string. Preserve UUID hexadecimal case in the fingerprint, canonicalizing only persisted identity. Omitted/null and different UUID spellings/values are distinct intents. Category names never enter Project hashes.
- Request fingerprint version and response shape are independent: new omitted creates use v1 but store/return `categoryId:null`; old replay rows remain legacy-shaped. Do not infer response shape solely from fingerprint version.
- Carry legacy snapshot field presence through storage, application result and HTTP serialization. Preserve original 201 response values/shape and existing exact-response assertions; do not inject null, enrich from current entities, rewrite hashes/snapshots or discard unexpired rows.
- Authenticate, resolve workspace and validate static request syntax before checking replay; resolve matching unexpired replay before live Category lookup. Different fingerprints conflict. New/expired creations validate current Category ownership and business constraints. Preserve old readers for retained expired records encountered before cleanup, as well as unexpired records.
- Both resources retain 24-hour persisted replay with 1–128 visible ASCII keys, namespace by workspace/method/path, no expiry extension on retry and no mutation on replay. Category creation hashes raw supplied name in a versioned length-prefixed format; name normalization-equivalent input is a distinct intent. Replay precedes live name/quota checks and survives Category rename/delete without recreating it.
- Category PATCH/DELETE remain revision-based, without creation replay. Old application instances unable to read new snapshots must be drained before enabling the new contract. Later implementation delivers a concrete compatibility runbook and isolated validation; this Plan does not authorize production deployment or deletion of live replay data.

## Execution Policy: Recover Within the Approved Contract

After explicit approval, change status to `active` and execute sessions in order. Complete and revalidate each session before advancing. Check an item only after the corresponding work succeeds; update this Plan with evidence during execution.

- Diagnose, correct and revalidate recoverable implementation, compilation/type inference, transaction, fixture/isolation/synchronization, migration-test setup, OpenAPI and browser readiness/selector failures within the accepted contract, then continue without requesting approval again.
- Preserve root-cause/failure evidence. Do not weaken assertions, skip required tests, use raw types/unchecked casts, relax security, rewrite applied migrations, invalidate replay rows or change contract semantics to obtain a pass.
- Use controlled synchronization, independent database transactions/connections and final-state assertions for races. Repeat affected concurrency cases and include them in the full suite; a lucky rerun is not a fix. A unique-constraint failure must fully roll back its transaction before any separate-transaction winner lookup; do not continue using an aborted PostgreSQL transaction.
- Stop only for genuine decision-level blockers: contradictory approved requirements, required unapproved architecture/schema changes, security/data-integrity compromises, unavailable external prerequisites after reasonable authorized recovery, or a root cause that cannot be resolved reliably within the accepted design.
- If blocked, keep `active`, leave incomplete checks unchecked and record reproduction, diagnosis, attempted fixes, affected validations and the specific decision/external prerequisite needed. Preserve unrelated work; do not change shared guides or completed Plans to adopt this policy.

## Execution Sessions

Execute these six sessions in order. No item below has been implemented or validated during Plan creation.

### Session 1: Category domain, ownership schema and populated upgrade

Objective: Establish persistence and nullable identity without changing existing data or behavior.

Implementation:

- [x] Recheck accepted Decision, relevant guides, source baseline and migration availability; record existing changes and fixed pre-change Project hash/snapshot fixtures before modifying their producers.
- [x] Add Category domain/value validation and owned repository boundaries; add nullable Project identity mapping without Category eager loading or implicit scope/name coupling.
- [x] Add provisionally V13 `create_project_categories` with table, name/ownership constraints, nullable Project relation and reference index; add V14 `create_project_category_create_idempotency` with workspace/method/path/key identity, original 201 snapshot/hash storage and expiry integrity/index following current replay conventions.
- [x] Preserve workspace creation, V1–V12, existing Project keys and all old columns/counters. Add no starter data or assignments.

Validation:

- [x] PostgreSQL Testcontainers proves fresh migration, JPA `ddl-auto=validate`, flush/clear round trip, composite ownership FK, restrictive reference deletion and deterministic name uniqueness. Test valid null relations and duplicate Project names separately from Category uniqueness.
- [x] Upgrade an isolated populated V12 schema containing multiple owners, both Project scopes/statuses, Task/Journal/Milestone/Link/Dashboard records, noninitial revisions/counters and old Project replay rows with controlled expired/unexpired timestamps. Compare old column values, dependent data, replay bodies/hashes/expiry and V1–V12 checksums before/after; only new nullable columns/tables appear, and Category collections remain empty.
- [x] Test trim/NFC, case distinctions, Unicode canonical equivalence, malformed surrogate input, normalized UTF-16 bounds, blank names and preserved internal/boundary whitespace rules. Assert read/login/workspace reuse never creates Categories or changes counters.

Session 1 evidence: CategoryPersistenceTests passed 3 tests on PostgreSQL 16.4, including populated V12 -> V14 upgrade and preserved replay/data/checksum comparisons. Golden baseline and migration evidence are in `.gradle/project-category-validation/`. Gradle sandbox download denial recovered through approved execution with Java 21; no contract change.

### Session 2: Project replay compatibility boundary

Objective: Preserve existing creation retries before Category-bearing requests are exposed.

Implementation:

- [x] Introduce presence-aware Project creation intent and v1/v2 fingerprint branches without losing the raw UUID string or changing old seven-field semantics; extend minimal snapshots/replay representations and call sites so code remains compilable.
- [x] Preserve old/new snapshot shape through replay port/adapter, application result and POST serialization. Normal/new response mapping includes nullable identity; legacy POST replay omits it and does not query current Project/Category data.
- [x] Preserve authentication/static-validation precedence, TTL/namespace/cleanup behavior and atomicity; prepare live Category validation to occur only for a genuinely new creation. Keep response shape independent of hash version.

Validation:

- [x] Assert fixed pre-change golden v1 hashes rather than generating expected values with the modified producer. Cover raw whitespace/numeric conversion/optional-field omission and JSON property-order stability. Verify v2 omitted-versus-null, UUID value/case changes and absence of name participation.
- [x] Read literal pre-change serialized snapshots and actual V12-upgraded replay rows through the new application and HTTP POST path. Assert original response string/shape, 201, original revision/audits, absent categoryId, unchanged counters/expiry and no resurrection after administrative Project removal.
- [x] Verify new v1 omitted creates store/return explicit null, v2 null/UUID snapshots retain their shape, old expired records remain readable until normal cleanup, restart/fresh-service retries work, and the exact 24-hour boundary permits a new creation.
- [x] Run existing Project replay/command/API tests with targeted new compatibility cases. Keep currently non-nullable request validation and old same-key conflict behavior intact.

Session 2 evidence: 15 fingerprint/legacy replay/Project command/API tests passed. Literal old response is returned byte-for-byte through HTTP; new omitted snapshots include null. Test-only CSRF field/Origin assumptions were corrected to the actual contract/configuration; failure XML retained under recovery.

### Session 3: Category application services, idempotency and restrictive deletion

Objective: Deliver atomic owned Category CRUD with deterministic reference reads.

Implementation:

- [x] Implement read/full-list and create/rename/delete services with workspace-first locking, account/ownership resolution, deterministic ordering, cap enforcement, explicit revisions and counter-capacity checks.
- [x] Add persisted Category creation replay and raw-name fingerprinting; replay before live uniqueness/quota checks, independent of current Category existence and Project key namespace.
- [x] Implement active-plus-archived usage checks, specific FK/unique error translation and atomic category/replay/dataRevision writes; no automatic detachment or reassignment.

Validation:

- [x] Test exact create/rename/delete transitions, same-name rename, revision-only rejection at the applicable boundary, own-name versus another-name collision, name reuse with a new ID, deleted-resource 404 and ownership-before-revision precedence.
- [x] Verify 0/100/full ordered collections with tied timestamps, no truncation of a deliberately over-cap database fixture, no writes on reads, 101st new-create quota error, and successful replay/rename/delete at capacity. Verify list consistency under concurrent mutation.
- [x] Verify active and archived Project references both block deletion and FK enforcement cannot silently clear/cascade. Test rename at maximum revision, delete at maximum revision, workspace overflow, failed flush/replay storage rollback and unchanged metadata/unrelated revisions in fresh transactions.
- [x] Test Category replay after rename/delete, raw normalized-equivalent input conflict, same key in another workspace/resource namespace, expired key reuse and no replay resurrection/counter changes. Repeat controlled same-key, different-body, duplicate-name and quota-boundary races.

Session 3 evidence: Category command/persistence suites passed, including three repetitions of same-key/different-body/duplicate-name/quota races and concurrent rename/read snapshot. Injected Repository failure assertion now verifies Spring exception translation and the original cause; rollback/counter assertions retained.

### Session 4: Project relation integration and strict REST contracts

Objective: Expose Category CRUD and Project assignment with exact presence/security semantics.

Implementation:

- [x] Add Category controllers, purpose-specific request/response DTOs, strict parsers and minimal common error mappings, following the accepted methods/status/headers and query allowlist.
- [x] Extend Project create/update DTOs/commands/values and snapshots with omitted/null/raw-UUID handling; validate owned live Category only for new mutations, preserve archived editing and one revision increment per successful PATCH.
- [x] Return UUID-only nullable Category identity from all normal Project reads/list/PATCH and new creates; retain legacy POST replay path. Keep query predicates, order, cursor codec, scope/color and existing field rules unchanged.

Validation:

- [x] MockMvc plus PostgreSQL verifies the full Category contract, no-store, strict unknown/repeated/null/type/query matrices, UUID casing and normalized names; error codes/envelope/field errors match the Decision.
- [x] Verify Project POST/PATCH omitted/null/UUID matrix, foreign/missing Category parity, reassignment/clear, same-value/already-null and combined-field PATCH, stale revisions, and archived Category editing/archive-unarchive preservation. Check other explicit nulls still fail.
- [x] Verify all normal Project response paths include categoryId even when null, historical replay alone retains omission, Category names never appear, and later rename leaves Project response/revision unchanged while Category reads are fresh.
- [x] Verify anonymous/disabled sessions, CSRF/Origin rejection and two-user ownership injection without writes; do not relax documentation or API security. Run Project/Category/authentication suites before advancing.

Session 4 evidence: 38 Category/Project/legacy replay/security tests passed. Existing logout regression initially failed because the local APP_ORIGIN differs from its fixed localhost:8080 fixture; rerun with that test-process Origin passed without changing security code or assertions.

### Session 5: Cross-feature concurrency, query behavior and regression

Objective: Prove Category introduction does not alter scope-derived behavior or drop uncategorized resources.

Implementation:

- [x] Add controlled assignment/delete race cases for new Project creation and PATCH, and regression fixtures containing uncategorized/categorized active/archived Projects across both scopes and workspaces.
- [x] Add replay-after-reassignment/clear/Category-deletion scenarios, including HTTP verification of original Category IDs and legacy bodies; retain current-state validation on genuinely new requests.
- [x] Extend affected existing tests only for new contract coverage or necessary constructor/fixture adaptations. Preserve assertions for old scope/ownership/revision behavior. Inspect actual SQL/query counts and the reference-check index plan.

Validation:

- [x] Force both assignment/delete serial outcomes with independent transactions and explicit synchronization: assignment wins then delete is CATEGORY_IN_USE; deletion wins then new assignment is 404. Verify active/archived cases, no dangling FK, exact Project/Category/replay/counter state, and conflict/replay precedence. Repeat affected race cases deterministically.
- [x] Verify Project filters/defaults, name/stack literal search, duplicate Project names, createdAt/id keyset order, cursor binding and existing tokens remain valid after Category operations. Category name does not become a Project search key.
- [x] Verify Overview active-only total/separate archived counts, undeleted Task stats across archived Projects, scope intersection and consistent read snapshot; Category changes alone leave all aggregates unchanged.
- [x] Verify Task/Journal/Milestone live projectName/scope and historical replay behavior, unchanged own revisions, Task/Journal archived-create/reassignment rejection and Milestone archived-create/reassignment acceptance.
- [x] Verify Dashboard owned/archived Project validation and projectId-to-scope-all normalization, unchanged stored widget shape/revision; Link independent scope and common/all inclusion remain unchanged.
- [x] Measure SQL/query count with increasing Project/related-resource counts: no per-row Category loads or eager association, no inner join excluding null Category, no full entity loading for aggregate/reference counts. Confirm Category list cost remains bounded and read-only; do not add speculative indexes.

Session 5 evidence: expanded regression ran 132 tests; after fixing the fixture schema leak only an outdated Dashboard migration checksum comparison remained. That comparison was corrected without dropping old-column/checksum assertions; targeted migration and Category/Project/Task race suites then passed together. All 18 actual PostgreSQL lock-wait schedules passed, and four resource list queries used 16 statements for both 1 and 10 rows. Failure XML, lock-wait and query-plan artifacts retained. Session 6 subsequently passed the full suite and clean build with all affected race suites included.

### Session 6: OpenAPI, browser documentation and final evidence

Objective: Publish the exact approved contract and complete reproducible validation.

Implementation:

- [x] Add focused OpenAPI schemas/customization/examples for Category operations, nullable/presence-aware Project fields, typed conflicts/quota and original replay semantics. Describe Project POST 201 as new or legacy replay shape; detail/list/PATCH always use the new response.
- [x] Update generated OpenAPI assertions for existing 13-field/non-null Project assumptions only where this Decision changes them. Express strict legacy/new alternatives without accepting ambiguous undocumented shapes; preserve other resource schemas and documentation security.
- [x] Add/adapt a documentation-only real Chrome harness using current browser tooling, HTTP readiness and visible semantic method/path/schema content. No Swagger internal state selectors, auth relaxation or frontend implementation.
- [x] Document deployment compatibility: immutable existing migrations/replay rows, drain incompatible old instances before new Category traffic, validate old-snapshot reading, no destructive replay cleanup, and explicit limitations on rollback to an old binary. Rehearse the data/reader transition in an isolated database; do not deploy or mutate live data for this check.

Validation:

- [x] Generated `/v3/api-docs` verifies exact five Category operations, request/response fields, headers, UUID/revision/name constraints, fixed collection behavior, all typed errors and explicit Project omission/null/UUID rules. Legacy/new POST schemas match actual responses and no Category filters/widget fields appear.
- [x] Existing default/production Swagger protection tests pass. Real Chrome visibly renders Category/Project methods and schemas, including nullable fields/replay documentation; save result JSON, HTML and screenshot and inspect the screenshot.
- [x] Java 21 `./gradlew.bat test --no-daemon --rerun-tasks` and `./gradlew.bat clean build --no-daemon` succeed with PostgreSQL Testcontainers, all required concurrency and migration checks, and no required skips. Targeted sessions use relevant `--tests` selections before these final full runs.
- [x] Preserve XML counts, full-suite/clean-build output, generated OpenAPI, populated-upgrade/replay evidence, SQL plans and browser artifacts outside cleanable build output, following the existing `.gradle/...-validation/` convention. Record actual evidence; never copy prior Plan pass counts as current results. Stop validation-only processes afterward.

## Final Validation

- [x] All six sessions pass, with no failures caused by this Plan remaining and no unchecked required coverage.
- [x] Fresh and populated V12 upgrades preserve old data/checksums/revisions/counters and replay rows; all existing Projects remain uncategorized with no automatic defaults.
- [x] Ownership/name/FK integrity, restrictive active/archived deletion, deterministic bounded list and exact revision/counter/rollback behavior pass.
- [x] v1 golden compatibility, v2 presence/case semantics, old/new response shapes, persisted unexpired replay after upgrade and replay independent of live Category state pass through application and HTTP boundaries.
- [x] Project assignment/null/omission/archive semantics and related-resource scope/metadata/revision/query behavior pass, with no N+1 or missing uncategorized rows.
- [x] Full tests and clean build pass; concurrency failures are diagnosed rather than hidden by retries, and required browser/OpenAPI evidence is retained.
- [x] Final diff is limited to authorized Category/Project backend integration, new migrations, necessary error mapping, tests and implementation documentation; no frontend, shared-guide, dependency, old-migration or unrelated-resource contract changes.

### API Validation

- [x] Implemented Category endpoints and Project extensions are present in `/v3/api-docs`.
- [x] Request/response schemas, nullable/presence semantics and legacy POST alternative match DECISION-0001 and actual HTTP responses.
- [x] Bean Validation constraints appear where applicable; normalization, ownership, in-use checks, revision and historical replay semantics are explicitly documented.
- [x] HTTP methods, success/error statuses, required security/idempotency headers and no-store behavior match the accepted contract.
- [x] Swagger UI renders the implemented endpoints and schemas correctly without changing production documentation protection.

## Unresolved Decisions

No unresolved architectural or API decision blocks this Plan. Name normalization/uniqueness, cap/order, defaults, relation nullability, archived editing, deletion, revisions, both creation replay contracts and legacy response exception are already accepted and are not reopened here.

Exact class placement, presence/replay representation, migration numbers if intervening work occupies V13/V14, and isolated deployment-rehearsal tooling are implementation choices within that contract. Any contradictory requirement discovered during execution uses the self-recovery/blocker policy rather than silently changing the Decision. Category filtering, manual ordering, starter data, mandatory assignment, Dashboard Category support and scope replacement remain deferred outside this Plan.

## Completion

Completed on 2026-09-14 after all six sessions and Final/API Validation passed. The accepted Decision, existing scope behavior, V1-V12 bytes/checksums and historical replay rows were preserved. No unresolved decision-level blocker remains. No runtime deployment was performed.

### Actual validation

- Java 21 `./gradlew.bat test --no-daemon --rerun-tasks`: **204 tests, 44 suites, 0 failures/errors/skips**, BUILD SUCCESSFUL (3m 59s).
- Java 21 `./gradlew.bat clean build --no-daemon`: **204 tests, 44 suites, 0 failures/errors/skips**, BUILD SUCCESSFUL (1m 54s), including PostgreSQL 16.4 Testcontainers, fresh migration and populated V12-to-V14 upgrades.
- Both commands use test-process `APP_ORIGIN=http://localhost:8080` and the installed Java 21 JAVA_HOME. Production configuration/security assertions were not changed.
- Populated upgrade verifies every old column/checksum, existing null assignments, no Categories/defaults, related-resource rows and expired/unexpired replay rows. A literal unexpired V12 replay is migrated, read through the new application and returned byte-for-byte via HTTP. The v1 golden fingerprint remains unchanged; v2 null/raw UUID variants differ.
- Each final run includes three repetitions of six forced assignment/delete schedules: create, active PATCH and archived PATCH, in both serial orders. PostgreSQL Lock wait is observed before releasing the winner; final relation/replay/resource/counter states are asserted. Category same-key/different-body/name/quota races and existing Project/Task races also pass.
- Query-count evidence is 16 statements for four list operations at both 1 and 10 rows; uncategorized rows remain present. Reference lookup uses `ix_projects_workspace_category`. Scope-derived metadata, aggregate results, cursors and unrelated revisions remain compatible.
- Generated OpenAPI has the exact approved 40-operation surface (38 MVC plus two security-filter operations), five Category operations, required nullable normal Project categoryId and a strict legacy/new POST response alternative. Existing default/production documentation security tests pass.
- Real headless Chrome against isolated `bootTestRun`/Testcontainers passes seven visible Category/Project operations, nine schemas, CSRF documentation, expanded replay text and nullable UUID schema. HTML, JSON and three screenshots retained; screenshots inspected. Validation-only Java/Chrome processes stopped.
- Final V1-V12 SHA-256 comparison and `git diff --check` pass. No old migrations, dependency/build configuration, shared guides, accepted Decision or related-resource production contracts changed.

### Recovery evidence and root causes

- Session 2 fixtures used the wrong CSRF JSON property and assumed a different Origin; aligned test clients with actual csrfToken/configured Origin. Session 4's pre-existing logout fixture requires localhost:8080; supplied that Origin only to the test process. Security checks were retained.
- Session 3 injected Repository failure is translated by Spring into InvalidDataAccessApiUsageException. Asserted the translated exception and original IllegalStateException cause/message while retaining rollback/counter checks.
- Session 5 migration fixture leaked its temporary JDBC schema through a pooled connection. Hikari's unset default schema did not restore a manually changed schema, so later concurrent commands queried the wrong schema and could not find owned workspaces. The fixture now saves/restores the original schema in finally and asserts restoration. Targeted migration plus Category/Project/Task races passed together, followed by both full final runs. This was a diagnosed fixture-isolation failure, not a lucky concurrency retry or production-lock relaxation.
- Existing populated-upgrade comparisons assumed the old Project column set and old latest migration count. They now explicitly assert the added column is null, compare every original column, and restrict checksum comparison to the fixture's originally installed versions. No old-data assertions were removed.
- Session 6's first full run passed 203/204; the global API inventory lacked five approved Category operations. Updated exact paths/method counts, Category creation idempotency and non-cursor collection expectations; the following full run and clean build pass all 204.
- Expanded browser checks initially read immediately after clicking, before React committed the expanded content (failure HTML lacked the description). Added bounded polling of visible text after expansion; the exact replay/nullable assertions now pass and screenshots show both. No Swagger internals or weakened assertions were used.

### Retained artifacts

All generated evidence is outside cleanable build output under [project-category-validation](../../.gradle/project-category-validation/):

- [Validation totals](../../.gradle/project-category-validation/validation-summary.json); `full-suite/` and `clean-build/` retain all 44 JUnit XML files for each successful run, with test stdout/stderr; captured Gradle console in `full-suite/console.log` and complete redirected [clean-build log](../../.gradle/project-category-validation/clean-build.log).
- `baseline/`: original fingerprint/snapshot/response source and migration SHA-256; `migration/`: populated upgrade and final unchanged-checksum evidence; `replay/`: literal old/new HTTP responses.
- [Generated OpenAPI](../../.gradle/project-category-validation/openapi/openapi.json), `concurrency/` lock-wait schedules, `queries/` statement counts/EXPLAIN, `browser/` final JSON/HTML/screenshots, and `recovery/` failure XML/browser diagnostics. Historical browser blocker artifacts are retained as failure evidence; final browser result.json records PASS.
- [Deployment compatibility runbook](../project-category-deployment.md) documents traffic draining, forward-compatible readers, immutable replay rows/migrations and old-binary rollback limitations.

The evidence directory follows the repository's local ignored-artifact convention; retain it when reviewing or handing off this implementation. Category filtering, manual ordering, starter data, mandatory assignment, Dashboard Category support and scope replacement remain deferred.
