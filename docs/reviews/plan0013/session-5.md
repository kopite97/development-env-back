# Session 5: Integration evidence and handoff

Date: 2026-09-16. Isolated local validation only; no production access, frontend implementation, dependency addition or shared-guide modification.

## Validation results

- Full `test check bootJar`: **269 tests, 65 suites, zero failures/errors/skips**. [Results and XML hashes](session-5-full-results.json).
- Final changes to canonical UUID path validation, operation tags and nullable OpenAPI schemas: **15 affected tests passed**, then `bootJar` succeeded. [Results](session-5-final-targeted-results.json).
- The first full run exposed 28 obsolete expectations/fixtures. Final-schema fixtures no longer insert removed scope columns; historical migration fixtures retain them. Historical bridge replay tests explicitly select their test-only compatibility mode and stop their upgrade at V16, preserving V17's drain rejection. FinalCategoryRuntimeTests verifies actual final retirement separately. Dashboard v2 tests were migrated to Widget/v3 while retaining Overview, ownership, strict input, Category retention/recreation, six-type/size/selection matrix, CSRF and race coverage. No tests were disabled.
- OpenAPI inventory resolves every schema reference and matches **51 documented operations** (49 MVC mappings plus two security-filter operations). Validated response shapes, typed payloads, revision bounds, errors, no-store, CSRF, Idempotency-Key, Location, DELETE query/body rules and retirement. Missing success declarations/revision bounds and overly broad GET error metadata were corrected during validation.
- Configuration ordering is explicitly aligned to placement ordering after batch loading; the reorder regression verifies both arrays. Canonical UUID path aliases are rejected with 400, matching the frozen input contract.
- Final schema inspection found nullable object annotations generating `type:null` alongside an object `$ref`, an impossible OpenAPI 3.1 intersection. Replaced these with explicit object-or-null unions, added payload discriminator mappings and documented nullable business snapshot fields. Added structural regression assertions; this changes documentation, not response JSON.

Commands use Java 21.0.10, Spring Boot 4.1.1, Gradle 9.7.1 and PostgreSQL 16.4 Testcontainers on Windows/Docker Desktop. `widget-isolated-test.yml` **replaces** application.yml, imports only the generated final-mode property, uses fake loopback OAuth endpoints, and never imports `.env`. [Init script](isolated-tests.init.gradle) selects this configuration and one test fork.

```text
./gradlew.bat -Dorg.gradle.java.home="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot" -PcategoryStage=final -I docs/reviews/plan0013/isolated-tests.init.gradle test check bootJar --no-daemon
./gradlew.bat -Dorg.gradle.java.home="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot" -PcategoryStage=final -I docs/reviews/plan0013/isolated-tests.init.gradle test --tests *WidgetInputTests --tests *WidgetApiTests --tests *BackendOpenApiContractTests --tests *DashboardOverviewOpenApiTests bootJar --no-daemon
```

## Descriptive performance comparison

No operating target or improvement percentage is asserted. Fixture: all-selector board configurations, 6 and 60 placements, 10 warmups then 50 sequential MockMvc GETs. Timings include MVC/security/DB/JSON work but exclude TCP, TLS, Render latency and browser rendering. Hibernate prepared-statement counts cover these read queries, not wire round trips or query planner cost. Compare the changed response contracts, not identical payloads.

The pre-Widget source at `a80fec5781943c34adc6457213d4c366009013d1` was exported to an ignored local copy, using bridge mode, its original Dashboard v2 endpoint, and the same isolated test configuration/fixture. [Measurement source](LegacyLayoutMeasurementTests.java), [baseline results](legacy-performance.json). The current measurement is [WidgetPerformanceTests](../../../src/test/java/com/kopite/devspace/WidgetPerformanceTests.java); [standalone results](performance.json) and [full-suite repeat](full-suite-performance.json) are retained to show run variability.

| Contract / run | Count | p50 ms | p95 ms | SQL/request | Response bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Legacy v2 | 6 | 18.107 | 36.606 | 6 | 758 |
| Widget/layout v3, standalone | 6 | 17.531 | 24.752 | 7 | 2,298 |
| Widget/layout v3, full-suite repeat | 6 | 12.258 | 15.640 | 7 | 2,298 |
| Legacy v2 | 60 | 17.628 | 23.032 | 6 | 7,126 |
| Widget/layout v3, standalone | 60 | 18.108 | 26.827 | 7 | 22,116 |
| Widget/layout v3, full-suite repeat | 60 | 12.051 | 18.945 | 7 | 22,116 |

The extra relational lookup and larger independent-resource metadata are real costs. SQL count stays constant as configuration count grows; WidgetDataTests independently verifies batch configuration loading. Small samples, JIT warmth, Docker/host scheduling, stage differences and different payload sizes prevent a causal speedup claim. No new cache, index infrastructure or arbitrary latency budget was added.

Independent updates: two threads, 30 synchronized pairs (60 committed transactions) per case. Every Widget revision advanced from 1 to 31 without a logical conflict. These timings are application transaction calls, **not HTTP update latency**; body timing excludes transaction completion, total timing includes it. Monitoring used a separate PostgreSQL connection, excluded from Hikari occupancy, with nominal 2 ms polling plus query/scheduler delay.

| Full-suite repeat | Same workspace, different Widgets | Different workspaces |
| --- | ---: | ---: |
| Transaction p50 / p95 ms | 8.147 / 13.615 | 5.651 / 6.941 |
| Transaction body p50 / p95 ms | 7.330 / 12.951 | 4.956 / 6.124 |
| Transactions/s in this short run | 175.29 | 342.95 |
| Maximum sampled active pooled connections | 2 | 2 |
| Sampled pooled connection occupancy, connection-ms | 551.43 | 345.53 |
| Workspace lock-wait samples / monitor samples | 10 / 23 | 0 / 12 |
| Maximum sampled pool waiters / accumulated sampled wait ms | 0 / 0 | 0 / 0 |

Standalone transaction p50/p95 were 14.066/27.622 ms (same workspace) and 9.464/12.166 ms (different); corresponding throughputs 100.42/203.33 transactions/s. This demonstrates serialization, not a production throughput capacity. Sampled zero pool wait does not prove no sub-sample wait or behavior under saturation. A controlled holder/waiter test observed PostgreSQL `wait_event_type=Lock` on workspace SQL, held the blocked window for 105.878 ms, and observed the successful waiting transaction finish in 132.576 ms. This is a controlled contention observation, not a per-query lock-duration histogram. No locking redesign was made.

## Migration, recovery and artifact

The populated fixture contains 100 saved workspaces and six types each: 600 Widgets, 600 Placements and 600 mapping rows. V18 Flyway execution took **131.870 ms** after V17. Rolling back the manually executed V18 transaction after all inserts took **1.477 ms**; the original Dashboard rows remained identical and new tables disappeared. Retry reconciled all rows and counters. [Measurements](migration-volume.json). These are small local fixtures, not production migration/backup-restore estimates.

The seven Widget migration tests additionally cover absent/saved-empty layouts, retained missing Categories, broken Projects, overflow, duplicate IDs, preexisting mapping collisions, complete rollback/retry, cross-workspace identical legacy IDs, array reordering, Unicode/delimiter framing, timestamp/omission preservation, UUID vectors, V17 prerequisite and unchanged earlier checksums. Runtime rollback includes Widget/Placement/replay/counter atomicity; create/delete/replay preserves historical results without resurrection.

[Final artifact inspection](final-artifact.json): 18 migrations, V16/V17/V18 each once, generated legacy replay flag false. Preserved bridge evidence is in [build-policy.md](build-policy.md). The final JAR builds; a Docker image build and production artifact deployment were not performed. Docker argument guards were reviewed, not presented as container runtime validation.

## Handoff and remaining operational unknowns

Real headless Chrome on loopback passed [Swagger/browser verification](browser-result.json): ten new operations render, Home GET executes through Swagger's Try it out/Execute controls, and browser session/CSRF requests exercise catalog, create, attach, typed empty data, deletion conflict, remove/delete, historical replay without a revision header, current 404 and retired 410. The browser also checks final nullable object unions and discriminator mapping in the served document. The initial harness used textContent, which concatenated Swagger labels; switching to rendered innerText fixed the harness. No frontend code changed. Local HTML/screenshot evidence is under `build/reports/plan0013/browser/`.

Server command: `./gradlew.bat -PcategoryStage=final bootTestRun --args="--spring.config.location=classpath:widget-isolated-test.yml --server.port=18080 --app.security.origin=http://127.0.0.1:18080 --spring.profiles.active=test" --no-daemon` (same Java 21 override as tests). Browser command: `node src/test/browser/widget-openapi-validation.mjs`, with a dedicated headless Chrome profile and loopback CDP port 19223. The test login filter belongs only to the existing test configuration, not the production JAR.

The [contract](../../widget-api-contract.md) contains fetch/edit/placement/replay sequences, pagination, missing Category versus broken Project behavior, typed availability semantics and versioning. [Complete HTTP examples](http-examples.json) were generated from real isolated API responses for all six types, uninitialized and saved layouts. [Cutover/recovery procedure](../../widget-rollout.md) separates writer drain, transactional backfill/switch and deferred destructive cleanup.

Production V17/replay state, volume/shape, real concurrent traffic, latency objectives, backup restore time and frontend rollout readiness are unverified. Local concurrency uses independent transactions in one application process; production multi-instance behavior and deployment coordination were not exercised. Database locks/FKs enforce the shared invariants, but fixtures are not proof of operational readiness. Actual Google OAuth provider interaction is outside the loopback fixture; existing OIDC/security regression tests pass.

No unresolved persisted configuration, identity/mapping, replay or public API contract gate remains. Operational unknowns remain explicit and require separate production/frontend work. Legacy JSONB/mappings remain intact and no external source integrations, Redis or shared guides were added.

Final document validation found no broken local links or CJK/kana in the English planning/handoff documents. `git diff --check` passed; new source/document files were also checked separately because they are not yet tracked. Original build-policy console logs are retained verbatim, including their whitespace. The dedicated test server and Chrome instance were stopped after browser validation. Terminating the owned test JVM intentionally ends the long-running bootTestRun task with process exit -1; startup and browser checks had already passed. This is separate from the successful test/check/bootJar validation.
