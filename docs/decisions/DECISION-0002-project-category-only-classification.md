# DECISION-0002: ProjectCategory as the sole Project classification

Date: 2026-09-14
Status: `accepted`

사용자가 2026-09-14에 본 Decision과 PLAN-0011을 승인했다. 아래 A1–A6의 권장안이 선택된 계약이다. DECISION-0001은 superseded로 표시했고 accepted index에 이 문서를 등록했다. DECISION-0001의 Category identity/lifecycle 보장은 아래에 재확인하며, scope 병행 지원과 Category 필터 유보 방향을 새 방향으로 대체한다. 운영별 데이터 매핑과 실제 배포 승인은 별도다.

## Context

사용자는 Project 분류를 categoryId 하나로 통일하고 unity/server 분류를 제거하도록 방향을 변경했다. [실제 구현 조사](../reviews/2026-09-14-project-category-only-impact.md)와 [현재 생성 OpenAPI](../reviews/project-category-only-evidence/current-openapi.json)가 근거다. 현재 DB는 V14까지 적용됐으며 유효한 scope 포함 replay가 존재한다. 따라서 필드 삭제만으로는 안전하게 전환할 수 없다.

현재 Project와 Link만 scope를 저장한다. Task/Journal/Milestone은 소유 Project join으로 scope를 유도하고, Overview 및 Dashboard도 scope를 소비한다. Link는 Project 관계가 없고 Deploy는 백엔드 리소스가 없다. 이 두 영역에 임의의 Category 관계를 추가하지 않는다.

Decision guide의 template 파일이 저장소에 없어 기존 Decision과 같은 Context/Decision/Rationale/Consequences/Validation 형식을 사용한다.

## Decision

### 1. 단일 도메인 관계와 nullable 정책

- Project.categoryId를 유일한 분류 관계로 사용한다. Category UUID가 identity이고 name은 표시값이다. Project.scope와 colorToken=scope를 정상 도메인/API에서 제거한다. 대체 colorToken이나 고정 enum을 새로 만들지 않는다.
- **categoryId nullable 유지**를 권장한다. 생성 생략/null은 미분류, UUID는 같은 workspace Category를 지정한다. PATCH 생략은 보존, null은 해제, UUID는 지정/변경이다. 다른 현재 필드의 null 허용 범위를 넓히지 않는다.
- 기존 non-null Category UUID와 사용자 이름을 최우선 보존한다. 기존 미분류는 null 그대로다. scope 값으로 Category를 만들거나 기존 이름에 병합/재매핑하지 않는다. null을 나중에 필수로 바꾸는 것은 별도 결정이다.
- active/archived Project 모두 Category 편집 가능하고 archive/unarchive는 관계를 보존한다. Project 이름 중복 허용, 기타 필드 검증 및 Project 수정 revision 규칙은 그대로다. Project 삭제 API를 추가하지 않는다.
- Category 모델은 id/workspaceId/name/revision/createdAt/updatedAt 그대로다. Java trim→NFC, 유효 Unicode, normalized 100 UTF-16 단위, workspace-local case-sensitive exact uniqueness, C collation, 생성 cap 100, createdAt/id ASC full collection을 유지한다. 자동 starter Category는 없다.
- Category CRUD는 `/api/v1/project-categories`의 현재 5개 operation과 body/status/key/revision을 유지한다. 참조하는 active/archived Project가 하나라도 있으면 409 CATEGORY_IN_USE, 빈 fieldErrors. composite FK로 뒷받침하고 cascade/자동 해제/대체 Category를 금지한다. Project가 직접 참조하는 한 하위 리소스/Link 개수는 삭제 판정에 별도 영향을 주지 않는다.

### 2. 정상 API 버전과 요청/응답

Project/Task/Journal/Milestone/Link/Overview/Dashboard의 breaking 계약은 `/api/v2`를 사용한다. Category CRUD 및 auth/csrf/me는 현재 v1 endpoint를 유지한다. 새 dependency나 API gateway는 요구하지 않는다. v1/v2 business writer를 동시에 운용하지 않는 coordinated cutover를 권장한다. 전환 상세는 8절을 따른다.

정상 v2 Project 응답은 다음 12개 required 필드다. categoryId만 nullable이며 이름을 복사하지 않는다.

```text
id, revision, createdAt, updatedAt, name, subtitle, stack, progress,
currentMilestone, repositoryUrl, status, categoryId
```

Project POST는 name/stack 필수와 현재 optional subtitle/progress/currentMilestone/repositoryUrl 기본값을 유지한다. scope는 unknown field로 거절한다. PATCH는 revision과 현재 수정 가능한 필드를 사용하되 scope를 거절한다. categoryId를 포함한 same-value PATCH도 기존처럼 Project revision/updatedAt/dataRevision을 한 번 증가시킨다.

Task/Journal/Milestone의 생성·수정 body는 기존 projectId 관계를 유지하고 categoryId 입력을 허용하지 않는다. 정상 응답은 scope를 제거하고 **read-only nullable categoryId**를 추가한다. 기존 projectName은 현재 Project에서 유도한다. Category name/revision은 embedding하지 않고 별도 Category read/list에서 가져온다. Category 변경 시 하위 행 일괄 갱신은 없다.

기존 Task soft delete/restore 및 Journal/Milestone hard delete, 상태/기간/문자열 검증, archived 규칙을 유지한다. 특히 Task/Journal archived 신규 생성·타 Project에서 archived로 재배정 금지, Milestone archived 생성·재배정 허용은 바뀌지 않는다.

### 3. 필터·cursor·집계 계약

모든 대상 목록과 Task stats/Overview는 아래 **단일 query `category`**를 사용한다. body 관계 이름 `categoryId`와 구분하여 UUID 필드에 sentinel을 넣지 않는다.

| query | 의미 |
| --- | --- |
| 생략 또는 `category=all` | 해당 workspace의 모든 Category 및 미분류 |
| `category=<UUID>` | Project.categoryId와 정확히 같은 UUID |
| `category=uncategorized` | Project.categoryId IS NULL |

빈 값, `null` 문자열, 잘못된 UUID, 반복 query 및 scope query는 400 VALIDATION_ERROR다. UUID 대소문자는 허용하고 필터/cursor 바인딩에서는 canonical lowercase로 정규화한다. 이름 검색으로 Category를 선택하지 않는다. 소유하지 않거나 삭제된 Category는 404 RESOURCE_NOT_FOUND다. 기본 all은 Category 유무와 무관하게 동작한다.

Task/Journal/Milestone/Link/Overview의 projectId와 category는 **AND**다. 두 UUID의 소유권을 각각 검증한 뒤 소유 Project가 선택 Category에 속하지 않으면 200 빈 결과/0집계다. 하나를 무시하거나 자동 보정하지 않는다. projectStatus 등 나머지 필터와도 AND다. 정상 ownership 검증을 cursor decode보다 먼저 수행하는 현재 순서를 유지한다.

- Project status 기본 active, 하위 projectStatus 기본 all, Task deleted 기본 false, Milestone status 기본 open과 모든 기존 query/기간/sort/limit 기본값을 유지한다.
- 검색 필드 및 SQL literal escaping을 유지한다. Category 이름을 기존 query 검색 대상에 추가하지 않는다. Milestone에 새 검색 기능을 만들지 않는다.
- 4종 cursor는 v2 형식으로 교체한다. 서명에 resource, workspace, canonical category selector와 나머지 기존 필터·limit·sort를 모두 포함한다. 정렬 키와 keyset 비교 방향은 현재대로다. old cursor는 400 INVALID_CURSOR이며 클라이언트가 처음부터 재조회한다. 기존 서명 key 회전은 필요하지 않다.
- dataRevision을 cursor에 묶지 않는다. 현재와 같이 한 요청의 count/page는 REPEATABLE_READ이지만 페이지 간 membership 고정은 보장하지 않는다. Category 이동 중 오래된 cursor는 유효한 live keyset이나 이동된 행을 놓칠 수 있다. 클라이언트는 관련 mutation/freshness 변경 후 페이지 체인을 재시작한다. snapshot pagination은 별도 범위다.
- Task stats는 기존처럼 미삭제 Task의 todo/doing/done/total이며 projectStatus=all은 archived Project Task도 포함한다. status/deleted/limit/cursor query는 추가하지 않는다.

`GET /api/v2/overview?category=...&projectId=...` 응답:

```text
{ category: "all" | "uncategorized" | UUID, projectId: UUID | null,
  projects: { total: activeCount, archived: archivedCount,
              byCategory: [{ categoryId: UUID | null, total: activeCount, archived: archivedCount }] },
  tasks: { todo, doing, done, total }, asOf }
```

총계와 bucket은 동일 필터 교집합에서 산출한다. all은 전체 Category와 null bucket을 포함하고, 특정 UUID/uncategorized는 해당 bucket만 포함한다. 빈 Category는 0건으로 표시한다. Category 목록 createdAt/id 순서, null bucket 마지막. projectId가 있더라도 적용 가능한 bucket의 0값을 생략하지 않는다. asOf는 기존 observation time이며 commit watermark가 아니다.

`GET /api/v2/projects/category-counts`는 sidebar 전용 bounded 집계다. query를 받지 않고 workspace 전체를 SQL GROUP BY로 집계한다.

```text
{ items: [{ categoryId: UUID | null, active, archived }], totals: { active, archived } }
```

Category 0건 bucket과 마지막 null bucket을 포함한다. 통상 cap100+null=101개지만, 현재 Category list와 같이 이미 cap을 넘는 데이터가 있으면 잘라내지 않는다. 이름은 별도 Category map에서 읽는다. 클라이언트가 모든 Project를 수집해 sidebar를 만드는 방식을 사용하지 않는다.

### 4. 조회 비용·revision·freshness

필터는 Project.category_id에서 수행한다. Task/Journal/Milestone은 기존 소유권 join을 활용하고 Category table join은 ownership/reference 목록이 필요한 곳에 한정한다. Category 없는 Project를 잃는 inner join, per-row Category fetch, EAGER 전환, 중복 category 컬럼을 금지한다. Link는 nullable Project 때문에 LEFT JOIN이 필요하다.

기존 V13 workspace/category 인덱스를 재사용해 측정한다. category별 keyset 비용을 위해 `(workspace_id,category_id,created_at DESC,id DESC)` 또는 status를 포함한 변형을 후보로 평가한다. 둘 다 무조건 만들지 않는다. 하위 리소스의 workspace/project 정렬 인덱스와 함께 EXPLAIN/대표 데이터로 선택한다. Link에는 새 FK 참조 조회용 `(workspace_id,project_id)` 인덱스를 둔다.

| 이벤트 | 직접 revision 변화 | 관련 리소스·캐시 |
| --- | --- | --- |
| Project Category 지정/해제/같은 값 PATCH | Project +1, updatedAt 변경, workspace dataRevision +1 | Task/Journal/Milestone/Link/Dashboard row revision 변화 없음. 관련 목록·집계 membership 재조회 |
| Category rename(같은 normalized name 포함) | Category +1, updatedAt 변경, dataRevision +1 | Project 및 하위 revision 변화 없음. Category name map 재조회 |
| Category create/delete | create revision1 / delete row 제거, dataRevision +1 | Category collection/sidebar 갱신. Dashboard 참조 상태도 재조회 |
| Link 직접 수정/Project 재배정 | Link +1, collectionRevision +1, dataRevision +1 | 기존 reorder/delete/create semantics 보존 |
| Project Category 이동 때문에 Link 파생값 변경 | Link/collectionRevision 변화 없음 | collectionRevision 단독으로 membership freshness 판정 금지 |
| Dashboard PUT | Dashboard +1(최초 0→1), dataRevision +1 | full replacement; same-value도 증가 |
| 조회/실패/replay | 변화 없음 | replay body는 현재값으로 enrich하지 않음 |

모든 정상 v2 read 및 신규 mutation에 `X-Workspace-Data-Revision` 응답 header를 추가한다. Category v1 GET list/detail 및 신규 mutation에도 같은 header를 제공한다. 값은 기존 long의 **10진 문자열**이며 JavaScript Number로 강제하지 않는다. 헤더는 해당 body를 읽은 snapshot 또는 mutation transaction에서 획득한다. read를 별도 transaction으로 나눠 header만 더 최신으로 붙이지 않는다. Project detail도 이 목적에 맞게 일관된 read boundary를 사용한다. 실패/역사적 replay에는 이 header를 생략한다. 이는 전송 캐시 허용이나 ETag가 아니며 no-store를 유지한다.

클라이언트는 mutation 결과와 header로 의존 query를 무효화하고, focus/navigation 시 Category GET 등 정상 read로 다른 세션 변경을 확인한다. 같은 resource revision만 보고 파생 데이터를 재사용하지 않는다. 서로 다른 response header 값은 다른 관찰 시점임을 뜻하며 UI가 더 최신 데이터를 과거 응답으로 덮어쓰지 않게 한다. 여러 HTTP 요청 전체의 원자적 UI snapshot은 보장하지 않는다.

브라우저 인계 검증에서 실제 API 연결 방식으로 header를 읽을 수 있어야 한다. 이미 승인된 cross-origin 연결을 사용한다면 이 header만 expose하고 허용 Origin/credential/CSRF 정책을 확대하지 않는다. 새로운 CORS 허용 범위를 만드는 것은 본 계약에 포함하지 않는다.

### 5. Link 관계: 별도 승인이 필요한 권장안

Link는 **nullable links.project_id**를 갖고 `(workspace_id,project_id)`로 projects(workspace_id,id)를 참조하는 restrictive FK를 사용한다. 직접 Category 컬럼은 만들지 않는다. scope는 제거한다.

- 신규 POST의 projectId 생략/null은 미연결, UUID는 owned Project. PATCH 생략 보존/null 해제/UUID 재배정. 응답은 nullable projectId/projectName/categoryId를 제공하며 categoryId는 Project에서만 유도한다.
- active/archived 모두 연결/편집을 허용하는 안을 권장한다. archive/unarchive가 Link를 제거·재배정하지 않는다. 향후 Project 삭제가 생기더라도 FK가 Link를 cascade 삭제하지 않는다. 이번에는 Project 삭제 기능을 추가하지 않는다.
- 전체 Link는 미연결도 포함한다. 특정 Category는 해당 Project에 연결된 Link만, 미분류는 미연결 Link와 미분류 Project의 Link를 포함한다. projectStatus=active/archived는 미연결 Link를 제외한다. 특정 projectId와 category는 AND다.
- 기존 scope=all Link를 모든 Category에 자동 포함하는 규칙은 종료한다. 전역 position/id 순서, 전체 owned ID 순열 reorder, collectionRevision 및 500개/8 MiB 제한은 유지한다.
- **기존 Link는 모두 미연결로 남기는 것을 기본 권장**하되 이것은 분류 정보 손실을 수반하므로 승인이 필요하다. 보존할 Project 관계가 필요하면 사전 검토한 workspace+Link UUID→Project UUID 매핑만 적용한다. scope/이름/URL로 추론하지 않는다. 승인 없이 전환하지 않는다.

대안: 모든 신규 Link에 Project 필수는 더 엄격하지만 범용 Link를 표현하지 못하며 legacy 예외/완전 수동 매핑이 필요하다. 직접 Category 연결은 독립 분류 모델이며 “Project의 Category를 따른다”와 다르다. 이 둘은 본 권장안과 함께 구현하지 않는다.

### 6. Dashboard schemaVersion 2

GET/PUT `/api/v2/dashboards/home`은 `{schemaVersion:2,revision,widgets}`를 사용한다. widgets의 id/type/title/size, optional limit 및 현재 제한은 유지한다. scope와 top-level projectId를 제거하고 required `selection` 객체를 사용한다.

```json
{"kind":"all"}
{"kind":"project","projectId":"<UUID>"}
{"kind":"category","categoryId":"<UUID>"}
{"kind":"uncategorized"}
```

서로 배타적인 strict oneOf다. 다른 kind의 ID, null ID, 추가 필드 또는 scope는 400이다. Project/Category UUID는 workspace 소유권을 검증한다. Project 선택은 Project가 어느 Category로 이동하든 그 Project를 가리킨다. Category 선택은 현재 해당 UUID의 Project들을 가리킨다. 미분류도 정상 선택이다. all에는 미분류가 포함된다.

overview/board/journal/milestone은 네 선택을 허용한다. Link 관계 권장안 승인 시 links도 네 선택을 허용하며 limit 금지는 유지한다. deploy는 기존 placeholder로 보존하되 **all만 허용**, Project/Category 연결이나 limit은 받지 않는 안을 권장한다. 새 Deploy API/도메인은 만들지 않는다. 신규 기본 위젯은 기존 ID/type/title/size 순서와 all 선택을 사용한다(기본 deploy 유지 여부는 승인 항목).

Category 삭제는 Dashboard JSON 참조 때문에 차단하지 않는다. Project 참조만 CATEGORY_IN_USE 기준이다. Dashboard GET은 삭제된 Category 선택을 원래 UUID로 반환하고 해당 위젯의 read-only `selectionState="missingCategory"`를 표시한다. 정상 위젯은 `selectionState="valid"`다. 자동 전체·미분류·동명 Category로 바꾸지 않는다. 이 계산은 Dashboard revision을 바꾸지 않는다.

PUT은 새로 유효하지 않은 Category를 참조하면 404다. 단, owned 저장 Dashboard의 동일 widget.id 및 동일 selection UUID에 이미 있던 missingCategory 선택을 그대로 보존하는 full replacement는 허용한다. 새 widget ID로 복제하거나 다른 missing UUID로 바꾸는 것은 404다. 반환용 selectionState는 입력 불가이며 프런트가 round-trip 전에 제거한다. 삭제와 저장은 workspace lock으로 직렬화한다. 삭제 직후 GET이 실패하거나 전체 Dashboard가 사라져서는 안 된다.

Home 일시 필터는 **해당 렌더의 effective selection만 override**하는 안을 권장한다. 임시 선택 없음은 저장 selection 사용, 임시 all은 전체 사용이다. 저장 JSON·revision·PUT을 변경하지 않는다. 이 override UX는 승인 항목이며 두 종류의 selector를 이름으로 합치지 않는다.

기존 schemaVersion1 변환:

| 기존 widget | 제안 변환 |
| --- | --- |
| projectId 있음 | selection.kind=project, 기존 UUID 그대로. scope는 기존 서버가 all로 정규화했으므로 재분류에 사용하지 않음 |
| projectId 없음, scope=all | selection.kind=all |
| projectId 없음, scope=unity/server | **자동 매핑 금지**. owner/product가 승인한 widget ID별 selector manifest가 없으면 cutover 차단 |
| deploy | placeholder 유지 승인 후 all. 그 외 기존 scope가 있으면 역시 명시 승인 전 차단 |

id/title/size/type/order/limit은 보존한다. schemaVersion 변경은 Dashboard revision +1, updatedAt 변경으로 stale draft를 차단한다. overflow는 사전 검사 후 전환 중단이며 reset하지 않는다. 저장하지 않은 Dashboard는 계속 revision0의 가상 기본값을 반환하고 GET으로 행을 만들지 않는다.

### 7. Idempotency와 역사적 응답

API 경로 버전 변경은 생성 의도를 바꿔도 된다는 뜻이 아니다. 기존 key/body를 재작성하거나 새 key로 자동 전송하지 않는다. 클라이언트의 pending create는 원래 URL/body/key와 함께 보존한다.

**동일 resource의 v1/v2 생성은 기존 replay table의 canonical namespace를 공유한다.** 기존 `(workspace,POST,/api/v1/<resource>,key)`를 내부 저장 namespace로 계속 사용한다. v2 요청이라고 다른 path row를 생성하지 않는다. 이는 기존 행/checksum 변경 없이 concurrent cross-version duplicate를 차단하기 위한 저장 규칙이다. Category POST는 기존 namespace/hash 그대로다.

| 생성 계약 | fingerprint |
| --- | --- |
| 기존 Project scope 포함, categoryId omitted | 기존 v1 raw 7필드 framing/순서/UTF-8 hash 완전 보존 |
| 기존 Project scope 포함, categoryId present | 기존 v2 raw null/UUID 및 case 완전 보존 |
| 새 v2 API Project | hash version3: name,subtitle,stack,progress,currentMilestone,repositoryUrl의 현재 raw 값 + categoryId presence boolean + raw null/UUID. 기존 길이-prefix/null framing 유지 |
| 기존 Link | hash version1 label,description,url,scope 그대로 |
| 새 v2 API Link | hash version2 label,description,url + projectId presence boolean + raw null/UUID |
| 새 v2 API Task/Journal/Milestone | hash version2로 기존 요청 필드 순서/값/밀스톤 dueDate presence 유지. Category 및 Project의 현재 분류/이름은 hash에 넣지 않음 |
| Category | 기존 hash/version/24시간 그대로 |

새 fingerprint version은 응답 표현 변경도 분리한다. v2에서 unexpired v1 row를 같은 key로 만나면 body가 비슷해도 409 IDEMPOTENCY_KEY_REUSED이며 새 resource를 생성하거나 과거 body를 v2 shape로 변환하지 않는다. 현재 key scope의 기존 의도와 새 의도를 합치지 않는다. 프런트는 원래 v1 URL로 retry를 완료하고 정상 v2 GET으로 최신 데이터를 읽어야 한다.

전환 후 v1 POST는 **replay-only**다. auth/session/CSRF/Origin/workspace 및 기존 static parser/hash/각 resource ownership 전제는 유지한다. matching unexpired legacy row는 원래 HTTP status/body를 반환한다. Project pre-Category 13필드 및 이후 14필드, Task/Journal/Milestone의 scope/projectName, Link의 원래 collectionRevision까지 보존한다. Category rename/delete/reassignment로 snapshot을 enrich하지 않는다. Task replay의 original resource existence 및 Journal/Milestone의 삭제 후 replay 차이는 현재대로 유지한다.

| replay-only v1 요청 | 결과 |
| --- | --- |
| matching unexpired legacy hash | 원래 201 body, counter/expiry 변화 없음 |
| unexpired 동일 key의 다른 hash 또는 v2 row | 409 IDEMPOTENCY_KEY_REUSED |
| legacy key 없음/만료 | 410 API_VERSION_RETIRED, 새 생성 없음 |
| 인증/CSRF/기존 resource ownership 전제 위반 | 현재 401/403/404 우선순위 유지 |

v2 정상 재시도는 자신이 저장한 새 snapshot을 반환하고 live Category 상태로 바꾸지 않는다. 기간은 기존 24시간이며 expires_at을 연장/단축하지 않는다. 정상 만료 key 재사용 정책은 유지하지만, 클라이언트가 불확실한 옛 요청을 다른 의도로 자동 재사용해서는 안 된다.

구 snapshot을 새 domain record로 바로 역직렬화하지 않는다. 저장 body의 표현/필드 존재를 식별하는 legacy snapshot reader와 transport 경계를 둔다. scope와 colorToken을 포함한 JSON은 역사적 payload로만 취급한다. 모든 retained old rows(만료 포함)를 읽을 수 있어야 하며 미만료 행 rewrite/delete/강제 expiry는 없다. 전환 종료 후에도 raw JSON 기록은 그대로 남을 수 있다.

### 8. 데이터 이전과 배포 순서

V1–V14는 불변이다. 현재 사용 가능한 번호 기준 V15 이후 새 Flyway만 사용하며 실제 구현 직전에 재확인한다. 하나의 배포에서 미래 contract migration까지 모두 자동 실행되지 않도록 단계별 artifact에 해당 migration만 포함한다.

1. **준비/승인:** production read-only inventory, backup/restore 검증, 기존 Category/Project/replay checksums와 widgets/Link 원본 snapshot 확보. Link 정책과 모든 모호한 widget의 UUID별 mapping/명시 all 선택을 승인받는다. 데이터에 우연히 같은 이름이 있다고 병합하지 않는다. 미해결 mapping은 preflight 실패다.
2. **expand release(A, 예: V15):** nullable links.project_id + 소유권 FK/index, Dashboard schema 1/2 전환을 허용할 DB 제약 준비. 아직 기존 v1 writer만 활성화하며 새 v2 쓰기는 열지 않는다. 프런트는 새 DTO/selector/cache 및 pending-retry 보존을 준비한다. old reader가 새 Dashboard JSON을 읽는 혼합 배포를 허용하지 않는다.
3. **coordinated cutover(B, 예: V16):** 쓰기 drain 및 old instance 종료. 승인 manifest와 revision/counter overflow를 검증한다. Dashboard 변환, 승인한 경우 Link 관계 할당, 기존 scope 컬럼의 NOT NULL/default 해제 후 새 writer는 그 컬럼을 쓰지 않는다. v2 정상 API와 v1 replay-only reader, 새 프런트를 함께 연다. 기존 Project category_id/name/UUID/사업 값은 보존하고 scope-only Project는 미분류로 남는다.
4. **counter migration:** schema 표현 제거만으로 Project/Task/Journal/Milestone/Category revision을 바꾸지 않는다. 실제 Link assignment 변경만 해당 Link revision/updatedAt +1 및 workspace당 Link collectionRevision +1. 변환 Dashboard는 revision/updatedAt +1. cutover 자체는 기존 workspace당 dataRevision을 **한 번** 증가시키는 하나의 논리적 migration으로 기록한다(위 변환을 중복 더하지 않음). 이후 일반 mutation은 원래 규칙이다. overflow나 manifest precondition 불일치는 원자적으로 실패한다.
5. **replay drain window:** 마지막 v1 신규 생성 commit 시각 T와 최대 기존 expires_at을 기록한다. 최소 T+24시간 및 관찰된 모든 legacy 만료시각 이후(운영 clock margin 포함)까지 v1 replay-only를 유지한다. 기존 v1 read/PATCH/DELETE/PUT는 cutover부터 410이며 구 프런트는 업데이트해야 한다. 보안 endpoint/Category v1은 제외한다. 이 기간은 정상 이중 scope 모델 지원 기간이 아니다.
6. **contract release(C, 예: V17):** replay drain 완료를 확인하고 projects.scope/links.scope 및 관련 제약을 제거한다. 정상 scope parser/filter/DTO/집계/Dashboard 설정과 legacy retry HTTP surface를 제거하거나 scope-free 410 tombstone으로 바꾼다. v2 OpenAPI에는 scope/unity/server 분류가 없어야 한다. 역사적 Flyway/fixture/replay JSON과 OAuth scopes는 삭제 대상이 아니다.

v2 신규 row를 old binary가 읽거나 old binary로 쓰기를 되돌리는 것은 cutover 후 안전하지 않다. A 이전/old writer만 쓴 기간에는 원래 binary rollback을 검토할 수 있다. B 이후에는 호환 reader로 forward-fix가 기본이며, DB restore rollback은 cutover 이후 쓰기와 새 replay를 잃을 수 있어 별도 사고 대응 승인이 필요하다. C 이후는 drop column 복원만으로 rollback되지 않는다. 과거 scope/원본 Dashboard·Link는 승인한 보존 정책으로 export/backup하되 최종 업무 테이블이나 분류 로직으로 재도입하지 않는다.

이 Decision/Plan 승인은 구현·격리 검증을 위한 것이며 운영 배포, 운영별 매핑 또는 데이터 손실 승인까지 대체하지 않는다. 실제 배포는 준비된 결과와 manifest를 별도로 검토한다.

## Rationale and alternatives

nullable 유지와 Project-only 관계는 현재 Category 데이터를 가장 적게 건드리면서 미분류를 명확히 표현한다. 기존 scope를 새 Category로 자동 변환하거나 이름으로 병합하면 이미 수동 분류한 UUID의 뜻을 덮어쓰게 된다.

v2 및 짧은 coordinated cutover는 scope 없는 새 Project를 구 API의 필수 scope로 역변환해야 하는 모순을 피한다. 긴 v1/v2 동시 business 지원은 별도의 역사적 projection/권한/동기화 모델을 요구하므로 권장하지 않는다. 무중단 장기 양방향 호환이 필수라면 이 Decision을 재검토해야 한다.

UUID-only 파생 응답과 별도 Category map은 이름 변경 시 하위 resource 대량 갱신을 피한다. revision에 파생 freshness를 섞지 않고 snapshot header를 제공한다. cursor를 global dataRevision에 묶는 대안은 무관한 Category rename 하나로 모든 페이지를 실패시키므로 선택하지 않는다.

## Consequences and approval items

아래는 사용자가 명시적으로 승인한 선택이다. 운영별 UUID 매핑 승인은 이 승인에 포함되지 않는다.

| ID | 승인할 권장안 |
| --- | --- |
| A1 | Project nullable 유지, 기존 미분류 유지, scope에서 Category 자동 생성·할당 없음 |
| A2 | Link nullable Project, legacy Link 기본 미연결, 공통 자동 포함 종료, archived 연결 허용. 필요한 UUID별 예외 매핑은 별도 승인 |
| A3 | `/api/v2` breaking API + coordinated cutover + 최소 24h legacy replay-only, shared canonical key namespace. 장기 이중 writer 미지원 |
| A4 | category selector/AND/미분류, live v2 cursor, sidebar endpoint, decimal dataRevision header 및 cache 계약 |
| A5 | Dashboard selection/schema2/missingCategory 보존, 임시 selection override; 모호한 scope-only widget은 명시 mapping 전까지 배포 차단 |
| A6 | Deploy 기능 미구현, 기존 기본 deploy를 all-only 데모 placeholder로 유지 |

구현 Plan은 처음 proposed로 작성한 뒤 사용자 승인에 따라 실행한다. 이 계약을 변경하려면 새 승인이 필요하며, 테스트 통과를 위해 선택을 바꾸지 않는다. 공통 agent guide 변경은 현재 제안하지 않으며 별도 승인 없이 수정하지 않는다.

## Validation

실제 구현 전후 source/generated OpenAPI/DB를 대조한다. tenant/CSRF, Category CRUD/FK·cap·duplicate, 전체/UUID/null 필터·AND·검색·각 keyset·0 bucket·stats·archived/trash를 검증한다. Project 이동/Category rename 후 파생 응답·revision·header·Link collectionRevision·Dashboard missing 상태를 검사한다.

V14 populated upgrade와 과거 V12→V14→새 migrations를 모두 검증한다. 기존 Category/Project 관계, old migration checksums, 모든 resource old replay bytes/hashes/expiry 및 mixed v1/v2 same-key 동시 시도를 검증한다. assignment/delete, Category delete/Dashboard save, concurrent revision 및 transaction rollback은 원인 확인 가능한 잠금/독립 transaction 테스트로 검증한다. 브라우저 Swagger와 프런트 인계 schema 검증을 포함한 상세 실행 항목은 [PLAN-0011](../plans/PLAN-0011-project-category-only-classification.md)에 있다.
