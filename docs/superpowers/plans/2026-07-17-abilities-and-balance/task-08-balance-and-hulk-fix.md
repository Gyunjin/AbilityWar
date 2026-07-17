# Task 8: 블링커/헐크 밸런스 + 헐크 낙하 데미지 버그

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 7](task-07-advancement-suppressor.md) · [진척 현황](README.md) · [Task 9 →](task-09-regression-check.md)

---


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

[← Task 7](task-07-advancement-suppressor.md) · [진척 현황](README.md) · [Task 9 →](task-09-regression-check.md)
