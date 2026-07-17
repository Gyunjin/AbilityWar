# Task 11: 암살자

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 10](task-10-mauga.md) · [진척 현황](README.md) · [Task 12 →](task-12-deathworm.md)

---


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

[← Task 10](task-10-mauga.md) · [진척 현황](README.md) · [Task 12 →](task-12-deathworm.md)
