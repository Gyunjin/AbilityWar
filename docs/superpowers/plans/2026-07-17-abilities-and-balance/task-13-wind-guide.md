# Task 13: 바람 인도자

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 12](task-12-deathworm.md) · [진척 현황](README.md) · [Task 14 →](task-14-reincarnator.md)

---


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

[← Task 12](task-12-deathworm.md) · [진척 현황](README.md) · [Task 14 →](task-14-reincarnator.md)
