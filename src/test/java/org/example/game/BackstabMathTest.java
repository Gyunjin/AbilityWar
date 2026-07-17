package org.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackstabMathTest {

    // 피격자가 +X 방향을 바라보고 있다고 고정하고, 공격이 들어온 방향을 바꿔가며 확인합니다.
    private static final double LOOK_X = 1.0;
    private static final double LOOK_Z = 0.0;

    @Test
    void 뒤에서_때리면_등_뒤다() {
        // 공격자가 -X쪽에 있으면 (공격자 -> 피격자) 벡터는 +X. 피격자 시선도 +X = 등 뒤.
        assertTrue(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 1.0, 0.0));
    }

    @Test
    void 정면에서_때리면_등_뒤가_아니다() {
        // 공격자가 +X쪽에 있으면 (공격자 -> 피격자) 벡터는 -X. 시선 +X와 반대 = 정면.
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, -1.0, 0.0));
    }

    @Test
    void 옆에서_때리면_등_뒤가_아니다() {
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 0.0, 1.0));
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 0.0, -1.0));
    }

    @Test
    void 약_45도면_등_뒤다() {
        // 내적 0.5 = 60도가 경계. 45도는 내적 약 0.707 > 0.5 이므로 등 뒤.
        double c = Math.cos(Math.toRadians(45));
        double s = Math.sin(Math.toRadians(45));
        assertTrue(BackstabMath.isBackstab(LOOK_X, LOOK_Z, c, s));
    }

    @Test
    void 약_75도면_등_뒤가_아니다() {
        // 75도는 내적 약 0.26 < 0.5 이므로 등 뒤가 아니다.
        double c = Math.cos(Math.toRadians(75));
        double s = Math.sin(Math.toRadians(75));
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, c, s));
    }

    @Test
    void 크기가_달라도_방향만_본다() {
        // 정규화하므로 벡터 길이는 결과에 영향을 주지 않아야 한다.
        assertTrue(BackstabMath.isBackstab(5.0, 0.0, 100.0, 0.0));
    }

    @Test
    void 영벡터는_등_뒤가_아니다() {
        // 공격자와 피격자가 정확히 같은 위치면 방향을 정할 수 없다.
        // 그냥 나누면 NaN이 나와 비교가 조용히 false가 되므로 명시적으로 확인한다.
        assertFalse(BackstabMath.isBackstab(LOOK_X, LOOK_Z, 0.0, 0.0));
        assertFalse(BackstabMath.isBackstab(0.0, 0.0, 1.0, 0.0));
    }
}
