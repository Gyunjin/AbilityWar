package org.example.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 능력을 플레이어 수만큼 배정합니다. Bukkit에 의존하지 않는 순수 로직이라
 * 서버 없이 테스트할 수 있습니다.
 *
 * 중복 없이 배정하는 이유: 사망 시 능력 공개와 맞물려 소거법이 성립해야 하는데,
 * 같은 능력이 여러 명에게 가면 "티모가 죽었다"를 알아도 아무것도 좁힐 수 없습니다.
 */
public final class AbilityAssigner {

    private AbilityAssigner() {
    }

    /**
     * @param pool   배정 가능한 능력 이름 목록 (변경하지 않음)
     * @param count  배정할 인원 수
     * @return 크기가 count인 능력 이름 목록. 인원이 풀보다 많으면 풀을 다 쓴 뒤
     *         초과분만 다시 섞어 이어 붙이므로, 모든 능력이 최소 한 번씩 등장합니다.
     */
    public static List<String> assign(List<String> pool, int count, Random random) {
        if (pool == null || pool.isEmpty() || count <= 0) return List.of();

        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            List<String> round = new ArrayList<>(pool);
            Collections.shuffle(round, random);
            for (String name : round) {
                if (result.size() >= count) break;
                result.add(name);
            }
        }
        return result;
    }

    /**
     * @param all   전체 능력 이름 목록 (변경하지 않음)
     * @param taken 이미 다른 플레이어가 보유 중인 능력 이름
     * @return all에서 taken을 뺀 목록. 단, 그 결과가 비면(=인원이 능력 수보다 많아
     *         taken이 all 전체를 덮는 경우) all을 그대로 반환합니다. 비어있는 풀을
     *         assign()에 넘기면 빈 목록이 나오고, 호출부의 assigned.get(i)가
     *         IndexOutOfBoundsException을 던지기 때문입니다.
     */
    public static List<String> availablePool(List<String> all, Collection<String> taken) {
        List<String> result = new ArrayList<>(all);
        result.removeAll(taken);
        return result.isEmpty() ? all : result;
    }
}
