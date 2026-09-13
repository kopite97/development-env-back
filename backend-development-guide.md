# 백엔드 개발 및 프론트 연동 지침

이 문서는 [구현 명세](backend-implementation-spec.md)를 실제 서버와 프론트에 적용하는 지침이다. 현재 서버 코드·로그인·DB·배포는 구현되지 않았다. 특정 프레임워크를 강제하지 않는다. 인증 제공자/스택 답변이 없는 상태의 기본 제안은 [문서 안내](README.md)에 명시했다.

## 1. 구현 경계와 순서

1. 인증/사용자/개인 작업실과 요청 컨텍스트를 먼저 만든다. 인증된 userId→workspaceId를 한 경로에서 결정한다. 최소 두 사용자로 권한 테스트를 작성한다.
2. DB 마이그레이션, 공통 DTO/오류/검증, revision, 멱등성 저장, 트랜잭션 규칙을 구현한다. 새 명세를 OpenAPI 파일로 옮겨 요청/응답 예제를 검증한다.
3. 프로젝트 생성/편집/보관과 선택 목록을 구현한다. 이후 태스크/일지/마일스톤 관계를 추가한다.
4. 링크 CRUD·원자 재정렬, 홈 배치 저장, 전체 통계를 연결한다. 모든 목록에 동일 소유권 필터를 적용한다.
5. 프론트의 인증 경계와 비동기 데이터 계층을 연결하고 아래 사용자 전환·저장 실패 시나리오를 검증한다.
6. 기존 로컬 사용자를 전환하기 전 명시적 가져오기와 백업/복구를 검증한다. 서버 운영 준비 완료 후 서비스 모드를 기본으로 전환한다.

화면 컴포넌트가 DB/인증을 대신하지 않게 한다. 서버는 HTTP 계층(요청 검증/인증) → 도메인 서비스(소유권/상태 전이/트랜잭션) → 저장소(항상 작업실 조건)의 책임을 구분한다. 아직 없는 팀/배포 실행/원격 개발 환경을 초기 엔드포인트에 섞지 않는다.

## 2. 인증과 개인 데이터 보호

### 외부 로그인 + 서버 세션 기본안

OIDC Authorization Code + PKCE를 사용하고 공급자의 검증된 라이브러리로 issuer, audience, 서명, 만료, nonce를 검증한다. 로그인 거래의 state/nonce/code verifier는 요청 브라우저에 묶고 일회성으로 소비한다. returnTo는 상대 SPA 경로만 허용하고 `//host`, 다른 origin, API/auth 경로를 차단한다. callback의 비밀값을 로그에서 마스킹한다. 이 선택은 외부 로그인 토큰을 프론트 저장소에 배포하지 않는 서버 세션 구조다. [OWASP OAuth2 지침](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)

앱 로그인에 저장소 쓰기/배포 권한을 요청하지 않는다. 사용자 키는 검증된 `(issuer, subject)`다. 외부 계정의 이메일 변경으로 소유자가 바뀌지 않아야 한다. 자체 비밀번호 방식을 선택하면 별도 가입, 이메일 검증, 로그인 실패 제한, 재설정 및 자격 증명 저장 계약을 추가하고 이 OIDC 계약을 개정한다.

세션 쿠키는 운영 HTTPS에서 `__Host-devspace-session; Secure; HttpOnly; SameSite=Lax; Path=/`, Domain 없음을 기본으로 한다. 로그인 성공/권한 변화 때 세션을 교체하고 로그아웃/계정 비활성화 시 서버에서 폐기한다. 유휴·절대 만료를 서버에서 검사한다. 기본 제안은 유휴 12시간·절대 7일이며 제품 정책으로 확정한다. 브라우저 탭을 닫는 것과 로그아웃은 다르다. [OWASP 세션 관리](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)

쿠키 인증 mutation에는 서버 세션에 묶인 `X-CSRF-Token`과 허용 Origin 검증을 적용한다. SameSite만을 유일한 방어로 쓰지 않는다. GET은 업무 변경을 하지 않으며 로그인 callback은 별도 state 검증 경로다. cross-origin이 꼭 필요하면 credentials 허용 origin을 정확히 나열하고 wildcard와 조합하지 않는다. [OWASP CSRF 방어](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

### 소유권·캐시·로그

요청마다 권한을 확인하고 기본 거절을 적용한다. 예를 들어 `UPDATE tasks ... WHERE workspace_id=:sessionWorkspace AND id=:id AND revision=:expected`처럼 소유권과 revision을 함께 검사한다. 실패 원인을 구분할 추가 조회도 같은 작업실에 한정한다. 사용자 A의 projectId를 B의 POST/PATCH/배치/가져오기에 끼워 넣을 수 없어야 한다. [OWASP 권한 검사](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)

개인 JSON 응답은 초기 운영에서 `Cache-Control: no-store`로 제공한다. CDN/shared cache를 거치며 섞이지 않게 한다. 앱 내부 캐시와 서버 캐시는 userId/workspaceId를 키에 포함한다. 로그에는 requestId·내부 사용자/작업실 id·작업 종류·리소스 id·결과·지연을 남기되 본문/일지/URL 자격 증명/세션 토큰/CSRF 토큰은 남기지 않는다. 오류 응답에 SQL·stack trace를 노출하지 않는다.

위 인용은 보안 구현의 외부 근거이고, 개인 작업실 수·세션 만료 기본안·DB 모델은 이 프로젝트의 설계 제안이다.

## 3. 데이터베이스와 동시성

| 대상          | 필요한 제약/인덱스                                                                                                                |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| 사용자/인증   | auth_identities(issuer, subject) UNIQUE; workspaces(owner_user_id) UNIQUE                                                         |
| 프로젝트/자식 | projects(workspace_id,id) UNIQUE; 자식의 동일 두 필드 FK. 프로젝트 물리 삭제는 RESTRICT                                           |
| 기본 목록     | (workspace_id, created_at, id); 작업은 deleted_at/status/project_id 조건에 맞춘 인덱스                                            |
| 일지          | (workspace_id, entry_date, created_at, id), 필요 시 project_id 포함                                                               |
| 마일스톤      | (workspace_id, project_id, completed, due_date, id); null 정렬을 DB별로 명시                                                      |
| 홈 배치       | (workspace_id, dashboard_key='home') UNIQUE; widgets JSON은 전체 검증                                                             |
| 링크          | link_collections(workspace_id) UNIQUE, links(workspace_id,position). 재정렬 중 위치 유일성 충돌은 임시 위치/지연 제약 등으로 처리 |
| 멱등성        | (workspace_id,method,path,key) UNIQUE, expires_at 인덱스; 성공 기록과 업무 변경의 원자성                                          |
| 가져오기      | import_jobs(workspace_id,id), (workspace_id,source_digest) 성공 중복 제어                                                         |

제목 부분 검색을 B-tree 인덱스 하나로 해결했다고 가정하지 않는다. 초기 데이터 규모에서 측정하고 필요하면 DB의 부분 문자열 검색 인덱스를 선택하되 검색 의미는 유지한다. 모든 길이·enum·날짜를 서비스에서 검증하고 FK/NOT NULL/CHECK/UNIQUE로 가능한 제약을 DB에도 둔다.

DB의 날짜 타입과 UTC timestamp 타입을 구분한다. 일지 entryDate와 마일스톤 dueDate는 날짜다. 실제 생성 시각을 사용자가 바꾸게 하지 않는다. 프로젝트 progress와 태스크 완료율은 독립된 값이다.

업무 변경은 workspace 행 → 관련 프로젝트/컬렉션 → 개별 리소스 순서로 잠금을 취하는 기본안을 사용한다. 각 업무 변경은 내부 workspace.dataRevision도 증가시킨다. 이 값은 가져오기 미리보기 후 다른 변경이 발생했는지 판단하는 용도이며 개별 리소스 revision을 대체하지 않는다. 첫 배치/링크 컬렉션 생성도 유일 제약과 같은 잠금 순서를 따른다. 더 높은 처리량이 필요하면 잠금 전략을 변경하고 경쟁 테스트를 다시 통과시킨다.

원자 저장의 기준은 “DB를 쓴 후 HTTP 성공”이다. revision 비교, 변경, 감사 메타데이터와 멱등 결과 저장을 분리해 중복 생성/부분 성공을 만들지 않는다. 쿼터 검사도 같은 트랜잭션에서 수행한다. DELETE의 중복 요청은 허가된 404 확인 또는 멱등 결과로 처리하며 다른 사용자 존재 여부를 확인하는 데 사용하지 않는다.

마일스톤 영구 삭제는 workspace → Project → Milestone 잠금 순서에서 소유권과 현재 revision을 검증하고 행 삭제와 dataRevision 증가를 원자 처리한다. 소프트 삭제/복구/tombstone이나 Project·다른 마일스톤의 연쇄 변경은 없다. 생성 멱등 기록은 삭제와 함께 제거하지 않는다. 삭제 후 동일 생성 키/본문은 소유권 확인 후 원래 201 응답만 재전송하고 리소스와 카운터를 변경하지 않는다. PostgreSQL 테스트에서 삭제 rollback과 동시 수정/삭제·중복 삭제를 별도 트랜잭션으로 검증한다.

## 4. 프론트 연동 작업

현재 Provider의 `boolean` 저장을 `Promise` 기반 mutation으로 바꾸는 작업이 필요하다. 서버 문서 작성만으로 이 변경이 적용된 것은 아니다.

### 초기 진입과 사용자 전환

1. 앱 진입에서 GET /me로 세션을 확인한다. 확인 중에는 이전 사용자의 업무 화면을 렌더링하지 않는다.
2. 로그인 성공 후 userId/workspaceId로 업무 Provider를 새로 생성하고 데이터/배치/CSRF를 읽는다. 오류를 fixture나 기존 localStorage로 대체하지 않는다.
3. 조회 키는 `(userId,workspaceId,resource,filters,sort,cursor)`로 구성한다. 같은 데이터 위젯은 캐시를 공유하되 편집 초안은 분리한다.
4. 로그아웃 요청 전에 미저장 변경을 확인한다. 로그아웃 완료/401 때 요청 취소, 폴링 중지, 캐시·편집 상태 폐기/격리를 수행한다. 로컬 업무 원본의 자동 삭제와는 구분한다.
5. 이전 사용자 요청이 늦게 응답해도 현재 세션 generation과 다르면 반영하지 않는다. abort만으로 서버 mutation을 되돌렸다고 가정하지 않는다.
6. 세션 만료 시 A의 초안은 A에게만 복귀 가능한 일시 메모리로 격리하고 B에게 렌더링/저장하지 않는다. 같은 userId로 재인증한 뒤 revision을 다시 확인한다. 새로고침 이후 자동 초안 복구는 현재 기능에 없으므로 보장하지 않는다.
7. 여러 탭에서 로그아웃/계정 변경을 전파하고 포커스 복귀 시 /me를 재확인한다. BroadcastChannel 메시지에는 업무 내용/토큰을 싣지 않는다.

서버 세션이 실제 폐기되지 않은 네트워크 오류 상황에서 로그아웃 성공으로 표시하지 않는다. 대신 현재 화면은 잠그고 로그아웃 재시도를 안내한다. 인증 redirect 전에 사용자가 편집 내용을 처리할 수 있게 하며 허용된 SPA 경로만 복귀한다.

### 조회/편집/실패

- loading, 성공 빈 값, error, 401을 구분한다. 미연결 운영 위젯은 기능 플래그/미연결 안내로 처리하고 가짜 정상 응답을 만들지 않는다.
- 저장 중 같은 리소스의 중복 제출을 막는다. 새 리소스의 재시도 동안 동일 Idempotency-Key를 유지하고 성공/명시적 새 작성 시 새 키를 만든다.
- 성공 응답의 id/revision/정규화된 필드를 캐시에 적용한 뒤 폼을 닫는다. 실패/409는 초안을 유지한다. 최신 revision을 받아 몰래 재전송하지 않는다.
- 수정 성공 시 목록·상세·홈·통계를 무효화/갱신한다. 프로젝트 변경은 연결 태스크·휴지통·일지·마일스톤·선택 목록을 갱신한다. 마일스톤 자체 완료나 영구 삭제는 프로젝트 수동 progress·목표 메모를 바꾸지 않는다.
- 마일스톤 삭제는 DELETE `/api/v1/milestones/{id}?revision=...`로 요청하며 세션·CSRF·Origin 보호를 유지한다. 성공은 200 `{deletedId}`, 오래된 revision은 409, 없거나 접근 불가/반복 삭제는 404다. 성공 응답 후 관련 목록·상세·홈 캐시를 갱신하고 휴지통/복구를 표시하지 않는다. 응답 유실 시 소유 GET으로 삭제 여부를 확인하며 최신 revision으로 몰래 재시도하지 않는다.
- 링크 편집/삭제/이동 응답의 collectionRevision을 함께 반영한다. 필터링된 목록으로 전체 ids 순열을 구성하지 않는다.
- 위젯 설정/드래그는 배치 초안이다. PUT 성공 전 저장 완료로 표시하지 않는다. 배치 취소가 이미 성공한 태스크/일지/마일스톤 변경을 되돌리지 않는다.
- 특정 프로젝트 위젯의 보관 프로젝트는 상세 조회로 보충한다. 선택 목록 첫 페이지에 없다는 이유로 “없는 프로젝트”라고 판단하지 않는다.
- 일지 DTO에 entryDate를 추가하고 표시/입력/기간 조회/정렬을 변경한다. createdAt을 지역 정오로 덮어쓰는 현재 동작을 서버에 보내지 않는다.
- 페이징은 화면별 더 보기와 total을 제공한다. 통계를 현재 페이지의 배열 길이로 계산하지 않는다. 위젯 전체 카드 제한과 보드 열별 페이징을 구분한다.
- 기존 메뉴 초점·모달 이름·Tab/Escape·미저장 이탈 보호를 유지한다. API 오류 메시지는 role=alert 등으로 전달하고 세션 이동 후 적절한 화면으로 초점을 복원한다.

## 5. 기존 로컬 데이터 가져오기

필수 원칙은 자동 업로드/자동 삭제 금지다. 로컬 키는 계정 소유가 기록되지 않은 브라우저 데이터이므로 서버가 어느 계정의 것인지 추측할 수 없다. 로그인한 사용자에게 대상 작업실과 가져올 자료를 명시해 확인받는다. 처음부터 빈 작업실을 사용할 수도 있다. 이 절의 API는 로컬 사용자 이전을 제공하는 릴리스에서 구현한다.

### 입력 패키지

```json
{
  "formatVersion": 1,
  "timeZone": "Asia/Seoul",
  "data": {
    "projects": [],
    "tasks": [],
    "journals": [],
    "links": [],
    "milestones": [],
    "layout": []
  },
  "options": { "includeLayout": false, "deriveLegacyMilestones": false }
}
```

data는 여섯 원본 localStorage 키의 값을 사용한다. 키 부재는 해당 속성 생략, 저장된 빈 배열은 `[]`로 구분한다. 손상 JSON을 fixture로 대체해 보내지 않는다. 사용자가 실제 선택한 자료만 패키지에 포함한다. 처음에는 **기존 서버 데이터에 추가하는 방식**만 제공하고 이름으로 자동 병합/덮어쓰기하지 않는다. layout 교체는 includeLayout=true일 때만 별도 확인한다. 기존 서버 데이터 일괄 삭제 모드는 없다.

### API와 처리 순서

| API                         | 계약                                                                                                                                               |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| POST `/imports/preview`     | 입력 패키지 검증. 201 `{id,sourceDigest,baseDataRevision,expiresAt,counts,warnings,errors,projectMappings,canCommit}`. 업무 데이터는 생성하지 않음 |
| GET `/imports/{id}`         | 소유자만 `{id,status,sourceDigest,counts,errors,expiresAt,result}` 조회. status=preview/committed/expired, result는 성공 시 id 매핑과 생성 건수    |
| POST `/imports/{id}/commit` | `{sourceDigest,baseDataRevision}` + Idempotency-Key. 성공 200 `{id,status:"committed",result}`; 전체 원자 적용                                     |

미리보기는 24시간 후 만료하는 기본안이다. import job 저장은 업무 생성이 아니며 dataRevision을 증가시키지 않는다. 결과 확인/멱등 재전송은 업무를 다시 생성하지 않는다. expiresAt 이후 미확정 commit은 409 `IMPORT_EXPIRED`. 동일 작업실의 이미 성공한 sourceDigest를 다시 preview하면 기존 결과를 안내하고 중복 commit을 막는다. 재수입을 별도 기능으로 만들기 전에는 우회 플래그를 제공하지 않는다.

미리보기에서 sourceId→예약 서버 id 매핑을 만들어 job에 저장한다. 원본 projectId는 서버 API에 그대로 쓰지 않는다. 모호한 구형 태스크 이름 참조는 errors에 표시하고 사용자가 명시적으로 프로젝트를 정한 새 패키지로 preview한다. 외부 작업실 서버 id를 매핑 대상으로 받을 수 없다. `projectMappings`에는 원본 id, 예정 서버 id, 처리 방식을 포함하고 canCommit=false이면 commit을 거절한다.

commit에서 소유자→멱등 결과→job 유효성→workspace 잠금→baseDataRevision 일치→참조/쿼터 재검증 순으로 처리한다. 다른 저장이 먼저 일어나면 409 IMPORT_STALE, 재미리보기한다. 프로젝트→자식→링크 순서 및 명시한 배치를 같은 트랜잭션으로 저장하고 dataRevision을 증가시킨다. 하나라도 실패하면 전체 rollback한다. 장시간 대형 파일/부분 수입/비동기 작업 큐는 별도 범위다.

### 변환 규칙

- 프로젝트 보관 상태, 작업 휴지통 상태와 설명, 링크 배열 순서, 일지 본문, 마일스톤 완료/기한, 위젯 순서·projectId·limit을 보존한다.
- `forest`, `api`, `legacy-*` 등 예시 id에도 실제 사용자 편집이 있을 수 있다. id 모양만으로 버리지 않는다.
- 일지의 옛 createdAt은 사용자가 편집한 날짜일 수 있다. 확인받은 IANA timeZone으로 지역 날짜를 계산해 entryDate로 가져오고 원본 값은 import provenance에 기록한다. 서버 createdAt은 가져오기 시각이다. 이전 날짜 내 세부 순서가 달라질 수 있음을 미리보기에서 알린다.
- milestones 속성이 존재하면 그 배열을 사용하며 빈 배열도 존중한다. 키가 없을 때에만 사용자가 deriveLegacyMilestones를 선택하면 프로젝트 목표 메모에서 기한 없는 미완료 목표를 생성한다. 이미 존재하는 milestones와 이중 생성하지 않는다.
- 배치 projectId는 예정 프로젝트 id로 바꾼다. 선택 프로젝트를 가져오지 않아 참조가 풀리지 않으면 자동 제거/대체하지 않고 오류를 보여준다. 업무 가져오기와 배치 가져오기를 분리 선택할 수 있다.
- 원본 localStorage는 성공 후에도 자동 삭제하지 않는다. 로그인이 바뀌면 업로드 job/매핑을 재사용하지 않는다. 계정별로 가져오기 결과를 확인한다.

## 6. 수용 테스트와 완료 기준

프론트의 기존 테스트는 로컬 동작의 회귀 기준이다. 현재 확인된 19개 단위·35개 브라우저 테스트 통과가 서버 인증/소유권 검증 통과를 의미하지 않는다. 실제 DB·쿠키·HTTP를 사용하는 서버 통합 테스트와 프론트 연동 E2E를 추가한다.

| 분류            | 반드시 검증할 시나리오                                                                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 사용자 격리     | A/B 두 계정. A가 만든 프로젝트·자식·배치·링크·import id를 B가 GET/PATCH/DELETE/restore/order/참조 생성에 사용해도 정보 유출·변경 없음. 검색·통계에도 A 데이터 없음 |
| 세션            | 첫 로그인 경합 시 작업실 1개, 잘못된 state/nonce/PKCE/issuer 거절, 세션 회전/만료/로그아웃/비활성화, 외부 returnTo 거절, CSRF 없는 mutation 거절                   |
| 계정 전환       | A 로그아웃→B 로그인, A의 늦은 응답/초안/캐시/로컬 키가 B에 나타나거나 저장되지 않음. 다른 탭 로그아웃 전파                                                         |
| 경합/재시도     | 같은 revision 두 수정 중 하나만 성공. 첫 배치 동시 저장, 동일 키 생성 경합, 다른 본문 같은 키 거절, 응답 유실 후 재시도 시 중복 없음                               |
| 프로젝트/태스크 | 보관 후 데이터 유지, 신규 작업 대상 보관 경쟁, 이름·분야 전파, 삭제·복구 통계, 상태 드래그 실패 시 기존 상태                                                       |
| 일지            | 수정/영구 삭제, 본문 검색, 날짜 양끝 포함, 하루 범위, 윤년/잘못된 날짜, 기기 timezone 변경에도 entryDate 유지, 과거/최신 정렬                                      |
| 링크            | URL scheme/자격 증명 거절, 공통 링크 포함, URL 검색, 전체 순열/누락/중복/타 사용자 id 검증, 재정렬과 생성·삭제 경합, 빈 목록                                       |
| 마일스톤        | 생성/수정/완료/재개/영구 삭제, 삭제 revision 충돌·반복 404·소유권·CSRF/Origin, 정확히 한 번 dataRevision 증가와 rollback, Project/다른 목표 불변, 삭제 후 생성 replay의 비재생성, null 기한 정렬, 지연 표시, 보관 프로젝트 연동, 목표 메모와 독립, 홈/상세 공유 |
| 위젯            | 전체 카드 limit, 독립 통계, projectId 우선, 보관 프로젝트 유지, 구형 필드 없는 배치/빈 배치, 배치 취소와 업무 저장 독립                                            |
| 페이징/집계     | 100건 초과 목록, 필터 바뀐 cursor 거절, 전체 total과 items 구분, 사용자 범위 통계 및 보관/삭제 조건                                                                |
| 가져오기        | 원본 불변, 계정별 job 격리, sourceId 전부 매핑, 기한/완료/휴지통 보존, 빈 milestones 중복 생성 없음, 만료/경합/실패 rollback/중복 commit                           |
| UI 회귀         | 저장 실패 초안/재시도, 401/409 안내, 메뉴·모달 초점, 모바일 긴 내용, 새로고침/뒤로가기, API 장애를 데모로 숨기지 않음                                              |

출시 완료는 명세의 필수 API·OpenAPI·DB 마이그레이션·위 테스트·실제 프론트 연결이 모두 준비된 상태다. 기본 OIDC 제공자를 골랐다는 것만으로 인증 완료라 하지 않는다.

## 7. 운영 준비와 남은 결정

- SPA와 `/api` 라우팅을 분리한다. `/projects/{id}` 직접 접속은 SPA, `/api/v1/...` 오류는 JSON이며 index.html을 반환하면 안 된다. HTTPS와 인증 callback 경로를 staging에서 검증한다.
- 인증/업무 API 요청 제한, 최대 body/가져오기 크기, 사용자별 리소스/위젯/링크 수 제한을 설정한다. 초기 제안: 일반 body 128 KiB, 가져오기 5 MiB, 홈 위젯 100개, 사용자 링크 500개. 이는 기존 UI 제한이 아닌 운영 기본안이며 배포 전 확정·문서화·413/429 테스트가 필요하다.
- 세션과 멱등성 저장은 여러 서버 인스턴스가 공유하거나 동등한 일관성을 가져야 한다. 메모리 전용 저장으로 수평 확장/재시작 시 인증·멱등성을 잃지 않는다.
- DB 마이그레이션은 배포와 버전 관리하고 실제 백업 복원 테스트를 수행한다. health/readiness는 인증/개인 데이터 없이 최소 정보만 제공한다. 오류율·지연·인증 실패·충돌/쿼터 지표를 수집한다.
- 계정 삭제/데이터 내보내기, 개인정보와 import payload/멱등 응답의 보존 기간, 세션 만료 수치, 복구 목표, 신규 가입 허용 정책은 공개 운영 전 확정한다. UI가 없는 동안도 운영 절차와 책임자를 정한다. 이를 현재 구현된 기능으로 표시하지 않는다.
- 운영 모니터링 API/웹훅/실제 배포 실행은 안정화 16번 이후 요구를 확정해 별도 명세로 만든다. URL 저장과 서버의 URL 접속은 다르며 후자에는 네트워크 접근 정책이 추가로 필요하다.

이 문서 묶음은 백엔드 작업의 출발점이며 실제 배포나 데이터 이전을 수행하지 않는다.
