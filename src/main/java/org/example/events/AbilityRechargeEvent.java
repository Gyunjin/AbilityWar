package org.example.events;

import org.bukkit.ChatColor;

/**
 * 전원의 능력 쿨타임을 즉시 초기화합니다.
 *
 * 지속 시간이 없는 순간 이벤트입니다. "30초간 무제한"이 아니라 "즉시 초기화"로 잡은
 * 이유는, 지속 추적도 종료 시 원복도 필요 없어 정리 누락 버그가 생길 수 없기 때문입니다.
 */
public class AbilityRechargeEvent implements GameEvent {

    @Override
    public String getName() {
        return "능력 재충전";
    }

    @Override
    public void start(GameContext ctx) {
        if (ctx.getAbilityManager() == null) return;
        ctx.getAbilityManager().resetAllCooldowns(ctx.getSurvivors());

        GameEventManager.announce("능력 재충전",
                ChatColor.AQUA + "모든 능력의 쿨타임이 초기화되었습니다!");
    }
}
