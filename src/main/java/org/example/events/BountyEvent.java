package org.example.events;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.game.BountySelector;

import java.util.UUID;

/**
 * 생존자 중 최다 킬러의 위치를 60초간 노출합니다.
 *
 * 보상이 아니라 페널티입니다. 앞서가는 사람이 견제당하게 만드는 안티 스노우볼
 * 장치이고, 그동안 집계만 되고 쓰이지 않던 killCounts를 처음으로 게임에 씁니다.
 *
 * 1킬 이상인 생존자가 없으면 canRun이 false를 반환해 다른 이벤트가 추첨됩니다.
 */
public class BountyEvent implements GameEvent {

    private static final int DURATION_TICKS = 60 * 20;

    @Override
    public String getName() {
        return "현상수배";
    }

    @Override
    public boolean canRun(GameContext ctx) {
        if (ctx.isFarming()) return false;
        return BountySelector.topKiller(ctx.getKills(), ctx.getAliveUuids()) != null;
    }

    @Override
    public void start(GameContext ctx) {
        UUID targetId = BountySelector.topKiller(ctx.getKills(), ctx.getAliveUuids());
        if (targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null) return;

        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DURATION_TICKS, 0, false, false, false));

        int kills = ctx.getKills().getOrDefault(targetId, 0);
        GameEventManager.announce("현상수배",
                ChatColor.RED + target.getName() + ChatColor.GRAY + " (" + kills + "킬) 의 위치가 60초간 드러납니다.");
    }
}
