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
