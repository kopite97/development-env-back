# DECISION-0003: PostgreSQL revision으로 검증하는 선택적 조회 캐시

Date: 2026-09-16
Status: `accepted`

설계 방향은 사용자 승인으로 accepted다. 2026-09-16 사용자가 PLAN-0012를 취소하고 Session 1 추가 사항의 롤백을 요청했다. 연결된 계획은 rejected이며 이 결정만으로 구현을 재개하지 않는다.

## Context

백엔드는 개인 작업실의 업무 변경과 dataRevision 증가를 같은 PostgreSQL transaction으로 처리한다. 정상 조회의 본문과 observation 번호를 함께 반환하며 생성 replay는 과거 snapshot이다. Redis 조회 캐시는 이 계약을 약화시키지 않아야 한다. 조사·구현·검증 상세는 [PLAN-0012](../plans/PLAN-0012-revision-keyed-redis-query-cache.md)에 있다.

Decision guide가 지정한 DECISION-TEMPLATE.md는 저장소에 없어 기존 Decision의 Context/Decision/Rationale/Consequences/Validation 형식을 따른다. 설계 채택과 실제 실행 권한은 구분하며 실행 범위는 상단 승인 기록을 따른다.

## Decision

다음 설계 방향을 채택한다.

- PostgreSQL primary의 workspace dataRevision을 매 요청 권한 확인과 함께 관측하고, 환경/DB 세대 namespace·cache schema·workspace·revision·정규 조건이 포함된 키만 조회한다.
- 캐시 payload와 저장된 번호는 같은 DB snapshot에서 생성한다. miss에서 다시 관측한 번호가 바뀌면 그 번호로만 저장/응답한다. Redis I/O는 DB transaction 밖에 둔다.
- TTL은 이전 키 정리에 사용한다. writer의 after-commit 삭제나 Redis 성공 여부를 원본 정합성 조건으로 삼지 않는다.
- Redis 실패 시 DB 조회하며, 인증·소유권·no-store·DTO·상태·replay·수정 충돌·커서 계약을 유지한다. Redis는 세션·원본 저장소가 아니다.
- 첫 적용은 category-counts 집계 하나이며 기능별 revision/분산 락/메시지 인프라를 도입하지 않는다. 활성화는 성능 증거에 따른다.

## Rationale

TTL+commit 후 삭제만으로는 commit/delete 사이의 stale hit, 삭제 실패, 지연 read의 stale 재삽입을 막을 수 없다. 버전 키에서는 R의 지연 write가 R+1 조회에 사용되지 않는다. 기존 workspace counter를 활용하므로 새 DB schema나 분산 commit이 필요 없다.

## Consequences

hit에서도 DB 권한·revision 확인 비용이 남고, 전체 workspace 변경으로 관련 없는 조회도 miss가 된다. Redis 실패를 처음 만난 요청은 추가 admission·실패 대기 비용도 부담하며, 사전 bypass는 권한/snapshot 계약을 지키는 DB reader로 직접 진입한다. 작은 집계는 Redis RTT가 더 클 수 있으므로 기준 측정과 최소 비교의 결과에 따라 구현을 보류하거나 종료할 수 있다. 상세 비용·진행 판단은 PLAN을 따른다. DB 복원 시 revision 재사용을 막도록 namespace를 교체한다. 직접 SQL/새 writer도 번호 증가 invariant를 지켜야 한다. 진행 중 read 뒤에 commit된 변경까지 포함하는 절대 최신성은 약속하지 않는다.

## Validation

구현 완료/활성화에는 PLAN-0012의 snapshot pairing, writer inventory, delayed fill, rollback, isolation, Redis 장애, 다중 instance, 복원 세대, OpenAPI/성능 gate를 통과해야 한다. 현재 accepted는 설계 방향의 승인이지 구현/활성화 검증 완료가 아니다. 반복 구현 규칙이 필요해도 `docs/agents/` 수정은 별도 승인 후 진행한다.
