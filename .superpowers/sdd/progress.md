# SDD 진행 원장 — 이벤트 시스템 + 정보 공개

Plan: docs/superpowers/plans/2026-07-17-game-events.md
Branch: feat/game-events
Base: (아래 첫 태스크 직전 커밋)

## 완료된 태스크

Task 1: complete (commits d9ea00b..f8b8877, review clean)
  - Critical 수정: 능력변경권 선배정자의 능력이 풀에 남아 중복되던 문제 (계획 코드의 버그)
  - Minor 이월: needAbility.contains(p) O(n) 스캔 / AbilityRegistry.create null 무시
Task 2: complete (commits f8b8877..2b6846c, review clean)
  - 수정: 계획의 테스트가 new Random(seed) 순차 시드로 표본 추출 -> nextInt(2)가 항상 1. 단일 Random으로 교체
  - Minor 이월: EventPicker.pool.remove(lastPicked)는 첫 항목만 제거 (후보 중복 시)
Task 3: complete (commits 2b6846c..c90e007, review clean)
  - Minor 이월: BountySelector 오토언박싱 NPE 가능성 (kills 값이 null일 때) / 테스트 커버리지 소소한 공백
Task 4: complete (commits c90e007..73523c0, review clean, 이슈 없음)
Task 5: complete (commits 73523c0..f533835, review clean)
  - Important 수정: GameContext가 생존자/킬 집계의 살아있는 참조를 노출 -> 생성자에서 불변 복사
  - Minor 수정: 이벤트 오류 로그에 스택 트레이스 포함 (getMessage()만으로는 NPE가 "null"로 찍힘)
  - Minor 이월: EventSpawns.sweep()은 로드된 청크의 엔티티만 회수 가능
Task 6: complete (commits f533835..1f0e78f, review clean, 3라운드)
  - Important 수정 1R: getChunkAt 동기 청크 생성(랙) / 상자 용암 배치 / 좀비 지형 매몰
  - Important 수정 2R: 청크 좌표 truncation vs floor 불일치(음수에서 다른 청크 검증) / 검증 없는 최종 폴백
  - Minor 이월: SupplyDrop 호위 좀비는 상자 기준 평면 오프셋 스폰 (경사지에서 매몰 가능)
Task 7: complete (commits 1f0e78f..dde3742, review clean, 브리프 무결)
  - Minor 이월: 생존자 0명일 때 이벤트가 효과 없이 공지만 할 수 있음 (프로젝트 공통 패턴)
Task 8: complete (commits dde3742..c91523a, review clean, 이슈 없음)
Task 9: complete (commits c91523a..6a71def, review clean)
  - Minor 이월: GameContext를 매 초 생성(방어적 복사)하나 tick()의 조기 반환 전에 만들어짐
  - Minor 이월: 이벤트간격(초)에 최소값 검증 없음 (<=0은 '비활성화'로 안전)

== 구현 태스크 9/9 완료. 최종 전체 리뷰 대기 ==

== 최종 전체 리뷰 (opus) ==
1차: Not ready — blocking 2건
  - 보급 상자가 블록인데 sweep()은 엔티티만 회수 -> 1판 전리품이 2판에 잔존 (한 세션은 월드 재사용)
  - sweep()이 로드된 청크만 봄 -> 200칸 밖 좀비 영구 잔존 (네크로맨서 사고 재현)
2차: Ready — Important 1건 (isGameStarted 가드 때문에 리스너가 정작 필요할 때 안 돎)
3차: Ready — 잔여 없음. 세션 id 방식으로 가드 제거

미해결(사용자 판단용):
  - Minor: createFreshGameWorld의 nextInt(10000) 이름 충돌 시 옛 월드를 로드 (~1/3000 재시작)
    -> 이게 성립해야만 세션 id 0 리셋 문제와 구버전 BYTE 태그 문제가 도달 가능해짐
  - 이월 Minor 9건은 최종 리뷰에서 전부 SHIP 판정
