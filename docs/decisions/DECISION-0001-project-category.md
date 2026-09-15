# DECISION-0001: Workspace-owned ProjectCategory with scope compatibility

Date: 2026-09-14
Status: `superseded`

Superseded by [DECISION-0002](DECISION-0002-project-category-only-classification.md) on 2026-09-14. Historical contract below is retained.

Explicitly approved by the user for the backend architectural contract only. Acceptance does not authorize implementation, an implementation Plan, or changes to agent guides.

## Context

The accepted [frontend ProjectCategory Decision](../../../frontdev/docs/decisions/project-category-boundary.md) establishes a separate server-backed relation while retaining scope. This record resolves its backend dependencies; frontend UX does not determine backend policy.

The backend investigation found:

- [Project](../../src/main/java/com/kopite/devspace/project/domain/Project.java) stores required `scope` (`unity|server`) and derives `colorToken` from it. Archived Projects remain editable. Project names are not unique.
- [Project commands](../../src/main/java/com/kopite/devspace/project/application/command/ProjectCommandService.java) use workspace-first pessimistic locking, explicit Project revisions, and an independent workspace `dataRevision`.
- [Creation fingerprints](../../src/main/java/com/kopite/devspace/project/application/command/ProjectRequestHash.java) preserve seven raw command values and omission. [Replay storage](../../src/main/java/com/kopite/devspace/project/infrastructure/persistence/ProjectCreateReplayAdapter.java) retains the original creation snapshot for 24 hours.
- Overview and related resources use Project scope independently of classification. Category support does not require changing these contracts.
- At investigation time, local PostgreSQL had V1–V12 applied, one workspace, two active `unity` Projects, and two unexpired Project creation replay rows. These are observations, not migration preconditions. No backend listened on port 8080; API evidence came from current source/customizers and OpenAPI tests, not a live generated document.

The Decision guide names `DECISION-TEMPLATE.md`, but no such template or prior backend Decision exists in the repository. This record follows the prescribed filename/status conventions with context, decision, rationale, consequences, and validation sections.

## Decision

### Domain and ownership

Introduce `ProjectCategory` with server-generated UUID `id`, immutable UUID `workspaceId`, `name`, explicit long `revision`, `createdAt`, and `updatedAt`. Revision begins at 1 and is bounded by the existing positive JavaScript-safe integer range, 1–9007199254740991. Audit timestamps use the existing UTC/microsecond convention. Category has no `scope`, archive state, manual position, or default flag.

Category identity is always UUID-based. Names are mutable display data, never foreign keys, relation values, or identity lookup keys. Neither scope nor Category may be inferred from the other.

Resolve ownership through the authenticated user's personal workspace. Reject client-owned identity/workspace fields. UUID inputs must have the standard hyphenated representation; hexadecimal case is accepted, and responses use canonical lowercase UUID strings. Missing and foreign resources both return `404 RESOURCE_NOT_FOUND` without disclosing ownership.

Persist `project_categories` with primary key `id`, a workspace FK with restrictive deletion, unique `(workspace_id,id)`, and workspace-local name uniqueness. Add nullable `projects.category_id` with composite FK `(workspace_id,category_id)` referencing `project_categories(workspace_id,id) ON DELETE RESTRICT`. Retain the Project workspace non-null constraint and existing Project keys. Index `(workspace_id,category_id)` for reference checks; this does not introduce Category filtering.

### Names

Normalize with Java `String.trim()` followed by Unicode NFC normalization. Reject malformed Unicode, null, and names blank under `String.isBlank()`; require at most 100 UTF-16 code units after normalization. Preserve internal whitespace. Boundary whitespace outside Java `trim()` is not silently removed.

Names are case-sensitive: `Tools` and `tools` are distinct. No case folding, locale-specific comparison, accent folding, or compatibility normalization is performed. Canonically equivalent Unicode spellings normalize to the same name. Uniqueness is exact equality of the normalized stored name within a workspace, using deterministic PostgreSQL `C` collation; enforce it with a database unique constraint. Java validation owns the full normalization/UTF-16 contract; SQL checks complement it with nonblank/basic length integrity.

An existing Category may keep its own normalized name. Another Category with that name yields `409 CATEGORY_NAME_CONFLICT`, with a `name` field error. A deleted name may be reused, but the replacement receives a new UUID. Exact case-sensitive uniqueness avoids unspecified locale/case-fold equivalence while preventing duplicate normalized display names; names still do not define identity.

### Category API and collection

Base path: `/api/v1/project-categories`. Responses are JSON with `Cache-Control: no-store`. Apply existing session ownership, disabled-account checks, and CSRF/Origin protection for mutations. Do not add public endpoints.

`CategoryResponse` has exactly the required fields `id`, `name`, `revision`, `createdAt`, `updatedAt`; all except `name` are server-owned. No workspace identity or `dataRevision` is exposed.

| Operation | Request | Success response |
| --- | --- | --- |
| List | `GET /api/v1/project-categories`, no query parameters | `200 {"items":[CategoryResponse...],"total":n}` |
| Read | `GET /api/v1/project-categories/{id}` | `200 CategoryResponse` |
| Create | `POST /api/v1/project-categories`, required `Idempotency-Key`, body `{"name":"Tools"}` | `201 CategoryResponse` |
| Rename | `PATCH /api/v1/project-categories/{id}`, body `{"revision":1,"name":"Platform"}` | `200 CategoryResponse` |
| Delete | `DELETE /api/v1/project-categories/{id}?revision=2`, no body | `200 {"deletedId":"<uuid>"}` |

Create and rename require their shown fields. Reject unknown/repeated fields, explicit nulls, wrong types, malformed UUIDs, and invalid revisions. Category rename requires `name`; revision-only requests are invalid, but a supplied same-value name is a successful rename operation. Reject unsupported/repeated Category query parameters. Use `X-CSRF-Token` as for existing mutations.

Return the full owned collection ordered by `createdAt ASC,id ASC`, with `total == items.length`, no cursor, search, or manual ordering. Rename does not change order. Limit a workspace to 100 persisted Categories; a new creation at the limit returns `429 QUOTA_EXCEEDED`. This is an admission limit, not permission to truncate existing data. Valid replays, reads, renames, and deletes work at the limit. List reads use a consistent read snapshot and do not write. No separate collection revision is needed because there is no collection reorder/replace operation.

### Errors and restrictive deletion

Use the existing `ApiError` envelope (`code`, `message`, `fieldErrors`, `requestId`) and these semantics:

| HTTP/code | Condition |
| --- | --- |
| `400 VALIDATION_ERROR` | Invalid body/header/query/UUID/revision syntax or name constraints |
| `401 AUTH_REQUIRED` | No authenticated session |
| `403 ACCOUNT_DISABLED` / `CSRF_INVALID` | Existing account or authenticated mutation security rejection |
| `404 RESOURCE_NOT_FOUND` | Missing/inaccessible workspace, Category, or Project target |
| `409 REVISION_CONFLICT` | Stale expected revision or inability to advance a required revision/counter |
| `409 CATEGORY_NAME_CONFLICT` | Another owned Category has the normalized name |
| `409 CATEGORY_IN_USE` | Any active or archived Project references the Category |
| `409 IDEMPOTENCY_KEY_REUSED` | An unexpired creation key is reused for a different fingerprint |
| `429 QUOTA_EXCEEDED` | A new Category would exceed 100 Categories |

Unexpected failures retain the existing `500 INTERNAL_ERROR` envelope. Do not expose database constraint names or SQL. Name conflict has `fieldErrors.name`; in-use conflict has empty field errors and explains that Projects must be reassigned or cleared explicitly before deletion.

For rename/delete, check owned existence before expected revision, then business constraints. Delete checks references across all Project statuses and physically removes only the Category. The composite FK backs the application check; translate the specific reference violation to `CATEGORY_IN_USE`. Never cascade-delete Projects, silently null relations, or choose a replacement. Repeated deletion returns 404. Deletion at Category maximum revision is allowed because the deleted Category needs no next revision; workspace counter advancement must still be possible.

### Project relation and API

Add `categoryId` to Project editable values independently of scope:

| Input | POST Project | PATCH Project |
| --- | --- | --- |
| Omitted | Create uncategorized | Preserve current Category |
| Explicit null | Create uncategorized | Clear Category |
| UUID | Assign owned Category | Assign/reassign owned Category |

Use presence-aware parsing/commands so omission and null cannot collapse. Explicit null remains invalid for all currently non-nullable Project request fields. Unknown/repeated fields remain invalid. A syntactically valid but missing/foreign Category returns `404 RESOURCE_NOT_FOUND`; do not fall back to uncategorized.

Archived Projects may assign, reassign, or clear Category. Archive/unarchive preserves it. Scope remains required on create and retains its current PATCH semantics; no Category mutation may alter scope. A Project PATCH that changes Category plus other fields advances revision only once.

Normal Project detail/list/PATCH responses and new creation snapshots include required nullable `categoryId`. Return only the UUID relation, with no embedded `categoryName`, Category revision, or copied display value. Historical pre-deployment creation replay responses retain the exception specified below. Category read/list is authoritative for names; rename changes neither Project response metadata nor Project revision.

### Revisions, transactions, and races

Retain explicit revision checks and pessimistic locking; do not introduce JPA `@Version`. Every Category command locks its owning workspace before reading mutable workspace state, follows account validation, and performs its write and counter increment in one transaction. Project assignment and Category deletion use the same workspace lock, preventing a check-then-delete race. When a command needs both, lock Project before Category; order multiple IDs consistently. Reads remain side-effect free.

| Successful operation | Resource effect | Workspace effect |
| --- | --- | --- |
| Category create | New revision 1; equal creation/update timestamps | `dataRevision +1` |
| Category rename, including same normalized name | Category revision +1; update timestamp assigned | `dataRevision +1` |
| Category delete | Remove Category only | `dataRevision +1` |
| Project assign/reassign/clear, including same-value or already-null PATCH | Normal Project revision +1; update timestamp assigned | `dataRevision +1` |
| Creation replay or read | None | None |

Use operation time for `updatedAt` as today; it need not be strictly later if clock precision yields the same instant. Preserve workspace metadata `revision` and `updatedAt`. Failures and transaction rollback leave all counters unchanged. Check required counter capacity before committing and return `REVISION_CONFLICT` on exhaustion in these new mutation paths. Category rename does not update Projects or Task/Journal/Milestone/Dashboard resource revisions.

If assignment wins the workspace lock, subsequent deletion returns `CATEGORY_IN_USE`. If deletion wins, a new assignment returns 404. A matching historical Project creation replay follows the replay rule instead of current Category existence checks. Concurrent same-name creates with different keys yield one creation and one name conflict; same-key identical creates yield one creation and its replay. A stale rename/delete loses with `REVISION_CONFLICT` after ownership validation.

### Creation idempotency and deployment compatibility

Preserve Project creation's required 1–128 visible ASCII `Idempotency-Key`, workspace/method/path namespace, original 201 status, and 24-hour replay lifetime. Expiry allows a new creation and fresh relation validation; successful replay never extends expiry or increments counters.

Project fingerprints must distinguish request presence without changing old requests:

- With `categoryId` omitted, use the current version-1 fingerprint byte-for-byte: version integer 1; existing seven fields in their existing order, raw command value conversion and length-prefixed UTF-8/null encoding. Do not append an omission marker, normalize old values differently, or bump this branch's version.
- With `categoryId` present, use version integer 2, the same seven fields/encoding, then the eighth field: null marker for explicit null, otherwise the supplied valid UUID string. Preserve its supplied hexadecimal case for hashing while persisting UUID identity canonically. Property order remains irrelevant; duplicate fields remain invalid.
- Omission and explicit null both create an uncategorized Project but are different creation intents and fingerprints. Different supplied UUID spellings/values are also different fingerprints. Category name never participates.
- New Category-omitted creation records still store the new response including `categoryId:null`; request fingerprint version and snapshot shape are independent.

Retain existing Project replay rows, hashes, expiry and original response values. Old serialized snapshots without `categoryId` must remain readable. For a pre-deployment replay, return the original legacy Project response shape, including absence of `categoryId`; do not materialize current Project data or add a null field to that historical response. Preserve this shape through the replay abstraction and transport rather than losing field presence in a new record constructor. Existing exact-response replay assertions must continue to hold.

For new replay snapshots, return the originally captured `categoryId`, even if the Project was subsequently reassigned/cleared or the original Category was then deleted. After authentication, workspace resolution and static request validation, resolve an unexpired matching replay before live Category lookup. A different fingerprint returns key conflict; an uncached/new creation validates current Category ownership/existence. Never invalidate a valid historical replay because of Category rename, deletion, reassignment, or current Project state. Current Category freshness belongs to subsequent reads.

Document Project POST 201 as a new Project response or a legacy creation-replay response without `categoryId`; detail/list/PATCH always use the new shape. Preserve the legacy reader for all retained old rows, including expired records encountered before cleanup. Do not clear unexpired replay rows, rewrite their hashes, shorten expiry, or enrich snapshots during deployment. Old application instances that cannot read new Category-bearing snapshots must not handle traffic after the new contract is enabled; deployment must drain such writers/readers before enabling Category requests. Exact rollout mechanics belong to later implementation work.

Category POST also uses persisted 24-hour idempotency, matching existing resource-create conventions. Namespace by workspace, POST and Category path, independently of Project keys. Hash the supplied raw `name` using a versioned length-prefixed encoding; property order is ignored, normalization-equivalent raw names are different intents. Return the original Category creation snapshot after rename/deletion, without recreating it or consulting current quota/name uniqueness. Validate the live name/quota only for new creation after static validation/replay resolution. PATCH and DELETE have revision preconditions, not creation replay; uncertain mutations require a fresh read before retry.

### Migration and defaults

Use only new Flyway versions after V12; never modify, rename, delete or repair V1–V12 to introduce this feature. Add `project_categories`, nullable `projects.category_id`, ownership/name constraints, reference index, and separate Category creation replay storage following existing replay table conventions.

All existing Projects, including archived rows, remain uncategorized. Preserve their IDs, scope, status, editable data, revisions and timestamps, and preserve workspace counters. No scope-derived assignments, fallback Category, or Project rewrite/backfill is needed. Existing Project replay data remains intact.

Existing and new workspaces start with no automatic Categories. Login and workspace reuse must not seed or recreate deleted Categories. User creation of Categories is the only initial provisioning path; existing workspace-creation behavior and counter initialization stay unchanged.

### Compatibility boundaries

- Preserve Project required scope, `colorToken = scope`, filters (`scope`, `status`, `query`, `limit`), defaults, literal name/stack search, `createdAt DESC,id DESC` order, signed cursor format/binding and per-request read consistency. Category does not join filter/search/order/cursor identity.
- Preserve Project primary/composite keys and absence of Project-name uniqueness. Category name uniqueness must not impose Project-name uniqueness.
- Preserve Overview grouping by Project scope/status: `projects.total` means active count, archived count is separate; task counts include archived Projects and exclude deleted Tasks. Category changes alone do not change aggregates.
- Preserve Task/Journal/Milestone normal-read derivation of `projectName` and `scope` from Project and their existing historical replay behavior. Task/Journal disallow new creation or reassignment into archived Projects but retain editing of existing relations; Milestone permits creation/reassignment into archived Projects. Category introduces no additional gates.
- Preserve Dashboard owned-Project validation, including archived references, and normalization of widget scope to `all` when `projectId` is supplied. Add no Category widget fields or validation dependency.
- Preserve Link's independent scope and inclusion of `all` links in scoped lists. Category changes alone do not alter Task, Journal, Milestone or Dashboard revisions, nor require their response/cache invalidation for scope-derived metadata.

## Rationale and consequences

Nullable UUID classification introduces user-managed categories without assigning meaning to old Projects or breaking scope contracts. Restrictive deletion protects both active and archived references. Bounded reference data avoids pagination/selection gaps; a stable creation order avoids rename-induced movement. No manual ordering or starter lifecycle is justified by current requirements.

Returning only `categoryId` keeps Project snapshots and revisions independent of mutable display data. Consumers must refresh the Category collection after its mutations and compose names by UUID; they must not treat Project revision as a name-freshness signal or treat historical creation replay as current Category state. Legacy replay shape support is intentional compatibility complexity required by existing unexpired records.

The 100-item limit and case-sensitive normalized uniqueness are concrete initial product constraints, subject to approval with this Decision. They require no new library, database extension, infrastructure, or global architecture pattern.

## Required future validation and risks

No implementation or tests are performed by this record. A later authorized implementation must cover:

| Coverage | Required outcomes |
| --- | --- |
| Category CRUD/OpenAPI | Exact endpoints/shapes, required headers, strict parsing, no-store, empty/full deterministic list, cap 100 and replay at capacity, typed errors |
| Ownership/security | Missing/foreign equivalence, body ownership injection, disabled account, CSRF/Origin, composite FK rejection of cross-workspace assignment |
| Names | Trim/blank rules, UTF-16 boundary and malformed Unicode, NFC equivalence, case distinctions, duplicate create/rename, own-name rename, concurrent duplicates, reuse after deletion |
| Delete/archive | Active and archived references both block deletion; unreferenced delete succeeds; repeat delete 404; no cascading/clearing; archived Project assignment and archive/unarchive preservation |
| Revisions/atomicity | Stale/same-value mutations, maximum revisions/counters, delete at maximum Category revision, rollback and replay storage failures, exact counter increments, unchanged workspace metadata and unrelated resource revisions |
| Project relation | Create omitted/null/UUID; PATCH omission/null/UUID; scope independence; all normal responses include nullable identity, no copied name |
| Races | Assignment versus deletion in both serial orders, rename/delete stale writes, identical/different-key creates, original replay after clear/reassignment/deletion |
| Legacy replay | Golden version-1 hashes; version-2 null/UUID/presence/case differences; unchanged old seven-field hashing; persisted old snapshot decoding and exact historical response; new omitted snapshot shape; no replay counter increments or live Category dependency |
| Flyway/deployment | Populated V12 upgrade with active/archived Projects and dependent rows, unexpired and expired old replay rows; unchanged old checksums/data/revisions/counters; no auto-seeding; fresh database; no old application traffic against new snapshot formats |
| Scope regressions | Project filters/cursors/search/uniqueness; Overview aggregate and repeatable-read tests; Task/Journal/Milestone derived metadata/archive/replay behavior; Dashboard reference/normalization and Link scope behavior |
| Read efficiency/freshness | Uncategorized Projects never disappear through inner joins; no eager/N+1 Category queries in Project or dependent-resource reads; current Category name after rename without Project revision changes; no late historical replay overwriting current Category display |

Extend existing `ProjectApiTests`, `ProjectCommandTests`, `ProjectPersistenceTests`, and `ProjectOpenApiTests`; add focused Category suites and populated-upgrade/replay tests. Preserve and extend relevant Overview, Task, Journal, Milestone, Link and Dashboard regression suites. In particular, update current OpenAPI assertions that all Project request fields reject null and that Project responses have 13 required properties without weakening unchanged field contracts.

The principal risks are accidental inner joins dropping uncategorized Projects, eager/N+1 display lookups, stale Category display after rename, collapsing omitted/null request presence, breaking old replay hashes or response shapes, and unnecessary invalidation of unrelated scope-based resources. Ownership constraints, identity-only Project responses, explicit replay compatibility, and the coverage above address these risks.

## Deferred scope

Category filtering/search of Projects or aggregates, Dashboard Category configuration, manual ordering, pagination beyond the bounded collection, starter/default Categories, mandatory assignment, scope replacement, merge/reassign-on-delete operations, and Category archive/restore are not introduced. They require separate approval if needed. Frontend management location/display UX remains governed by its Decision. Exact code organization, deployment tooling and execution sequencing belong to a later authorized implementation Plan.
