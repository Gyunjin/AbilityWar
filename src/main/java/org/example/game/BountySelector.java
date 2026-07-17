package org.example.game;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 현상수배 대상(생존자 중 최다 킬러)을 고릅니다. Bukkit에 의존하지 않는 순수 로직입니다.
 *
 * 0킬이면 null을 반환하고, 호출부(BountyEvent.canRun)는 이때 발동을 포기해
 * 다른 이벤트가 추첨되게 합니다.
 */
public final class BountySelector {

    private BountySelector() {
    }

    /**
     * @param kills 플레이어별 킬 수
     * @param alive 아직 생존 중인 플레이어
     * @return 생존자 중 킬이 가장 많은 사람. 1킬 이상인 생존자가 없으면 null.
     */
    public static UUID topKiller(Map<UUID, Integer> kills, Set<UUID> alive) {
        if (kills == null || alive == null) return null;

        UUID best = null;
        int bestCount = 0;
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            if (!alive.contains(entry.getKey())) continue;
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }
}
