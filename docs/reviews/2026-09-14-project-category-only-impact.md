# ProjectCategory 단일 분류 전환: 현재 구현 조사 및 프런트 인계

Date: 2026-09-14
Status: analysis / proposed contract, implementation not authorized

이번 문서는 현재 작업 트리와 실행 중인 서버를 조사한 결과다. 기존 Decision/Plan의 목표를 현재 구현으로 간주하지 않았다. 제안 계약은 [DECISION-0002](../decisions/DECISION-0002-project-category-only-classification.md), 실행 순서는 [PLAN-0011](../plans/PLAN-0011-project-category-only-classification.md)에 있다. 두 문서 모두 승인 전이다.

## 조사 근거와 한계

- AGENTS.md, architecture/API/database/Flyway/security/implementation guide, Decision/Plan guide와 runtime Plan template을 읽었다. Decision guide가 지시하는 DECISION-TEMPLATE.md는 저장소에 없으므로 기존 Decision의 Context/Decision/Rationale/Consequences/Validation 형식을 따른다. 공통 지침은 수정하지 않는다.
- 현재 작업 트리는 PLAN-0010 구현과 문서가 아직 커밋되지 않은 상태다. 이를 보존했다. 이 조사에서는 소스·테스트·마이그레이션·런타임 데이터를 변경하지 않는다.
- `http://127.0.0.1:8080/v3/api-docs`가 200을 반환했다. [현재 생성 OpenAPI](project-category-only-evidence/current-openapi.json)를 저장했다. 22개 경로, 40개 operation에 Category CRUD와 scope가 함께 존재한다. 18080에는 서버가 없었다. 별도 서버나 Flyway를 실행하지 않았다.
- PostgreSQL 16.4 컨테이너에서 `BEGIN READ ONLY` / `ROLLBACK`으로 Flyway·컬럼·인덱스·익명 건수만 확인했다. V1–V14가 모두 적용돼 있고 성공 상태다. 앞선 조사 문서의 V12 관찰은 현재 상태가 아니다.
- [DB 관찰 요약과 실제 Flyway checksums](project-category-only-evidence/database-observation.json)를 보존했다. 과거 계획의 관찰값을 복사한 것이 아니다.
- 현지 DB: active/unity Project 2개 모두 category_id NULL, Category 1개, Link 0개, schemaVersion 1 Dashboard 1개(6개 기본 타입, 모두 scope=all, projectId 없음). Project replay 2개 모두 unexpired이며 categoryId 없는 과거 응답이다. Task 2개, Journal 2개, Milestone 1개의 replay도 unexpired다. 실제 이름·키·응답 내용은 추출하지 않았다. 이 소규모 현지 데이터가 운영 전체를 대표한다고 가정하지 않는다.
- [소스 검색 목록](project-category-only-evidence/scope-source-inventory.txt)은 scope/deploy가 발견된 106개 파일이다. OAuth의 `scope`나 deployment 환경 설정처럼 분류와 무관한 용례도 포함하므로 단순 문자열 전역 삭제 목록으로 사용하면 안 된다. [336개 소스 파일 기준 해시](project-category-only-evidence/source-baseline-sha256.json)는 이번 문서 작업의 소스 불변 확인용이다.
- 이번 단계에서 실행 테스트 결과를 새로 주장하지 않는다. 이전 PLAN-0010 검증은 기준선일 뿐, 새 계약 검증을 대체하지 않는다.

## 실제 저장·도메인 영향

아래 Java 경로는 `src/main/java/com/kopite/devspace/` 기준이다. 역사적 SQL은 `src/main/resources/db/migration/`에 있으며 수정 대상이 아니다.

| 영역 | 현재 구현 근거 | 전환 영향 |
| --- | --- | --- |
| Project | `project/domain/Project.java`, `ProjectValues.java`; V2 projects.scope NOT NULL, ck_projects_scope unity/server; name 유일성 없음 | scope 및 colorToken() 제거. category_id UUID 단일 관계. 기존 Category FK/UUID·Project 식별자와 나머지 값 유지 |
| Category | V13 project_categories, uq_project_categories_workspace_id/name, fk_projects_owned_category; `projectcategory/` | 현재 ownership·이름·revision·100개 admission·제한 삭제 유지. 필터 ownership 검증과 Dashboard 참조 조회 추가 |
| Project API | `project/presentation/dto/{CreateProjectRequest,UpdateProjectRequest,ProjectRequestFields,ProjectResponse,LegacyProjectResponse}.java` | 현재 normal 14필드에서 scope/colorToken 제거한 12필드 정상 응답. old 13/14필드 replay는 별도 전환 경로에서 그대로 보존 |
| Project 명령 | `project/application/command/{CreateProjectCommand,UpdateProjectCommand,ProjectCommandService,ProjectRequestHash,ProjectCategorySelection}.java`, `application/model/ProjectSnapshot.java` | 새 생성 fingerprint와 old 읽기 분리. PATCH 생략/null/UUID 유지, archived 편집 유지 |
| Project 목록 | `ProjectListFilter`, `ProjectListRequest`, `ProjectQueryService`, `ProjectSearchAdapter`, `SignedProjectCursorCodec` | scope predicate 및 HMAC 바인딩 교체. status/query/limit/seek 유지 |
| Task | V4 tasks.project_id NOT NULL + fk_tasks_owned_project; scope/category 컬럼 없음. `TaskSnapshot`, `TaskResponse`, `TaskListFilter`, `TaskStatsRequest`, `TaskSearchAdapter`, `SignedTaskCursorCodec` | 현재 Project join으로 유도하는 scope를 categoryId로 교체. Task 행에 Category를 저장하지 않음. stats 동일 predicate 사용 |
| Journal | V6 journals.project_id + fk_journals_owned_project; `JournalSnapshot`, `JournalResponse`, `JournalListFilter`, `JournalSearchAdapter`, `SignedJournalCursorCodec` | Project.categoryId 기준으로 SQL 필터. 본문/제목/Project 이름 검색과 기간/정렬 유지 |
| Milestone | V8 milestones.project_id + fk_milestones_owned_project; `MilestoneSnapshot`, `MilestoneResponse`, `MilestoneListFilter`, `MilestoneSearchAdapter`, `SignedMilestoneCursorCodec` | Category 파생값 및 필터. completed/dueDate NULLS LAST/id 순서 유지. 현재 검색 파라미터는 없음 |
| Link | V10 links.scope NOT NULL DEFAULT all, CHECK all/unity/server. Project FK 없음. `Link`, `LinkValues`, create/update DTO/command, `LinkRequestHash`, `LinkSnapshot`, `LinkSearchAdapter`, `LinkQueryService` | 단순 category_id 치환 불가. nullable project_id와 소유권 보존 FK 권장(승인 필요). Project에서 categoryId 유도. 기존 글로벌 포함 규칙은 명시적으로 종료해야 함 |
| Link 순서 | V10 link_collections.revision, deferrable uq_links_workspace_position. `LinkCommandService`, `LinkLimits`, `LinkListResponse` | 전역 순서·전체 순열 reorder·500개/8 MiB 제한·collectionRevision 유지. Category 이동이 collectionRevision을 뜻하지 않게 freshness 분리 |
| Overview | `OverviewFilter`, `OverviewQueryService`, `OverviewProjectAdapter`, `OverviewResponse`, `OverviewProjectCounts`, `OverviewProjectsByScope`, `OverviewProjectScopeCounts`, `OverviewOpenApiConfiguration` | group by p.scope,p.status와 unity/server 고정 DTO 제거. UUID/null bucket 집계, Category별 sidebar 서버 집계 추가 |
| Dashboard | V12 dashboards.schema_version CHECK=1, widgets JSONB. `DashboardWidget`, `HomeDashboard`, command/query/repository, request strict parser와 OpenAPI configuration | schemaVersion 2 선택 객체. 기존 widgets JSON 변환, revision 충돌 보존. JSON에는 현재 FK가 없으며 서비스가 Project 소유권 검증 |
| 공통 API | `global/exception/ApiExceptionHandler.java`, 보안 경로와 `BackendOpenApiContractTests` | v2/retired 경로 보안과 신규 typed error/header 문서. session/CSRF/Origin/no-store 불변 |
| replay | V3/V5/V7/V9/V11/V14, 각 `*CreateReplayAdapter`/Store, `*RequestHash`, snapshots | Project뿐 아니라 하위 리소스와 Link에 scope 포함 응답이 저장됨. 고정 DTO를 단순 변경하면 과거 응답 필드 손실 위험 |
| Workspace | `workspace/domain/PersonalWorkspace.java`, `auth/presentation/dto/WorkspaceResponse.java` | dataRevision은 이미 내부 long counter이나 `/me`에는 미노출. revision과 별개 freshness header 제안 |

현재 Project 인덱스는 `(workspace_id,created_at DESC,id DESC)`, `(workspace_id,status,created_at DESC,id DESC)`, V13 `(workspace_id,category_id)`다. scope 전용 인덱스는 없다. Task/Journal/Milestone은 각각 workspace와 workspace/project 기반 정렬 인덱스를 가지고 있어 Project 필터 join을 평가할 출발점이 된다. Links는 workspace/position 유일 인덱스가 전역 정렬을 담당한다. Category 이름 유일 인덱스는 `C` collation이며 이름 기반 관계가 아니다.

## 현재 조회 동작: 반드시 보존할 부분

| 조회 | 현재 필터 기본값 | 현재 검색·정렬·집계 |
| --- | --- | --- |
| Projects | scope=all, status=active, query="", limit=20(1–100) | name/stack literal 대소문자 비구분 검색, createdAt DESC/id DESC, count + limit+1 keyset |
| Tasks | scope=all, projectId optional, projectStatus=all, query="", status optional, deleted=false, limit=20 | Task 제목/Project 이름 검색, createdAt DESC/id DESC. deleted=true는 휴지통만 |
| Task stats | scope=all, projectId optional, projectStatus=all, query="" | 미삭제 todo/doing/done 전체 group count. status/deleted/limit/cursor는 받지 않음 |
| Journals | scope=all, projectId optional, projectStatus=all, query="", from/to optional, sort=newest, limit=20 | body/title/Project 이름 검색, 양끝 포함 날짜 범위, entryDate/createdAt/id 동일 방향 newest/oldest |
| Milestones | scope=all, projectId optional, projectStatus=all, status=open, limit=20 | 검색 없음. completed ASC, dueDate ASC NULLS LAST, id ASC |
| Links | scope=all, query="" | label/description/url 검색, position/id ASC, 전체 bounded collection. 특정 scope에도 scope=all Link가 포함됨 |
| Overview | scope=all, projectId optional | Project total은 active만, archived 별도. Task 통계는 archived Project도 포함하고 삭제 Task는 제외. 동일 REPEATABLE_READ snapshot |

필터가 다른 소유 Project를 참조하면 404이고, 유효한 Project와 scope가 불일치하면 교집합 결과가 비어 있다. 이 교집합 원칙을 Category에도 적용한다. 네 cursor는 v1 HMAC에 workspace, scope, 해당 필터·limit·sort를 묶으며 workspace dataRevision을 묶지 않는다. 요청 하나 안의 count/page는 일관되지만 페이지 간 고정 스냅샷은 아니다.

Task/Journal의 신규 생성 및 다른 archived Project로 재배정은 금지된다. 같은 archived Project의 기존 리소스 편집은 허용된다. Milestone은 archived 생성/재배정도 허용한다. Project 삭제 API는 없으며 archive/unarchive는 categoryId를 보존한다.

## replay·revision·캐시에서 발견한 차이

- Project hash v1은 scope 포함 raw 7개 필드, categoryId를 보낸 경우 v2는 raw UUID/null을 덧붙인다. 대문자 UUID와 소문자 UUID, omitted와 null은 다른 생성 의도다.
- Project replay reader는 categoryId가 없는 경우 원래 JSON 문자열을 운반해 그대로 201로 반환한다. Category create도 24시간 raw-name replay이며 rename/delete 뒤에도 반환한다.
- Task/Journal/Milestone 생성 요청에는 scope가 없으므로 기존 요청 hash에 scope는 없다. 하지만 저장 snapshot/응답에는 scope와 projectName이 있다. Task replay는 원본 Task와 관련 Project 존재를 검사한다. Journal/Milestone은 원본 Project 존재를 검사하고 해당 리소스가 삭제된 뒤에도 과거 snapshot을 반환할 수 있다. 이 차이를 공통화하여 없애지 않는다.
- 모든 생성 replay의 현재 namespace는 workspace/method/고정 v1 path/key다. v2 경로를 별도 namespace로 만들면 동일 키가 새로운 생성으로 통과할 수 있으므로 전환 설계에 cross-version collision 차단이 필요하다.
- 쓰기는 workspace lock을 먼저 얻고 explicit resource revision 및 dataRevision을 증가시킨다. Project Category 변경은 Project만, Category rename은 Category만 수정한다. 후자의 경우 Project나 하위 리소스 revision만 보아서는 화면 최신성을 판정할 수 없다.
- HTTP no-store는 프런트 메모리 캐시 무효화를 대신하지 않는다. 현재 `/me.workspace.revision`은 dataRevision이 아니다. Link collectionRevision도 Category별 membership의 revision이 아니다. 따라서 별도 snapshot freshness 정보가 필요하다.

## Link와 Deploy: 결정할 경계

권장안은 Link에 **nullable Project UUID**를 추가하고 Category는 해당 Project에서만 유도하는 것이다. Project 없는 북마크는 categoryId=null이다. 새 Link의 Project도 선택 사항으로 제안한다. 이는 “모든 항목은 반드시 Project에 연결”이라는 필수 관계 정책과 다르므로 승인 항목이다.

기존 scope=all Link의 자동 공통 포함은 Category 모델에서 그대로 재현하지 않는다. 전체 조회에만 모든 Link가 포함되고, Category UUID 조회는 그 Category의 Project에 연결된 Link만 반환한다. 미분류 조회는 Project 없는 Link와 미분류 Project의 Link를 포함한다. 이 동작 변화도 승인해야 한다.

직접 links.category_id를 추가하면 Project와 무관하게 Category를 선택할 수 있지만, Project Category 변경을 따라가지 않는다. 요청한 Project 중심 분류 모델과 다른 대안이다. 모든 Link에 Project를 강제하면 기존 Link마다 명시적인 매핑이 필요하고 범용 북마크를 표현할 수 없다. 가짜 Project를 생성하거나 scope/name으로 자동 매핑하는 안은 권장하지 않는다.

백엔드에서 deploy는 `DashboardWidget.TYPES`와 기본 위젯 설정에만 있다. Deploy 테이블·엔티티·CRUD·상태 수집 API·외부 제공자 adapter는 없다. 프런트 [DeploymentStatus.tsx](../../../frontdev/src/features/operations/DeploymentStatus.tsx)는 하드코딩한 두 서비스를 “예시/실시간 연동 전”으로 표시한다. 이번 Plan은 Deploy 리소스를 만들지 않는다. 기존 deploy 위젯의 표시·유지 여부는 승인 항목으로 남긴다.

## 프런트 인계용 변경표: 승인 후 구현된 v2 계약

2026-09-15 PLAN-0011 구현 기준이다. 앞부분의 “현재 구현”은 2026-09-14 조사 시점의 역사적 기준선이며, 아래 표는 구현된 새 계약이다. 깨지는 정상 계약은 `/api/v2`로 분리하고 기존 auth/me/Category CRUD는 `/api/v1`을 유지한다. v1/v2 이중 쓰기 기간은 없다. [배포·캐시·Dashboard/Link 인계 지침](../project-category-only-rollout.md)과 [생성 OpenAPI](project-category-only-evidence/final-openapi.json)를 함께 사용한다. 운영에는 아직 배포하지 않았다.

| endpoint | request/query 변경 | response 변경 | error/revision/freshness |
| --- | --- | --- | --- |
| POST /api/v2/projects | name/stack 필수, 기존 optional 값 유지, scope 금지, categoryId omitted/null/UUID | scope/colorToken 없는 12필드 Project, categoryId 항상 존재하되 nullable | 201, Category foreign/missing=404, 새 hash v3. revision=1 |
| GET/PATCH /api/v2/projects/{id} | PATCH revision 필수, categoryId 생략 유지/null clear/UUID assign, scope 금지 | 정상 Project 동일 | archived 편집 가능, 성공 PATCH마다 revision/dataRevision +1 |
| GET /api/v2/projects | scope 대신 category=all 또는 uncategorized 또는 UUID | 기존 items/total/nextCursor 구조, 새 Project | v2 cursor; old cursor=400 INVALID_CURSOR |
| GET /api/v2/tasks, /journals, /milestones | scope 대신 category; 나머지는 위 현재 필터 표 그대로 | scope 제거, read-only nullable categoryId 추가; projectId/projectName 유지 | Project와 Category 필터는 AND; 소유 여부 먼저 검증 |
| POST/PATCH/GET/DELETE /api/v2/tasks[/{id}], POST /tasks/{id}/restore | 기존 task body/revision 및 삭제·복원 계약 유지, categoryId 직접 쓰기 금지 | 반환 Task에 최신 파생 categoryId. DELETE/restore의 현재 반환 구조 유지 | Task 자체 수정만 Task revision 증가; RESOURCE_DELETED/PROJECT_ARCHIVED 기존 의미 유지 |
| POST/PATCH/GET/DELETE /api/v2/journals[/{id}], /milestones[/{id}] | 기존 body/revision 관계 유지, categoryId 직접 쓰기 금지 | scope→categoryId; hard delete의 deletedId 응답 유지 | Journal과 Milestone의 archive/replay 차이 유지 |
| GET /api/v2/tasks/stats | category/projectId/projectStatus/query | counts/total/asOf 그대로 | 미삭제만, 상태별 제한 없는 집계 |
| GET /api/v2/overview | category/projectId | scope/byScope 제거, category/projectId와 projects.byCategory UUID/null bucket | active total/archived 별도 및 기존 Task 의미 유지 |
| GET /api/v2/projects/category-counts | query 없음; 모든 Category의 sidebar count | items[{categoryId,active,archived}], totals{active,archived} | SQL 집계, 0건 Category와 null bucket 포함; Category 리스트 순서 |
| POST/PATCH/GET/DELETE /api/v2/links[/{id}] | scope 금지; projectId 생략/null/UUID 선택 관계 | scope 제거, nullable projectId/projectName/categoryId 추가 | active/archived owned Project 허용; 기존 Link/collection revision 유지 |
| GET /api/v2/links | category/projectId/projectStatus=all/query | bounded items/total/nextCursor=null/collectionRevision 유지 | category UUID에 공통 Link 자동 포함 없음. projectStatus active/archived이면 Project 없는 Link 제외 |
| PUT /api/v2/links/order | 기존 collectionRevision, 전체 owned ids 순열 | 새 Link shape의 items와 collectionRevision | 필터 부분 집합 reorder 불가. Category 이동은 collectionRevision을 증가시키지 않음 |
| GET/PUT /api/v2/dashboards/home | schemaVersion=2, revision, widgets[].selection discriminated object | selectionState read-only 추가, scope/projectId top-level 설정 제거 | version1 write=400 UNSUPPORTED_SCHEMA_VERSION, stale=409 REVISION_CONFLICT |
| /api/v1/project-categories[/{id}] | 현재 CRUD/name/revision/key 규칙 그대로 | 현재 id/name/revision/timestamps만 | CATEGORY_IN_USE, CATEGORY_NAME_CONFLICT, QUOTA_EXCEEDED 유지; rename 후 Category 캐시 갱신 |
| 위 정상 조회·새 mutation | 별도 client 입력 없음 | X-Workspace-Data-Revision decimal-string header 추가 | workspace identity는 추가 노출하지 않음. body와 같은 snapshot/transaction의 counter |
| 기존 v1 생성 endpoint(전환 기간만) | **원래 URL/body/key 그대로** | 기존 scope 포함 snapshot 그대로 | replay-only. 신규/만료 요청 410, body 충돌409. v2로 키를 복사하여 재시도 금지 |

query `category`는 이름이 아니다. 빈 문자열/`null` 문자열/중복 query/추가 scope는 400 VALIDATION_ERROR, UUID 문법 오류도 400, 외부 또는 삭제 Category는 404 RESOURCE_NOT_FOUND다. 동시에 지정한 소유 Project와 소유 Category가 맞지 않으면 200 빈 목록/0집계다. Category 이름은 별도 Category map에서 표시한다.

프런트 캐시는 API version, category selector, 나머지 모든 filter를 키에 포함한다. Project 재분류 후 해당 Project detail/list와 Task/Journal/Milestone/Link 목록·통계·Overview·sidebar를 재조회하고 진행 중 페이지 체인을 재시작한다. Category rename은 Category map을 갱신하면 파생 표시 이름이 바뀐다. Category 삭제 후 저장 Dashboard selector는 자동 전체로 바뀌지 않고 missingCategory 상태가 된다. Home 임시 필터는 저장 PUT을 발생시키지 않는다.

## 조사 당시 승인 항목 및 주요 위험

아래 A1–A6는 이후 사용자가 모두 승인했고 DECISION-0002에 반영되었다. 운영별 manifest, 배포 시각과 backup/clock margin은 여전히 release gate다. 구현 검증 결과는 PLAN-0011 및 evidence index를 따른다.

기존 `docs/agents/ARCHITECTURE.md`는 package-by-feature/DDD/layer 규칙이며 고정 분류 enum을 요구하지 않는다. 현재 scope 호환 방향을 설명하는 DECISION-0001, PLAN-0010, `docs/project-category-deployment.md`와 기존 API review는 새 방향 승인 시 역사적 기록임을 명시해야 한다. 프런트의 기존 Category 병행 Decision 역시 새 인계 계약과 다르므로 프런트 담당 측에서 별도로 갱신해야 한다. 이번 단계에서는 해당 문서와 agent guides를 수정하지 않았다.

1. nullable Project와 미분류 유지, scope 기반 자동 생성/할당 없이 기존 Category UUID 보존.
2. 선택 Link→Project 관계, 미연결 Link의 미분류 의미, 공통 Link 자동 포함 종료 및 archived 관계 허용.
3. v2 정상 API와 v1 replay-only 전환 창, 원래 키 namespace 공유 및 새 fingerprint, 짧은 coordinated cutover 허용.
4. Category selector/AND/미분류/새 sidebar 집계/header, live keyset과 변경 후 재시작 계약.
5. Dashboard schemaVersion 2 selection, 삭제 Category의 missingCategory 상태, Home 임시 override, 모호한 기존 scope-only widget은 개별 승인 매핑 전까지 전환 차단.
6. deploy 위젯은 all 선택의 기존 데모 placeholder로 유지 제안. 실제 Deploy 기능·Project 관계는 이번 작업에서 정의하지 않음.

주요 위험은 미분류를 제거하는 Category inner join, 하위 행별 Category 조회, 옛 snapshot 고정 DTO 역직렬화에 따른 응답 변경, 경로별 키 namespace로 인한 중복 생성, 과거 Dashboard의 broadening, Project 이동을 Link collectionRevision으로 놓치는 캐시다. 모든 위험에 대한 승인 후 검증 항목은 Plan에 있다. 현지 데이터에서 모호한 Link/widget이 없더라도 이 정책과 populated fixture 검증을 생략하지 않는다.
