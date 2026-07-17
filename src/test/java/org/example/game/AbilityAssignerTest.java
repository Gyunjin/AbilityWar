package org.example.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityAssignerTest {

    private static final List<String> POOL = List.of("블링커", "헐크", "포세이돈", "사망회귀", "네크로맨서", "티모");

    @Test
    void 인원이_풀보다_적으면_전원_다른_능력() {
        List<String> result = AbilityAssigner.assign(POOL, 5, new Random(1));

        assertEquals(5, result.size());
        assertEquals(5, new HashSet<>(result).size(), "중복이 있으면 안 됩니다: " + result);
        assertTrue(POOL.containsAll(result));
    }

    @Test
    void 인원이_풀과_같으면_전원_다른_능력() {
        List<String> result = AbilityAssigner.assign(POOL, 6, new Random(2));

        assertEquals(6, result.size());
        assertEquals(6, new HashSet<>(result).size());
    }

    @Test
    void 인원이_풀을_초과하면_초과분만_중복_허용() {
        List<String> result = AbilityAssigner.assign(POOL, 8, new Random(3));

        assertEquals(8, result.size());
        // 6종이 최소 한 번씩은 모두 등장해야 한다 (초과분 2명만 중복)
        assertEquals(6, new HashSet<>(result).size());
    }

    @Test
    void 배정_순서가_매번_같지_않다() {
        List<String> a = AbilityAssigner.assign(POOL, 6, new Random(1));
        List<String> b = AbilityAssigner.assign(POOL, 6, new Random(999));

        assertTrue(!a.equals(b), "서로 다른 시드인데 순서가 같습니다");
    }

    @Test
    void 원본_풀을_변경하지_않는다() {
        List<String> pool = new ArrayList<>(POOL);
        AbilityAssigner.assign(pool, 6, new Random(1));

        assertEquals(POOL, pool);
    }

    @Test
    void 인원이_0이면_빈_목록() {
        assertEquals(List.of(), AbilityAssigner.assign(POOL, 0, new Random(1)));
    }

    @Test
    void 풀이_비어있으면_빈_목록() {
        Set<String> empty = new HashSet<>();
        assertEquals(List.of(), AbilityAssigner.assign(List.copyOf(empty), 3, new Random(1)));
    }
}
