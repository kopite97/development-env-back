# PLAN-0013: Independent Widgets and Dashboard Placements

Status: `completed`
Date: 2026-09-16

## Goal

Separate widgets from HomeDashboard into an independent feature domain. Widget owns its information type, title, configuration, and editing lifecycle. Dashboard owns placement, order, and size. Prepare extension points for GitHub, server CPU/network metrics, Kubernetes, Jenkins, and database logs without implementing those integrations now.

The user approved backend implementation and isolated validation on 2026-09-16, subject to the Session 1 contract gate. Frontend adoption and production operations remain excluded. The accepted architecture is [DECISION-0004](../decisions/DECISION-0004-independent-widget-and-dashboard-placement.md).

## Scope

- Include independent Widget/Placement models, migration of six existing types, registration/validation, configuration/data APIs, migration and rollback design, isolated validation, OpenAPI, and frontend handoff.
- Reuse existing application queries for data. Preserve deploy as an explicitly unavailable placeholder, not a fictional deployment backend.
- Exclude external integrations, collectors, monitoring infrastructure, frontend code, SSE/WebSocket, notifications, sharing/public links, dynamic plugins, Redis, production access, and deployment.
- Use existing Java 21, Spring Boot, JPA, PostgreSQL, Flyway, and test tooling. No new dependency, infrastructure, or environment variable is required by default.
- Preserve session authentication, CSRF, no-store, workspace isolation, and unrelated business contracts. Introduce breaking Dashboard changes through explicit API versioning and coordinated cutover.
- Follow root AGENTS, plans/AGENTS and its runtime template, decisions/AGENTS, Architecture/API/Security/Spring Boot/Database/Flyway, and relevant Implementation guides. Shared agent-guide changes require separate approval.
- PLAN-0012 remains rejected. This work does not resume Redis implementation.

## 1. Current Implementation

Baseline findings describe source before implementation, not a verified production deployment. Java paths are relative to `src/main/java/com/kopite/devspace/`.

| Evidence | Behavior and consequence |
| --- | --- |
| `dashboard/domain/HomeDashboard.java` | Workspace/home identity, schemaVersion 2, JSONB array, array order as layout, one revision for all settings. No independent Widget persistence. |
| `DashboardWidget.java`, `DashboardSelection.java` | Shared id/type/title/size/selection/limit record, six types, all/uncategorized/project/category selectors. Extensions would expand common fields and branches. |
| `dashboard/presentation/dto/SaveHomeDashboardRequest.java` | Schema 2 only; strict unknown/null/duplicate rejection; full replacement. One Widget edit conflicts with other Dashboard edits. |
| `HomeDashboardQueryService`, `HomeDashboardResponse` | Repeatable-read query, virtual defaults at revision 0, no GET writes, saved empty arrays preserved, configuration only. |
| `HomeDashboardCommandService`, `DashboardReferences` | Workspace-first locking; ownership checks; same-value revision increments; identical existing missing Category references retained; broken Project references return 404. |
| `dashboard/infrastructure/HomeDashboardRepositoryAdapter` | Loads, locks, and persists the home configuration as one entity. |
| V12/V15 and staged V16/V17 migrations | V12 JSONB dashboards, V16 classification conversion, V17 schema_version=2 restriction. build.gradle uses categoryStage bridge/final packaging. |
| Existing Dashboard and Category dashboard tests | Round trips, defaults, empty layouts, first-save races, rollback, overflow, and Category deletion races. |

Link is workspace-owned with an existing nullable projectId, a composite owned-Project FK, and Category filtering through Project. This is implemented in V15, LinkQueryService, and LinkSearchAdapter and specified in DECISION-0002 section 5; the earlier statement that Links have no Project relationship was incorrect. Deploy still has no real backend resource. Read-only frontend composition evidence and the adopted data contracts are now recorded in [the frozen contract](../widget-api-contract.md).

Review evidence: [Dashboard command tests](../../src/test/java/com/kopite/devspace/DashboardCommandTests.java) cover revision 0, empty saves, and rollback; [Category dashboard API tests](../../src/test/java/com/kopite/devspace/CategoryOnlyDashboardApiTests.java) cover retained missing selections and foreign references; [Dashboard concurrency tests](../../src/test/java/com/kopite/devspace/DashboardCategoryConcurrencyTests.java) observe PostgreSQL workspace lock waits. [Category command tests](../../src/test/java/com/kopite/devspace/CategoryCommandTests.java) and [Journal API tests](../../src/test/java/com/kopite/devspace/JournalApiTests.java) cover replay after deletion. These sources were inspected, not executed during this documentation review.

## 2. Domain Boundaries

```text
Dashboard
  HomeDashboard (layoutRevision)
    WidgetPlacement (widgetId, order, size)
                      |
Widget                | identity reference
  Widget (workspaceId, type, title, configVersion, config, revision)
  WidgetTypeRegistry
    Definition + Config codec/validator + Data handler
                      |
Existing application queries
  overview / task / journal / milestone / link
Future source features
  github / server monitoring / kubernetes / jenkins / database observability
```

Source features own credentials, connections, collection, and synchronization. Widget owns presentation configuration and reads source data. Multiple Widgets must not create duplicate collectors. Source collection frequency, browser refresh frequency, and displayed time range are separate concepts.

### Widget aggregate

- UUID id, workspaceId, stable type, title, configVersion, validated config JSONB, revision, createdAt, updatedAt.
- Keep current type names: overview, board, deploy, links, journal, milestone. Future examples include github.commits and server.cpu. One Widget has one type.
- configVersion identifies the configuration format; Widget revision detects editing conflicts. Initial revision is 1. Preserve safe-integer limits and overflow handling.
- Type is immutable. Changing type creates another Widget. Full title/config replacement requires expected revision; same-value writes increment it.
- Unplaced Widgets are valid. Deletion requires expected revision and no placements; referenced deletion returns 409 WIDGET_IN_USE. Removing a placement does not delete its Widget.
- List configurations with keyset pagination and an optional unplaced filter. No automatic orphan deletion, sharing, or recycle bin.

### Dashboard and Placement

- Keep dashboard_key=home initially. Multiple-dashboard creation is deferred.
- Placement contains UUID, workspace/dashboard reference, widgetId, position, size. Title/configuration belong only to Widget.
- API array order is display order; normalize stored positions. No new coordinate/grid engine.
- Initially enforce at most one placement per Widget with UNIQUE(workspace_id, widget_id). Independent Widgets may share type/configuration. Sharing requires a later contract and constraint change.
- Layout writes check/increment layoutRevision only; Widget edits increment Widget revision only. Both increment workspace dataRevision once in the same transaction.
- This separates conflict semantics, not all physical serialization. Keep workspace-first locking.

### Registry and dependencies

- Code-registered types provide definitions, configuration codecs/validators, and data handlers. Detect duplicate types, unsupported versions, and invalid definitions at startup/tests.
- Definitions include type, configVersion, configuration schema, supported sizes, data kind, and availability. Use explicit JSON Schema or OpenAPI references without building a form-generation framework.
- Never execute user-provided class names, scripts, or URLs. No dynamically loaded plugins.
- Use immutable typed configs in application code and JSONB at persistence boundaries. Do not store ORM entities, SDK objects, or polymorphic class metadata.
- Separate structural validation from reference/ownership checks. Validate ownership on writes and reads; preserve selector semantics.
- Handlers adapt existing application queries through a narrow integration boundary. Dashboard domain must not manipulate Widget repositories. Application services coordinate cross-feature work.
- Avoid generic provider frameworks and interfaces for ordinary internal calls. Interfaces belong at actual dispatch or replaceable boundaries.
- Prove extension with a test-only type requiring no Dashboard domain/storage change. Do not publish it or add empty future integration implementations.

## 3. Persistence and Migration

| Target | Proposed shape |
| --- | --- |
| widgets | Common relational fields, object config JSONB, workspace FK, version/revision constraints, UNIQUE(workspace_id, id). |
| dashboard_widget_placements | Dashboard composite FK, Widget composite FK(workspace_id, widget_id), position/size constraints, placement identity, unique dashboard position. |
| dashboards | Retain home identity/timestamps; independent layout revision. Preserve legacy revision/widgets temporarily for recovery, not as the new write source. |
| Legacy mapping | Map (workspace, dashboard_key, old string widget id) to new Widget/Placement UUIDs. Old IDs need not be UUIDs. |

Composite FKs prevent cross-workspace references. Do not add a restrictive Category FK that breaks missing-reference retention. Preflight broken Project references rather than silently repairing/dropping them. Preserve titles, sizes, selectors, omitted limits, order, repeated types, and saved-empty layouts.

V18 was confirmed available in Session 1 and implements the new schema/backfill after V17. **Never apply V18 to a V16 bridge DB and add V17 afterward.** Session 1 establishes categoryStage, V17 prerequisites, and legacy replay expiry. If V17 cannot complete, block cutover or obtain approval for a different sequence. Never edit V12-V17 or bypass checksums through baseline/repair.

**Resolved Session 1 artifact policy:** the user approved final-only Widget releases. Application build/test/run tasks require explicit `-PcategoryStage=final`; Docker requires `--build-arg CATEGORY_STAGE=final`. Missing, bridge, or invalid values fail before preparing application resources, even if outputs are already up-to-date. Gradle configuration, IDE model import and metadata tasks such as `help` do not select a release and remain available without this option. Preserve the pre-Widget bridge artifact/source for its replay window and maintenance; do not add conditional entity scanning or substitute JDBC for JPA. V17 drain checks, schema validation and existing migration history remain unchanged. Production migration/deployment is still excluded. See the [rollout procedure](../project-category-only-rollout.md) and [build evidence](../reviews/plan0013/build-policy.md). This release-specific policy needs no shared agent-guide change.

Transform saved arrays once, initialize Widget revision to 1, and preserve old Dashboard revision as initial layoutRevision. Dashboard timestamps are migration values, not claimed individual Widget history. Unsaved dashboards remain absent.

Backfill identities must survive a complete transaction rollback and retry: use deterministic name-based UUID derivation with fixed, separate Widget/Placement namespaces and an unambiguous encoding of canonical workspace UUID, dashboard key, and the exact validated legacy Widget ID. Do not include position, mutable configuration, timestamps, or a run-specific seed. Session 1 must freeze the algorithm, namespace constants, encoding, and known-answer vectors before migration code. A standard algorithm such as UUIDv5 is a candidate, not permission to add a library or DB extension. No repository-specific name-based UUID migration convention was found.

The mapping table is an audit/reconciliation record, not the source of randomness: a mapping inserted in the same transaction also rolls back, so random UUIDs plus that table do not guarantee retry identity. Do not add separately committed mapping transactions merely to preserve random IDs. On retry, recompute the same IDs and verify any existing mapping; mismatches or collisions stop migration rather than overwrite or regenerate. Test failure after mapping/row insertion followed by full rollback and retry, Unicode/delimiter-containing IDs, reordered arrays, and identical legacy IDs in different workspaces. This requirement applies to backfill only, not normal Widget creation.

Increment dataRevision once per transformed workspace in the migration transaction; stop on overflow. Distinguish internal storage schema from response schema. New migrations explicitly replace V17's schema_version=2 restriction where needed. Keep legacy JSONB and identity mappings throughout this plan; destructive cleanup requires a later approved migration/plan. Measure backfill and lock impact on populated isolated fixtures; do not claim production representativeness without evidence.

## 4. Redesigned API and Responses

### Endpoints

| Endpoint | Contract proposal |
| --- | --- |
| GET `/api/v1/widget-types` | Authenticated catalog: config schema/version, sizes, data kind, availability; no secrets/class names. |
| GET `/api/v1/widgets` | Owned configs, optional unplaced filter, keyset pagination. |
| POST `/api/v1/widgets` | Create from type/title/configVersion/config; Idempotency-Key; 201 and Location. |
| GET/PUT `/api/v1/widgets/{id}` | Read / replace title and config with body revision; 200. |
| DELETE `/api/v1/widgets/{id}?revision={expected}` | Delete unreferenced Widget; 200 deletion DTO; no creation-style replay. |
| GET `/api/v1/widgets/{id}/data` | Read with saved configuration; documented paging/time-range parameters only; no config mutation. |
| GET/PUT `/api/v3/dashboards/home` | Read / replace placements with body layoutRevision; no Widget config changes. |
| POST `/api/v3/dashboards/home/initializations` | Body schemaVersion=3 and layoutRevision=0; create defaults once; Idempotency-Key; 201 and Location. |

API versions follow resource contracts, not a synchronized platform generation. New Widget resources start at v1; the incompatible Dashboard contract moves from v2 to v3. This follows existing coexistence of Category/auth v1 and business v2 under DECISION-0002. URL versions are independent of configVersion, payloadVersion, and dashboard schemaVersion.

### Expected revision transport

| Operation | Required precondition input | Semantics |
| --- | --- | --- |
| Widget PUT | JSON body `revision` | Integer 1..9007199254740991; compare to current Widget revision. |
| Widget DELETE | Single query `revision`, no body | Same positive range; repeated delete returns 404. |
| Dashboard PUT | JSON body `layoutRevision` | Integer 0..9007199254740991; 0 means no saved layout, otherwise compare to current layout revision. |
| Initialization POST | JSON body `layoutRevision: 0` | Create-if-absent, with schemaVersion=3; another numeric value is invalid input. |

This retains current body revision for edits (SaveHomeDashboardRequest and update DTOs) and bodyless DELETE query revision (Category/Journal/Milestone controllers). Missing, null, duplicate, fractional, or out-of-range values return 400; a well-formed stale expected revision returns 409 REVISION_CONFLICT. Use the existing ownership/security error precedence. No If-Match/ETag concurrency mechanism is introduced, and a header cannot substitute for a required body/query revision. Successful same-value PUT still advances the relevant revision once.

Do not run an old full-config writer alongside independent Widget editing. At coordinated cutover, retire legacy Dashboard routes with 410 API_VERSION_RETIRED after normal authentication and applicable CSRF checks. Inspect existing v1 behavior before choosing retirement handlers. Unrelated v1/v2 endpoints remain unchanged.

Breaking responses are permitted, data loss is not. Old PUT must never overwrite independently edited Widgets. Frontend readiness is a production cutover prerequisite. Any read-only compatibility window needs an explicit additional contract.

### Configuration response

Example Widget response:

```json
{
  "id": "<widget-uuid>",
  "type": "board",
  "title": "Project tasks",
  "configVersion": 1,
  "revision": 4,
  "config": { "selection": { "kind": "project", "projectId": "<project-uuid>" }, "limit": 10 },
  "referenceState": "valid",
  "createdAt": "2026-09-16T00:00:00Z",
  "updatedAt": "2026-09-16T00:00:00Z"
}
```

referenceState is response-only and computed: exactly `valid | missingCategory`. `valid` means no unresolved Category selection after workspace-scoped Project and Category validation; it does not promise external-source availability. `missingCategory` means the saved Category UUID cannot be resolved in this workspace, without asserting deletion provenance. Preserve identical existing missing Category references by Widget identity. Creating a new Widget or changing to a missing reference returns 404; editing other settings while retaining the same missing reference remains allowed. This compatibility exception does not extend to Project or a generic reference lifecycle: broken Project references still return 404. Frontend handoff must distinguish a renderable missing-Category configuration from an inaccessible Project-dependent resource.

For `/widgets/{id}/data`, first authorize the Widget. If its retained Category reference cannot be resolved within its workspace, return 200 with availability=unavailable, freshness=unknown, data=null, page=null, and problem={code:REFERENCE_MISSING,retryable:false}. Read config/reference state and dataRevision in one RR snapshot; do not call the business query with `all` or translate this condition into an empty success. Preserve the stored UUID and revision. Configuration GET reports missingCategory; same-name recreation does not repair the reference.

This exception is for a retained reference on an owned saved Widget. Requests for a foreign/missing Widget, new or changed unowned Category references, and any reference override supplied to `/data` never receive this envelope: return the normal 404 or strict-input 400. Keep broken Project references at 404. Existing DashboardReferences only performs workspace-scoped Category lookup; it cannot prove deletion versus an invalid historical row. Treat unresolved stored IDs as unavailable without looking up another workspace or revealing existence. Do not introduce deletion history or provenance infrastructure. Preserve known unresolved legacy references during migration, flag invalid input in preflight, and never fetch foreign data. Freeze this policy in Session 1.

Example Dashboard response:

```json
{
  "id": "home",
  "schemaVersion": 3,
  "initialized": true,
  "layoutRevision": 7,
  "placements": [
    { "id": "<placement-uuid>", "widgetId": "<widget-uuid>", "size": "wide" }
  ],
  "widgets": [
    {
      "id": "<widget-uuid>", "type": "board", "title": "Project tasks",
      "configVersion": 1, "revision": 4,
      "config": { "selection": { "kind": "all" }, "limit": 10 },
      "referenceState": "valid",
      "createdAt": "2026-09-16T00:00:00Z",
      "updatedAt": "2026-09-16T00:00:00Z"
    }
  ]
}
```

placements defines order. widgets includes each referenced config once, avoiding a config request per placement. Read both in one repeatable-read snapshot. PUT accepts only schemaVersion/layoutRevision/placements; reject response-only widgets.

Layout PUT placement items contain only `widgetId` and `size`; placement `id` is response-only and server-generated. With at most one placement per Widget, widgetId already identifies each optimistic draft item. Match existing placements by widgetId to retain their IDs across reorder/resize; a newly attached Widget receives a random server UUID. Removing and later reattaching creates a new placement identity. A lost response is resolved by current-state GET and layoutRevision comparison, not unconditional PUT replay. Client-generated IDs add collision/input complexity without an additional draft identity benefit here. This runtime policy is separate from deterministic migration backfill identities.

### Explicit initialization

Unsaved GET returns initialized=false, layoutRevision=0, placements=[], widgets=[] without writes. The frontend calls initialization during explicit setup. This replaces virtual six-widget GET behavior and avoids data requests for nonexistent persisted Widgets.

Keep both initialization routes: explicit defaults replace the old virtual defaults, while first layout PUT preserves the existing ability to save an intentional empty layout. Neither route is a reset.

| Starting state / operation | Result | Rows and revisions |
| --- | --- | --- |
| Unsaved / initialization POST | 201, initialized=true, Location=/api/v3/dashboards/home | Create 6 Widgets at revision 1 and 6 Placements; layoutRevision 0 -> 1. |
| Unsaved / empty PUT with layoutRevision=0 | 200, initialized=true | Create a layout at revision 1; create 0 Widgets and 0 Placements. Existing unplaced Widgets remain untouched. |
| Unsaved / valid nonempty PUT with layoutRevision=0 | 200, initialized=true | Attach existing owned Widgets; create placements and layout revision 1, not new Widgets. |
| Existing layout, including saved-empty / new initialization operation | 409 REVISION_CONFLICT | No reset, rows, or counter changes. No separate ALREADY_INITIALIZED code is needed. |
| Existing layout / PUT with layoutRevision=0 | 409 REVISION_CONFLICT | Preserve current layout. |
| Migrated saved-empty layout / GET | 200, initialized=true, empty placements/widgets | Preserve its prior positive revision, not 0; defaults are not recreated. |

Every successful fresh command increments workspace dataRevision exactly once, including the six-Widget initialization; failure increments nothing. POST/PUT races have one fresh winner. Check a valid unexpired replay before the create-if-absent conflict, as detailed below.

### Creation replay and deletion

Follow the historical-operation behavior verified in CategoryCommandService/CategoryCommandTests and JournalCommandService/JournalApiTests. This is not universal repository behavior: Task create also requires the original row to exist. Choose the Category/Journal behavior for Widgets rather than changing Task semantics.

- Follow existing adapters: capture the first successful command's Clock value as createdAt and set expiresAt=createdAt+24 hours in the same transaction (not a separately measured DB commit timestamp). Do not extend expiry on replay; expiresAt<=now is expired. Namespace by workspace, POST, and the canonical operation path. Widget creation and Dashboard initialization use distinct namespaces. A new operation after expiry follows normal creation/initialization checks.
- Use versioned SHA-256 request fingerprints with explicit field order and UTF-8 length framing, following existing RequestHash classes. Preserve raw value and omission distinctions; freeze nested config field traversal and hash test vectors per type in Session 1. Do not hash current reference state or JSON object key order.
- Authenticate and validate static input, then under workspace locking resolve unexpired replay/hash before live resource/reference checks. Same key/different fingerprint returns 409 IDEMPOTENCY_KEY_REUSED even if the dashboard now exists. Same key/body returns original 201/body/Location even if the Widget was edited or hard-deleted, or initialization's placements were later removed. No recreation, counters, or automatic placement occurs.
- Replay storage must survive Widget deletion; do not cascade-delete it or require a live Widget FK. Replay remains workspace-scoped and subject to current session/account authorization. Do not resolve deleted business references solely to enrich the historical snapshot.
- Omit X-Workspace-Data-Revision on historical replay and keep no-store. The frontend must GET current state before treating replay as current; a deleted Widget returns 404, and an initialization GET may return a subsequently edited layout.
- Existing adapters are resource-specific and store status/body, not a general response-header archive; WorkspaceResponses does not currently set Location. Add only the scoped Widget/initialization persistence and response support needed to preserve the proposed original Location and body. Do not claim the infrastructure can be reused unchanged or refactor unrelated replays.

### Display data response

Example envelope; inner board data is illustrative until its complete schema is frozen in Session 1:

```json
{
  "widgetId": "<widget-uuid>",
  "type": "board",
  "configRevision": 4,
  "configVersion": 1,
  "payloadVersion": 1,
  "availability": "empty",
  "freshness": "current",
  "readAt": "2026-09-16T00:00:00Z",
  "sourceObservedAt": null,
  "lastSuccessfulSyncAt": null,
  "data": { "kind": "board", "columns": [], "statistics": {} },
  "page": null,
  "problem": null
}
```

- Define complete typed DTOs with OpenAPI oneOf/discriminator. Arbitrary Map/object is not the final contract.
- Only the following combinations are allowed. Browser loading is a client state, not an envelope value. `problem` is always present as an object or null, not omitted.

| availability | freshness | data | problem |
| --- | --- | --- | --- |
| ready | current | Required typed payload | null |
| ready | stale | Required typed payload | null or safe problem |
| empty | current | Required typed empty payload | null |
| empty | stale | Required last-known typed empty payload | null or safe problem |
| unavailable | unknown | null; page must also be null | Required safe problem |

- Reject all other combinations. `empty` means successful absence under that type's query, never failure: lists retain their typed empty items/columns and statistics retain their defined zero buckets. A measured zero value alone does not make a metric unavailable or necessarily empty. Define the type-specific empty predicate in Session 1.
- Stale content can coexist with a source problem, including a last-known empty result. A problem is allowed only with stale content or unavailable. It contains safe code/retryable fields, not provider details. Existing local handlers return current ready/empty, or defined unavailability; stale rows reserve response semantics only and do not authorize caching or future integration work.
- readAt is application read time; sourceObservedAt is source observation time; lastSuccessfulSyncAt is external sync time. Use null when absent/inapplicable. Do not fabricate sync times for local DB queries.
- current means current under the handler's contract, not guaranteed latest external state or atomicity across external systems.
- deploy returns unavailable/unknown with data=null and problem={code:NOT_CONFIGURED,retryable:false}, not fabricated health.
- Keep normal 401/403/404/400/409 and unexpected 5xx errors outside the envelope. Only defined data unavailability or stale-data diagnostics use a successful envelope problem; do not swallow unexpected database failures.
- configRevision identifies the exact configuration used. Read local config/data in one repeatable-read snapshot; never attach a newer revision afterward.
- Future external handlers keep I/O outside DB transactions and retain the configuration revision used. This is an extension contract, not external implementation now.
- X-Workspace-Data-Revision is the observed DB snapshot number, distinct from config/layout/payload versions and source times. Preserve no-store and atomic workspace counter updates.
- Frontend rejects data for older configs and restarts pagination after config changes. Widget cursors bind workspace, Widget, configRevision, type, and normalized conditions; do not change unrelated business cursors or pass them through without Widget binding.
- Catalog is not a workspace-data observation and invents no revision header. Config/data GET and fresh mutations follow observed-revision conventions; historical replay omits the header.

## 5. Existing Type Mapping

| Type | Configuration and handler policy |
| --- | --- |
| overview | Preserve selection/limit omission. Reuse Overview aggregate filtering and asOf semantics. |
| board | Preserve selection/limit. Reuse Task queries/statistics, with the documented limit across all columns and independent statistics. |
| journal | Preserve selection/limit and existing ordering/pagination. |
| milestone | Preserve selection/limit and existing status/time/order semantics. |
| links | Preserve config and prohibition on limit. Reuse nullable projectId and Project-derived Category filters; all includes unlinked Links, uncategorized includes unlinked/uncategorized-Project Links. Preserve position/id order and existing collection limits. Confirm frontend composition without inventing a direct Category relation. |
| deploy | Preserve selection=all and no limit. Return unavailable without external calls. |

Stored selector shapes and supported business filters are not automatically equivalent. Audit each type in Session 1 and resolve gaps rather than guessing filtering behavior.

## 6. Concurrency, Authorization, and Lifecycle

- Lock order: workspace, dashboard when necessary, Widgets by UUID, then placements. Config-only updates must not take unnecessary dashboard locks or change layoutRevision. Preserve existing Category writer order and PersonalWorkspaceJpaRepository's PESSIMISTIC_WRITE lock. Different Widgets in one workspace can both pass their independent revisions while physically waiting on that same lock; this is not lock-free concurrency. Finer lock granularity is outside scope absent separate evidence and approval.
- Concurrent attach/delete must not create dangling references. Complement checks with FKs/uniqueness. Test reordering with safe atomic replacement or deferred position constraints.
- Creation followed by placement is two operations. If layout PUT conflicts, the Widget remains unplaced and discoverable. Removal/deletion may also partially succeed; report this explicitly. Do not silently delete across operations.
- Catalog/config/data GET never writes. Registry schemas contain no secrets, private connection details, or implementation types.
- Existing arrays have no explicit count cap. Do not introduce a limit that truncates saved data. Measure growth before agreeing on limits or bulk retrieval thresholds.
- Source connections/credentials belong to source features. Do not build a speculative generic connection table, scheduler, metrics/log store, batch endpoint, or event bus.

## 7. Expected Files and Dependencies

| Area | Expected changes |
| --- | --- |
| `src/main/java/com/kopite/devspace/widget/` | Domain/configuration, command/query services, registry, controllers/DTOs/OpenAPI, persistence, existing-feature adapters. |
| `.../dashboard/domain/` | Layout-focused HomeDashboard and Placement; replace embedded configuration responsibilities. |
| `.../dashboard/application/`, `infrastructure/`, `presentation/` | Layout API, initialization, batch configuration reads, legacy retirement. |
| Existing response/error/replay components | Reuse conventions; add only required errors and operation scopes. |
| `src/main/resources/db/migration/` | Ordered schema/mapping/backfill changes; applied history unchanged. |
| `build.gradle` stage handling | Verify categoryStage safety; change only if required. No new dependencies assumed. |
| `src/test/java/`, `src/test/browser/` | Migration/domain/HTTP/concurrency/OpenAPI/regression tests without real integrations. |
| Contract/review documents | API examples, frontend handoff, evidence, cutover/rollback runbook during implementation. No shared guide changes. |

## Execution Sessions

Execute in order after explicit approval. Complete implementation and validation before advancing. Check only completed work. Production operations and frontend implementation are not implicit parts of execution approval.

### Session 1: Contracts and Migration Preconditions

Objective: Act as the contract gate before persisted configuration, migration, or API implementation.

Implementation:

- [x] After approval, update PLAN/Decision to active/accepted and register the accepted Decision under repository rules.
- [x] Inspect source/tests, available frontend evidence, categoryStage/V17 prerequisites, migration numbers, and replay requirements. Separate operational unknowns from fixtures.
- [x] Freeze complete schemas and empty predicates, links composition, board limit/statistics semantics, optimistic revision transport, placement identity, initialization/replay precedence, envelope matrix, and missing-Category data behavior.
- [x] Freeze deterministic migration algorithm/namespaces/encoding/test vectors, mapping reconciliation, and invalid/unsupported-data stop conditions.
- [x] Freeze categoryStage/V17 artifact prerequisites, scoped replay storage/24-hour expiry/operation paths/hash vectors/original Location, and legacy endpoint retirement.

Validation:

- [x] Capture Dashboard/query behavior in isolation; do not rely on deleted PLAN-0012 evidence.
- [x] Resolve links/board semantics and stage contradictions. Record frontend/operational prerequisites without marking them verified.
- [x] **No unresolved contract ambiguity remains that would alter persisted Widget configuration, migration mapping, or public API shape.** Any unresolved item above blocks Session 2. Production state may remain an explicit cutover blocker only when it cannot change the local design; otherwise it also blocks this gate. Do not mark fixture evidence as operational verification.

Execution record (2026-09-16): Session 1 contract gate completed. The [frozen contract](../widget-api-contract.md) defines complete payload/configuration shapes, effective limits, reference/initialization/replay semantics, canonical hash framing and known-answer vector, deterministic migration identities and vectors, and final-only prerequisites. Read-only frontend evidence confirmed global board limits and 20/3/2 defaults. Existing isolated baseline: 26 passing tests; final build/migration prerequisites: 8 passing tests, recorded under [Session 1 evidence](../reviews/plan0013/session-1.md). No unresolved local contract ambiguity remains; production data, drain completion and frontend readiness remain operational prerequisites, not verified facts.

### Session 2: Models, Registry, and Persistence

Objective: Separate configuration and placement while preserving user data.

Implementation:

- [x] Implement models, revisions, codecs, registry validation, and repositories.
- [x] Add deterministic mapping/backfill preserving unsaved, saved-empty, repeated-type, and missing-Category cases; retain legacy JSONB and mappings.
- [x] Implement composite references, deletion constraints, safe reordering, and overflow handling.

Validation:

- [x] Validate empty DB and populated V17 upgrades; compare IDs/order/config/omission/timestamps and old checksums.
- [x] Inject a full rollback after mapping/row writes, retry, and verify identical IDs and exactly-once committed counter effects; test namespace/encoding edge cases and conflicting preexisting mappings.
- [x] Reject bridge/missing/invalid build selection, verify final migration packaging, and reject unsafe V16-to-Widget migration order.
- [x] Test invalid config, duplicate registration, tenant violations, dangling references, uniqueness, and rollback.

Execution evidence: WidgetMigrationTests (6), WidgetModelTests (2), WidgetPersistenceTests (1), and CategoryCommandTests (7) passed on isolated PostgreSQL 16.4 / Java 21. Full V18 rollback/retry, reordered arrays, Unicode/delimiter IDs, namespace vectors, all six types, saved-empty/absent layouts, missing Category, invalid Project, conflicting preexisting mapping, checksums, composite FKs, JPA JSON/reorder/rollback and overflow were checked. The initial checksum assertion incorrectly included Flyway schema-creation metadata; fixed the assertion. A Category test fixture still inserted the V17-removed scope column; updated that fixture without changing business behavior.

### Session 3: Widget Commands and Layout API

Objective: Make configuration and layout independently editable and conflict-safe.

Implementation:

- [x] Implement Widget CRUD/list/catalog, layout GET/PUT, initialization, and creation replay.
- [x] Implement atomic defaults, unplaced listing, referenced-deletion conflicts, and legacy retirement.
- [x] Publish schemas/examples/security/errors/revisions in OpenAPI.

Validation:

- [x] Test authentication/CSRF/ownership, strict input, expected revisions, overflow, and same-value writes.
- [x] Test same-Widget conflicts, independent Widget edits, layout/config separation, attach/delete, every initialization-table transition and replay precedence, and Category deletion races.
- [x] Test create/delete/replay: original 201/body/Location, no resurrection/counter change, current GET404; initialization replay after layout edits/removals; changed hash, expiry, and workspace isolation.
- [x] Verify Widget/Placement/replay/counter rollback and prevent old PUT from overwriting new state.

Execution evidence: WidgetApiTests (8) and WidgetConcurrencyTests (4) passed in isolation. Covered strict nested input, CSRF/auth/tenant checks, independent and conflicting revisions, layout identity, explicit empty/default initialization, historical replay after deletion/expiry, attach-delete and Category races, full replay/Widget/placement/counter rollback, v2 retirement, cursor binding and generated OpenAPI. OpenAPI initially lacked explicit 200 declarations; added and verified them before advancing.

### Session 4: Typed Data and Frontend Contract

Objective: Expose existing content through explicit metadata and typed payloads.

Implementation:

- [x] Implement query adapters, unavailable deploy, type-aware cursors, and reference/freshness semantics.
- [x] Preserve snapshot pairing and document fetch/edit/placement sequences.
- [x] Register a test-only type; do not implement external integrations.

Validation:

- [x] Test every allowed envelope row and reject invalid combinations using local/test-only handlers. Test retained missing Category data200/REFERENCE_MISSING versus unauthorized input404, no fallback broadening, unchanged GET revisions, delayed responses, and configuration-bound cursors.
- [x] Verify no configuration N+1, semantic equivalence, read-only behavior, no-store, and revision pairing.
- [x] Verify types need no Dashboard model/storage edits; frontend forms/renderers still require support.

Execution evidence: WidgetDataTests (9), WidgetExtensionTests (1), and WidgetApiTests (8) passed. All six local types, typed empty/unavailable payloads, global board budget/statistics, 20/3/2 defaults, Project name/Category propagation, missing-reference non-broadening, invalid cursor binding, delayed-read RR pairing, safe unexpected 500, constant configuration lookup count, envelope matrix and a Spring-registered test-only type were verified. Frontend fetch/edit/placement/replay sequences are in the linked contract; no frontend changes.

### Session 5: Integration Validation and Handoff

Objective: Validate migration/retirement and deliver a usable frontend specification.

Implementation:

- [x] Rehearse migration/recovery with populated fixtures and inspect stage-specific builds.
- [x] Document frontend readiness, legacy writer shutdown, smoke checks, backup/recovery, and cutover.
- [x] Record evidence/operational unknowns without adding unapproved infrastructure.

Validation:

- [x] Run targeted tests and Dashboard/Category/auth/replay regressions; run `./gradlew.bat test -PcategoryStage=final` and required `check`/`bootJar` checks with explicit final selection in isolation. Historical bridge checks use the preserved pre-Widget source/artifact.
- [x] Validate `/v3/api-docs` and Swagger UI with existing local tooling; record limitations and alternative evidence where necessary.
- [x] Compare p50/p95, SQL counts, configuration lookup growth, and migration duration. For simultaneous edits to different Widgets, compare same-workspace and different-workspace runs and measure workspace lock wait, transaction/connection occupancy, pool wait, throughput, and latency. Verify both valid independent revisions succeed, without claiming physical parallelism. Derive budgets from observations, not invented improvements.

Execution evidence: Session 5 passed 269 full-suite tests, followed by 15 final affected checks and a successful bootJar. Real Swagger UI execution and browser lifecycle/replay checks passed. V18 was rehearsed with 100 workspaces/600 Widgets, including transactional rollback and deterministic retry. Configuration SQL stayed constant at 7 for 6/60 placements (legacy 6); no speedup is claimed. Same-workspace lock serialization was measured and retained. See [complete results, performance limits and operational unknowns](../reviews/plan0013/session-5.md), [HTTP examples](../reviews/plan0013/http-examples.json), and [cutover/recovery](../widget-rollout.md).

## Final Validation

- [x] All required sessions/checks pass without failures caused by this plan.
- [x] Preserve data, mapping, order, repeated types, omission, empty layouts, and missing Category references.
- [x] Verify independent revisions, atomic counters, isolation, replay, concurrency, and rollback.
- [x] Existing types meet approved contracts; unavailable capabilities are not presented as operational.
- [x] Test-only extension requires no Dashboard storage change or external infrastructure.

### API Validation

- [x] Endpoints and retirement behavior appear in `/v3/api-docs`.
- [x] Config/payload versions, discriminators, strict requests, nullable times, typed page/problem fields match implementation.
- [x] Verify POST201, GET/PUT200, DELETE200, initialization201, and 400/401/403/404/409/410/5xx.
- [x] Bean Validation, CSRF, Idempotency-Key, Location, no-store, and revision headers match real responses and Swagger UI.

### Frontend Handoff

- [x] Explain schema/ID mapping and new config/layout/data endpoints.
- [x] Explain initialization, saved-empty state, and creation/placement or removal/deletion partial successes.
- [x] Explain revision transport, independent conflicts, initialization transitions, and current-state GET after historical replay, including a deleted resource's 404.
- [x] Provide schemas, complete examples, renderer requirements, configuration batching, and paging rules.
- [x] Demonstrate delayed-response rejection, the envelope matrix, response-only valid/missingCategory, retained missing Category data200/REFERENCE_MISSING versus broken Project404, server-generated placement IDs correlated by widgetId, null times, placeholders, and per-resource API versioning.
- [x] Distinguish refresh from collection; do not promise SSE, external collection, or cache TTL behavior.
- [x] Distinguish backend readiness from frontend completion and production cutover.

## Rollback and Cutover

Production operations require separate approval. The following are rollout phases, not three mandatory deployments:

| Phase | Order and boundary |
| --- | --- |
| Expand | After the V17 prerequisite, add Widget/Placement/mapping and scoped replay storage while retaining legacy JSONB. Do not enable new writers. Test additive compatibility rather than assume it. |
| Backfill / Switch | Stop and drain legacy writers **before** reading the conversion snapshot; backup/preflight, deterministic backfill, reconciliation, and new constraints follow. Then enable verified new APIs and a compatible frontend. No old/new concurrent writers. |
| Contract | Removing legacy JSONB/mappings is deferred to a separately approved follow-up migration/plan after recovery and retention requirements are settled. It is not a completion requirement here. |

If the artifact packages Expand and Backfill together, stop writers before startup-triggered Flyway runs either phase; never rely on enabling/disabling HTTP endpoints alone to stop another old instance.

Before new writes, recovery may use preserved JSONB/mappings and backups through a rehearsed procedure. Verify old-app compatibility with changed Flyway history; binary downgrade is not guaranteed. Never delete history, repair checksums, or edit applied migrations to force rollback.

After new writes, old JSONB is stale. Unplaced Widgets and new configs may not fit the old model. Prefer a forward fix. Returning requires stopping writes, exporting differences, assessing conversion/loss, and explicit approval for destructive restoration. Never silently discard new data. Measure recovery time and volume limits.

## Unknowns and Stop Conditions

- Production categoryStage/V17, replay expiry, and volume are unknown. Do not mark operational readiness complete without evidence.
- Backend and read-only frontend Link/board composition were checked in Session 1; adopted payload and empty semantics are frozen in the linked contract. Frontend adoption is still required.
- Deterministic UUID v3 names and replay canonical hash framing/vectors were frozen in Session 1. Mapping rollback/retry was validated in Session 2; dedicated replay storage preserves original body/Location independently of live resources.
- Stored unresolved Category references have no deletion provenance. Preserve non-disclosure and unavailable semantics rather than claim proof that the Category was deleted.
- Response redesign and implementation of the planned initialization, unplaced lifecycle, deletion conflicts, retirement, and final-only build policy are authorized. Keep JPA persistence and the preserved bridge maintenance boundary; no new architecture is inferred.
- Shared placement, public access, batch data, SSE, and monitoring storage are deferred and do not block initial boundaries.
- Leave unverifiable checks incomplete, record limitations/alternative evidence, and report necessary scope changes before implementing them.

## Completion

Mark completed only after required sessions/checks succeed and no blocker prevents the Goal. Frontend and deployment are excluded; backend completion is not successful production cutover.

Append to plans README only on completion. Accept/register the Decision only after explicit approval. Shared guide changes require separate approval.

Backend implementation and isolated validation completed on 2026-09-16. The accepted Goal/Scope and Widget/configuration versus Dashboard/placement boundary are unchanged. Production V17/drain/volume, backup restoration and frontend rollout readiness remain unverified; no production or frontend completion is implied.
