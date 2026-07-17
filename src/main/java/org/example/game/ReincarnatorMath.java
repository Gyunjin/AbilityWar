package org.example.game;

/**
 * 윤회자의 스탯 변환. Bukkit에 의존하지 않는 순수 계산이라 서버 없이 테스트할 수 있습니다.
 */
public final class ReincarnatorMath {

    /** 바닐라 기본 JUMP_STRENGTH. 초기 속도이며 블록 높이가 아닙니다. */
    public static final double BASE_JUMP_STRENGTH = 0.42;

    /** BASE_JUMP_STRENGTH로 실제로 올라가는 높이(블록). */
    public static final double BASE_JUMP_HEIGHT = 1.25;

    /**
     * 점프 높이 하한. 명세의 -2블록은 기본 1.25에서 빼면 음수가 되는데, 점프가 아예
     * 불가능해지면 지형에 갇혀 게임이 끝납니다. "거의 못 뛴다"까지만 허용합니다.
     */
    public static final double MIN_JUMP_HEIGHT = 0.25;

    private ReincarnatorMath() {
    }

    /**
     * 목표 높이(블록)에 필요한 JUMP_STRENGTH를 역산합니다.
     *
     * JUMP_STRENGTH는 높이가 아니라 초기 속도라서 선형 대응이 안 됩니다.
     * 높이는 속도의 제곱에 비례하므로 sqrt로 뒤집습니다.
     */
    public static double jumpStrengthForHeight(double targetHeight) {
        double h = Math.max(targetHeight, MIN_JUMP_HEIGHT);
        return BASE_JUMP_STRENGTH * Math.sqrt(h / BASE_JUMP_HEIGHT);
    }

    /**
     * 넉백 배율을 KNOCKBACK_RESISTANCE 값으로 바꿉니다.
     *
     * KNOCKBACK_RESISTANCE는 이름과 달리 넉백에 (1.0 - resistance)를 곱합니다. 따라서:
     *   넉백 0%(철벽)   -> resistance  1.0
     *   넉백 100%(기본) -> resistance  0.0
     *   넉백 300%(종이) -> resistance -2.0
     *
     * 음수 저항이 실제로 넉백을 증폭시키는지는 서버 실측이 필요합니다.
     * 증폭되지 않으면 setVelocity 기반 대체 구현으로 전환합니다.
     */
    public static double knockbackResistanceFor(double multiplier) {
        return 1.0 - multiplier;
    }
}
