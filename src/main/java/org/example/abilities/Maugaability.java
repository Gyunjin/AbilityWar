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
