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
        // java.util.Random은 연속된 작은 시드값에 대해 초기 스크램블이 약해서, seed마다
        // new Random(seed)로 한 번씩만 뽑으면 특정 값(예: nextInt(2)의 첫 결과)이 편향되어
        // 분포 검증이 아니라 시드 배열의 우연에 좌우됩니다. 실제 분포를 검증하려면 Random
        // 인스턴스 하나로 충분히 많이 뽑아 통계를 내야 합니다.
        List<String> candidates = List.of("A", "B", "C");
        Set<String> seen = new HashSet<>();
        Random random = new Random(42);

        for (int i = 0; i < 200; i++) {
            seen.add(EventPicker.pick(candidates, null, random));
        }
        assertEquals(Set.of("A", "B", "C"), seen, "충분히 돌렸는데 안 뽑힌 후보가 있습니다");
    }

    @Test
    void 남은_후보_전부가_뽑힐_수_있다() {
        // 위와 동일한 이유로, seed별 단발 추첨 대신 고정 시드의 Random 하나로 200번 뽑아
        // 분포를 관찰합니다. 이렇게 해야 nextInt(2)류의 시드 편향 없이 실제 추첨 로직의
        // 분포를 검증할 수 있습니다.
        List<String> candidates = List.of("A", "B", "C");
        Set<String> seen = new HashSet<>();
        Random random = new Random(42);

        for (int i = 0; i < 200; i++) {
            seen.add(EventPicker.pick(candidates, "A", random));
        }
        assertEquals(Set.of("B", "C"), seen);
    }

    @Test
    void 직전이_후보에_없어도_정상_동작() {
        String picked = EventPicker.pick(List.of("A", "B"), "없는이벤트", new Random(1));
        assertTrue(List.of("A", "B").contains(picked));
    }
}
