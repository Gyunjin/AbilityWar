# Task 2: 기존 5종을 Cooldown으로 마이그레이션

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 1](task-01-cooldown.md) · [진척 현황](README.md) · [Task 3 →](task-03-ability-items.md)

---


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

[← Task 1](task-01-cooldown.md) · [진척 현황](README.md) · [Task 3 →](task-03-ability-items.md)
