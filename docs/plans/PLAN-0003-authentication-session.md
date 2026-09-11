# PLAN-0003: Authentication and Session

Status: `completed`

## Goal

Spring Security와 하나의 설정 가능한 OIDC provider를 사용해 브라우저 로그인, 서버 세션, CSRF, 로그아웃 및 `/api/v1/me` 인증 경계를 구축한다. 검증된 `(issuer, subject)`를 기존 AuthIdentity에 연결하고 최초 로그인만 User·AuthIdentity·PersonalWorkspace를 원자 생성한다. 브라우저 인증은 서버 세션을 사용한다.

## Scope

- 기준: 현재 `AGENTS.md`, `docs/agents/SECURITY.md`, `API.md`, `SPRING_BOOT.md`, `ARCHITECTURE.md`, `IMPLEMENTATION/` 지침 및 사용자 결정이 우선한다. `backend-implementation-spec.md` 1–3절은 기능 계약, `backend-development-guide.md`는 구현·연동 참고 자료다.
- 현재 상태: PLAN-0002는 completed이며 V1, 세 도메인의 JPA 매핑, 최소 repository, `UserWorkspaceCreationService`와 별도 transaction bean이 존재한다. 동일 identity 경쟁 시 실패 트랜잭션 rollback 후 재조회하며 기존 테스트 결과는 15개 통과다. 이번 Plan의 SecurityFilterChain, OIDC 설정, 인증 controller 및 generated OpenAPI 경계가 구현되었다.
- Java 21, Spring Boot 4.1.1, Gradle Wrapper 및 기존 Security·Validation·springdoc·PostgreSQL Testcontainers를 재사용한다. OIDC client starter는 아직 없으므로 이번에 요청된 기능에 필요한 Boot 관리 OAuth2 client dependency만 추가 대상으로 삼는다. 테스트 지원의 현재 전이 의존성을 확인하고 필요한 framework test support만 보완한다. 버전 업그레이드나 별도 provider SDK는 포함하지 않는다.
- 초기 OIDC provider는 Google로 정하고(`accounts.google.com` discovery), 구현은 표준 Spring Security OAuth2/OIDC registration을 사용한다. 기존 `.env` 명시적 import와 `application.yml`을 유지한다. issuer URI, client ID/secret, callback의 신뢰 가능한 애플리케이션 base URL, 허용 로컬 origin 및 세션 정책 값을 환경 설정으로 받는다. 한 registration만 구성하고 `openid` 및 표시 이름에 필요한 최소 scope를 요청한다. `.env`는 Git에서 제외하고 `.env.example`에는 변수 설명만 추가한다. Google 가입·실제 계정·실제 secret이 자동 테스트의 전제가 되지 않는다.
- Framework의 Authorization Code, state, nonce, PKCE 및 ID Token 검증을 사용한다. issuer·audience·서명·만료·nonce 검증 이전에는 내부 사용자 조회/생성을 수행하지 않는다. 이메일 자동 병합은 금지한다. 표시 이름은 검증된 name, preferred_username 순으로 선택하고 사용할 값이 없으면 비어 있지 않은 고정 기본 이름을 사용한다. 기존 사용자의 표시 이름을 로그인마다 덮어쓰지 않는다.
- `auth` feature의 presentation/infrastructure에서 HTTP 및 Spring Security/OIDC 연동을 담당하고 application에서 내부 사용자 결합·조회와 transaction을 조정한다. 기존 user/workspace domain에는 Security SDK 의존성을 넣지 않는다. 외부 통신은 DB transaction 밖에서 수행하며, 생성 서비스의 rollback/경합 재조회 구조를 보존한다. 세션에는 JPA entity graph 대신 내부 user ID 중심의 인증 principal을 둔다.
- 서버 저장은 기본 Servlet HttpSession/SecurityContext를 사용한다. 이전 명세의 직접 만든 `sessions` 해시 테이블은 구현하지 않으며 Spring Session JDBC/Redis, 신규 migration도 추가하지 않는다. 이는 이 Plan의 명시적 최소 구현 제안이다. 단일 프로세스 범위이고 재시작 시 세션은 소멸한다. 재시작 간 세션 보존·다중 인스턴스 세션 공유는 포함하지 않는다.
- 로그인 성공 시 session fixation 보호, 서버 SecurityContext 저장, 로그아웃 시 세션 무효화·쿠키 제거·관련 인증 거래/authorized-client 정리를 적용한다. 표준 Servlet session idle timeout만 설정 가능하게 하며, absolute expiration 전용 custom session infrastructure는 추가하지 않는다. provider access/refresh token으로 앱 세션을 갱신하지 않는다.
- 쿠키는 HttpOnly·Secure·SameSite=Lax, Path=/ 및 Domain 미설정을 기준으로 한다. 로컬 HTTP 실행은 명시적 로컬 설정에서만 Secure=false와 일반 쿠키 이름을 사용하고 HTTPS용 `__Host-` 이름과 혼용하지 않는다. 배포 구성을 추가하지 않고 로컬 검증 방법과 환경 차이를 문서화한다.
- 인증 기본 요구, 로그인/callback에 필요한 경로만 public 허용한다. `/v3/api-docs`와 Swagger UI는 인증된 접근으로 검증하며 편의를 위한 전역 permitAll은 하지 않는다. API 미인증은 로그인 HTML redirect 대신 401 JSON이다. 기존 framework 기본 로그인/Basic 인증을 애플리케이션의 대체 로그인 수단으로 노출하지 않는다.
- disabledAt은 기존 모델 그대로 사용한다. 로그인 완료 전에 disabled User를 거절하고, 기존 인증 요청에서도 DB 상태를 확인해 비활성 사용자 세션을 무효화하고 접근을 거절한다. 계정 비활성화 API는 만들지 않는다. 현재 workspace는 인증 principal의 내부 user ID와 owner 조회로만 결정한다.
- CSRF는 서버 세션에 묶인 토큰과 `X-CSRF-Token`을 사용하며 전역 비활성화하지 않는다. mutation의 Origin은 신뢰 가능한 애플리케이션 origin 기준으로 검증한다. CORS를 전역 해제하거나 wildcard로 열지 않는다. 필요하지 않은 cross-origin 접근은 추가하지 않는다.
- `returnTo`는 허용된 상대 SPA 경로만 서버에서 검증하고 로그인 거래에 저장한다. 기본 목적지는 `/`; 외부 URL, `//host`, 역슬래시·인코딩 우회, API/auth 경로는 안전한 기본값으로 정규화한다. callback 쿼리나 임의 Host 헤더로 최종 목적지를 정하지 않는다. 실패는 고정된 상대 오류 목적지 `/?authError=login_failed`로 이동하며 상세 provider 오류나 token/code/state를 포함하지 않는다. 프론트 오류 화면은 구현하지 않는다.
- 공통 오류 DTO `{code, message, fieldErrors, requestId}`와 Security entry point/access-denied 처리의 최소 공통화를 포함한다. 401 AUTH_REQUIRED/SESSION_EXPIRED, 403 CSRF_INVALID/ACCOUNT_DISABLED를 실제 구분 가능한 경우에 사용한다. 만료 여부를 입증할 수 없는 요청은 AUTH_REQUIRED로 처리한다. 토큰, secret, 인증 code/state, session ID 및 SQL/stack trace를 응답·로그에 노출하지 않는다.
- 제외: JWT bearer/browser 인증, 자체 비밀번호 로그인, 외부 provider API 연동, GitHub 저장소 연동, workspace 편집, Project/Task/Journal 등 업무 API·테이블, frontend 변경, Redis/Kafka, production deployment, CI/CD 및 관련 없는 refactoring. OIDC 프로토콜 내부 ID Token 검증은 브라우저용 JWT 인증 도입과 구분한다.

이번 Plan의 HTTP 계약은 다음과 같다. 경로는 모두 `/api/v1` 기준이다.

| Method / path | 접근 및 요청 | 응답 |
| --- | --- | --- |
| GET `/auth/login` | public, 선택 `returnTo`; 설정된 단일 provider만 선택 | 302 provider authorization redirect; 서버에 일회성 로그인 거래 저장 |
| GET `/auth/callback/{registrationId}` | public, framework가 code/state 또는 provider 오류 처리; registration의 redirect URI와 일치하도록 Security callback matcher 설정 | 검증·내부 사용자 확정·세션 생성 후 302 안전한 목적지; 실패는 고정 오류 목적지로 302, 인증 세션 없음 |
| GET `/auth/csrf` | 인증 세션 필요 | 200 `{csrfToken}`, `Cache-Control: no-store`; 미인증 401 |
| POST `/auth/logout` | 인증된 요청에는 유효한 CSRF와 origin 검증 필요 | 성공 204, 인증·세션·쿠키 제거; 이미 인증 세션이 없으면 쿠키 제거 후 204 |
| GET `/me` | 인증된 활성 내부 User | 200 `{id, displayName, workspace: {id, name, revision}}`, `Cache-Control: no-store`; 미인증 401, 비활성 403 후 세션 무효화 |

`/me`는 전용 DTO로 UUID를 문자열로 직렬화하고 기존 명시적 workspace revision을 반환한다. issuer/subject, disabledAt, dataRevision, provider token 및 entity 전체는 노출하지 않는다. 클라이언트 userId/ownerUserId/workspaceId를 조회 권한으로 사용하지 않는다. logout의 세션 부재 204 예외는 해당 경로와 인증 부재에만 한정하며 인증된 logout의 CSRF를 우회하지 않는다.

## Execution Sessions

Execute sessions in order.

Do not start the next session until the current session's implementation and validation are complete.

Check an item only after the corresponding work has actually been completed.
Update this Plan as execution progresses.

승인 후에는 세션별 별도 승인 없이 순서대로 실행할 수 있다. 이번 작성 작업에서는 어떤 세션도 시작하지 않는다.

### Session 1: Authentication and provider configuration

Status: `completed`

Objective:

기존 실행 환경 위에서 단일 OIDC provider와 기본 거절 Security 경계를 구성한다.

Implementation:

- [x] 현재 Boot/Security 버전에서 필요한 OAuth2 client dependency와 테스트 지원을 확인하고 최소 항목만 추가한다. `.env`/YAML의 provider·callback·로컬 cookie 설정과 설명형 `.env.example`을 구성한다.
- [x] 로그인/callback 경로를 framework authorization request/response 처리에 연결하고 state·nonce·PKCE 및 안전한 returnTo/실패 redirect를 구성한다. OIDC 검증 실패 시 인증은 완료되지 않도록 한다.
- [x] 인증 기본 요구와 최소 public matcher를 구성하고 API 401/403 공통 오류 처리 기반을 마련한다.

Validation:

- [x] 테스트 전용 registration/metadata로 외부 discovery 없이 context가 시작되고 기존 PostgreSQL persistence 테스트가 유지된다.
- [x] 로그인은 설정된 provider로 302 이동하고 redirect URI, scope 및 PKCE/state 설정이 의도와 일치한다. unsafe returnTo가 외부 redirect를 만들지 않는다.
- [x] 미인증 보호 API가 401 JSON을 반환하며 Basic/default form login 또는 광범위한 public/CSRF/CORS 예외가 추가되지 않는다. tracked 설정과 로그에 실제 secret이 없다.

### Session 2: Verified OIDC identity and persistence integration

Status: `completed`

Objective:

검증된 외부 identity를 기존 내부 사용자와 원자 생성 흐름에 연결한다.

Implementation:

- [x] framework OIDC 검증 이후 실행되는 adapter와 내부 application 경계를 연결한다. `(issuer, subject)`만 identity key로 사용하고 기존 `UserWorkspaceCreationService`를 재사용한다.
- [x] 최초 생성·기존 identity 재사용·disabled User 거절 및 내부 ID 중심 principal 구성을 구현한다. persistence 실패 또는 검증 실패에는 인증된 SecurityContext를 저장하지 않는다.

Validation:

- [x] 실제 PostgreSQL에서 최초 identity는 User/identity/workspace 각 하나를 생성하고 반복·경쟁 로그인 연결은 같은 데이터를 재사용한다. 실패에는 부분 레코드가 남지 않는다.
- [x] 같은 이메일이어도 issuer/subject가 다르면 자동 병합되지 않으며 표시 이름 fallback과 기존 이름 보존이 확인된다.
- [x] disabled User는 인증 세션을 얻지 못한다. application transaction 안에 provider 통신이 없고 도메인에 Security 의존성 또는 새 스키마가 추가되지 않는다.

### Session 3: Session lifecycle, CSRF, logout and current user

Status: `completed`

Objective:

브라우저 세션으로 현재 사용자와 개인 workspace를 조회하고 안전하게 로그아웃한다.

Implementation:

- [x] 세션 고정 공격 방어, 표준 Servlet 유휴 만료, 현재 사용자 조회 및 요청 시 disabled 상태 검사·세션 무효화를 연결한다. absolute expiration 전용 custom session infrastructure는 추가하지 않으며 workspace는 서버 owner 조회로 결정한다.
- [x] `GET /api/v1/me`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/logout` 계약을 전용 DTO 및 Security 처리로 구현한다. 인증 부재 logout의 제한된 204 예외와 인증된 logout의 CSRF/origin 검증을 구분한다.
- [x] 쿠키·캐시·오류 응답과 로그 비밀값 보호를 적용하고 로컬 로그인/세션 실행 방법을 문서화한다.

Validation:

- [x] 실제 로그인 완료 후의 세션으로 `/me`를 호출하면 내부 User와 그 owner workspace만 정확한 DTO로 반환한다. identity/token/dataRevision이 노출되지 않는다.
- [x] 로그인 시 세션 ID 교체, 표준 유휴 만료·logout 후 401, 로그인 이후 disabled 처리의 403/세션 무효화 및 cookie 속성을 검증한다.
- [x] CSRF 발급 200/no-store, 누락·잘못된 token의 인증된 logout 거절, 정상 token의 logout 204 및 무세션 logout 204를 검증한다. 악성 Origin은 거절한다.

### Session 4: Security and persistence integration tests

Status: `completed`

Objective:

외부 OIDC provider 없이 실제 Security 경계와 PostgreSQL 저장 결과를 검증한다.

Implementation:

- [x] 기존 PostgreSQL Testcontainers/`@ServiceConnection`과 Spring Security test support를 재사용한다. 검증된 OIDC principal fixture로 adapter/application 연결을 검증하고 HTTP 테스트는 실제 Security filter chain과 세션 저장을 포함한다.
- [x] OIDC login 후처리를 우회하는 mock principal 테스트만으로 인증 전체를 통과한 것으로 간주하지 않는다. framework-supported 테스트 경계 또는 테스트 전용 로컬 protocol fixture로 로그인 거래·callback 성공/실패·재사용을 검증한다. 실제 외부 계정/네트워크는 요구하지 않는다.
- [x] 인증/비인증, 최초/기존/disabled identity, 세션 고정·만료·로그아웃, CSRF/origin, redirect 및 두 사용자 격리 시나리오를 구현한다.

Validation:

- [x] 미인증 `/me` 401, 인증 `/me` 200과 정확한 User/workspace, 별도 세션 A/B의 소유자 격리가 확인된다. query/header의 다른 userId/workspaceId가 권한을 바꾸지 않는다.
- [x] 최초 생성·반복 재사용·동일 identity 경쟁의 최종 DB 상태가 한 User/identity/workspace로 수렴하고 고아 레코드가 없다. disabled identity는 인증을 얻지 못하며 기존 세션도 비활성화 후 거절된다.
- [x] 로그인 state 누락/불일치·callback 재사용·잘못된 검증 결과는 인증을 만들지 않는다. 성공 후 세션이 후속 HTTP 요청에서 복원된다.
- [x] 누락/invalid CSRF는 보호 mutation을 거절하고 valid CSRF는 승인된 logout만 허용한다. 무세션 logout 예외가 인증된 요청 보호를 약화하지 않는다.
- [x] unsafe redirect, 만료, logout의 세션 무효화와 쿠키 제거가 검증되며 전체 테스트가 실제 provider 없이 통과한다. 기존 15개 persistence/Foundation 테스트를 생략하거나 mock DB로 바꾸지 않는다.

### Session 5: OpenAPI and final build validation

Status: `completed`

Objective:

구현된 인증 HTTP 계약을 generated OpenAPI와 실행 결과로 검증하고 전체 빌드를 완료한다.

Implementation:

- [x] 실제 controller DTO와 Security filter가 처리하는 login/callback/logout을 모두 `/v3/api-docs`에 정확히 기술한다. 필요한 filter endpoint에는 명시적 OpenAPI operation을 추가하되 문서용 가짜 controller를 만들지 않는다. cookie 인증, CSRF header, redirect와 오류 응답을 실제 동작에 맞춘다.
- [x] 의미 있는 `@Tag`/`@Operation` 및 필요한 schema/response 설명만 추가한다. Bean Validation을 중복 선언하지 않고 적용 대상이 없는 항목은 근거를 기록한다.
- [x] 인증된 상태에서 generated JSON과 Swagger UI 렌더링을 확인한다. filter callback을 일반 수동 실행 API처럼 오해하게 하지 않고 실제 OIDC 거래가 필요함을 설명한다. Java 21 전체 test/clean build와 최종 diff를 점검한다.

Validation:

- [x] 5개 HTTP 경로가 generated OpenAPI에 존재하고 methods/status/DTO/nullability/필수 필드 및 적용 가능한 입력 제약이 위 계약과 일치한다. login/callback 302와 logout 204를 누락하지 않는다.
- [x] Swagger UI가 인증된 접근으로 endpoints/schema를 정상 렌더링한다. 문서 검증을 위해 production security를 완화하지 않는다.
- [x] `gradlew.bat test --no-daemon`과 `gradlew.bat clean build --no-daemon`가 PostgreSQL Testcontainers 및 security 테스트를 포함해 성공한다.
- [x] V1 및 `ddl-auto=validate`를 유지하며 신규 migration/업무 API/JWT 인증/외부 API 통합/인프라/관련 없는 dependency 변경이 없음을 최종 diff에서 확인한다.

## Execution Results

- Session 1: Google registration uses the standard Spring Security OAuth2/OIDC client with environment-backed client, issuer, callback, origin, and session settings. The test profile supplies static metadata, so startup and redirect validation do not contact Google.
- Session 2: The verified OIDC principal is reduced to `(issuer, subject)`, connected to the existing atomic User/AuthIdentity/PersonalWorkspace service, and stored in a serializable internal User ID principal. Disabled users and persistence/verification failures do not receive a saved application security context.
- Session 3: Servlet `HttpSession` and `HttpSessionSecurityContextRepository` provide browser authentication. Session fixation protection, idle timeout, CSRF, origin checks, logout invalidation, disabled-account checks, and the `/me`/CSRF endpoints are implemented. No custom absolute-expiration session infrastructure was added.
- Session 4: PostgreSQL Testcontainers and Spring Security test support cover unauthenticated/authenticated requests, first and repeated identity handling, same-email isolation, disabled users, callback failure, session restoration, CSRF/origin, logout, redirect safety, workspace isolation, and final persisted state.
- Session 5: Generated OpenAPI contains the controller and filter-owned authentication routes, Swagger UI renders while authenticated, and the full Java 21 test and clean build validations pass (28 tests, 0 failures).
- Local execution: add the real Google client ID/secret and callback URI to the ignored root `.env`, start PostgreSQL with the existing local Compose setup, then run `gradlew.bat bootRun --no-daemon`. Gradle does not load `.env` independently; Spring Boot imports it through `application.yml`. Sessions are in-process and are lost on application restart.

## Final Validation

- [x] Security integration tests가 최초/기존 identity, disabled User, 표준 세션 유휴 만료·logout, CSRF/origin, safe redirect 및 소유권 격리를 통과한다.
- [x] 기존 PostgreSQL Testcontainers persistence 테스트가 실제 V1 및 JPA `ddl-auto=validate`로 유지되고 신규 identity의 원자 생성·반복/경쟁 재사용 최종 DB 상태가 검증된다.
- [x] 인증·비인증 HTTP 동작이 승인 계약과 일치하고 server-side Session 인증을 사용한다. 브라우저 JWT 인증 또는 token 전달/브라우저 저장소 사용이 없다.
- [x] Java 21에서 `gradlew.bat test --no-daemon`과 `gradlew.bat clean build --no-daemon`가 성공한다.
- [x] 관련 없는 업무 API, 테이블, 신규 infrastructure, frontend·배포 변경이 없다. 실제 provider 자동 테스트 의존성이 없고 로컬 수동 로그인에 필요한 provider 설정 및 세션 재시작 한계가 기록된다.
- [x] No failures caused by this Plan remain.
- [x] All Execution Sessions are complete.

### API Validation

Complete these checks only when this Plan adds or changes HTTP API endpoints.

- [x] Implemented endpoints are present in `/v3/api-docs`.
- [x] Request and response schemas match the approved backend contract.
- [x] Bean Validation constraints are reflected where applicable.
- [x] HTTP methods and response status codes match the contract.
- [x] Swagger UI renders the implemented endpoints correctly.

## Completion

The Plan may be changed to `completed` only when:

- every required session item is checked,
- every applicable validation item is checked,
- and no unresolved blocker prevents the Goal from being satisfied.

Validation items that do not apply to the Plan must not be treated as required.

If execution cannot continue, leave incomplete items unchecked and record the blocker before stopping.

실행 결과: 5개 Execution Session과 적용 가능한 Final/API Validation 항목을 완료했다. 완료에 따라 `docs/plans/README.md`에 이 Plan 링크를 추가했다.
