package org.example.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPickerTest {

    @Test
    void 후보가_없으면_null() {
        assertNull(EventPicker.pick(List.of(), null, new Random(1)));
    }

    @Test
    void 후보가_하나면_직전과_같아도_그것을_반환() {
        // 대안이 없으면 연속 중복을 허용해야 합니다. null을 반환하면 이벤트가 영영 안 나옵니다.
        assertEquals("보급 투하", EventPicker.pick(List.of("보급 투하"), "보급 투하", new Random(1)));
    }

    @Test
    void 직전에_나온_것은_뽑지_않는다() {
        List<String> candidates = List.of("보급 투하", "사령의 밤", "능력 재충전");

        for (int seed = 0; seed < 200; seed++) {
            String picked = EventPicker.pick(candidates, "사령의 밤", new Random(seed));
            assertNotEquals("사령의 밤", picked, "seed=" + seed + "에서 직전 이벤트가 다시 뽑혔습니다");
        }
    }

    @Test
    void 직전이_null이면_전체에서_뽑는다() {
        List<String> candidates = List.of("A", "B", "C");
        Set<String> seen = new HashSet<>();

        for (int seed = 0; seed < 200; seed++) {
            seen.add(EventPicker.pick(candidates, null, new Random(seed)));
        }
        assertEquals(Set.of("A", "B", "C"), seen, "충분히 돌렸는데 안 뽑힌 후보가 있습니다");
    }

    @Test
    void 남은_후보_전부가_뽑힐_수_있다() {
        List<String> candidates = List.of("A", "B", "C");
        Set<String> seen = new HashSet<>();

        for (int seed = 0; seed < 200; seed++) {
            seen.add(EventPicker.pick(candidates, "A", new Random(seed)));
        }
        assertEquals(Set.of("B", "C"), seen);
    }

    @Test
    void 직전이_후보에_없어도_정상_동작() {
        String picked = EventPicker.pick(List.of("A", "B"), "없는이벤트", new Random(1));
        assertTrue(List.of("A", "B").contains(picked));
    }
}
