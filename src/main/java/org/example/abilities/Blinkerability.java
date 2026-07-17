package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.example.HealthBarListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Blinkerability implements Ability {

    private static final long COOLDOWN_MS = 8000;
    private static final String ITEM_TAG = "[능력] 블링커";
    private static final double DASH_DAMAGE = 4.0;

    private long lastUsed = 0;

    @Override
    public String getName() {
        return "블링커";
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + ITEM_TAG);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(createItem());
        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(사용 방법: 전용 네더의 별 우클릭 시 6칸 전방 벽 통과 순간이동)");
            p.sendMessage(ChatColor.GRAY + "(대쉬 경로에 있는 플레이어/동물에게 대미지를 줍니다)");
            p.sendMessage(ChatColor.RED + "(쿨타임: 8초)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        // 인벤토리의 능력 아이템 제거는 AbilityManager가 공통 로직으로 처리합니다.
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 우클릭 한 번에 PlayerInteractEvent는 주손/보조손 두 번 발생합니다. 이 가드가 없으면
        // 대쉬가 성공한 직후 보조손 패스가 다시 들어와 쿨타임 안내 메시지가 매번 떴습니다.
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return;
        if (!item.getItemMeta().hasDisplayName() || !item.getItemMeta().getDisplayName().contains(ITEM_TAG)) return;

        // 다른 능력들과 달리 블링커만 이벤트를 취소하지 않아, 네더의 별 우클릭이
        // 바닐라 동작으로도 처리되고 있었습니다.
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        long timeLeft = (lastUsed + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "아직 능력이 준비되지 않았습니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return;
        }

        Location startLoc = p.getLocation();
        Location dashEnd = startLoc.clone().add(startLoc.getDirection().multiply(6));

        // 착지 위치를 정하기 전, 실제로 지나가는 직선 경로(6칸 전방)를 따라 대미지를 먼저 적용합니다.
        damageAlongDash(p, startLoc, dashEnd);

        Location targetLoc = dashEnd.clone();
        boolean safeSpotFound = false;
        for (int yOffset = 0; yOffset <= 5; yOffset++) {
            Location checkLoc = targetLoc.clone().add(0, yOffset, 0);
            if (checkLoc.getBlock().isPassable() && checkLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
                targetLoc = checkLoc;
                safeSpotFound = true;
                break;
            }
        }
        if (!safeSpotFound) targetLoc.setY(p.getWorld().getHighestBlockYAt(targetLoc) + 1);

        targetLoc.setYaw(startLoc.getYaw());
        targetLoc.setPitch(startLoc.getPitch());

        // 그냥 p.teleport()를 쓰면 체력바 마커(탑승물) 때문에 이동이 조용히 실패해
        // 대쉬가 아예 발동하지 않습니다. 자세한 내용은 HealthBarListener.safeTeleport 참고.
        if (!HealthBarListener.safeTeleport(p, targetLoc)) {
            p.sendMessage(ChatColor.RED + "공간 이동에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return; // 쿨타임을 소모시키지 않습니다.
        }
        p.sendMessage(ChatColor.GREEN + "쉬슉! 벽을 왜곡하여 공간을 이동했습니다.");
        lastUsed = now;
    }

    /** 대쉬 경로(직선) 상에 있는 플레이어/동물 등 생물에게 대미지를 줍니다. */
    private void damageAlongDash(Player p, Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.001) return;

        int steps = Math.max(1, (int) Math.ceil(distance * 2)); // 0.5칸 간격으로 샘플링
        Set<UUID> alreadyHit = new HashSet<>();

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location point = start.clone().add(direction.clone().multiply(t));
            for (Entity e : point.getWorld().getNearbyEntities(point, 1.0, 1.5, 1.0)) {
                if (e instanceof LivingEntity && !e.equals(p) && alreadyHit.add(e.getUniqueId())) {
                    ((LivingEntity) e).damage(DASH_DAMAGE, p);
                }
            }
        }
    }
}