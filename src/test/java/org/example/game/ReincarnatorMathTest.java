package org.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReincarnatorMathTest {

    // --- 넉백 매핑 ---
    // KNOCKBACK_RESISTANCE는 이름과 달리 넉백에 (1.0 - resistance)를 곱합니다.

    @Test
    void 넉백_0퍼센트는_저항_1() {
        assertEquals(1.0, ReincarnatorMath.knockbackResistanceFor(0.0), 1e-9);
    }

    @Test
    void 넉백_100퍼센트는_저항_0() {
        assertEquals(0.0, ReincarnatorMath.knockbackResistanceFor(1.0), 1e-9);
    }

    @Test
    void 넉백_300퍼센트는_저항_음수2() {
        assertEquals(-2.0, ReincarnatorMath.knockbackResistanceFor(3.0), 1e-9);
    }

    @Test
    void 넉백_배율이_커지면_저항은_작아진다() {
        assertTrue(ReincarnatorMath.knockbackResistanceFor(2.0) < ReincarnatorMath.knockbackResistanceFor(1.0));
    }

    // --- 점프력 역산 ---
    // JUMP_STRENGTH는 블록 높이가 아니라 초기 속도입니다. 높이는 속도의 제곱에 비례합니다.

    @Test
    void 기본_높이면_기본_점프력이_나온다() {
        double s = ReincarnatorMath.jumpStrengthForHeight(ReincarnatorMath.BASE_JUMP_HEIGHT);
        assertEquals(ReincarnatorMath.BASE_JUMP_STRENGTH, s, 1e-9);
    }

    @Test
    void 높이가_4배면_점프력은_2배() {
        // 높이 ~ 속도^2 이므로 4배 높이는 2배 속도
        double s = ReincarnatorMath.jumpStrengthForHeight(ReincarnatorMath.BASE_JUMP_HEIGHT * 4);
        assertEquals(ReincarnatorMath.BASE_JUMP_STRENGTH * 2, s, 1e-9);
    }

    @Test
    void 높이가_올라가면_점프력도_올라간다() {
        double low = ReincarnatorMath.jumpStrengthForHeight(1.0);
        double high = ReincarnatorMath.jumpStrengthForHeight(3.0);
        assertTrue(high > low);
    }

    @Test
    void 음수_높이는_하한으로_클램프된다() {
        // 명세의 -2블록은 기본 1.25에서 빼면 음수가 됩니다. 점프가 아예 불가능해지면
        // 지형에 갇혀 게임이 끝나므로 "거의 못 뛴다"까지만 허용합니다.
        double s = ReincarnatorMath.jumpStrengthForHeight(-0.75);
        double expected = ReincarnatorMath.jumpStrengthForHeight(ReincarnatorMath.MIN_JUMP_HEIGHT);
        assertEquals(expected, s, 1e-9);
        assertTrue(s > 0, "점프력이 0 이하가 되면 점프가 불가능해집니다");
    }

    @Test
    void 하한_미만은_전부_같은_값() {
        assertEquals(ReincarnatorMath.jumpStrengthForHeight(-100),
                ReincarnatorMath.jumpStrengthForHeight(0.0), 1e-9);
    }

    @Test
    void 명세_범위의_경계값이_전부_양수() {
        // 목표높이 = 1.25 + random(-2.0, +2.0) 이므로 -0.75 ~ 3.25
        for (double h : new double[]{-0.75, 0.0, 1.25, 3.25}) {
            assertTrue(ReincarnatorMath.jumpStrengthForHeight(h) > 0, "높이 " + h + "에서 점프력이 0 이하");
        }
    }
}
