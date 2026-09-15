# ProjectCategory deployment compatibility

Historical PLAN-0010 reference. The accepted Category-only replacement is [DECISION-0002](decisions/DECISION-0002-project-category-only-classification.md); use the [new staged rollout and frontend handoff](project-category-only-rollout.md) for PLAN-0011. Do not apply this older scope-compatible contract to the v2 artifact.

Implements [DECISION-0001](decisions/DECISION-0001-project-category.md). This is an operator reference, not authorization to deploy. Tests use disposable PostgreSQL databases; the local application database is not a validation fixture.

## Release boundary

1. Preserve a normal database backup and the current artifact. Record applied V1–V12 checksums and existing Project replay counts/expiry without exposing credentials or response data in operational logs.
2. Drain all old application instances before enabling the new contract. Do not route new Category requests or creation retries to an old binary that cannot read new snapshots. Keep the rollout behind the existing deployment traffic boundary; no new feature-flag infrastructure is required.
3. Run the new artifact's Flyway V13/V14 migrations through the normal release process. V13 creates Categories and the nullable ownership-preserving Project FK; V14 adds separate Category creation replay storage. Neither seeds Categories, assigns Projects nor rewrites existing replay data. Do not baseline, repair, or edit old migrations.
4. Before opening traffic, verify schema validation, recorded old checksums, null Category on existing Projects and empty Category collections. Validate generated documentation and perform read-only application health checks under existing authentication controls.
5. Route traffic exclusively to the compatible artifact. Normal Project responses include nullable categoryId; old creation replay may omit it. Consumers must understand both POST 201 shapes before enabling Category editing.

## Replay guarantees

- Omitted categoryId retains the exact seven-field v1 hash, including original raw-value/omission semantics. New omitted requests still produce the new snapshot shape.
- Explicit null/UUID uses v2 and includes raw UUID spelling. Never retry an uncertain request by changing its body while retaining the key.
- Old response bodies, hashes and expiry remain intact. No upgrade cleanup of unexpired replay rows is permitted. Retained expired legacy snapshots must remain readable until normal expiry cleanup.
- Matching retries return their original 201 snapshot without current Category lookup, including after reassignment/clear and subsequent Category deletion. Normal detail/Category reads provide current state.
- Category creation replay uses its own workspace/method/path namespace and survives Category rename/delete. A replay never recreates a deleted Category or increments dataRevision.

## Rollback limitation

Do not roll back application traffic to an incompatible old binary after new response snapshots have been written. Draining traffic and fixing forward with the compatible reader is the default recovery. Database restore or another rollback strategy requires separate operational review; never erase replay rows, remove the new column, or edit the accepted contract to make an old binary work.

## Isolated rehearsal and evidence

`CategoryPersistenceTests` migrates a populated V12 schema through V14 and compares every old column, checksum and expired/unexpired replay body. Its JDBC schema is restored before returning the connection to the pool. `ProjectLegacyReplayTests` proves literal old snapshots return unchanged through HTTP, and new omitted snapshots retain their own shape. `CategoryApiTests` verifies replay after clear/deletion. `CategoryRelationConcurrencyTests` observes PostgreSQL lock waits in both serial orders; `CategoryScopeRegressionTests` checks unchanged derived state/cursors and query counts.

Execution records and commands are in [PLAN-0010](plans/PLAN-0010-project-category.md). Evidence is retained under `.gradle/project-category-validation/` (migration, replay, concurrency, queries, browser, full-suite, clean-build and recovery). Existing test fixtures require `APP_ORIGIN=http://localhost:8080`; set it only in the test process, not by changing production security configuration. This rehearsal does not deploy, mutate runtime business data, or automatically retry business requests against a live service.
