package org.example.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountySelectorTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void 킬이_가장_많은_생존자를_반환() {
        Map<UUID, Integer> kills = Map.of(A, 1, B, 3, C, 2);
        assertEquals(B, BountySelector.topKiller(kills, Set.of(A, B, C)));
    }

    @Test
    void 전원_0킬이면_null() {
        Map<UUID, Integer> kills = Map.of(A, 0, B, 0);
        assertNull(BountySelector.topKiller(kills, Set.of(A, B)));
    }

    @Test
    void 킬_기록이_아예_없으면_null() {
        assertNull(BountySelector.topKiller(Map.of(), Set.of(A, B)));
    }

    @Test
    void 죽은_사람은_제외한다() {
        // B가 최다 킬러지만 이미 탈락했으므로 생존자 중 최다인 C가 나와야 한다
        Map<UUID, Integer> kills = Map.of(A, 1, B, 5, C, 2);
        assertEquals(C, BountySelector.topKiller(kills, Set.of(A, C)));
    }

    @Test
    void 동점이면_그중_하나를_반환() {
        Map<UUID, Integer> kills = Map.of(A, 2, B, 2);
        UUID picked = BountySelector.topKiller(kills, Set.of(A, B));
        assertTrue(List.of(A, B).contains(picked));
    }

    @Test
    void 생존자가_없으면_null() {
        assertNull(BountySelector.topKiller(Map.of(A, 3), Set.of()));
    }
}
