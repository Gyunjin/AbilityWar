package org.example.game;

/**
 * 암살자의 등 뒤 판정. Bukkit에 의존하지 않는 순수 계산이라 서버 없이 테스트할 수 있습니다.
 *
 * Vector(org.bukkit.util.Vector)를 인자로 받지 않는 이유가 바로 그것입니다 - 테스트 코드는
 * paper-api가 compileOnly라 Bukkit 타입을 참조할 수 없습니다.
 */
public final class BackstabMath {

    /** 내적 0.5 = 약 60도. 이보다 좁으면 등 뒤로 봅니다. */
    private static final double THRESHOLD = 0.5;

    private BackstabMath() {
    }

    /**
     * 피격자의 시선 방향과 공격이 들어온 방향이 같으면 등 뒤로 판정합니다.
     *
     * Y축은 무시하고 수평 성분만 씁니다 - 위/아래에서 때린 것을 "등 뒤"로 치면 어색합니다.
     *
     * @param lookX     피격자 시선 방향의 X 성분 (정규화 전이어도 됨)
     * @param lookZ     피격자 시선 방향의 Z 성분
     * @param toVictimX (공격자 위치 -> 피격자 위치) 벡터의 X 성분
     * @param toVictimZ 같은 벡터의 Z 성분
     * @return 등 뒤에서 공격했으면 true. 방향을 정할 수 없으면(영벡터) false.
     */
    public static boolean isBackstab(double lookX, double lookZ, double toVictimX, double toVictimZ) {
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double toLen = Math.sqrt(toVictimX * toVictimX + toVictimZ * toVictimZ);

        // 영벡터면 방향이 없습니다. 그냥 나누면 NaN이 나와 비교가 조용히 false가 되므로
        // 명시적으로 처리합니다.
        if (lookLen == 0.0 || toLen == 0.0) return false;

        double dot = (lookX / lookLen) * (toVictimX / toLen)
                + (lookZ / lookLen) * (toVictimZ / toLen);
        return dot > THRESHOLD;
    }
}
