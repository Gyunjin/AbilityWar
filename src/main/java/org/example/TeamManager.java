package org.example;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 팀전 모드를 관리합니다. 팀 배정과 아군 공격 방지만 담당하고,
 * 승리 판정(팀 단위)은 Main.checkWinner()에서 이 클래스의 정보를 참고해 처리합니다.
 */
public class TeamManager implements Listener {

    private boolean enabled = false;
    private final Map<UUID, Integer> playerTeam = new HashMap<>();
    private int nextTeamId = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void clear() {
        playerTeam.clear();
        nextTeamId = 1;
    }

    public Integer getTeam(UUID uuid) {
        return playerTeam.get(uuid);
    }

    private boolean isSameTeam(UUID a, UUID b) {
        Integer ta = playerTeam.get(a);
        Integer tb = playerTeam.get(b);
        return ta != null && ta.equals(tb);
    }

    /** 온라인 플레이어를 무작위로 섞어 2인 1팀으로 편성합니다. 인원이 홀수면 마지막 한 명은 단독 팀. */
    public void autoAssignPairs(Collection<? extends Player> players) {
        clear();
        List<Player> list = new ArrayList<>(players);
        Collections.shuffle(list);

        int i = 0;
        while (i < list.size()) {
            int teamId = nextTeamId++;
            playerTeam.put(list.get(i).getUniqueId(), teamId);
            if (i + 1 < list.size()) {
                playerTeam.put(list.get(i + 1).getUniqueId(), teamId);
            }
            i += 2;
        }
    }

    /** 특정 플레이어를 지정한 팀 번호에 수동으로 배정합니다. */
    public void assignManual(int teamId, Player p) {
        playerTeam.put(p.getUniqueId(), teamId);
        if (teamId >= nextTeamId) nextTeamId = teamId + 1;
    }

    @EventHandler
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player damager = resolvePlayerDamager(event.getDamager());
        if (damager == null || damager.equals(victim)) return;

        if (isSameTeam(damager.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** 화살 등 발사체까지 고려해 실제 공격자(Player)를 찾아냅니다. */
    private Player resolvePlayerDamager(Entity damagerEntity) {
        if (damagerEntity instanceof Player) return (Player) damagerEntity;
        if (damagerEntity instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damagerEntity).getShooter();
            if (shooter instanceof Player) return (Player) shooter;
        }
        return null;
    }
}
