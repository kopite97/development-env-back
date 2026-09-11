# PLAN-0001: Backend Foundation

Status: `completed`

## Goal

기존 Spring Boot 프로젝트에서 Java 21과 Docker Compose PostgreSQL을 사용하는 최소 로컬 백엔드 개발 환경을 마련한다. 애플리케이션은 Gradle로 로컬 실행하고, 자동 테스트는 별도의 PostgreSQL Testcontainers를 사용한다. datasource 연결, Flyway 초기화, JPA `ddl-auto=validate` 적용, Spring Boot 시작 및 Gradle test/build 성공을 확보한다. 도메인과 업무 schema는 구현하지 않는다.

## Scope

- 기능 요구사항의 기준은 `backend-implementation-spec.md`이며, `backend-development-guide.md`는 후속 구현 순서와 연동 고려사항의 참고 자료다. 이번 Plan은 인증·도메인 구현에 앞선 최소 Foundation에 한정한다.
- 현재 루트 단일 Gradle 프로젝트에는 Java 21 toolchain, Spring Boot `4.1.1`, Gradle Wrapper `9.7.1`, dependency-management plugin `1.1.7`, Maven Central 및 JUnit Platform 설정이 있다. 기존 구성을 재생성하지 않고 실제 실행 환경과 의존성 해석을 검증한다. 버전 변경은 확인된 문제 해결에 필요한 경우에만 검토한다.
- Web MVC, Data JPA, Flyway starter, Flyway PostgreSQL 모듈, PostgreSQL JDBC driver 및 Testcontainers와 관련 테스트 의존성은 이미 선언되어 있다. Security, Validation, Actuator, springdoc, Lombok도 기존 구성이다. 중복 의존성 추가나 범위 밖 정리는 하지 않는다. 기술 근거는 현재 Gradle 구성과 관련 `docs/agents/` 지침이며, 이번 사용자 요청의 로컬 PostgreSQL Compose 외에 새로운 framework, dependency, infrastructure, architecture pattern은 도입하지 않는다. Spring Boot의 Compose 연동 dependency도 추가하지 않는다.
- `com.kopite.devspace.DevspaceApplication`과 기존 소스 구조를 유지한다. package-by-feature 지침을 따르되 Foundation을 위해 빈 feature/layer 패키지, 공통 기반 클래스나 추상화를 만들지 않는다.
- 현재 `application.properties`에는 애플리케이션 이름만 있다. 이번 사용자 지시에 따라 기존 properties 형식을 유지한다. 이는 `SPRING_BOOT.md`의 YAML 지침보다 우선하는 이번 작업의 명시적 요구이며, 지침 파일 자체는 변경하지 않는다. `spring.jpa.hibernate.ddl-auto=validate` 등 필요한 설정만 보완하고 Spring Boot/Flyway 기본 활성화, migration 위치, checksum 검증 등의 기본값은 이유 없이 재선언하지 않는다.
- 현재 Compose 구성은 없다. 로컬 개발의 표준 PostgreSQL 환경으로 루트에 PostgreSQL 서비스 하나만 포함하는 최소 `compose.yml`을 추가한다. 검증된 명시 이미지 버전, 로컬 호스트에서 접근할 수 있는 loopback 포트 매핑, 해당 이미지의 데이터 경로에 연결한 named volume을 사용한다. 데이터베이스 이름·사용자·비밀번호와 필요한 호스트 포트는 환경 설정으로 받는다. 별도 설치 PostgreSQL은 요구하지 않는다.
- 로컬 Spring Boot datasource는 Compose PostgreSQL의 호스트 포트와 데이터베이스를 가리키도록 환경변수로 구성한다. 실제 자격 증명은 저장소에 넣지 않고 환경 설정 예시와 로컬 실행 안내를 제공한다. 로컬 환경 파일을 사용하면 Git에서 제외하고, Compose용 환경 파일만으로 Gradle 프로세스에 값이 전달된다고 가정하지 않도록 양쪽 주입 방법을 명시한다. production 자격 증명·설정은 포함하지 않는다.
- 자동 테스트는 기존 `TestcontainersConfiguration`, `@ServiceConnection`, `DevspaceApplicationTests.contextLoads()`를 재사용한다. 현재 `postgres:latest`를 검증된 명시 버전으로 고정하고 가능한 한 Compose와 동일한 PostgreSQL major 버전을 사용한다. 테스트는 Compose의 포트·자격 증명·volume에 의존하지 않는다. 기존 `TestDevspaceApplication`은 유지하며, 표준 로컬 실행은 Compose와 `gradlew.bat bootRun`을 사용한다.
- 현재 production migration과 entity는 없다. 이번 Plan에서는 production migration을 생성하지 않는다. `SELECT 1` production migration, 임시 production 테이블, 가짜 도메인 테이블을 만들지 않는다. 최초 production migration은 첫 데이터 모델 승인 후 작성한다. Flyway 실행 확인에 추가 SQL이 꼭 필요하면 schema를 만들지 않는 테스트 전용 리소스로 한정하고 배포 산출물에서 제외한다.
- JPA 검증은 `validate` 적용, Hibernate의 schema 생성·갱신 방지 및 application context 시작 성공에 한정한다. 검증만을 위한 가짜 entity나 schema는 만들지 않는다. 실제 Entity ↔ Flyway schema 일치 및 불일치 검증은 첫 persistence 구현 Plan에서 수행한다.
- 제외: OAuth2/OIDC, Session 인증, User/AuthIdentity/Workspace, Project/Task/Journal 등 업무 API, 업무 database schema, JWT, 외부 API 연동, 프론트엔드 연동. 기존 Spring Security 보호를 유지하며 인증 구현이나 보안 완화 설정을 추가하지 않는다.
- 제외: application Dockerfile 및 애플리케이션 컨테이너화, production deployment/database 설정, cloud infrastructure, CI/CD deployment, Redis, Kafka.
- 이 Plan의 범위를 벗어난 기능은 구현하지 않는다. 상태는 승인에 따라 `active`이며, 구현과 검증이 끝난 뒤에만 lifecycle을 갱신한다.

## Steps

1. 현재 Java, Gradle, Spring Boot, 의존성 및 Docker 환경을 확인한다. Wrapper의 Java 21 실행과 toolchain, 의존성 해석, Docker daemon과 Compose 사용 가능 여부를 확인한다.
2. 최소 PostgreSQL Compose 개발 환경을 추가한다. 검증된 이미지 버전, 로컬 포트, named volume 및 환경 설정을 구성하고 시작·중지·데이터 보존 방법을 안내한다. 애플리케이션은 컨테이너화하지 않는다.
3. datasource, Flyway 및 JPA validation을 구성한다. `application.properties`를 유지하고 로컬 datasource 환경변수와 `spring.jpa.hibernate.ddl-auto=validate`만 필요한 범위에서 보완한다. Flyway 자동 구성을 활용하며 기본값 재선언, 불필요한 profile, production migration 및 baseline/repair 자동화를 추가하지 않는다.
4. 기존 PostgreSQL Testcontainers와 Spring Boot 테스트 구성을 검증한다. 이미지 버전을 고정하고 `@ServiceConnection`이 Compose와 독립된 테스트 DB 연결을 제공하는지 확인한다. 실제 datasource 연결과 Flyway 초기화를 검증하며 추가 SQL이 필요한 경우에만 schema·entity 없는 테스트 전용 리소스를 사용한다.
5. Compose PostgreSQL과 `gradlew.bat bootRun`으로 로컬 시작 및 volume 데이터 보존을 확인하고, `gradlew.bat test`, `gradlew.bat clean build`를 실행한다. Foundation으로 발생한 실패를 수정하고 실행 환경·명령·결과를 기록한다.

## Validation

- `./gradlew.bat --version` confirms that the Gradle Wrapper and Java toolchain use Java 21.
- `docker compose up -d` starts the pinned PostgreSQL service with the configured port, environment credentials, and named volume. Stopping and starting the Compose service without removing the volume preserves its database state.
- With the Compose connection environment supplied, `./gradlew.bat bootRun` starts Spring Boot and connects to PostgreSQL. The application context starts with Flyway initialized and `spring.jpa.hibernate.ddl-auto=validate` applied.
- The application starts without Hibernate creating or updating schema objects. No production migration, temporary table, fake domain table, fake entity, or fake schema is added for this Plan; entity-to-migration validation is deferred to the first persistence Plan.
- Automated tests start PostgreSQL through the existing Testcontainers and `@ServiceConnection` setup, independently of the Compose database, and verify the Spring Boot context starts successfully.
- `./gradlew.bat test` succeeds without skipping the PostgreSQL-backed test setup.
- `./gradlew.bat clean build` succeeds, and no build or test failures are introduced by the Foundation changes.
- Any unavailable environment check is recorded with its cause and remains incomplete until the required validation can run. The Plan is not marked `completed` before all required checks pass.
