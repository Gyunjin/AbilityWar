package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.example.PlayerStats;

/**
 * 헐크: 패시브로 최대 체력 20(하트 20개)이 상시 적용됩니다.
 * 액티브: 전용 건틀릿을 들고 우클릭하면 크게 점프한 뒤, 착지하는 순간
 * 주변 흙/모래/나뭇잎 등 부드러운 블록을 부수고 주변 플레이어에게 대미지를 줍니다.
 *
 * 참고: 이동/대미지 감지는 이 클래스가 직접 Listener를 등록하지 않고,
 * AbilityManager가 전역에서 한 번만 구독한 뒤 onPlayerMove/onEntityDamage로
 * 위임해줍니다(같은 능력을 가진 플레이어가 여러 명이어도 이벤트가 중복 처리되지 않도록).
 */
public class Hulkability implements Ability {

    private static final double MAX_HEALTH = 40.0;
    private static final String ITEM_TAG = "[능력] 괴력의 건틀릿";
    private static final double JUMP_POWER = 1.4;
    private static final double SLAM_RADIUS = 3.0;
    private static final double SLAM_DAMAGE = 6.0;
    private static final int BLOCK_RADIUS = 2;

    private final Cooldown cooldown = new Cooldown(10000);

    // 점프 후 착지를 기다리는 중인지, 착지 직후 낙하 대미지를 무효화할지 여부
    private boolean waitingForLanding = false;
    private boolean fallDamageImmune = false;

    @Override
    public String getName() {
        return "헐크";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        PlayerStats.setMaxHealth(p, MAX_HEALTH);
        p.setHealth(MAX_HEALTH);
        p.getInventory().addItem(AbilityItems.create(Material.COBBLESTONE, ChatColor.RED, ITEM_TAG));

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(패시브 효과: 최대 체력이 상시 하트 20개로 증가합니다.)");
            p.sendMessage(ChatColor.GRAY + "(액티브: 전용 건틀릿 우클릭 시 높이 점프 후 착지하며 대지를 내려찍습니다.)");
            p.sendMessage(ChatColor.RED + "(쿨타임: 10초)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]이(가) 적용되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        waitingForLanding = false;
        fallDamageImmune = false;
        // p가 null(오프라인)이면 최대 체력을 되돌릴 수 없습니다. 그 경우는
        // AbilityManager가 재접속 시점에 정리합니다.
        PlayerStats.resetMaxHealth(p);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 우클릭 한 번에 이벤트가 주손/보조손 두 번 발생하므로 주손만 처리합니다.
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AbilityItems.isHolding(p, Material.COBBLESTONE, ITEM_TAG)) return;

        event.setCancelled(true);

        if (!cooldown.tryUse(p, "아직 힘이 회복되지 않았습니다!")) return;

        waitingForLanding = true;
        // 면역을 여기서(점프하는 순간) 켭니다. performSlam()에서 켜면 늦습니다 -
        // 착지 시 EntityDamageEvent(FALL)가 PlayerMoveEvent보다 먼저 오므로,
        // 착지를 감지한 뒤에 켜면 낙하 데미지가 이미 지나간 뒤입니다.
        // 끄는 것은 performSlam()의 3틱 타이머가 그대로 담당합니다.
        fallDamageImmune = true;
        p.setVelocity(new Vector(0, JUMP_POWER, 0));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        p.sendMessage(ChatColor.RED + "포효하며 하늘로 뛰어오릅니다!");
    }

    @Override
    public void onPlayerMove(Player p, PlayerMoveEvent event) {
        if (!waitingForLanding) return;
        if (!p.isOnGround()) return;

        waitingForLanding = false;
        performSlam(p);
    }

    @Override
    public void onEntityDamage(Player p, EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (fallDamageImmune) {
            event.setCancelled(true);
        }
    }

    private void performSlam(Player p) {
        Location loc = p.getLocation();

        // 면역은 onInteract(점프 시점)에서 이미 켜져 있습니다. 여기서는 끄는 타이머만
        // 겁니다. waitingForLanding과 합치지 않는 이유: 착지 판정과 데미지가 정확히 같은
        // 틱에 오지 않는 경우가 있어, waitingForLanding이 false가 된 뒤에도 잠시 면역이
        // 남아야 합니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                fallDamageImmune = false;
            }
        }.runTaskLater(JavaPlugin.getProvidingPlugin(Hulkability.class), 3L);

        p.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
        p.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f);

        // 주변 플레이어 및 동물 등 생물 대미지
        for (Entity e : p.getNearbyEntities(SLAM_RADIUS, SLAM_RADIUS, SLAM_RADIUS)) {
            if (e instanceof LivingEntity && !e.equals(p)) {
                ((LivingEntity) e).damage(SLAM_DAMAGE, p);
            }
        }

        // 발밑 주변의 부드러운 블록(흙/모래/나뭇잎 등) 파괴
        for (int dx = -BLOCK_RADIUS; dx <= BLOCK_RADIUS; dx++) {
            for (int dz = -BLOCK_RADIUS; dz <= BLOCK_RADIUS; dz++) {
                Block b = loc.clone().add(dx, -1, dz).getBlock();
                if (isSoftBlock(b.getType())) {
                    b.breakNaturally();
                }
            }
        }

        p.sendMessage(ChatColor.RED + "쿠웅! 대지를 강하게 내려찍었습니다!");
    }

    private boolean isSoftBlock(Material m) {
        switch (m) {
            case DIRT:
            case GRASS_BLOCK:
            case COARSE_DIRT:
            case PODZOL:
            case SAND:
            case RED_SAND:
            case GRAVEL:
            case OAK_LEAVES:
            case SPRUCE_LEAVES:
            case BIRCH_LEAVES:
            case JUNGLE_LEAVES:
            case ACACIA_LEAVES:
            case DARK_OAK_LEAVES:
            case MANGROVE_LEAVES:
            case AZALEA_LEAVES:
            case FLOWERING_AZALEA_LEAVES:
                return true;
            default:
                return false;
        }
    }
}