# Frontend/backend integration readiness — 2026-09-13

## Scope and evidence

This is an **analysis-only review**, not an integration Plan or authorization to implement one. The frontend repository at `C:\Develop\dev-env\frontend` was inspected directly, including its bootstrap, router, providers, feature models/editors/renderers, storage hook, configuration and tests. Its HEAD was `75e679358e482c64f5e79ac7699948029ae3d6d2`, with a clean working tree at inspection. No frontend files were modified and no frontend build, dependency installation or browser test was run.

The backend baseline is the implemented **35-operation / 43-schema OpenAPI 3.1 document** verified in [the backend contract review](2026-09-13-backend-openapi-contract-review.md), the current controllers/DTOs/security configuration, and completed [PLAN-0003 through PLAN-0009](../plans/README.md). The reviewed generated document is retained at `.gradle/backend-openapi-review/after.json`. Frontend `docs/references/api/` contains historical proposals, including the statement that the backend is not implemented; it must not override the implemented backend and approved Plan clarifications.

Frontend source paths below are relative to `C:\Develop\dev-env\frontend`; backend links are relative to this report. Recommendations describe future work only.

## Current frontend architecture

### Bootstrap, composition and routing

`index.html` → `src/main.tsx` → React `StrictMode` → `app/App.tsx` → `AppProviders` → `AppLayout` → `PageRouter`. There is no session bootstrap, login page, auth gate or deferred business-data initialization. The complete local application mounts immediately.

`app/AppProviders.tsx` nests providers in this order:

1. Projects
2. Tasks
3. Journals
4. Dashboard
5. Links
6. Milestones
7. Workspace

Projects must precede Tasks/Journals because their providers resolve project names/scopes from the Project store; Milestones also uses Projects to derive its initial records. `WorkspaceProvider` depends on Dashboard editing state. **This WorkspaceProvider is navigation/presentation state, not the backend PersonalWorkspace.** It holds URL state, navigation actions, toast and detail-modal content; it has no authenticated identity, Workspace ID or revision.

React 19 and TypeScript are used with Vite; runtime dependencies are React, React DOM and lucide-react. There is no router library, Redux/Zustand, query-cache library, Axios or API SDK. `useState` and feature-specific Context providers hold state. Pages and `pages/home/widgetRenderers.tsx` compose features.

| Browser route | Current rendering |
| --- | --- |
| `/` | `HomePage` / DashboardWorkspace |
| `/projects` | Project list, active/archived tabs |
| `/projects/:projectId` | ProjectDetailPage; finds the project in the global in-memory array |
| `/tasks` | TaskManager/board and trash |
| `/journals` | JournalWorkspace |
| `/library` | LinkLibrary |
| Other paths | NotFoundPage; no authentication decision |

There is no standalone Milestone route; milestones appear on Home and Project detail. Task/Journal detail dialogs do not have dedicated routes. `app/routes.ts` maps Korean page IDs to URLs and stores `scope`, `q`, and Project `archived=true` in query parameters. API list parameters use `query`, not `q`; `archived` must become Project `status=archived`, not an API parameter of the same name.

`useBrowserNavigation.ts` implements push/replace/pop history. `devspace.navigation.v1` is a **history.state key**, not localStorage and not an authentication session identifier. `entryKey` remounts page-local state on navigation while feature providers remain mounted. Journal project/date/order controls therefore reset on route changes; global scope/search are URL-backed. Home scope/search selects **widgets by their configuration/title**, rather than searching all widget business data.

`useUnsavedChanges` registers module-level dirty forms, a beforeunload listener and discard confirmations. Workspace navigation blocks all navigation during Dashboard layout editing and otherwise consults dirty forms. Auth teardown must not rely on this ordinary navigation path: a session-invalid screen cannot remain mounted merely because the user cancels a layout-navigation guard.

### Persistence, fixtures and present error patterns

`shared/hooks/usePersistedState.ts` is the only application source using localStorage. It reads once in the initial state function, validates parsed JSON, and silently falls back to the supplied initial value when missing, invalid or unreadable. **Reading fallback data does not itself write it.** A save writes the entire array synchronously; React state changes only after storage succeeds. The result is boolean; failure preserves the previous state and exposes a Korean storage-error string. There is no storage-event subscription, cross-tab synchronization, account namespace or key-change reinitialization.

| Key | Provider / initial fallback | Required eventual replacement |
| --- | --- | --- |
| `devspace.projects.v1` | ProjectsProvider; four seeded Projects (`forest`, `orbit`, `api`, `web`) | Owned Project queries/mutations; no demo fallback on server empty/error |
| `devspace.tasks.v1` | TasksProvider; seven fixture Tasks with string IDs `1`…`7`, initially name-based Project relations | Owned Task lists/stats/mutations; remove seeded-name relationship resolution |
| `devspace.journals.v1` | JournalsProvider; three `demo-journal-*` entries with fixed 2026 dates | Journal API and explicit `entryDate` |
| `devspace.milestones.v1` | MilestonesProvider; `legacyMilestones(projects)` generates `legacy-*` IDs from Project goal memos | Independent Milestone API; never seed records merely because Projects load |
| `devspace.links.v1` | LinksProvider; four public reference links with semantic IDs | Full owned Link collection/revisions; a new account has an empty collection |
| `devspace.layout.v1` | DashboardProvider; local six-widget `defaultLayout` | Home Dashboard response envelope and explicit PUT save |

Fixtures are production runtime fallbacks, not an HTTP mocking layer. Some seeded Project objects also retain an extra fixture `tasks` number through object spreading, although it is absent from the declared Project type. Journal fixture objects similarly retain their display `date`. Never serialize a stored/model object wholesale as an API request. `projects/model.ts` imports fixtures for `legacyProjectId`, so removing only provider fixture imports would leave runtime demo coupling.

`features/operations/DeploymentStatus.tsx` embeds two example services/versions and explicitly labels them demo/non-live. There is no implemented Deployment/monitoring API. A saved `deploy` widget is configuration only and does not make those values real.

Editors use local form state, field/browser validation, boolean `onSave` results, inline `role="alert"` notices, confirmation dialogs and draft-preserving failure messages. `usePageScaffold` displays the first Dashboard/Task/Journal/Project provider error plus a toast; Links and Milestones expose their own error regions. A Task board status change accepts a `void` callback and does not itself show pending/retry status. Lists synchronously infer empty/not-found from arrays. No data-fetch loading/refresh/stale state, abort handling, server field-error mapping, error boundary or session-expiration mechanism was found. Changing a boolean callback to a Promise without updating callers would treat the Promise as success and prematurely close editors/reset drafts.

Search of application source found no `fetch`, Axios, XMLHttpRequest, WebSocket, EventSource, sessionStorage or cookie-access utility. The package and Vite configuration likewise provide no HTTP abstraction or API proxy. Existing Vitest model/router tests and Playwright tests validate local behavior; many browser tests directly manipulate localStorage or inject `Storage.setItem` failures. They are not evidence of API/authentication integration.

### Existing dependency boundaries

`src/app/APP.md`, `pages/PAGES.md`, `features/FEATURES.md`, `shared/SHARED.md`, `docs/decisions/feature-dependencies.md` and `tools/check-boundaries.mjs` govern composition. Shared cannot import app/features; features cannot import app; cross-feature imports are restricted to named Project contracts. An HTTP module in shared must not import an app auth hook or feature provider. Compose the auth boundary in app and inject transport/session-change callbacks or other explicit feature-agnostic dependencies. Introducing direct imports from every feature into a new auth feature would require a deliberate boundary decision/checker update; it is not automatically allowed by adding a provider.

## Missing authentication and User/PersonalWorkspace layer

### Proposed startup insertion point

Place session bootstrap **outside the existing AppProviders/AppLayout/PageRouter subtree**, in app composition. An auth/session provider or boundary should call **GET `/api/v1/me` before mounting authenticated business providers**. This also runs after the Google callback redirects back to the SPA. StrictMode can mount effects twice during development: bootstrap must be read-only, cancellable and protected against late results; it must not trigger login, seed creation or automatic Dashboard PUT in an effect.

Use explicit states rather than a nullable user alone:

| Result | Application behavior required |
| --- | --- |
| Not checked / checking | Bootstrap loading shell; no private data or fixtures |
| 200 | Store `{id,displayName,workspace:{id,name,revision}}`; mount protected application for this identity |
| 401 `AUTH_REQUIRED` | Unauthenticated/login entry; retain a validated local intended route, not a server-data cache |
| 403 `ACCOUNT_DISABLED` | Clear private state and show disabled-account state; do not loop login/token refresh |
| Network, invalid response or 5xx | Retryable bootstrap error; do not misclassify as anonymous or initialize demo data |

Authentication decides whether protected routes exist; `PageRouter` can continue deciding which authenticated feature page to render. Root `/` can be the unauthenticated entry and authenticated Home, avoiding an invented backend login page. Protected deep links, browser back/forward and callback return paths must work without a loaded Project list. Check `authError=login_failed` at startup before normal URL canonicalization drops it; render a safe fixed message, not provider error text.

User/PersonalWorkspace are server identity state, owned by the auth/session layer and consumed by shell UI. Keep the current navigation Workspace state separate or clarify its name during a later approved change. Sidebar's static `N`, “나의 작업실” / “My workspace”, local-only badge and explanatory modal need to use `/me` data and appropriate login/logout UI. `/me` has no email/avatar, permissions list, Workspace audit timestamps or `dataRevision`; do not fabricate them. A display-name initial can remain presentation-derived. `/me/workspace` GET/PATCH are not implemented; Workspace renaming/multiple-workspace switching cannot be implied by this integration.

### Google entry, logout and session lifecycle

- **Login:** use browser navigation to `/api/v1/auth/login?returnTo=<encoded local path and query>`. Backend first redirects to `/oauth2/authorization/google`, then Google. Backend alone handles state/nonce/PKCE, code exchange and `/api/v1/auth/callback/google`. Do not use fetch to follow the provider redirect, implement a Google token client, add a bearer token, or put credentials in browser storage.
- **Return path:** backend `SafeReturnToPolicy` accepts local application paths and rejects external URLs, API return paths, fragments and unsafe encodings. The success handler redirects to that **relative** path. The SPA and backend entry/callback therefore need coherent browser-visible routing through the same origin. A different frontend-origin absolute `returnTo` is not supported.
- **Logout:** an explicit user action should resolve ordinary dirty-draft confirmation first, then POST `/api/v1/auth/logout` with the current CSRF token when authenticated. Await bodyless 204, cancel requests and destroy private providers/caches/token/drafts before showing the unauthenticated entry. No-session logout is also 204 without CSRF. A network failure is not proof that the server session was invalidated; do not silently report successful logout.
- **CSRF:** after successful `/me`, acquire GET `/api/v1/auth/csrf` with session cookies; response is `{csrfToken}` and requires no query parameter. Keep the token in memory for the current session and attach `X-CSRF-Token` to POST/PATCH/PUT/DELETE, including authenticated logout. Acquire before enabling mutations, or serialize acquisition on demand. Never reuse a previous account's token. Browsers supply Origin; the backend rejects a supplied unapproved Origin.
- **401:** treat as an application-wide session invalidation, deduplicate concurrent failures and clear the protected tree. There is no refresh-token endpoint. Cancel queued mutations and invalidate session generation so late successes/401s from an old account cannot overwrite or log out a newer session. Do not automatically replay an old mutation after login.
- **403:** distinguish `ACCOUNT_DISABLED` from `CSRF_INVALID`. A token problem is not automatically logout; re-check session and reacquire CSRF as appropriate, then make retry deliberate/bounded. An Origin rejection will not be fixed by repeatedly fetching tokens. Revisions and creation keys must remain tied to the original user intent.
- **Cross-tab/session change:** the browser session cookie is shared by tabs, while current React stores are not. Account changes may produce successful 200 responses for a different identity, so 401 handling alone is insufficient. Plan an auth-change notification plus `/me` revalidation on relevant focus/resume events; gate/cancel writes during known identity transitions. Cache partitioning is client hygiene, not authorization—the server continues to derive ownership from the session.

Backend authentication edge: an ordinary failed callback redirects to `/?authError=login_failed`, but a verified disabled account can receive **403 JSON directly at the callback URL** from `OidcAuthenticationSuccessHandler`. A SPA gate cannot render over that top-level response. Accept that existing edge behavior explicitly for the first integration, or separately report/approve a backend redirect UX change; do not assume every callback outcome reaches Home. There is also no frontend-supported `prompt=select_account` contract: the application can log out and re-enter Google login, but must not promise an account-picker behavior that the current backend does not expose.

### Account-switch cleanup

The protected subtree should be remounted on authenticated User/Workspace identity changes, with a separate request/session generation guarding asynchronous results. A React key alone does not cancel network responses or queued callbacks.

Clear/partition feature entities, all list pages/cursors/filter caches, Overview/statistics, Link collection revision, Dashboard schema/revision/draft, selection/modal/drag state, pending creation keys, CSRF and private toasts/details. Unregister dirty-form guards on teardown. A stale Project route may remain as an intended URL, but the new account must resolve it independently and get 404 rather than old cached content. Do not use the navigation history `session` as the auth identity.

Existing localStorage business keys have **no ownership provenance**. Remounting providers while retaining their old storage reads would reload the previous local user's data. For the initial API mode, stop reading/writing all six keys as authority; leave legacy data unimported until an explicit preservation/import policy exists. Do not automatically clear all browser storage, delete local records, assign them to the first Google account, or upload fixtures. If same-user drafts are preserved across reauthentication, isolate them from other identities and require explicit resumption; never silently save them under another user.

## Shared HTTP/client requirements for a later Plan

These are missing capabilities, not an instruction to install a new HTTP/query framework. Existing native fetch and React composition can support the first integration if the Plan keeps responsibilities explicit.

| Concern | Required contract handling |
| --- | --- |
| Transport/origin | One agreed browser-visible API root. Prefer same-origin `/api/v1` with session credentials; do not rely on cross-origin cookies working by default. Request cancellation and a session-generation guard are required. |
| JSON boundaries | Separate request DTOs, response DTOs and view/form models. Validate/narrow external data; a TypeScript cast is not runtime validation. Check HTTP status/content type, tolerate the approved empty 204 and never parse SPA HTML as a successful DTO. |
| Headers | JSON content type for bodies; CSRF on mutations; required Idempotency-Key only on five creation POSTs. No Authorization bearer or client-supplied ownership identifiers. Do not try to set browser-controlled Origin/Cookie manually. |
| Errors | Parse `{code,message,fieldErrors,requestId}` and retain status/requestId for support. Map server fields back to form names. Distinguish network/abort, malformed response, 400 validation/invalid cursor, 401, typed 403, inaccessible/missing 404, 409 conflict, Link 429 quota and 5xx. No fixture fallback. |
| Creation retries | Generate a key per intended create; keep the exact request field presence/body and key across a retry of that intent. Changing the submitted body requires a new intent/key. Replay lasts 24 hours and returns the original 201 snapshot, which may be stale after later edits/deletion; refetch current state instead of resurrecting a deleted record or regressing a Link collection revision. |
| Optimistic concurrency | Keep server revisions with entities, lists and Dashboard. PATCH/restore body revision and DELETE query revision use the last observed value. Link order uses collectionRevision. Preserve drafts on 409; fetch current state and ask the user to reconcile/retry intentionally. No silent increment-and-resubmit loop. Serialize or otherwise coordinate overlapping same-entity mutations. |
| Mutation lifecycle | Async result/pending/error replaces synchronous boolean saves at every caller. Disable duplicate submissions; keep drafts open until confirmed success. Use returned normalized DTOs. Optimistic UI, if selected, needs rollback/reconciliation rather than reporting success immediately. |
| Pagination | Keep `{items,total,nextCursor}` and filter/limit identity together. Omit cursor for first page; use the returned opaque cursor unchanged. Reset/deduplicate after filter changes or mutations; each request has a snapshot, but there is no cross-page snapshot guarantee. Do not fetch one page and apply all remaining filters locally. |
| Invalidation | Project rename/scope/archive affects child presentation and filtering without necessarily changing child revision. Task changes affect stats/Overview/trash. Link writes affect both item and collection revisions/order. Dashboard writes change configuration only. Refresh affected queries rather than inferring server state from a single loaded array. |
| Scope isolation | Page-specific queries must not overwrite shared data needed by another widget. Cache/query identity includes authenticated owner context and all effective filters. Shared transport reports auth changes through composition; it does not import app/feature hooks. |

Read pages have default limit 20 and max 100; widget configuration limit is separately restricted to 1–20. `Number` can represent the approved revisions up to `9007199254740991`, but only accept safe integers and never synthesize/increment them on the client. Omission, `null`, `''`, `false` and `0` have different meanings and cannot be collapsed by a generic “remove falsy values” serializer.

## Feature-by-feature comparison

### Project

Sources: `features/projects/model.ts`, ProjectsProvider, ProjectEditor, ProjectOverview, ProjectsWorkspace; `pages/ProjectDetailPage.tsx`; Sidebar and usePageScaffold.

| Current frontend | Implemented backend / necessary adapter |
| --- | --- |
| `id,name,subtitle,scope,stack,progress,color,milestone,repositoryUrl,archived` | Response: `id,revision,createdAt,updatedAt,name,subtitle,scope,stack,progress,currentMilestone,repositoryUrl,status,colorToken`. Map `milestone ↔ currentMilestone`, `archived ↔ status` and `colorToken → presentation color`. Keep revision/audit metadata even if not displayed. |
| IDs generated in editor, demo icon/color keyed by `forest/orbit/api/web` | Server creates UUID business IDs; request omits ID/color/audit fields. Current CSS classes use fixture colors; map unity/server tokens deliberately rather than passing a token into incompatible CSS. UUIDs take the existing generic scope icon fallback unless presentation is separately changed. |
| `upsert` replaces a whole local object; archive flips boolean | POST requires `name,scope,stack`; optional subtitle/currentMilestone/repositoryUrl default `''`, progress defaults 0. Creation is active; there is no create `status`. PATCH requires revision plus supplied editable fields, including status; all explicit nulls rejected. Return full Project on 201/200. |
| Local active/archived arrays, case-insensitive `(name + stack)` search, array-length totals | GET `/projects`: scope, status active/archived/all, query, limit, cursor; fixed createdAt DESC/id DESC. Server matches name or stack rather than arbitrary concatenated field boundaries. Use response total for filtered lists, Overview for unsearched global/scope counters. |
| Detail means `projects.find(id)`; no row means unavailable | GET `/projects/{id}` independently of list cache/page; permit owned archived Project. Loading, 404 and network error must differ. |

Replace `devspace.projects.v1` and fixture fallback. Project selectors must page/load suitable active Projects and separately retain/fetch a currently selected archived Project; absence from the first page is not absence from the Workspace. Clear legacy name-to-ID lookup from API mode. `currentMilestone` is an independent goal memo, not a Milestone resource; do not create/update milestone records from it. Manual decimal progress is permitted by the backend; the current number input's default integer step is a UI limitation, not a backend restriction.

**Dependencies/risks:** Projects establish canonical server IDs used by Tasks/Journals/Milestones/widgets. Converting Projects while leaving dependent local records active would break fixture-ID/name joins and expose mixed authorities. Introduce the auth gate and a coherent API-data mode before switching Project source. Project changes require cross-view revalidation; don't derive Sidebar totals or missing-project decisions from a partially loaded list.

### Task

Sources: `features/tasks/model.ts`, TasksProvider, TaskManager, TaskBoard, TaskEditor; Project detail and Home renderers.

| Current frontend | Implemented backend / necessary adapter |
| --- | --- |
| `id,title,project,scope,status,priority,tag`, optional `projectId,description,deletedAt` | Response: `id,revision,createdAt,updatedAt,title,projectId,projectName,scope,description,status,priority,tag,deletedAt`. `projectName → project`; description is always a string; deletedAt is present string-or-null; projectId is required UUID. Remove name-based recovery for server data. |
| Priority `높음` / `보통` | Map display labels to `high` / `normal`; do not send Korean enum values. Status already matches todo/doing/done. Tag is one string, not a tags array. |
| Editor saves its whole object and resets deletedAt to null | POST requires title/projectId; description/tag default `''`, status todo, priority normal. PATCH requires revision and editable fields only. Omit derived project/scope, ID and deletedAt. Restoration is a distinct POST `/{id}/restore` with `{revision}`. |
| Local status mutation and local-clock soft deletion | PATCH status; DELETE `/{id}?revision=…` returns the full soft-deleted Task; restore returns full Task. Use returned deletedAt/revision, not the browser clock. PATCH trash gives RESOURCE_DELETED; repeated delete/invalid restore can give INVALID_RESOURCE_STATE. |
| One global array, local board columns and trash counts | GET `/tasks`: scope/projectId/projectStatus/query/status/deleted/limit/cursor. `deleted=true` selects only trash, not active+trash. `/tasks/stats` accepts scope/projectId/projectStatus/query and counts undeleted rows; it rejects status/deleted and paging parameters. |

Replace `devspace.tasks.v1`, initialTasks and `resolveTask` fixture-name inference. Create/reassignment requires an active owned Project; retaining an existing archived relation and restoring a Task associated with an archived Project are allowed. Preserve server projectName/scope and refresh after Project edits instead of overwriting them from an incomplete Project page.

**Dependencies/risks:** TaskBoard callbacks and drag/drop need pending/conflict handling. TaskManager slices before columns, so a Home board limit is a combined-card limit; do not accidentally fetch N cards in each of three columns. A full Task page can use per-status pages with `/tasks/stats` counts, while a limited Home board uses a combined list query. Trash currently ignores the text search; preserve that intentionally or explicitly change the UX, rather than accidentally applying the active list's query. Project-detail task titles currently have no edit callback (existing backlog), so adding that UI is not automatically part of API integration.

### Journal

Sources: `features/journal/model.ts`, JournalsProvider, JournalEditor, JournalWorkspace, RecentJournals.

| Current frontend | Implemented backend / necessary adapter |
| --- | --- |
| `id,title,projectId,project,scope,body,createdAt`; createdAt doubles as editable journal date | Response adds revision and updatedAt, uses projectName, and has **separate `entryDate` (date) and `createdAt`/`updatedAt` (audit date-times)**. Model the date explicitly; do not alias entryDate into createdAt. |
| Changing date converts local noon to an ISO timestamp; display/filter/sort reads createdAt | POST requires title/projectId/body/entryDate. PATCH revision plus supplied title/projectId/body/entryDate. Audit timestamps cannot be written. Display and date filters use YYYY-MM-DD directly; do not pass it through timezone-dependent Date conversion. |
| Local order is `newest`/`oldest`, sorted only by createdAt; date/project controls are local | GET `/journals` uses scope/projectId/projectStatus/query/from/to/**sort**/limit/cursor; sort is entryDate, createdAt, id in the selected direction. Map UI `order` to API `sort`. Dates are inclusive calendar dates, years 0001–9999. |
| Editor trims body; local delete filters array | Backend preserves body content while enforcing nonblank and 20,000 UTF-16 units. Avoid unintended destructive trimming on edit round-trips. DELETE revision returns only `{deletedId}`, permanently; no restore. |

Replace `devspace.journals.v1`, fixture IDs/dates and local date-based authority. Create/reassign needs an active Project; retaining the existing archived relation is allowed. General recent-journal widgets include archived Projects by default, and selected Project widgets pass projectId with projectStatus=all. Default recent count is 3, passed as API limit, not the generic default 20.

**Dependencies/risks:** this is a semantic model migration, not merely field renaming. Old local timestamps cannot be converted into a user's intended calendar date without a specified timezone/import policy. Defer legacy import rather than silently guessing. Change editor/date selectors/RecentJournals/Project-detail ordering together; retain audit timestamps separately.

### Milestone

Sources: `features/milestones/model.ts`, MilestonesProvider, MilestoneEditor, MilestoneList; shared ProjectSelect and Home renderers.

| Current frontend | Implemented backend / necessary adapter |
| --- | --- |
| `id,projectId,title,dueDate:string,completed`; `''` means no deadline | Response: `id,revision,createdAt,updatedAt,title,projectId,projectName,scope,dueDate,completed`. Convert no date `'' ↔ null` at the form boundary. Keep projectName/scope and revision/audits; dueDate is date-or-null. |
| Whole local-object `upsert`, completion toggles boolean | POST requires title/projectId; dueDate omitted defaults null, completed omitted defaults false. PATCH requires revision; omitted dueDate preserves it, null clears it, `''` is invalid. Completion remains boolean; status is a list filter, not a resource field. |
| Locally sorted open-first/date-first/undated-last/id; UI status open/done/all | GET `/milestones` accepts scope/projectId/projectStatus/status/limit/cursor; same basic completed/date-null-last/id ordering. Server supports archived Project targets for creation and reassignment. |
| No delete action/provider method | Backend has permanent DELETE with query revision, returning `{deletedId}`. This is an unused backend capability, not a required new frontend control unless the integration scope explicitly adds deletion UI. |

Replace `devspace.milestones.v1` and **remove `legacyMilestones(projects)` from API initialization**. A Project's `currentMilestone` memo must not be automatically expanded into a new persisted Milestone.

**Dependencies/risks:** ProjectSelect filters archived candidates except the current value. That behavior suits Task/Journal restrictions but prevents choosing a different archived Project that Milestone API explicitly permits. Adapt Milestone selection deliberately without globally loosening Task/Journal constraints. Home general milestones use projectStatus=active; selected Project/detail uses all + projectId and allows archived. Home default limit is 2; detail needs all matches through paging. Scope/membership must not be derived only from a partial Project array.

### Link

Sources: `features/links/model.ts`, LinksProvider, LinkEditor, QuickLinks, LinkLibrary.

| Current frontend | Implemented backend / necessary adapter |
| --- | --- |
| `QuickLink={id,label,desc,url,scope}` and array position | `LinkResponse={id,revision,createdAt,updatedAt,label,description,url,scope,position}`; map desc↔description, preserve returned ordering/metadata. Semantic fixture IDs become server UUIDs; existing icons will use their generic fallback. |
| Whole-object local append/update | POST requires label/url, description defaults `''`, scope defaults all. PATCH requires item revision plus supplied editable fields; position is not writable. POST/PATCH return **`{item,collectionRevision}`**, not a bare Link. |
| Local delete filters array | DELETE with item revision returns `{deletedId,collectionRevision}`; permanent, repeated delete 404. |
| Move swaps neighbors in full local array; UI disables move while filtered | PUT `/links/order` requires `{collectionRevision,ids}` with the exact whole-Workspace permutation. Response is full unfiltered LinkListResponse. No partial/filtered reorder, per-item position request or item-revision list. |
| Full locally filtered list; all links included in each scope | GET `/links` supports only scope/query; `{items,total,nextCursor:null,collectionRevision}`, ordered position/id. No paging, project filter or limit. Full collection is bounded at 500 Links / approved payload limit; POST can return 429 QUOTA_EXCEEDED. |

Replace `devspace.links.v1` and four default reference records. Store global collectionRevision separately from item revisions, including virtual collection revision 0. Existing unfiltered-only reorder UI agrees with backend intent, but must await success and handle stale collection conflicts. Every successful PATCH advances item and collection revision; reorder advances collection revision even if unchanged and updates moved items' revisions. Use returned items after reorder.

**Dependencies/risks:** do not regress the collection counter by accepting an old replay/success after a newer query. Refetch after ambiguous creation replay. Current URL validation largely aligns with http/https/host/no credentials, but the backend also rejects an empty user-info delimiter; server validation remains authoritative. New accounts start empty instead of seeded. Link is independent of Project ownership and can be integrated before Project-dependent features once auth/transport exist.

### Home Dashboard

Sources: `features/dashboard/model.ts`, DashboardProvider, WidgetEditor, DashboardWorkspace; `pages/HomePage.tsx` and `pages/home/widgetRenderers.tsx`.

| Current frontend | Implemented backend / necessary adapter |
| --- | --- |
| Bare `Widget[]`; widgets have id/type/scope/title/size, optional projectId/limit | GET returns `{id:'home',schemaVersion:1,revision,widgets}`. PUT accepts `{schemaVersion:1,revision,widgets}` without id. Preserve response envelope metadata outside the editable widget array. |
| Separate layout/draft; sync commit; reset copies local defaultLayout | Make commit async; only explicit PUT persists, use normalized response, keep draft on error/409. Reset remains a draft and must use the approved default, not a DELETE/revision reset. |
| isLayout validates enum/basic values and raw ID uniqueness but accepts blank titles/IDs and project settings for utility types | Backend trims nonblank IDs/titles, title max 48 UTF-16 units, ID uniqueness after trim, strict property/type/null rules. Repeated types are allowed. Only overview/board/journal/milestone accept projectId/limit; deploy/links forbid both. |
| Client-generated widget UUIDs plus semantic default IDs | Widget IDs are client-owned instance strings, **not server-generated business UUIDs**. Preserve valid instance IDs across PUT. Do not regenerate all IDs or treat type as unique. |
| UI usually normalizes selected-project scope; supportsProject forms omit unused fields | Backend validates owned active/archived Project then normalizes scope to all. Omitted limit remains omitted, never null or inserted numeric defaults; 1–20 when present. |
| Missing selected Project shows an in-widget recovery message | Missing/inaccessible stored reference makes Dashboard GET return 404 without fallback/repair. Distinguish GET failure from a Project not yet loaded. Do not synthesize/PUT defaults or drop the reference automatically. |

Replace `devspace.layout.v1` as the source of saved layout. Unsaved GET returns virtual revision **0** and writes no row; the first PUT with 0 returns persisted revision **1**. Concurrent first saves have one success/one 409. Subsequent saves compare and increment persisted revision, including unchanged saves. On a network-ambiguous first save, reload/reconcile: do not assume absence or silently change 0 to 1 and retry. An intentionally saved empty widgets array remains empty. Cancel/removing a widget affects configuration only, not business data already edited inside widgets.

**Default parity is now verified and does not match.** The backend default was explicitly approved in PLAN-0009; use it for a new account and for the future reset behavior unless a separate contract decision changes it.

| Position | Current frontend: ID / type / scope / size / title | Approved backend: ID / type / scope / size / title |
| ---: | --- | --- |
| 1 | overview / overview / unity / medium / 프로젝트 한눈에 보기 | home-overview / overview / all / wide / 프로젝트 개요 |
| 2 | deploy / deploy / server / small / 운영 · 배포 현황 | home-board / board / all / wide / 작업 보드 |
| 3 | board / board / unity / medium / 작업 보드 | home-deploy / deploy / all / medium / 운영 |
| 4 | links / links / all / small / 빠른 링크 | home-links / links / all / small / 바로가기 |
| 5 | journal / journal / all / medium / 최근 개발 일지 | home-journal / journal / all / medium / 개발 일지 |
| 6 | milestone / milestone / unity / small / 다가오는 마일스톤 | home-milestone / milestone / all / medium / 마일스톤 |

Both omit projectId/limit in defaults. There is no endpoint returning factory defaults once a saved row exists. A reset control therefore needs a contract-aligned frontend constant with a parity test; the initial GET itself remains authoritative for an unsaved account.

**Dependencies/risks:** configuration reads do not provide widget business data. Home composition must coordinate separate queries per widget, deduplicate identical requests and avoid one widget's loading/error affecting another's data. General overview cards use active Project lists; selected Project cards permit archived. Board/journal include archived Project relations by default; general milestone excludes them. Home search/scope filters visible widgets and must not overwrite each widget's own API scope/projectId. An omitted overview/board limit means all matching rows via paging, not silently accepting the API default 20; decide loading/paging UX for this in the Plan. Journal/milestone defaults are 3/2 and Links loads the full collection. Deploy stays explicitly disconnected/demo.

### Overview and shared counters

There is no Overview model/provider/client. `ProjectOverview` derives stats from arrays before card slicing/search; Sidebar/usePageScaffold count activeProjects; Project detail counts filtered tasks; TaskBoard counts the supplied cards. This works only because the prototype loads complete local arrays.

GET `/overview` accepts only scope and optional projectId, returns `{scope,projectId,projects:{total,archived,byScope:{unity:{total,archived},server:{total,archived}}},tasks:{todo,doing,done,total},asOf}`. Both scopes/status keys are always present; projectId is present and nullable. Project total is active only. Archived count is separate. Task totals exclude trash and include active/archived Projects. Ownership is checked, then scope/projectId intersection applies—unlike stored widget normalization to scope all.

Introduce a separate Overview DTO/query for unsearched totals. Use all-scope Overview for global Sidebar counts, scoped Overview for Project statistics, archived count for the archive tab, and project-scoped counts for detail. Search result totals come from filtered lists; searched Task board statistics use `/tasks/stats`, because Overview deliberately rejects query/status/projectStatus/deleted/limit/cursor. Do not infer complete stats from the currently loaded card/page. Existing search-statistics tests explicitly expect overview counters to stay independent of Project search.

## Recommended integration order and acceptance boundaries

1. **Fix the integration baseline in the Plan:** browser-visible origin/proxy/callback arrangement, local-data preservation policy, approved backend defaults, and API-mode versus prototype-mode rollout. Resolve frontend dependency-boundary implications before importing auth into every feature.
2. **Transport + auth gate + User/Workspace shell:** `/me` bootstrap, Google entry, CSRF, logout, session-expiry/disabled states, late-response protection and account cleanup. Keep protected feature providers behind the gate. Test two users, loading/error versus anonymous and deep-link return.
3. **Read infrastructure + Project + Overview:** explicit DTOs/revisions, paged list/detail, active/archived selectors, server-owned IDs, global counters; then Project create/PATCH/archive with key/conflict handling. Do not mount legacy-dependent stores against server Projects. Either keep API mode separately gated until ready or disable unintegrated dependent views rather than mixing local and server IDs.
4. **Task:** paged active/trash lists, stats, create/edit/status/delete/restore; preserve Home combined-limit semantics and coordinate Project changes.
5. **Journal:** migrate entryDate, form/date filtering/sort and recent/detail widgets together; then create/PATCH/permanent delete.
6. **Milestone:** independent records, nullable dates, open/done filtering, archived targets and Home/detail query policies; no memo-derived seed. Deletion UI only if explicitly scoped.
7. **Link:** full collection, wrappers, item/collection revisions, quota and exact reorder. This can move directly after step 2 as an independent vertical slice; it has no Project prerequisite.
8. **Dashboard configuration + full Home composition:** server defaults/envelope, explicit save/reset/conflict recovery, per-widget query orchestration and failure boundaries. Configuration DTO/read work can start earlier, but release of connected Home should follow the data features it displays.
9. **Retire API-mode storage/fixtures and validate end to end:** remove active reads/seeds/legacy name lookup, update local-only copy, replace storage-failure tests with network/auth/conflict tests while retaining accessibility/navigation coverage. Verify >100-row lists, pagination changes, archived relations, empty-new-account behavior, first-save races, two tabs/accounts and delayed responses. Deployment fixtures remain visibly non-live until a separately approved integration.

This order is advisory; no sessions were executed and no clients/authentication code were created. No new state-management dependency is required merely to start planning. The eventual Plan should specify where list/query cache state lives and how reads/mutations are coordinated rather than spreading fetch calls into presentation components.

## Decisions and blockers to resolve when drafting the integration Plan

| Item | Evidence / recommended disposition |
| --- | --- |
| **Browser origin and proxy topology** | Vite has no proxy; frontend Nginx only serves static files with SPA fallback; backend has relative OAuth redirects and configured Origin checks, no cross-origin frontend integration configuration. Serving `/api/v1/me` through current static fallback can return HTML 200 instead of JSON. Agree on same-origin forwarding for `/api/**` **and `/oauth2/**`**, with callback to backend and ordinary paths to SPA. Align APP_ORIGIN, callback URI and cookie HTTPS policy. This is a prerequisite for executable auth/browser acceptance criteria. Cross-origin instead would require additional backend/security scope, not just `credentials: include`. |
| **Legacy local data** | Six unowned keys contain fixtures and possibly actual local edits; no implemented import API/mapping/revision workflow. Recommended first API release leaves them untouched and unused, creates no automatic server records, and defers explicit import/export. Confirm whether preservation/import is a launch requirement before promising migration. If required, separately specify ID remapping (Projects first), Journal timezone policy, duplicates and target-owner confirmation. |
| **First connected UI scope** | Recommended: authoritative backend defaults, real empty states, deploy remains labeled demo, no Workspace editing, no automatic Milestone delete UI, no unrelated Task-detail editor enhancement. A request for the old Home default, Workspace editing, deployment data or full legacy import expands scope and needs a decision before implementation. |
| **Callback disabled-account UX** | Current callback can end at JSON 403; approve acceptance of that existing edge or request a separately reviewed backend redirect behavior. `/me` disabled/error UI is still required. |
| **Query and paging UX** | Specify paged selectors/detail and all-row widgets, board combined versus per-column limits, invalidation and pending/conflict behavior. Current synchronous-array components do not resolve these decisions by themselves. Avoid an unconditional “load the entire Workspace at startup” workaround. |
| **Auth dependency composition** | Follow existing boundary checker; recommended app composition plus shared transport with injected callbacks avoids a new unrestricted cross-feature auth import. A different approach must explicitly update the accepted dependency decision in the frontend Plan. |

There is **no discovered missing backend CRUD/Overview/Home-configuration operation blocking the listed feature integration**. The primary blockers are rollout/origin and data-migration scope choices, not a need to change the approved resource contracts. Real Google credentials/callback registration are required for a real-account smoke test; their absence need not block writing the Plan or testing against the backend's local authentication test fixtures. No secrets or external provider setup were inspected or changed in this review.

## Source index and verification limits

| Evidence | Key frontend files |
| --- | --- |
| Bootstrap/routing | `src/main.tsx`; `src/app/App.tsx`, `AppProviders.tsx`, `WorkspaceProvider.tsx`, `PageRouter.tsx`, `routes.ts`, `useBrowserNavigation.ts` |
| State/persistence/guards | `src/shared/hooks/usePersistedState.ts`, `useUnsavedChanges.ts`; `src/shared/lib/navigationGuard.ts`; each feature's Provider |
| Current identity shell/counters | `src/app/layouts/Sidebar.tsx`, `usePageScaffold.tsx` |
| Project/Task | `src/features/projects/{model.ts,fixtures.ts,ProjectsProvider.tsx,ProjectEditor.tsx,ProjectOverview.tsx,ProjectsWorkspace.tsx,ProjectSelect.tsx}`; `src/features/tasks/{model.ts,fixtures.ts,TasksProvider.tsx,TaskEditor.tsx,TaskManager.tsx,TaskBoard.tsx}` |
| Journal/Milestone | `src/features/journal/{model.ts,fixtures.ts,JournalsProvider.tsx,JournalEditor.tsx,JournalWorkspace.tsx,RecentJournals.tsx}`; `src/features/milestones/{model.ts,MilestonesProvider.tsx,MilestoneEditor.tsx,MilestoneList.tsx}` |
| Link/Dashboard/Home | `src/features/links/{model.ts,LinksProvider.tsx,LinkEditor.tsx,QuickLinks.tsx,LinkLibrary.tsx}`; `src/features/dashboard/{model.ts,DashboardProvider.tsx,WidgetEditor.tsx,DashboardWorkspace.tsx}`; `src/pages/HomePage.tsx`, `home/widgetRenderers.tsx`, `ProjectDetailPage.tsx`; `src/features/operations/DeploymentStatus.tsx` |
| Configuration/tests | `package.json`, `vite.config.ts`, `nginx.conf`; `tools/check-boundaries.mjs`; `tests/search-statistics.spec.ts` and local-storage/CRUD browser tests |

Backend references: [auth controller](../../src/main/java/com/kopite/devspace/auth/presentation/controller/AuthController.java), [Me DTO](../../src/main/java/com/kopite/devspace/auth/presentation/dto/MeResponse.java), [security configuration](../../src/main/java/com/kopite/devspace/auth/infrastructure/SecurityConfiguration.java), [return-path policy](../../src/main/java/com/kopite/devspace/auth/infrastructure/oidc/SafeReturnToPolicy.java), [callback success handler](../../src/main/java/com/kopite/devspace/auth/infrastructure/oidc/OidcAuthenticationSuccessHandler.java), [Home defaults](../../src/main/java/com/kopite/devspace/dashboard/domain/HomeDashboard.java), [PLAN-0009](../plans/PLAN-0009-dashboard-overview-api.md) and the [full backend contract review](2026-09-13-backend-openapi-contract-review.md).

This report is based on source inspection and the previously validated backend document, not a live frontend/backend integration run. No new build/test result is claimed. Only this backend review document is added for this task.
