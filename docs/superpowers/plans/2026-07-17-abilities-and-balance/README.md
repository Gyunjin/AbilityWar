# 능력 확장 — 진척 현황

> **설계 문서:** [../../specs/2026-07-17-abilities-and-balance-design.md](../../specs/2026-07-17-abilities-and-balance-design.md)
> **공통 사항:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**, File Structure
>
> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (권장) 또는 superpowers:executing-plans.
> 태스크 파일 하나가 곧 브리프입니다. 구현자에게 해당 파일 경로 + `00-overview.md` 경로를 함께 주세요.

능력 풀을 6종 → **11종**으로 늘리고, 그 전에 5종이 공통으로 필요로 하는 것을 공용화합니다.

## 진행 방법

태스크는 **번호 순서대로** 합니다. 2~4단계가 전부 1단계 인프라에 의존합니다.

각 태스크를 마치면 이 파일의 상태를 갱신하세요. 태스크 파일 안의 `- [ ]` 스텝 체크박스는 작업 중 진행 표시용입니다.

## 진척

**범례:** ⬜ 대기 · 🟦 진행 중 · ✅ 완료 · ⚠️ 문제 있음

### 1단계 — 공용 인프라 (신규 5종이 얹힐 토대)

| # | 태스크 | 스텝 | 테스트 | 상태 |
|---|---|---|---|---|
| 1 | [Cooldown](task-01-cooldown.md) | 5 | ✅ 9개 신규 | ⬜ |
| 2 | [기존 5종 Cooldown 마이그레이션](task-02-cooldown-migration.md) | 8 | 기존 유지 | ⬜ |
| 3 | [AbilityItems](task-03-ability-items.md) | 10 | 기존 유지 | ⬜ |
| 4 | [Vanish](task-04-vanish.md) | 4 | 기존 유지 | ⬜ |
| 5 | [Ability 훅 2종 + 디스패치](task-05-ability-hooks.md) | 4 | 기존 유지 | ⬜ |

### 2단계 — 밸런스 · 버그 · 명령어

| # | 태스크 | 스텝 | 테스트 | 상태 |
|---|---|---|---|---|
| 6 | [/쿨타임 명령어](task-06-cooldown-command.md) | 7 | 기존 유지 | ⬜ |
| 7 | [AdvancementSuppressor](task-07-advancement-suppressor.md) | 6 | 기존 유지 | ⬜ |
| 8 | [블링커/헐크 밸런스 + 헐크 낙하 버그](task-08-balance-and-hulk-fix.md) | 5 | 기존 유지 | ⬜ |

### 🚦 중간 검증 (신규 능력 착수 전 필수)

| # | 태스크 | 스텝 | 상태 |
|---|---|---|---|
| 9 | [기존 6종 회귀 확인 (서버)](task-09-regression-check.md) | 5 | ⬜ |

> **여기서 멈추고 반드시 확인하세요.** Task 2~3은 동작이 바뀌면 안 되는 순수 리팩터링이고 기존 6종을 전부 건드립니다. 여기서 회귀를 잡지 않으면 신규 5종과 섞여 원인 분리가 어려워집니다. 설계 문서 §10이 1단계를 먼저 하라고 한 이유가 이것입니다.

### 3단계 — 신규 능력 3종 (Vanish · onDealMeleeDamage 공유)

| # | 태스크 | 스텝 | 테스트 | 상태 |
|---|---|---|---|---|
| 10 | [마우가](task-10-mauga.md) | 4 | 기존 유지 | ⬜ |
| 11 | [암살자](task-11-assassin.md) | 8 | ✅ 7개 신규 | ⬜ |
| 12 | [데스웜](task-12-deathworm.md) | 4 | 기존 유지 | ⬜ |

### 4단계 — 신규 능력 2종 (onToggleFlight · Attribute 조작)

| # | 태스크 | 스텝 | 테스트 | 상태 |
|---|---|---|---|---|
| 13 | [바람 인도자](task-13-wind-guide.md) | 4 | 기존 유지 | ⬜ |
| 14 | [윤회자](task-14-reincarnator.md) | 9 | ✅ 10개 신규 | ⬜ |
| 15 | [AbilityRegistry 등록 확인](task-15-registry-test.md) | 3 | ✅ 7개 신규 | ⬜ |

### 5단계 — 실측 검증

| # | 태스크 | 스텝 | 상태 |
|---|---|---|---|
| 16 | [서버 실측 검증](task-16-server-verification.md) | 7 | ⬜ |

## 테스트 개수 추이

이 프로젝트는 **Bukkit 의존 코드를 서버 없이 검증할 수 없습니다.** 컴파일 성공은 동작을 보장하지 않습니다. 순수 로직만 JUnit으로 잠급니다.

| 시점 | 테스트 수 |
|---|---|
| 시작 (기존) | 24 |
| Task 1 이후 | 33 |
| Task 11 이후 | 40 |
| Task 14 이후 | 50 |
| Task 15 이후 | **57** |

테스트가 커버하는 것: `Cooldown` 타이밍 · 암살자 등 뒤 판정 · 윤회자 점프력 역산/넉백 매핑 · `AbilityRegistry` 등록.

테스트가 **커버하지 못하는 것**(Task 9·16에서 서버로 확인): 능력 발동 전반, 은신, 돌진, 습격, 발전과제 억제, 정리 로직.

## 알려진 런타임 가정 (설계 §9)

jar 검증으로 해소되지 않아 **서버에서만** 확인 가능합니다. Task 16에서 다룹니다.

| # | 가정 | 실패 시 대안 |
|---|---|---|
| 1 | 헐크 낙하 데미지 버그가 실제로 고쳐진다 | 이벤트 우선순위 조정 |
| 2 | 음수 `KNOCKBACK_RESISTANCE`가 넉백을 증폭한다 | `setVelocity` 기반 구현 |
| 3 | 바람 인도자 밀쳐내기가 약 15칸 | 계수 보정 후 **실측값을 주석에 기록** |
| 4 | `setAllowFlight(true)`가 `/관전`과 충돌하지 않는다 | 관전 진입 시 플래그 해제 |

## 발견된 문제

작업 중 발견한 문제를 여기에 기록하고 해당 태스크로 돌아가세요.

| 발견 시점 | 문제 | 조치 |
|---|---|---|
| — | — | — |
