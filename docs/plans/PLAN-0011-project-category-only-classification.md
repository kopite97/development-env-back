# PLAN-0011: ProjectCategory-only classification

Status: `completed`

Approved by the user on 2026-09-14, including Decision A1-A6. Implementation and isolated validation are authorized; production rollout remains separate.

## Goal

[DECISION-0002](../decisions/DECISION-0002-project-category-only-classification.md)의 승인된 최종 선택에 따라 scope 병행 모델을 ProjectCategory 단일 분류로 전환한다. Decision A1–A6와 본 Plan이 승인되어 구현과 격리 환경 검증을 수행한다. 실제 운영 데이터 변경과 배포는 별도 승인 대상이다.

## Scope

- [현재 구현 영향 조사와 프런트 인계표](../reviews/2026-09-14-project-category-only-impact.md), 실제 V14 DB와 생성 OpenAPI를 기준으로 한다. PLAN-0010의 구현/검증 결과는 보존하되 새 변경의 검증으로 대체하지 않는다.
- Project/Task/Journal/Milestone/Link/Overview/Dashboard v2 정상 API, Category 필터·집계·파생 응답·cache header, Link Project 관계(승인 시), Dashboard schema2, 역사적 replay compatibility, 새 Flyway 및 전환 runbook을 포함한다.
- auth/me/Category CRUD의 현재 v1 계약과 소유권/CSRF/Origin/no-store, Category 이름/UUID/revision/quota, Project·하위 리소스 기존 비분류 동작을 유지한다.
- V1–V14 수정·repair·checksum 변경, replay 재작성/강제 만료, scope/name 자동 매핑, Category 복제 저장, starter 데이터, Project 삭제, 실제 Deploy 리소스, 새 외부 infrastructure/dependency는 제외한다.
- 프런트 코드를 구현하지 않는다. v2 endpoint/DTO/query/error/header/revision 및 deployment handoff 문서·브라우저 문서 검증만 포함한다. 운영 배포와 운영별 manifest 실행은 별도 승인 대상이다.
- 새 번호는 현재 기준 V15/V16/V17을 예약하되 승인 후 재확인한다. 단계별 release artifact에 migration을 구분하며, 향후 contract migration이 expand 배포에서 먼저 실행되면 안 된다.

## Execution Policy: Recover Within the Approved Contract

승인 이후 `active`로 변경하고 아래 세션을 순서대로 실행한다. 각 세션의 구현/검증이 완료되기 전 다음 세션으로 넘어가지 않는다. 실제 통과한 항목만 체크하고 검증 증거를 기록한다.

- 구현/컴파일/strict JSON/SQL/migration fixture/replay reader/lock ordering/OpenAPI/Testcontainers/Chrome readiness 실패는 승인 범위에서 진단·수정·재검증하고 계속한다. 일반적인 실행 문제 때문에 매번 재승인을 요청하지 않는다.
- 실패 산출물과 원인을 기록한다. assertion 완화, required test skip, old migration 변경, snapshot/hash 변조, scope 재추론, accepted Decision 변경으로 테스트를 맞추지 않는다.
- 동시성 실패는 lucky rerun으로 종료하지 않는다. 독립 connection/transaction, lock acquisition·PostgreSQL 대기 관찰, 양쪽 serial 결과와 정확한 counters로 재현/해결한다. 실패 transaction은 rollback 후 다음 transaction을 사용한다.
- populated migration 테스트는 JDBC 원래 schema를 finally로 복구하고 pooling 오염을 검증한다. PLAN-0010에서 발견된 schema leak을 반복하지 않는다. 로컬 APP_ORIGIN 설정과 테스트 fixture Origin을 구분하며 production 보안을 완화하지 않는다.
- Genuine decision blocker: A1–A6 변경 필요, legacy 성공 replay를 보존할 수 없는 실제 데이터, 소유권/무결성 손상, 승인 없는 Link/widget 매핑 필요, 장기 이중 writer/무중단 요구, 보존·rollback 정책 충돌, 새 infrastructure 필요. 재현/원인/시도/필요한 선택을 기록하고 해당 실행을 중단한다.
- 모호한 실제 데이터는 조용히 all/null로 처리하지 않는다. 승인 manifest 없는 행은 배포 preflight를 실패시킨다. 구현 및 독립 fixture 검증은 구체적인 운영 매핑이 없어도 진행할 수 있지만 운영 전환 완료를 주장하지 않는다.

## Execution Sessions

### Session 1: 승인 계약 고정 및 전환 전 기준선

Objective: A1–A6와 과거 계약을 재현 가능한 fixture/evidence로 고정한다.

Implementation:

- [x] 사용자 승인 내용을 Decision에 반영하고 accepted/superseded index를 규칙대로 정리한다. 별도 승인 없이 공통 guides를 수정하지 않는다. Plan을 active로 전환한다.
- [x] 현재 source/DB/generated OpenAPI를 재확인하고 V1–V14 SHA/Flyway checksums, Project·Category·Link·Dashboard 및 모든 replay fixture를 보존한다. 실제 운영 secret/key/body를 테스트 repo에 복사하지 않는다.
- [x] v2 정상 API와 v1 replay-only boundary, canonical storage path, fingerprint versions, frontend pending-request 보존 규칙을 계약 fixture로 정의한다.
- [x] 배포 manifest 형식과 preflight를 설계한다: workspace/resource UUID, 기존 revision/JSON digest, 승인 target UUID/null/selector, scope-only unresolved 항목, overflow 검증. 데이터별 map을 이름으로 생성하지 않는다.

Validation:

- [x] 기존 전체 테스트로 baseline을 확인하고 실제 실패는 원인을 분리한다. 기존 uncommitted 사용자 변경을 보존한다.
- [x] golden Project hash v1/v2, Link/Task/Journal/Milestone v1 및 13/14필드 Project/하위 scope snapshot을 구현과 별개의 literal fixture로 보존한다.
- [x] 같은 이름·서로 다른 UUID·foreign workspace·cap100·null Category·active/archived·Task trash·모호한 Link/widget의 대표 dataset을 확보한다.

Session 1 evidence (2026-09-14): existing 204 tests passed (0 failures/skips); six independent literal creation fingerprint/snapshot fixtures match the current implementation. Full source, generated OpenAPI, migration observation and JUnit XML/logs retained under `.gradle/project-category-only-validation/baseline/`. Existing populated/ownership/archive/quota/race fixtures retained with the source baseline; the approved manifest preconditions are documented in `docs/project-category-only-rollout.md`.

### Session 2: Additive schema와 populated 전환 rehearsal

Objective: old data를 보존하고 staged migration의 실패 경계를 검증한다.

Implementation:

- [x] expand migration(V15 후보): nullable links.project_id, workspace 소유권 composite FK ON DELETE RESTRICT 및 참조 index; Dashboard schema1/2 전환 제약 준비. existing rows/counters/replay 무변경.
- [x] cutover migration/tooling(V16 후보)을 격리 환경에서 개발한다: 승인 manifest의 정확한 preconditions, Dashboard JSON 변환, 필요할 때만 Link UUID mapping, scope NOT NULL/default 제거, 정확한 counter 증가.
- [x] Project 기존 categoryId·Category name/UUID와 기존 비분류값을 보존한다. 미분류는 그대로 유지하고 생성/할당을 하지 않는다.
- [x] 단계별 release 구성 및 snapshot/export/restore runbook을 작성한다. 아직 destructive contract migration을 live 환경에 실행하지 않는다.

Validation:

- [x] fresh 및 populated V14→expand→cutover, V12→V14→새 단계 migration을 검증한다. 원래 column 값, FK, Category UUID/name, old Flyway checksum, 모든 replay raw bytes/hash/status/expiry를 비교한다.
- [x] archived Project 관계·foreign Link Project FK·Project 삭제 RESTRICT를 SQL 레벨에서 확인한다. Project/Category 없는 신규 workspace에 starter row가 생기지 않는다.
- [x] 미승인/변경된 widget/Link manifest, dangling reference, maximum revision/dataRevision/collectionRevision에서 fail-before-write 또는 transaction rollback을 검증한다. partially migrated workspace가 없어야 한다.
- [x] Dashboard 변환 +1, 실제 Link relation 변경 +1/collection +1, workspace당 cutover +1 및 나머지 resource revision 불변을 정확히 비교한다.

Session 2 evidence: four isolated PostgreSQL migration tests passed. Fresh V1?V16, V12/V14 starting histories, populated Category/Project/child rows, expired and unexpired raw replay records, reviewed widget/Link mapping, foreign FK, missing/stale manifests and revision/counter overflow rollback verified. Existing V12 populated-to-V14 fixtures also passed in the Session 1 baseline. V16 is outside default Flyway resources until the bridge release packaging is enabled; V1?V14 unchanged. Evidence: `migration/populated-upgrade.json`, `migration-test.log`; compile-fixture typo recovery retained.

### Session 3: 정상 v2 도메인·DTO 및 역사적 생성 replay

Objective: scope 없는 정상 쓰기/응답과 기존 생성 의도 보존을 함께 구현한다.

Implementation:

- [x] Project domain/values/commands/parser/response에서 scope와 colorToken을 제거하고 categoryId 생략/null/raw UUID를 유지한다. 정상 response는 required nullable categoryId를 포함한 12필드다.
- [x] Project hash v3, Link v2, Task/Journal/Milestone v2 및 별도 historical v1/v2 validators/readers를 구현한다. Category hash/CRUD는 보존한다.
- [x] 기존 canonical workspace/resource/path/key namespace에서 v1/v2 collision을 직렬화한다. replay format을 식별하고 old row를 새 DTO로 자동 변환하지 않는다.
- [x] v1 POST replay-only를 구현한다. new/expired=410, unexpired mismatch=409, valid=원래201. 나머지 retire 대상 v1 경로의 410과 동일 security boundary를 구현한다.
- [x] Task/Journal/Milestone current Project metadata mapper를 categoryId로 전환하고 원래 archive/delete/restore/replay 존재 검증 차이를 유지한다.

Validation:

- [x] Project POST/PATCH 생략/null/UUID/case/동일값/archived/stale/foreign를 HTTP와 새 transaction에서 검증한다. scope/colorToken 입력은 unknown field로 거절한다.
- [x] migration을 거친 unexpired old Project 13/14필드 및 하위 scope 응답을 HTTP byte/shape 기준으로 검증한다. replay 후 현재 GET은 새 계약이고 과거 응답은 enrich하지 않는다.
- [x] old/new 동일 key 요청의 각 방향·동시 시도, body mismatch, omitted/null 차이, raw UUID case, expired reuse, 삭제/rename/재분류 뒤 replay, timestamp/counter/TTL 불변을 검증한다.
- [x] Task/Journal/Milestone의 category/name이 request fingerprint에 들어가지 않음을 검증한다. Category 삭제·Task soft delete·Journal/Milestone hard delete의 기존 replay 차이도 유지한다.

Session 3 evidence (2026-09-15): 14 targeted tests passed, including all six literal historical snapshots/fingerprints, new v3 presence/case, real migrated legacy HTTP response, cross-version conflicts and three repetitions of both actual PostgreSQL lock-wait schedules. Scope-free Project/derived responses, basic archived clear and Category-deleted replay pass. A broad retired route initially captured POST; disjoint HTTP mappings and an exact handler assertion fixed the root cause. Failure XML retained. Link nullable domain/request/snapshot support was implemented here to support its v2 hash; collection/filter regression remains Session 5.

### Session 4: 서버 Category 필터·파생 metadata·집계·freshness

Objective: 클라이언트 전체 수집 없이 모든 대상 조회와 집계를 Category로 전환한다.

Implementation:

- [x] `category=all|uncategorized|UUID` strict selector 및 owned Category 검증을 각 list/stats/Overview에 통합한다. projectId와 AND, 나머지 status/search/date/sort/deleted/limit 기본값은 유지한다.
- [x] Project/Task/Journal/Milestone v2 signed cursor를 구현한다. 기존 order/keyset을 유지하며 Category selector와 기존 filter binding을 서명한다.
- [x] Overview fixed byScope DTO/grouping을 UUID/null bucket으로 교체하고 Project category-counts endpoint를 구현한다. 0건 Category 및 미분류 bucket, active/archived 합계를 정의대로 반환한다.
- [x] 관련 read와 신규 mutation의 X-Workspace-Data-Revision을 body와 동일 transaction에서 확보한다. Project detail도 snapshot 일관성을 확보한다. 정상 Category v1 read/new mutation에도 header를 추가하고 replay에는 생략한다.
- [x] 기존 index 기반으로 SQL을 검토한 뒤 증거가 있는 Category keyset index만 추가한다. 하위 row category 복제나 eager loading은 하지 않는다.

Validation:

- [x] all/UUID/null/잘못된 값/unknown/duplicate/cross-tenant/삭제 Category, projectId 교집합 불일치 200/0, ownership404를 검증한다.
- [x] 검색 escaping, Journal 날짜 경계·동률·oldest/newest, Milestone NULL 날짜·open/done, Project active/archived, Task 휴지통 및 stats 제한을 회귀 검증한다.
- [x] v1 cursor rejection, v2 변조/foreign/filter/limit/sort mismatch, UUID case normalization, page count/order를 검증한다. mutation 중 live cursor의 한계와 frontend restart를 문서화한다.
- [x] Category 이동/rename 중 RR count/page/buckets/header가 같은 snapshot임을 검증한다. 여러 페이지의 고정 snapshot을 허위 보장하지 않는다.
- [x] uncategorized 행 포함, 1/10/대표 대량 Project의 bounded query count, SQL EXPLAIN과 selected indexes, Category list over-cap no truncation, duplicate-name Project를 검증한다.

Session 4 evidence (2026-09-15): 17 selected tests passed without skips, followed by targeted passing checks for Overview's counter across concurrent commits and Journal date/time ties in both cursor directions. Four resource count/page reads retain old Category membership and the matching workspace counter during committed reassignment/rename. Strict selectors, tenant checks, intersections, archive/trash, literal escaping, zero buckets, decimal-string Long.MAX_VALUE headers, v1/tampered/foreign/mismatched cursors, Category cap/over-cap collections, and replay omission of freshness metadata verified. Existing workspace/category and resource ordering indexes remain; EXPLAIN used ix_projects_workspace_category for aggregation, and 1/10-row plus 121-row tests showed bounded query counts. No speculative index added. Logs/JUnit and SQL plans: `.gradle/project-category-only-validation/session4/` and session4-*.log. Recovery: observation-copy argument shadowing was caught by child revision assertions and fixed; MockMvc query encoding and snapshot-versus-observation assertions were corrected without dropping body/revision/replay assertions.

### Session 5: Link Project 관계와 collection 의미

Objective: 승인한 Link 관계를 구현하고 기존 순서/생성 의도·공통 필터 종료를 검증한다.

Implementation:

- [x] Link domain/commands/DTO에 nullable projectId presence를 구현한다. Project join으로 projectName/categoryId만 유도한다. direct Category 입력/저장은 금지한다.
- [x] list에 category/projectId/projectStatus/query를 적용하고 LEFT JOIN으로 미연결을 보존한다. scope=all wildcard 의미를 새 분류로 옮기지 않는다.
- [x] workspace→Project(필요한 UUID 정렬)→Link collection→Link 순서를 고정한다. 기존 full reorder/position uniqueness/quota/body-size 보호를 유지한다.

Validation:

- [x] Project 없는 Link 및 미분류 Project Link, 특정 Category/Project AND, projectStatus와 unlinked 제외, foreign ownership를 검증한다.
- [x] active/archived 배정·해제·Project 재분류 후 live Link 응답, Link revision/collectionRevision 불변과 workspace header 갱신을 검증한다.
- [x] Link 직접 mutation/reorder counters, partial reorder rejection, 동시 reorder/create/reassign, quota500 및 응답 8 MiB, historical Link replay의 원래 collectionRevision을 검증한다.
- [x] populated scoped Links에 승인 정책/manifest만 적용되고 names/scope/URL 추론이 없음을 확인한다.

Session 5 evidence (2026-09-15): 25 Link/API/persistence/replay tests passed; six further targeted executions covered derived read snapshots, constant query count with distinct Project references, and three repetitions of six controlled concurrency orders. All 18 competing operations were observed waiting on PostgreSQL workspace locks; exact relation, order, resource/collection/workspace revisions were verified for reorder/create/reassign. The 500-item response includes maximum-length derived Project names and stays below 8 MiB. Historical Link wrapper/collectionRevision remains unchanged. V9-V14 historical upgrade test remains pinned to its historical contract; V16 manifest validation is separately covered in Session 2. Evidence retained in `.gradle/project-category-only-validation/session5/`, session5-*.log and concurrency/link-relations-*.txt.

### Session 6: Dashboard schema2 및 삭제 참조

Objective: 저장 selector의 뜻과 migration을 보존하며 임시 필터와 분리한다.

Implementation:

- [x] Dashboard selection strict oneOf, schemaVersion2, widget 종류별 조합 및 기존 title/id/size/limit 제약을 구현한다. projectId/scope 정규화는 제거한다.
- [x] owned Project/Category batch 검증, missingCategory 응답 상태와 unchanged missing selection full-save 예외를 구현한다. Category 삭제는 Project in-use만으로 제한한다.
- [x] 가상 기본값과 저장 migration, Dashboard +1/stale draft semantics를 구현한다. 승인한 deploy all-only placeholder 외에 Deploy 기능은 추가하지 않는다.
- [x] Home 임시 override의 프런트 handoff를 작성한다. 별도 저장 endpoint나 자동 PUT을 만들지 않는다.

Validation:

- [x] schema1 unsupported, strict fields/null/duplicate/oneOf/foreign, project/category/uncategorized/all/타입별 조합, empty widgets, first revision0 save 및 same-value +1을 검증한다.
- [x] Category rename/Project 이동은 Dashboard row revision을 바꾸지 않음, Category delete는 missingCategory로 보임, unchanged missing 참조 보존과 새 missing 참조404를 검증한다.
- [x] Category delete vs Dashboard save 두 순서, Category assign vs delete 두 순서, 동시 Dashboard PUT conflict를 독립 transaction과 실제 lock wait로 반복 검증한다.
- [x] schema1 fixture 전체 보존/mapping unresolved 차단/overflow rollback을 검사한다. stored JSON에 scope가 없고, 조회가 저장 데이터를 수정하지 않는다.

Session 6 evidence (2026-09-15): 37 API/domain/persistence/migration/concurrency executions passed, plus two Dashboard snapshot/batch-read tests. Missing Category UUID/state survives deletion and same-name recreation without Dashboard writes; preserving existing missing selections succeeds and new missing/foreign selections fail. Schema2 strict shape/type/null/duplicate matrices, defaults, limits, empty/repeated saves, rollback, overflow, archived Projects, and selectionState input rejection verified. Eighteen controlled Dashboard/delete/save and eighteen Project assignment/delete lock waits passed across both serial orders. RR Category deletion retains matching old selection state/counter; 2 versus 80 widget references use a constant query count. Session evidence retained in `.gradle/project-category-only-validation/session6/`, session6-*.log and concurrency directories. Recovery: schema customizer generic typing and a remaining retired-scope ownership fixture corrected; no security or concurrency assertion was relaxed.

### Session 7: Contract migration·retirement·통합 회귀

Objective: 최종 runtime의 scope 의존성을 제거하고 rollback 범위를 증명한다.

Implementation:

- [x] contract migration(V17 후보)으로 projects.scope/links.scope와 관련 제약을 제거하고 Dashboard schema_version=2 제약을 확정한다. old Flyway는 보존한다. 최종 entity validation에 잔존 필드가 없어야 한다.
- [x] bridge와 final artifact의 v1 replay-only 만료/410 정책을 구분하고 미만료 row를 유지하는 rollout preflight를 구현한다. T 및 max expires_at/clock margin을 명시하며 old writers가 다시 생성하지 못하게 한다.
- [x] 배포/프런트 교체/retry queue/rollback runbook을 완성한다. source/historical JSON/SQL과 정상 domain scope 사용을 구분하는 최종 audit를 추가한다.

Validation:

- [x] full populated expand→cutover→drain→contract를 격리 PostgreSQL에서 재현한다. V12/V14 Category assignment, Dashboard·Link ambiguous fixtures, retained expired/unexpired replay를 모두 포함한다.
- [x] v1 replay-only는 신규 생성을 하지 않고 v2 namespace를 우회하지 않음, drain 이전 종료 차단, 최종 v2 shape에 scope/colorToken/byScope/unity/server enum이 없음을 검증한다.
- [x] old column 제거 후 schema validation, Category FK/in-use/ownership, task trash/archive, all existing nonclassification behaviors를 검증한다.
- [x] 두 실행에서 concurrency 결과가 반복 가능함을 확인하고 모든 실패의 root cause/evidence를 기록한다. Scope 관련 OAuth permission 설정이나 역사적 migration을 잘못 제거하지 않는다.

Session 7 evidence (2026-09-15): 30 final-runtime/migration/controlled-concurrency executions passed. V17 Hibernate validation and normal v2 CRUD/reads succeeded on a separate empty database. Populated V12-V14-V16-V17 retains Category IDs, child rows, all historical replay bytes and V1?V14 checksums; early retirement and unexpired legacy rows block V17. Durable cutover deadline survives replay cleanup; future v2 expiry does not block contract. Both jar stages inspected, including final?bridge without clean (V17 removed, replay flag true). Business regression recovery: 129 non-OpenAPI executions passed; seven old OpenAPI assertions are carried into Session 8. Previous 235-test diagnostic and 136-test recovery XML/log retained. Deterministic snapshot comparison failures came from nonserialized observation metadata, not resource/replay changes or lock ordering; full payload comparisons still include all persisted fields/revisions/timestamps. Exact deployment/rollback commands are in the rollout runbook. Evidence: `.gradle/project-category-only-validation/session7/`, migration/, concurrency/, recovery/ and session7-*.log. No production data was changed.

### Session 8: OpenAPI·브라우저·최종 증거와 인계

Objective: 실제 계약과 validation evidence를 남기고 실행 완료를 판단한다.

Implementation:

- [x] 정상 v2 및 유지 v1 Category/auth OpenAPI와 bridge legacy-replay 문서를 구분한다. 필터/nullable/strict request/error/header/schemaVersion/missingCategory와 response examples를 실제 구현에 맞춘다.
- [x] 프런트 인계표를 최종 endpoint/DTO/query/error/revision/cache/retirement 계약으로 갱신한다. 기존 배포 runbook에는 승인된 새 방향을 알리는 링크를 추가하고 역사적 Decision/Plan 본문을 사실과 다르게 재작성하지 않는다.
- [x] 기존 Chrome harness를 활용해 실제 method/path/schema/selection/nullable/header를 visible semantic content로 검증한다. 새 dependency·production security 완화 없이 수행한다.

Validation:

- [x] generated OpenAPI inventory/schema/error/CSRF/no-store/default 및 production Swagger 보안 테스트가 통과한다. 최종 normal API 분류 enum/scope 부재를 확인한다.
- [x] Java21 `./gradlew.bat test --no-daemon --rerun-tasks` 및 `./gradlew.bat clean build --no-daemon`를 PostgreSQL Testcontainers로 실행하고 required skip 없이 통과한다. 이전 pass 수를 재사용하지 않는다.
- [x] Chrome JSON/HTML/screenshots, generated OpenAPI, migration before/after checksums/counters, golden replay/raw HTTP, concurrency lock-wait/rollback, SQL counts/EXPLAIN, 전체 JUnit XML 및 build logs를 `.gradle/project-category-only-validation/`에 보존한다. screenshot을 직접 확인하고 검증용 프로세스만 종료한다.
- [x] 최종 diff가 승인 scope 내인지, V1–V14/기존 replay/Category UUID가 보존됐는지 확인한다. 운영 데이터 변경이나 프런트 구현을 수행했다고 주장하지 않는다.

Session 8 evidence (2026-09-15): generated OpenAPI inventory validates 41 operations and strict schemas/errors/CSRF/nullable/selector/header behavior. Java 21 full rerun: 236 tests, zero failures/errors/skips (2m21s). Independent clean build: 236 tests, zero failures/errors/skips (2m22s). Real Chrome on isolated Testcontainers server port 18080 passed 11 visible operation and 13 schema controls, including nullable UUID, selection, historical retry instructions and decimal observation header/replay omission. Screenshots inspected and tracked validation processes stopped. V1-V14 byte checks passed; original 6 replay fixtures retain SHA-256 166156fb28d4c2ee78ed8752d6960bf1a4ca8d1edfe20ee4d72a5b264c5fd469. Normal scope field/enum audit and git diff whitespace check passed. OpenAPI/migration/replay/concurrency summaries and the 317-artifact manifest are retained under docs/reviews/project-category-only-evidence; full logs/JUnit/HTML/screenshots remain under .gradle/project-category-only-validation. Frontend handoff and rollout references updated; no source guidance rule or production data was changed.

## Final Validation

- [x] 승인 항목 A1–A6가 반영됐고 구현/검증을 막는 decision-level blocker가 없다.
- [x] 모든 실행 세션 구현 및 required validation이 완료됐다.
- [x] Category 단일 Project 관계와 nullable/ownership/이름/quota/in-use/archived 정책을 만족한다.
- [x] Project 및 모든 관련 서버 필터·집계·파생 metadata·revision/cache·Dashboard/Link semantics가 승인 계약과 일치한다.
- [x] old data/replay/key intent/Flyway checksum을 보존하고 staged release/rollback 제한을 격리 환경에서 검증했다.
- [x] 최종 정상 runtime/DTO/DB/설정에서 분류 scope가 제거됐다. 역사적 migrations/replay 및 OAuth scope는 적절히 남아 있다.
- [x] 전체 테스트/clean build/실제 Swagger browser 검증에 변경으로 인한 실패가 남지 않았다.

### API Validation

- [x] 승인 endpoint/method/query/response 및 header가 generated OpenAPI에 있다.
- [x] required/nullable/oneOf/UUID/name/revision 제약과 400/401/403/404/409/410/429/common envelope가 실제 HTTP와 일치한다.
- [x] bridge legacy shape와 final scope-free shape가 혼합되거나 historical response를 새 계약으로 위장하지 않는다.
- [x] Swagger UI가 실제 새 selector/nullable/legacy retry 안내를 렌더링하며 기존 보안을 유지한다.

## Unresolved Decisions and Release Gates

A1–A6는 모두 사용자 승인되었다. nullable Project, 선택 Link 관계, Deploy placeholder, Dashboard selector/migration/temporary override, API v2/cutover/replay window와 header 계약을 승인 내용대로 구현하고 검증한다. 운영 데이터별 Link/widget manifest 승인과 실제 배포는 별도 release gate다.

운영별 Link/widget 매핑과 traffic cutover 시각·backup 보존 기간·clock margin은 배포 gate다. 소규모 현지 DB가 명확하다는 이유로 운영 전체의 모호한 행을 자동 처리하지 않는다. 별도 Deploy domain, mandatory Category, manual Category ordering, 장기 동시 v1/v2 writer, cross-request snapshot pagination은 범위 밖이다.

## Completion

2026-09-15 모든 실행 세션과 Final/API Validation이 통과하여 `completed`로 변경하고 completed-only README에 등록했다. Java 21 전체 재실행과 clean build는 각각 236개 테스트, 실패·오류·생략 0건이다. 실제 Chrome Swagger 검증도 통과했다. [검증 증거와 복구 기록](../reviews/project-category-only-evidence/README.md), [최종 프런트 인계표](../reviews/2026-09-14-project-category-only-impact.md), [단계별 배포 지침](../project-category-only-rollout.md)을 함께 참조한다.

구현 완료와 실제 운영 배포 완료를 구분한다. 운영별 승인이 남아 있다면 runbook의 release gate로 명시하고 배포했다고 기록하지 않는다. 구현 계약 자체가 해결되지 않았으면 미완료 항목을 남기고 blocker를 기록한다.
