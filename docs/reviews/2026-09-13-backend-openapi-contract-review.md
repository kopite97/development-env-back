# Backend OpenAPI contract review — 2026-09-13

Standalone verification of the implemented backend, against [the implementation specification](../../backend-implementation-spec.md) and completed [PLAN-0003 through PLAN-0009](../plans/README.md). No frontend, business behavior, persistence, dependencies, security policy or approved wire contract is changed by this review.

## Inventory

The implemented application surface is **35 operations on 20 paths**: 33 Spring MVC operations and two Spring Security filter operations. Authentication and `/me` form one logical review group, displayed as two Swagger tags.

| Group | Operations | Paths and methods beneath `/api/v1` |
| --- | ---: | --- |
| Authentication and `/me` | 5 | GET `/auth/login`, GET `/auth/callback/{registrationId}`, GET `/auth/csrf`, POST `/auth/logout`, GET `/me` |
| Project | 4 | GET/POST `/projects`; GET/PATCH `/projects/{id}` |
| Task | 7 | GET/POST `/tasks`; GET/PATCH/DELETE `/tasks/{id}`; POST `/tasks/{id}/restore`; GET `/tasks/stats` |
| Journal | 5 | GET/POST `/journals`; GET/PATCH/DELETE `/journals/{id}` |
| Milestone | 5 | GET/POST `/milestones`; GET/PATCH/DELETE `/milestones/{id}` |
| Link | 6 | GET/POST `/links`; GET/PATCH/DELETE `/links/{id}`; PUT `/links/order` |
| Dashboard | 2 | GET/PUT `/dashboards/home` |
| Overview | 1 | GET `/overview` |

No implemented application operation is missing from the generated document, and no undocumented extra business operation is present. Framework redirects such as `/oauth2/authorization/google`, documentation/static resources, actuator, `/error`, and implicit HEAD/OPTIONS handling are infrastructure, outside this application-operation count.

## Findings and corrections

All implementation edits below are OpenAPI annotations/customizers. Tests exercise the generated document and existing behavior; controller method bodies, validators and service/domain logic are unchanged.

| Finding | Classification | Correction |
| --- | --- | --- |
| `/auth/csrf` incorrectly required a `token` query parameter and exposed the framework `CsrfToken` model | Documentation defect | Hide the injected argument; retain only `CsrfTokenResponse`. Verify authenticated GET succeeds without query input. |
| `/me` and `/auth/csrf` error responses referenced their success DTOs under `*/*`; authentication error coverage was incomplete | Documentation defect | Explicit JSON `ApiError` responses, explicit success JSON schemas, and applicable disabled/invalid-session errors. Login describes its actual two-hop redirect. |
| Required properties were absent from `/me`, Workspace and CSRF response schemas | Documentation defect | Require the actual returned fields and document server-owned IDs, Workspace revision and name constraints. |
| The documented session cookie was hard-coded; callback had no required path parameter; filter operations had no tag/operationId; logout omitted conditional CSRF/anonymous semantics | Documentation defect | Resolve cookie name from the same configuration property as runtime; document callback path/protocol parameters, Authentication tags and operation IDs; document authenticated CSRF and no-session 204 separately. Test a non-default cookie name. |
| Class-level error declarations advertised mutation conflicts on GETs and unrelated error codes on mutations; Link list advertised an inaccessible-item 404; Dashboard GET advertised unsupported schema-version errors | Documentation defect | Declare conflicts and validation descriptions at the applicable operations, keep Link list free of 404/409, distinguish account checks from mutation CSRF, and retain collection-exhaustion conflicts for Link creation. |
| Task, Journal, Milestone and Link path IDs lacked UUID format | Documentation defect | Add UUID schema metadata to the existing string path arguments. |
| Custom nonblank text validators were incompletely represented in schemas; Dashboard parent widget constraints differed from its conditional branches | Documentation defect | Document minimum length for required/nonblank-when-present text, existing Task response length bounds, and matching widget ID/title minima. No validator changes. |
| Returned revisions outside Project lacked consistent read-only metadata | Documentation defect | Mark response item/dashboard/collection revisions read-only; expected revisions in request DTOs remain writable. |
| Task statistics used the generic component name `Counts` | Documentation defect | Rename only the OpenAPI component to `TaskStatusCounts`; JSON still uses `counts` and the same status fields. |
| PLAN-0009 incorrectly claimed the source Overview example had inconsistent arithmetic | Documentation defect | Correct the factual wording: both examples satisfy the active/archived scope sums. The approved counting rules and implementation remain unchanged. |

During validation, springdoc's class/operation response merging overwrote the Dashboard PUT-specific 400 description. The existing Dashboard test detected the lost `UNSUPPORTED_SCHEMA_VERSION` text. Moving validation responses to individual operations fixed this; the full-surface regression now also checks that `INVALID_CURSOR` appears only on cursor lists and `UNSUPPORTED_SCHEMA_VERSION` only on Dashboard PUT. The failed test evidence is retained under `.gradle/backend-openapi-review/recovery/`.

## Contract checks

- **Status and security:** login/callback 302; logout 204 without a body; five resource creations 201; all other successful business operations 200, including deletes, restore and initial Dashboard save. Documented errors reference JSON `ApiError`. Cookie authentication applies to protected operations. Every authenticated mutation requires CSRF; logout alone permits anonymous 204 without a token. An Origin supplied on an authenticated mutation must be allowed.
- **Concurrency inputs:** the five resource creation POSTs require `Idempotency-Key` (1–128 visible ASCII characters, 24-hour replay). Restore and Dashboard save do not. PATCH/restore require body revision; four DELETE operations require query revision. Item revisions start at 1 and are bounded by JavaScript's safe-integer maximum. Dashboard virtual revision and Link collection revision may be 0; request preconditions remain writable despite read-only response revisions.
- **DTOs and ownership:** compare response/request properties and required fields with feature DTOs and completed Plan clarifications. Business DTOs expose no User/Workspace ownership IDs, `dataRevision`, entities, credentials or tokens. `/me` deliberately returns its authenticated User ID and nested Workspace ID. Child `projectId` is an approved relationship; `projectName` and derived scope are response-only. Omitted PATCH fields remain unchanged; explicit null is rejected except the approved nullable Milestone due date. Dashboard optional settings are omitted, never null.
- **Lists:** Project/Task/Journal/Milestone use `{items,total,nextCursor}`, default limit 20, bounds 1–100, nullable cursor and filter-bound cursor semantics. Link is a full ordered collection with global `collectionRevision`, maximum 500 items and null-only `nextCursor`; it has no cursor/limit/project filters. List totals describe the full filter result, not the loaded page.
- **Enums and formats:** review Project scope/status/colorToken, Task status/priority, Milestone completed filtering, Link scope and every widget type/size/scope. Entity IDs and relationship inputs use UUID format. Journal `entryDate` and Milestone `dueDate` are dates; audit timestamps, Task `deletedAt`, statistics/Overview `asOf` are date-times. Due dates and deleted timestamps retain their approved nullability. Defaults are creation/filter defaults, not implicit PATCH updates.
- **Dashboard:** required `schemaVersion=1`, ordered widgets with five required core properties and two optional settings. Six allowed types; every type supports small/medium/wide. Only overview/board/journal/milestone allow owned active or archived `projectId` and limit 1–20. IDs are unique after trimming; repeated types are legal. Referenced projects normalize widget scope to `all`. Unsaved GET returns deterministic defaults at revision 0 without writing; persisted revisions start at 1. First-save and stale-update races retain the existing tested one-winner/one-conflict contract. Saved empty arrays stay empty.
- **Overview:** owned Project validation includes archived Projects, then applies scope/projectId intersection. Project total means active, archived is separate, and both scope keys remain present. Undeleted Task status counts reuse the same aggregation semantics as unsearched Task statistics with `projectStatus=all`. Totals are independent of pages/widgets, and the response uses a consistent read snapshot.

## Specification reconciliation

| Source difference | Classification and disposition |
| --- | --- |
| Raw specification lists GET/PATCH `/me/workspace`, neither implemented | Deferred specification coverage. PLAN-0003 explicitly excludes Workspace editing; no completed Plan delivers these two operations. This is not a missing OpenAPI entry for an implemented endpoint. Implementing them requires a separately approved scope. |
| Raw specification abbreviates callback as `/auth/callback` | Specification inconsistency already resolved by approved PLAN-0003's Spring Security registration path. The implementation and OpenAPI use `/auth/callback/{registrationId}`. No alias was added. |
| Raw specification describes a custom sessions table and absolute expiry | Specification inconsistency already resolved by PLAN-0003: in-process Servlet sessions with idle timeout, no custom absolute-expiry infrastructure. No session implementation change was made. |

No new implementation defect or unresolved discrepancy against the completed Plans' approved API contracts was identified. The original broad specification is not rewritten by this review; the remaining source-document differences above must not be mistaken for newly implemented scope.

## Validation evidence

Final result: **PASS**. Reproducible checks are [BackendOpenApiContractTests](../../src/test/java/com/kopite/devspace/BackendOpenApiContractTests.java), [AuthenticationOpenApiContractTests](../../src/test/java/com/kopite/devspace/AuthenticationOpenApiContractTests.java), existing feature OpenAPI/integration tests, and [the complete Swagger browser harness](../../src/test/browser/backend-openapi-validation.mjs).

| Validation | Final evidence |
| --- | --- |
| Generated `/v3/api-docs` | OpenAPI 3.1.0; 20 paths, 35 operations, 43 schemas. Exact approved inventory matches runtime MVC mappings plus the two filter operations. All local schema references resolve. |
| Live HTTP document | Anonymous GET on the test server returns 200. Its complete `paths` and `components` deep-equal those verified by the full build. Saved as `.gradle/backend-openapi-review/after.json`; original document retained as `before.json`. |
| Swagger UI in Chrome | `BACKEND_OPENAPI_SWAGGER_PASS`: all 35 operation buttons and 43 schema names visibly rendered; all 35 operations expand and render their Responses sections. No API-definition/resolver rendering errors. JSON, DOM and screenshot are under `.gradle/backend-openapi-review/browser/`. |
| Full test/build | Java 21 `./gradlew.bat clean build --no-daemon`: BUILD SUCCESSFUL, 2m 13s; **36 suites, 183 tests, 0 failures, 0 errors, 0 skipped**. PostgreSQL Testcontainers, migrations, authorization, concurrency, OpenAPI and production documentation-policy tests all included. Packaging completed. |
| Retained full-build evidence | XML reports, generated OpenAPI and `summary.json` under `.gradle/backend-openapi-review/clean-build/`; HTML report at `build/reports/tests/test/index.html`. |
| Final source validation | `git diff --check` passed; Node browser harness syntax check passed. No frontend changes. |

The existing documentation policy remains unchanged: anonymous GET access in dev/development/local/test profiles, with prod/production overriding this allowance. Protected business endpoints remain protected while development documentation is public. The existing production-profile security tests are included in the full build.
