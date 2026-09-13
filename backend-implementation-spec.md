# 다중 사용자 백엔드 구현 명세

2026-09-10 기준 구현 대상 계약안이다. 현재 서버 동작을 설명하지 않는다. 제품 범위·기술 선택 가정은 [문서 안내](README.md), 기존 계약과의 변경점은 [대조표](frontend-contract-gap-analysis.md), 인증 보안·이전·검증 절차는 [개발 지침](backend-development-guide.md)을 따른다. 기존 계약을 읽어야만 구현 가능한 생략 규칙을 두지 않는다.

## 1. 사용자와 소유권

초기 모델은 `User 1 → 1 PersonalWorkspace → N 업무 리소스`다. 작업실은 로그인 사용자의 개인 소유이며 공개 접근·다른 사용자 초대가 없다.

- `users`: id, displayName, createdAt, updatedAt, disabledAt. 이메일은 선택 프로필 정보이며 소유권 키가 아니다.
- `auth_identities`: userId, issuer, subject. `(issuer, subject)` 유일. 인증된 외부 주체를 내부 userId로 매핑한다. 같은 이메일이라는 이유로 계정을 자동 합치지 않는다.
- `workspaces`: id, ownerUserId(유일), name, revision, createdAt, updatedAt. 내부 dataRevision(초기 0)은 모든 업무 변경 때 증가하며 가져오기 경쟁 검사에 사용한다. 최초 로그인 시 사용자·identity·빈 작업실을 원자 생성하며 중복 callback에도 하나만 생성한다.
- `sessions`: 안전한 세션 식별자 해시, userId, 생성/최종 활동/유휴 만료/절대 만료/폐기 시각. 실제 세션 토큰은 로그·API JSON·브라우저 저장소에 넣지 않는다.
- projects/tasks/journals/links/milestones/dashboards/link_collections/import_jobs는 모두 서버 내부 `workspaceId`를 가진다.

아래 API의 workspaceId는 **인증 세션의 userId로 서버가 조회**한다. 요청 body/query/header의 userId·ownerUserId·workspaceId로 권한을 선택하지 않는다. 이 필드를 변경 DTO에 보내면 400이다. URI에 workspaceId를 추가할 필요가 없다.

모든 상세·목록·검색·통계·수정·삭제·복구·정렬·가져오기에서 작업실 범위를 적용한다. UUID를 알기 어렵다는 것은 권한 검사가 아니다. 다른 사용자의 id와 존재하지 않는 id는 모두 404 `RESOURCE_NOT_FOUND`로 응답한다. 프로젝트 외래키와 배치 JSON 안의 projectId도 같은 범위로 검증한다. 데이터 존재나 revision을 먼저 알려주지 않는다.

관계형 저장소에서 projects에 `(workspace_id, id)` 유일 제약, tasks/journals/milestones에 `(workspace_id, project_id)` 복합 FK를 둔다. 세션에서 구한 작업실로 조회하는 저장소 인터페이스를 사용한다. 배치·가져오기의 JSON 참조는 트랜잭션 안에서 별도 검증한다. 작업실 간 이동 API는 제공하지 않는다.

## 2. 공통 HTTP·저장 계약

기본 경로 `/api/v1`, JSON UTF-8. 예제 id는 설명용 불투명 문자열이며 실제 리소스 id는 서버가 발급한다. 위젯 인스턴스 id와 가져오기 sourceId만 클라이언트 식별자를 허용한다.

### 응답과 검증

- 생성 201, 조회/수정/배치 저장/소프트 삭제/복구 200. 변경 성공은 전체 리소스를 반환한다. 영구 삭제는 200 삭제 확인 DTO를 반환한다. 인증 redirect/logout은 3절의 예외다.
- 일반 목록: `{ "items": [], "total": 0, "nextCursor": null }`. total은 해당 조건 전체 건수, items는 현재 페이지다. 오류를 빈 목록으로 숨기지 않는다.
- 일반 리소스: id, revision, createdAt, updatedAt + 도메인 필드. revision은 안전한 양의 정수, 최초 1. 날짜는 `YYYY-MM-DD`, 감사 시각은 UTC ISO 8601. 응답에 내부 소유권/인증 정보는 포함하지 않는다.
- PATCH는 전달한 필드만 수정. 선택 문자열의 빈 값은 `""`, 선택 날짜의 해제는 `null`; 필수값의 null은 400. 서버 발급 필드와 파생 필드는 변경 불가. 알 수 없는 쓰기 필드는 400으로 거절한다.
- 문자열 제한은 HTML maxLength와 같은 UTF-16 코드 단위. 필수 문자열은 trim 후 검사. 제목·이름·URL·태그는 trim, 본문은 내용 보존하되 공백만인 필수 본문은 거절한다. 일반 텍스트로 저장·표시하며 HTML 실행을 허용하지 않는다.
- URL은 파싱 가능한 http/https만 허용한다. 링크의 URL에는 username/password를 허용하지 않는다. 저장소 URL에도 같은 제한을 적용하는 것은 연동 시 추가 검증이다. URL 저장 자체로 서버가 해당 주소를 요청하지 않는다.

오류 DTO:

```json
{
  "code": "REVISION_CONFLICT",
  "message": "다른 기기에서 변경되었습니다.",
  "fieldErrors": {},
  "requestId": "req-example"
}
```

| HTTP    | code 예                                                                                     | 처리                                                     |
| ------- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| 400     | VALIDATION_ERROR, INVALID_CURSOR, UNSUPPORTED_SCHEMA_VERSION                                | 필드 오류 표시; 저장 전 상태 유지                        |
| 401     | AUTH_REQUIRED, SESSION_EXPIRED                                                              | 인증 경계로 전환; 계정 소유가 확인되기 전 초안 전송 금지 |
| 403     | CSRF_INVALID, ACCOUNT_DISABLED                                                              | 재인증/권한 안내; 리소스 소유권 유출에 사용하지 않음     |
| 404     | RESOURCE_NOT_FOUND                                                                          | 없거나 접근 불가; 다른 소유자의 정보 포함 금지           |
| 409     | REVISION_CONFLICT, PROJECT_ARCHIVED, RESOURCE_DELETED, IDEMPOTENCY_KEY_REUSED, IMPORT_STALE | 자동 덮어쓰기 금지; 최신 조회와 초안 비교                |
| 413     | PAYLOAD_TOO_LARGE                                                                           | 입력/가져오기 용량 안내                                  |
| 429     | RATE_LIMITED, QUOTA_EXCEEDED                                                                | Retry-After 또는 제한 안내; 초안 보존                    |
| 500/503 | INTERNAL_ERROR, SERVICE_UNAVAILABLE                                                         | 재시도 가능한 오류 표시; 내부 예외/SQL/비밀값 노출 금지  |

### revision과 멱등성

PATCH/PUT/restore의 본문 revision, DELETE의 query revision을 요구한다. 누락 400, 불일치 409. 프로젝트 보관, 태스크 상태 이동, 마일스톤 완료/재개에도 같은 규칙을 적용한다. 비교와 변경은 한 트랜잭션에서 수행하고 저장되는 리소스 revision은 성공마다 1 증가한다. 영구 삭제는 현재 revision을 검증한 뒤 행을 제거하므로 새 revision이나 tombstone을 남기지 않는다. 응답 유실 후 같은 구 revision으로 재시도해도 조용히 덮어쓰지 않는다.

업무 리소스 POST(프로젝트·태스크·일지·링크·마일스톤)와 import commit에 `Idempotency-Key`를 요구한다. 사용자/작업실/메서드/정규 경로/키에 유일 제약을 두고 요청 해시·성공 상태·응답을 24시간 보존한다. 같은 본문은 같은 결과, 다른 본문은 409. 경쟁 요청은 키 잠금 후 하나만 실행한다. 트랜잭션이 rollback되면 성공 결과를 기록하지 않는다. 인증과 소유권 검증은 재전송에도 먼저 수행한다. 삭제 후 저장된 멱등 결과가 있어도 리소스를 재생성하지 않는다.

시간 초과는 실패 확정이 아니다. 생성은 동일 키/본문으로 재시도한다. 수정·삭제는 재조회해 처리 여부를 확인한다. 키 보존 기간을 넘기면 결과 확인 없이 자동 생성하지 않는다. 충돌 응답에 다른 사용자의 최신 데이터나 revision을 포함하지 않는다.

### 목록 필터와 순서

- scope 기본 all, 허용 all/unity/server. projectId는 선택이며 소유권 확인 후 scope와 교집합 적용. 분야가 다르면 빈 목록이다. 위젯 특정 프로젝트는 scope=all로 호출한다.
- query 기본 `""`, 대소문자를 구분하지 않는 리터럴 부분 검색. SQL LIKE의 `%`/`_`는 검색 문자로 escape한다. 전체 텍스트 검색으로 의미를 바꾸려면 계약과 UI를 함께 변경한다.
- 일반 목록 cursor/limit: 기본 20, 1~100. cursor는 작업실·필터·정렬·마지막 정렬 키에 묶인 불투명 값. 변조·다른 조건 재사용은 400. id를 마지막 정렬 키로 사용한다.
- 페이지 사이 데이터 변경에 대한 스냅샷 보장은 초기 범위에 없다. 첫 페이지/필터를 다시 읽을 때 cursor를 초기화하고 프론트는 id로 중복 제거한다. 화면 전체 통계는 별도 집계다.
- 보관 필터는 프로젝트의 `status=active|archived|all`, 자식 리소스의 `projectStatus=active|archived|all`로 구분한다. 자식 기본값은 all이다.

## 3. 인증·내 작업실

다음 로그인 경로는 OIDC 기본 제안의 계약이다. 직접 비밀번호 로그인 방식으로 결정하면 이 절을 먼저 개정한다. 로그인 공급자 토큰과 외부 저장소 연동 토큰은 별개다.

| API                                     | 동작                                                                                                                     |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| GET `/auth/login?returnTo=/projects`    | 사전 등록 제공자로 302 이동. 서버에 일회성 state/nonce/PKCE 거래 저장. returnTo는 허용된 상대 SPA 경로만 수용            |
| GET `/auth/callback?code=...&state=...` | 제공자 응답 검증/코드 교환/ID Token 검증 후 세션 생성 및 302 SPA 이동. 실패는 고정 오류 경로; 토큰을 URL로 반환하지 않음 |
| GET `/auth/csrf`                        | 인증 세션에 묶인 `{csrfToken}` 반환; Cache-Control: no-store. 이후 mutation은 X-CSRF-Token 헤더 사용                     |
| GET `/me`                               | `{id, displayName, workspace: {id, name, revision}}`; 미인증 401. 인증 경계 초기화용                                     |
| POST `/auth/logout`                     | 서버 세션 폐기와 쿠키 만료 후 204. 인증된 요청은 CSRF 검사. 이미 세션이 없으면 쿠키 제거 후 204                          |
| GET `/me/workspace`                     | `{id, name, revision, createdAt, updatedAt}`                                                                             |
| PATCH `/me/workspace`                   | `{name, revision}`, name trim 1~100. 사용자별 작업실 이름; 현재 UI에는 편집 화면 추가 필요                               |

기본 작업실 이름은 `나의 작업실`. 이름 설정은 사용자별 환경 확장의 작은 추가 기능이며 현재 프론트에 이미 있다고 가정하지 않는다. 로그인 전에 업무 API는 모두 401. 비활성 사용자 세션은 즉시 거절한다. 신규 계정은 업무 테이블이 빈 상태이며 기본 배치는 7절의 가상 응답이다.

## 4. 프로젝트·태스크

### 프로젝트

리소스: 공통 필드 + `name, subtitle, scope, stack, progress, currentMilestone, repositoryUrl, status, colorToken`. scope는 unity/server, status는 active/archived. currentMilestone은 **목표 메모 문자열**이며 독립 마일스톤 id가 아니다. progress는 수동 숫자 0~100(소수 허용)이며 태스크 완료율로 바꾸지 않는다. colorToken은 서버가 분야에 따라 허용 토큰을 반환하고 프론트는 알 수 없는 값에 기본 아이콘/색을 사용한다.

| API                    | 입력/조회                                                                                                                 |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| GET `/projects`        | scope, query(name·stack), status(기본 active), cursor, limit. createdAt DESC, id DESC                                     |
| GET `/projects/{id}`   | 보관 포함 소유자의 전체 리소스                                                                                            |
| POST `/projects`       | name, subtitle, scope, stack, progress, currentMilestone, repositoryUrl. 선택값 기본 `""`, progress 기본 0, status=active |
| PATCH `/projects/{id}` | 위 입력 필드 및 status, revision. 보관/해제 포함                                                                          |

name 필수 최대 100, stack 필수 200, subtitle 4000, currentMilestone 200, repositoryUrl 2000. 영구 삭제 API 없음. 보관은 연결된 업무 데이터를 지우지 않는다. 이름/분야 변경은 작업·일지·마일스톤의 파생 이름/분야 및 검색·통계에 즉시 반영한다.

### 태스크

리소스: 공통 필드 + `title, projectId, projectName, scope, description, status, priority, tag, deletedAt`. status=todo/doing/done, priority=normal/high, tag는 단일 문자열. deletedAt은 null 또는 UTC 시각.

| API                               | 입력/조회                                                                                                                                 |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| GET `/tasks`                      | scope, projectId, projectStatus, query(title·projectName), status(생략=모두), deleted(기본 false), cursor, limit. createdAt DESC, id DESC |
| GET `/tasks/{id}`                 | 휴지통 포함 소유자 상세. description과 revision 포함                                                                                      |
| POST `/tasks`                     | title, projectId 필수; description/tag 기본 `""`, status 기본 todo, priority 기본 normal                                                  |
| PATCH `/tasks/{id}`               | 위 필드의 부분 변경, revision. 드래그도 `{status, revision}`                                                                              |
| DELETE `/tasks/{id}?revision=...` | 소프트 삭제 후 전체 태스크 반환                                                                                                           |
| POST `/tasks/{id}/restore`        | `{revision}`, deletedAt=null 후 전체 태스크 반환; 생성용 Idempotency-Key 요구 대상 아님                                                   |

title 최대 160, description 10000, tag 40. 일반 PATCH로 deletedAt 수정 불가. 삭제된 태스크 PATCH는 409 `RESOURCE_DELETED`, 이미 삭제된 태스크 재삭제와 미삭제 태스크 restore는 409 `INVALID_RESOURCE_STATE`다. 같은 구 revision 재시도는 상태보다 revision 충돌을 먼저 판정한다.

새 작업과 다른 프로젝트로의 변경은 미보관 프로젝트만 허용한다. 기존 보관 프로젝트 연결을 유지하는 수정·상태 변경·삭제·복구는 허용한다. 프로젝트 보관 경쟁과 생성/연결 변경은 동일 프로젝트 잠금 또는 동등한 트랜잭션 격리로 검사한다.

## 5. 개발일지

리소스: 공통 필드 + `title, projectId, projectName, scope, body, entryDate`. 본문을 포함한 전체 DTO를 목록/상세에서 반환하는 것을 초기 계약으로 한다. 이후 요약 DTO 최적화는 명시적으로 버전 관리한다.

| API                                  | 입력/조회                                                                                                         |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| GET `/journals`                      | scope, projectId, projectStatus, query(title·projectName·body), from, to, sort(newest 기본/oldest), cursor, limit |
| GET `/journals/{id}`                 | 전체 리소스                                                                                                       |
| POST `/journals`                     | title, projectId, body, entryDate 필수                                                                            |
| PATCH `/journals/{id}`               | 위 필드의 부분 변경과 revision                                                                                    |
| DELETE `/journals/{id}?revision=...` | 영구 삭제; `{deletedId}`. 복구/휴지통 API 없음                                                                    |

title 최대 120, body 최대 20000, 둘 다 필수. entryDate는 유효한 달력 날짜다. createdAt은 서버 감사 시각이고 변경할 수 없다. from/to는 날짜이고 **양끝 포함**하며 from>to는 400이다. 기간 필터에 UTC 경계 시간을 보내지 않는다. newest=`entryDate DESC, createdAt DESC, id DESC`, oldest는 세 키 ASC. 편집 시 updatedAt만 변경하고 createdAt 유지. 프론트는 날짜 표시를 entryDate 기준으로 바꿔야 한다.

새 일지/프로젝트 변경은 미보관 대상만, 동일한 보관 프로젝트 연결을 유지하는 편집·삭제는 허용한다. 영구 삭제 후 GET은 404이며 삭제 응답 유실 시 재조회 404를 확인하고 사용자에게 완료 처리할 수 있다. 백업 보존은 API 복구 기능과 다르며 운영 정책에 명시한다.

## 6. 링크·마일스톤

### 링크

리소스: 공통 필드 + `label, description, url, scope, position`. scope=all/unity/server, projectId 없음. label 필수 100, description 최대 300(기본 `""`), url 필수 2000. scope 기본 all. position은 서버 관리 정수다.

| API                               | 입력/조회                                                                                                                                                |
| --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET `/links`                      | scope, query(label·description·url). `{items,total,nextCursor:null,collectionRevision}`. position ASC, id ASC; 현재 화면 호환을 위해 필터 결과 전체 반환 |
| GET `/links/{id}`                 | 전체 리소스                                                                                                                                              |
| POST `/links`                     | label, description, url, scope. 전체 목록 끝에 추가. `{item, collectionRevision}`                                                                        |
| PATCH `/links/{id}`               | 위 필드와 revision. `{item, collectionRevision}`                                                                                                         |
| DELETE `/links/{id}?revision=...` | 영구 삭제 후 `{deletedId, collectionRevision}`                                                                                                           |
| PUT `/links/order`                | `{collectionRevision, ids}`: 사용자 전체 목록의 정확한 순열. `{items,total,nextCursor:null,collectionRevision}`                                          |

분야 조회는 해당 scope와 all 공통 링크를 포함한다. 재정렬은 필터를 초기화한 전체 목록에서만 한다. ids의 누락/중복/다른 작업실 id를 검증하고 일부만 저장하지 않는다. 잘못된 집합은 400(다른 작업실 상세 정보 없이), 오래된 collectionRevision은 409다.

작업실마다 link_collections revision을 관리한다(빈 초기 목록 0). 모든 링크 생성/수정/삭제/재정렬이 이 행을 잠그고 collectionRevision을 증가시킨다. 생성/수정/삭제는 각자의 요청 계약으로 저장하며 응답에 새 collectionRevision을 포함한다. 재정렬은 변경된 위치의 링크 revision/updatedAt도 증가시킨다. 목록 읽기는 items와 collectionRevision을 동일 스냅샷에서 반환한다. 빈 ids는 실제 빈 목록에서만 유효하다. 전체 반환 API의 링크 수/응답 크기 상한은 배포 설정과 계약 테스트로 고정한다.

### 마일스톤

리소스: 공통 필드 + `projectId, projectName, scope, title, dueDate, completed`. title 필수 최대 200, dueDate는 날짜 또는 null(기본), completed boolean(기본 false). 로컬 `dueDate=""`는 null로 변환한다. 별도 progress/상태 enum/본문/완료 기준은 도입하지 않는다.

| API                      | 입력/조회                                                                  |
| ------------------------ | -------------------------------------------------------------------------- |
| GET `/milestones`        | scope, projectId, projectStatus, status(open 기본/done/all), cursor, limit |
| GET `/milestones/{id}`   | 전체 리소스                                                                |
| POST `/milestones`       | projectId, title, dueDate, completed                                       |
| PATCH `/milestones/{id}` | 위 필드와 revision. 완료/재개도 `{completed, revision}`                    |
| DELETE `/milestones/{id}?revision=...` | 현재 revision으로 영구 삭제; 200 `{deletedId}` |

정렬은 completed ASC → dueDate ASC(null 마지막) → id ASC. 지연 목표도 포함한다. 기한 지남 표시는 미완료이고 dueDate가 브라우저의 오늘보다 이전일 때 프론트가 계산한다. 서버에서 timezone 의존 overdue 값을 저장하지 않는다.

DELETE `/api/v1/milestones/{id}?revision=...`는 인증된 사용자의 작업실 범위에서 마일스톤과 연결 Project 소유권을 확인하고 현재 revision을 요구한다. revision 누락/잘못된 값은 400 `VALIDATION_ERROR`, 소유한 기존 행의 오래된 revision은 409 `REVISION_CONFLICT`다. 없거나 접근할 수 없는 마일스톤은 revision 충돌을 노출하지 않고 404 `RESOURCE_NOT_FOUND`로 응답한다. 세션 인증, `X-CSRF-Token` 및 Origin 검증은 다른 mutation과 동일하게 적용한다.

성공하면 한 트랜잭션에서 해당 마일스톤 행만 영구 제거하고 workspace.dataRevision을 정확히 한 번 증가시키며, 200 `{ "deletedId": "..." }`를 반환한다. 휴지통·소프트 삭제·복구·tombstone은 없다. 삭제 시 새 리소스 revision을 저장하지 않으므로 최대 유효 revision에서도 삭제할 수 있다. 이미 삭제된 id의 GET/PATCH 및 반복 DELETE는 404다. 실패/반복 삭제는 dataRevision을 증가시키지 않으며 rollback 시 행과 카운터가 함께 복원된다. Project와 다른 마일스톤의 데이터·revision·감사 시각, 공개 Workspace 메타데이터 revision은 변경하지 않는다. 완료 여부나 소유 Project의 보관 여부와 관계없이 삭제할 수 있다. DELETE에는 생성용 Idempotency-Key를 요구하지 않는다.

생성 POST의 24시간 멱등 결과는 삭제 후에도 보존한다. 같은 키/본문의 재전송은 인증된 작업실과 원래 Project 소유권을 다시 확인한 뒤 원래 201 응답을 반환하며, 삭제된 마일스톤을 재생성하거나 dataRevision을 증가시키지 않는다. 살아 있는 행이 있으면 현재 Project 관계도 검증한다. 이 생성 재전송 규칙은 반복 DELETE의 404 규칙을 바꾸지 않는다.

현재 마일스톤 UI를 유지하기 위해 소유자의 보관 프로젝트도 생성/편집/재개 대상이 될 수 있다. 일반 홈은 projectStatus=active, 특정 프로젝트 위젯과 상세는 projectStatus=all + projectId로 조회한다. 프로젝트 목표 메모를 바꿔도 마일스톤을 자동 생성·수정하지 않는다.

## 7. 홈 배치와 조회 조합

GET `/dashboards/home`: `{id:"home", schemaVersion:1, revision, widgets}`. 미저장 시 기본 위젯 구성과 revision=0, 업무 데이터는 빈 값이다. GET으로 기록 생성 금지. PUT `/dashboards/home`: `{schemaVersion:1, revision, widgets}`, 전체 원자 저장. 처음 0 비교 후 1, 이후 비교·증가. 사용자별 home 유일 제약으로 동시 최초 저장도 하나만 성공한다.

위젯 DTO: `{id,type,title,scope,size,projectId?,limit?}`. 배열 순서가 배치 순서다. id는 비어 있지 않은 문자열이며 배치 내 유일. type=overview/board/deploy/links/journal/milestone, size=small/medium/wide. title trim 1~48. scope=all/unity/server. 같은 type의 여러 인스턴스와 widgets=[] 허용.

- projectId/limit은 overview/board/journal/milestone만 허용한다. limit은 정수 1~20, 생략 시 아래 기본값. deploy/links에 이 설정을 보내면 400.
- projectId가 있으면 소유권 검증(보관도 허용) 후 scope=all로 정규화해 반환한다. 접근 불가능한 참조는 404. 조회 시 참조가 깨져도 다른 프로젝트로 자동 대체하지 않는다.
- 기본 복원은 프론트 초안으로 처리한다. 위젯 제거는 업무 데이터를 삭제하지 않는다. 배치 저장/취소와 업무 저장은 서로 독립이다.
- 로컬 schemaVersion 없는 배열을 서버로 올릴 때 `{schemaVersion:1,revision,widgets}`로 감싼다. 기존 projectId/limit 없는 위젯도 유효하다. 미래 스키마 변경은 별도 마이그레이션 필요.

| 화면/위젯                | 서버 조회와 표시 규칙                                                                                                                         |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| 프로젝트 개요            | 일반 위젯 GET projects(status=active), 선택 프로젝트면 GET projects/{id}(보관 포함). 목록 limit 적용, 전체 통계는 overview로 별도 조회        |
| 홈 작업 보드             | GET tasks(scope/projectId, deleted=false, status 생략). 설정 limit은 **모든 열 합계**에 적용하고 그 결과를 상태별 렌더링. 통계는 limit과 무관 |
| 작업 페이지              | 열별 GET tasks(status=todo/doing/done)와 GET tasks/stats. 페이지마다 더 보기 제공; 홈의 전체 limit 규칙과 구분                                |
| 일지 위젯·상세 최근 일지 | sort=newest, 기본 표시 3, 위젯 limit 있으면 해당 값. 보관 프로젝트 일지도 기본 포함                                                           |
| 마일스톤 위젯            | 기본 open, 표시 2, 위젯 limit 우선. 일반 홈 projectStatus=active; 완료/전체 필터도 같은 limit 적용                                            |
| 프로젝트 상세 목표       | projectId, projectStatus=all, 기본 open. 표시 제한 없이 페이징으로 전체 접근                                                                  |
| 링크                     | scope 필터(공통 포함), 전체 반환. 프로젝트·표시 개수 설정 없음                                                                                |
| 운영                     | 업무 서버 초기 범위 밖. 프론트는 미연결/데모를 명확히 표시; 로그인만으로 정상 운영 상태를 표시하지 않음                                       |

현재 개요/보드에서 limit 생략은 모든 항목이다. 서버의 기본 20건을 “전체”로 오표시하지 말고 페이징/더 보기를 붙인다. 홈 검색과 분야 탭은 보이는 위젯 선택이며 업무 API 필터를 몰래 덮어쓰지 않는다.

## 8. 통계

GET `/overview?scope=all&projectId=...`:

```json
{
  "scope": "all",
  "projectId": null,
  "projects": {
    "total": 2,
    "archived": 1,
    "byScope": { "unity": { "total": 1, "archived": 1 }, "server": { "total": 1, "archived": 0 } }
  },
  "tasks": { "todo": 2, "doing": 1, "done": 3, "total": 6 },
  "asOf": "2026-09-10T09:00:00Z"
}
```

projects.total은 미보관 수, archived는 보관 수다. 프로젝트 검색/보관 탭 상태와 독립적이며 query를 받지 않는다. byScope의 두 키를 항상 반환하며 범위 밖은 0이다. 선택 보관 프로젝트는 total=0, archived=1이며 프론트에서 보관된 프로젝트 수로 표시한다.

GET `/tasks/stats`: scope, projectId, projectStatus(기본 all), query(title·projectName). 응답 `{counts:{todo,doing,done},total,asOf}`. status/deleted 필터는 받지 않는다. 미삭제 작업만 집계하며 보관 프로젝트 작업도 기본 포함한다. 같은 조건의 미삭제 tasks 전체 total과 일치해야 한다. overview의 작업 집계도 동일한 공통 집계 로직을 사용하되 검색 제한은 없다. 권한 검사 후 같은 조회 스냅샷에서 집계한다.

## 9. 프론트 DTO 변환표

| 현재 로컬 필드/동작                    | API 표현 / 필요한 변경                                                      |
| -------------------------------------- | --------------------------------------------------------------------------- |
| Project.archived, milestone, color     | status, currentMilestone(메모), colorToken                                  |
| Task.project, priority(보통/높음), tag | projectName, normal/high, 단일 tag                                          |
| Journal.project, 수정 가능한 createdAt | projectName, entryDate + 불변 createdAt; 표시/편집/정렬을 분리              |
| QuickLink.desc와 배열 순서             | description과 position; 응답의 collectionRevision도 보관                    |
| Milestone.dueDate=""                   | dueDate=null; completed는 그대로                                            |
| Widget.projectId/limit 생략            | 기본값 유지. 특정 프로젝트는 scope=all                                      |
| boolean 저장 반환값                    | Promise mutation 결과 + pending/error/revision; 성공 응답 전 모달 종료 금지 |
| 클라이언트 UUID 리소스 생성            | 서버 id로 교체; 재시도용 Idempotency-Key는 별도 유지                        |

신규 DTO 타입/변환 함수를 feature 경계 안에 두고, 공용 HTTP 클라이언트에는 도메인 의존성을 넣지 않는다. id/revision/날짜를 손실하는 범용 `as` 캐스팅으로 기존 타입에 억지로 맞추지 않는다.
