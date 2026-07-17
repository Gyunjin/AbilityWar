package org.example.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 이벤트 후보 중 하나를 뽑습니다. Bukkit에 의존하지 않는 순수 로직입니다.
 *
 * 같은 이벤트가 연속으로 나오면 "무작위"가 고장 난 것처럼 보이므로 직전에 나온 것은
 * 제외합니다. 다만 후보가 그것 하나뿐이면 제외하지 않습니다 - 제외해버리면
 * 이벤트가 영영 발동하지 않습니다.
 */
public final class EventPicker {

    private EventPicker() {
    }

    /**
     * @param candidates 발동 가능한 이벤트 이름 목록
     * @param lastPicked 직전에 발동한 이벤트 이름 (없으면 null)
     * @return 뽑힌 이름. 후보가 비어있으면 null.
     */
    public static String pick(List<String> candidates, String lastPicked, Random random) {
        if (candidates == null || candidates.isEmpty()) return null;

        List<String> pool = new ArrayList<>(candidates);
        if (pool.size() > 1) {
            pool.remove(lastPicked);
        }
        return pool.get(random.nextInt(pool.size()));
    }
}
