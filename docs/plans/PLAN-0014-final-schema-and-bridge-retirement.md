# PLAN-0014: Final Schema Consolidation and Bridge Retirement

Status: `active`
Date: 2026-09-17

## Goal

Bring the local Compose PostgreSQL database to the supported final schema and retire the Category transition bridge in the local/repository release workflow. Make ordinary Gradle/IDE/Docker builds independent of categoryStage without weakening Flyway, replay retention, ownership or Widget migration safeguards.

Consolidation means a common schema and runtime policy across environments, not merging database contents, copying production data into development, or moving Render to a personal server. The repository target is currently V18; recheck the migration inventory before execution and review any newer migration before changing the target.

## Scope

- Inventory the local Compose database, its application instances and local release settings. On 2026-09-17 the user explicitly excluded Render inspection from this execution. Render remains active and unverified, not decommissioned or certified by local results; no Render access, migration, configuration change or deployment is included.
- Rehearse existing V16/V17/V18 transitions and recovery on isolated databases, then execute only separately approved environment operations.
- After the environment gate, remove the temporary stage argument/guard and make final migration packaging unconditional. Retire the configurable legacy HTTP replay path while retaining authenticated 410 tombstones and historical data compatibility needed by normal APIs.
- Preserve PostgreSQL data, existing migration contents/names/versions/checksums, canonical idempotency namespaces, stored response bytes, expiry/hash behavior, no-store, CSRF, current APIs and Widget/Dashboard boundaries.
- Exclude data merging, DB resets, destructive legacy JSONB/mapping/replay cleanup, shortened replay windows, new integrations, dependencies, infrastructure, frontend implementation and shared agent-guide changes.
- Execution was approved on 2026-09-17 for repository work and isolated validation within this plan. This does not itself authorize production access, backup/export, migration, deployment or database-specific mapping choices. Obtain explicit environment/action authorization after a concrete execution package is ready; reuse authorization already supplied for that exact scope.

## Repository Evidence and Current Failure

| Evidence | Confirmed repository behavior |
| --- | --- |
| [build.gradle](../../build.gradle) | The original processResources guard blocked IDE classes. The bounded compilation fix below moves it to packaging/test/bootRun. The guard checks an argument, not DB state. |
| [Dockerfile](../../Dockerfile) | Requires CATEGORY_STAGE=final before bootJar. Current Widget source cannot produce bridge artifacts. |
| [application.yml](../../src/main/resources/application.yml) | Imports the ignored local .env and generated category-transition.properties; Hibernate uses validate. Default startup runs packaged Flyway migrations. |
| [V16](../../src/main/migration-stages/cutover/V16__category_only_cutover.sql) | Requires reviewed mapping/preconditions for existing data and records a durable cutover deadline. A populated cutover requires at least 24 hours plus the stored clock margin and applicable legacy expiry. |
| [V17](../../src/main/migration-stages/contract/V17__retire_classification_scope.sql) | Refuses retirement before category_cutover_state.retire_after or the latest scope-bearing historical replay expiry plus margin. Removes old classification columns, not replay history. |
| [V18](../../src/main/resources/db/migration/V18__independent_widgets_and_placements.sql) | Requires V17, preserves legacy JSONB/mapping, performs deterministic Widget backfill and advances workspace counters transactionally. |
| [LegacyReplayController](../../src/main/java/com/kopite/devspace/compatibility/presentation/LegacyReplayController.java) | The packaged flag is false, but the controller still has an overridable enabled branch and a true fallback. Removing the generated property alone could re-enable replay. |
| [Existing build evidence](../reviews/plan0013/build-policy.md), [Widget validation](../reviews/plan0013/session-5.md) | Isolated final migration/build checks passed; actual environment migration and frontend readiness were not verified. |

Follow [the migration guide](../agents/FLYWAY.md), [Category rollout](../project-category-only-rollout.md), [Widget rollout](../widget-rollout.md), [DECISION-0002](../decisions/DECISION-0002-project-category-only-classification.md) and [DECISION-0004](../decisions/DECISION-0004-independent-widget-and-dashboard-placement.md). This proposes the retirement phase of the existing transition, not a new architecture. On approval, update only the release-policy wording of DECISION-0004 to reflect unconditional final packaging after the gate; do not supersede its Widget architecture or erase historical evidence. No Decision or shared-guide change is made by this proposal.

## Target Design and Gates

### Environment inventory

Create an approved inventory using non-secret environment aliases. For each database record: purpose/owner, exact DB and schema identity in protected operational records, active writers and restart paths, deployed artifact/source digest, Flyway versions/checksums/success, relevant schema shape, cutover deadline/margin, legacy replay expiry, data volume, mapping requirements, backup/restore readiness and compatible frontend/API usage. Inventory CI and developer run configurations too.

Do not log connection passwords, tokens, raw production response bodies or production mapping manifests in the repository. Prefer authorized read-only metadata/count queries and protected evidence references. Do not start an application against an unknown database merely to discover its version: startup can migrate it. A schema version alone is insufficient; verify checksums, actual constraints, replay state and deployed writers. A database still needed for recovery is an explicit restore case, not silently omitted from the inventory.

| Observed state | Required route |
| --- | --- |
| Fresh and proven empty | Rehearse V1-V18 with no starter data or fabricated legacy window. Never classify an existing user DB as disposable without approval. |
| Earlier than V16 | Validate the history and supported upgrade path, prepare approved V16 mappings on a consistent snapshot, drain old writers, and use preserved stage-compatible tooling/artifacts. Do not point the Widget application at an unprepared DB. |
| V16, drain incomplete | Keep the already-approved compatible bridge available for its required replay window. Read the durable DB deadline and applicable latest legacy expiry; elapsed wall time or an empty replay table alone does not satisfy the gate. |
| V16, drain complete | Recheck all writers and replay preconditions under the planned maintenance boundary; apply unchanged V17, then V18 with reconciliation. |
| V17 | Verify history/shape and Widget preflight, stop legacy Dashboard writers, then apply V18. |
| V18 | Validate only; do not repeat backfill or increment counters again. Confirm the artifact and clients actually use final contracts. |
| Divergent/failed history, unexpected later migration, unidentified writer | Stop that environment's transition and investigate. No repair, baseline, out-of-order migration or guessed mapping. |

For V16 use the existing reviewed manifest mechanism, including expectedDataRevision, Dashboard revision/JSONB digest and Link mappings. Identity must not be inferred from names, URLs or retired scope labels. Session-local Flyway initialization uses the existing indexed spring.flyway.init-sqls mechanism; no new environment variable is required. Remove one-time settings only after their successful transition and evidence capture. Production values remain protected.

### Retirement gate

Before enabling the simplified release locally, every in-scope inventory row must be either (a) verified at the approved final schema with compatible writers/clients, or (b) explicitly decommissioned with user-approved data retention and restart prevention. An offline old database is not automatically decommissioned. Required bridge replay windows must have elapsed, pending client creates must be resolved according to their original contract, and old instances/jobs/rollback automation must not resume writes. For local execution, investigation and isolated tests need no application downtime. Restrict old writes/restarts only across actual migration and backend replacement; stop a running backend if present, not the IDE itself. A zero-connection observation is not a restart fence. Frontend readiness is a client enablement prerequisite: keep old clients from the migrated Dashboard until adapted, and report the dependency rather than implement it here.

An additional local writer or in-scope old DB reopens the gate. Render does not block local work under the revised scope, but a future Render release must independently verify its history, drain deadline, writers, backup and frontend compatibility using the same admission procedure. Local rehearsal proves migration behavior, not remote state. Preserve immutable pre-Widget source/artifacts and migration instructions as recovery evidence; ending active bridge service/support does not mean deleting the only recovery path. Future restores of old backups require an isolated, stage-aware upgrade before admission to the normal final runtime.

### Build and runtime simplification

- Remove categoryStage and validateCategoryStage from Gradle and CATEGORY_STAGE from Docker. Do not replace them with a default final flag or an IDE-only exception. Normal help/classes/test/check/bootJar/bootRun and Docker builds use one supported schema policy.
- Keep V16/V17 files in their existing staged source directories and copy them unconditionally to db/migration. Their current packaging is already unconditional after the guard; directory relocation offers no required benefit and risks history/duplicate-resource mistakes. V1-V18 must occur exactly once in the artifact.
- Replace generated category-transition.properties with removal of both its configuration imports and its consumers in the same change. The final runtime must have no switch that re-enables legacy HTTP replay. Update isolated test configuration too; verify both incremental builds with stale generated output and clean builds.
- Retain the final 410 behavior, request binding and authentication/CSRF/disabled-account/error precedence of retired routes. Do not accidentally retire auth, project-category or new Widget v1 APIs. Keep the separate Dashboard v2 tombstone.
- Remove the enabled HTTP replay branch and only demonstrably exclusive, unreachable bridge code after dependency review. Preserve historical snapshot readers still needed by retained replay rows, normal v2 key-conflict checks and canonical /api/v1/... persistence namespaces. Do not bulk-delete code or data because its name contains legacy or scope.
- No new SQL migration is expected: this plan applies existing migrations and simplifies packaging/runtime. Any newly discovered data repair or schema requirement is a stop-and-review item, not permission to edit V16-V18 or invent V19.

## Expected Changes

| Area | Planned change |
| --- | --- |
| build.gradle, Dockerfile | Remove stage selection, retain deterministic final resource packaging and normal Java 21 build behavior. |
| src/main/resources/application.yml; src/test/resources/widget-isolated-test.yml | Remove the obsolete generated property import with its runtime consumers. Preserve .env handling, DB credentials and Hibernate validate. |
| compatibility/presentation/LegacyReplayController.java and proven bridge-only collaborators | Make retirement unconditional; preserve tombstones and shared replay semantics. Exact removals follow dependency analysis. |
| CategoryOnlyReplayApiTests, ProjectLegacyReplayTests, FinalCategoryRuntimeTests, CategoryCutoverMigrationTests, WidgetMigrationTests and OpenAPI/security tests | Separate preserved historical evidence from supported final runtime; never disable migration/hash/retention tests to make deletion pass. |
| docker-deployment.md, project-category-only-rollout.md, widget-rollout.md, DECISION-0004 | Publish current no-stage commands, explicit archive-only bridge instructions, restore route and approved retirement evidence. Preserve historical checkpoints. |
| docs/reviews/plan0014/ | Sanitized inventory, rehearsal/build results and protected operational evidence references, created during execution. |
| Existing DBs and deployment configuration | Only explicitly approved transitions and removal of obsolete build/one-time migration settings; no new infrastructure or arbitrary DB recreation. |

## Execution Sessions

Execute in order. Do not advance until the current session's implementation and validation are complete. Check only observed results; retain incomplete operational gates when access or approval is unavailable.

### Interim fix: IDE compilation

Following the repeated IDE classes failure report on 2026-09-17, allow classes/processResources without stage selection and keep explicit-final validation on bootJar, jar, test and bootRun. Resource contents remain final-only and unchanged; no default stage is introduced. This bounded build fix precedes environment convergence because compilation does not connect to a DB. It does not complete Session 4 or authorize DB startup/migration. Native IDE Java launches bypass Gradle bootRun validation and must still follow the database admission procedure; Flyway V17 checks and Hibernate validation remain active. Full guard removal and bridge retirement retain their original gates.

### Session 1: Inventory and Transition Contract

Objective: Freeze the target environments, target version and safe transition route.

Implementation:

- [x] Recheck repository/AGENTS/Decision evidence, migration inventory, build graph, property consumers and bridge-only versus shared replay dependencies.
- [ ] Prepare the environment inventory and non-secret inspection procedure; perform real environment reads only within explicit authorization. Identify any missing access without guessing.
- [ ] Freeze each environment's version route, writer drain, replay deadline, manifest approval, frontend readiness, backup/restore plan, execution owner and restart controls.

Validation:

- [ ] Every in-scope DB and writer is accounted for; unknown targets, divergent history and mapping decisions are explicit blockers.
- [ ] Verify DB admission facts from authorized evidence, not PLAN-0013 fixtures; no unresolved migration or retirement contract remains before Session 2.

Session 1 progress (2026-09-17): [inspection evidence](../reviews/plan0014/session-1.md) records the source/dependency baseline and authorized local read-only results. The user confirmed local Compose and Render PostgreSQL are active targets. Local history is V16 with observed replay deadlines elapsed; this does not establish migration readiness. Render was subsequently excluded by the user; remaining local admission checks, writers, frontend and backup/recovery remain unresolved. Session 2 has not started.

### Session 2: Isolated Upgrade and Recovery Rehearsal

Objective: Produce a reviewable execution package using the current explicit-final artifact and preserved stage-compatible artifacts where necessary.

Implementation:

- [ ] Rehearse fresh, V16-waiting, eligible V16, V17 and V18 routes using synthetic fixtures or a separately authorized protected restore. Do not export production data implicitly.
- [ ] Exercise V16 manifest validation and replay drain, V17 removal, V18 deterministic conversion, and restore/retry boundaries. Preserve artifacts/digests and measure volume/durations without inventing budgets.
- [ ] Prepare each real environment's commands, reviewed manifest reference, backup reference, reconciliation queries, maintenance/restart procedure, smoke checks and abort criteria for final approval.

Validation:

- [ ] Prove V17 rejects early retirement even after expired-row cleanup; do not modify real clocks, deadlines or expiries to pass it.
- [ ] Preserve Category/Project identities, Link assignments, historical replay bytes/hash/expiry, Widget mappings/order/omissions/missing Categories, saved-empty/absent layouts and expected counters.
- [ ] Test failure/retry and determine actual per-migration transaction boundaries: V17 may remain committed if V18 fails. Do not claim the whole upgrade is one transaction.
- [ ] Verify the intended backup/application pair restores in isolation. A transaction rollback test alone is not backup recovery evidence.

### Session 3: Approved Environment Convergence

Objective: Bring the approved active environment inventory to the final schema before retiring transitional controls.

Implementation:

- [ ] Obtain approval for the concrete Session 2 execution package where not already authorized. Stop here while required authorization/access is pending.
- [ ] Execute each approved route with backup, old-writer drain and replay scheduling. Re-read mutable preconditions immediately before execution; leave bridge service available where the retention contract still requires it.
- [ ] Record actual Flyway history/shape, migration results, reconciled data/counters, final artifact/frontend compatibility and confirmed old-writer shutdown per environment.

Validation:

- [ ] All in-scope inventory rows satisfy the retirement gate, or execution remains incomplete. A successful local migration does not imply Render readiness.
- [ ] Authenticated read/write/replay/Widget smoke checks pass under approved operational limits; no production load test is implied.
- [ ] Backups, artifact digests, safe restart configuration and the forward-fix/restore boundary are recorded. No unapplied legacy writer can restart against migrated data.

### Session 4: Remove Transitional Build and Runtime Controls

Objective: Implement one final runtime and ordinary IDE/build commands after convergence.

Implementation:

- [ ] Remove Gradle/Docker stage options and generated-property plumbing; retain unchanged, unconditional migration packaging.
- [ ] Replace conditional legacy replay serving with final-only tombstones. Remove only proven exclusive dependencies; retain shared historical compatibility and existing error contracts.
- [ ] Update tests and release/restore instructions. Propose the limited accepted Decision wording update with this plan's approval; do not change shared guides.

Validation:

- [ ] Without categoryStage, verify help, classes, clean/incremental bootJar, test and check; verify IDE build/run against a disposable final DB. Run bootRun only with explicit isolated configuration, never the developer's unknown .env DB.
- [ ] Build the Docker image without CATEGORY_STAGE and inspect/run it against an isolated final DB; verify Java/schema validation and no legacy-serving override. No push/deployment occurs in this session.
- [ ] Inspect final JAR/image resources: unique V1-V18, unchanged checksums and no obsolete generated property. Verify clean and preexisting build outputs; remove only identified generated files within verified build directories if needed.
- [ ] Authentication, CSRF, no-store, 410, normal idempotency namespaces/conflicts, historical snapshot readability and all Widget/API regressions pass. Historical bridge fixtures remain runnable in their preserved scope, not as a supported final HTTP mode.

### Session 5: Approved Final Release and Closure

Objective: Retire active bridge support across the approved inventory and hand off the simplified workflow.

Implementation:

- [ ] Obtain any outstanding deployment/configuration approval for the reviewed Session 4 artifact and per-environment release package.
- [ ] Deploy only within that authorization, remove obsolete stage/build and one-time migration settings, and verify no bridge-capable service remains active or restartable. Preserve archives and recovery documentation.
- [ ] Update current commands, inventory status, evidence and restore admission procedure; distinguish actual deployments from local build results.

Validation:

- [ ] Confirm normal IDE classes/run no longer fails for missing stage; confirm every approved environment starts with Flyway validation at the final schema and unchanged normal API contracts.
- [ ] Confirm replay serving cannot be re-enabled by the old property, while stored history remains intact. Verify excluded v1 APIs are unaffected.
- [ ] Check document paths/links, accepted release-policy wording and remaining operational limitations. No mandatory environment or release gate remains unchecked.

## Rollback and Stop Conditions

Before a schema transition, use the verified backup and stage-compatible application pair. After V16, old v1 writers are unsafe; after V17, a bridge binary is not a rollback option. If V18 fails after committed V17, retain the verified final-compatible maintenance boundary and retry/fix forward; do not reset Flyway history. After Widget writes, preserved legacy JSONB is stale and cannot silently replace current data.

The simplified build change can be reverted to the previous explicit-final Widget release on a compatible final DB. That does not justify re-enabling bridge or restoring old DB data. Any restore that discards later writes/replays requires separate loss/reconciliation approval. Stop for failed preconditions, backup failure, unidentified writers, checksum mismatch, pending retention, incompatible frontend, unsupported versions or unexpected data changes. Report the concrete blocker; do not shorten the plan's completion criteria to hide it.

## Final Validation

- [ ] Every required session is complete with evidence; all in-scope environments are verified final or explicitly decommissioned.
- [ ] No categoryStage/CATEGORY_STAGE selection is required in current Gradle, IDE, Docker or deployment workflows.
- [ ] No runtime flag can restore legacy HTTP replay service; historical data and required shared readers remain preserved.
- [ ] Flyway history/checksums, Hibernate validate, security, normal idempotency and Widget/API contracts remain intact.
- [ ] Fresh install, populated upgrade, already-final startup, failed transition/retry and approved recovery checks pass.
- [ ] No failures caused by this plan remain; no unapproved data cleanup, frontend work, infrastructure or guide changes occurred.

### API Validation

No new endpoint or normal response contract is planned. Preserve and regress existing final 410 behavior, auth/CSRF/error precedence and normal OpenAPI inventory/schemas/Swagger rendering. Endpoint-addition checklist items are not applicable unless a reviewed scope change introduces one.

## Unknowns and Decisions Required

- Actual DB names/versions/checksums, deployed artifacts, writer count, replay deadlines, frontend readiness and backup capability are unverified. No DB connections were made while writing this plan.
- The user confirmed local Compose and Render PostgreSQL are active, then excluded Render checks. No decommissioning or Render readiness is inferred. Local writer and recovery choices remain to be confirmed.
- Real V16 mappings, protected backup locations, maintenance timing, downtime limits and per-environment operational authorizations must be resolved before execution.
- Restoring a populated older backup can require another staged replay window. Preserve that recovery route even though active bridge support ends.
- If a protected restore, Docker execution or IDE validation cannot be performed, document why and the strongest alternative; do not label the corresponding operational claim verified from unrelated fixtures.

## Completion

Keep proposed until explicit approval, then active during execution. Mark completed only after all applicable session and final checks pass and the local/repository goal is achieved. Retain active status while mandatory local gates remain pending. Render operations are explicitly excluded and must remain reported as unperformed even when this local plan completes. Append this plan to the completed-plan README only on completion. No implementation, environment changes, migration, backup/export or deployment was performed as part of this proposal.
