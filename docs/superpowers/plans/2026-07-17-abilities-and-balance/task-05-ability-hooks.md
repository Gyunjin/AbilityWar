# Task 5: Ability 훅 2종 + AbilityManager 디스패치

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 4](task-04-vanish.md) · [진척 현황](README.md) · [Task 6 →](task-06-cooldown-command.md)

---


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

[← Task 4](task-04-vanish.md) · [진척 현황](README.md) · [Task 6 →](task-06-cooldown-command.md)
