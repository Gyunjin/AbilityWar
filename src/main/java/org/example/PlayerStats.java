package org.example;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * 플레이어 스탯(최대 체력 등) 조작을 한 곳에 모아둡니다.
 *
 * Damageable의 setMaxHealth/getMaxHealth/resetMaxHealth는 전부 deprecated 되었고,
 * 정식 경로는 Attribute API(Attribute.MAX_HEALTH)입니다.
 *
 * 유틸로 묶은 이유가 하나 더 있습니다. 기존 코드는 최대 체력을 낮출 때마다
 * "if (p.getHealth() > 20.0) p.setHealth(20.0)" 같은 클램프를 매번 손으로 붙였는데,
 * 빠뜨리면 현재 체력이 최대치를 넘어 IllegalArgumentException이 납니다.
 * 여기서 항상 함께 처리하므로 호출부가 신경 쓸 필요가 없습니다.
 */
public final class PlayerStats {

    /** 최대 체력 속성을 못 읽는 예외적인 경우에 쓰는 기본값. */
    private static final double FALLBACK_MAX_HEALTH = 20.0;

    private PlayerStats() {
    }

    /** 현재 적용 중인 최대 체력을 반환합니다. */
    public static double getMaxHealth(Player p) {
        AttributeInstance attr = (p == null) ? null : p.getAttribute(Attribute.MAX_HEALTH);
        return (attr == null) ? FALLBACK_MAX_HEALTH : attr.getValue();
    }

    /** 최대 체력을 설정합니다. 현재 체력이 새 최대치를 넘으면 함께 낮춥니다. */
    public static void setMaxHealth(Player p, double value) {
        if (p == null) return;
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        attr.setBaseValue(value);
        double max = attr.getValue();
        if (p.getHealth() > max) {
            p.setHealth(max);
        }
    }

    /**
     * 최대 체력을 이 서버의 기본값으로 되돌립니다.
     * 20.0을 하드코딩하지 않고 속성의 기본값을 쓰므로, 서버가 기본 최대 체력을
     * 바꿔둔 경우에도 올바른 값으로 복원됩니다.
     */
    public static void resetMaxHealth(Player p) {
        if (p == null) return;
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        setMaxHealth(p, attr.getDefaultValue());
    }

    /** 최대 체력을 기본값으로 되돌리고 체력을 가득 채웁니다. (게임 시작/종료 시 초기화용) */
    public static void resetToFullHealth(Player p) {
        if (p == null) return;
        resetMaxHealth(p);
        p.setHealth(getMaxHealth(p));
    }
}
