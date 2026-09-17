# Session 1: Contract gate evidence

Date: 2026-09-16

## Isolated baseline

Java 21.0.10, Spring Boot 4.1.1, PostgreSQL 16.4 Testcontainers, categoryStage=bridge (V1-V16). Test configuration replaces application.yml with `widget-isolated-test.yml`; no .env import, production database, or real OAuth discovery. Gradle init script in this directory selects that configuration.

Command: `./gradlew.bat -Dorg.gradle.java.home="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot" -I docs/reviews/plan0013/isolated-tests.init.gradle test --tests com.kopite.devspace.DashboardCommandTests --tests com.kopite.devspace.CategoryOnlyDashboardApiTests --tests com.kopite.devspace.DashboardCategoryConcurrencyTests --tests com.kopite.devspace.OverviewQueryTests --tests com.kopite.devspace.CategoryCommandTests --no-daemon`

| Suite | Tests | Failures/errors/skips | Suite seconds |
| --- | ---: | --- | ---: |
| CategoryCommandTests | 7 | 0/0/0 | 15.403 |
| CategoryOnlyDashboardApiTests | 2 | 0/0/0 | 4.240 |
| DashboardCategoryConcurrencyTests | 3 | 0/0/0 | 4.031 |
| DashboardCommandTests | 11 | 0/0/0 | 3.511 |
| OverviewQueryTests | 3 | 0/0/0 | 3.383 |

Source: generated `build/test-results/test/TEST-*.xml`, timestamp 2026-09-16T12:19Z. These 26 passing checks establish existing behavior, not new Widget implementation or performance. Suite duration includes setup and is not API latency.

The retained [machine-readable summary](baseline-results.json) includes each source XML SHA-256. Local Markdown links and English-language checks passed; `git diff --check` found no whitespace errors in tracked changes. No full regression or new Widget validation is claimed.

## Review decisions

- Adopt exact computed enum `valid | missingCategory`. DashboardReferences and CategoryOnlyDashboardApiTests establish retained unresolved Category compatibility and broken Project 404. Do not create generic reference-state semantics or deletion provenance.
- Simplify runtime placement IDs to server generation. One placement per Widget makes widgetId sufficient for ordered full replacement and optimistic draft correlation. Preserve existing identity by widgetId on reorder/resize. Backfill remains deterministic and independent of runtime generation.
- Production category stage, replay drain, data volume and frontend readiness are not verified. Never infer them from fixtures. Existing stage packaging requires V17 before new Widget migrations; no migration-history edits or bypassing drain checks.

## Former stop condition: bridge/final artifact policy

Evidence at the initial review, before the approved build-policy change:

- `build.gradle:53` defaults categoryStage to bridge; its resource task includes V17 only for final.
- `Dockerfile:12` also defaults CATEGORY_STAGE to bridge.
- `src/main/resources/application.yml:16` requires Hibernate schema validation; isolated baseline uses the same validation setting and confirms the bridge database contains V1-V16.
- `docs/agents/DATABASE.md` prescribes JPA persistence; `IMPLEMENTATION/DOMAIN.md` uses JPA/domain entities as the default model.
- `docs/project-category-only-rollout.md` retains bridge and final build commands and requires compatible bridge fixes before V17. DECISION-0002 requires stage-specific artifacts.

Inference: normally scanned new JPA entities referencing post-V17 tables cannot validate against the V16 bridge schema. A conditional HTTP controller would not remove those entity mappings. This is a prospective implementation conflict identified from source, not a failure of the current application: all 26 baseline tests passed.

At the initial review the plan had not selected between final-only Widget releases with a separately retained bridge maintenance artifact, and continuing bridge-compatible builds with explicit feature/entity packaging. That uncertainty has now been resolved by explicit user approval of final-only releases. No JDBC substitution, disabled Hibernate validation, or out-of-order migration is required.

The approved policy is recorded in DECISION-0004: preserve the bridge artifact/source for its drain window and require explicit final selection on the Widget source. See [build-policy evidence](build-policy.md). No shared guide change is necessary because this is a release-specific transition, not a new general implementation convention.

Session 1 is now complete after renewed review. The [frozen contract](../../widget-api-contract.md) resolves payload schemas, hash framing/vector, deterministic migration constants/vectors, and remaining lifecycle semantics. Read-only sibling frontend inspection confirmed global board budgeting, defaults 20/3/2 and Project/Category Link filtering. No frontend files changed. Production state remains unverified. Session 2 execution evidence is recorded in PLAN-0013.
