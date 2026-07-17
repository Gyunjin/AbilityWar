package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 30초간 전 생존자를 발광시켜 위치를 노출합니다.
 *
 * 자기장은 물리적으로 좁히고 발광은 정보로 좁힙니다. 최후반 대치를 깨는 장치입니다.
 * 유한한 지속시간이라 스스로 만료되므로 해제 코드가 필요 없습니다.
 */
public class GlowingEvent implements GameEvent {

    private static final int DURATION_TICKS = 30 * 20;

    @Override
    public String getName() {
        return "전원 발광";
    }

    @Override
    public boolean canRun(GameContext ctx) {
        return !ctx.isFarming(); // 파밍 중엔 서로 못 때리므로 위치 노출이 의미 없다
    }

    @Override
    public void start(GameContext ctx) {
        for (Player p : ctx.getSurvivors()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DURATION_TICKS, 0, false, false, false));
        }

        GameEventManager.announce("전원 발광",
                ChatColor.YELLOW + "30초간 모두의 위치가 드러납니다. " + ChatColor.GRAY + "숨을 곳은 없습니다.");
    }
}
