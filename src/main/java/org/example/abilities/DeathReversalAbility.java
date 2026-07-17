package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.example.HealthBarListener;
import org.example.PlayerStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;

public class DeathReversalAbility implements Ability {

    private static final long COOLDOWN_MS = 300000;
    private static final int HISTORY_MAX_SIZE = 30; // 30초 저장

    private long lastUsed = 0;
    private final LinkedList<PlayerSnapshot> history = new LinkedList<>();
    private final LinkedList<BlockLog> blockHistory = new LinkedList<>();

    private static class PlayerSnapshot {
        Location loc;
        double health;
        int hunger;
        ItemStack[] inventoryContents;
        ItemStack[] armorContents;
        Collection<PotionEffect> activeEffects;
        long timestamp;
    }

    private static class BlockLog {
        BlockState beforeState;
        long timestamp;
        BlockLog(BlockState state, long ts) { this.beforeState = state; this.timestamp = ts; }
    }

    @Override
    public String getName() {
        return "사망회귀";
    }

    @Override
    public void resetCooldown() {
        lastUsed = 0;
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(패시브 효과: 죽음에 이르는 치명적인 대미지를 받으면)");
            p.sendMessage(ChatColor.LIGHT_PURPLE + "10초 ~ 30초 전 과거의 시간선으로 완전히 회귀시킵니다.");
            p.sendMessage(ChatColor.RED + "(쿨타임: 5분)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]이(가) 적용되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        history.clear();
        blockHistory.clear();
    }

    @Override
    public void recordTick(Player p) {
        long now = System.currentTimeMillis();

        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.loc = p.getLocation().clone();
        snapshot.health = p.getHealth();
        snapshot.hunger = p.getFoodLevel();
        snapshot.activeEffects = new ArrayList<>(p.getActivePotionEffects());
        snapshot.timestamp = now;

        ItemStack[] inv = p.getInventory().getContents();
        ItemStack[] invCopy = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null) invCopy[i] = inv[i].clone();
        }
        snapshot.inventoryContents = invCopy;

        ItemStack[] armor = p.getInventory().getArmorContents();
        ItemStack[] armorCopy = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) armorCopy[i] = armor[i].clone();
        }
        snapshot.armorContents = armorCopy;

        history.addLast(snapshot);
        if (history.size() > HISTORY_MAX_SIZE) history.removeFirst();

        while (!blockHistory.isEmpty() && (now - blockHistory.getFirst().timestamp > HISTORY_MAX_SIZE * 1000L)) {
            blockHistory.removeFirst();
        }
    }

    @Override
    public void onBlockPlace(Player p, BlockPlaceEvent event) {
        blockHistory.addLast(new BlockLog(event.getBlockReplacedState(), System.currentTimeMillis()));
    }

    @Override
    public void onBlockBreak(Player p, BlockBreakEvent event) {
        blockHistory.addLast(new BlockLog(event.getBlock().getState(), System.currentTimeMillis()));
    }

    @Override
    public boolean onFatalDamage(Player p, EntityDamageEvent event) {
        if (p.getHealth() - event.getFinalDamage() > 0) return false;

        long now = System.currentTimeMillis();
        long timeLeft = (lastUsed + COOLDOWN_MS) - now;
        if (timeLeft > 0) {
            p.sendMessage(ChatColor.RED + "사망회귀가 대기 중입니다! (남은 시간: " + String.format("%.1f", timeLeft / 1000.0) + "초)");
            return false;
        }

        if (history.isEmpty()) return false;

        event.setCancelled(true);
        lastUsed = now;

        int availableSize = history.size();
        int minSec = Math.min(10, availableSize);
        int maxSec = availableSize;

        int randomBackIndex;
        if (maxSec > minSec) {
            randomBackIndex = new Random().nextInt((maxSec - minSec) + 1) + minSec;
        } else {
            randomBackIndex = minSec;
        }

        int targetIndexFromFront = Math.max(0, availableSize - randomBackIndex);
        PlayerSnapshot past = history.get(targetIndexFromFront);

        for (int i = blockHistory.size() - 1; i >= 0; i--) {
            BlockLog log = blockHistory.get(i);
            if (log.timestamp >= past.timestamp) {
                log.beforeState.update(true, false);
            }
        }
        blockHistory.clear();

        // 체력바 마커(탑승물)가 붙어있으면 p.teleport()가 조용히 실패해 과거 위치로
        // 돌아가지 못합니다. 자세한 내용은 HealthBarListener.safeTeleport 참고.
        HealthBarListener.safeTeleport(p, past.loc);
        // 사망회귀는 다른 능력과 동시에 가질 수 없으므로 최대 체력은 항상 기본값으로 복원합니다.
        PlayerStats.resetMaxHealth(p);
        // 스냅샷의 체력이 현재 최대 체력보다 크면 setHealth가 IllegalArgumentException을
        // 던집니다. 이미 이벤트를 취소하고 쿨타임까지 소모한 뒤라, 예외가 나면 플레이어가
        // 반쯤 복원된 상태로 남습니다. 안전하게 잘라서 넣습니다.
        p.setHealth(Math.clamp(past.health, 0.5, PlayerStats.getMaxHealth(p)));
        p.setFoodLevel(past.hunger);
        p.getInventory().setContents(past.inventoryContents);
        p.getInventory().setArmorContents(past.armorContents);

        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
        for (PotionEffect pastEffect : past.activeEffects) {
            p.addPotionEffect(pastEffect);
        }

        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        p.sendTitle(ChatColor.LIGHT_PURPLE + "시간선 회귀!", ChatColor.GRAY + "" + randomBackIndex + "초 전으로 돌아갔습니다 (사망하지 않음)", 5, 40, 10);

        // 체력을 직접 설정(setHealth)하거나 대미지 이벤트를 취소하면 마인크래프트 엔진이
        // 잠깐의 무적 프레임(hurt resistance)을 남겨두는 경우가 있어, 회귀 직후에도
        // 정상적으로 다시 공격받을 수 있도록 명시적으로 0으로 초기화합니다.
        p.setNoDamageTicks(0);

        history.clear();
        p.sendMessage("");
        p.sendMessage(ChatColor.DARK_RED + "☠ 치명상 감지! 시간선 왜곡으로 " + randomBackIndex + "초 전 과거 세계선으로 긴급 전이합니다. (쿨타임 5분)");
        p.sendMessage("");

        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[능력자] '" + p.getName() + "'이(가) " + randomBackIndex + "초 전 시간선으로 사망회귀를 발동시켰습니다!");

        return true;
    }
}
