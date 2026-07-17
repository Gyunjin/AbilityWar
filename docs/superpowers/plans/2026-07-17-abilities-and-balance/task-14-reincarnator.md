# Task 14: 윤회자

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 13](task-13-wind-guide.md) · [진척 현황](README.md) · [Task 15 →](task-15-registry-test.md)

---


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

[← Task 13](task-13-wind-guide.md) · [진척 현황](README.md) · [Task 15 →](task-15-registry-test.md)
