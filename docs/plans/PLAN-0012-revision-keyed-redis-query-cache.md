# PLAN-0012: Workspace revision 기반 Redis 조회 캐시 시범 도입

Status: `rejected`
Date: 2026-09-16

## Goal

PostgreSQL을 원본으로 유지하면서 반복 집계의 DB 비용을 줄인다. 인증, 소유권, 응답 스냅샷과 `X-Workspace-Data-Revision`의 관계, 수정 충돌, 멱등 생성 재처리, 커서 계약은 보존한다. Redis 장애 시 DB로 조회하며, 동일 백엔드 이미지를 Render에서 개인 서버로 이전할 수 있게 한다.

2026-09-16: 사용자 요청으로 계획을 취소했다. Session 1의 추가 검증 코드·테스트 설정·측정 자료와 실행 결과 반영을 롤백했다. Session 2는 시작하지 않았다. 아래 설계와 실행 절차는 종료된 계획의 기록이며 실행 승인을 의미하지 않는다. 설계 결정은 [DECISION-0003](../decisions/DECISION-0003-revision-keyed-query-cache.md)을 참조한다.

## Scope

- 첫 적용은 `GET /api/v2/projects/category-counts` 하나다. 다른 조회는 분석·계약 인계 대상이며 자동 확대하지 않는다.
- 승인 시 추가 의존성은 Boot 관리 `spring-boot-starter-data-redis`와 그 기본 Lettuce/Spring Data Redis 전이 의존성으로 한정한다. Redis 호환 저장소 1개를 제안한다.
- 세션 Redis 이전, 전체/기기별 로그아웃, XSS/CSP, 프론트 변경, 원본 DB 대체, Redis 관리 API는 제외한다. 인증 실패·401·403·CSRF 동작을 변경하지 않는다.
- no-store 변경, ETag/304, 새 집계/선택 API, 기능별 revision, Redis Cluster/Sentinel, 메시지 브로커/outbox/분산 락/추가 캐시 라이브러리는 기본 범위가 아니다.
- 운영 환경은 접속·변경하지 않았다. Session 1에서 사용한 격리 검증 산출물은 사용자 요청으로 제거했다. 애플리케이션 코드·의존성·운영 설정 변경은 없었다.
- 적용 지침: 루트 AGENTS, plans/AGENTS와 RUNTIME 템플릿, Architecture/Spring Boot/API/Database/Security/Flyway, Implementation의 Application/Ports and Adapters/Design Principles, decisions/AGENTS. `docs/agents/`는 변경하지 않는다. proposed 문서는 완료/승인 README에 등록하지 않는다.

## 1. 현재 구조와 조사 근거

조사 기준은 로컬 HEAD `a80fec5`다. 아래 경로는 `src/main/java/com/kopite/devspace/` 기준이다. 코드상 사실과 성능 추정을 분리한다. 배포된 커밋·실제 실행 설정과 로컬 소스가 같은지는 미확인이다.

### 1.1 데이터·인증·트랜잭션

| 확인한 코드 | 사실 및 의미 |
| --- | --- |
| `global/response/WorkspaceResponses.java`, `WorkspaceOpenApiConfiguration.java` | 서비스가 넘긴 observation 번호를 십진 문자열 헤더로 반환하고 no-store 적용. 응답 시점의 별도 최신 번호 조회는 없다. 정상 조회/새 mutation에 적용, 생성 replay에서는 번호를 생략한다. resource edit revision과 별개다 |
| `workspace/domain/PersonalWorkspace.java`, `workspace/infrastructure/PersonalWorkspaceJpaRepository.java` | `dataRevision`은 bigint 원본 필드. `recordBusinessMutation()`은 정확한 증가/overflow 검사. 소유자 UNIQUE와 workspace-first `PESSIMISTIC_WRITE`로 같은 작업실의 writer를 직렬화한다 |
| `project/.../ProjectCommandService`, `task/.../TaskCommandService`, `projectcategory/.../CategoryCommandService` | mutation과 작업실 증가가 같은 `@Transactional` 경계. replay 반환은 증가 전 분기. 프로젝트 변경, Task 생성/수정/휴지통/복구, Category 생성/수정/삭제를 확인했다 |
| Journal/Milestone/Link command, `dashboard/application/HomeDashboardCommandService` | 같은 workspace lock/증가 패턴. Link 순서 변경·Dashboard 같은 값 저장도 전체 변경 번호에 영향을 준다. 실패·rollback 시 증가도 rollback된다. DB 직접 수정은 이 Java 규칙을 자동 실행하지 않는다 |
| `auth/infrastructure/security/CurrentUserAuthenticationFilter`, `auth/application/CurrentUserService` | 요청마다 사용자 존재/disabled 여부와 소유 개인 작업실을 DB로 확인. 서비스에서도 resolve한다. Redis hit도 이 동작을 우회하지 않는다. 개인 작업실 하나/소유자 하나 모델이며 클라이언트가 캐시 workspaceId를 지정하지 않는다 |
| `overview/application/OverviewQueryService`, `TaskQueryService` | Overview는 RR 트랜잭션에서 프로젝트 집계와 Task stats를 합친다. stats의 REQUIRED 트랜잭션은 외부 RR에 참여하며 최종 revision은 stats에서 전달된다 |
| `ProjectQueryService`, `CategoryQueryService`, `HomeDashboardQueryService` | 목록/상세/집계는 RR에서 읽고 DTO/snapshot에 관측 번호를 함께 전달한다. annotation 존재가 모든 호출자의 isolation을 강제하는 것은 아니므로 외부 트랜잭션 참여도 검증해야 한다 |
| `ProjectCreateReplayAdapter`와 snapshot의 `@JsonIgnore dataRevision` | PostgreSQL에 원래 생성 결과를 보존. replay 역직렬화 결과는 최신 observation을 갖지 않는다. 캐시용 직렬화를 replay 직렬화와 혼용하면 revision 유실/역사 결과 오표시 위험이 있다 |
| `build.gradle`, `application.yml` | Java 21, Boot 4.1.1, springdoc 3.1.0. PostgreSQL/JPA/Flyway, Actuator 존재. Redis starter, Redis 설정, `@Cacheable` 조회 경로는 발견되지 않았다 |

사용자가 제시한 구조는 소스의 의도와 일치한다. 다만 프론트의 실제 Query/Store 구현은 이번 백엔드 소스 조사로 재검증하지 않았고 사용자 제공 배경으로 취급한다. 변경 번호는 push 알림이 아니다. 프론트가 요청하지 않으면 타 기기의 변경을 즉시 알 수 없다.

**선행 검증 사항:** `spring.jpa.open-in-view` 명시 설정이 없고 같은 요청에서 사용자/작업실 entity를 반복 resolve한다. 이미 로드된 entity의 번호를 새로운 DB 관측 번호라고 간주하면 안 된다. 캐시 admission과 cache fill에는 DB scalar/projection으로 읽은 번호를 사용하고, 상위 트랜잭션이 없는 호출 경계와 실제 HTTP 경로의 본문/번호 일치를 검증해야 한다. 전역 OSIV 변경을 본 계획의 부수 작업으로 하지 않는다.

### 1.2 조회 후보별 평가

표의 쿼리 수는 **인증/소유권/작업실 조회를 제외한 코드상 업무 조회 호출 수**다. 실제 SQL 수·시간은 Hibernate 1차 캐시, 실행 계획, 데이터 크기에 따라 달라지며 측정값으로 단정하지 않는다.

| 후보 / 호출 흐름 | 조건·비용·반복성 | 인덱스/N+1/최적화 우선순위 | 영향 범위와 캐시 판단 |
| --- | --- | --- | --- |
| Overview: Controller → OverviewQueryService → ProjectCategoryAggregation + TaskQueryService.stats | `GET /api/v2/overview`; category=all/uncategorized/UUID, optional projectId 교집합. all은 Category 목록 + 프로젝트 GROUP BY + Task/Project JOIN GROUP BY, 보통 업무 조회 3개. 화면 진입/필터 재사용 가능성 높음(호출량 미측정) | aggregate DTO라 프로젝트별 N+1은 보이지 않음. resolve와 category/project 소유권 확인이 중복됨. Task 빈 검색어 predicate 개선과 실제 join 계획 확인 우선 병행 | Project 생성/이름/카테고리/상태, Task 생성/상태/이동/삭제/복구, Category bucket 변화. 절감 여지는 가장 크지만 필터 조합·asOf 계약·중복 검증 비용 때문에 2차 후보 |
| Task stats: Controller → TaskQueryService.stats → TaskSearchAdapter.counts | `GET /api/v2/tasks/stats`; category, projectId, projectStatus, query. deleted=false 고정; status/deleted/limit/cursor 미허용. JOIN + GROUP BY 1개, 모든 상태 0 포함. 검색어별 키 다양 | `ix_tasks_workspace_status_live`(deleted_at IS NULL), workspace/project/created 인덱스. 매 요청 `%query%`, 빈 문자열에도 lower LIKE. 단순 B-tree로 임의 substring 가속 보장 불가. join/빈 검색 조건 검토 우선; 근거 없이 pg_trgm 추가 금지 | Task와 Project 이름/카테고리/상태 변경에 영향. 재사용성이 높은 빈 query에서 효과 가능. asOf 정책과 필터 정규화 확인 후 확대 |
| Project category counts: ProjectController → OverviewQueryService.categoryCounts → ProjectCategoryAggregation | `GET /api/v2/projects/category-counts`; 파라미터 없음. Category 목록 + Project GROUP BY 2개. 최대 100개 Category와 마지막 미분류 bucket, 0건도 포함 | `ix_projects_workspace_category`, workspace/status/created, owned UNIQUE 존재. 전체 작업실 집계는 행 수에 비례하며 N+1 없음. 실제로 이미 저렴하면 Redis RTT가 더 클 수 있음 | Project 생성/상태/카테고리와 Category 생성/삭제/정렬 데이터에 영향. 키 수가 작고 asOf/커서 없음. **정합성 시범 1순위**, 성능 측정 gate 필수 |
| Category 목록: CategoryController → CategoryQueryService.collection/list | `GET /api/v1/project-categories`, 파라미터 없는 전체 목록. createdAt,id ASC. 생성 cap 100. 업무 SELECT 1개 | workspace/id 및 workspace/name UNIQUE가 있음. created 순서 전용 인덱스는 없음; 100행 정렬 때문에 추가 인덱스/Redis를 먼저 도입할 이유가 약함. collection→list의 resolve 중복 | Category CRUD에만 본문 변화. 표시 이름의 프론트 재사용성이 높지만 서버 비용 낮음. 프론트 메모리 캐시 및 중복 resolve 진단 우선 |
| Project 목록: Controller → ProjectQueryService.list → ProjectSearchAdapter | `GET /api/v2/projects`; category, status(active 기본), query, limit 1..100(기본20), cursor. count + limit+1 SELECT 2개. createdAt DESC,id DESC keyset | `ix_projects_workspace_created`, `ix_projects_workspace_status_created`, category 인덱스. LIKE 검색은 별도 병목 후보. entity에 UUID categoryId만 있어 snapshot 변환의 category별 lazy N+1은 안 보임. count 비용/검색 계획 먼저 측정 | Project 생성/편집/상태/카테고리에 영향. page/query별 고카디널리티와 전체 revision churn으로 효과 불명. 초기 제외 |
| Project 상세: ProjectQueryService.get → ProjectRepository.findOwned | `GET /api/v2/projects/{id}`; owned UUID point read 1개 + resolve 반복 | PK/owned composite UNIQUE. N+1 징후 없음. 이미 낮은 DB 비용 | Project 변경에 영향. 캐시 운영·revision 확인 비용이 원 쿼리와 비슷할 수 있어 제외 |
| Project 선택 목록 | 전용 options/selector GET은 현재 ProjectController에 없음. 프론트가 목록 API를 선택 UI에 쓰는지는 별도 확인 필요 | 목록 전체 페이지를 선택 목록으로 반복 읽는 문제라면 Redis만으로 HTTP 수/DTO 크기/페이지 순회를 해결 못함 | 별도 선택 API 신설을 가정하지 않는다. 실제 호출 확인 후 프론트 캐시 또는 좁은 조회 계약 필요성을 별도 제안 |
| Home Dashboard: Controller → HomeDashboardQueryService → dashboard find + DashboardReferences | `GET /api/v2/dashboards/home`은 **설정**만 반환. 기본값은 revision 0이며 저장하지 않음. 저장된 경우 workspace/home PK 1개 + 참조 Project/Category 각각 최대 1회 IN 조회 | 참조 검증은 이미 batch라 위젯당 N+1 아님. widget 배열의 총 개수 상한은 코드에서 확인되지 않아 IN/JSON 크기는 관측 필요 | Dashboard 저장, Category 삭제에 따른 missingCategory, 참조 Project에 영향. 실제 위젯 데이터는 Overview/Task/Journal/Milestone/Link API로 별도 조회. 설정 캐시의 기대 이득 작음; 초기 제외 |

Home의 Link 조회도 Project left join으로 표시 이름/categoryId를 파생하며 위치 순서를 유지한다. Category 이름은 Project/Task 본문에 복사되지 않고 별도 Category 목록에서 조합한다. 따라서 Project 이름 변경은 Task/Journal/Milestone/Link의 표시 데이터와 검색 결과에, Category 이름 변경은 Category 표시 캐시에 영향을 준다. workspace revision 방식은 직접 영향이 없는 변경에도 보수적으로 새 키를 사용한다.

### 1.3 기존 성능 자료와 한계

- 기존 `OverviewQueryTests`에는 소량→121개 데이터에서 SQL 수가 행 수에 따라 증가하지 않는 assertion과 EXPLAIN 수집 코드가 있다.
- 로컬 비추적 산출물 `build/reports/dashboard-overview-api/query-plan.txt`에서 프로젝트 집계 121행/0.117ms, Task join 집계 121행/1.694ms, join에서 7,260행 제거와 shared hit 4,421이 기록되어 있다. **과거 테스트 fixture 결과**이며 Render의 API 시간·현재 데이터 규모·현행 모든 predicate의 실행 계획이 아니다. 재현성을 위해 이 파일에 의존하지 않는다.
- 이 자료는 작은 데이터에서는 Redis 이득이 없을 수도 있고 Task join/통계 추정 확인이 필요하다는 단서다. 실제 병목 확정·속도 향상 비율·p95 목표의 근거로 쓰지 않는다.
- 현재 SQL에는 N+1을 피하는 집계/명시 join/batch가 이미 있다. 가장 확실한 후보는 반복 aggregate, 반복 resolve, 빈 검색 predicate, 항상 수행하는 목록 count다. 전체 데이터 크기·필터 분포·API 호출률·쓰기 비율·DB 통계 최신성·네트워크 RTT는 미확인이다.
- 다음 진단은 기존 로그/metrics와 읽기 전용 schema/index/통계부터 한다. 운영 `EXPLAIN ANALYZE`/대량 조회/ANALYZE는 하지 않는다. 부하가 없는 추정 EXPLAIN도 운영 접근 승인이 있을 때만 한다. 실제 SQL 및 재현 데이터의 ANALYZE/실행 계획은 승인된 격리 환경에서 수집한다.

## 2. 전략 비교와 권장안

| 판단 항목 | A: Cache-aside + TTL + after-commit 삭제 | B: DB revision을 키에 포함 |
| --- | --- | --- |
| 정상 hit 비용 | 인증/권한 DB 확인 + Redis. 번호 검증을 생략하면 최신성 요구 충족 불가 | 인증/권한 + 원본 DB revision 확인 + Redis. DB를 완전히 없애지 않음 |
| writer 커밋 직후 | DB commit과 Redis 삭제는 원자적이지 않아 그 사이 stale hit. 삭제 실패/프로세스 종료 시 TTL까지 잔존 | commit과 번호 증가가 원자적이면 새 reader는 새 키만 조회 |
| 늦은 이전 read | reader R 시작 → writer R+1 commit/delete → reader R이 같은 키에 put하여 stale 재삽입 | reader R의 put은 R 키만 갱신. R+1 키에 절대 저장하지 않음 |
| 장애 | after-commit 삭제 보장/재시도/전달 실패 처리가 필요. DB 성공을 Redis 실패로 되돌릴 수 없음 | GET/SET 실패는 miss/bypass. writer가 Redis에 의존하지 않음 |
| TTL 역할 | TTL만으로는 커밋 이후 정합성 보장 불가 | 도달 불가능해진 이전 버전 키의 보관/메모리 정리 |
| 비용/복잡성 | 기능별 광범위 무효화 목록과 경쟁 방어가 필요. double-delete만으로 엄밀히 해결되지 않음 | revision 조회 비용, 쓰기마다 miss, 이전 키 메모리 비용. 훨씬 단순한 correctness proof |

**B를 제안한다.** A의 cached revision을 매번 PostgreSQL과 비교해 해결한다면 사실상 B와 같은 원본 버전 검증 비용을 부담한다. outbox/분산 락을 추가하여 A를 복잡하게 만드는 이점이 현재는 없다.

시범 API 선택은 **최대 성능 이득을 입증했다는 뜻이 아니다**. category-counts는 작은 키 공간, 모든 Category/미분류 bucket 불변 계약, 시간 필드 부재로 검증이 쉽다. Session 1 종료 시 구현 보류 또는 최소 비교 구현 진입을 판단한다. 이득 가능성이 낮으면 전체 구현을 끝내고 비활성으로 남길 의무는 없다. 최소 비교에서도 효과가 없으면 운영 기능으로 확대하지 않고 후보 변경 또는 계획 종료를 제안한다. category-counts를 Overview 등 다른 API로 자동 교체하지 않는다.

## 3. 정합성 및 실행 경계

### 3.1 키와 저장 내용

제안 키:

```text
devspace:{namespace}:query:project-category-counts:v1:{workspaceId}:{dataRevision}:{queryHash}
```

- namespace는 배포 환경과 **DB 데이터 세대**를 구분한다. 예: `render-dev-epoch1`. cache schema v1은 HTTP API v2 및 Dashboard schemaVersion=2와 별개다.
- workspaceId는 인증된 userId로 원본 DB에서 결정한다. 현재 owned personal workspace 전체의 동일 결과이므로 userId를 중복 넣지 않아도 격리된다. membership/사용자별 필터가 도입되면 schema와 권한 범위 재설계 전까지 캐시 금지.
- queryHash는 정규화한 명시적 descriptor의 UTF-8 canonical JSON을 SHA-256으로 해시한다. 시범은 `{"kind":"project-category-counts","query":{}}` 고정. delimiter 이어붙이기, Map 임의 순서, Java hashCode를 사용하지 않는다. envelope에 descriptor도 보관하여 hash 일치만 신뢰하지 않는다.
- 확장 시 허용된 필드/기본값을 먼저 파싱하고 UUID canonicalization은 기존 CategoryFilter 규칙을 따른다. unknown/repeated/empty invalid 값은 캐시 접근 전 거절. query 문자열은 현재 계약대로 유지하며 임의 trim/lowercase/NFC를 하지 않는다. DB case-folding과 Java 정규화가 같다고 가정하지 않는다.
- 목록 확대 시 filter/status/deleted/date/sort/limit/검증된 cursor 위치까지 포함해야 한다. 사용자 입력 raw cursor를 먼저 키로 써서 서명 검증을 생략하지 않는다. 이번 시범에는 목록/커서 캐시 없음.
- immutable cache envelope: schemaVersion, kind, namespace, workspaceId, **dataRevision 십진 문자열**, canonical descriptor, ordered bucket payload, cachedAt(내부 진단용). revision과 payload는 한 번의 RR DB 읽기에서 함께 생성한다.
- ORM entity/proxy, HttpSession/CSRF/OAuth token, 실제 session ID, 응답 Cookie/security header, replay 결과/오류 응답은 저장하지 않는다. schema별 명시적인 JSON DTO를 사용하며 Java serialization/광범위 polymorphic default typing 금지. 기존 Boot 4의 `tools.jackson` JsonMapper와 호환성을 테스트한다.
- 누락 필드, schema/kind/namespace/workspace/revision/descriptor 불일치, 잘못된 UUID/음수 count/중복 bucket/미분류 위치 오류, 크기 초과는 miss. parse 가능한 잘못된 구조도 거절한다. 임의의 정상 모양 숫자 위조까지 DB 없이 탐지할 수 있다고 주장하지 않는다; Redis 쓰기 권한을 서비스로 제한한다.

### 3.2 DB 트랜잭션 밖에서 Redis 접근

캐시 service 전체를 `@Transactional`로 감싸거나 controller에서 RedisTemplate을 호출하지 않는다. 기존 application-defined port + infrastructure adapter를 사용한다.

1. 기존 Security filter 및 요청 검증을 그대로 통과한다. admission 전에 off/cooldown 상태를 확인한다. off 또는 cooldown 중 복구 probe로 선정되지 않은 요청은 admission·Redis·single-flight를 생략하고 기존 RR DB reader로 직접 진입한다. 이 reader의 사용자/disabled·소유권 확인과 동일 snapshot의 본문/번호 반환은 유지한다. 생략 대상은 캐시용 추가 admission이며 인증이 아니다.
2. 사전 bypass가 아닌 요청만 짧은 읽기 application transaction에서 **fresh scalar/projection**으로 현재 user의 존재/disabled·workspace 소유권·dataRevision R을 관측하고 immutable admission을 반환한다. Redis에 revision을 대신 저장하거나 JVM에서 장시간 재사용하지 않는다. 동일 PostgreSQL primary를 사용한다.
3. 트랜잭션 종료 후 R의 키를 GET한다. envelope 모든 identity 필드와 R을 검증한다. hit면 **저장된 payload와 저장된 R**을 그대로 사용한다. admission의 번호를 다른 payload에 덧붙이지 않는다.
4. miss/Redis 오류면 별도 DB snapshot reader의 RR transaction에서 권한을 다시 확인하고 번호 S + Category 순서 + Project aggregate를 같은 snapshot으로 읽는다. 먼저 얻었던 R과 S가 다를 수 있다. S 본문을 R 키로 저장하거나 R 헤더로 반환하지 않는다.
5. reader의 transaction이 성공적으로 종료되고 해당 요청에서 Redis 사용이 허용된 경우에만 envelope(S)를 S 키에 TTL과 함께 원자적으로 SET한다. GET 연결/timeout 실패를 만난 요청과 사전 bypass 요청은 SET도 생략한다. SET 실패여도 DB 결과(S)를 정상 반환한다. 미완료/rollback된 read transaction 결과를 publish하지 않는다.
6. `WorkspaceResponses`는 결과에 동봉된 revision으로 기존 DTO/200/no-store를 만든다. off 상태에서는 Redis 접속/초기 handshake도 필요하지 않게 한다. admission 이후 다른 요청에 의해 cooldown이 열리면 이미 발생한 admission 비용은 남지만 이후 Redis 시도는 생략할 수 있다.

Admission과 miss reader는 별도 Spring bean의 public transaction 경계로 두어 self-invocation을 피한다. 상위 transaction이 없는 호출 흐름을 보장하고 실제 transaction 활성 상태를 테스트한다. 캐시 orchestration을 Overview의 외부 RR 내부에서 호출하지 않는다. Overview는 이번에는 기존 직접 DB 집계 경로를 유지한다. 불필요한 REQUIRES_NEW/전역 EntityManager.clear를 도입하지 않는다.

hit에서 cached payload의 원래 MVCC snapshot과 현재 admission snapshot이 물리적으로 같은 snapshot인 것은 아니다. **같은 원본 revision은 해당 workspace의 모든 의존 데이터가 동일한 논리 상태임을 보장한다는 invariant**로 재사용한다. 캐시 payload와 저장된 번호 자체는 항상 같은 snapshot 출처여야 한다. 사용자 disabled/권한 등 revision 밖의 보안 상태는 별도로 매 요청 확인한다.

### 3.3 보장과 한계

- PostgreSQL RR은 transaction 시작 호출 시각이 아니라 첫 유효 query에서 정해진 snapshot을 사용한다. 그 이후 commit된 변경을 기존 진행 중 응답에 끼워 넣지 않는다. [PostgreSQL isolation 문서](https://www.postgresql.org/docs/current/transaction-iso.html)
- hit 경로의 관측점은 admission query, miss 경로는 DB reader snapshot이다. 관측 이후 writer가 commit하면 이전 번호의 응답이 늦게 도착할 수 있다. 이는 기존 snapshot read 계약이며 응답 송신 순간의 절대 최신성을 보장하지 않는다.
- mutation 성공을 받은 뒤 새로 시작한 primary DB read는 그 변경 이상의 번호를 관측한다. 오래된 결과에 새 번호를 붙이는 것은 금지한다. 프론트는 이미 관측한 번호보다 작은 지연 응답을 버린다.
- R read 지연 → R+1 commit → R 캐시 fill의 경우 R 키만 생성된다. 이후 admission R+1은 이를 조회하지 않는다. 여러 백엔드에서도 같은 원리다. writer의 Redis 삭제·Pub/Sub는 correctness 조건이 아니다.
- 업무 변경과 번호 증가가 함께 rollback되면 키 공간도 변하지 않는다. 정상적인 이전 committed read를 R 키에 저장하는 것은 안전하다. command transaction의 중간 상태는 캐시하지 않는다.
- DB 직접 SQL/배치/향후 writer가 번호 증가를 빠뜨리면 B도 안전하지 않다. 현재/향후 업무 writer inventory를 gate로 확인한다. 테스트 fixture의 raw SQL 변경에도 번호 증가를 함께 넣거나 매 케이스 namespace를 격리한다.
- backup restore로 revision이 되돌아가면 같은 번호가 다른 데이터에 재사용될 수 있다. DB 복원/데이터 교체 시 **새 namespace**와 빈 Redis로 시작한다. 단순 TTL 대기는 복구 정합성 수단이 아니다.
- 모든 workspace 변경이 모든 조회 키를 바꾼다. Journal/Link/Dashboard 저장으로 category-counts도 miss가 된다. revision당 read 수, 무관한 쓰기 비중을 측정한다. 기능별 counter는 측정상 필요성과 모든 writer의 영향 표가 입증될 때만 별도 승인/마이그레이션으로 제안한다.

## 4. 장애·만료·운영 설계

- GET timeout/connection refusal/DNS·인증·TLS 실패/corrupt data는 bounded cache miss로 처리하고 정상 DB reader로 간다. DB 오류·인증 오류·프로그래밍 오류를 광범위 catch로 숨기지 않는다.
- SET/serialization 실패는 이미 얻은 정상 DB 응답을 실패시키지 않는다. 불량 항목 정리는 best effort이며 정리 실패 때문에 DB 응답을 막지 않는다. stale 이전 revision을 장애 대체값으로 제공하지 않는다.
- 요청당 Redis 재시도는 하지 않는다. 짧은 connect/command timeout과 bounded disconnected-command queue를 설정하고 끊긴 상태에서는 빠르게 실패시킨다. 연속 실패 시 인스턴스 내 짧은 bypass cooldown, 복구 시 소수 probe만 허용한다. 새로운 circuit-breaker dependency 없이 좁은 adapter 정책으로 구현한다.
- cache read timeout 뒤 같은 요청에서 write까지 다시 timeout을 기다리지 않도록 그 요청은 Redis를 bypass한다. GET/SET/연결 포함 총 추가 지연 budget을 테스트한다.
- Redis는 선택적 가속기다. 장애 상태에서도 백엔드 기동/DB endpoint 이용이 가능해야 한다. Redis health를 필수 readiness로 연결해 서비스 전체가 제거되지 않게 한다. 기존 공개 health/auth 정책은 바꾸지 않는다.
- stampede: TTL ±20% jitter; 원자적 SET+expire. 운영 후보의 single-flight는 아래 4.4절의 범위에서만 계산을 공유한다.
- 장애 시 업무 집계는 DB로 돌아가지만 총 비용이 항상 baseline과 같지는 않다. 첫 Redis 실패 요청에는 추가 admission과 실패 대기가 남고, 사전 off/cooldown 요청만 추가 admission 없이 DB reader로 직접 간다. 경로별 비용은 4.3절로 구분한다. 캐시 없는 트래픽뿐 아니라 장애 전환 순간의 추가 비용·동시 fallback도 기존 HTTP worker/Hikari pool/DB connection 상한으로 검증한다. baseline/합의 budget을 초과하면 활성화·확대를 보류하며 무한 큐/재시도/stale 허용으로 숨기지 않는다. 새로운 rate-limit/503 계약은 별도 결정이다.
- key TTL 제안 60초 ±20%, hit 시 연장하지 않음. 단위 workspace/버전/descriptor별 저장이므로 이전 키 개수는 쓰기 속도×TTL×활성 조건 수에 영향받는다. TTL은 최신성 보장 시간이 아니라 정리 기간이다.
- 메모리 제안: 전용 캐시 `allkeys-lru`, 개인 서버 `maxmemory 64mb` 시작 후보, container limit 예:128MiB 이상에서 RSS/fragmentation/client buffer 측정 후 조정. Render는 제공 플랜 상한 내에서 정책 선택. 한 payload 64KiB 초과면 캐시 저장 생략하되 API 결과는 유지. 상한 값은 성능 목표가 아닌 임시 보호값이다. [Redis eviction 문서](https://redis.io/docs/latest/develop/reference/eviction/)
- 측정 지표: 요청 수, eligible hit/miss/bypass와 이유, Redis GET/SET latency/timeout/error, deserialize rejection, payload bytes, key 수/expiry/eviction, used_memory/RSS, connection 수, DB aggregate 횟수/SQL 수/시간, single-flight 합류·대기·초과, DB pool pending, API p50/p95. user/workspace/query/key를 metric label로 넣지 않는다. 인증 비밀이나 원본 검색어를 로그에 남기지 않는다.

### 4.1 환경변수 제안 — 이번에는 파일/운영값 변경 없음

| 제안 변수 | 기본/검증 초깃값 | 역할 |
| --- | --- | --- |
| `QUERY_CACHE_ENABLED` | `false` | off면 Redis 없는 기존 DB 조회 |
| `QUERY_CACHE_NAMESPACE` | 로컬 `local-epoch1`; 배포 enabled 시 명시 필수 | 환경·DB 복원 세대 격리; 동일 배포 인스턴스 간 동일 |
| `QUERY_CACHE_TTL` | `60s` | 이전 revision 키 정리; jitter 20% |
| `QUERY_CACHE_MAX_ENTRY_BYTES` | `65536` | 초과 시 put 생략 |
| `QUERY_CACHE_FAILURE_COOLDOWN` | `5s` | 실패 후 DB bypass; 소수 복구 probe |
| `QUERY_CACHE_SINGLE_FLIGHT_MAX_KEYS` | `128` | JVM 내 진행 중 키 상한 |
| `QUERY_CACHE_SINGLE_FLIGHT_WAIT` | 잠정 `100ms` | 실제 집계 시간 분포와 API 지연 예산으로 확정; 1회 집계 보장값 아님 |
| `REDIS_URL` | 로컬 예시 `redis://localhost:6379`, enabled 배포에는 필수 | `spring.data.redis.url`에 매핑; 인증정보는 secret. URL과 host/password 이중 지정 금지 |
| `REDIS_CONNECT_TIMEOUT` | `200ms` | `spring.data.redis.connect-timeout` 후보 |
| `REDIS_COMMAND_TIMEOUT` | `100ms` | `spring.data.redis.timeout` 후보 |
| `REDIS_SSL_ENABLED` | 로컬 `false`, 실제 제공 endpoint에 맞게 명시 | `spring.data.redis.ssl.enabled`; TLS endpoint는 검증 활성화, 검증 우회 금지 |

위 값은 같은 사설망 테스트 시작점이며 API SLO/Render RTT/개인 서버 RAM 확인 후 승인한다. enabled=false일 때 URL 누락으로 기동 실패하면 안 된다. 구현 시 grouped ConfigurationProperties + application.yml 매핑, 루트 `.env`와 `.env.example`에 안전한 기본값/placeholder를 함께 추가하되 기존 DB/OAuth 값은 변경하지 않는다. 이번 문서 작성은 새 환경변수 적용 승인이 아니다.

### 4.2 호환성·Render → 개인 서버

- Boot 4.1.1 BOM은 Spring Data Redis 4.1.1을 관리한다. 버전을 수동 override하지 않고 starter의 Lettuce를 사용한다. explicit port로 버전 envelope와 fallback을 제어하며 `@Cacheable` 하나로 권한/스냅샷 규칙을 숨기지 않는다. 별도 reactive starter/pool2/session starter는 불필요하다. [Boot Redis 구성](https://docs.spring.io/spring-boot/reference/data/nosql.html), [관리 의존성](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- Render의 신규 Key Value는 **Valkey 8 기반 Redis 호환 서비스**다. 같은 리전 사설 endpoint를 사용하고 내부 인증을 활성화하는 구성을 제안한다. 외부 접속은 기본적으로 열지 않는다. 외부 endpoint는 TLS이며 내부 endpoint의 TLS 지원/URL scheme은 실제 서비스에서 확인한다. 무료 Key Value는 영속성이 없으므로 재시작은 전체 miss로 처리한다. `allkeys-lru`를 캐시 정책으로 선택한다. [Render Key Value](https://render.com/docs/key-value)
- 개인 서버에서는 동일 이미지를 사용하고 Compose의 별도 Redis/Valkey 서비스명으로 접속한다. 외부 6379 publish 금지, 전용 내부 네트워크/인증 secret 사용, 호스트 간 접속이면 TLS 지원과 인증서 검증 구성. 캐시 전용이라 AOF/RDB·볼륨 백업을 필수로 하지 않는다. PostgreSQL 영속 볼륨/백업은 별개다.
- Render와 같은 Valkey major를 개인 서버에 쓰는 것이 차이를 줄인다. 실제 Redis를 선택하면 사용할 GET/SET TTL/삭제 명령·Lettuce·직렬화 contract를 그 버전에서도 검증하고 이미지 tag/digest를 고정한다. 현재 컨테이너를 설치하거나 생성하지 않는다.
- 이전 시 PostgreSQL 쓰기 정지/최종 백업/복원 절차와 캐시를 분리한다. Redis 데이터는 이관하지 않고 새 namespace로 cold start한다. DB migration stage(`bridge`/`final`) 및 Flyway 복원 규칙은 [기존 rollout](../project-category-only-rollout.md)을 그대로 따른다.
- 세션은 이번 계획에서도 기존 메모리 방식이다. 여러 백엔드 캐시 테스트는 각 인스턴스에 유효한 독립 세션으로 수행한다. 공유 캐시가 세션 이동성이나 무중단 로그인까지 해결하지 않는다.

### 4.3 경로별 비용과 bypass

비용 기호: F=공통 Security filter/요청 검증, A=캐시 admission(권한·번호 조회), D=기존 RR reader 전체(그 안의 권한·번호 조회와 Category 목록/Project 집계 포함), G=Redis GET/검증, P=직렬화/SET, W=single-flight 대기. 인증 SQL을 F/A/D에 중복 집계하지 않고 실제 실행 위치별로 기록한다. 합은 비용 항목 구분이며 p95를 단순 덧셈하여 추정하지 않는다.

| 경로 | 단독 요청의 비용 구성 | 집계/주의점 |
| --- | --- | --- |
| 기존 baseline / off | F + D (+off 분기 비용은 별도 관측) | 추가 A/G/P 없음; reader 1회 |
| 사전 cooldown bypass | F + 상태 판단 + D | probe 아닌 요청은 A/G/P/W 없음; reader 1회 |
| 정상 hit | F + A + G | 업무 Category 목록/Project 집계 0회 |
| 정상 miss, 단독 계산 | F + A + G + D + P | 추가 admission과 GET/SET 비용; reader 1회 |
| Redis 장애를 처음 만난 요청 / 실패한 복구 probe | F + A + 실패한 G의 연결·대기 + D | reader 1회, P 없음. 아직 cooldown을 관측하지 못한 동시 요청도 이 비용을 낼 수 있음 |
| admission 뒤 cooldown 전환 관측 | F + A + D | 이미 지불한 A는 없어지지 않음; 이후 G/P 생략 |
| 정상 single-flight 합류 | waiter는 F + A + G + W; owner는 정상 miss 비용 | 성공·동일 R 결과 공유; 합류 집단에 D/P 각 1회. 개별 요청마다 F/A/G 비용은 남음 |
| single-flight 예외 fallback | F + A + G + W + 자기 D + 허용된 P | 대기 초과/계산 실패/R≠S에 추가 계산 가능; capacity 초과는 W=0. P는 요청의 Redis 허용 상태에 따름 |

각 경로에서 SQL 수(인증/admission/reader별), reader·집계 실행 횟수, 요청 총 지연, DB connection 점유 시간과 pool 대기를 독립 측정한다. Redis I/O와 W 동안 connection을 보유하지 않아도 추가 A/D나 중복 D가 pool 부하를 높일 수 있다. shared owner의 SET은 한 번 시도하며 정상 waiter는 별도 SET하지 않는다. GET 실패 요청은 single-flight 재합류/Redis 재시도 없이 D로 전환하고 SET을 기다리지 않는다.

### 4.4 single-flight 소유권과 예외

- JVM 내 **동일 전체 키의 진행 중 계산**만 공유하며 결과를 남기는 L1 cache가 아니다. 사전 bypass는 참여하지 않는다. 참여 요청은 각자 인증/admission을 통과한다. 다른 instance·다른 키의 계산은 합쳐지지 않는다.
- 초기 capacity 128 keys와 대기 100ms는 잠정값이다. 집계 시간 분포·API 전체 예산·DB pool 대기를 측정하여 결정한다. 집계가 한도보다 느리면 여러 waiter가 동시에 fallback하여 중복 집계가 발생할 수 있다. single-flight를 전역 또는 모든 상황의 1회 집계 보장으로 설명하지 않는다.
- 정상 합류(동일 R, owner 성공, waiter 대기 한도 내 완료, capacity 내) 집단은 D 1회를 공유한다. timeout·owner 실패·admission R과 결과 S 불일치·capacity 초과에서는 요청당 최대 한 번의 독립 D fallback을 허용하고 같은 요청이 재합류/계산 재시도를 반복하지 않는다. owner 자신은 S를 S 키/헤더로 사용할 수 있지만 R waiter는 재라벨하지 않고 자기 D로 전환한다. DB fallback 자체 실패는 기존 오류 계약으로 종료한다.
- 이 fallback 허용은 waiter의 공유 결과 이용 실패에 대한 정책이다. owner 자신의 DB 실패를 자동 재시도하거나 인증·소유권 실패/프로그래밍 오류를 성공으로 덮어쓰지 않는다. 독립 D에서도 각 요청의 기존 권한·오류 계약을 그대로 적용한다.
- 엔트리/공유 future의 소유자는 최초 계산 owner다. waiter 취소/timeout은 자기 구독·대기자 계수만 해제하고, 공유 future 취소나 엔트리 삭제를 하지 않는다. 다른 요청이 사용하는 계산을 끊지 않는다. owner HTTP 요청 취소도 공유 계산 취소로 직접 전파하지 않고, 시작된 계산은 설정된 DB 실행 deadline 내 성공/실패까지 마무리한다. 별도 무제한 background task/새 executor 인프라를 도입하지 않는다.
- owner는 성공·실패·실제 계산 종료 시 waiter를 깨우고 finally에서 **자신의 엔트리일 때만** 제거한다(compare-and-remove). 엔트리는 terminal 상태 이후 보관하지 않는다. owner가 아직 실행 중이면 timeout waiter가 엔트리를 지워 새 owner를 무제한 만드는 방식은 금지한다. hung 계산은 명시적 DB 실행 timeout/deadline으로 종료·정리하며, 단순 waiter timeout을 DB 계산 종료로 간주하지 않는다. 취소 처리와 connection 반환은 드라이버 동작까지 검증한다.
- 정상 합류의 D 1회와 예외의 추가 D 허용을 별도로 평가한다. 느린 동일 키 burst에서 owner 수·waiter 수·독립 fallback 수·합산 집계 횟수·pool 대기·API p50/p95를 기록한다. 허용 추가 비용/동시성 한도는 실측 후 정하며, budget 초과 시 확대를 보류한다. 분산 락·추가 캐시/circuit-breaker 라이브러리는 도입하지 않는다.

## 5. Swagger와 프론트 인계 계약

Redis hit/miss/장애는 외부 DTO·HTTP 상태·revision 규칙에 영향을 주지 않는다. 캐시 관리 endpoint나 클라이언트용 Redis hit 헤더는 추가하지 않는다. `/v3/api-docs`와 UI 확인은 기존 local/test 접근 정책 안에서 수행한다.

### 5.1 문서화할 API/예시

| API | 인계할 DTO/조건 |
| --- | --- |
| `/api/v2/projects/category-counts` | `ProjectCategoryCountsResponse(items, totals)`; 모든 Category 0건 포함/createdAt,id ASC, 미분류 마지막, active와 archived 분리. query 미허용 |
| `/api/v2/overview` | `OverviewResponse(category, projectId, projects, tasks, asOf)`; category/projectId만, project total=active만, Task=all projectStatus/live/non-search. 필터 외 파라미터 400 |
| `/api/v2/tasks/stats` | `TaskStatsResponse(counts,total,asOf)`; category/projectId/projectStatus/query. 상태 3개 항상 존재; 페이지/삭제/status 필터 미허용 |
| `/api/v1/project-categories`, `/{id}` | Category 목록/상세 DTO와 cap100·정렬·UUID identity; category name은 별도 표시 데이터 |
| `/api/v2/projects`, `/{id}` | ProjectListResponse/ProjectResponse; 기존 필터·default·limit·nextCursor 및 선택 UI에 전용 API 없음 명시 |
| `/api/v2/dashboards/home` | HomeDashboardResponse: schemaVersion=2, configuration only, default revision0, saved empty 유지, selectionState는 output only |

시범 정상 응답 예시(문서 예시, 실데이터 아님):

```http
HTTP/1.1 200 OK
Cache-Control: no-store
X-Workspace-Data-Revision: 42

{"items":[{"categoryId":"11111111-1111-4111-8111-111111111111","active":2,"archived":1},{"categoryId":null,"active":1,"archived":0}],"totals":{"active":3,"archived":1}}
```

변경 없는 DB에서 miss/hit는 같은 JSON과 같은 번호여야 한다. Category 0건/Project 0건/archived-only/삭제된 Category 선택/오류 예시도 추가한다. Overview/Task stats의 `asOf`는 현재 서버 관측 시각이며 commit watermark가 아니다. 두 API를 향후 캐시할 때 원래 snapshot 관측 시각 보존 또는 새 관측 시각 생성의 의미를 별도 결정하고 문서화해야 한다. 번호가 같다는 이유로 현재와 동일하지 않은 시간 필드까지 byte-identical하다고 주장하지 않는다. 시범 선택으로 이번에는 이 의미를 바꾸지 않는다.

### 5.2 프론트 규칙

- workspace별 최대 관측 revision을 decimal 문자열/BigInt로 비교한다. JavaScript Number 변환 금지. 로그인 사용자/작업실 전환 시 관측값과 캐시 경계를 분리한다. 큰 번호는 어느 API의 resource edit revision과도 비교하지 않는다.
- 정상 read의 revision보다 큰 변경 번호를 이미 봤다면 지연 read로 store를 덮어쓰지 않는다. 같은 번호라도 필터/페이지가 다른 응답은 같은 데이터가 아니다. 여러 요청의 번호가 다르면 UI 전체가 단일 snapshot이라고 주장하지 않는다.
- Project 이름/카테고리/상태 변경 시 Project 목록·상세·선택 결과와 관련 Task/Journal/Milestone/Link 표시·검색·집계, Overview, category counts를 영향에 따라 stale로 표시한다. Category rename은 이름 표시 맵, 삭제는 Category 목록/0 bucket/Dashboard missingCategory에 반영한다. Task mutation은 Task 목록/상세/stats/Overview에 반영한다.
- 전체 revision은 어떤 기능이 바뀌었는지 설명하지 않으므로 알려진 자기 mutation은 영향표를 사용하고 출처 불명의 더 큰 번호는 보수적으로 관련 workspace 캐시를 stale 처리한다. 기능별 counter가 있는 것처럼 가정하지 않는다.
- fresh POST/PATCH/Task 삭제·복구 등이 반환하는 **완전한 해당 resource DTO**와 정상 observation header가 있고, 클라이언트가 더 높은 번호를 이미 보지 않았으며 화면이 그 DTO로 충분하면 해당 상세 GET은 생략 가능하다. 이것이 다른 목록의 total/order/필터 membership/집계 최신성을 보장하지는 않는다.
- 생성 replay는 원래 201 snapshot이며 observation header가 없다. 이를 최신 조회 결과로 승격하거나 최신 상세를 덮어쓰지 않는다. 필요한 현재 상태는 GET. header 누락은 숫자 0으로 해석하지 않는다. Idempotency-Key 충돌/24h replay/v1 legacy retry 계약 유지.
- hard delete의 DeletedResource는 삭제 확인이지 최신 전체 목록이 아니다. resource revision/collectionRevision/작업실 dataRevision의 역할을 분리한다. `409 REVISION_CONFLICT`는 현재 상태 재조회 후 draft 조정, 자동 overwrite 금지. 기존 `IDEMPOTENCY_KEY_REUSED`, `CATEGORY_IN_USE`, `PROJECT_ARCHIVED`, `RESOURCE_DELETED` 등 코드/우선순위 유지.
- 커서는 workspace/필터/limit/정렬에 바인딩된 서명 v2. revision이나 만료시각은 현재 커서 payload에 없으며 Redis TTL과 무관하다. 변조/조건 변경/다른 workspace/구버전은 `400 INVALID_CURSOR`; 페이지 간 snapshot 보장은 없다. 관련 변경 뒤 첫 페이지부터 다시 읽는 정책을 문서화한다.
- Redis 장애만으로 401/403/409/500을 새로 반환하지 않는다. DB fallback이 성공하면 평소 200+동일 계약이다. DB도 실패한 경우 기존 DB 오류 응답 정책이며, 이전 캐시를 최신으로 위장하지 않는다.
- 브라우저 HTTP 캐시 정책은 계속 no-store. 프론트 Query/Store의 freshness/GC 시간은 네트워크 요청을 줄이는 제품 정책이고 Redis TTL은 서버 계산 재사용/메모리 정리 정책이다. 둘을 같은 시간으로 맞출 이유는 없다. ETag/새 집계 API는 측정 근거가 있을 때 후속 제안한다.

## 6. 예상 변경 파일·설정·의존성

아래는 승인 후 예상 경로이며 이번에 생성/수정한 애플리케이션 파일은 없다. 세부 클래스명은 구현 검토에서 최소화할 수 있다.

| 경로 | 예정 변경 |
| --- | --- |
| `build.gradle` | BOM 관리 Redis starter 1개; 별도 버전 override 금지. 기존 Testcontainers GenericContainer 재사용 가능 여부 확인 후 Redis용 추가 test library 없이 시작 |
| `overview/application/ProjectCategoryCountsQueryService.java` (신규 후보) | non-transactional 캐시 orchestration. 기존 controller는 이 application service로 위임만 변경 |
| `overview/application/ProjectCategoryCountsSnapshotReader.java` (신규 후보), 기존 `OverviewQueryService.java` | 시범 DB RR 경계 분리. Overview의 외부 RR/Task stats 결합은 유지하고 cache orchestration을 내부 호출하지 않음 |
| `overview/application/port/ProjectCategoryCountsCache.java` (신규 후보) | typed get/put capability; vendor exception 밖으로 노출하지 않음 |
| `overview/infrastructure/cache/*` (신규 후보) | Redis adapter, 명시적 envelope serializer/key/timeout/fallback 정책, 좁은 설정. 범용 캐시 프레임워크 생성 금지 |
| `workspace/application/*`, `workspace/infrastructure/*` (최소 신규 후보) | 인증된 user에 대한 fresh observation DTO/조회; entity 1차 캐시로부터 독립한 번호 읽기. auth filter 계약 그대로 |
| `src/main/resources/application.yml`, `.env`, `.env.example` | 승인된 설정 매핑·off 기본값·secret placeholder. cache off 기동과 선택적 health 확인 |
| `project/presentation/ProjectController.java`, 기존 Overview/Task/Workspace OpenAPI configuration | DTO/status 유지, 시범 service 위임과 관측/프론트 계약 설명·예시 보완 |
| `src/test/java/com/kopite/devspace/*Cache*Tests.java`, 기존 API/OpenAPI/concurrency tests | PostgreSQL+Redis 호환 컨테이너 격리 테스트, HTTP 경계 검증, 지연 제어, 손상/장애, 측정 fixture |
| `docs/query-cache-contract.md`, `docs/docker-deployment.md` | Swagger 연결 계약/환경변수/복구/이전 runbook. 실제 배포 실행 제외 |

**DB 마이그레이션은 기본적으로 필요 없다.** 기존 data_revision/ownership 인덱스를 사용한다. SQL 계획이 새로운 인덱스를 요구할 때만 근거·쓰기 비용과 함께 별도 범위를 승인받고 새로운 Flyway version으로 추가한다. 기존 migration, bridge/final 설정, replay 테이블은 수정하지 않는다. query 최적화도 실제 병목이 증명된 최소 변경만 별도 검토하며 빈 query 분기 등은 캐시 구현에 몰래 섞지 않는다.

## Execution Sessions

계획은 rejected이며 아래 세션은 실행하지 않는다. 롤백에 따라 실행 결과와 완료 체크를 제거했다. 향후 재추진은 별도 승인과 계획을 따른다.

### 단계별 진행 판단과 최소 비교 범위

Session 1 종료 시 자료·한계·추정 손익을 보고하고 **보류 / 최소 비교 구현** 중 선택을 기록한다. 작은 원 집계 비용, 낮은 revision당 재사용, 큰 Redis RTT 등으로 이득 가능성이 낮으면 Session 2를 시작하지 않는다. 판단 자료가 부족한데 더 조사할 가치가 있으면 필요한 자료만 수집한다. 불확실하다는 이유만으로 전체 구현을 진행하지 않는다.

실제 hit/miss 비교 없이는 판단하기 어렵고 사용자가 최소 시범 범위를 승인한 경우에만 Session 2로 간다. 최소 범위는 category-counts 하나의 typed key/envelope, fresh admission/RR reader, 선택적 Redis adapter, off·cooldown 직접 진입, timeout/queue 상한·GET 실패 후 SET 생략, schema/identity/크기 검증, 경로별 측정과 격리 테스트 연결이다. 정상 miss의 원자적 TTL 저장과 권한/snapshot/rollback 검증은 처음부터 필요하다. 이 안전장치를 생략한 빠른 시제품으로 성능을 비교하지 않는다.

최소 비교는 비운영 격리 환경·제한된 동시성에서 수행한다. single-flight 구현, 전체 Swagger 인계, 다중 instance·복원·장기 장애 검증·운영 runbook 완성은 Session 3~4로 미룰 수 있다. 이 경우 single-flight 미적용을 결과에 표시하고 낮은 부하 비교를 burst 안전성의 증거로 쓰지 않는다. **운영 활성화 전에는 전체 검증 행렬과 완료 조건이 필수**다.

Session 2 종료 시 baseline 대비 hit/miss/첫 장애/cooldown 경로의 비용을 보고하여 **후속 구현 / 보류 / 종료 제안**을 결정한다. 효과가 없거나 budget을 충족할 근거가 없으면 Session 3~4의 운영 기능으로 확대하지 않는다. 필요한 경우 single-flight만의 제한된 추가 실험 범위를 승인받아 비교할 수 있지만, 이 역시 전체 구현 승인을 대신하지 않는다. 다른 API로의 교체 또는 계획 종료는 근거를 붙여 제안하며 자동 실행하지 않는다. lifecycle 처리는 Completion 절을 따른다.

### Session 1: 승인·기준 측정·정합성 전제 고정

Objective: 승인된 시범 범위와 캐시 도입 유효성, 원본 observation의 안전성을 확인한다.

Implementation:

- [ ] 실행 범위와 상태 전환 확인.
- [ ] 배포 commit/DB stage, 실제 호출 분포·프로젝트/Task/Category 크기·쓰기 비율·Render 사설 RTT를 승인된 접근 범위에서 확인.
- [ ] 전체 업무 writer의 revision 증가/rollback/replay 예외 inventory와 실제 HTTP의 OSIV·transaction 경계 확인.
- [ ] 격리 환경에서 기존 DB 경로의 SQL 수·집계 횟수·HTTP p50/p95·connection 점유 측정. 측정 조건·대표성·한계를 기록.
- [ ] Redis보다 쿼리 최적화가 우선인지 비교하고 후보 제안.
- [ ] 관측 근거에 따라 latency/DB 비용/장애 budget 합의. 미확인 수치는 유보.

Validation:

- [ ] 실제 HTTP의 동시 조회·수정에서 본문/번호 snapshot pairing과 상위 transaction 참여 검증.
- [ ] 격리 DB 연결 및 fixture의 행 변경·counter 원자성 검증.
- [ ] 원 집계 비용과 admission/Redis 추가 비용을 구분해 Session 2 보류 또는 최소 비교 구현 판단.
- [ ] 미확인 항목·판단 근거·재개 조건 기록. 추정으로 검증 완료 처리하지 않음.

### Session 2: 승인된 최소 비교 구현과 진행 판단

Objective: Session 1 진입 판단이 허용한 최소 범위에서 Redis 손익을 실측한다. 전체 운영 구현은 아직 요구하지 않는다.

Implementation:

- [ ] 승인된 최소 starter/설정/typed DTO·key/envelope·Redis adapter 및 off/cooldown 사전 bypass 구현.
- [ ] fresh DB observation과 RR snapshot reader 분리, Redis I/O는 transaction 밖으로 제한.
- [ ] bounded timeout/queue/cooldown, 원자적 TTL, payload 크기/구조 검증, GET 실패 요청의 SET 생략, 경로별 SQL·집계·지연·connection 계측 구현. 제한된 격리 비교를 위한 category-counts 연결만 추가.

Validation:

- [ ] secret 없음/typed JSON roundtrip/identity mismatch·손상·size 초과 테스트.
- [ ] off + Redis 미설치, on + Redis unreachable 기동/조회, Redis optional health 검증.
- [ ] connect/GET/SET 실패 budget 및 disconnected queue 상한 검증. DB/auth 오류가 삼켜지지 않음 확인.
- [ ] 권한 격리·본문/번호 pairing·지연 fill·rollback을 검증한 뒤 baseline/off/hit/miss/첫 장애/cooldown을 비교. off/cooldown에는 추가 admission/Redis 호출이 없음 확인.
- [ ] 결과·제약과 Session 3 진행/보류/종료 제안을 기록. single-flight를 아직 구현하지 않았다면 단독/제한 동시성 결과만 주장하고 운영 burst 검증은 미완료로 남김.

### Session 3: category-counts 통합·정합성 회귀

Objective: 하나의 API에서 기존 응답 계약과 경쟁 조건을 증명한다.

Implementation:

- [ ] 최소 비교 연결을 운영 후보로 정리하고 기존 response snapshot/200/no-store 유지. Session 2 진행 판단을 통과한 경우에만 수행.
- [ ] TTL jitter와 4.4절 bounded single-flight 소유권·취소·deadline·정리 정책 및 지표 구현. 대기값은 실측 분포/지연 budget으로 결정.
- [ ] 다른 집계/목록/명령/replay 경로는 기존 DB 사용 유지.
- [ ] 아래 검증 행렬의 정합성·장애·격리 테스트 구현 및 증거 기록.

Validation:

- [ ] cache GET/SET 동안 활성 DB transaction/connection 유지가 없고 miss publication은 성공적인 read transaction 종료 후임을 확인.
- [ ] hit 업무 집계 0회, 단독 miss의 RR 계산 1회, 정상 single-flight 합류 집단의 계산 1회를 각각 검증. timeout/실패/R≠S/capacity fallback의 추가 계산은 별도 집계하여 요청당 독립 fallback 최대 1회 및 합의 budget 확인.
- [ ] 집계 시간이 대기 한도를 넘는 동일 키 burst와 waiter 취소/timeout/owner 실패·deadline 종료를 재현. 다른 waiter의 공유 계산 보존, 엔트리/계수/connection 누수 없음 검증.
- [ ] off/cooldown 사전 bypass와 첫 Redis 실패/복구 probe를 구분해 SQL 수·집계 횟수·총 지연·connection 점유/pool 대기 측정.
- [ ] 기존 인증·소유권·Category/Project/Task·replay·409·cursor 회귀 통과.

### Session 4: Swagger·성능 비교·운영 인계

Objective: 프론트가 계약만으로 후속 캐시 작업을 시작할 수 있고 활성화 여부를 판단할 수 있게 한다.

Implementation:

- [ ] 5절 계약·DTO·예시·오류·헤더를 Swagger 및 연결 문서로 정리.
- [ ] 아래 성능 행렬의 baseline/off/cooldown/첫 실패/probe/miss/hit/write-heavy/느린 동일 키 burst 비교 실행. 정상 합류와 예외 fallback을 분리. Render 검증은 승인된 비운영 서비스에서만, 개인 서버 비교도 별도 승인된 환경에서만 수행.
- [ ] migration-free rollback, 새 namespace 복원, Render→개인 서버 cold-start runbook 작성.
- [ ] 유의미한 DB 비용 감소와 허용 가능한 latency/실패 비용이 확인될 때만 별도 활성화 제안. 데이터가 부족하면 미완료 검증을 숨기지 않음.

Validation:

- [ ] OpenAPI 생성물·Swagger UI·예시와 실제 HTTP parity 확인. 프론트 인계 체크리스트 완료.
- [ ] 신규 Redis 장애로 API가 unavailable해지지 않고 off 경로로 복귀 가능함을 확인.
- [ ] 합의한 성능 gate 결과와 미확인 환경 항목을 보고. 운영 배포는 이 session에 포함되지 않음.

## 7. 검증 행렬 및 성능 완료 기준

| 시나리오 | 검증 방법/기대 결과 |
| --- | --- |
| miss → hit | 동일 변경 번호에서 JSON bucket 순서·0건·미분류·totals·헤더·no-store 일치; hit aggregate 0회 |
| 사용자/작업실 격리 | 같은 조건의 서로 다른 user/workspace, 위조 envelope/key, 세션 만료/삭제/disabled에서 타 데이터 노출 없음; 기존 401/403 유지 |
| 생성/수정/삭제 | Project 생성·상태/카테고리 변경, Category 생성/rename/사용 불가 삭제409/허용 삭제, Task·Journal·Link·Dashboard mutation 후 번호 증가 및 새 키 관측. Project 삭제 API를 가정하지 않음 |
| 파생 데이터 | Project 이름 변경 후 Task/Link 표시와 query 검색, Category 변경 후 counts/Overview/children categoryId 갱신, Category rename 표시 맵·삭제 missingCategory. 시범 외 DB endpoint와 교차 검증 |
| 지연 read/cache fill | latch로 R read, R+1 writer commit, R put 순서 재현. R+1 request는 R hit 불가. 늦은 응답도 header R, 오래된 본문에 R+1 부착 금지 |
| admission/fill 사이 commit | initial R miss 후 S reader는 S에만 publish/응답. transaction 분리와 1차 캐시/외부 tx 참여 검증 |
| rollback | command rollback은 번호·업무 행 유지. reader 실패/rollback은 캐시 publish 없음. Redis 실패가 writer의 DB commit을 막지 않음 |
| TTL/eviction/restart | 만료/jitter/메모리 eviction/재시작 후 miss fallback. no-expiry key가 생성되지 않음. 이전 revision 데이터 사용 없음 |
| Redis 장애/손상 | timeout·연결 거절·DNS·인증/TLS 실패·SET 실패·잘못된 JSON/schema/revision/workspace/descriptor·초과 payload. DB 가능하면 정상 조회, bounded 지연, cache 성공으로 오집계 금지 |
| 사전 bypass / 첫 실패 | off·cooldown 비probe 요청은 추가 admission/Redis 없이 권한 확인을 포함한 D로 직접 진입. 첫 실패/probe는 A+실패 대기+D, GET 실패 뒤 SET 없음. SQL/집계/총 지연/connection 점유/pool 대기 별도 비교 |
| 정상 single-flight 합류 | 동일 키·동일 R·성공·대기 한도 내 완료 집단은 계산 1회. 요청별 인증/admission 유지, waiter별 SET 없음 |
| 느린 동일 키 burst / 예외 fallback | 집계 시간이 대기 한도를 넘도록 제어. timeout·owner 실패·R≠S·capacity 초과를 구분하고 owner/waiter/fallback/합산 집계 수·pool 대기·API 지연 측정. 추가 계산 허용하되 요청당 독립 fallback 최대 1회, 무한 재합류 없음. budget 초과 시 확대 보류 |
| single-flight 취소·정리 | waiter 하나의 취소/timeout이 owner future/다른 waiter를 취소하지 않음. owner HTTP 취소/계산 실패/deadline 종료 후 엔트리·대기 계수·connection 정리. timeout waiter가 실행 중 owner 엔트리를 지우지 않고 terminal owner의 조건부 삭제만 수행 |
| 다중 backend | 동일 DB/namespace의 두 instance 독립 세션. A mutation 후 B read는 새 번호; A의 늦은 put으로 B 오염 불가. rolling schema 버전 key 격리 |
| restore/세대 | 동일 UUID/revision이나 다른 데이터를 복원한 fixture에서 namespace 변경 시 과거 캐시 hit 불가 |
| 계약 회귀 | 401/403/CSRF/Origin/404, optimistic 409 우선순위, fresh201 vs historical201/header 부재, cursor 서명·필터·limit·cross-workspace400, 페이지 간 snapshot 비보장 유지 |

성능은 같은 commit/fixture/JVM·DB warmup/동시성으로 비교하고 순서 편향을 줄인다. 작은 개인 workspace, 예상 증가 규모, 조회 위주, 무관한 mutation 다수, 같은 키 burst, 검색/필터 다양성(후속 후보)을 구분한다. 운영 부하 테스트는 하지 않는다.

| 비교 | 필수 기록 |
| --- | --- |
| cache off baseline vs warm hit | API p50/p95, 인증·observation 포함 DB SQL 수, aggregate 횟수/실행시간, Redis RTT, payload/메모리 |
| cold miss/TTL miss | 추가 admission roundtrip + Redis GET + DB reader + SET 비용, 지연 분포/connection 점유 |
| write-heavy | revision당 재사용 횟수, 무관한 mutation에 따른 miss, old keys/eviction/메모리 증가 |
| Redis 첫 실패/복구 probe | A SQL + 실패 대기 + D SQL/집계 비용, 총 p50/p95·connection 점유/pool pending, SET 생략, 동시 초기 실패 수 |
| off/cooldown 사전 bypass | baseline 대비 A/G/P 없음, 실제 F+D SQL/집계/총 지연·connection 점유/pool pending. admission 후 cooldown 전환은 별도 분류 |
| single-flight 정상/예외 burst | 정상 합류 1회와 timeout/실패/R≠S의 추가 계산 분리; owner/waiter/fallback/집계 실행 수, wait 시간·DB pool 대기·API p50/p95·엔트리 정리 |
| 두 인스턴스 | 합산 DB aggregate 수, 중복 fill, 공유 hit율, 권한/응답 pairing |

성능 평가는 4.3절 경로별 실측 비용에 실제 경로 비중을 적용한다. cache 시도 요청에는 A/G가 있지만 off/cooldown 사전 bypass에는 없다. miss에는 D/P, 첫 GET 실패에는 실패 대기+D(SET 없음), 정상 합류에는 W와 집단당 D, 예외 fallback에는 W 뒤 추가 D가 포함된다. 이 비용과 baseline F+D를 비교하며, DB 절감은 hit/정상 합류로 피한 업무 조회에서 추가 admission·중복 fallback SQL을 뺀 값으로 평가한다. 총 지연 p50/p95는 실제 요청 분포에서 측정하고 경로 p95의 가중합으로 계산하지 않는다. hit율만 높아도 원 쿼리가 매우 저렴하면 실패다. 프론트 최적화 후 요청 수가 감소하면 손익도 다시 측정한다.

**수치 목표는 baseline 후 결정**한다. correctness는 0건 위반, 선택적 장애 시 외부 계약 보존, hit에서 업무 집계 제거는 필수 조건이다. latency·DB CPU·memory·fault budget의 허용치/표본 수/분포는 Session 1에서 관측 가능한 근거로 합의하고 미확인 항목은 유보한다. Session 2 최소 비교와 Session 3 single-flight 실측으로 남은 기준을 확정한다. 개선 증거가 없으면 후속 구현/운영 확대를 보류하고 근거를 보고한다. 이미 만든 최소 시범은 비활성으로 두되 이것만으로 계획 완료를 선언하지 않는다.

## 8. 롤백과 미확인 결정

롤백은 `QUERY_CACHE_ENABLED=false`로 전 인스턴스를 기존 DB 경로로 재시작/재배포하는 방법을 기본으로 한다. 즉시 원격 feature flag 인프라는 추가하지 않는다. 진행 중 read는 자기 번호로 완료하며 남은 cache는 TTL로 소멸한다. DB schema/원본/replay/프론트 API 변경이 없어 DB rollback이 필요 없다. 이전 코드 배포도 가능하다. emergency cleanup은 전용 namespace 범위에 한정하고 공유 저장소 `FLUSHALL`을 요구하지 않는다.

schema/의미가 달라지는 앱 rollback·rolling deploy에는 schemaVersion 변경 또는 별도 namespace를 사용한다. off 상태에서 Redis를 제거해도 DB 조회가 가능한 것을 사전 검증한다. Redis가 없어진다는 이유로 session을 삭제하거나 인증을 완화하지 않는다.

결정/확인 대기:

1. B와 category-counts 시범, 최소 starter/저장소 도입의 명시적 승인. 향후 다른 endpoint 확대는 별도 판단.
2. 실제 Render 배포 커밋·DB stage·데이터 크기·읽기/쓰기 비율·사설 RTT·요금/메모리/연결 상한. 실제 프론트 선택 UI/API 호출 방식과 revision 처리도 인계 시 확인.
3. HTTP 경계의 persistence-context/transaction 동작과 모든 운영 writer의 revision invariant. 실패 시 캐시 적용 blocker.
4. 임시 TTL/timeout/memory/cooldown/single-flight 값을 baseline으로 검증하고 운영값 결정. 가용하지 않은 Render/개인 서버 테스트는 대체 검증과 한계를 명시.
5. 향후 Overview/Task stats 확대 시 asOf 의미, query 정규화, 권한 lookup 비용, cache cardinality를 별도 확정.
6. 기능별 revision/추가 인덱스/전용 선택 API/no-store 또는 ETag 변경은 근거가 생길 때만 별도 제안. 현재 승인 요청에 포함하지 않는다.
7. Session 1/2 진행 판단에 필요한 실측 손익, first-failure와 cooldown 비중, 느린 집계의 분포·single-flight wait/DB deadline·fallback 비용 예산은 미확정이다. 보류 시 재개에 필요한 자료/승인과 미실행 단계, 종료 시 사유를 기록한다.

## Final Validation

- [ ] 신규 PostgreSQL/Redis 격리 테스트와 기존 관련 API/OpenAPI/concurrency 테스트 통과.
- [ ] `./gradlew.bat test`, `./gradlew.bat check`, `./gradlew.bat bootJar` 중 repository 적용 범위에 맞는 검증 완료. 기존 migration 테스트는 운영 DB와 완전 분리된 컨테이너에서만 수행.
- [ ] Session 1→2 및 최소 비교→후속 구현 진행 근거가 기록되고, 실측에 근거한 합격 기준으로 전체 검증 완료. 보류·종료를 completed로 대체하지 않음.
- [ ] baseline/off/cooldown/hit/miss/첫 실패/probe/write-heavy 경로별 SQL·집계·총 지연·connection 점유/pool 대기 검증 완료. 사전 bypass admission 생략 및 GET 실패 후 SET 생략 확인.
- [ ] 정상 single-flight 합류의 계산 1회와 예외 fallback의 추가 계산 기준을 분리 검증. 느린 동일 키 burst·취소·timeout·실패·deadline에서 누수/공유 계산 전파 취소 없이 합의 budget 충족.
- [ ] 환경변수 예시·off 기동·새 namespace 복구·이전·rollback 검증 완료.
- [ ] No failures caused by this Plan remain.
- [ ] All Execution Sessions are complete.

### API Validation / 프론트 인계 체크리스트

새 endpoint는 추가하지 않지만 설명/계약을 보완하므로 다음 항목은 적용한다.

- [ ] 기존 endpoint·파라미터·DTO·Bean Validation·상태 코드가 `/v3/api-docs`와 실제 응답에서 일치.
- [ ] Swagger UI에서 정상/빈 결과/오류/생성 replay 예시 확인.
- [ ] X-Workspace-Data-Revision 적용 범위·문자열·관측점·지연 응답·멱등 replay의 헤더 부재 명시.
- [ ] 목록/상세/집계 영향표, 저장 후 상세 GET 생략 조건과 제한, cursor/409 처리 인계.
- [ ] no-store 유지·프론트 메모리 freshness와 Redis TTL 분리·Redis 장애의 DB fallback 명시.
- [ ] 프론트가 Redis 관리 권한/endpoint나 비밀정보 없이 구현할 수 있는 연결 문서 제공.

## Completion

운영 후보까지 진행하기로 결정한 경우 필수 session/전체 validation을 실제 완료하고 Goal과 성능 gate를 충족하며 blocker가 없을 때만 `completed`로 전환하여 plans/README에 등록한다. 환경상 검증 불가 시 사유와 가장 강한 대체 검증을 기록하고 미완료 항목을 숨기지 않는다. 최소 비교의 안전장치 검증만으로 운영 활성화나 완료를 선언하지 않는다.

성능상 보류는 별도 lifecycle 상태를 만들지 않는다. 승인 전이면 `proposed`를 유지하고, 승인 후 실행 중이면 `active` 상태에 보류 사유·측정 근거·미실행 항목·재개 조건을 기록한 뒤 작업을 멈춘다. 보류를 completed로 표시하거나 모든 session을 계속 구현할 의무는 없다. 취소/대체/의도적 종료가 결정되면 계획 규칙에 따라 `rejected`로 전환하고 근거 및 대체 계획 링크(있는 경우)를 남긴다. 다른 API로의 전환은 별도 승인된 범위/계획으로 처리한다. DECISION의 채택 여부는 PLAN의 성능상 중단과 별도 판단하며 자동 상태 변경하지 않는다. 완료 README에는 보류·rejected 계획을 등록하지 않는다.

현재 PLAN은 사용자 요청으로 `rejected`다. Session 1 추가 사항은 롤백했으며 후속 구현은 진행하지 않는다. 완료 계획 README에는 등록하지 않는다. DECISION-0003의 설계 승인은 별도로 유지한다.
