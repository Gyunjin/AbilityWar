# 능력 확장 — 공용 인프라 정비와 신규 능력 5종 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 능력 코드의 중복(쿨타임·아이템 판정)을 공용화하고, 기존 능력의 밸런스와 버그를 잡은 뒤, 능력 풀을 6종 → 11종으로 늘린다.

**Architecture:** 먼저 5종이 공통으로 필요로 하는 것(`Cooldown`, `AbilityItems`, `Vanish`, `Ability` 훅 2종)을 만들고 기존 6종을 거기로 옮긴다. 이 마이그레이션은 **동작 변경이 없는 순수 리팩터링**이므로 신규 능력이 얹히기 전에 서버에서 회귀를 확인한다(Task 9). 그 뒤 신규 5종을 하나씩 붙인다. Bukkit에 의존하지 않는 계산(쿨타임 시각, 등 뒤 판정, 점프력 역산, 넉백 매핑)은 `org.example.game`의 순수 클래스로 분리해 JUnit으로 잠근다.

**Tech Stack:** Java 25, Paper API 26.1.2.build.74-stable (compileOnly), JUnit 5.12.2, Gradle 9.3.

## Global Constraints

- **Paper API 좌표:** `io.papermc.paper:paper-api:26.1.2.build.74-stable` — `compileOnly`. 절대 `implementation`으로 바꾸지 말 것(서버가 런타임에 제공).
- **Java 25** 툴체인. `gradle.properties`의 `org.gradle.java.installations.paths`가 JDK를 찾아준다.
- **테스트 코드는 `org.bukkit` 타입을 직접 참조할 수 없다.** paper-api가 `compileOnly`라 테스트 컴파일 클래스패스에 없다 — 참조하면 `compileTestJava`가 실패한다. (검증됨: 테스트가 Bukkit 타입을 참조하면 컴파일 실패, 그러나 Bukkit을 내부에서 참조하는 클래스의 **순수 메서드 호출은 정상 동작**한다.)
- **`Attribute`와 `Sound`는 enum이 아니라 인터페이스**(`OldEnum`, `Keyed`). `switch`, `EnumMap`, `.ordinal()`, `.name()` 사용 불가. `getKey()`로 식별한다. (jar 검증 완료)
- **`Material`과 `Particle`은 진짜 enum.** `switch` 가능.
- **`AttributeModifier`는 `NamespacedKey` 생성자 2종만 쓴다.** `String`/`UUID` 생성자 4종은 전부 deprecated.
- **`Particle.BLOCK`/`FALLING_DUST`는 BlockData 인자 필수.** 누락 시 컴파일이 아니라 **런타임** `IllegalArgumentException`.
- **포션 효과는 유한 지속시간만.** 예외는 윤회자뿐이며 그 경우 `PotionEffect.INFINITE_DURATION`(-1)을 쓰고 `onRevoke` + `AbilityManager.onPlayerJoin` 양쪽에서 제거한다.
- **엔티티 표식은 PDC만.** `FixedMetadataValue`는 저장되지 않아 재시작 후 제거 불가능해진다.
- **플레이어 이동은 반드시 `HealthBarListener.safeTeleport()`.** `p.teleport()`는 체력바 마커(탑승물) 때문에 조용히 실패한다.
- **deprecated API 금지:** `setPVP/getPVP` 대신 `GameRules.setPvp/isPvp`, `setMaxHealth` 대신 `PlayerStats`.
- **`ChatColor`와 `Bukkit.broadcastMessage`는 계속 사용한다.** Adventure 마이그레이션은 이번 범위 밖 — 기존 코드와 일관성을 유지한다.
- **순수 로직 클래스(`org.example.game`)에는 Bukkit import 금지.** 테스트 가능성이 거기서 나온다.
- **테스트 명령:** `./gradlew test --console=plain`
- **빌드 명령:** `./gradlew build --console=plain` (컴파일 + 테스트 모두 수행)

## File Structure

**신규 (순수 로직 — Bukkit 없음, 테스트 대상)**
- `src/main/java/org/example/game/BackstabMath.java` — 암살자 등 뒤 판정
- `src/main/java/org/example/game/ReincarnatorMath.java` — 윤회자 점프력 역산 / 넉백 매핑

**신규 (공용 인프라)**
- `src/main/java/org/example/abilities/Cooldown.java` — 쿨타임 + 전역 무시 스위치
- `src/main/java/org/example/abilities/AbilityItems.java` — 아이템 생성/판정
- `src/main/java/org/example/abilities/Vanish.java` — 완전 투명화
- `src/main/java/org/example/AdvancementSuppressor.java` — 발전과제 억제

**신규 (능력 5종)**
- `src/main/java/org/example/abilities/Maugaability.java`
- `src/main/java/org/example/abilities/Assassinability.java`
- `src/main/java/org/example/abilities/Deathwormability.java`
- `src/main/java/org/example/abilities/WindGuideability.java`
- `src/main/java/org/example/abilities/Reincarnatorability.java`

**수정**
- `src/main/java/org/example/abilities/Ability.java` — 훅 2종 추가
- `src/main/java/org/example/abilities/AbilityRegistry.java` — 신규 5종 등록
- `src/main/java/org/example/abilities/{Blinker,Hulk,DeathReversal,Necromancer,Teemo}ability.java` — `Cooldown` 마이그레이션
- `src/main/java/org/example/abilities/{Blinker,Hulk,Necromancer,Poseidon,Teemo}ability.java` — `AbilityItems` 마이그레이션
- `src/main/java/org/example/AbilityManager.java` — 디스패치 2종, `isBoundItem` 위임, `onPlayerJoin` 정리 확장
- `src/main/java/org/example/GameRules.java` — `setAnnounceAdvancements`
- `src/main/java/org/example/Main.java` — `/쿨타임`, 리스너 등록, 게임룰 호출
- `src/main/resources/plugin.yml` — `/쿨타임` 등록

**테스트**
- `src/test/java/org/example/abilities/CooldownTest.java`
- `src/test/java/org/example/game/BackstabMathTest.java`
- `src/test/java/org/example/game/ReincarnatorMathTest.java`
- `src/test/java/org/example/abilities/AbilityRegistryTest.java`
