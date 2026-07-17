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
            p.sendMessage(ChatColor.RED + "(잠행 중 땅에 착지했는데 모래가 아니면 실패하고 쿨타임이 반환됩니다. 점프는 가능. 쿨타임: 25초)");
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

                // 실패 조건: 땅에 착지한 상태에서 발밑이 모래가 아니면 실패. 습격 없음.
                // 점프로 잠깐 공중에 뜬 경우(발밑이 공기)는 실패로 치지 않습니다 -
                // 그래야 잠행 중에도 점프할 수 있습니다.
                if (p.isOnGround() && !onSand(p)) {
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

    /** 실패로 끝난 경우 습격은 발생하지 않습니다. 캔슬이므로 쿨타임을 돌려줍니다. */
    private void failBurrow(Player p, boolean notify) {
        cancelBurrow();
        burrowing = false;
        // 잠행이 실패로 캔슬되면 쿨타임을 돌려줘서 바로 다시 시도할 수 있게 합니다.
        cooldown.reset();
        if (p == null) return;
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
        if (notify && p.isOnline()) {
            p.sendMessage(ChatColor.RED + "모래를 벗어나 잠행이 실패했습니다! " + ChatColor.GRAY + "(쿨타임이 반환되었습니다)");
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

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        for (Entity e : p.getNearbyEntities(AMBUSH_RADIUS, AMBUSH_RADIUS, AMBUSH_RADIUS)) {
            if (!(e instanceof LivingEntity victim) || e.equals(p)) continue;
            victim.damage(AMBUSH_DAMAGE, p);
            // 띄우기를 다음 틱에 적용합니다. damage()가 유발하는 바닐라 넉백이 같은 틱의
            // setVelocity를 덮어써서, 특히 동물이 안 떠오르던 문제를 피합니다.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (victim.isValid()) victim.setVelocity(victim.getVelocity().add(new Vector(0, AMBUSH_LIFT, 0)));
            });
        }
        p.sendMessage(ChatColor.YELLOW + "모래를 뚫고 솟아올랐습니다!");
    }
}
