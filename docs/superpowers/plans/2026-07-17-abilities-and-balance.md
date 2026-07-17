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

---

### Task 1: Cooldown

**Files:**
- Create: `src/main/java/org/example/abilities/Cooldown.java`
- Test: `src/test/java/org/example/abilities/CooldownTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `new Cooldown(long durationMs)` / `new Cooldown(long durationMs, LongSupplier clock)`
  - `boolean tryUse(Player p, String busyMessage)` — 준비됐으면 소모하고 true, 아니면 p에게 안내 후 false
  - `boolean consume()` — `tryUse`와 같지만 메시지를 보내지 않는다. Bukkit이 필요 없으므로 테스트가 이것을 쓴다.
  - `boolean isReady()`, `long remainingMs()`, `void reset()`
  - `static void setDisabled(boolean)`, `static boolean isDisabled()`

시간을 주입 가능하게 만드는 이유: 테스트에서 20초를 실제로 기다릴 수 없다. 기본 생성자는 `System::currentTimeMillis`를 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/abilities/CooldownTest.java`:

```java
package org.example.abilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cooldown은 tryUse(Player, String) 때문에 Bukkit 타입을 참조하지만, 이 테스트는
 * 그 메서드를 부르지 않으므로 paper-api 없이도 클래스가 정상 로드됩니다.
 * (테스트 코드가 Bukkit 타입을 직접 참조하면 compileTestJava가 실패합니다.)
 */
class CooldownTest {

    /** 테스트용 가짜 시계. 20초를 실제로 기다릴 수 없으므로 시간을 주입합니다. */
    private static final class FakeClock {
        long now = 1_000_000L;
        long get() { return now; }
        void advance(long ms) { now += ms; }
    }

    @AfterEach
    void resetGlobalSwitch() {
        // static 상태이므로 테스트 간 누수를 막습니다.
        Cooldown.setDisabled(false);
    }

    @Test
    void 처음에는_준비된_상태() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);

        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
    }

    @Test
    void 소모하면_준비되지_않은_상태가_된다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);

        assertTrue(cd.consume());
        assertFalse(cd.isReady());
        assertEquals(5000, cd.remainingMs());
    }

    @Test
    void 소모_후_시간이_지나면_다시_준비된다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();

        clock.advance(4999);
        assertFalse(cd.isReady());
        assertEquals(1, cd.remainingMs());

        clock.advance(1);
        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
    }

    @Test
    void 준비되지_않았으면_consume이_false를_내고_시각을_갱신하지_않는다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();

        clock.advance(3000);
        assertFalse(cd.consume());
        // 실패한 consume이 lastUsed를 밀어버리면 쿨이 영원히 안 끝납니다.
        assertEquals(2000, cd.remainingMs());
    }

    @Test
    void reset하면_즉시_준비된다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();
        assertFalse(cd.isReady());

        cd.reset();
        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
    }

    @Test
    void 전역_무시가_켜지면_항상_준비된_상태() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();
        assertFalse(cd.isReady());

        Cooldown.setDisabled(true);
        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
        assertTrue(cd.consume());
    }

    @Test
    void 전역_무시를_끄면_원래_쿨타임_상태로_돌아온다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);

        Cooldown.setDisabled(true);
        cd.consume();          // 무시 모드에서도 lastUsed는 갱신되어야 합니다
        Cooldown.setDisabled(false);

        // 무시 모드에서 갱신해두지 않았다면 여기서 isReady()가 true가 되어버립니다.
        assertFalse(cd.isReady());
        assertEquals(5000, cd.remainingMs());
    }

    @Test
    void 전역_무시는_모든_인스턴스에_적용된다() {
        FakeClock clock = new FakeClock();
        Cooldown a = new Cooldown(5000, clock::get);
        Cooldown b = new Cooldown(99999, clock::get);
        a.consume();
        b.consume();

        Cooldown.setDisabled(true);
        assertTrue(a.isReady());
        assertTrue(b.isReady());
    }

    @Test
    void isDisabled가_현재_상태를_반영한다() {
        assertFalse(Cooldown.isDisabled());
        Cooldown.setDisabled(true);
        assertTrue(Cooldown.isDisabled());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class Cooldown`

- [ ] **Step 3: 최소 구현**

`src/main/java/org/example/abilities/Cooldown.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.function.LongSupplier;

/**
 * 능력 쿨타임. 기존 5종이 각자 lastUsed 필드와 남은시간 포맷 문자열을 복붙하고 있던 것을
 * 한 곳으로 모읍니다.
 *
 * 시계를 주입 가능하게 둔 이유: 테스트에서 20초를 실제로 기다릴 수 없습니다.
 * 운영 코드는 인자 없는 생성자를 쓰면 됩니다.
 *
 * 이 클래스는 tryUse()의 안내 메시지 때문에 Bukkit 타입을 참조하지만, 나머지 메서드는
 * 순수 계산이라 paper-api 없이도 테스트에서 호출할 수 있습니다.
 */
public final class Cooldown {

    /**
     * 전역 쿨타임 무시 스위치(/쿨타임 명령어). static인 이유: 서버 전역 토글인데
     * 인스턴스는 능력마다 따로 생기므로 다른 선택지가 없습니다.
     * 게임 종료 시 자동으로 꺼지지 않습니다 - OP가 명시적으로 끄는 물건입니다.
     */
    private static volatile boolean disabled = false;

    private final long durationMs;
    private final LongSupplier clock;
    private long lastUsed = 0;

    public Cooldown(long durationMs) {
        this(durationMs, System::currentTimeMillis);
    }

    public Cooldown(long durationMs, LongSupplier clock) {
        this.durationMs = durationMs;
        this.clock = clock;
    }

    public static void setDisabled(boolean value) {
        disabled = value;
    }

    public static boolean isDisabled() {
        return disabled;
    }

    /** 남은 밀리초. 준비됐으면 0. */
    public long remainingMs() {
        if (disabled) return 0;
        long left = (lastUsed + durationMs) - clock.getAsLong();
        return Math.max(0, left);
    }

    public boolean isReady() {
        return remainingMs() <= 0;
    }

    /**
     * 준비됐으면 소모하고 true. 아니면 아무것도 하지 않고 false.
     *
     * 무시 모드에서도 lastUsed는 갱신합니다 - 마우가의 둔화 패시브처럼 "쿨이 도는 중"을
     * 참조하는 로직이 무시 모드에서도 일관되게 "항상 준비됨"으로 보이게 하기 위함입니다.
     */
    public boolean consume() {
        if (!isReady()) return false;
        lastUsed = clock.getAsLong();
        return true;
    }

    /** 준비됐으면 소모하고 true. 아니면 p에게 남은시간을 안내하고 false. */
    public boolean tryUse(Player p, String busyMessage) {
        long left = remainingMs();
        if (left > 0) {
            p.sendMessage(ChatColor.RED + busyMessage
                    + " (남은 시간: " + String.format("%.1f", left / 1000.0) + "초)");
            return false;
        }
        lastUsed = clock.getAsLong();
        return true;
    }

    public void reset() {
        lastUsed = 0;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests (기존 24 + 신규 9)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/abilities/Cooldown.java src/test/java/org/example/abilities/CooldownTest.java
git commit -m "feat: 공용 Cooldown 추가

기존 5종이 각자 복붙하던 lastUsed/남은시간 포맷을 한 곳으로 모읍니다.
전역 무시 스위치(/쿨타임)와 테스트용 시계 주입을 포함합니다."
```

---

### Task 2: 기존 5종을 Cooldown으로 마이그레이션

**Files:**
- Modify: `src/main/java/org/example/abilities/Blinkerability.java`
- Modify: `src/main/java/org/example/abilities/Hulkability.java`
- Modify: `src/main/java/org/example/abilities/DeathReversalAbility.java`
- Modify: `src/main/java/org/example/abilities/Necromancerability.java`
- Modify: `src/main/java/org/example/abilities/Teemoability.java`

**Interfaces:**
- Consumes: `Cooldown` (Task 1) — `tryUse(Player, String)`, `reset()`
- Produces: 없음

**동작이 바뀌면 안 되는 순수 리팩터링이다.** 쿨타임 수치는 이 태스크에서 바꾸지 않는다(Task 8에서 한다). 쿨타임이 없는 포세이돈은 손대지 않는다.

각 능력에서 다음을 바꾼다:
1. `private static final long COOLDOWN_MS = N;` + `private long lastUsed = 0;` → `private final Cooldown cooldown = new Cooldown(N);`
2. 쿨 체크 블록 → `if (!cooldown.tryUse(p, "메시지")) return;`
3. `lastUsed = now;` 제거 (tryUse가 처리)
4. `resetCooldown()` → `cooldown.reset();`

기존 안내 메시지에서 `Cooldown.tryUse`가 붙여주는 `(남은 시간: N초)` 부분은 빼고 앞부분만 넘긴다.

- [ ] **Step 1: 블링커 마이그레이션**

`Blinkerability.java`에서 다음을 찾아:

```java
    private static final long COOLDOWN_MS = 8000;
    private static final String ITEM_TAG = "[능력] 블링커";
    private static final double DASH_DAMAGE = 4.0;

    private long lastUsed = 0;
```

이렇게 바꾼다:

```java
    private static final String ITEM_TAG = "[능력] 블링커";
    private static final double DASH_DAMAGE = 4.0;

    private final Cooldown cooldown = new Cooldown(8000);
```

`resetCooldown()`을 찾아:

```java
    @Override
    public void resetCooldown() {
        lastUsed = 0;
    }
```

이렇게 바꾼다:

```java
    @Override
    public void resetCooldown() {
        cooldown.reset();
    }
```

쿨 체크 블록을 찾아:

```java
        long now = System.currentTimeMillis();
        long timeLeft = (lastUsed + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "아직 능력이 준비되지 않았습니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return;
        }
```

이렇게 바꾼다:

```java
        if (!cooldown.tryUse(p, "아직 능력이 준비되지 않았습니다!")) return;
```

마지막으로 대쉬 성공 시의 `lastUsed = now;`를 찾아 **삭제**한다:

```java
        p.sendMessage(ChatColor.GREEN + "쉬슉! 벽을 왜곡하여 공간을 이동했습니다.");
        lastUsed = now;
    }
```

이렇게 바꾼다:

```java
        p.sendMessage(ChatColor.GREEN + "쉬슉! 벽을 왜곡하여 공간을 이동했습니다.");
    }
```

**주의:** 블링커는 텔레포트 실패 시 `return`으로 쿨타임을 소모하지 않는 동작이 있었다. `tryUse`는 호출 시점에 이미 소모하므로 이 동작이 바뀐다. **의도된 변경이며 그대로 둔다** — 텔레포트 실패는 정상 경로가 아니고, 실패 시 쿨을 안 먹는 것이 오히려 무한 재시도를 허용한다. 실패 안내 메시지는 유지한다.

- [ ] **Step 2: 헐크 마이그레이션**

`Hulkability.java`에서:

```java
    private static final double MAX_HEALTH = 40.0;
    private static final long COOLDOWN_MS = 20000;
```

→

```java
    private static final double MAX_HEALTH = 40.0;
```

```java
    private long lastUsed = 0;
```

→

```java
    private final Cooldown cooldown = new Cooldown(20000);
```

`resetCooldown()` 본문 `lastUsed = 0;` → `cooldown.reset();`

쿨 체크 블록:

```java
        long now = System.currentTimeMillis();
        long timeLeft = (lastUsed + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "아직 힘이 회복되지 않았습니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return;
        }

        lastUsed = now;
        waitingForLanding = true;
```

→

```java
        if (!cooldown.tryUse(p, "아직 힘이 회복되지 않았습니다!")) return;

        waitingForLanding = true;
```

- [ ] **Step 3: 사망회귀 마이그레이션**

`DeathReversalAbility.java`에서:

```java
    private static final long COOLDOWN_MS = 300000;
    private static final int HISTORY_MAX_SIZE = 30; // 30초 저장

    private long lastUsed = 0;
```

→

```java
    private static final int HISTORY_MAX_SIZE = 30; // 30초 저장

    private final Cooldown cooldown = new Cooldown(300000);
```

`resetCooldown()` 본문 → `cooldown.reset();`

`onFatalDamage`의 쿨 체크:

```java
        long now = System.currentTimeMillis();
        long timeLeft = (lastUsed + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "사망회귀가 대기 중입니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return false;
        }

        if (history.isEmpty()) return false;

        event.setCancelled(true);
        lastUsed = now;
```

→

```java
        // 히스토리가 비어 있으면 회귀할 곳이 없으므로 쿨타임을 소모하기 전에 확인합니다.
        if (history.isEmpty()) return false;
        if (!cooldown.tryUse(p, "사망회귀가 대기 중입니다!")) return false;

        event.setCancelled(true);
```

**주의:** 원래 코드는 쿨 체크 → 히스토리 체크 순서였다. `tryUse`가 호출 즉시 소모하므로, 히스토리가 비어 있을 때 쿨을 헛되이 먹지 않도록 순서를 뒤집었다. 의도된 개선이다.

- [ ] **Step 4: 네크로맨서 마이그레이션**

`Necromancerability.java`에서:

```java
    private static final long COOLDOWN_MS = 35000;
```

→ (삭제)

```java
    private long lastUsed = 0;
```

→

```java
    private final Cooldown cooldown = new Cooldown(35000);
```

`resetCooldown()` 본문 → `cooldown.reset();`

`onInteract`의 쿨 체크:

```java
        long now = System.currentTimeMillis();
        long timeLeft = (lastUsed + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "지팡이에 사령의 기운이 부족합니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return;
        }
```

→

```java
        if (!cooldown.tryUse(p, "지팡이에 사령의 기운이 부족합니다!")) return;
```

메서드 끝의 `lastUsed = now;`를 **삭제**한다.

- [ ] **Step 5: 티모 마이그레이션**

`Teemoability.java`에서 (필드명이 `lastShotTime`임에 주의):

```java
    private static final long COOLDOWN_MS = 2500;
```

→ (삭제)

```java
    private long lastShotTime = 0;
```

→

```java
    private final Cooldown cooldown = new Cooldown(2500);
```

`resetCooldown()` 본문 `lastShotTime = 0;` → `cooldown.reset();`

`onInteract`의 쿨 체크:

```java
        long now = System.currentTimeMillis();
        long timeLeft = (lastShotTime + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "독침을 재장전 중입니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return;
        }
        lastShotTime = now;
```

→

```java
        if (!cooldown.tryUse(p, "독침을 재장전 중입니다!")) return;
```

- [ ] **Step 6: 잔재 확인**

Run: `grep -rn "COOLDOWN_MS\|lastUsed\|lastShotTime" src/main/java/org/example/abilities/`
Expected: `Cooldown.java`의 내부 구현만 나오고, 능력 5종에는 하나도 남아 있지 않아야 한다.

- [ ] **Step 7: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/org/example/abilities/
git commit -m "refactor: 기존 5종을 공용 Cooldown으로 마이그레이션

동작 변경 없는 리팩터링입니다. 쿨타임 수치는 그대로 두었습니다.
두 곳만 의도적으로 달라집니다:
- 블링커: 텔레포트 실패 시에도 쿨을 소모합니다(무한 재시도 방지)
- 사망회귀: 히스토리 확인을 쿨 소모보다 먼저 합니다(헛되이 쿨을 먹지 않도록)"
```

---

### Task 3: AbilityItems

**Files:**
- Create: `src/main/java/org/example/abilities/AbilityItems.java`
- Modify: `src/main/java/org/example/abilities/Blinkerability.java`
- Modify: `src/main/java/org/example/abilities/Hulkability.java`
- Modify: `src/main/java/org/example/abilities/Necromancerability.java`
- Modify: `src/main/java/org/example/abilities/Poseidonability.java`
- Modify: `src/main/java/org/example/abilities/Teemoability.java`
- Modify: `src/main/java/org/example/AbilityManager.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `static ItemStack AbilityItems.create(Material type, ChatColor color, String tag)`
  - `static boolean AbilityItems.isHolding(Player p, Material type, String tag)`
  - `static boolean AbilityItems.isBound(ItemStack item)`

- [ ] **Step 1: `AbilityItems` 작성**

`src/main/java/org/example/abilities/AbilityItems.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 능력 아이템의 생성과 판정을 한 곳으로 모읍니다. 기존 6종이 각자 createItem()과
 * isHoldingX()를 복붙하고 있었고 판정 로직도 완전히 동일했습니다.
 *
 * 판정을 표시이름 문자열로 하는 것은 기존 코드의 방식을 그대로 따른 것입니다.
 * PDC 기반으로 바꾸면 모루로 이름만 바꾼 잡템이 귀속 아이템으로 인정되는 문제까지
 * 해결되지만, 그것은 이번 범위가 아닙니다.
 */
public final class AbilityItems {

    /** 모든 능력 아이템의 표시이름에 들어가는 공통 태그. 귀속 판정에 씁니다. */
    public static final String BOUND_TAG = "[능력]";

    private AbilityItems() {
    }

    /**
     * 표시이름이 color+tag인 귀속 아이템을 만듭니다.
     *
     * @param tag "[능력] "으로 시작해야 합니다. 안 그러면 귀속 판정(isBound)에 걸리지 않아
     *            사망 시 드랍되고 버릴 수 있게 됩니다.
     */
    public static ItemStack create(Material type, ChatColor color, String tag) {
        if (!tag.startsWith(BOUND_TAG)) {
            throw new IllegalArgumentException("능력 아이템 태그는 \"" + BOUND_TAG + "\"으로 시작해야 합니다: " + tag);
        }
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + tag);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** p가 주손에 해당 태그의 아이템을 들고 있는지. */
    public static boolean isHolding(Player p, Material type, String tag) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return matches(main, type, tag);
    }

    /** 귀속 아이템("[능력]" 태그)인지. */
    public static boolean isBound(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(BOUND_TAG);
    }

    private static boolean matches(ItemStack item, Material type, String tag) {
        return item != null && item.getType() == type
                && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(tag);
    }
}
```

- [ ] **Step 2: `AbilityManager.isBoundItem`을 위임으로 교체**

`AbilityManager.java`에서 다음을 찾아:

```java
    /** 능력 아이템(태그 "[능력]")인지 확인합니다. 귀속 아이템 판정에 공용으로 사용합니다. */
    private boolean isBoundItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains("[능력]");
    }
```

이렇게 바꾼다:

```java
    /** 능력 아이템(태그 "[능력]")인지 확인합니다. 판정 로직은 AbilityItems가 갖고 있습니다. */
    private boolean isBoundItem(ItemStack item) {
        return AbilityItems.isBound(item);
    }
```

import 추가:

```java
import org.example.abilities.AbilityItems;
```

- [ ] **Step 3: 블링커를 AbilityItems로 교체**

`Blinkerability.java`의 `createItem()`을 찾아 **삭제**하고, `onGrant`의 `p.getInventory().addItem(createItem());`을 이렇게 바꾼다:

```java
        p.getInventory().addItem(AbilityItems.create(Material.NETHER_STAR, ChatColor.AQUA, ITEM_TAG));
```

`onInteract`의 아이템 판정을 찾아:

```java
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return;
        if (!item.getItemMeta().hasDisplayName() || !item.getItemMeta().getDisplayName().contains(ITEM_TAG)) return;
```

이렇게 바꾼다:

```java
        if (!AbilityItems.isHolding(p, Material.NETHER_STAR, ITEM_TAG)) return;
```

- [ ] **Step 4: 헐크를 AbilityItems로 교체**

`Hulkability.java`의 `createItem()`과 `isHoldingGauntlet(Player)`를 **삭제**한다.

`onGrant`:

```java
        p.getInventory().addItem(createItem());
```

→

```java
        p.getInventory().addItem(AbilityItems.create(Material.COBBLESTONE, ChatColor.RED, ITEM_TAG));
```

`onInteract`:

```java
        if (!isHoldingGauntlet(p)) return;
```

→

```java
        if (!AbilityItems.isHolding(p, Material.COBBLESTONE, ITEM_TAG)) return;
```

- [ ] **Step 5: 네크로맨서를 AbilityItems로 교체**

네크로맨서의 `createItem()`은 **lore(설명문) 2줄을 갖고 있어 다르다.** `AbilityItems.create()`는 lore를 다루지 않으므로, 생성 부분만 위임하고 lore는 그대로 얹는다.

`createItem()`을 이렇게 바꾼다:

```java
    private ItemStack createItem() {
        ItemStack item = AbilityItems.create(Material.BONE, ChatColor.DARK_RED, ITEM_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "우클릭 시 주인을 따르고 적을 공격하는 사령 좀비 3마리를 부립니다.");
            lore.add(ChatColor.GRAY + "지팡이를 들고 있으면 멀리 떨어진 소환수들이 주인 쪽으로 다가옵니다.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
```

`isHoldingStaff(Player)`를 **삭제**하고, 호출부 `if (!isHoldingStaff(p)) return;` 2곳(`onInteract`, `onPassiveTick`)을 이렇게 바꾼다:

```java
        if (!AbilityItems.isHolding(p, Material.BONE, ITEM_TAG)) return;
```

**주의:** `onPassiveTick`의 호출은 `if (!isHoldingStaff(p)) return;` 형태다. 두 곳 모두 바꾼다.

- [ ] **Step 6: 포세이돈을 AbilityItems로 교체**

포세이돈의 `createItem()`은 **AttributeModifier와 인챈트, ItemFlag를 갖고 있어 다르다.** 생성만 위임한다.

`createItem()`의 첫 두 줄을 찾아:

```java
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + ITEM_TAG);
            meta.setUnbreakable(true);
```

이렇게 바꾼다:

```java
        ItemStack item = AbilityItems.create(Material.TRIDENT, ChatColor.BLUE, ITEM_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
```

나머지(AttributeModifier, 인챈트, ItemFlag, `item.setItemMeta(meta)`)는 그대로 둔다.

- [ ] **Step 7: 티모를 AbilityItems로 교체**

`Teemoability.java`의 `createItem()`과 `isHoldingBlowgun(Player)`를 **삭제**한다.

`onGrant`:

```java
        p.getInventory().addItem(createItem());
```

→

```java
        p.getInventory().addItem(AbilityItems.create(Material.BLAZE_ROD, ChatColor.GREEN, ITEM_TAG));
```

`onInteract`:

```java
        if (!isHoldingBlowgun(p)) return;
```

→

```java
        if (!AbilityItems.isHolding(p, Material.BLAZE_ROD, ITEM_TAG)) return;
```

- [ ] **Step 8: 잔재 확인**

Run: `grep -rn "isHoldingGauntlet\|isHoldingStaff\|isHoldingBlowgun" src/main/java/`
Expected: 출력 없음

Run: `grep -rn "getItemMeta().getDisplayName().contains" src/main/java/`
Expected: `AbilityItems.java`만 나온다

- [ ] **Step 9: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/org/example/abilities/ src/main/java/org/example/AbilityManager.java
git commit -m "refactor: 능력 아이템 생성/판정을 AbilityItems로 공용화

기존 6종이 각자 복붙하던 createItem()/isHoldingX()를 한 곳으로 모읍니다.
lore(네크로맨서)와 인챈트/모디파이어(포세이돈)를 가진 아이템은 생성만
위임하고 나머지는 각자 유지합니다."
```

---

### Task 4: Vanish

**Files:**
- Create: `src/main/java/org/example/abilities/Vanish.java`
- Modify: `src/main/java/org/example/AbilityManager.java` (`onPlayerJoin`)

**Interfaces:**
- Consumes: 없음
- Produces:
  - `static void Vanish.hide(Plugin plugin, Player p)`
  - `static void Vanish.show(Plugin plugin, Player p)`
  - `static boolean Vanish.isHidden(Player p)`
  - `static void Vanish.reapplyFor(Plugin plugin, Player joiner)` — 접속자에게 기존 은신자들을 다시 숨김

- [ ] **Step 1: `Vanish` 작성**

`src/main/java/org/example/abilities/Vanish.java`:

```java
package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 완전 투명화. 암살자와 데스웜이 씁니다.
 *
 * 포션(INVISIBILITY)을 쓰지 않는 이유: 바닐라 투명화는 입은 갑옷과 든 아이템이 그대로
 * 보입니다. 암살자는 검을 들어야 하므로 포션만으로는 "완전 투명화"가 성립하지 않습니다.
 * 갑옷을 벗겨 숨기는 방법도 있지만 손에 든 무기는 여전히 남고, 벗긴 갑옷을 되돌리는
 * 과정에서 사망/접속종료가 끼면 장비가 사라집니다.
 *
 * 대신 viewer.hideEntity()로 다른 플레이어들의 클라이언트에서 대상을 통째로 지웁니다.
 * 갑옷, 손아이템, 이름표, 파티클까지 전부 사라집니다. 대상 본인에게는 아무 변화가 없고,
 * 서버 측 히트박스와 대미지 판정은 그대로 살아 있습니다.
 *
 * show()는 반드시 호출돼야 합니다. 누락되면 영구 투명 버그가 됩니다(티모가 겪었던 것과
 * 같은 계열). 호출 경로: 은신 만료 태스크, 공격에 의한 해제, onRevoke, 그리고 안전망으로
 * onPassiveTick의 정합성 체크.
 */
public final class Vanish {

    private static final Set<UUID> hidden = Collections.synchronizedSet(new HashSet<>());

    private Vanish() {
    }

    /** p를 다른 모든 온라인 플레이어에게서 숨깁니다. */
    public static void hide(Plugin plugin, Player p) {
        if (p == null) return;
        hidden.add(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(p)) {
                viewer.hidePlayer(plugin, p);
            }
        }
    }

    /** 숨김을 해제합니다. 이미 보이는 상태면 무시(중복 호출은 무해). */
    public static void show(Plugin plugin, Player p) {
        if (p == null) return;
        hidden.remove(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(p)) {
                viewer.showPlayer(plugin, p);
            }
        }
    }

    public static boolean isHidden(Player p) {
        return p != null && hidden.contains(p.getUniqueId());
    }

    /**
     * 방금 접속한 플레이어에게 현재 은신 중인 사람들을 다시 숨깁니다.
     *
     * 이게 없으면 은신 도중 접속한 플레이어에게는 hidePlayer가 걸려 있지 않아
     * 그 사람 눈에만 은신자가 보입니다.
     */
    public static void reapplyFor(Plugin plugin, Player joiner) {
        if (joiner == null) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(joiner) && isHidden(other)) {
                joiner.hidePlayer(plugin, other);
            }
        }
    }
}
```

**주의:** 스펙은 `hideEntity`/`showEntity`를 언급하지만, `Player`를 숨기는 데는 `hidePlayer(Plugin, Player)`/`showPlayer(Plugin, Player)`가 정확한 오버로드다. 둘 다 존재하며 `hideEntity(Plugin, Entity)`는 임의 엔티티용이다. 플레이어 전용 오버로드를 쓴다.

- [ ] **Step 2: `AbilityManager.onPlayerJoin`에 재적용 추가**

`AbilityManager.java`의 `onPlayerJoin`을 찾아:

```java
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (playerAbilities.containsKey(p.getUniqueId())) return; // 능력 보유 중이면 정상 상태

        PlayerStats.resetMaxHealth(p);
        // 티모의 투명화는 스스로 만료되지만, 이전 버전에서 무한 지속시간으로 걸려
        // 저장돼 있던 플레이어를 위해 여기서도 확실히 정리합니다.
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }
```

이렇게 바꾼다:

```java
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // 은신 중인 다른 플레이어를 이 접속자에게도 숨깁니다. 이게 없으면 방금 들어온
        // 사람 눈에만 은신자가 보입니다. 능력 보유 여부와 무관하므로 조기 반환보다 앞에 둡니다.
        Vanish.reapplyFor(plugin, p);

        if (playerAbilities.containsKey(p.getUniqueId())) return; // 능력 보유 중이면 정상 상태

        PlayerStats.resetMaxHealth(p);
        // 티모의 투명화는 스스로 만료되지만, 이전 버전에서 무한 지속시간으로 걸려
        // 저장돼 있던 플레이어를 위해 여기서도 확실히 정리합니다.
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // 능력이 없는데 은신 상태로 남아 있으면 잔재입니다.
        Vanish.show(plugin, p);
    }
```

import 추가:

```java
import org.example.abilities.Vanish;
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/abilities/Vanish.java src/main/java/org/example/AbilityManager.java
git commit -m "feat: 완전 투명화 Vanish 추가

hidePlayer로 클라이언트에서 통째로 지웁니다. 포션 투명화는 갑옷과 손아이템이
그대로 보여서 암살자에 쓸 수 없습니다. 은신 중 접속한 플레이어에게 재적용하는
경로를 AbilityManager.onPlayerJoin에 얹었습니다."
```

---

### Task 5: Ability 훅 2종 + AbilityManager 디스패치

**Files:**
- Modify: `src/main/java/org/example/abilities/Ability.java`
- Modify: `src/main/java/org/example/AbilityManager.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `default void Ability.onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {}`
  - `default void Ability.onToggleFlight(Player p, PlayerToggleFlightEvent event) {}`

이번 설계에서 기존 코드에 가하는 유일한 구조 변경이다.

- [ ] **Step 1: `Ability`에 훅 2종 추가**

`Ability.java`의 `resetCooldown()` 선언 바로 아래에 삽입:

```java
    /**
     * 이 플레이어가 다른 엔티티를 근접 공격했을 때 호출됩니다. (공격자 기준)
     *
     * 기존 onEntityDamageByEntity는 "맞는 쪽"이 이 플레이어일 때만 옵니다. 신규 능력
     * 4종(마우가 흡혈, 암살자 배율, 데스웜 습격 트리거, 바람 인도자 밀쳐냄)이 "내가 때릴 때"를
     * 필요로 하므로 공격자 기준 훅을 따로 둡니다. 각 능력이 자기 Listener를 등록하면
     * 같은 능력 보유자 수만큼 이벤트가 중복 처리되므로(AbilityManager 주석 참고)
     * 여기서 위임합니다.
     */
    default void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {}

    /** 이 플레이어가 공중에서 비행 토글(스페이스 두 번)을 시도할 때 호출됩니다. */
    default void onToggleFlight(Player p, PlayerToggleFlightEvent event) {}
```

import 추가:

```java
import org.bukkit.event.player.PlayerToggleFlightEvent;
```

(`EntityDamageByEntityEvent`는 이미 import되어 있다 — 확인 후 중복 추가하지 말 것.)

- [ ] **Step 2: `AbilityManager`에 디스패치 2종 추가**

`AbilityManager.java`의 기존 `onAbilityDamageByEntity` 메서드 바로 아래에 삽입:

```java
    /**
     * 공격자 기준 근접 대미지 위임. 피격자 기준인 onAbilityDamageByEntity와는 별개이며,
     * 플레이어가 플레이어를 때리면 두 훅이 각각 자기 능력 인스턴스로 갑니다. 의도된 동작입니다.
     */
    @EventHandler
    public void onAbilityDealDamage(EntityDamageByEntityEvent event) {
        if (!plugin.isGameStarted()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        Ability a = playerAbilities.get(attacker.getUniqueId());
        if (a != null) a.onDealMeleeDamage(attacker, event);
    }

    /** 바람 인도자의 더블 점프용. 게임 진행 중이 아니어도 위임합니다(로비에서도 비행 토글은 발생). */
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        Ability a = playerAbilities.get(p.getUniqueId());
        if (a != null) a.onToggleFlight(p, event);
    }
```

import 추가:

```java
import org.bukkit.event.player.PlayerToggleFlightEvent;
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/abilities/Ability.java src/main/java/org/example/AbilityManager.java
git commit -m "feat: 공격자 기준 근접 대미지 훅과 비행 토글 훅 추가

신규 능력 4종이 '내가 때릴 때'를 필요로 하는데, 기존 위임은 피격자 기준뿐이었습니다.
각 능력이 Listener를 직접 등록하는 것은 이 코드베이스가 의도적으로 없앤 패턴입니다."
```

---

### Task 6: /쿨타임 명령어

**Files:**
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/org/example/Main.java`

**Interfaces:**
- Consumes: `Cooldown.setDisabled(boolean)`, `Cooldown.isDisabled()` (Task 1)
- Produces: 없음

- [ ] **Step 1: `plugin.yml`에 명령어 등록**

`src/main/resources/plugin.yml`의 `commands:` 블록 끝(마지막 항목인 `관전:` 아래)에 추가:

```yaml
  쿨타임:
    description: 모든 능력의 쿨타임을 무시합니다. (테스트용)
    usage: /쿨타임 [on|off]
```

- [ ] **Step 2: `Main.onEnable`에 executor 등록**

`Main.java`에서 다음을 찾아:

```java
        if (this.getCommand("관전") != null) {
            this.getCommand("관전").setExecutor(this);
            this.getCommand("관전").setTabCompleter(this);
        }
```

바로 아래에 삽입:

```java
        if (this.getCommand("쿨타임") != null) {
            this.getCommand("쿨타임").setExecutor(this);
            this.getCommand("쿨타임").setTabCompleter(this);
        }
```

- [ ] **Step 3: `isAdminCommand`에 추가**

`Main.java`의 `isAdminCommand`에서 `case "팀설정":` 바로 아래에 삽입:

```java
            case "쿨타임":
```

- [ ] **Step 4: `onCommand`에 분기 추가**

`Main.java`의 `onCommand`에서 `관전` 처리 블록 바로 아래(`return false;` 직전)에 삽입:

```java
        if (cmd.getName().equalsIgnoreCase("쿨타임")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "현재 쿨타임 무시: "
                        + (Cooldown.isDisabled() ? "켜짐 (테스트 모드)" : "꺼짐"));
                player.sendMessage(ChatColor.GRAY + "사용법: /쿨타임 [on|off]");
                return true;
            }

            boolean on = args[0].equalsIgnoreCase("on");
            Cooldown.setDisabled(on);

            // 테스트 모드가 켜진 줄 모르고 진짜 게임을 하면 곤란하므로 전원에게 알립니다.
            if (on) {
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[능력자] 쿨타임 무시가 켜졌습니다. "
                        + ChatColor.GRAY + "(테스트 모드 - 모든 능력을 즉시 재사용할 수 있습니다)");
            } else {
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[능력자] 쿨타임 무시가 꺼졌습니다. "
                        + ChatColor.GRAY + "(정상 모드)");
            }
            return true;
        }

```

import 추가:

```java
import org.example.abilities.Cooldown;
```

- [ ] **Step 5: 탭 완성 추가**

`Main.java`의 `onTabComplete`에서 `관전` 처리 블록 바로 아래에 삽입:

```java
        if (command.getName().equalsIgnoreCase("쿨타임") && args.length == 1) {
            for (String opt : new String[]{"on", "off"}) {
                if (opt.startsWith(args[0].toLowerCase())) completions.add(opt);
            }
        }
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/plugin.yml src/main/java/org/example/Main.java
git commit -m "feat: /쿨타임 [on|off] 추가 (OP 전용)

능력 테스트 시 매번 20초를 기다리지 않도록 전역 쿨타임 무시를 제공합니다.
켤 때 전원에게 알립니다 - 테스트 모드인 줄 모르고 진짜 게임을 하면 곤란합니다."
```

---

### Task 7: AdvancementSuppressor

**Files:**
- Create: `src/main/java/org/example/AdvancementSuppressor.java`
- Modify: `src/main/java/org/example/GameRules.java`
- Modify: `src/main/java/org/example/Main.java`

**Interfaces:**
- Consumes: 없음
- Produces: `static void GameRules.setAnnounceAdvancements(World world, boolean announce)`

- [ ] **Step 1: `GameRules`에 메서드 추가**

`GameRules.java`의 `isPvp` 메서드 아래에 삽입:

```java
    /** 발전과제 달성을 채팅에 알릴지 설정합니다. */
    public static void setAnnounceAdvancements(World world, boolean announce) {
        if (world == null) return;
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, announce);
    }
```

- [ ] **Step 2: `AdvancementSuppressor` 작성**

`src/main/java/org/example/AdvancementSuppressor.java`:

```java
package org.example;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 발전과제를 채팅과 토스트 양쪽에서 없앱니다.
 *
 * 왜 PlayerAdvancementDoneEvent가 아닌가: 그 이벤트는 Cancellable이 아닙니다. 있는 것은
 * message(Component) 오버로드뿐이라 채팅 브로드캐스트만 없앨 수 있고, 우측 상단 토스트는
 * 발전과제가 이미 부여된 뒤에 클라이언트가 띄우는 것이라 막지 못합니다.
 *
 * PlayerAdvancementCriterionGrantEvent(Paper)는 Cancellable입니다. 취소하면 달성 조건
 * 자체가 기록되지 않아 발전과제가 완성되지 않고, 따라서 토스트도 채팅도 발생하지 않습니다.
 * 데이터팩이 필요 없습니다.
 */
public class AdvancementSuppressor implements Listener {

    /**
     * 조합법 해금 발전과제는 통과시킵니다. 마인크래프트는 레시피북 해금을 발전과제로
     * 구현하므로, 전부 막으면 레시피북이 영영 비어 있게 됩니다. 제작 자체는 레시피를
     * 알면 되지만, 파밍 위주 게임에서 도감이 안 열리는 것은 실질적인 불편입니다.
     */
    private static final String RECIPE_PREFIX = "minecraft:recipes/";

    @EventHandler(ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        String key = event.getAdvancement().getKey().toString();
        if (key.startsWith(RECIPE_PREFIX)) return;
        event.setCancelled(true);
    }
}
```

- [ ] **Step 3: `Main.onEnable`에 리스너 등록**

`Main.java`에서 다음을 찾아:

```java
        getServer().getPluginManager().registerEvents(new EventSpawnCleanupListener(this), this);
```

바로 아래에 삽입:

```java
        getServer().getPluginManager().registerEvents(new AdvancementSuppressor(), this);
```

- [ ] **Step 4: 월드 생성 시 게임룰 적용**

`Main.java`의 `createFreshGameWorld()`에서 다음을 찾아:

```java
            newWorld.getWorldBorder().setCenter(0, 0);
            newWorld.getWorldBorder().setSize(cfgInitialBorderSize);
            GameRules.setPvp(newWorld, false);
```

바로 아래에 삽입:

```java
            // 발전과제 채팅 알림을 끕니다. AdvancementSuppressor가 토스트까지 막지만,
            // 예외로 통과시킨 레시피 발전과제가 채팅에 새어나가지 않도록 하는 이중 방어입니다.
            GameRules.setAnnounceAdvancements(newWorld, false);
```

`startGame()`에서 다음을 찾아:

```java
        gameWorld.getWorldBorder().setCenter(0, 0);
        gameWorld.getWorldBorder().setSize(cfgInitialBorderSize);
        GameRules.setPvp(gameWorld, false);
```

바로 아래에 삽입:

```java
        GameRules.setAnnounceAdvancements(gameWorld, false);
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/org/example/AdvancementSuppressor.java src/main/java/org/example/GameRules.java src/main/java/org/example/Main.java
git commit -m "feat: 발전과제 억제 (채팅 + 토스트)

PlayerAdvancementDoneEvent는 Cancellable이 아니라 토스트를 못 막습니다.
Paper의 PlayerAdvancementCriterionGrantEvent를 취소해 달성 조건 자체를
기록하지 않습니다. 레시피북 해금 발전과제는 예외로 통과시킵니다."
```

---

### Task 8: 블링커/헐크 밸런스 + 헐크 낙하 데미지 버그

**Files:**
- Modify: `src/main/java/org/example/abilities/Blinkerability.java`
- Modify: `src/main/java/org/example/abilities/Hulkability.java`

**Interfaces:**
- Consumes: `Cooldown` (Task 1)
- Produces: 없음

`SLAM_DAMAGE`는 **이미 6.0이므로 건드리지 않는다.** 스펙 §3.2에서 확인된 사실이다.

- [ ] **Step 1: 블링커 쿨타임 8초 → 4초**

`Blinkerability.java`에서:

```java
    private final Cooldown cooldown = new Cooldown(8000);
```

→

```java
    private final Cooldown cooldown = new Cooldown(4000);
```

`onGrant`의 안내 메시지에서:

```java
            p.sendMessage(ChatColor.RED + "(쿨타임: 8초)");
```

→

```java
            p.sendMessage(ChatColor.RED + "(쿨타임: 4초)");
```

- [ ] **Step 2: 헐크 쿨타임 20초 → 10초**

`Hulkability.java`에서:

```java
    private final Cooldown cooldown = new Cooldown(20000);
```

→

```java
    private final Cooldown cooldown = new Cooldown(10000);
```

`onGrant`의 안내 메시지에서:

```java
            p.sendMessage(ChatColor.RED + "(쿨타임: 20초)");
```

→

```java
            p.sendMessage(ChatColor.RED + "(쿨타임: 10초)");
```

- [ ] **Step 3: 헐크 낙하 데미지 버그 수정**

**근본 원인:** `fallDamageImmune = true`가 `performSlam()` 안에서 켜지는데, `performSlam()`은 `onPlayerMove`에서 착지를 감지한 뒤에야 불린다. 착지 시 Bukkit의 이벤트 순서는:

1. `EntityDamageEvent(cause=FALL)` — 여기서 `fallDamageImmune`은 **아직 false**
2. `PlayerMoveEvent` → `performSlam()` → 그제서야 `fallDamageImmune = true`

즉 면역이 항상 한 발 늦게 켜진다. 3틱 뒤 끄는 타이머는 이미 지나간 데미지에 소용이 없다.

**수정:** 면역을 점프하는 순간 켠다.

`onInteract`에서 다음을 찾아:

```java
        waitingForLanding = true;
        p.setVelocity(new Vector(0, JUMP_POWER, 0));
```

이렇게 바꾼다:

```java
        waitingForLanding = true;
        // 면역을 여기서(점프하는 순간) 켭니다. performSlam()에서 켜면 늦습니다 -
        // 착지 시 EntityDamageEvent(FALL)가 PlayerMoveEvent보다 먼저 오므로,
        // 착지를 감지한 뒤에 켜면 낙하 데미지가 이미 지나간 뒤입니다.
        // 끄는 것은 performSlam()의 3틱 타이머가 그대로 담당합니다.
        fallDamageImmune = true;
        p.setVelocity(new Vector(0, JUMP_POWER, 0));
```

`performSlam()`에서 다음을 찾아:

```java
        // 착지 직후 잠깐 낙하 대미지 면역 처리(같은 틱/다음 틱에 들어오는 FALL 이벤트 방지)
        fallDamageImmune = true;
        new BukkitRunnable() {
```

이렇게 바꾼다:

```java
        // 면역은 onInteract(점프 시점)에서 이미 켜져 있습니다. 여기서는 끄는 타이머만
        // 겁니다. waitingForLanding과 합치지 않는 이유: 착지 판정과 데미지가 정확히 같은
        // 틱에 오지 않는 경우가 있어, waitingForLanding이 false가 된 뒤에도 잠시 면역이
        // 남아야 합니다.
        new BukkitRunnable() {
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/abilities/Blinkerability.java src/main/java/org/example/abilities/Hulkability.java
git commit -m "fix: 헐크 낙하 데미지 버그 + 블링커/헐크 쿨타임 조정

헐크가 자기 슬램으로 자기가 낙하 데미지를 맞던 버그를 고칩니다. 면역이
performSlam()에서 켜졌는데, 착지 시 EntityDamageEvent(FALL)가 PlayerMoveEvent보다
먼저 오므로 항상 한 발 늦었습니다. 점프하는 순간 켜도록 바꿉니다.

블링커 8초 -> 4초, 헐크 20초 -> 10초. SLAM_DAMAGE는 이미 6.0이라 그대로 둡니다."
```

---

### Task 9: [중간 검증] 기존 6종 회귀 확인

**Files:** 없음 (서버에서 확인)

**Interfaces:**
- Consumes: Task 1~8 전체
- Produces: 없음

Task 2~3의 마이그레이션은 **동작 변경이 없어야 하는 순수 리팩터링**이다. 여기서 회귀를 잡지 않으면 신규 능력 5종과 섞여 원인 분리가 어려워진다. 스펙 §10이 1단계를 먼저 하라고 한 이유가 이것이다.

- [ ] **Step 1: jar 빌드 및 배포**

Run: `./gradlew clean build --console=plain`
Expected: BUILD SUCCESSFUL, `build/libs/AbilityWar-1.0-SNAPSHOT.jar` 생성

jar를 서버의 `plugins/`에 복사하고 **서버를 완전히 재시작**한다(`/reload` 금지).

- [ ] **Step 2: 기존 6종 회귀 체크리스트**

`/게임설정 평화시간(초) 30` 후 `/게임시작`. `/능력변경 <자기이름> <능력>`으로 각 능력을 직접 확인한다.

| 능력 | 확인 |
|---|---|
| 블링커 | 우클릭 시 6칸 순간이동. 쿨 **4초**. 쿨 중 우클릭 시 남은시간 메시지 |
| 헐크 | 최대 체력 40. 우클릭 시 점프. 착지 시 슬램(주변 대미지 + 부드러운 블록 파괴). 쿨 **10초** |
| **헐크 낙하** | **높은 곳에서 액티브 사용 → 착지해도 체력이 줄지 않아야 함** (핵심 수정) |
| 포세이돈 | 삼지창 지급. 물속에서 힘+돌고래의 우아함. 급류로 솟구침 |
| 사망회귀 | 치명상 시 과거로 회귀. 쿨 5분. **히스토리가 없는 상태(게임 시작 직후)에서 즉사 시 쿨을 먹지 않아야 함** |
| 네크로맨서 | 우클릭 시 좀비 3마리. 쿨 35초. 지팡이 들고 있으면 따라옴 |
| 티모 | 우클릭 시 독침. 쿨 2.5초. 5초 정지 시 투명화 |

- [ ] **Step 3: 신규 기능 확인**

| 항목 | 확인 |
|---|---|
| `/쿨타임 on` | 전원에게 안내 메시지. 이후 모든 능력이 쿨 없이 즉시 재사용 가능 |
| `/쿨타임 off` | 안내 메시지. 쿨타임이 정상 복귀 |
| `/쿨타임` (인자 없음) | 현재 상태 표시 |
| 발전과제 | 나무 캐기 등으로 발전과제 조건 달성 → **토스트와 채팅 모두 안 뜸** |
| 레시피북 | 나무 획득 후 제작대에서 **레시피북이 정상 표시됨** |
| 귀속 아이템 | 능력 아이템을 버릴 수 없음(Q). 왼손으로 옮길 수 없음(F). 사망 시 드랍 안 됨 |

- [ ] **Step 4: 문제가 있으면 기록 후 해당 Task로 복귀**

회귀가 발견되면 이 파일 하단에 기록하고 원인 Task로 돌아간다. **신규 능력 작업(Task 10~)을 시작하기 전에 해소한다.**

- [ ] **Step 5: 설정 원복**

```
/게임설정 평화시간(초) 720
/쿨타임 off
```

---

### Task 10: 마우가

**Files:**
- Create: `src/main/java/org/example/abilities/Maugaability.java`
- Modify: `src/main/java/org/example/abilities/AbilityRegistry.java`

**Interfaces:**
- Consumes: `Cooldown` (Task 1), `AbilityItems` (Task 3), `Ability.onDealMeleeDamage` (Task 5), `PlayerStats.getMaxHealth(Player)` (기존)
- Produces: 없음

컨셉: 돌진으로 진입하고 근접전에서 버티는 탱커. 진입기를 쓰면 쿨타임 20초 내내 느려지는 리스크를 진다.

- [ ] **Step 1: `Maugaability` 작성**

`src/main/java/org/example/abilities/Maugaability.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.example.PlayerStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 마우가: 돌진으로 진입하고 근접전에서 버티는 탱커.
 *
 * 진입기를 쓰면 쿨타임 20초 내내 느려지는 리스크를 집니다. 들어가는 건 쉽지만
 * 빠져나오는 건 어렵다는 것이 이 능력의 성격입니다.
 */
public class Maugaability implements Ability {

    private static final String ITEM_TAG = "[능력] 돌진";
    private static final long COOLDOWN_MS = 20000;

    private static final double SLOW_AMOUNT = -0.2;      // 이동속도 -20%
    private static final double DASH_SPEED = 1.2;
    private static final int DASH_MAX_TICKS = 60;        // 3초
    private static final double IMPACT_RADIUS = 4.0;
    private static final double IMPACT_DAMAGE = 8.0;
    private static final double LIFESTEAL_CHANCE = 0.3;
    private static final double LIFESTEAL_AMOUNT = 1.0;
    private static final double ABSORPTION_MAX = 4.0;    // 노란 하트 2개
    private static final int LANDING_WATCH_TICKS = 200;  // 10초
    private static final int LANDING_SLOW_TICKS = 40;    // 2초

    private final Cooldown cooldown = new Cooldown(COOLDOWN_MS);
    private final Random random = new Random();

    private BukkitTask dashTask;
    private BukkitTask slowRemovalTask;
    private final List<BukkitTask> landingWatchers = new ArrayList<>();
    private boolean dashing = false;

    @Override
    public String getName() {
        return "마우가";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    private static NamespacedKey slowKey() {
        return new NamespacedKey(JavaPlugin.getProvidingPlugin(Maugaability.class), "mauga_dash_slow");
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(AbilityItems.create(Material.IRON_INGOT, ChatColor.RED, ITEM_TAG));

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(액티브: 철괴 우클릭 시 무적 상태로 돌진합니다. 벽에 부딪히거나 좌클릭하면 폭발합니다.)");
            p.sendMessage(ChatColor.GRAY + "(패시브: 근접 타격 시 30% 확률로 회복합니다. 체력이 꽉 차면 흡수 하트로 쌓입니다.)");
            p.sendMessage(ChatColor.RED + "(대가: 돌진 쿨타임 20초 동안 이동속도가 20% 느려집니다.)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        // 돌진 중에 능력이 바뀌면 저항 5가 영구히 남습니다. 반드시 전부 정리합니다.
        cancelDash();
        if (slowRemovalTask != null) {
            slowRemovalTask.cancel();
            slowRemovalTask = null;
        }
        for (BukkitTask t : landingWatchers) {
            if (t != null) t.cancel();
        }
        landingWatchers.clear();

        if (p == null) return;
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        removeSlow(p);
    }

    // --- 패시브: 둔화 ---

    private void applySlow(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        removeSlow(p); // 중첩 방지
        attr.addModifier(new AttributeModifier(slowKey(), SLOW_AMOUNT,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeSlow(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        NamespacedKey key = slowKey();
        for (AttributeModifier m : new ArrayList<>(attr.getModifiers())) {
            if (key.equals(m.getKey())) attr.removeModifier(m);
        }
    }

    private boolean hasSlow(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return false;
        NamespacedKey key = slowKey();
        for (AttributeModifier m : attr.getModifiers()) {
            if (key.equals(m.getKey())) return true;
        }
        return false;
    }

    /**
     * 안전망: 쿨이 준비됐는데 둔화가 남아 있으면 걷어냅니다.
     * 제거 태스크가 유실되는 경우와, /쿨타임 on으로 쿨이 즉시 준비 상태가 되는 경우를
     * 함께 처리합니다.
     */
    @Override
    public void onPassiveTick(Player p) {
        if (cooldown.isReady() && !dashing && hasSlow(p)) {
            removeSlow(p);
        }
    }

    // --- 패시브: 흡혈 ---

    @Override
    public void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {
        // 발사체는 제외합니다 - 명세가 "근접 타격"입니다.
        if (!attacker.equals(event.getDamager())) return;
        if (random.nextDouble() >= LIFESTEAL_CHANCE) return;

        double max = PlayerStats.getMaxHealth(attacker);
        if (attacker.getHealth() < max) {
            attacker.setHealth(Math.min(attacker.getHealth() + LIFESTEAL_AMOUNT, max));
        } else {
            // 체력이 꽉 차면 흡수 하트로. ABSORPTION 포션이 아니라 setAbsorptionAmount를
            // 직접 쓰는 이유: 포션은 레벨당 4.0씩 고정 부여라 1.0씩 누적이 불가능하고,
            // 만료 시 통째로 사라집니다.
            double current = attacker.getAbsorptionAmount();
            if (current < ABSORPTION_MAX) {
                attacker.setAbsorptionAmount(Math.min(current + LIFESTEAL_AMOUNT, ABSORPTION_MAX));
            }
        }
    }

    // --- 액티브: 돌진 ---

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        // 좌클릭은 돌진 중일 때 종료 트리거입니다.
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (dashing) {
                event.setCancelled(true);
                endDash(p);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!AbilityItems.isHolding(p, Material.IRON_INGOT, ITEM_TAG)) return;

        event.setCancelled(true);
        if (dashing) return;
        if (!cooldown.tryUse(p, "아직 돌진할 힘이 모이지 않았습니다!")) return;

        startDash(p);
    }

    private void startDash(Player p) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        dashing = true;

        // 저항 5(amp 4)는 바닐라에서 레벨당 20% 감소이므로 실제로 100% 감소, 즉 무적입니다.
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DASH_MAX_TICKS + 20, 4, false, false, false));
        applySlow(p);

        // 쿨타임이 끝나는 시각에 맞춰 둔화를 제거합니다. onPassiveTick의 안전망이 보조합니다.
        if (slowRemovalTask != null) slowRemovalTask.cancel();
        slowRemovalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeSlow(p), COOLDOWN_MS / 50);

        final Vector direction = p.getLocation().getDirection().normalize();
        p.sendMessage(ChatColor.RED + "돌진! " + ChatColor.GRAY + "(좌클릭으로 즉시 폭발)");

        dashTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!dashing || !p.isOnline() || p.isDead()) {
                    endDash(p);
                    return;
                }
                ticks++;

                // 매 틱 속도를 다시 주지 않으면 마찰로 즉시 감속합니다.
                p.setVelocity(direction.clone().multiply(DASH_SPEED));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 6, 0.3, 0.3, 0.3, 0.02);

                // 종료 조건 1: 벽 충돌 - 다음 틱 위치가 통과 불가면
                Location next = p.getLocation().add(direction.clone().multiply(DASH_SPEED));
                if (!next.getBlock().isPassable()) {
                    endDash(p);
                    return;
                }
                // 종료 조건 2: 3초 경과
                if (ticks >= DASH_MAX_TICKS) {
                    endDash(p);
                }
            }
        }, 1L, 1L);
    }

    private void cancelDash() {
        dashing = false;
        if (dashTask != null) {
            dashTask.cancel();
            dashTask = null;
        }
    }

    private void endDash(Player p) {
        if (!dashing) return;
        cancelDash();

        if (p == null || !p.isOnline()) return;
        p.removePotionEffect(PotionEffectType.RESISTANCE);

        Location loc = p.getLocation();
        p.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        for (Entity e : p.getNearbyEntities(IMPACT_RADIUS, IMPACT_RADIUS, IMPACT_RADIUS)) {
            if (!(e instanceof LivingEntity victim) || e.equals(p)) continue;
            victim.damage(IMPACT_DAMAGE, p);
            victim.setVelocity(victim.getVelocity().add(new Vector(0, 0.9, 0)));
            watchLanding(plugin, victim);
        }
        p.sendMessage(ChatColor.RED + "쾅! 충격파가 퍼집니다.");
    }

    /** 띄운 적이 착지하는 순간 구속 10을 2초 겁니다. 10초 안에 착지하지 않으면 추적을 포기합니다. */
    private void watchLanding(JavaPlugin plugin, LivingEntity victim) {
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!victim.isValid() || victim.isDead() || ticks > LANDING_WATCH_TICKS) {
                    holder[0].cancel();
                    landingWatchers.remove(holder[0]);
                    return;
                }
                if (victim.isOnGround()) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, LANDING_SLOW_TICKS, 9, false, false, false));
                    holder[0].cancel();
                    landingWatchers.remove(holder[0]);
                }
            }
        }, 5L, 2L);
        landingWatchers.add(holder[0]);
    }
}
```

- [ ] **Step 2: `AbilityRegistry`에 등록**

`AbilityRegistry.java`의 static 블록에서 `register(Teemoability::new);` 바로 아래에 삽입:

```java
        register(Maugaability::new);
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/abilities/Maugaability.java src/main/java/org/example/abilities/AbilityRegistry.java
git commit -m "feat: 마우가 능력 추가"
```

---

### Task 11: 암살자

**Files:**
- Create: `src/main/java/org/example/game/BackstabMath.java`
- Create: `src/main/java/org/example/abilities/Assassinability.java`
- Test: `src/test/java/org/example/game/BackstabMathTest.java`
- Modify: `src/main/java/org/example/abilities/AbilityRegistry.java`

**Interfaces:**
- Consumes: `Cooldown`, `AbilityItems`, `Vanish`, `Ability.onDealMeleeDamage`
- Produces: `static boolean BackstabMath.isBackstab(double lookX, double lookZ, double toVictimX, double toVictimZ)`

등 뒤 판정을 순수 함수로 분리하는 이유: `Vector`가 Bukkit 타입이라 테스트 코드가 참조할 수 없다. raw double을 받으면 테스트가 가능하다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/game/BackstabMathTest.java`:

```java
package org.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackstabMathTest {

    // 피격자가 +X 방향을 바라보고 있다고 고정하고, 공격이 들어온 방향을 바꿔가며 확인합니다.
    private static final double LOOK_X = 1.0;
    private static final double LOOK_Z = 0.0;

    @Test
    void 뒤에서_때리면_등_뒤다() {
        // 공격자가 -X쪽에 있으면 (공격자 -> 피격자) 벡터는 +X. 피격자 시선도 +X = 등 뒤.
        assertTrue(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 1.0, 0.0));
    }

    @Test
    void 정면에서_때리면_등_뒤가_아니다() {
        // 공격자가 +X쪽에 있으면 (공격자 -> 피격자) 벡터는 -X. 시선 +X와 반대 = 정면.
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, -1.0, 0.0));
    }

    @Test
    void 옆에서_때리면_등_뒤가_아니다() {
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 0.0, 1.0));
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 0.0, -1.0));
    }

    @Test
    void 약_45도면_등_뒤다() {
        // 내적 0.5 = 60도가 경계. 45도는 내적 약 0.707 > 0.5 이므로 등 뒤.
        double c = Math.cos(Math.toRadians(45));
        double s = Math.sin(Math.toRadians(45));
        assertTrue(BackstabMath.isBackstab(LOOK_X, LOOK_Z, c, s));
    }

    @Test
    void 약_75도면_등_뒤가_아니다() {
        // 75도는 내적 약 0.26 < 0.5 이므로 등 뒤가 아니다.
        double c = Math.cos(Math.toRadians(75));
        double s = Math.sin(Math.toRadians(75));
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, c, s));
    }

    @Test
    void 크기가_달라도_방향만_본다() {
        // 정규화하므로 벡터 길이는 결과에 영향을 주지 않아야 한다.
        assertTrue(BackstabMath.isBackstab(5.0, 0.0, 100.0, 0.0));
    }

    @Test
    void 영벡터는_등_뒤가_아니다() {
        // 공격자와 피격자가 정확히 같은 위치면 방향을 정할 수 없다.
        // 그냥 나누면 NaN이 나와 비교가 조용히 false가 되므로 명시적으로 확인한다.
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 0.0, 0.0));
        assertFalse(BackstabMath.isBackstab(0.0, 0.0, 1.0, 0.0));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class BackstabMath`

- [ ] **Step 3: `BackstabMath` 작성**

`src/main/java/org/example/game/BackstabMath.java`:

```java
package org.example.game;

/**
 * 암살자의 등 뒤 판정. Bukkit에 의존하지 않는 순수 계산이라 서버 없이 테스트할 수 있습니다.
 *
 * Vector(org.bukkit.util.Vector)를 인자로 받지 않는 이유가 바로 그것입니다 - 테스트 코드는
 * paper-api가 compileOnly라 Bukkit 타입을 참조할 수 없습니다.
 */
public final class BackstabMath {

    /** 내적 0.5 = 약 60도. 이보다 좁으면 등 뒤로 봅니다. */
    private static final double THRESHOLD = 0.5;

    private BackstabMath() {
    }

    /**
     * 피격자의 시선 방향과 공격이 들어온 방향이 같으면 등 뒤로 판정합니다.
     *
     * Y축은 무시하고 수평 성분만 씁니다 - 위/아래에서 때린 것을 "등 뒤"로 치면 어색합니다.
     *
     * @param lookX     피격자 시선 방향의 X 성분 (정규화 전이어도 됨)
     * @param lookZ     피격자 시선 방향의 Z 성분
     * @param toVictimX (공격자 위치 -> 피격자 위치) 벡터의 X 성분
     * @param toVictimZ 같은 벡터의 Z 성분
     * @return 등 뒤에서 공격했으면 true. 방향을 정할 수 없으면(영벡터) false.
     */
    public static boolean isBackstab(double lookX, double lookZ, double toVictimX, double toVictimZ) {
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double toLen = Math.sqrt(toVictimX * toVictimX + toVictimZ * toVictimZ);

        // 영벡터면 방향이 없습니다. 그냥 나누면 NaN이 나와 비교가 조용히 false가 되므로
        // 명시적으로 처리합니다.
        if (lookLen == 0.0 || toLen == 0.0) return false;

        double dot = (lookX / lookLen) * (toVictimX / toLen)
                + (lookZ / lookLen) * (toVictimZ / toLen);
        return dot > THRESHOLD;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 40 tests (기존 33 + 신규 7)

- [ ] **Step 5: `Assassinability` 작성**

`src/main/java/org/example/abilities/Assassinability.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.example.game.BackstabMath;

/**
 * 암살자: 어둠 속에 숨어 단 한 번의 강력한 일격을 노립니다.
 *
 * 은신은 Vanish(hidePlayer)를 씁니다. 포션 투명화는 갑옷과 든 검이 그대로 보여서
 * 암살자에게는 쓸 수 없습니다(Vanish 주석 참고).
 */
public class Assassinability implements Ability {

    private static final String ITEM_TAG = "[능력] 그림자 은신";
    private static final long COOLDOWN_MS = 20000;
    private static final int HIDE_TICKS = 100;          // 5초
    private static final double BACKSTAB_MULTIPLIER = 2.5;
    private static final double FRONT_MULTIPLIER = 2.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_MS);
    private BukkitTask expiryTask;
    private boolean hiding = false;

    @Override
    public String getName() {
        return "암살자";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(AbilityItems.create(Material.INK_SAC, ChatColor.DARK_GRAY, ITEM_TAG));

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(액티브: 먹물 주머니 우클릭 시 5초간 완전히 사라지고 신속 2를 얻습니다.)");
            p.sendMessage(ChatColor.GRAY + "(은신 중 공격하면 대미지 2배. 등 뒤에서 찌르면 2.5배.)");
            p.sendMessage(ChatColor.RED + "(공격하는 순간 은신이 풀립니다. 쿨타임: 20초)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        cancelExpiry();
        hiding = false;
        if (p == null) return;
        // show()가 누락되면 영구 투명 버그가 됩니다. 중복 호출은 무해합니다.
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
    }

    /** 안전망: 은신 상태가 아닌데 숨겨진 채로 남아 있으면 되돌립니다. */
    @Override
    public void onPassiveTick(Player p) {
        if (!hiding && Vanish.isHidden(p)) {
            Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AbilityItems.isHolding(p, Material.INK_SAC, ITEM_TAG)) return;

        event.setCancelled(true);
        if (hiding) return;
        if (!cooldown.tryUse(p, "아직 그림자에 숨을 수 없습니다!")) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        hiding = true;
        Vanish.hide(plugin, p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, HIDE_TICKS, 1, false, false, false));
        p.sendMessage(ChatColor.DARK_GRAY + "그림자에 스며듭니다... " + ChatColor.GRAY + "(5초)");

        cancelExpiry();
        expiryTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            unhide(p);
            if (p.isOnline()) p.sendMessage(ChatColor.GRAY + "은신이 풀렸습니다.");
        }, HIDE_TICKS);
    }

    @Override
    public void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {
        if (!hiding) return;
        if (!attacker.equals(event.getDamager())) return; // 발사체 제외

        double multiplier = FRONT_MULTIPLIER;
        Entity victim = event.getEntity();
        // LivingEntity가 아니면(방어구 거치대 등) 배율만 적용하고 등 뒤 판정은 건너뜁니다.
        if (victim instanceof LivingEntity living) {
            Vector look = living.getLocation().getDirection();
            Vector toVictim = living.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (BackstabMath.isBackstab(look.getX(), look.getZ(), toVictim.getX(), toVictim.getZ())) {
                multiplier = BACKSTAB_MULTIPLIER;
            }
        }

        event.setDamage(event.getDamage() * multiplier);
        attacker.sendMessage(ChatColor.DARK_GRAY + (multiplier == BACKSTAB_MULTIPLIER
                ? "등 뒤에서 급소를 찔렀습니다! (x2.5)" : "기습! (x2)"));

        // 공격하는 순간 은신 해제
        unhide(attacker);
    }

    private void unhide(Player p) {
        cancelExpiry();
        hiding = false;
        if (p == null) return;
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
    }

    private void cancelExpiry() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }
}
```

- [ ] **Step 6: `AbilityRegistry`에 등록**

`AbilityRegistry.java`의 static 블록에서 `register(Maugaability::new);` 바로 아래에 삽입:

```java
        register(Assassinability::new);
```

- [ ] **Step 7: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 40 tests

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/org/example/game/BackstabMath.java src/test/java/org/example/game/BackstabMathTest.java src/main/java/org/example/abilities/Assassinability.java src/main/java/org/example/abilities/AbilityRegistry.java
git commit -m "feat: 암살자 능력 추가"
```

---

### Task 12: 데스웜

**Files:**
- Create: `src/main/java/org/example/abilities/Deathwormability.java`
- Modify: `src/main/java/org/example/abilities/AbilityRegistry.java`

**Interfaces:**
- Consumes: `Cooldown`, `AbilityItems`, `Vanish`, `Ability.onDealMeleeDamage`
- Produces: 없음

컨셉: 모래 위에서만 진가를 발휘하는 사막의 포식자. 지형이 곧 능력이다.

- [ ] **Step 1: `Deathwormability` 작성**

`src/main/java/org/example/abilities/Deathwormability.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * 데스웜: 모래 위에서만 진가를 발휘하는 사막의 포식자.
 *
 * 지형이 곧 능력입니다. 모래를 벗어나면 패시브가 끊기고, 잠행 중 모래를 벗어나면
 * 습격 없이 실패합니다.
 */
public class Deathwormability implements Ability {

    private static final String ITEM_TAG = "[능력] 사막의 포식자";
    private static final long COOLDOWN_MS = 25000;
    private static final int BURROW_TICKS = 100;      // 5초
    private static final int PASSIVE_TICKS = 40;      // 2초. 무한 지속을 쓰지 않는 이유는 아래 주석 참고
    private static final double AMBUSH_RADIUS = 3.0;
    private static final double AMBUSH_DAMAGE = 7.0;
    private static final double AMBUSH_LIFT = 1.6;    // 마우가(0.9)보다 강하게 - 명세가 "높게"

    private final Cooldown cooldown = new Cooldown(COOLDOWN_MS);
    private BukkitTask burrowTask;
    private boolean burrowing = false;

    @Override
    public String getName() {
        return "데스웜";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    /** Material은 진짜 enum이라 switch가 가능합니다. Attribute/Sound와 다릅니다. */
    private static boolean isSand(Material m) {
        switch (m) {
            case SAND:
            case RED_SAND:
            case SUSPICIOUS_SAND:
            case SANDSTONE:
            case SMOOTH_SANDSTONE:
            case CUT_SANDSTONE:
            case CHISELED_SANDSTONE:
            case RED_SANDSTONE:
            case SMOOTH_RED_SANDSTONE:
            case CUT_RED_SANDSTONE:
            case CHISELED_RED_SANDSTONE:
                return true;
            default:
                return false;
        }
    }

    private static boolean onSand(Player p) {
        return isSand(p.getLocation().add(0, -1, 0).getBlock().getType());
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(AbilityItems.create(Material.SUSPICIOUS_SAND, ChatColor.YELLOW, ITEM_TAG));

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(패시브: 모래/사암 위에 있으면 재생 1과 저항 1을 얻습니다.)");
            p.sendMessage(ChatColor.GRAY + "(액티브: 모래 위에서 우클릭 시 5초간 땅속으로 잠행합니다. 완전히 사라지고 신속 3.)");
            p.sendMessage(ChatColor.GRAY + "(잠행이 끝나거나 적을 때리면 습격 - 주변에 대미지 7과 높은 띄우기.)");
            p.sendMessage(ChatColor.RED + "(잠행 중 모래를 벗어나면 실패합니다. 쿨타임은 그대로 소모됩니다. 쿨타임: 25초)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        cancelBurrow();
        burrowing = false;
        if (p == null) return;
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    /**
     * 모래 위 패시브. 지속시간 2초로 매 초 갱신합니다.
     *
     * 무한 지속을 쓰지 않는 이유는 티모 주석에 이미 있습니다 - 오프라인 플레이어에게서
     * 해제할 수 없어 영구 버프 버그가 됩니다. 모래를 벗어나면 최대 2초 뒤 자연히 사라집니다.
     *
     * ambient/particles/icon을 전부 false로 두어 상대에게 정보를 주지 않습니다.
     */
    @Override
    public void onPassiveTick(Player p) {
        // 안전망: 잠행이 아닌데 숨겨진 채로 남아 있으면 되돌립니다.
        if (!burrowing && Vanish.isHidden(p)) {
            Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        }

        if (!onSand(p)) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PASSIVE_TICKS, 0, false, false, false), true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PASSIVE_TICKS, 0, false, false, false), true);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AbilityItems.isHolding(p, Material.SUSPICIOUS_SAND, ITEM_TAG)) return;

        event.setCancelled(true);
        if (burrowing) return;

        // 발동 조건 확인을 쿨 소모보다 먼저 합니다 - 모래가 아니면 쿨을 소모하지 않습니다.
        if (!onSand(p)) {
            p.sendMessage(ChatColor.RED + "모래 위에서만 잠행할 수 있습니다!");
            return;
        }
        if (!cooldown.tryUse(p, "아직 땅속으로 들어갈 수 없습니다!")) return;

        startBurrow(p);
    }

    private void startBurrow(Player p) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        burrowing = true;
        Vanish.hide(plugin, p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BURROW_TICKS, 2, false, false, false));
        p.sendMessage(ChatColor.YELLOW + "모래 속으로 스며듭니다...");

        cancelBurrow();
        burrowTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!burrowing || !p.isOnline() || p.isDead()) {
                    failBurrow(p, false);
                    return;
                }
                ticks++;

                // 실패 조건: 모래가 아닌 블록 위로 올라가면 즉시 실패. 습격 없음.
                if (!onSand(p)) {
                    failBurrow(p, true);
                    return;
                }

                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HUSK_AMBIENT, 0.4f, 0.5f);
                // Particle.BLOCK은 BlockData 인자가 필수입니다. 누락하면 컴파일이 아니라
                // 런타임 IllegalArgumentException이 납니다.
                p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 12, 0.4, 0.1, 0.4,
                        Material.SAND.createBlockData());

                if (ticks >= BURROW_TICKS) {
                    ambush(p);
                }
            }
        }, 1L, 1L);
    }

    private void cancelBurrow() {
        if (burrowTask != null) {
            burrowTask.cancel();
            burrowTask = null;
        }
    }

    /** 실패로 끝난 경우 습격은 발생하지 않습니다. 쿨타임은 그대로 소모된 채 남습니다. */
    private void failBurrow(Player p, boolean notify) {
        cancelBurrow();
        burrowing = false;
        if (p == null) return;
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
        if (notify && p.isOnline()) {
            p.sendMessage(ChatColor.RED + "모래를 벗어나 잠행이 실패했습니다! " + ChatColor.GRAY + "(쿨타임은 그대로 흐릅니다)");
        }
    }

    @Override
    public void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {
        if (!burrowing) return;
        if (!attacker.equals(event.getDamager())) return;
        // 선제 공격으로 트리거된 경우 그 타격의 대미지는 배율 없이 그대로 두고,
        // 습격 광역 대미지가 별도로 들어갑니다. 즉 맞은 대상은 평타 + 7을 함께 받습니다.
        ambush(attacker);
    }

    private void ambush(Player p) {
        cancelBurrow();
        burrowing = false;
        if (p == null || !p.isOnline()) return;

        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);

        Location loc = p.getLocation();
        p.getWorld().playSound(loc, Sound.BLOCK_SAND_BREAK, 1.4f, 0.6f);
        p.getWorld().spawnParticle(Particle.BLOCK, loc, 40, 1.0, 0.5, 1.0, Material.SAND.createBlockData());

        for (Entity e : p.getNearbyEntities(AMBUSH_RADIUS, AMBUSH_RADIUS, AMBUSH_RADIUS)) {
            if (!(e instanceof LivingEntity victim) || e.equals(p)) continue;
            victim.damage(AMBUSH_DAMAGE, p);
            victim.setVelocity(victim.getVelocity().add(new Vector(0, AMBUSH_LIFT, 0)));
        }
        p.sendMessage(ChatColor.YELLOW + "모래를 뚫고 솟아올랐습니다!");
    }
}
```

- [ ] **Step 2: `AbilityRegistry`에 등록**

`AbilityRegistry.java`의 static 블록에서 `register(Assassinability::new);` 바로 아래에 삽입:

```java
        register(Deathwormability::new);
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 40 tests

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/abilities/Deathwormability.java src/main/java/org/example/abilities/AbilityRegistry.java
git commit -m "feat: 데스웜 능력 추가"
```

---

### Task 13: 바람 인도자

**Files:**
- Create: `src/main/java/org/example/abilities/WindGuideability.java`
- Modify: `src/main/java/org/example/abilities/AbilityRegistry.java`

**Interfaces:**
- Consumes: `AbilityItems`, `Ability.onToggleFlight` (Task 5), `Ability.onDealMeleeDamage` (Task 5)
- Produces: 없음

컨셉: 하늘을 지배한다. 공격력은 없지만 위치를 지배한다. **쿨타임 없음.**

- [ ] **Step 1: `WindGuideability` 작성**

`src/main/java/org/example/abilities/WindGuideability.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

/**
 * 바람 인도자: 하늘을 지배합니다. 공격력은 없지만 위치를 지배합니다.
 *
 * 브리즈 막대 자체의 대미지는 바닐라 기본값(막대기 수준)입니다. 별도 조정하지 않습니다 -
 * 이 능력의 가치는 대미지가 아니라 위치 지배입니다.
 */
public class WindGuideability implements Ability {

    private static final String ITEM_TAG = "[능력] 바람의 인도";

    private static final double DOUBLE_JUMP_FORWARD = 0.9;
    private static final double DOUBLE_JUMP_UP = 0.8;

    /**
     * 밀쳐내기 초기 속도. 목표는 도달 거리 약 15칸(바닐라 넉백 II가 약 6칸이므로 2.5배)입니다.
     *
     * 초기 속도에서 도달 거리는 마찰/중력에 좌우되므로 정확한 계산이 불가능합니다.
     * 이 값은 설계상 근사값이며 실측으로 보정해야 합니다. 보정 결과를 이 주석에 남기세요.
     * (실측 전 초기값: 수평 2.6 / 수직 0.6)
     */
    private static final double PUSH_HORIZONTAL = 2.6;
    private static final double PUSH_UP = 0.6;

    private boolean doubleJumpUsed = false;

    @Override
    public String getName() {
        return "바람 인도자";
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(AbilityItems.create(Material.BREEZE_ROD, ChatColor.AQUA, ITEM_TAG));
        // 더블 점프를 받으려면 비행 허용이 켜져 있어야 PlayerToggleFlightEvent가 발생합니다.
        p.setAllowFlight(true);

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(공중에서 스페이스를 두 번 누르면 더블 점프합니다. 착지하면 다시 씁니다.)");
            p.sendMessage(ChatColor.GRAY + "(낙하 데미지를 전혀 받지 않습니다.)");
            p.sendMessage(ChatColor.GRAY + "(브리즈 막대로 때리면 상대가 멀리 날아갑니다.)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        doubleJumpUsed = false;
        if (p == null) return;
        // 되돌리지 않으면 능력이 바뀐 뒤에도 비행이 남습니다.
        p.setAllowFlight(false);
        p.setFlying(false);
    }

    /** 안전망: 능력을 가진 동안 비행 허용이 꺼져 있으면(사망/게임모드 변경 등) 다시 켭니다. */
    @Override
    public void onPassiveTick(Player p) {
        if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
        }
    }

    @Override
    public void onToggleFlight(Player p, PlayerToggleFlightEvent event) {
        // 크리에이티브 비행 진입을 막습니다. 우리는 이 이벤트를 "공중에서 스페이스 두 번"의
        // 신호로만 씁니다.
        event.setCancelled(true);
        p.setFlying(false);

        if (doubleJumpUsed) return;

        doubleJumpUsed = true;
        Vector dir = p.getLocation().getDirection().normalize();
        p.setVelocity(new Vector(dir.getX() * DOUBLE_JUMP_FORWARD, DOUBLE_JUMP_UP, dir.getZ() * DOUBLE_JUMP_FORWARD));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1.0f, 1.2f);
    }

    /** 착지하면 더블 점프를 다시 쓸 수 있게 합니다. 헐크가 착지 감지에 쓰는 패턴과 같습니다. */
    @Override
    public void onPlayerMove(Player p, PlayerMoveEvent event) {
        if (doubleJumpUsed && p.isOnGround()) {
            doubleJumpUsed = false;
        }
    }

    /** 낙하 데미지 면역. 상시 적용이라 조건이 없습니다. */
    @Override
    public void onEntityDamage(Player p, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {
        // 주손에 브리즈 막대를 들고 있을 때만 밀쳐냅니다.
        if (!AbilityItems.isHolding(attacker, Material.BREEZE_ROD, ITEM_TAG)) return;
        if (!attacker.equals(event.getDamager())) return; // 발사체 제외

        Entity victim = event.getEntity();
        Vector away = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0e-6) {
            // 정확히 같은 위치면 방향을 정할 수 없습니다. 공격자의 시선 방향으로 밀어냅니다.
            away = attacker.getLocation().getDirection();
            away.setY(0);
        }
        if (away.lengthSquared() < 1.0e-6) return; // 그것도 불가능하면 포기

        away.normalize().multiply(PUSH_HORIZONTAL);
        victim.setVelocity(new Vector(away.getX(), PUSH_UP, away.getZ()));
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.0f);
    }
}
```

- [ ] **Step 2: `AbilityRegistry`에 등록**

`AbilityRegistry.java`의 static 블록에서 `register(Deathwormability::new);` 바로 아래에 삽입:

```java
        register(WindGuideability::new);
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 40 tests

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/abilities/WindGuideability.java src/main/java/org/example/abilities/AbilityRegistry.java
git commit -m "feat: 바람 인도자 능력 추가

밀쳐내기 초기 속도는 설계상 근사값입니다. 도달 거리 15칸 목표로 실측 보정이 필요합니다."
```

---

### Task 14: 윤회자

**Files:**
- Create: `src/main/java/org/example/game/ReincarnatorMath.java`
- Create: `src/main/java/org/example/abilities/Reincarnatorability.java`
- Test: `src/test/java/org/example/game/ReincarnatorMathTest.java`
- Modify: `src/main/java/org/example/abilities/AbilityRegistry.java`
- Modify: `src/main/java/org/example/AbilityManager.java` (`onPlayerJoin` 잔재 정리)

**Interfaces:**
- Consumes: `Cooldown`, `AbilityItems`, `PlayerStats`
- Produces:
  - `static double ReincarnatorMath.jumpStrengthForHeight(double targetHeight)`
  - `static double ReincarnatorMath.knockbackResistanceFor(double multiplier)`
  - `static final double ReincarnatorMath.BASE_JUMP_HEIGHT` / `BASE_JUMP_STRENGTH` / `MIN_JUMP_HEIGHT`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/game/ReincarnatorMathTest.java`:

```java
package org.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReincarnatorMathTest {

    // --- 넉백 매핑 ---
    // KNOCKBACK_RESISTANCE는 이름과 달리 넉백에 (1.0 - resistance)를 곱합니다.

    @Test
    void 넉백_0퍼센트는_저항_1() {
        assertEquals(1.0, ReincarnatorMath.knockbackResistanceFor(0.0), 1e-9);
    }

    @Test
    void 넉백_100퍼센트는_저항_0() {
        assertEquals(0.0, ReincarnatorMath.knockbackResistanceFor(1.0), 1e-9);
    }

    @Test
    void 넉백_300퍼센트는_저항_음수2() {
        assertEquals(-2.0, ReincarnatorMath.knockbackResistanceFor(3.0), 1e-9);
    }

    @Test
    void 넉백_배율이_커지면_저항은_작아진다() {
        assertTrue(ReincarnatorMath.knockbackResistanceFor(2.0) < ReincarnatorMath.knockbackResistanceFor(1.0));
    }

    // --- 점프력 역산 ---
    // JUMP_STRENGTH는 블록 높이가 아니라 초기 속도입니다. 높이는 속도의 제곱에 비례합니다.

    @Test
    void 기본_높이면_기본_점프력이_나온다() {
        double s = ReincarnatorMath.jumpStrengthForHeight(ReincarnatorMath.BASE_JUMP_HEIGHT);
        assertEquals(ReincarnatorMath.BASE_JUMP_STRENGTH, s, 1e-9);
    }

    @Test
    void 높이가_4배면_점프력은_2배() {
        // 높이 ~ 속도^2 이므로 4배 높이는 2배 속도
        double s = ReincarnatorMath.jumpStrengthForHeight(ReincarnatorMath.BASE_JUMP_HEIGHT * 4);
        assertEquals(ReincarnatorMath.BASE_JUMP_STRENGTH * 2, s, 1e-9);
    }

    @Test
    void 높이가_올라가면_점프력도_올라간다() {
        double low = ReincarnatorMath.jumpStrengthForHeight(1.0);
        double high = ReincarnatorMath.jumpStrengthForHeight(3.0);
        assertTrue(high > low);
    }

    @Test
    void 음수_높이는_하한으로_클램프된다() {
        // 명세의 -2블록은 기본 1.25에서 빼면 음수가 됩니다. 점프가 아예 불가능해지면
        // 지형에 갇혀 게임이 끝나므로 "거의 못 뛴다"까지만 허용합니다.
        double s = ReincarnatorMath.jumpStrengthForHeight(-0.75);
        double expected = ReincarnatorMath.jumpStrengthForHeight(ReincarnatorMath.MIN_JUMP_HEIGHT);
        assertEquals(expected, s, 1e-9);
        assertTrue(s > 0, "점프력이 0 이하가 되면 점프가 불가능해집니다");
    }

    @Test
    void 하한_미만은_전부_같은_값() {
        assertEquals(ReincarnatorMath.jumpStrengthForHeight(-100),
                ReincarnatorMath.jumpStrengthForHeight(0.0), 1e-9);
    }

    @Test
    void 명세_범위의_경계값이_전부_양수() {
        // 목표높이 = 1.25 + random(-2.0, +2.0) 이므로 -0.75 ~ 3.25
        for (double h : new double[]{-0.75, 0.0, 1.25, 3.25}) {
            assertTrue(ReincarnatorMath.jumpStrengthForHeight(h) > 0, "높이 " + h + "에서 점프력이 0 이하");
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class ReincarnatorMath`

- [ ] **Step 3: `ReincarnatorMath` 작성**

`src/main/java/org/example/game/ReincarnatorMath.java`:

```java
package org.example.game;

/**
 * 윤회자의 스탯 변환. Bukkit에 의존하지 않는 순수 계산이라 서버 없이 테스트할 수 있습니다.
 */
public final class ReincarnatorMath {

    /** 바닐라 기본 JUMP_STRENGTH. 초기 속도이며 블록 높이가 아닙니다. */
    public static final double BASE_JUMP_STRENGTH = 0.42;

    /** BASE_JUMP_STRENGTH로 실제로 올라가는 높이(블록). */
    public static final double BASE_JUMP_HEIGHT = 1.25;

    /**
     * 점프 높이 하한. 명세의 -2블록은 기본 1.25에서 빼면 음수가 되는데, 점프가 아예
     * 불가능해지면 지형에 갇혀 게임이 끝납니다. "거의 못 뛴다"까지만 허용합니다.
     */
    public static final double MIN_JUMP_HEIGHT = 0.25;

    private ReincarnatorMath() {
    }

    /**
     * 목표 높이(블록)에 필요한 JUMP_STRENGTH를 역산합니다.
     *
     * JUMP_STRENGTH는 높이가 아니라 초기 속도라서 선형 대응이 안 됩니다.
     * 높이는 속도의 제곱에 비례하므로 sqrt로 뒤집습니다.
     */
    public static double jumpStrengthForHeight(double targetHeight) {
        double h = Math.max(targetHeight, MIN_JUMP_HEIGHT);
        return BASE_JUMP_STRENGTH * Math.sqrt(h / BASE_JUMP_HEIGHT);
    }

    /**
     * 넉백 배율을 KNOCKBACK_RESISTANCE 값으로 바꿉니다.
     *
     * KNOCKBACK_RESISTANCE는 이름과 달리 넉백에 (1.0 - resistance)를 곱합니다. 따라서:
     *   넉백 0%(철벽)   -> resistance  1.0
     *   넉백 100%(기본) -> resistance  0.0
     *   넉백 300%(종이) -> resistance -2.0
     *
     * 음수 저항이 실제로 넉백을 증폭시키는지는 서버 실측이 필요합니다.
     * 증폭되지 않으면 setVelocity 기반 대체 구현으로 전환합니다.
     */
    public static double knockbackResistanceFor(double multiplier) {
        return 1.0 - multiplier;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 50 tests (기존 40 + 신규 10)

- [ ] **Step 5: `Reincarnatorability` 작성**

`src/main/java/org/example/abilities/Reincarnatorability.java`:

```java
package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.PlayerStats;
import org.example.game.ReincarnatorMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 윤회자: 순수한 도박. 매 판, 매 회귀마다 완전히 다른 캐릭터가 됩니다.
 *
 * 무작위 효과 3종에서 제외는 없습니다 - 사용자의 명시적 결정입니다. 그 결과:
 *   - WITHER가 뽑히면 회귀 쿨(3분) 전에 확정 사망합니다. 회복 수단이 없습니다.
 *   - INSTANT_DAMAGE는 즉발이라 무한 지속을 걸어도 부여 순간 1회만 적용됩니다.
 *   - POISON은 체력 1에서 멈추므로 단독으로는 죽이지 않습니다.
 *
 * 이건 버그가 아니라 설계된 리스크입니다. "시들음이 걸려서 죽는데요"를 버그로 오인해
 * 고치지 마세요.
 */
public class Reincarnatorability implements Ability {

    private static final String ITEM_TAG = "[능력] 회귀";
    private static final long COOLDOWN_MS = 180000; // 3분
    private static final int EFFECT_COUNT = 3;

    private static final double MAX_HEALTH_MIN = 2.0;
    private static final double MAX_HEALTH_MAX = 40.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_MS);
    private final Random random = new Random();
    private final List<PotionEffectType> activeEffects = new ArrayList<>();

    @Override
    public String getName() {
        return "윤회자";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    /** 이 능력이 거는 모든 AttributeModifier의 키. onRevoke와 재굴림에서 이 목록으로 지웁니다. */
    private static NamespacedKey key(String suffix) {
        return new NamespacedKey(JavaPlugin.getProvidingPlugin(Reincarnatorability.class), "reincarnator_" + suffix);
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(AbilityItems.create(Material.ECHO_SHARD, ChatColor.LIGHT_PURPLE, ITEM_TAG));

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(무작위 효과 3종과 무작위 스탯 6종을 갖고 시작합니다.)");
            p.sendMessage(ChatColor.GRAY + "(메아리 파편 우클릭 시 전부 다시 굴립니다. 쿨타임 3분.)");
            p.sendMessage(ChatColor.RED + "(경고: 시들음이 뽑히면 죽습니다. 그것이 이 능력입니다.)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]이(가) 적용되었습니다.");
        }
        reroll(p);
    }

    @Override
    public void onRevoke(Player p) {
        if (p == null) return;
        clearAll(p);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AbilityItems.isHolding(p, Material.ECHO_SHARD, ITEM_TAG)) return;

        event.setCancelled(true);
        if (!cooldown.tryUse(p, "아직 윤회할 수 없습니다!")) return;

        reroll(p);
    }

    /** 제거 -> 재적용 순서를 반드시 지킵니다. 모디파이어를 안 지우고 또 걸면 중첩됩니다. */
    private void reroll(Player p) {
        clearAll(p);
        rollEffects(p);
        rollStats(p);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "윤회했습니다. " + ChatColor.GRAY + "완전히 다른 존재가 되었습니다.");
    }

    private void clearAll(Player p) {
        for (PotionEffectType t : activeEffects) {
            p.removePotionEffect(t);
        }
        activeEffects.clear();
        removeAllModifiers(p);
        PlayerStats.resetMaxHealth(p);
    }

    // --- 효과 3종 ---

    private void rollEffects(Player p) {
        List<PotionEffectType> pool = new ArrayList<>();
        for (PotionEffectType t : Registry.MOB_EFFECT) {
            pool.add(t);
        }
        Collections.shuffle(pool, random);

        p.sendMessage(ChatColor.GRAY + "  효과:");
        for (int i = 0; i < Math.min(EFFECT_COUNT, pool.size()); i++) {
            PotionEffectType t = pool.get(i);
            // 무한 지속의 예외적 허용: 이 코드베이스는 무한 지속 포션을 금기시하지만
            // (티모 영구 투명 버그), 윤회자는 명세가 무한 지속을 요구하고 능력이 살아 있는
            // 동안 유지돼야 하므로 다른 선택지가 없습니다. 대신 onRevoke와
            // AbilityManager.onPlayerJoin 양쪽에서 제거합니다.
            p.addPotionEffect(new PotionEffect(t, PotionEffect.INFINITE_DURATION, 0, false, false, true), true);
            activeEffects.add(t);
            p.sendMessage(ChatColor.GRAY + "    - " + t.getKey().getKey());
        }
    }

    // --- 스탯 6종 ---

    private void rollStats(Player p) {
        p.sendMessage(ChatColor.GRAY + "  스탯:");

        double range = randomIn(-1.5, 1.5);
        addModifier(p, Attribute.ENTITY_INTERACTION_RANGE, key("range"), range,
                AttributeModifier.Operation.ADD_NUMBER);
        p.sendMessage(ChatColor.GRAY + String.format("    - 공격 사거리 %+.2f", range));

        double dmg = randomIn(-0.5, 0.5);
        addModifier(p, Attribute.ATTACK_DAMAGE, key("damage"), dmg,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        p.sendMessage(ChatColor.GRAY + String.format("    - 근접 대미지 %+.0f%%", dmg * 100));

        double scale = randomIn(-0.5, 0.5);
        addModifier(p, Attribute.SCALE, key("scale"), scale,
                AttributeModifier.Operation.ADD_NUMBER);
        p.sendMessage(ChatColor.GRAY + String.format("    - 크기 %.2f배", 1.0 + scale));

        double speed = randomIn(-0.4, 0.4);
        addModifier(p, Attribute.MOVEMENT_SPEED, key("speed"), speed,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        p.sendMessage(ChatColor.GRAY + String.format("    - 이동 속도 %+.0f%%", speed * 100));

        // 점프: 목표 높이를 뽑아 JUMP_STRENGTH로 역산합니다(높이 ~ 속도^2 이라 선형이 아님).
        double targetHeight = ReincarnatorMath.BASE_JUMP_HEIGHT + randomIn(-2.0, 2.0);
        double jump = ReincarnatorMath.jumpStrengthForHeight(targetHeight);
        setAttributeBase(p, Attribute.JUMP_STRENGTH, jump);
        p.sendMessage(ChatColor.GRAY + String.format("    - 점프 높이 약 %.2f블록", Math.max(targetHeight, ReincarnatorMath.MIN_JUMP_HEIGHT)));

        double knockMultiplier = randomIn(0.0, 3.0);
        double resistance = ReincarnatorMath.knockbackResistanceFor(knockMultiplier);
        addModifier(p, Attribute.KNOCKBACK_RESISTANCE, key("knockback"), resistance,
                AttributeModifier.Operation.ADD_NUMBER);
        p.sendMessage(ChatColor.GRAY + String.format("    - 피격 넉백 %.0f%%", knockMultiplier * 100));

        // 최대 체력은 AttributeModifier가 아니라 PlayerStats를 씁니다. 헐크가 이미 쓰는
        // 경로이고, 체력 클램프를 함께 처리해줍니다.
        double maxHealth = randomIn(MAX_HEALTH_MIN, MAX_HEALTH_MAX);
        PlayerStats.setMaxHealth(p, maxHealth);
        p.sendMessage(ChatColor.GRAY + String.format("    - 최대 체력 하트 %.0f개", maxHealth / 2));
    }

    private double randomIn(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private void addModifier(Player p, Attribute attr, NamespacedKey k, double amount,
                             AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.addModifier(new AttributeModifier(k, amount, op));
    }

    private void setAttributeBase(Player p, Attribute attr, double value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.setBaseValue(value);
    }

    /**
     * 이 능력이 건 모디파이어를 전부 제거합니다.
     * SCALE 누락은 특히 위험합니다 - 히트박스가 0.5배인 채로 남으면 다음 판까지 영향이 갑니다.
     */
    private void removeAllModifiers(Player p) {
        removeModifier(p, Attribute.ENTITY_INTERACTION_RANGE, key("range"));
        removeModifier(p, Attribute.ATTACK_DAMAGE, key("damage"));
        removeModifier(p, Attribute.SCALE, key("scale"));
        removeModifier(p, Attribute.MOVEMENT_SPEED, key("speed"));
        removeModifier(p, Attribute.KNOCKBACK_RESISTANCE, key("knockback"));

        // 점프는 base value를 바꿨으므로 기본값으로 되돌립니다.
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.setBaseValue(jump.getDefaultValue());
    }

    private void removeModifier(Player p, Attribute attr, NamespacedKey k) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (k.equals(m.getKey())) inst.removeModifier(m);
        }
    }

    /**
     * 오프라인 중 능력이 정리된 경우 재접속 시점이 유일한 복구 지점입니다.
     * AbilityManager.onPlayerJoin이 호출합니다.
     */
    public static void cleanupResidue(Player p) {
        if (p == null) return;
        for (PotionEffectType t : Registry.MOB_EFFECT) {
            PotionEffect e = p.getPotionEffect(t);
            // 이 능력만 무한 지속을 겁니다. 다른 능력의 유한 효과는 건드리지 않습니다.
            if (e != null && e.getDuration() == PotionEffect.INFINITE_DURATION) {
                p.removePotionEffect(t);
            }
        }
        for (String suffix : new String[]{"range", "damage", "scale", "speed", "knockback"}) {
            NamespacedKey k = key(suffix);
            for (Attribute attr : new Attribute[]{Attribute.ENTITY_INTERACTION_RANGE, Attribute.ATTACK_DAMAGE,
                    Attribute.SCALE, Attribute.MOVEMENT_SPEED, Attribute.KNOCKBACK_RESISTANCE}) {
                AttributeInstance inst = p.getAttribute(attr);
                if (inst == null) continue;
                for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                    if (k.equals(m.getKey())) inst.removeModifier(m);
                }
            }
        }
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.setBaseValue(jump.getDefaultValue());
    }
}
```

- [ ] **Step 6: `AbilityManager.onPlayerJoin`에 윤회자 잔재 정리 추가**

`AbilityManager.java`의 `onPlayerJoin`에서 다음을 찾아:

```java
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // 능력이 없는데 은신 상태로 남아 있으면 잔재입니다.
        Vanish.show(plugin, p);
```

바로 아래에 삽입:

```java
        // 윤회자는 무한 지속 효과와 AttributeModifier를 겁니다. 오프라인 중 능력이
        // 정리됐다면 여기가 유일한 복구 지점입니다(기존 최대체력 정리와 같은 이유).
        Reincarnatorability.cleanupResidue(p);
```

import 추가:

```java
import org.example.abilities.Reincarnatorability;
```

- [ ] **Step 7: `AbilityRegistry`에 등록**

`AbilityRegistry.java`의 static 블록에서 `register(WindGuideability::new);` 바로 아래에 삽입:

```java
        register(Reincarnatorability::new);
```

- [ ] **Step 8: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 50 tests

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/org/example/game/ReincarnatorMath.java src/test/java/org/example/game/ReincarnatorMathTest.java src/main/java/org/example/abilities/Reincarnatorability.java src/main/java/org/example/abilities/AbilityRegistry.java src/main/java/org/example/AbilityManager.java
git commit -m "feat: 윤회자 능력 추가

무작위 효과 3종(제외 없음 - 시들음 포함) + 무작위 스탯 6종. 점프력 역산과
넉백 매핑은 순수 함수로 분리해 테스트했습니다."
```

---

### Task 15: AbilityRegistry 등록 확인 테스트

**Files:**
- Test: `src/test/java/org/example/abilities/AbilityRegistryTest.java`

**Interfaces:**
- Consumes: `AbilityRegistry.getNames()`, `AbilityRegistry.create(String)`, `AbilityRegistry.isValid(String)`
- Produces: 없음

**중요한 제약:** `AbilityRegistry`의 static 블록은 각 능력의 생성자를 호출해 `getName()`을 읽는다. 능력 생성자가 Bukkit API를 건드리면 클래스 초기화가 실패한다. 이 테스트는 그것도 함께 잡는다.

- [ ] **Step 1: 테스트 작성**

`src/test/java/org/example/abilities/AbilityRegistryTest.java`:

```java
package org.example.abilities;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbilityRegistry의 static 블록은 각 능력의 생성자를 호출해 getName()을 읽습니다.
 * 능력 생성자가 Bukkit API를 건드리면 여기서 ExceptionInInitializerError가 납니다.
 * 이 테스트는 그것도 함께 잡습니다.
 */
class AbilityRegistryTest {

    private static final List<String> EXPECTED = List.of(
            "블링커", "헐크", "포세이돈", "사망회귀", "네크로맨서", "티모",
            "마우가", "암살자", "데스웜", "바람 인도자", "윤회자");

    @Test
    void 능력이_11종_등록되어_있다() {
        assertEquals(11, AbilityRegistry.getNames().length);
    }

    @Test
    void 기대한_이름이_전부_있다() {
        Set<String> actual = new HashSet<>(Arrays.asList(AbilityRegistry.getNames()));
        for (String name : EXPECTED) {
            assertTrue(actual.contains(name), "등록되지 않은 능력: " + name);
        }
    }

    @Test
    void 이름이_중복되지_않는다() {
        String[] names = AbilityRegistry.getNames();
        assertEquals(names.length, new HashSet<>(Arrays.asList(names)).size(),
                "이름이 겹치면 레지스트리가 하나를 덮어씁니다: " + Arrays.toString(names));
    }

    @Test
    void create가_각각_새_인스턴스를_반환한다() {
        // 인스턴스를 공유하면 쿨타임/소환물 상태가 플레이어 간에 섞입니다.
        for (String name : AbilityRegistry.getNames()) {
            Ability a = AbilityRegistry.create(name);
            Ability b = AbilityRegistry.create(name);
            assertNotNull(a, name + " 생성 실패");
            assertNotNull(b, name + " 생성 실패");
            assertNotSame(a, b, name + "이 같은 인스턴스를 재사용합니다");
        }
    }

    @Test
    void create한_능력의_이름이_등록된_이름과_같다() {
        for (String name : AbilityRegistry.getNames()) {
            assertEquals(name, AbilityRegistry.create(name).getName());
        }
    }

    @Test
    void 없는_이름은_null() {
        assertNotNull(AbilityRegistry.getNames());
        assertEquals(null, AbilityRegistry.create("존재하지않는능력"));
    }

    @Test
    void isValid가_등록된_이름에만_true() {
        for (String name : AbilityRegistry.getNames()) {
            assertTrue(AbilityRegistry.isValid(name), name);
        }
        assertFalse(AbilityRegistry.isValid("존재하지않는능력"));
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 57 tests (기존 50 + 신규 7)

**만약 `ExceptionInInitializerError`가 난다면** 어떤 능력의 생성자가 Bukkit을 건드리는 것이다. 그 능력의 필드 초기화를 `onGrant` 시점으로 미뤄야 한다. 이 경우 보고하고 멈춘다.

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/org/example/abilities/AbilityRegistryTest.java
git commit -m "test: AbilityRegistry에 11종이 등록되고 각각 새 인스턴스를 내는지 확인"
```

---

### Task 16: 서버 실측 검증

**Files:** 없음 (서버에서 확인)

**Interfaces:**
- Consumes: 전체
- Produces: 없음

스펙 §9는 **jar 검증으로 해소되지 않는 런타임 가정 4개**를 명시한다. 단위 테스트로는 잡히지 않는다.

- [ ] **Step 1: jar 빌드 및 배포**

Run: `./gradlew clean build --console=plain`
Expected: BUILD SUCCESSFUL

jar를 서버 `plugins/`에 복사하고 **서버 완전 재시작**.

- [ ] **Step 2: 테스트 환경 설정**

```
/쿨타임 on
/게임설정 평화시간(초) 600
/게임시작
```

- [ ] **Step 3: 런타임 가정 4개 확인**

| # | 가정 | 확인 방법 | 실패 시 |
|---|---|---|---|
| 1 | 헐크 낙하 데미지 버그가 고쳐졌다 | `/능력변경 <이름> 헐크` → 높은 곳에서 액티브 → **체력 불변** | 이벤트 우선순위 조정 |
| 2 | 음수 `KNOCKBACK_RESISTANCE`가 넉백을 증폭한다 | 윤회자로 넉백 300%가 나올 때까지 회귀 → 피격 시 평소보다 멀리 날아가는지 | `setVelocity` 기반 구현으로 전환 |
| 3 | 바람 인도자 밀쳐내기가 약 15칸 | 평지에서 타격 → 좌표 차이 측정 | `PUSH_HORIZONTAL` 보정 후 **실측값을 주석에 기록** |
| 4 | `setAllowFlight(true)`가 `/관전`과 충돌하지 않는다 | 바람 인도자로 사망 → 관전 모드 정상 진입/이동 확인 | 관전 진입 시 플래그 해제 |

- [ ] **Step 4: 신규 5종 동작 확인**

| 능력 | 확인 |
|---|---|
| 마우가 | 우클릭 돌진(무적). 벽 충돌/좌클릭/3초로 종료 → 폭발 + 띄우기 → 착지 시 구속. 20초간 느려짐. 근접 시 30% 회복/흡수하트 |
| 암살자 | 우클릭 5초 완전 투명(**갑옷과 손아이템도 안 보여야 함**). 공격 시 2배(등 뒤 2.5배) + 즉시 해제 |
| 데스웜 | **모래 밖에서 우클릭 → 쿨 소모 없이 거부**. 모래 위 우클릭 → 잠행. 모래 벗어나면 실패(쿨은 소모됨). 5초 후/공격 시 습격 |
| 바람 인도자 | 공중 스페이스 2번 → 더블 점프. 착지 후 재사용. 낙하 데미지 0. 막대로 때리면 멀리 날아감 |
| 윤회자 | 효과 3종 + 스탯 6종이 채팅에 요약됨. 우클릭 시 재굴림(중첩되지 않아야 함). **크기(SCALE) 변화가 눈에 보여야 함** |

- [ ] **Step 5: 정리 확인 (가장 중요)**

각 능력에서 `/능력변경 <이름> 포세이돈`으로 바꾼 뒤 잔재가 남지 않는지 확인한다.

| 확인 | 기대 |
|---|---|
| 마우가 → 다른 능력 | 이동속도 정상. 저항 없음 |
| 암살자 은신 중 → 다른 능력 | **투명 해제됨** |
| 데스웜 잠행 중 → 다른 능력 | 투명 해제됨 |
| 바람 인도자 → 다른 능력 | **비행 불가로 돌아옴** |
| 윤회자 → 다른 능력 | **크기 1.0배 복귀**, 효과 전부 제거, 최대 체력 20 |
| 윤회자 상태로 접속 종료 → 게임 종료 → 재접속 | 효과/스탯 잔재 없음 |

- [ ] **Step 6: 설정 원복 및 실측값 반영**

```
/쿨타임 off
/게임설정 평화시간(초) 720
```

3번(밀쳐내기 거리)의 실측 결과로 `WindGuideability.PUSH_HORIZONTAL`을 보정하고, **측정한 값을 주석에 기록한 뒤 커밋**한다.

- [ ] **Step 7: 문제 기록**

발견한 문제를 이 파일 하단에 적고 해당 Task로 돌아간다.
