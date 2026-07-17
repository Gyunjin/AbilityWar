package org.example;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 발전과제를 채팅과 토스트 양쪽에서 없앱니다.
 *
 * 왜 PlayerAdvancementDoneEvent가 아닌가: 그 이벤트는 Cancellable이 아닙니다. 있는 것은
 * message(Component) 오버로드뿐이라 채팅 브로드캐스트만 없앨 수 있고, 우측 상단 토스트는
 * 발전과제가 이미 부여된 뒤에 클라이언트가 띄우는 것이라 막지 못합니다.
 *
 * PlayerAdvancementCriterionGrantEvent(Paper)는 Cancellable입니다. 취소하면 달성 조건
 * 자체가 기록되지 않아 발전과제가 완성되지 않고, 따라서 토스트도 채팅도 발생하지 않습니다.
 * 데이터팩이 필요 없습니다.
 */
public class AdvancementSuppressor implements Listener {

    /**
     * 조합법 해금 발전과제는 통과시킵니다. 마인크래프트는 레시피북 해금을 발전과제로
     * 구현하므로, 전부 막으면 레시피북이 영영 비어 있게 됩니다. 제작 자체는 레시피를
     * 알면 되지만, 파밍 위주 게임에서 도감이 안 열리는 것은 실질적인 불편입니다.
     */
    private static final String RECIPE_PREFIX = "minecraft:recipes/";

    @EventHandler(ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        String key = event.getAdvancement().getKey().toString();
        if (key.startsWith(RECIPE_PREFIX)) return;
        event.setCancelled(true);
    }
}
