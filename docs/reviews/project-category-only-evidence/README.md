# PLAN-0011 validation evidence

Validated on 2026-09-15 with Java 21 and disposable PostgreSQL 16.4 Testcontainers. No production deployment or live data mutation was performed. The original `current-openapi.json`, database observation and source inventory are the pre-change baseline, not the new contract.

| Evidence | Result |
| --- | --- |
| [Generated normal OpenAPI](final-openapi.json), [scope audit](scope-audit.json) | 41 operations; no classification scope/colorToken/byScope or unity/server enums. Category/auth remain v1; normal resources are v2. Legacy replay routes are independently HTTP-tested and hidden from normal OpenAPI. |
| [Full suite](full-suite-result.json) | `./gradlew.bat test --no-daemon --rerun-tasks`: 236 tests, zero failures/errors/skips, 2m21s. |
| [Clean build](clean-build-result.json) | `./gradlew.bat clean build --no-daemon`: 236 tests, zero failures/errors/skips, 2m22s. Includes separate V17 Spring/Hibernate startup and normal HTTP operations. |
| [Browser result](browser-result.json) | Real headless Chrome, isolated port 18080: 11 visible operations and 13 schema controls; nullable relation, Dashboard Category selector, original v1 retry guidance and workspace header/replay exception visibly rendered. Screenshots inspected. |
| [V1–V14 source checksums](v1-v14-source-checksums.json) | All 14 files byte-identical to the pre-change source backup. |
| [Populated cutover](populated-upgrade.json), [contract upgrade](contract-upgrade.json) | Existing V12 rows, V14 Category assignments, archived/trash data and old replay bytes retained. Explicit Link/widget manifest required; stale/foreign/missing mapping and counter exhaustion roll back. Early V17 and unexpired legacy rows block retirement; future v2 replay rows do not. |
| [Controlled concurrency](clean-build-concurrency.txt) | Three repetitions of both version orders, six Link relation/order scenarios, six Dashboard/save/delete scenarios, and six Category assignment/delete scenarios. PostgreSQL lock waits observed, exact success/conflict/rollback and counters asserted. |
| [Artifact manifest](validation-evidence-sha256.json) | Paths, sizes and SHA-256 for retained session evidence, JUnit XML, build/recovery logs, browser HTML/screenshots, query/EXPLAIN and replay evidence under `.gradle/project-category-only-validation/`. |

The six frozen historical request/hash/raw-response fixtures are in `src/test/resources/category-only/legacy-creations.json` (SHA-256 `166156fb28d4c2ee78ed8752d6960bf1a4ca8d1edfe20ee4d72a5b264c5fd469`). HTTP tests assert byte-for-byte replay, original shape, unchanged expiry/counters and canonical cross-version key conflicts. The migrated replay rows are included in the populated evidence; no historical row was rewritten.

Bridge/final JAR entry lists and replay flags are retained in `.gradle/project-category-only-validation/session7/`. Building final and then bridge without clean was validated: V17 appears only in final, and replay mode matches the artifact. The durable V16 retirement deadline is tested separately from remaining replay-row expiry. Clock passage in contract-upgrade fixtures adjusts only the fixture's cutover metadata; it does not rewrite historical replay expiry or bypass the live migration gate.

SQL-count tests cover 1/10/121 child rows, distinct Link Project references and 2/80 Dashboard references with constant query counts. Repeatable-read tests interleave Project reassignment/Category rename/delete/creation with reads and verify matching body/counter snapshots. Maximum 500-Link response including maximum-length derived Project names stays below 8 MiB. Existing Category index plus resource Project/order indexes are retained; measured evidence is under session4/session5/session6 and the earlier Category query evidence directory.

## Recovery record

- Legacy retired-route catch-all initially intercepted the historical POST path. Split route mappings; all historical byte replays and both controlled version orders pass. Original failure XML retained.
- Observation-copy parameter shadowing initially replaced a resource revision with workspace dataRevision. Renamed the parameter and verified exact child revisions/timestamps and same-snapshot headers; no child bulk update was introduced.
- Old v1 API/OpenAPI fixtures were updated to the accepted breaking v2 contract. Snapshot comparisons now exclude only the nonserialized observation counter, comparing every persisted field including revisions, timestamps and decimals. Actual HTTP bodies, raw replay bytes and replay header omission remain independently asserted. These deterministic comparison failures were not treated as concurrency successes by rerun.
- Historical V1/V3/V5/V7/V9/V11/V12 upgrade tests now target their preserved V14 contract; separate populated V12/V14→V17 tests cover the approved cutover and drain gates. A PostgreSQL boolean scalar expectation was corrected from `true` to JDBC text `t` without changing migration logic.
- Build resource copying now removes only a verified build-directory V17 file when switching final→bridge. Both packaging orders were inspected. OpenAPI customizer generic typing and stale contract assertions were corrected; strict DTO, ownership, CSRF, error and conditional schema assertions remain.

Production Link/widget mappings, backup retention, cutover time and clock margin remain release gates in the [rollout runbook](../../project-category-only-rollout.md). There is no remaining implementation-level blocker. The frontend itself was not modified.

The browser-only `bootTestRun` logs end with a nonzero Java process exit because the tracked validation server was explicitly stopped after the browser passed. This is cleanup, not the result of `test` or `clean build`; both required Gradle validations completed successfully. Only the tracked port-18080 Java processes and isolated Chrome processes were stopped; the pre-existing port-8080 application was left running.
