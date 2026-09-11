# PLAN-0002: User and Personal Workspace Persistence

Status: `completed`

## Goal

`User 1 → 1 PersonalWorkspace → N 업무 리소스`의 소유권 기반을 위한 첫 실제 영속성 모델을 구현하고 PostgreSQL에서 검증한다. 외부 identity의 `(issuer, subject)`를 내부 User에 연결하고 User·AuthIdentity·빈 PersonalWorkspace를 원자적으로 생성할 수 있게 한다. 외부 주체 인증과 HTTP 접근 제어는 후속 Plan에서 구현한다.

## Scope

- 기능 기준은 `backend-implementation-spec.md` 1절 사용자·소유권, 2절 공통 저장 계약, 3절 내 작업실이다. `backend-development-guide.md`는 생성 순서·동시성·격리 검증의 참고 자료이며 인증 흐름 전체를 이번 범위로 가져오지 않는다.
- PLAN-0001은 `completed`이다. 현재 구현은 Java 21, Gradle Wrapper 9.7.1, Spring Boot 4.1.1, JPA/Flyway/PostgreSQL 의존성, PostgreSQL 16.4 Compose와 Testcontainers, `@ServiceConnection` 및 context/연결 테스트를 갖춘 상태다. entity·repository·production migration은 없다.
- PLAN-0001 이후 설정은 `application.yml`과 root `.env` 명시적 import로 변경되었다. 현재 `ddl-auto=validate`와 기존 실행·테스트 환경을 재사용하며 properties 복구, 환경 재구성, 의존성·인프라 추가는 하지 않는다.
- 아래는 승인된 최소 모델이다. User와 PersonalWorkspace의 서버 생성 식별자는 Java UUID / PostgreSQL `uuid`, 감사 시각은 `Instant` / `timestamp with time zone`을 사용한다. 생성 시 `createdAt=updatedAt`, `disabledAt=null`이며 감사 시각은 서버가 정한다.

| 모델 / 테이블 | 필드와 기본값 | 주요 DB 제약 |
| --- | --- | --- |
| User / `users` | `id`, `display_name`, `created_at`, `updated_at`, nullable `disabled_at` | `id` PK, 나머지 필수 필드 NOT NULL |
| AuthIdentity / `auth_identities` | `issuer`, `subject`, `user_id` | `(issuer, subject)` 복합 PK로 유일성 보장, `user_id` NOT NULL FK → `users.id` |
| PersonalWorkspace / `workspaces` | `id`, `owner_user_id`, `name`, `revision` 초기 1, `data_revision` 초기 0, `created_at`, `updated_at` | `id` PK, `owner_user_id` NOT NULL UNIQUE FK → `users.id`, 필수 필드 NOT NULL, revision 양수·안전한 정수 범위 및 data_revision 비음수 CHECK |

- AuthIdentity는 명세에 없는 별도 id·감사 필드를 추가하지 않고 복합키를 JPA 식별자로 매핑한다. issuer와 subject는 외부 주체를 식별하는 불투명 값으로 보존하며 임의 소문자화·이메일 대체로 서로 다른 identity를 합치지 않는다. 명세가 최대 길이를 정하지 않은 문자열에는 임의 제품 제한을 만들지 않고 저장 타입과 인덱스 가능 범위를 Session 1에서 확인한다.
- PersonalWorkspace의 기본 이름은 `나의 작업실`이며 이름은 trim 후 1~100 UTF-16 코드 단위로 검증한다. PostgreSQL 문자열 길이와 Java 길이의 차이를 고려해 DB 제약이 유효한 이름을 잘못 거절하지 않게 한다. `revision`과 내부 `dataRevision`은 서로 다른 의미로 매핑하며, 이번에는 생성·저장·재조회에 필요한 동작만 구현한다. 업무 변경에 따른 dataRevision 증가와 작업실 편집 API는 후속 범위다.
- 각 workspace는 정확히 한 User를 참조하고 User는 최대 한 workspace를 소유한다. DB FK·UNIQUE만으로 모든 User의 workspace 존재까지 보장되는 것은 아니므로 신규 생성 경로가 세 레코드를 한 트랜잭션으로 저장해 정확히 하나를 보장한다. 소유자와 identity 연결은 생성 후 임의 재할당하지 않으며 기본 cascade 삭제·계정 병합은 도입하지 않는다.
- 생성 경로는 향후 인증 계층이 검증한 issuer·subject와 표시 이름을 전달할 내부 application use case로 한정한다. 같은 identity의 반복 요청은 기존 User/workspace를 사용하고, 동시 최초 생성에서도 하나만 남게 한다. 고유키 충돌로 실패한 생성 전체를 rollback하며, 재조회는 실패한 트랜잭션 밖에서 처리한다. 인증 검증을 수행하거나 임의 client 식별자를 신뢰하는 공개 API는 만들지 않는다.
- 이메일은 이 최소 모델에 저장하지 않으며 소유권 키·identity 조회·자동 병합에 사용하지 않는다. `userId`, `ownerUserId`, `workspaceId`는 내부 생성·조회 결과이며 client가 권한 대상을 선택하는 입력이 아니다. 이 단계의 영속성 조회를 HTTP 권한 검증 완료로 간주하지 않는다.
- `com.kopite.devspace` 아래 `user`와 `workspace` feature 경계를 사용한다. User/AuthIdentity는 `user.domain`, PersonalWorkspace는 `workspace.domain`에 두며 도메인/JPA 모델을 공유한다. 생성 조정은 application 계층, 필요한 저장·identity 조회·owner 조회의 최소 repository 계약은 내부 계층, Spring Data/JPA 구현은 infrastructure 계층에 둔다. 전역 repository 계층, 별도 persistence 모델, 범용 CRUD 추상화, 빈 presentation 패키지는 만들지 않는다.
- aggregate 간 참조는 필요한 식별자 중심으로 작게 유지하고 DB FK를 명시한다. 객체 연관을 사용할 때는 단방향·LAZY를 우선한다. 불필요한 역방향 컬렉션, class-level setter, `@Data`, `CascadeType.ALL`, mutable collection 노출을 피한다. 생성 factory/constructor가 불변식을 지키며 JPA용 기본 생성자는 protected로 제한한다.
- 최초 migration은 `src/main/resources/db/migration/V1__create_users_auth_identities_workspaces.sql`이다. 세 실제 테이블과 필요한 PK/FK/UNIQUE/NOT NULL/CHECK만 생성한다. Hibernate DDL, `ddl-auto=update`, placeholder, 자동 baseline/repair, 적용된 migration 수정은 금지한다.
- 제외: OAuth2/OIDC 로그인 흐름, Spring Security 인증 설정, Session 저장, `/auth/**`, `/me`, 업무 테이블(Project/Task/Journal/Link/Milestone 등), 프론트엔드, JWT, 외부 provider API, Redis/Kafka 및 배포. 토큰·세션·회원 역할·팀 membership 같은 미래 필드나 테이블을 추가하지 않는다.

### Session 1 approved database design

This design was approved for Session 1. Session 1 records the persistence decisions; implementation begins in Session 2 with the first production migration.

#### `users`

| Column | PostgreSQL type | Nullability / default | Key or constraint |
| --- | --- | --- | --- |
| `id` | `uuid` | NOT NULL; application-generated | Primary key `pk_users` |
| `display_name` | `text` | NOT NULL | Application validates a meaningful display name; no email column |
| `created_at` | `timestamp with time zone` | NOT NULL; server-generated | — |
| `updated_at` | `timestamp with time zone` | NOT NULL; server-maintained | — |
| `disabled_at` | `timestamp with time zone` | NULL | — |

Foreign keys: none. Delete behavior: this table is referenced by both child tables; referenced User rows are protected by `RESTRICT` foreign keys. No user-delete operation is introduced in this Plan. Indexes: the primary-key index only; no disabled/time index is justified yet.

#### `auth_identities`

| Column | PostgreSQL type | Nullability / default | Key or constraint |
| --- | --- | --- | --- |
| `issuer` | `text` | NOT NULL | Part of composite primary key; non-empty value check |
| `subject` | `text` | NOT NULL | Part of composite primary key; non-empty value check |
| `user_id` | `uuid` | NOT NULL | Foreign key to `users.id` |

Primary key: `(issuer, subject)` (`pk_auth_identities`), which also enforces the required identity uniqueness. Foreign key: `fk_auth_identities_user` → `users(id)` `ON DELETE RESTRICT`. Check constraints: `char_length(issuer) > 0` and `char_length(subject) > 0`; issuer and subject remain opaque and are not lowercased or replaced by email. Delete behavior: an identity cannot outlive its User, and deleting a User with identities is rejected. Indexes: the composite primary-key index is sufficient for the `(issuer, subject)` lookup used by the creation boundary; no user_id index is added until a query requiring it exists.

#### `workspaces`

| Column | PostgreSQL type | Nullability / default | Key or constraint |
| --- | --- | --- | --- |
| `id` | `uuid` | NOT NULL; application-generated | Primary key `pk_workspaces` |
| `owner_user_id` | `uuid` | NOT NULL | Foreign key to `users.id`; unique constraint `uq_workspaces_owner` |
| `name` | `text` | NOT NULL | Database stores a non-null value; application enforces trim, blank-name rejection, and the specified 1–100 UTF-16 code-unit limit |
| `revision` | `integer` | NOT NULL DEFAULT `1` | Check `revision >= 1` |
| `data_revision` | `bigint` | NOT NULL DEFAULT `0` | Check `data_revision >= 0` |
| `created_at` | `timestamp with time zone` | NOT NULL; server-generated | — |
| `updated_at` | `timestamp with time zone` | NOT NULL; server-maintained | — |

Primary key: `id`. Foreign key: `fk_workspaces_owner` → `users(id)` `ON DELETE RESTRICT`. The unique owner constraint enforces at most one workspace per User. The database checks only positive `revision` and non-negative `data_revision`; `name` is intentionally constrained to NOT NULL because PostgreSQL checks for blankness or character length could conflict with the application's trim and UTF-16 code-unit rules. The application rejects blank names and enforces the trimmed 1–100 UTF-16 code-unit range. Delete behavior: a workspace cannot be detached from or cascade-delete its User; no workspace delete operation is introduced. Indexes: the primary-key index and the owner unique index; no additional index is justified before workspace-scoped business queries exist.

#### Relationships and invariant ownership

- One User may have many AuthIdentity rows, each identified by its opaque `(issuer, subject)` pair. An AuthIdentity has exactly one User through its non-null foreign key.
- A User may have at most one PersonalWorkspace at the database level through `UNIQUE(owner_user_id)`. The application creation use case must ensure that every newly authenticated User receives exactly one workspace in the same transaction as the User and AuthIdentity; a foreign key/unique constraint alone cannot require existence.
- PersonalWorkspace is the future aggregate root for workspace-scoped business resources. This Plan creates no business-resource tables or workspace foreign keys beyond the three tables above.
- The database enforces primary keys, non-null required values, identity-pair uniqueness, one-owner uniqueness, foreign-key integrity, revision checks, and restrictive deletes. The application/domain layer enforces server-side UUID and timestamp generation, display/name normalization and length, blank-name rejection, `createdAt = updatedAt` on creation, default workspace name `나의 작업실`, the atomic three-record creation flow, duplicate-callback reuse, concurrency retry after a unique-key race, dataRevision increments for future business changes, and the rule that client-supplied IDs never select authorization scope. This Plan persists nullable `disabledAt` only; authentication and access decisions for disabled accounts belong to the later authentication/security Plan. Email is neither stored nor used to merge Users.
- JPA will map the same domain entities with unidirectional child-to-User references and no User-side mutable collections. AuthIdentity uses a composite embedded identifier. `revision` is an explicit domain field rather than an implicit Hibernate-generated schema version; transaction ownership remains in the application layer.

## Execution Sessions

Execute sessions in order.

Do not start the next session until the current session's implementation and validation are complete.

Check an item only after the corresponding work has actually been completed.
Update this Plan as execution progresses.

### Session 1: Persistence model and schema design

Status: `completed`

Objective:

명세의 필드·소유권·생성 규칙을 세 테이블과 JPA 모델에 일관되게 대응시킨다.

Implementation:

- [x] 위 모델의 컬럼 타입·nullability·PK/FK·unique/check 제약과 삭제 시 참조 보호를 구체화한다. 명세에 없는 필드를 추가하지 않고 제약 및 인덱스의 근거를 확인한다.
- [x] aggregate와 repository 경계, 복합 identity key 매핑, 서버 UUID·감사 시각 생성 및 revision 초기화 방식을 정리한다. JPA의 신규 entity 판별·version 초기화가 명세의 최초 revision 1과 충돌하지 않게 한다.
- [x] 신규 생성의 단일 트랜잭션, 기존 identity 재사용, 동시 생성 충돌의 rollback/재조회 결과를 정한다. 인증 계층과의 경계를 내부 입력 계약으로 명시한다.

Validation:

- [x] 필드별 명세 대조로 불필요한 email·token·session·업무 필드가 없음을 확인한다.
- [x] DB의 최대 한 workspace 제약과 application의 신규 사용자당 정확히 한 workspace 생성 책임을 구분한다.
- [x] 문자열 저장 및 복합키 인덱스 제약, UTC 시각과 revision 초기값이 JPA와 PostgreSQL 양쪽에서 일치하도록 설계를 확인한다. workspace 이름의 blank/trim/UTF-16 규칙은 application validation으로 두고, DB에는 충돌하지 않는 NOT NULL만 둔다.

### Session 2: First production Flyway migration

Status: `completed`

Objective:

Hibernate의 schema 생성 없이 세 테이블과 무결성 제약을 Flyway V1으로 생성한다.

Implementation:

- [x] 확정한 모델의 의미 있는 V1 SQL을 추가한다. 참조되는 `users`를 먼저 생성하고 identity와 workspace FK를 연결한다.
- [x] 기존 PostgreSQL Testcontainers에서 V1 적용·재실행과 DB 제약을 확인할 최소 migration 통합 검증을 마련한다. production SQL을 그대로 사용한다.

Validation:

- [x] 깨끗한 PostgreSQL에서 V1이 성공하고 Flyway history에 성공 이력이 기록된다.
- [x] 같은 DB로 다시 migrate하면 V1 적용 건수가 늘지 않고 checksum 검증과 기존 데이터 보존이 성공한다.
- [x] 세 테이블의 PK/FK/UNIQUE/NOT NULL/CHECK가 실제 존재하며 별도 SQL 초기화나 Hibernate DDL에 의존하지 않는다.

### Session 3: JPA mappings, repositories and atomic creation

Status: `completed`

Objective:

Flyway schema와 일치하는 도메인/JPA 모델 및 내부 생성·조회 경계를 구현한다.

Implementation:

- [x] User, AuthIdentity와 복합키, PersonalWorkspace를 확정된 타입·제약에 맞춰 매핑한다. 유효한 생성 factory, 초기값, 감사 시각과 최소 불변식을 구현한다.
- [x] application에서 필요한 저장·identity 조회·owner 조회만 제공하는 repository 계약과 infrastructure 구현을 추가한다. JPA 세부사항을 application에 노출하지 않는다.
- [x] 내부 생성 use case가 세 모델을 원자적으로 저장하고 기존 identity를 재사용하도록 한다. 중복 최초 생성 시 부분 User/workspace가 남지 않도록 처리하며 임의 소유자 선택·변경 경로는 제공하지 않는다.

Validation:

- [x] 기존 Spring Boot Testcontainers context가 실제 V1 적용 후 `ddl-auto=validate`로 시작한다.
- [x] 서버 생성 id, identity 복합키와 소유자 참조를 저장 후 flush/clear·재조회하여 매핑을 확인한다.
- [x] domain의 HTTP/infrastructure 의존성, 불필요한 양방향 관계·setter·컬렉션 노출이 없고 application이 트랜잭션을 소유함을 확인한다.

### Session 4: Persistence rules and integration tests

Status: `completed`

Objective:

실제 PostgreSQL에서 identity 유일성, 소유권, 생성 원자성과 schema 일치를 검증한다.

Implementation:

- [x] 기존 `TestcontainersConfiguration`과 `@ServiceConnection`을 재사용해 repository·생성 use case 통합 테스트를 확장한다. Compose DB나 H2로 대체하지 않는다.
- [x] 독립된 두 사용자, 중복 identity/owner, 존재하지 않는 User 참조, 생성 중 실패 및 동시 동일 identity 생성 시나리오를 작성한다. DB 제약 테스트는 flush/commit을 실제 수행하고 실패 트랜잭션을 분리한다.

Validation:

- [x] `(issuer, subject)` 중복이 DB에서 거절되고 issuer 또는 subject가 다른 identity는 구분된다. 이메일·표시 이름에 의한 사용자 병합 경로가 없다.
- [x] 한 소유자의 두 번째 workspace와 잘못된 identity/workspace FK가 DB에서 거절된다. 다른 사용자 소유의 workspace 조회가 섞이지 않는다.
- [x] 최초 생성 결과는 User·identity·workspace 각 하나이며 이름 `나의 작업실`, revision 1, dataRevision 0, 서버 감사 시각 및 nullable disabledAt이 재조회 후 보존된다.
- [x] 반복 호출은 기존 결과를 사용하고 동시 최초 호출도 최종적으로 동일한 User/workspace를 사용한다. 실패나 경합으로 고아 사용자·부분 생성 데이터가 남지 않는다.
- [x] 필수값·이름 범위·revision 관련 제약과 실제 필드 매핑을 확인한다. 실제 V1 schema가 `ddl-auto=validate`로 시작하고 도메인의 이름 규칙을 검증했으며, 가짜 entity·production migration을 추가하거나 적용 이력을 수정하지 않았다.

Validation result: `gradlew.bat test --no-daemon` passed: 15 tests, 0 failures, 0 errors.

### Session 5: Final schema and build validation

Status: `completed`

Objective:

기존 Foundation 위에서 실제 persistence schema가 재현 가능하게 시작하고 전체 빌드가 통과함을 확인한다.

Implementation:

- [x] migration·entity·repository·생성 경계의 최종 diff와 검증 결과를 점검하고 이번 변경으로 발생한 오류를 수정한다.
- [x] Java 21 환경에서 전체 test 및 clean build를 수행하고 V1 적용·재실행·JPA validation 결과를 이 Plan에 기록한다.

Validation:

- [x] `gradlew.bat test`가 실제 PostgreSQL Testcontainers 테스트를 생략하지 않고 성공한다.
- [x] `gradlew.bat clean build`가 성공하며 Foundation의 기존 context/연결 테스트도 통과한다.
- [x] Hibernate가 schema를 생성·수정하지 않고, V1으로 생성된 실제 세 모델의 매핑이 `ddl-auto=validate`로 검증된다.
- [x] 인증 흐름·endpoint·업무 테이블·신규 의존성·배포 변경이 포함되지 않았음을 확인한다.

Validation result: `gradlew.bat test --no-daemon`와 `gradlew.bat clean build --no-daemon`가 모두 성공했다. 전체 테스트는 15개 통과, 실패·오류·skip 0개이며, production migration은 V1 하나로 유지되었고 Testcontainers의 깨끗한 DB 적용·재실행 및 `ddl-auto=validate` context 검증을 통과했다.

## Final Validation

- [x] PostgreSQL Testcontainers의 깨끗한 DB에 실제 V1 적용이 성공하고 같은 DB 재실행에서 V1이 재적용되지 않는다.
- [x] JPA 매핑이 Flyway schema와 일치하고 `ddl-auto=validate`로 시작한다. Hibernate schema 생성·갱신은 없다.
- [x] identity 유일성, 소유자당 하나의 workspace, FK 무결성 및 원자적 생성/경합 검증이 통과한다.
- [x] `gradlew.bat test`와 `gradlew.bat clean build`가 성공하고 관련 없는 도메인 테이블·인증 흐름이 추가되지 않는다.
- [x] No failures caused by this Plan remain.
- [x] All Execution Sessions are complete.

## Completion

The Plan may be changed to `completed` only when:

- every required session item is checked,
- every required validation item is checked,
- and no unresolved blocker prevents the Goal from being satisfied.

If execution cannot continue, leave incomplete items unchecked and record the blocker before stopping.

This Plan is completed. Sessions 1-5 and Final Validation are complete; PLAN-0002 is indexed in docs/plans/README.md.
