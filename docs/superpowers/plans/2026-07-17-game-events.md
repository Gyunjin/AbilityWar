# AbilityWar 이벤트 시스템 + 정보 공개 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 5분마다 무작위 이벤트가 발동하고, 능력 정보가 단계적으로 공개되는 시스템을 추가해 매 판이 다르게 전개되도록 한다.

**Architecture:** `GameEvent`(3-메서드 인터페이스) 구현체들을 `GameEventManager`가 보유한 목록에서 추첨해 발동한다. 매니저는 `Main.gameTimer`에서 1초마다 `tick()`을 받는다(새 스케줄러 없음). 모든 이벤트는 순간 이벤트라 지속 추적이 없고, 스폰물 정리는 게임 종료 시 PDC 스윕 한 곳에서만 한다. 검증 가능한 순수 로직(배정·추첨·최다킬러)은 Bukkit 타입을 쓰지 않는 클래스로 분리해 JUnit으로 잠근다.

**Tech Stack:** Java 25, Paper API 26.1.2.build.74-stable (compileOnly), JUnit 5.12.2, Gradle 9.3.

## Global Constraints

- **Paper API 좌표:** `io.papermc.paper:paper-api:26.1.2.build.74-stable` — `compileOnly`. 절대 `implementation`으로 바꾸지 말 것(서버가 런타임에 제공).
- **Java 25** 툴체인. `gradle.properties`의 `org.gradle.java.installations.paths=F:\tools\java\jdk\corretto-25`가 JDK를 찾아준다.
- **deprecated API 금지:** `setPVP/getPVP` 대신 `GameRules.setPvp/isPvp`, `setMaxHealth` 대신 `PlayerStats`. `ChatColor`와 `Bukkit.broadcastMessage`는 이 프로젝트에서 여전히 사용한다(B그룹 마이그레이션은 범위 밖 — 기존 코드와 일관성 유지).
- **엔티티 표식은 PDC만.** `FixedMetadataValue`는 저장되지 않아 재시작 후 제거 불가능해진다(네크로맨서 좀비 사고 원인).
- **포션 효과는 유한 지속시간만.** 무한(`Integer.MAX_VALUE`) 금지 — 해제 실패 시 영구히 남는다(티모 사고 원인).
- **플레이어 이동은 반드시 `HealthBarListener.safeTeleport()`.** `p.teleport()`는 체력바 마커(탑승물) 때문에 조용히 실패한다.
- **순수 로직 클래스에는 Bukkit import를 넣지 않는다.** 테스트 가능성이 거기서 나온다.
- **테스트 명령:** `./gradlew test --console=plain`
- **빌드 명령:** `./gradlew build --console=plain`

## File Structure

**신규 (순수 로직 — Bukkit 없음, 테스트 대상)**
- `src/main/java/org/example/game/AbilityAssigner.java` — 중복 없는 능력 배정
- `src/main/java/org/example/game/EventPicker.java` — 이벤트 추첨(연속 중복 방지)
- `src/main/java/org/example/game/BountySelector.java` — 최다 킬러 선정

**신규 (이벤트 시스템 — Bukkit 사용)**
- `src/main/java/org/example/events/GameEvent.java`
- `src/main/java/org/example/events/GameContext.java`
- `src/main/java/org/example/events/GameEventManager.java`
- `src/main/java/org/example/events/EventSpawns.java` — PDC 표식/스윕 공용
- `src/main/java/org/example/events/SupplyDropEvent.java`
- `src/main/java/org/example/events/NightRushEvent.java`
- `src/main/java/org/example/events/AbilityRechargeEvent.java`
- `src/main/java/org/example/events/GlowingEvent.java`
- `src/main/java/org/example/events/BountyEvent.java`

**수정**
- `src/main/java/org/example/abilities/Ability.java` — `resetCooldown()` 훅 추가
- `src/main/java/org/example/abilities/{Blinker,Hulk,Necromancer,Teemo}ability.java` — `resetCooldown()` 구현
- `src/main/java/org/example/AbilityManager.java` — 중복 없는 배정, 쿨타임 초기화 위임, 능력 목록 조회
- `src/main/java/org/example/Main.java` — 매니저 연결, 설정 항목, 능력 공개 2종

**테스트**
- `src/test/java/org/example/game/AbilityAssignerTest.java`
- `src/test/java/org/example/game/EventPickerTest.java`
- `src/test/java/org/example/game/BountySelectorTest.java`

---

### Task 1: 중복 없는 능력 배정

**Files:**
- Create: `src/main/java/org/example/game/AbilityAssigner.java`
- Test: `src/test/java/org/example/game/AbilityAssignerTest.java`
- Modify: `src/main/java/org/example/AbilityManager.java` (`assignAbilitiesIfNone`)

**Interfaces:**
- Consumes: 없음
- Produces: `AbilityAssigner.assign(List<String> pool, int count, Random random)` → `List<String>` (크기 = count)

현재 `assignAbilitiesIfNone`은 플레이어마다 `AbilityRegistry.randomName()`을 독립 호출해 같은 능력이 중복 배정된다. 소거법 심리전(Task 8)의 전제가 무너지므로 먼저 고친다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/game/AbilityAssignerTest.java`:

```java
package org.example.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityAssignerTest {

    private static final List<String> POOL = List.of("블링커", "헐크", "포세이돈", "사망회귀", "네크로맨서", "티모");

    @Test
    void 인원이_풀보다_적으면_전원_다른_능력() {
        List<String> result = AbilityAssigner.assign(POOL, 5, new Random(1));

        assertEquals(5, result.size());
        assertEquals(5, new HashSet<>(result).size(), "중복이 있으면 안 됩니다: " + result);
        assertTrue(POOL.containsAll(result));
    }

    @Test
    void 인원이_풀과_같으면_전원_다른_능력() {
        List<String> result = AbilityAssigner.assign(POOL, 6, new Random(2));

        assertEquals(6, result.size());
        assertEquals(6, new HashSet<>(result).size());
    }

    @Test
    void 인원이_풀을_초과하면_초과분만_중복_허용() {
        List<String> result = AbilityAssigner.assign(POOL, 8, new Random(3));

        assertEquals(8, result.size());
        // 6종이 최소 한 번씩은 모두 등장해야 한다 (초과분 2명만 중복)
        assertEquals(6, new HashSet<>(result).size());
    }

    @Test
    void 배정_순서가_매번_같지_않다() {
        List<String> a = AbilityAssigner.assign(POOL, 6, new Random(1));
        List<String> b = AbilityAssigner.assign(POOL, 6, new Random(999));

        assertTrue(!a.equals(b), "서로 다른 시드인데 순서가 같습니다");
    }

    @Test
    void 원본_풀을_변경하지_않는다() {
        List<String> pool = new ArrayList<>(POOL);
        AbilityAssigner.assign(pool, 6, new Random(1));

        assertEquals(POOL, pool);
    }

    @Test
    void 인원이_0이면_빈_목록() {
        assertEquals(List.of(), AbilityAssigner.assign(POOL, 0, new Random(1)));
    }

    @Test
    void 풀이_비어있으면_빈_목록() {
        Set<String> empty = new HashSet<>();
        assertEquals(List.of(), AbilityAssigner.assign(List.copyOf(empty), 3, new Random(1)));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class AbilityAssigner`

- [ ] **Step 3: 최소 구현**

`src/main/java/org/example/game/AbilityAssigner.java`:

```java
package org.example.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 능력을 플레이어 수만큼 배정합니다. Bukkit에 의존하지 않는 순수 로직이라
 * 서버 없이 테스트할 수 있습니다.
 *
 * 중복 없이 배정하는 이유: 사망 시 능력 공개와 맞물려 소거법이 성립해야 하는데,
 * 같은 능력이 여러 명에게 가면 "티모가 죽었다"를 알아도 아무것도 좁힐 수 없습니다.
 */
public final class AbilityAssigner {

    private AbilityAssigner() {
    }

    /**
     * @param pool   배정 가능한 능력 이름 목록 (변경하지 않음)
     * @param count  배정할 인원 수
     * @return 크기가 count인 능력 이름 목록. 인원이 풀보다 많으면 풀을 다 쓴 뒤
     *         초과분만 다시 섞어 이어 붙이므로, 모든 능력이 최소 한 번씩 등장합니다.
     */
    public static List<String> assign(List<String> pool, int count, Random random) {
        if (pool == null || pool.isEmpty() || count <= 0) return List.of();

        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            List<String> round = new ArrayList<>(pool);
            Collections.shuffle(round, random);
            for (String name : round) {
                if (result.size() >= count) break;
                result.add(name);
            }
        }
        return result;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: `AbilityManager`가 이 로직을 쓰도록 수정**

`src/main/java/org/example/AbilityManager.java`의 `assignAbilitiesIfNone`을 통째로 교체:

```java
    /** 게임 시작 시 호출. 아직 능력이 없는 플레이어에게만 무작위 배정합니다. */
    public void assignAbilitiesIfNone(Collection<? extends Player> players) {
        // 능력이 없는 플레이어를 먼저 모읍니다. 이들에게만 중복 없이 배정합니다.
        List<Player> needAbility = new ArrayList<>();
        for (Player p : players) {
            if (!playerAbilities.containsKey(p.getUniqueId())) {
                needAbility.add(p);
            }
        }

        List<String> assigned = AbilityAssigner.assign(
                List.of(AbilityRegistry.getNames()), needAbility.size(), new Random());

        for (int i = 0; i < needAbility.size(); i++) {
            Player p = needAbility.get(i);
            Ability ability = AbilityRegistry.create(assigned.get(i));
            if (ability == null) continue;
            playerAbilities.put(p.getUniqueId(), ability);
            ability.onGrant(p, false);
        }

        // 이미 능력이 있는 플레이어는 같은 능력으로 새 인스턴스를 만들어 초기화한 뒤
        // 장비만 재지급합니다. 기존 인스턴스를 그대로 쓰면 소환물·쿨타임 상태가 남습니다.
        for (Player p : players) {
            Ability existing = playerAbilities.get(p.getUniqueId());
            if (existing == null || needAbility.contains(p)) continue;

            String abilityName = existing.getName();
            existing.onRevoke(p);
            Ability fresh = AbilityRegistry.create(abilityName);
            if (fresh != null) {
                playerAbilities.put(p.getUniqueId(), fresh);
                fresh.onGrant(p, true);
            }
        }
    }
```

import 추가:

```java
import org.example.game.AbilityAssigner;
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/org/example/game/AbilityAssigner.java src/test/java/org/example/game/AbilityAssignerTest.java src/main/java/org/example/AbilityManager.java
git commit -m "feat: 능력을 중복 없이 배정

3~6명 게임에서 같은 능력이 여러 명에게 가던 문제를 고칩니다.
사망 시 능력 공개와 맞물린 소거법 심리전의 전제입니다."
```

---

### Task 2: 이벤트 추첨 로직

**Files:**
- Create: `src/main/java/org/example/game/EventPicker.java`
- Test: `src/test/java/org/example/game/EventPickerTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `EventPicker.pick(List<String> candidates, String lastPicked, Random random)` → `String` (후보 없으면 `null`)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/game/EventPickerTest.java`:

```java
package org.example.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPickerTest {

    @Test
    void 후보가_없으면_null() {
        assertNull(EventPicker.pick(List.of(), null, new Random(1)));
    }

    @Test
    void 후보가_하나면_직전과_같아도_그것을_반환() {
        // 대안이 없으면 연속 중복을 허용해야 합니다. null을 반환하면 이벤트가 영영 안 나옵니다.
        assertEquals("보급 투하", EventPicker.pick(List.of("보급 투하"), "보급 투하", new Random(1)));
    }

    @Test
    void 직전에_나온_것은_뽑지_않는다() {
        List<String> candidates = List.of("보급 투하", "사령의 밤", "능력 재충전");

        for (int seed = 0; seed < 200; seed++) {
            String picked = EventPicker.pick(candidates, "사령의 밤", new Random(seed));
            assertNotEquals("사령의 밤", picked, "seed=" + seed + "에서 직전 이벤트가 다시 뽑혔습니다");
        }
    }

    @Test
    void 직전이_null이면_전체에서_뽑는다() {
        List<String> candidates = List.of("A", "B", "C");
        Set<String> seen = new HashSet<>();

        for (int seed = 0; seed < 200; seed++) {
            seen.add(EventPicker.pick(candidates, null, new Random(seed)));
        }
        assertEquals(Set.of("A", "B", "C"), seen, "충분히 돌렸는데 안 뽑힌 후보가 있습니다");
    }

    @Test
    void 남은_후보_전부가_뽑힐_수_있다() {
        List<String> candidates = List.of("A", "B", "C");
        Set<String> seen = new HashSet<>();

        for (int seed = 0; seed < 200; seed++) {
            seen.add(EventPicker.pick(candidates, "A", new Random(seed)));
        }
        assertEquals(Set.of("B", "C"), seen);
    }

    @Test
    void 직전이_후보에_없어도_정상_동작() {
        String picked = EventPicker.pick(List.of("A", "B"), "없는이벤트", new Random(1));
        assertTrue(List.of("A", "B").contains(picked));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class EventPicker`

- [ ] **Step 3: 최소 구현**

`src/main/java/org/example/game/EventPicker.java`:

```java
package org.example.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 이벤트 후보 중 하나를 뽑습니다. Bukkit에 의존하지 않는 순수 로직입니다.
 *
 * 같은 이벤트가 연속으로 나오면 "무작위"가 고장 난 것처럼 보이므로 직전에 나온 것은
 * 제외합니다. 다만 후보가 그것 하나뿐이면 제외하지 않습니다 - 제외해버리면
 * 이벤트가 영영 발동하지 않습니다.
 */
public final class EventPicker {

    private EventPicker() {
    }

    /**
     * @param candidates 발동 가능한 이벤트 이름 목록
     * @param lastPicked 직전에 발동한 이벤트 이름 (없으면 null)
     * @return 뽑힌 이름. 후보가 비어있으면 null.
     */
    public static String pick(List<String> candidates, String lastPicked, Random random) {
        if (candidates == null || candidates.isEmpty()) return null;

        List<String> pool = new ArrayList<>(candidates);
        if (pool.size() > 1) {
            pool.remove(lastPicked);
        }
        return pool.get(random.nextInt(pool.size()));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/game/EventPicker.java src/test/java/org/example/game/EventPickerTest.java
git commit -m "feat: 이벤트 추첨 로직 (연속 중복 방지)"
```

---

### Task 3: 최다 킬러 선정

**Files:**
- Create: `src/main/java/org/example/game/BountySelector.java`
- Test: `src/test/java/org/example/game/BountySelectorTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `BountySelector.topKiller(Map<UUID, Integer> kills, Set<UUID> alive)` → `UUID` (없으면 `null`)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/game/BountySelectorTest.java`:

```java
package org.example.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountySelectorTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void 킬이_가장_많은_생존자를_반환() {
        Map<UUID, Integer> kills = Map.of(A, 1, B, 3, C, 2);
        assertEquals(B, BountySelector.topKiller(kills, Set.of(A, B, C)));
    }

    @Test
    void 전원_0킬이면_null() {
        Map<UUID, Integer> kills = Map.of(A, 0, B, 0);
        assertNull(BountySelector.topKiller(kills, Set.of(A, B)));
    }

    @Test
    void 킬_기록이_아예_없으면_null() {
        assertNull(BountySelector.topKiller(Map.of(), Set.of(A, B)));
    }

    @Test
    void 죽은_사람은_제외한다() {
        // B가 최다 킬러지만 이미 탈락했으므로 생존자 중 최다인 C가 나와야 한다
        Map<UUID, Integer> kills = Map.of(A, 1, B, 5, C, 2);
        assertEquals(C, BountySelector.topKiller(kills, Set.of(A, C)));
    }

    @Test
    void 동점이면_그중_하나를_반환() {
        Map<UUID, Integer> kills = Map.of(A, 2, B, 2);
        UUID picked = BountySelector.topKiller(kills, Set.of(A, B));
        assertTrue(List.of(A, B).contains(picked));
    }

    @Test
    void 생존자가_없으면_null() {
        assertNull(BountySelector.topKiller(Map.of(A, 3), Set.of()));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class BountySelector`

- [ ] **Step 3: 최소 구현**

`src/main/java/org/example/game/BountySelector.java`:

```java
package org.example.game;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 현상수배 대상(생존자 중 최다 킬러)을 고릅니다. Bukkit에 의존하지 않는 순수 로직입니다.
 *
 * 0킬이면 null을 반환하고, 호출부(BountyEvent.canRun)는 이때 발동을 포기해
 * 다른 이벤트가 추첨되게 합니다.
 */
public final class BountySelector {

    private BountySelector() {
    }

    /**
     * @param kills 플레이어별 킬 수
     * @param alive 아직 생존 중인 플레이어
     * @return 생존자 중 킬이 가장 많은 사람. 1킬 이상인 생존자가 없으면 null.
     */
    public static UUID topKiller(Map<UUID, Integer> kills, Set<UUID> alive) {
        if (kills == null || alive == null) return null;

        UUID best = null;
        int bestCount = 0;
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            if (!alive.contains(entry.getKey())) continue;
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/game/BountySelector.java src/test/java/org/example/game/BountySelectorTest.java
git commit -m "feat: 현상수배 대상 선정 로직"
```

---

### Task 4: 능력 쿨타임 초기화 훅

**Files:**
- Modify: `src/main/java/org/example/abilities/Ability.java`
- Modify: `src/main/java/org/example/abilities/Blinkerability.java`
- Modify: `src/main/java/org/example/abilities/Hulkability.java`
- Modify: `src/main/java/org/example/abilities/Necromancerability.java`
- Modify: `src/main/java/org/example/abilities/Teemoability.java`
- Modify: `src/main/java/org/example/abilities/DeathReversalAbility.java`
- Modify: `src/main/java/org/example/AbilityManager.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `Ability.resetCooldown()` — default no-op
  - `AbilityManager.resetAllCooldowns(Collection<? extends Player> players)` → `void`

포세이돈은 쿨타임이 없으므로 구현하지 않는다(default no-op 사용).

- [ ] **Step 1: `Ability` 인터페이스에 훅 추가**

`src/main/java/org/example/abilities/Ability.java`의 `onFatalDamage` 선언 바로 위에 삽입:

```java
    /**
     * 쿨타임을 즉시 초기화합니다. '능력 재충전' 이벤트가 호출합니다.
     * 쿨타임이 없는 능력(포세이돈)은 구현하지 않아도 됩니다.
     */
    default void resetCooldown() {}
```

- [ ] **Step 2: 쿨타임이 있는 능력 4종에 구현 추가**

`Blinkerability.java` — `getName()` 메서드 바로 아래에 삽입:

```java
    @Override
    public void resetCooldown() {
        lastUsed = 0;
    }
```

`Hulkability.java` — `getName()` 메서드 바로 아래에 삽입:

```java
    @Override
    public void resetCooldown() {
        lastUsed = 0;
    }
```

`Necromancerability.java` — `getName()` 메서드 바로 아래에 삽입:

```java
    @Override
    public void resetCooldown() {
        lastUsed = 0;
    }
```

`Teemoability.java` — `getName()` 메서드 바로 아래에 삽입 (필드명이 `lastShotTime`임에 주의):

```java
    @Override
    public void resetCooldown() {
        lastShotTime = 0;
    }
```

`DeathReversalAbility.java` — `getName()` 메서드 바로 아래에 삽입:

```java
    @Override
    public void resetCooldown() {
        lastUsed = 0;
    }
```

- [ ] **Step 3: `AbilityManager`에 위임 메서드 추가**

`src/main/java/org/example/AbilityManager.java`의 `checkPassiveAbilities` 메서드 바로 위에 삽입:

```java
    /** 모든 플레이어의 능력 쿨타임을 즉시 초기화합니다. ('능력 재충전' 이벤트용) */
    public void resetAllCooldowns(Collection<? extends Player> players) {
        for (Player p : players) {
            Ability a = playerAbilities.get(p.getUniqueId());
            if (a != null) a.resetCooldown();
        }
    }
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/abilities/ src/main/java/org/example/AbilityManager.java
git commit -m "feat: 능력 쿨타임 초기화 훅 추가

'능력 재충전' 이벤트가 사용합니다. 포세이돈은 쿨타임이 없어 default no-op을 씁니다."
```

---

### Task 5: 이벤트 시스템 뼈대

**Files:**
- Create: `src/main/java/org/example/events/GameEvent.java`
- Create: `src/main/java/org/example/events/GameContext.java`
- Create: `src/main/java/org/example/events/EventSpawns.java`
- Create: `src/main/java/org/example/events/GameEventManager.java`

**Interfaces:**
- Consumes: `EventPicker.pick(List<String>, String, Random)` (Task 2)
- Produces:
  - `GameEvent` — `getName()`, `start(GameContext)`, `canRun(GameContext)`
  - `GameContext` — `getWorld()`, `getSurvivors()`, `isFarming()`, `getKills()`, `getAliveUuids()`, `getPlugin()`
  - `EventSpawns.tag(Entity, JavaPlugin)`, `EventSpawns.sweep(World, JavaPlugin)`
  - `GameEventManager(JavaPlugin)` — `tick(GameContext, int intervalSeconds)`, `reset()`

이 태스크는 이벤트 0개로 뼈대만 만든다. 실제 이벤트는 Task 6~7에서 목록에 추가한다.

- [ ] **Step 1: `GameEvent` 작성**

`src/main/java/org/example/events/GameEvent.java`:

```java
package org.example.events;

/**
 * 게임 도중 무작위로 발동하는 사건.
 *
 * 새 이벤트를 추가하려면:
 *   1. 이 인터페이스를 구현하는 클래스를 events 패키지에 만든다.
 *   2. GameEventManager의 EVENTS 목록에 생성자 참조를 한 줄 추가한다.
 *
 * 모든 이벤트는 발동 즉시 끝나는 순간 이벤트다. 지속 효과가 필요하면 유한한
 * 지속시간을 가진 포션 효과를 쓴다(스스로 만료되므로 해제 코드가 필요 없다).
 * 스폰한 엔티티는 EventSpawns.tag()로 표식을 달아 게임 종료 시 회수되게 한다.
 */
public interface GameEvent {

    /** 공지와 로그에 쓰일 이름 (예: "보급 투하") */
    String getName();

    /** 발동. 예외를 던져도 GameEventManager가 잡아 게임 타이머는 계속 돈다. */
    void start(GameContext ctx);

    /**
     * 지금 발동할 수 있는지 여부. false면 추첨 후보에서 빠진다.
     * 전투 전용 이벤트는 ctx.isFarming()이 true일 때 false를 반환한다.
     */
    default boolean canRun(GameContext ctx) {
        return true;
    }
}
```

- [ ] **Step 2: `GameContext` 작성**

`src/main/java/org/example/events/GameContext.java`:

```java
package org.example.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.AbilityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 이벤트가 필요로 하는 것만 담은 묶음입니다.
 *
 * 이벤트가 Main을 직접 참조하지 않게 하려고 둡니다. 그래야 이벤트를 독립적으로
 * 이해하고 교체할 수 있습니다.
 */
public final class GameContext {

    private final JavaPlugin plugin;
    private final World world;
    private final List<Player> survivors;
    private final boolean farming;
    private final Map<UUID, Integer> kills;
    private final AbilityManager abilityManager;

    public GameContext(JavaPlugin plugin, World world, List<Player> survivors,
                       boolean farming, Map<UUID, Integer> kills, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.world = world;
        this.survivors = survivors;
        this.farming = farming;
        this.kills = kills;
        this.abilityManager = abilityManager;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    /** '능력 재충전' 이벤트가 쿨타임 초기화를 위임하는 데 씁니다. */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public World getWorld() {
        return world;
    }

    public List<Player> getSurvivors() {
        return survivors;
    }

    /** 파밍 구간이면 true. 전투 전용 이벤트가 canRun에서 거르는 데 씁니다. */
    public boolean isFarming() {
        return farming;
    }

    public Map<UUID, Integer> getKills() {
        return kills;
    }

    public Set<UUID> getAliveUuids() {
        Set<UUID> result = new HashSet<>();
        for (Player p : survivors) {
            result.add(p.getUniqueId());
        }
        return result;
    }
}
```

- [ ] **Step 3: `EventSpawns` 작성**

`src/main/java/org/example/events/EventSpawns.java`:

```java
package org.example.events;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 이벤트가 스폰한 엔티티에 표식을 달고, 게임 종료 시 한 번에 회수합니다.
 *
 * PDC를 쓰는 이유: 메타데이터(FixedMetadataValue)는 런타임 전용이라 월드에 저장되지
 * 않습니다. 예전에 네크로맨서 좀비가 메타데이터로 표식을 달고 setPersistent(true)로
 * 저장되는 바람에, 재시작하면 좀비는 남고 표식만 사라져 영영 제거할 수 없었습니다.
 * PDC는 엔티티와 함께 저장되므로 재시작 후에도 회수됩니다.
 */
public final class EventSpawns {

    private static final String KEY = "event_spawn";

    private EventSpawns() {
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /** 이벤트가 스폰한 엔티티임을 표시합니다. */
    public static void tag(Entity entity, JavaPlugin plugin) {
        entity.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    /** 표식이 붙은 엔티티를 모두 제거합니다. 게임 종료/플러그인 비활성화 시 호출합니다. */
    public static int sweep(World world, JavaPlugin plugin) {
        if (world == null) return 0;

        NamespacedKey k = key(plugin);
        int removed = 0;
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(k, PersistentDataType.BYTE)) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }
}
```

- [ ] **Step 4: `GameEventManager` 작성**

`src/main/java/org/example/events/GameEventManager.java`:

```java
package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.game.EventPicker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 일정 주기마다 이벤트를 하나 추첨해 발동합니다.
 *
 * Main.gameTimer에서 1초마다 tick()을 받습니다. 별도 스케줄러를 만들지 않는 것은
 * 능력의 checkPassiveAbilities와 같은 방식입니다.
 *
 * 별도의 레지스트리 클래스를 두지 않는 이유: AbilityRegistry에 create(name)/isValid()가
 * 있는 것은 /능력변경 명령어가 그것들을 쓰기 때문입니다. 이벤트에는 그런 명령어가
 * 없으므로 같은 API를 복사하면 아무도 호출하지 않는 코드만 늘어납니다.
 */
public class GameEventManager {

    /** ★ 새 이벤트는 여기에 한 줄만 추가하면 됩니다. */
    private static final List<Supplier<GameEvent>> EVENTS = List.of();

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<String, GameEvent> byName = new LinkedHashMap<>();

    private int secondsSinceLast = 0;
    private String lastPicked = null;

    public GameEventManager(JavaPlugin plugin) {
        this.plugin = plugin;
        for (Supplier<GameEvent> supplier : EVENTS) {
            GameEvent e = supplier.get();
            byName.put(e.getName(), e);
        }
    }

    /** 게임 시작 시 호출해 주기와 직전 기록을 초기화합니다. */
    public void reset() {
        secondsSinceLast = 0;
        lastPicked = null;
    }

    /**
     * @param intervalSeconds 이벤트 간격(초). 0 이하면 이벤트 시스템을 끕니다.
     */
    public void tick(GameContext ctx, int intervalSeconds) {
        if (intervalSeconds <= 0 || byName.isEmpty()) return;

        secondsSinceLast++;
        if (secondsSinceLast < intervalSeconds) return;
        secondsSinceLast = 0;

        List<String> candidates = new ArrayList<>();
        for (GameEvent e : byName.values()) {
            if (e.canRun(ctx)) candidates.add(e.getName());
        }

        String picked = EventPicker.pick(candidates, lastPicked, random);
        if (picked == null) return;

        GameEvent event = byName.get(picked);
        lastPicked = picked;

        // 이벤트 하나가 터져도 게임 타이머 전체가 죽지 않도록 격리합니다.
        try {
            event.start(ctx);
        } catch (Exception e) {
            plugin.getLogger().warning("[능력자] 이벤트 '" + picked + "' 실행 중 오류: " + e.getMessage());
        }
    }

    /** 이벤트 공지에 공통으로 쓰는 형식입니다. */
    static void announce(String title, String detail) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "◆ [이벤트] " + ChatColor.WHITE + title);
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + detail);
        Bukkit.broadcastMessage("");
    }
}
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL (이벤트 목록이 비어 있어 아직 아무 일도 하지 않음)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/org/example/events/
git commit -m "feat: 이벤트 시스템 뼈대

GameEvent 인터페이스(3메서드), GameContext, EventSpawns(PDC 정리), GameEventManager.
이벤트 구현체는 아직 없습니다."
```

---

### Task 6: 공용 이벤트 3종

**Files:**
- Create: `src/main/java/org/example/events/SupplyDropEvent.java`
- Create: `src/main/java/org/example/events/NightRushEvent.java`
- Create: `src/main/java/org/example/events/AbilityRechargeEvent.java`
- Modify: `src/main/java/org/example/events/GameEventManager.java` (EVENTS 목록)

**Interfaces:**
- Consumes: `GameEvent`, `GameContext`, `EventSpawns` (Task 5), `AbilityManager.resetAllCooldowns(Collection)` (Task 4)
- Produces: 없음 (이벤트 구현체는 목록을 통해서만 쓰인다)

- [ ] **Step 1: `SupplyDropEvent` 작성**

`src/main/java/org/example/events/SupplyDropEvent.java`:

```java
package org.example.events;

import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * 자기장 안 무작위 좌표에 보급 상자를 떨어뜨리고 좌표를 공지합니다.
 *
 * 상자 주변에 몬스터를 함께 배치하는 것이 핵심입니다. 파밍 중에는 PVP가 잠겨 있어
 * 상자만 놓으면 가장 가까운 사람이 걸어가서 먹고 끝이라 경쟁이 성립하지 않습니다.
 * 몬스터가 있어야 "뚫을 수 있나"라는 판단이 생깁니다.
 */
public class SupplyDropEvent implements GameEvent {

    private static final int GUARD_COUNT = 3;
    private final Random random = new Random();

    @Override
    public String getName() {
        return "보급 투하";
    }

    @Override
    public void start(GameContext ctx) {
        World world = ctx.getWorld();
        if (world == null) return;

        Location loc = randomLocationInBorder(world);
        if (loc == null) return;

        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            chest.getBlockInventory().addItem(
                    new ItemStack(Material.DIAMOND_SWORD),
                    new ItemStack(Material.DIAMOND_CHESTPLATE),
                    new ItemStack(Material.GOLDEN_APPLE, 2),
                    new ItemStack(Material.COOKED_BEEF, 16));
        }

        for (int i = 0; i < GUARD_COUNT; i++) {
            Location spawn = loc.clone().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            Zombie guard = (Zombie) world.spawnEntity(spawn, EntityType.ZOMBIE);
            guard.setAdult();
            guard.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            guard.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            EventSpawns.tag(guard, ctx.getPlugin());
        }

        GameEventManager.announce("보급 투하",
                ChatColor.YELLOW + "좌표 " + loc.getBlockX() + ", " + loc.getBlockZ()
                        + ChatColor.GRAY + " 에 보급 상자가 떨어졌습니다. 사령들이 지키고 있습니다.");
    }

    /**
     * 현재 자기장 크기 안쪽의 무작위 지면 좌표를 고릅니다.
     * 자기장은 (0,0) 중심이므로 크기의 절반이 반경입니다. 축소가 진행 중이면
     * 그 시점의 실제 크기를 쓰므로 후반에는 중앙 근처에만 뜹니다.
     */
    private Location randomLocationInBorder(World world) {
        double radius = world.getWorldBorder().getSize() / 2.0;
        if (radius > 200) radius = 200; // 초반 자기장이 너무 크면 아무도 못 감

        double x = (random.nextDouble() * 2 - 1) * radius;
        double z = (random.nextDouble() * 2 - 1) * radius;

        // 청크를 먼저 로드해야 getHighestBlockYAt이 정확한 높이를 돌려줍니다.
        Chunk chunk = world.getChunkAt((int) x >> 4, (int) z >> 4);
        if (!chunk.isLoaded()) chunk.load(true);

        int y = world.getHighestBlockYAt((int) x, (int) z);
        if (y <= 0) return null;

        return new Location(world, Math.floor(x), y + 1, Math.floor(z));
    }
}
```

- [ ] **Step 2: `NightRushEvent` 작성**

`src/main/java/org/example/events/NightRushEvent.java`:

```java
package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.Random;

/**
 * 시간을 밤으로 돌리고 각 생존자 주변에 몬스터를 스폰합니다.
 * 밤은 자연히 지나가므로 시간을 되돌리지 않습니다.
 */
public class NightRushEvent implements GameEvent {

    private static final long NIGHT_TIME = 14000L;
    private static final int PER_PLAYER = 2;
    private static final double SPAWN_RADIUS = 6.0;

    private final Random random = new Random();

    @Override
    public String getName() {
        return "사령의 밤";
    }

    @Override
    public void start(GameContext ctx) {
        if (ctx.getWorld() == null) return;
        ctx.getWorld().setTime(NIGHT_TIME);

        for (Player p : ctx.getSurvivors()) {
            for (int i = 0; i < PER_PLAYER; i++) {
                double dx = (random.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                double dz = (random.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                Location spawn = p.getLocation().clone().add(dx, 0, dz);

                Zombie z = (Zombie) p.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
                z.setAdult();
                EventSpawns.tag(z, ctx.getPlugin());
            }
        }

        GameEventManager.announce("사령의 밤",
                ChatColor.YELLOW + "어둠이 내렸습니다. " + ChatColor.GRAY + "사령들이 당신을 찾아옵니다.");
    }
}
```

- [ ] **Step 3: `AbilityRechargeEvent` 작성**

`src/main/java/org/example/events/AbilityRechargeEvent.java`:

```java
package org.example.events;

import org.bukkit.ChatColor;

/**
 * 전원의 능력 쿨타임을 즉시 초기화합니다.
 *
 * 지속 시간이 없는 순간 이벤트입니다. "30초간 무제한"이 아니라 "즉시 초기화"로 잡은
 * 이유는, 지속 추적도 종료 시 원복도 필요 없어 정리 누락 버그가 생길 수 없기 때문입니다.
 */
public class AbilityRechargeEvent implements GameEvent {

    @Override
    public String getName() {
        return "능력 재충전";
    }

    @Override
    public void start(GameContext ctx) {
        if (ctx.getAbilityManager() == null) return;
        ctx.getAbilityManager().resetAllCooldowns(ctx.getSurvivors());

        GameEventManager.announce("능력 재충전",
                ChatColor.AQUA + "모든 능력의 쿨타임이 초기화되었습니다!");
    }
}
```

- [ ] **Step 4: `GameEventManager`의 EVENTS 목록에 등록**

`src/main/java/org/example/events/GameEventManager.java`에서 다음 줄을

```java
    private static final List<Supplier<GameEvent>> EVENTS = List.of();
```

이렇게 교체:

```java
    private static final List<Supplier<GameEvent>> EVENTS = List.of(
            SupplyDropEvent::new,
            NightRushEvent::new,
            AbilityRechargeEvent::new);
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/org/example/events/
git commit -m "feat: 공용 이벤트 3종 (보급 투하 / 사령의 밤 / 능력 재충전)"
```

---

### Task 7: 전투 전용 이벤트 2종

**Files:**
- Create: `src/main/java/org/example/events/GlowingEvent.java`
- Create: `src/main/java/org/example/events/BountyEvent.java`
- Modify: `src/main/java/org/example/events/GameEventManager.java` (EVENTS 목록)

**Interfaces:**
- Consumes: `BountySelector.topKiller(Map, Set)` (Task 3), `GameEvent`/`GameContext` (Task 5)
- Produces: 없음

- [ ] **Step 1: `GlowingEvent` 작성**

`src/main/java/org/example/events/GlowingEvent.java`:

```java
package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 30초간 전 생존자를 발광시켜 위치를 노출합니다.
 *
 * 자기장은 물리적으로 좁히고 발광은 정보로 좁힙니다. 최후반 대치를 깨는 장치입니다.
 * 유한한 지속시간이라 스스로 만료되므로 해제 코드가 필요 없습니다.
 */
public class GlowingEvent implements GameEvent {

    private static final int DURATION_TICKS = 30 * 20;

    @Override
    public String getName() {
        return "전원 발광";
    }

    @Override
    public boolean canRun(GameContext ctx) {
        return !ctx.isFarming(); // 파밍 중엔 서로 못 때리므로 위치 노출이 의미 없다
    }

    @Override
    public void start(GameContext ctx) {
        for (Player p : ctx.getSurvivors()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DURATION_TICKS, 0, false, false, false));
        }

        GameEventManager.announce("전원 발광",
                ChatColor.YELLOW + "30초간 모두의 위치가 드러납니다. " + ChatColor.GRAY + "숨을 곳은 없습니다.");
    }
}
```

- [ ] **Step 2: `BountyEvent` 작성**

`src/main/java/org/example/events/BountyEvent.java`:

```java
package org.example.events;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.game.BountySelector;

import java.util.UUID;

/**
 * 생존자 중 최다 킬러의 위치를 60초간 노출합니다.
 *
 * 보상이 아니라 페널티입니다. 앞서가는 사람이 견제당하게 만드는 안티 스노우볼
 * 장치이고, 그동안 집계만 되고 쓰이지 않던 killCounts를 처음으로 게임에 씁니다.
 *
 * 1킬 이상인 생존자가 없으면 canRun이 false를 반환해 다른 이벤트가 추첨됩니다.
 */
public class BountyEvent implements GameEvent {

    private static final int DURATION_TICKS = 60 * 20;

    @Override
    public String getName() {
        return "현상수배";
    }

    @Override
    public boolean canRun(GameContext ctx) {
        if (ctx.isFarming()) return false;
        return BountySelector.topKiller(ctx.getKills(), ctx.getAliveUuids()) != null;
    }

    @Override
    public void start(GameContext ctx) {
        UUID targetId = BountySelector.topKiller(ctx.getKills(), ctx.getAliveUuids());
        if (targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null) return;

        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DURATION_TICKS, 0, false, false, false));

        int kills = ctx.getKills().getOrDefault(targetId, 0);
        GameEventManager.announce("현상수배",
                ChatColor.RED + target.getName() + ChatColor.GRAY + " (" + kills + "킬) 의 위치가 60초간 드러납니다.");
    }
}
```

- [ ] **Step 3: EVENTS 목록에 등록**

`src/main/java/org/example/events/GameEventManager.java`의 EVENTS를 교체:

```java
    private static final List<Supplier<GameEvent>> EVENTS = List.of(
            SupplyDropEvent::new,
            NightRushEvent::new,
            AbilityRechargeEvent::new,
            GlowingEvent::new,
            BountyEvent::new);
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/events/
git commit -m "feat: 전투 전용 이벤트 2종 (전원 발광 / 현상수배)"
```

---

### Task 8: 능력 단계적 공개

**Files:**
- Modify: `src/main/java/org/example/AbilityManager.java` (능력 이름 목록 조회)
- Modify: `src/main/java/org/example/Main.java` (파밍 종료 공지, 사망 시 공개)

**Interfaces:**
- Consumes: 없음
- Produces: `AbilityManager.getAssignedAbilityNames()` → `List<String>` (배정된 능력 이름들, 정렬됨)

- [ ] **Step 1: `AbilityManager`에 조회 메서드 추가**

`src/main/java/org/example/AbilityManager.java`의 `getPlayerAbilityName` 바로 아래에 삽입:

```java
    /** 이번 게임에 배정된 능력 이름 목록(정렬). 파밍 종료 시 등장 능력 공개에 씁니다. */
    public List<String> getAssignedAbilityNames() {
        List<String> names = new ArrayList<>();
        for (Ability a : playerAbilities.values()) {
            names.add(a.getName());
        }
        Collections.sort(names);
        return names;
    }
```

import 추가:

```java
import java.util.Collections;
```

(`java.util.ArrayList`와 `java.util.List`는 Task 1에서 이미 추가됨)

- [ ] **Step 2: `Main`에 등장 능력 공개 메서드 추가**

`src/main/java/org/example/Main.java`의 `revealAllAbilities()` 메서드 바로 위에 삽입:

```java
    /**
     * 파밍이 끝나는 순간 이번 판에 등장한 능력의 "이름만" 공개합니다.
     * 누가 무엇을 가졌는지는 알리지 않습니다. 사망 시 공개(onPlayerDeath)와 맞물려
     * 소거법이 성립하고, 그것이 후반 교전의 긴장을 만듭니다.
     */
    private void revealAbilityLineup() {
        List<String> names = abilityManager.getAssignedAbilityNames();
        if (names.isEmpty()) return;

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "========== [ 이번 판 등장 능력 ] ==========");
        Bukkit.broadcastMessage(ChatColor.AQUA + "  " + String.join(ChatColor.GRAY + " · " + ChatColor.AQUA, names));
        Bukkit.broadcastMessage(ChatColor.GRAY + "  (누가 어떤 능력인지는 공개되지 않습니다)");
        Bukkit.broadcastMessage(ChatColor.GOLD + "==========================================");
        Bukkit.broadcastMessage("");
    }
```

- [ ] **Step 3: 파밍 종료 시점에 호출**

`src/main/java/org/example/Main.java`의 `startTimer()` 안, PVP 활성화 블록에서 브로드캐스트 바로 뒤에 한 줄 추가한다. 다음 코드를 찾아서:

```java
                    Bukkit.broadcastMessage(ChatColor.RED + "[능력자] 파밍 시간이 종료되었습니다! PVP가 활성화되며, "
                            + cfgCombatTime + "초에 걸쳐 자기장이 서서히 " + (int) cfgFinalBorderSize + "x" + (int) cfgFinalBorderSize + "(으)로 축소됩니다!");
                }
```

이렇게 바꾼다:

```java
                    Bukkit.broadcastMessage(ChatColor.RED + "[능력자] 파밍 시간이 종료되었습니다! PVP가 활성화되며, "
                            + cfgCombatTime + "초에 걸쳐 자기장이 서서히 " + (int) cfgFinalBorderSize + "x" + (int) cfgFinalBorderSize + "(으)로 축소됩니다!");
                    revealAbilityLineup();
                }
```

- [ ] **Step 4: 사망 시 능력 공개**

`src/main/java/org/example/Main.java`의 `onPlayerDeath`에서 킬 집계 블록 바로 뒤에 삽입한다. 다음 코드를 찾아서:

```java
        Player killer = deadPlayer.getKiller();
        if (killer != null) {
            killCounts.merge(killer.getUniqueId(), 1, Integer::sum);
        }
```

이렇게 바꾼다:

```java
        Player killer = deadPlayer.getKiller();
        if (killer != null) {
            killCounts.merge(killer.getUniqueId(), 1, Integer::sum);
        }

        // 사망자의 능력을 공개합니다. 파밍 종료 시 공개된 등장 능력 목록과 맞물려
        // 남은 사람의 능력을 소거법으로 좁힐 수 있게 됩니다.
        String deadAbility = abilityManager.getPlayerAbilityName(deadPlayer.getUniqueId());
        if (deadAbility != null && !deadAbility.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "☠ " + ChatColor.WHITE + deadPlayer.getName()
                    + ChatColor.GRAY + " 님의 능력은 " + ChatColor.AQUA + "[" + deadAbility + "]"
                    + ChatColor.GRAY + " 이었습니다.");
        }
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/org/example/AbilityManager.java src/main/java/org/example/Main.java
git commit -m "feat: 능력 단계적 공개

파밍 종료 시 등장 능력 이름만 공개하고, 사망 시 그 사람의 능력을 공개합니다.
둘이 맞물려 소거법이 성립합니다."
```

---

### Task 9: Main에 이벤트 시스템 연결 + 설정 항목

**Files:**
- Modify: `src/main/java/org/example/Main.java`

**Interfaces:**
- Consumes: `GameEventManager(JavaPlugin)`, `tick(GameContext, int)`, `reset()`, `GameContext(...)`, `EventSpawns.sweep(World, JavaPlugin)` (Task 5)
- Produces: 없음

- [ ] **Step 1: 필드와 설정값 추가**

`src/main/java/org/example/Main.java`에서 `private HealthBarListener healthBarListener;` 바로 아래에 삽입:

```java
    private GameEventManager gameEventManager;
```

`private int cfgCombatTime = 300;` 바로 아래에 삽입:

```java
    private int cfgEventInterval = 300;
```

import 추가:

```java
import org.example.events.EventSpawns;
import org.example.events.GameContext;
import org.example.events.GameEventManager;
```

- [ ] **Step 2: onEnable에서 생성 및 config 로드**

`cfgCombatTime = getConfig().getInt("cfg-combat-time", cfgCombatTime);` 바로 아래에 삽입:

```java
        cfgEventInterval = getConfig().getInt("cfg-event-interval", cfgEventInterval);
```

`this.abilityManager = new AbilityManager(this);` 바로 아래에 삽입:

```java
        this.gameEventManager = new GameEventManager(this);
```

- [ ] **Step 3: `/게임설정`에 항목 추가**

`case "전투시간(초)": cfgCombatTime = (int) value; break;` 바로 아래에 삽입:

```java
                case "이벤트간격(초)": cfgEventInterval = (int) value; break;
```

`getConfig().set("cfg-combat-time", cfgCombatTime);` 바로 아래에 삽입:

```java
                getConfig().set("cfg-event-interval", cfgEventInterval);
```

탭 완성 목록을 교체한다. 다음 줄을 찾아서:

```java
                String[] options = {"시작크기", "최종크기", "자기장대미지", "평화시간(초)", "전투시간(초)"};
```

이렇게 바꾼다:

```java
                String[] options = {"시작크기", "최종크기", "자기장대미지", "평화시간(초)", "전투시간(초)", "이벤트간격(초)"};
```

- [ ] **Step 4: `/게임설정확인`에 표시 추가**

`player.sendMessage(ChatColor.YELLOW + "▶ 최종 자기장 고정 크기: " ...);` 바로 아래에 삽입:

```java
            player.sendMessage(ChatColor.YELLOW + "▶ 이벤트 간격: " + ChatColor.WHITE
                    + (cfgEventInterval > 0 ? cfgEventInterval + "초" : "꺼짐"));
```

- [ ] **Step 5: startGame에서 리셋**

`startTimer();` 바로 위에 삽입:

```java
        gameEventManager.reset();
```

- [ ] **Step 6: gameTimer에서 tick 호출**

`startTimer()` 안, `checkWinner();` 바로 위에 삽입:

```java
                if (gameEventManager != null && gameWorld != null) {
                    List<Player> survivors = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (isSurvivingParticipant(p)) survivors.add(p);
                    }
                    GameContext ctx = new GameContext(Main.this, gameWorld, survivors,
                            timeElapsed < cfgFarmingTime, killCounts, abilityManager);
                    gameEventManager.tick(ctx, cfgEventInterval);
                }
```

- [ ] **Step 7: 게임 종료 시 스폰물 정리**

`stopGameForce()` 안, `teamManager.clear();` 바로 위에 삽입:

```java
        // 이벤트가 스폰한 몬스터/상자를 회수합니다. 정리 지점은 여기 한 곳뿐입니다.
        if (gameWorld != null) {
            int removed = EventSpawns.sweep(gameWorld, this);
            if (removed > 0) {
                getLogger().info("[능력자] 이벤트 스폰물 " + removed + "개를 정리했습니다.");
            }
        }
```

`onDisable()` 안, `if (abilityManager != null) {` 바로 위에 삽입:

```java
        if (gameWorld != null) {
            EventSpawns.sweep(gameWorld, this);
        }
```

- [ ] **Step 8: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 전체 테스트 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL (Task 1~3의 테스트 전부 통과)

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/org/example/Main.java
git commit -m "feat: 이벤트 시스템을 게임 루프에 연결

/게임설정 이벤트간격(초) 항목 추가, 게임 종료 시 스폰물 회수."
```

---

### Task 10: 서버 수동 검증

**Files:** 없음 (서버에서 확인)

**Interfaces:**
- Consumes: 전체
- Produces: 없음

이 프로젝트는 Bukkit 의존 코드를 서버 없이 검증할 수 없다. 컴파일 성공은 동작을 보장하지 않는다 — 실제로 "컴파일은 되는데 텔레포트가 전부 실패"하던 버그가 있었다.

- [ ] **Step 1: jar 빌드**

Run: `./gradlew clean build --console=plain`
Expected: BUILD SUCCESSFUL, `build/libs/AbilityWar-1.0-SNAPSHOT.jar` 생성

- [ ] **Step 2: 서버에 배포**

`build/libs/AbilityWar-1.0-SNAPSHOT.jar`를 서버의 `plugins/`에 복사하고 **서버를 완전히 재시작**한다(`/reload` 금지 — onEnable/onDisable 동작이 바뀌었다).

- [ ] **Step 3: 빠른 확인용 설정**

게임 내에서 OP로 실행:

```
/게임설정 이벤트간격(초) 30
/게임설정 평화시간(초) 60
/게임설정 전투시간(초) 120
/게임설정확인
```

Expected: 이벤트 간격 30초가 표시됨

- [ ] **Step 4: 체크리스트 확인**

`/게임시작` 후 아래를 확인한다.

| 항목 | 기대 |
|---|---|
| 중복 없는 배정 | 3명 이상 게임에서 전원 다른 능력 |
| 이벤트 추첨 | 30초마다 공지. 같은 이벤트가 연속으로 안 나옴 |
| 구간 제한 | 파밍 중(첫 60초)엔 전원 발광/현상수배가 안 나옴 |
| 보급 투하 | 공지된 좌표에 상자와 좀비가 실제로 있음 |
| 사령의 밤 | 밤이 되고 주변에 좀비 스폰 |
| 능력 재충전 | 쿨타임 중이던 능력이 즉시 사용 가능 |
| 전원 발광 | 벽 너머로 윤곽이 보이고 30초 후 사라짐 |
| 현상수배 | 전원 0킬일 땐 안 나옴. 킬 발생 후엔 최다 킬러가 발광 |
| 등장 능력 공개 | 파밍 종료 시 목록이 뜨고, 이름만 나옴 |
| 사망 시 공개 | 죽은 사람의 능력이 공지됨 |
| 스폰물 정리 | `/게임종료` 후 이벤트 좀비가 사라짐 |

- [ ] **Step 5: 실제 설정으로 되돌리기**

```
/게임설정 이벤트간격(초) 300
/게임설정 평화시간(초) 720
/게임설정 전투시간(초) 480
```

- [ ] **Step 6: 문제가 있으면 기록**

발견한 문제를 이 파일 하단에 적고 해당 Task로 돌아간다.
